package com.softzenith.crm.leadsheet;

import com.jayway.jsonpath.JsonPath;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.lead.LeadSourceType;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every enquiry of a business with a lead sheet becomes a row there; other businesses send nothing. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadSheetTests {

    private static FakeSheetScript script;

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;

    @BeforeAll
    static void startScript() throws IOException {
        script = new FakeSheetScript();
    }

    @AfterAll
    static void stopScript() {
        script.close();
    }

    @Test
    void newAndRepeatEnquiriesBecomeRows() throws Exception {
        var tenant = fixtures.tenant(withSheet(script.url()));
        var phone = TestData.phone();
        enquire(tenant, "Sheet Riya", phone, "riya.sheet@mail.test", "Home page form");

        var first = await().atMost(Duration.ofSeconds(15)).until(() -> rowOf("Sheet Riya", "New"), r -> r != null);
        assertThat((String) JsonPath.read(first, "$.secret")).isEqualTo("s3cret");
        assertThat((List<String>) JsonPath.read(first, "$.columns")).isEqualTo(LeadSheetSync.COLUMNS);
        List<String> row = JsonPath.read(first, "$.row");
        assertThat(row.get(0)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(row.get(1)).matches("LD-\\d{6}");
        assertThat(row.subList(3, 6)).containsExactly("Sheet Riya", phone, "riya.sheet@mail.test");
        assertThat(row.get(9)).isEqualTo("Website form");
        assertThat(row.get(10)).isEqualTo("Home page form");
        assertThat(row.get(12)).isEqualTo(JsonPath.read(first, "$.ref"));

        enquire(tenant, "Sheet Riya", phone, "riya.sheet@mail.test", "Contact page form");
        var repeat = await().atMost(Duration.ofSeconds(15)).until(() -> rowOf("Sheet Riya", "Repeat #2"), r -> r != null);
        List<String> repeatRow = JsonPath.read(repeat, "$.row");
        assertThat(repeatRow.get(1)).isEqualTo(row.get(1));
        assertThat(repeatRow.get(10)).isEqualTo("Contact page form");
    }

    @Test
    void aBusinessWithoutASheetSendsNothing() throws Exception {
        var tenant = fixtures.tenant();
        enquire(tenant, "Sheetless Sam", TestData.phone(), null, null);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() -> rowOf("Sheetless Sam", "New") == null);
    }

    @Test
    void anUnreachableSheetIsLoggedForRetry(CapturedOutput output) throws Exception {
        var tenant = fixtures.tenant(new TenantSettings("UNR", null, null, null,
                new TenantSettings.LeadSheet("http://localhost:1/exec", "s3cret")));
        enquire(tenant, "Unreachable Uma", TestData.phone(), null, null);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(output)
                .contains("Copying lead UNR-").contains("to the business's sheet failed; the event will be retried on restart"));
    }

    @Test
    void rowValuesAreTidy() {
        var q = new LeadSheetSync.Enquiry(UUID.randomUUID(), UUID.randomUUID(), null, "LD-000001", "New", "Asha", "+91",
                null, null, null, null, null, null, null);
        var row = LeadSheetSync.row(q, ZoneId.of("Asia/Kolkata"));
        assertThat(row).hasSize(LeadSheetSync.COLUMNS.size());
        assertThat(row.get(0)).isNotBlank();
        assertThat(row.get(9)).isEmpty();
        assertThat(LeadSheetSync.label(LeadSourceType.META_LEAD_AD)).isEqualTo("Meta lead ad");
    }

    @Test
    void aBadTimeZoneFallsBackToIndia() {
        var tenant = mock(Tenant.class);
        when(tenant.getTimezone()).thenReturn("Not/AZone");
        assertThat(LeadSheetSync.zone(tenant)).isEqualTo(ZoneId.of("Asia/Kolkata"));
        when(tenant.getTimezone()).thenReturn(null);
        assertThat(LeadSheetSync.zone(tenant)).isEqualTo(ZoneId.of("Asia/Kolkata"));
        when(tenant.getTimezone()).thenReturn("Europe/London");
        assertThat(LeadSheetSync.zone(tenant)).isEqualTo(ZoneId.of("Europe/London"));
    }

    private static TenantSettings withSheet(String url) {
        return new TenantSettings(null, null, null, null, new TenantSettings.LeadSheet(url, "s3cret"));
    }

    /** The post whose row has this name and enquiry kind, or null. */
    private static String rowOf(String name, String kind) {
        return script.posts.stream()
                .filter(p -> name.equals(JsonPath.read(p, "$.row[3]")) && kind.equals(JsonPath.read(p, "$.row[2]")))
                .findFirst().orElse(null);
    }

    private void enquire(Tenant tenant, String name, String phone, String email, String sourceDetail) throws Exception {
        var body = "{\"fullName\":\"%s\",\"phone\":\"%s\"%s%s}".formatted(name, phone,
                email == null ? "" : ",\"email\":\"" + email + "\"",
                sourceDetail == null ? "" : ",\"sourceDetail\":\"" + sourceDetail + "\"");
        mvc.perform(post("/api/v1/public/tenants/{slug}/enquiries", tenant.getSlug())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }
}
