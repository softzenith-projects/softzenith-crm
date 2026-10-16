package com.softzenith.crm.identity;

import com.softzenith.crm.Fixtures;
import com.softzenith.crm.Fixtures.Staff;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional tests for /api/v1/users, /branches and /roles, one nested class per endpoint: the success path, then each
 * way the request can be refused. Journey-level behaviour (invite → sign in → disable) is in {@link StaffAdminTests}.
 */
@IntegrationTest
class StaffEndpointTests {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;

    Tenant tenant;
    Staff admin;
    Staff receptionist;
    Staff counsellor;

    @BeforeEach
    void setUp() {
        tenant = fixtures.tenant();
        admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        receptionist = fixtures.staff(tenant, DefaultRoles.RECEPTIONIST);
        counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
    }

    @Nested
    class ListStaff {
        @Test
        void filtersByAssignableAndStatus() throws Exception {
            mvc.perform(get("/api/v1/users").param("assignable", "false").with(admin.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", hasItem(receptionist.id().toString())))
                    .andExpect(jsonPath("$[*].id", not(hasItem(counsellor.id().toString()))));

            mvc.perform(get("/api/v1/me").with(counsellor.token())).andExpect(status().isOk()); // → ACTIVE
            mvc.perform(get("/api/v1/users").param("status", "ACTIVE").with(admin.token()))
                    .andExpect(jsonPath("$[*].status", everyItem(is("ACTIVE"))))
                    .andExpect(jsonPath("$[*].id", hasItem(counsellor.id().toString())));
        }

        @Test
        void needsSignInAndUserView() throws Exception {
            mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/users").with(leadOnlyStaff().token())).andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetStaff {
        @Test
        void unknownIdIs404() throws Exception {
            mvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()).with(admin.token())).andExpect(status().isNotFound());
        }

        @Test
        void staffOfAnotherTenantAre404() throws Exception {
            var other = fixtures.staff(fixtures.tenant(), DefaultRoles.ADMIN);
            mvc.perform(get("/api/v1/users/{id}", other.id()).with(admin.token())).andExpect(status().isNotFound());
        }
    }

