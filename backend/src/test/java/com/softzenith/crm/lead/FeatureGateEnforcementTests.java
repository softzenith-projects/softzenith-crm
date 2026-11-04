package com.softzenith.crm.lead;

import com.jayway.jsonpath.JsonPath;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.Fixtures.Staff;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tenant's plan (subscription feature gates), orthogonal to permissions: a tenant whose plan is missing
 * {@link FeatureGate#TEAM_AND_STUDENTS} gets 403 on team features even for its Admin, but its Admin still works every
 * lead ({@link FeatureGate#LEADS_CORE}).
 */
@IntegrationTest
class FeatureGateEnforcementTests {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;

    @Test
    void teamFeaturesAre403WithoutThePlanGate() throws Exception {
        Tenant tenant = fixtures.tenant(Set.of(FeatureGate.LEADS_CORE));
        Staff admin = fixtures.staff(tenant, DefaultRoles.ADMIN);

        String leadId = createLead(admin);

        mvc.perform(put("/api/v1/leads/{id}/assignment", leadId).with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + admin.id() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Hire","phone":"%s","roleId":"%s"}"""
                                .formatted(TestData.phone(), fixtures.roleId(tenant, DefaultRoles.COUNSELLOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherRolesAndStaffCannotBeReadWithoutThePlanGate() throws Exception {
        Tenant tenant = fixtures.tenant(Set.of(FeatureGate.LEADS_CORE));
        Staff admin = fixtures.staff(tenant, DefaultRoles.ADMIN);

        mvc.perform(get("/api/v1/roles").with(admin.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").with(admin.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/{id}", admin.id()).with(admin.token())).andExpect(status().isForbidden());
    }

    @Test
    void anAdminWorksEveryLeadWithoutTeamAndStudents() throws Exception {
        Tenant tenant = fixtures.tenant(Set.of(FeatureGate.LEADS_CORE));
        Staff admin = fixtures.staff(tenant, DefaultRoles.ADMIN);

        String leadId = createLead(admin);
        mvc.perform(get("/api/v1/leads").with(admin.token())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/leads/{id}", leadId).with(admin.token())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/leads/stats").with(admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        changeStatus(admin, leadId, "{\"status\":\"CONTACTED\"}").andExpect(status().isOk());
        changeStatus(admin, leadId, "{\"status\":\"CLOSED\",\"reason\":\"Not interested\"}").andExpect(status().isOk());
        changeStatus(admin, leadId, "{\"status\":\"NEW\"}") // reopen
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void anAdminCanStillChangeTheirOwnSigninCodeWithoutTeamAndStudents() throws Exception {
        Tenant tenant = fixtures.tenant(Set.of(FeatureGate.LEADS_CORE));
        Staff admin = fixtures.staff(tenant, DefaultRoles.ADMIN);

        mvc.perform(put("/api/v1/users/{id}/signin-code", admin.id()).with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"739164\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void meTellsTheAppWhichFeaturesTheTenantHas() throws Exception {
        Staff admin = fixtures.staff(fixtures.tenant(Set.of(FeatureGate.LEADS_CORE)), DefaultRoles.ADMIN);

        mvc.perform(get("/api/v1/me").with(admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features", contains("LEADS_CORE")));
    }

    @Test
    void leadFeaturesAre403WithoutLeadsCore() throws Exception {
        Tenant tenant = fixtures.tenant(Set.of(FeatureGate.TEAM_AND_STUDENTS));
        Staff admin = fixtures.staff(tenant, DefaultRoles.ADMIN);

        mvc.perform(post("/api/v1/leads").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Walk In","phone":"%s","sourceType":"WALK_IN"}""".formatted(TestData.phone())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/leads/stats").with(admin.token())).andExpect(status().isForbidden());
        changeStatus(admin, UUID.randomUUID().toString(), "{\"status\":\"CONTACTED\"}").andExpect(status().isForbidden());
    }

    private ResultActions changeStatus(Staff admin, String leadId, String body) throws Exception {
        return mvc.perform(post("/api/v1/leads/{id}/status", leadId).with(admin.token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createLead(Staff admin) throws Exception {
        var body = """
                {"fullName":"Walk In","phone":"%s","sourceType":"WALK_IN"}""".formatted(TestData.phone());
        var result = mvc.perform(post("/api/v1/leads").with(admin.token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
