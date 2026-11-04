package com.softzenith.crm.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.Fixtures.Staff;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.lead.LeadEvents.LeadCreated;
import com.softzenith.crm.lead.LeadSourceType;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who is told what, driven through the real API: each tenant notification setting and each recipient rule in
 * {@link LeadNotifications}. The happy-path journey is in {@code LeadFlowTests}.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class NotificationRulesTests {

    @RegisterExtension
    static GreenMailExtension mail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired LeadNotifications listeners;

    @Test
    void withWelcomeMessagesOffOnlyStaffAreAlerted() throws Exception {
        var tenant = fixtures.tenant(settings(null, false, false, true));
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var leadId = enquire(tenant, admin, "Quiet Welcome", "quiet@mail.test");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(kinds(leadId, admin))
                .contains("EMAIL/NEW_LEAD_ALERT/" + admin.email()));
        assertThat(kinds(leadId, admin)).noneMatch(k -> k.contains("LEAD_WELCOME"));
    }

    @Test
    void anEnquiryWithoutEmailGetsOnlyTheWhatsAppWelcomeAndStaffWithoutEmailGetNoAlert() throws Exception {
        var tenant = fixtures.tenant();
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        fixtures.staffWithoutEmail(tenant, DefaultRoles.RECEPTIONIST);
        var leadId = enquire(tenant, admin, "No Email", null);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(kinds(leadId, admin))
                .containsExactlyInAnyOrder("WHATSAPP/LEAD_WELCOME/" + phoneOf(leadId, admin),
                        "EMAIL/NEW_LEAD_ALERT/" + admin.email()));
    }

    @Test
    void withStudentUpdatesOffOnlyTheCounsellorHearsAboutAnAssignment() throws Exception {
        var tenant = fixtures.tenant(settings(null, true, true, false));
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        var leadId = enquire(tenant, admin, "No Updates", "noupdates@mail.test");
        assign(leadId, admin, counsellor);
        changeStatus(leadId, counsellor, "{\"status\":\"CONTACTED\"}");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(kinds(leadId, admin))
                .contains("EMAIL/LEAD_ASSIGNED/" + counsellor.email()));
        assertThat(kinds(leadId, admin)).noneMatch(k -> k.contains("LEAD_STATUS_UPDATE"));
    }

    @Test
    void movingBackToNewIsNotNewsToTheStudentButContactedIs() throws Exception {
        var tenant = fixtures.tenant();
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var leadId = enquire(tenant, admin, "Back And Forth", "backforth@mail.test");
        changeStatus(leadId, admin, "{\"status\":\"CONTACTED\"}");
        changeStatus(leadId, admin, "{\"status\":\"NEW\"}");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(kinds(leadId, admin))
                .filteredOn(k -> k.contains("LEAD_STATUS_UPDATE")).hasSize(2)); // CONTACTED: email + WhatsApp
        Thread.sleep(500); // give a (wrong) third message the chance to appear
        assertThat(kinds(leadId, admin)).filteredOn(k -> k.contains("LEAD_STATUS_UPDATE")).hasSize(2);
    }

    @Test
    void aConfiguredSenderNameIsTheEmailFromName() throws Exception {
        var tenant = fixtures.tenant(settings("Western World Admissions", null, null, null));
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var email = "sender-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.test";
        enquire(tenant, admin, "Sender Test", email);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(Arrays.stream(mail.getReceivedMessages())
                .filter(m -> recipients(m).contains(email))
                .map(NotificationRulesTests::from)).anyMatch(f -> f.contains("Western World Admissions")));
    }

    @Test
    void aRepeatEnquiryOnALeadWhoseCounsellorWasDisabledAlertsTheNewLeadStaff() throws Exception {
        var tenant = fixtures.tenant();
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        var phone = TestData.phone();
        var leadId = enquire(tenant, admin, "Orphaned", "orphaned@mail.test", phone);
        assign(leadId, admin, counsellor);
        mvc.perform(post("/api/v1/users/{id}/disable", counsellor.id()).with(admin.token())).andExpect(status().isOk());
        enquire(tenant, admin, "Orphaned", "orphaned@mail.test", phone);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(kinds(leadId, admin))
                .contains("EMAIL/REPEAT_ENQUIRY_ALERT/" + admin.email()));
        assertThat(kinds(leadId, admin)).doesNotContain("EMAIL/REPEAT_ENQUIRY_ALERT/" + counsellor.email());
    }

    @Test
    void aListenerFailureIsLoggedForRetry(CapturedOutput output) {
        // A tenant that does not exist: the listener cannot load it and must fail loudly, not silently.
        listeners.on(new LeadCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "LD-404", "Ghost",
                "+919000000000", null, null, null, null, LeadSourceType.WEBSITE_FORM, null, null, null));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(output)
                .contains("Notifications for LeadCreated on lead LD-404 failed; the event will be retried on restart"));
    }

    private static TenantSettings settings(String senderName, Boolean welcomeEmail, Boolean welcomeWhatsApp, Boolean studentUpdates) {
        return new TenantSettings(null, null, new TenantSettings.Notifications(senderName, welcomeEmail, welcomeWhatsApp, studentUpdates), null);
    }

    private String enquire(Tenant tenant, Staff reader, String name, String email) throws Exception {
        return enquire(tenant, reader, name, email, TestData.phone());
    }

    private String enquire(Tenant tenant, Staff reader, String name, String email, String phone) throws Exception {
        var body = email == null ? "{\"fullName\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone)
                : "{\"fullName\":\"%s\",\"phone\":\"%s\",\"email\":\"%s\"}".formatted(name, phone, email);
        mvc.perform(post("/api/v1/public/tenants/{slug}/enquiries", tenant.getSlug())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        var result = mvc.perform(get("/api/v1/leads").param("q", phone).with(reader.token())).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    }

    private void assign(String leadId, Staff by, Staff to) throws Exception {
        mvc.perform(put("/api/v1/leads/{id}/assignment", leadId).with(by.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + to.id() + "\"}")).andExpect(status().isOk());
    }

    private void changeStatus(String leadId, Staff by, String body) throws Exception {
        mvc.perform(post("/api/v1/leads/{id}/status", leadId).with(by.token()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
    }

    private String phoneOf(String leadId, Staff reader) throws Exception {
        var result = mvc.perform(get("/api/v1/leads/{id}", leadId).with(reader.token())).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.phone");
    }

    /** channel/kind/recipient of every notification on the lead */
    private List<String> kinds(String leadId, Staff reader) throws Exception {
        var result = mvc.perform(get("/api/v1/leads/{id}/notifications", leadId).with(reader.token())).andReturn();
        List<Map<String, Object>> rows = JsonPath.read(result.getResponse().getContentAsString(), "$");
        return rows.stream().map(n -> n.get("channel") + "/" + n.get("kind") + "/" + n.get("recipient")).toList();
    }

    private static String recipients(jakarta.mail.Message m) {
        try {
            return Arrays.toString(m.getAllRecipients());
        } catch (Exception e) {
            return "";
        }
    }

    private static String from(jakarta.mail.Message m) {
        try {
            return Arrays.toString(m.getFrom());
        } catch (Exception e) {
            return "";
        }
    }
}
