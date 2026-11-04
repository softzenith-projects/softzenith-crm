package com.softzenith.crm.lead;

import com.jayway.jsonpath.JsonPath;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.Fixtures.Staff;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lead import (IMPORT1) end to end, with a file shaped like Western World's "Portal Data Pattern.xlsx": a blank first
 * row, then Sr.No. / Name / Mobile No. / Email ID / Enquiry for / Address / Country Planned.
 */
@IntegrationTest
class LeadImportTests {

    /** Rows 3-10 of imports/portal-data.xlsx; row 9 is blank. */
    private static final String MAPPED = "[\"IGNORE\",\"FULL_NAME\",\"PHONE\",\"EMAIL\",\"SERVICE_INTEREST\",\"EXTRA\",\"PREFERRED_COUNTRY\"]";

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    Tenant tenant;
    Staff admin;

    @BeforeEach
    void setUp() {
        tenant = fixtures.tenant();
        admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
    }

    @Test
    void readsTheFileAndSuggestsAFieldPerColumn() throws Exception {
        mvc.perform(multipart("/api/v1/lead-imports/read").file(portalFile()).with(admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers[1]", is("Name")))
                .andExpect(jsonPath("$.columns", contains("IGNORE", "FULL_NAME", "PHONE", "EMAIL", "SERVICE_INTEREST", "EXTRA", "PREFERRED_COUNTRY")))
                .andExpect(jsonPath("$.rows", hasSize(7)))
                .andExpect(jsonPath("$.rows[0].row", is(3)))
                .andExpect(jsonPath("$.rows[0].cells[2]", is("9876500001")))
                .andExpect(jsonPath("$.rows[6].row", is(10)));
    }

    @Test
    void checkReportsEachRowAndSavesNothing() throws Exception {
        existingLead();
        var count = leadCount();

        mvc.perform(post("/api/v1/lead-imports").param("dryRun", "true").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request(preview(), MAPPED, "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun", is(true)))
                .andExpect(jsonPath("$.total", is(7)))
                .andExpect(jsonPath("$.added", is(3)))
                .andExpect(jsonPath("$.alreadyInCrm", is(1)))
                .andExpect(jsonPath("$.duplicates", is(1)))
                .andExpect(jsonPath("$.rejected", is(2)))
                .andExpect(jsonPath("$.problems[0].row", is(5)))
                .andExpect(jsonPath("$.problems[0].detail", containsString("Not a valid mobile number")))
                .andExpect(jsonPath("$.problems[1].detail", is("No name")))
                .andExpect(jsonPath("$.problems[2].detail", is("Same person as row 3")))
                .andExpect(jsonPath("$.problems[3].outcome", is("ALREADY_IN_CRM")));

        assertThat(leadCount()).isEqualTo(count);
    }

    @Test
    void importsTheRowsWithoutAlertsAndRemembersTheColumns() throws Exception {
        existingLead();
        var preview = preview();
        var values = "{\"SERVICE_INTEREST\": {\"IELTS\": \"IELTS / PTE Coaching\", \"Visa\": \"Student Visa\"}}";

        mvc.perform(post("/api/v1/lead-imports").param("dryRun", "false").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request(preview, MAPPED, values)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", is(3)))
                .andExpect(jsonPath("$.alreadyInCrm", is(1)));

        var json = mvc.perform(get("/api/v1/leads").param("sourceType", "IMPORT").param("q", "Aman").with(admin.token()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> aman = JsonPath.read(json, "$.items[0]");
        assertThat(aman).containsEntry("phone", "+919876500001").containsEntry("email", "aman@mail.test")
                .containsEntry("serviceInterest", "IELTS / PTE Coaching").containsEntry("preferredCountry", "Canada")
                .containsEntry("sourceDetail", "Portal").containsEntry("customFields", Map.of("Address", "12 MG Road, Rohtak"));

        mvc.perform(get("/api/v1/leads/{id}/activities", aman.get("id")).with(admin.token()))
                .andExpect(jsonPath("$[0].type", is("CREATED")))
                .andExpect(jsonPath("$[0].toValue", is("IMPORT")))
                .andExpect(jsonPath("$[0].note", is("Imported from Portal Data Pattern.xlsx, row 3")));

        // No alert or message for imported leads (alerts go out after commit, off the request thread).
        Thread.sleep(Duration.ofMillis(500));
        assertThat(jdbc.queryForObject("""
                select count(*) from notification_log n join leads l on l.id = n.lead_id
                where l.tenant_id = ? and l.source_type = 'IMPORT'""", Integer.class, tenant.getId())).isZero();

        // The next file is matched the way the Admin chose, even where the guess would differ.
        var changed = MAPPED.replace("\"EXTRA\"", "\"MESSAGE\"");
        mvc.perform(post("/api/v1/lead-imports").param("dryRun", "false").with(admin.token())
                .contentType(MediaType.APPLICATION_JSON).content(request(preview, changed, "{}"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/lead-imports/read").file(portalFile()).with(admin.token()))
                .andExpect(jsonPath("$.columns[5]", is("MESSAGE")));
    }

    @Test
    void readsCsvWithSemicolonsSavedByExcelOnWindows() throws Exception {
        var csv = "Name;Mobile;City\r\nJosé Fernandes;9876500011;Goa\r\n".getBytes(Charset.forName("windows-1252"));
        mvc.perform(multipart("/api/v1/lead-imports/read").file(new MockMultipartFile("file", "leads.csv", "text/csv", csv))
                        .with(admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", contains("FULL_NAME", "PHONE", "EXTRA")))
                .andExpect(jsonPath("$.rows[0].cells[0]", is("José Fernandes")))
                .andExpect(jsonPath("$.rows[0].row", is(2)));
    }

    @Test
    void refusesOtherFilesAndRequestsWithoutNameAndPhone() throws Exception {
        mvc.perform(multipart("/api/v1/lead-imports/read")
                        .file(new MockMultipartFile("file", "old.xls", "application/vnd.ms-excel", new byte[]{1, 2}))
                        .with(admin.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString(".xlsx or .csv")));
        mvc.perform(multipart("/api/v1/lead-imports/read")
                        .file(new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", "not a zip".getBytes(StandardCharsets.UTF_8)))
                        .with(admin.token()))
                .andExpect(status().isBadRequest());

        var noPhone = MAPPED.replace("\"PHONE\"", "\"IGNORE\"");
        mvc.perform(post("/api/v1/lead-imports").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(preview(), noPhone, "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("mobile number")));
        var twoNames = MAPPED.replace("\"EXTRA\"", "\"FULL_NAME\"");
        mvc.perform(post("/api/v1/lead-imports").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(preview(), twoNames, "{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saysWhyEachRowCannotBeImported() throws Exception {
        var longText = "x".repeat(201);
        mvc.perform(post("/api/v1/lead-imports").with(admin.token()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fileName": "f.csv", "headers": ["Name", "Mobile", "Email", "Service", "Note", "Note"],
                         "columns": ["FULL_NAME", "PHONE", "EMAIL", "SERVICE_INTEREST", "EXTRA", "EXTRA"],
                         "values": {"SERVICE_INTEREST": {"IELTS": " "}},
                         "rows": [
                           {"row": 2, "cells": ["Asha", "", "", "", "", ""]},
                           {"row": 3, "cells": ["R2-D2", "9876500021", "", "", "", ""]},
                           {"row": 4, "cells": ["Ravi", "9876500022", "not-an-email", "", "", ""]},
                           {"row": 5, "cells": ["Ravi", "9876500023", "", "%s", "", ""]},
                           {"row": 6, "cells": ["Ravi", "9876500024", "", "", "%s", "%<s"]},
                           {"row": 7, "cells": ["Meena", "9876500025", null, "IELTS", "a", "b"]},
                           {"row": 8, "cells": ["Short"]}
                         ]}""".formatted(longText, "y".repeat(4_999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", is(1)))
                .andExpect(jsonPath("$.problems[0].detail", is("No mobile number")))
                .andExpect(jsonPath("$.problems[1].detail", containsString("may only contain letters")))
                .andExpect(jsonPath("$.problems[2].detail", is("Not an email address: not-an-email")))
                .andExpect(jsonPath("$.problems[3].detail", is("A value is too long")))
                .andExpect(jsonPath("$.problems[4].detail", is("The extra columns hold more than 10 KB")))
                .andExpect(jsonPath("$.problems[5].detail", is("No mobile number")));

        mvc.perform(post("/api/v1/lead-imports").param("dryRun", "false").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"headers": ["Name", "Mobile", "Service", "Note", "Note"],
                         "columns": ["FULL_NAME", "PHONE", "SERVICE_INTEREST", "EXTRA", "EXTRA"],
                         "values": {"SERVICE_INTEREST": {"IELTS": " "}},
                         "rows": [{"row": 2, "cells": ["Meena", "9876500025", "IELTS", "a", "b"]}]}"""))
                .andExpect(jsonPath("$.added", is(1)));
        var meena = mvc.perform(get("/api/v1/leads").param("q", "Meena").with(admin.token()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(meena, "$.items[0].serviceInterest")).isEqualTo("IELTS");
        assertThat(JsonPath.<String>read(meena, "$.items[0].sourceDetail")).isEqualTo("Import");
        assertThat(JsonPath.<Map<String, Object>>read(meena, "$.items[0].customFields")).isEqualTo(Map.of("Note", "a", "Note 2", "b"));
    }

    @Test
    void refusesARequestThatDoesNotFitTheFile() throws Exception {
        for (var body : List.of(
                "{}",
                "{\"headers\": [\"Name\"], \"columns\": [], \"rows\": []}",
                "{\"headers\": [\"Name\", \"Mobile\"], \"columns\": [\"FULL_NAME\", null], \"rows\": []}",
                "{\"headers\": [\"Name\", \"Mobile\"], \"columns\": [\"FULL_NAME\", \"PHONE\"], \"rows\": [{\"row\": 2}]}",
                "{\"headers\": [\"Name\", \"Mobile\"], \"columns\": [\"FULL_NAME\", \"PHONE\"], \"rows\": [{\"row\": 2, \"cells\": [\"%s\"]}]}"
                        .formatted("z".repeat(5_001)),
                "{\"headers\": [\"Name\", \"Mobile\"], \"columns\": [\"FULL_NAME\", \"EXTRA\"], \"rows\": []}")) {
            mvc.perform(post("/api/v1/lead-imports").with(admin.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void needsTheImportPermission() throws Exception {
        var receptionist = fixtures.staff(tenant, DefaultRoles.RECEPTIONIST);
        mvc.perform(multipart("/api/v1/lead-imports/read").file(portalFile()).with(receptionist.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/lead-imports").with(receptionist.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(preview(), MAPPED, "{}")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/lead-imports/read").file(portalFile())).andExpect(status().isUnauthorized());
    }

    private MockMultipartFile portalFile() throws Exception {
        try (var in = getClass().getResourceAsStream("/imports/portal-data.xlsx")) {
            return new MockMultipartFile("file", "Portal Data Pattern.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", in.readAllBytes());
        }
    }

    /** The preview's headers and rows, as JSON. */
    private String preview() throws Exception {
        var json = mvc.perform(multipart("/api/v1/lead-imports/read").file(portalFile()).with(admin.token()))
                .andReturn().getResponse().getContentAsString();
        List<Object> headers = JsonPath.read(json, "$.headers");
        List<Object> rows = JsonPath.read(json, "$.rows");
        return "\"headers\": " + JsonPath.parse(headers).jsonString() + ", \"rows\": " + JsonPath.parse(rows).jsonString();
    }

    private static String request(String preview, String columns, String values) {
        return "{\"fileName\": \"Portal Data Pattern.xlsx\", \"sourceLabel\": \"Portal\", " + preview
                + ", \"columns\": " + columns + ", \"values\": " + values + "}";
    }

    /** Row 8's person already has an open lead. */
    private void existingLead() throws Exception {
        mvc.perform(post("/api/v1/leads").with(admin.token()).contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName": "Existing Person", "phone": "9876500006", "email": "existing@mail.test", "sourceType": "PHONE"}"""))
                .andExpect(status().isCreated());
    }

    private Integer leadCount() {
        return jdbc.queryForObject("select count(*) from leads where tenant_id = ?", Integer.class, tenant.getId());
    }
}