    @Nested
    class InviteStaff {
        @Test
        void validatesTheBody() throws Exception {
            mvc.perform(post("/api/v1/users").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"\",\"phone\":\"\",\"email\":\"not-an-email\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("roleId")));
        }

        @Test
        void unknownRoleOrBranchIs404() throws Exception {
            mvc.perform(post("/api/v1/users").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("New", TestData.phone(), UUID.randomUUID(), null)))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/users").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("New", TestData.phone(), roleId(DefaultRoles.COUNSELLOR), UUID.randomUUID())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anInvalidPhoneIs400() throws Exception {
            mvc.perform(post("/api/v1/users").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("New", "12", roleId(DefaultRoles.COUNSELLOR), null)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void blankOptionalFieldsAreStoredAsEmpty() throws Exception {
            mvc.perform(post("/api/v1/users").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName":"  Spaced Name  ","phone":"%s","email":"","roleId":"%s","employeeCode":"  "}"""
                                    .formatted(TestData.phone(), roleId(DefaultRoles.COUNSELLOR))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fullName").value("Spaced Name"))
                    .andExpect(jsonPath("$.email").doesNotExist())
                    .andExpect(jsonPath("$.employeeCode").doesNotExist());
        }
    }

    @Nested
    class UpdateStaff {
        @Test
        void updatesProfileRoleBranchAndPhoneBeforeFirstSignIn() throws Exception {
            var branch = fixtures.branch(tenant, "Rohini");
            var newPhone = TestData.phone();
            mvc.perform(put("/api/v1/users/{id}", counsellor.id()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Natasha K", newPhone, roleId(DefaultRoles.BRANCH_MANAGER), branch.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Natasha K"))
                    .andExpect(jsonPath("$.phone").value(newPhone))
                    .andExpect(jsonPath("$.role.code").value(DefaultRoles.BRANCH_MANAGER))
                    .andExpect(jsonPath("$.branch.name").value("Rohini"));
        }

        @Test
        void thePhoneIsLockedOnceTheyHaveSignedIn() throws Exception {
            mvc.perform(get("/api/v1/me").with(counsellor.token())).andExpect(status().isOk());
            mvc.perform(put("/api/v1/users/{id}", counsellor.id()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Test COUNSELLOR", TestData.phone(), roleId(DefaultRoles.COUNSELLOR), null)))
                    .andExpect(status().isConflict());
        }

        @Test
        void anotherStaffMembersPhoneIsRefused() throws Exception {
            mvc.perform(put("/api/v1/users/{id}", counsellor.id()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("Test COUNSELLOR", receptionist.phone(), roleId(DefaultRoles.COUNSELLOR), null)))
                    .andExpect(status().isConflict());
        }

        @Test
        void unknownUserIs404AndNonManagersAre403() throws Exception {
            var counsellorRole = roleId(DefaultRoles.COUNSELLOR);
            mvc.perform(put("/api/v1/users/{id}", UUID.randomUUID()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("X", TestData.phone(), counsellorRole, null)))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/v1/users/{id}", counsellor.id()).with(receptionist.token()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("X", counsellor.phone(), counsellorRole, null)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class SetSigninCode {
        private org.springframework.test.web.servlet.ResultActions setCode(UUID id, String code, Staff as) throws Exception {
            return mvc.perform(put("/api/v1/users/{id}/signin-code", id).with(as.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"%s\"}".formatted(code)));
        }

        @Test
        void anAdminSetsACodeForStaffAndForThemselves() throws Exception {
            mvc.perform(get("/api/v1/users/{id}", counsellor.id()).with(admin.token()))
                    .andExpect(jsonPath("$.hasSigninCode").value(false))
                    .andExpect(jsonPath("$.signinCodeSetAt").doesNotExist());
            setCode(counsellor.id(), "482917", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasSigninCode").value(true))
                    .andExpect(jsonPath("$.signinCodeSetAt").isNotEmpty())
                    .andExpect(jsonPath("$.code").doesNotExist());
            setCode(admin.id(), "739164", admin).andExpect(status().isOk());
        }

        @Test
        void aWeakOrMalformedCodeIs400() throws Exception {
            setCode(counsellor.id(), "123456", admin).andExpect(status().isBadRequest());
            setCode(counsellor.id(), "12ab56", admin).andExpect(status().isBadRequest());
            setCode(counsellor.id(), "", admin).andExpect(status().isBadRequest());
        }

        @Test
        void unknownOrOtherTenantsStaffAre404() throws Exception {
            setCode(UUID.randomUUID(), "482917", admin).andExpect(status().isNotFound());
            var other = fixtures.staff(fixtures.tenant(), DefaultRoles.COUNSELLOR);
            setCode(other.id(), "482917", admin).andExpect(status().isNotFound());
        }

        @Test
        void onlyStaffManagersMayAndNeverForSomeoneWithMorePower() throws Exception {
            setCode(counsellor.id(), "482917", receptionist).andExpect(status().isForbidden());
            setCode(counsellor.id(), "482917", counsellor).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/users/{id}/signin-code", counsellor.id())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"482917\"}")).andExpect(status().isUnauthorized());

            // A narrow HR role may manage staff, but must not take over the Admin by setting their code.
            fixtures.role(tenant, "HR", DataScope.ALL, false, java.util.Set.of(Permission.USER_VIEW, Permission.USER_MANAGE));
            var hr = fixtures.staff(tenant, "HR");
            setCode(admin.id(), "482917", hr).andExpect(status().isForbidden());
        }
    }

    @Nested
    class DisableEnableReset {
        @Test
        void disableThenEnableAnInvitedUserReturnsToInvited() throws Exception {
            mvc.perform(post("/api/v1/users/{id}/disable", counsellor.id()).with(admin.token()))
                    .andExpect(jsonPath("$.status").value("DISABLED"));
            mvc.perform(post("/api/v1/users/{id}/enable", counsellor.id()).with(admin.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INVITED"));
        }

        @Test
        void enablingSomeoneWhoHadSignedInMakesThemActiveAgain() throws Exception {
            mvc.perform(get("/api/v1/me").with(counsellor.token())).andExpect(status().isOk());
            mvc.perform(post("/api/v1/users/{id}/disable", counsellor.id()).with(admin.token()));
            mvc.perform(post("/api/v1/users/{id}/enable", counsellor.id()).with(admin.token()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            mvc.perform(get("/api/v1/me").with(counsellor.token())).andExpect(status().isOk());
        }

        @Test
        void nobodyDisablesOrResetsThemselves() throws Exception {
            mvc.perform(post("/api/v1/users/{id}/disable", admin.id()).with(admin.token())).andExpect(status().isConflict());
            mvc.perform(post("/api/v1/users/{id}/reset-sign-in", admin.id()).with(admin.token())).andExpect(status().isConflict());
        }

        @Test
        void nonManagersAre403() throws Exception {
            mvc.perform(post("/api/v1/users/{id}/enable", counsellor.id()).with(receptionist.token()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/users/{id}/disable", counsellor.id())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Branches {
        @Test
        void adminRenamesAndDeactivatesABranch() throws Exception {
            var branch = fixtures.branch(tenant, "Old Name");
            mvc.perform(put("/api/v1/branches/{id}", branch.getId()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\" New Name \",\"city\":\" \",\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.city").doesNotExist())
                    .andExpect(jsonPath("$.active").value(false));
        }

        @Test
        void activeDefaultsToTrueWhenOmitted() throws Exception {
            var branch = fixtures.branch(tenant, "Somewhere");
            mvc.perform(put("/api/v1/branches/{id}", branch.getId()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Somewhere\"}"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        void unknownBranchIs404AndBlankNameIs400() throws Exception {
            mvc.perform(put("/api/v1/branches/{id}", UUID.randomUUID()).with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/branches").with(admin.token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void listingNeedsSignInAndRolesNeedUserView() throws Exception {
            mvc.perform(get("/api/v1/branches")).andExpect(status().isUnauthorized());
            var leadOnly = leadOnlyStaff();
            mvc.perform(get("/api/v1/branches").with(leadOnly.token())).andExpect(status().isOk());
            mvc.perform(get("/api/v1/roles").with(leadOnly.token())).andExpect(status().isForbidden());
        }
    }

    /** Every default role has USER_VIEW, so permission failures on staff reads need a narrower tenant-defined role. */
    private Staff leadOnlyStaff() {
        fixtures.role(tenant, "LEAD_ONLY", DataScope.OWN, false, java.util.EnumSet.of(Permission.LEAD_VIEW));
        return fixtures.staff(tenant, "LEAD_ONLY");
    }

    private UUID roleId(String code) {
        return fixtures.roleId(tenant, code);
    }

    private static String body(String name, String phone, UUID roleId, UUID branchId) {
        return """
                {"fullName":"%s","phone":"%s","roleId":"%s"%s}""".formatted(name, phone, roleId,
                branchId == null ? "" : ",\"branchId\":\"" + branchId + "\"");
    }
}
