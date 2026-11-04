package com.softzenith.crm.identity;

import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.onboarding.TenantOnboardingService;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.softzenith.crm.TestData.phone;
import static com.softzenith.crm.TestData.slug;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in resolution: JWT (as issued by Supabase phone OTP) -> staff membership -> tenant + permissions. */
@IntegrationTest
class MeControllerTests {

    @Autowired MockMvc mvc;
    @Autowired TenantOnboardingService onboarding;
    @Autowired RoleRepository roles;
    @Autowired AppUserRepository users;
    @Autowired TransactionTemplate tx;

    @Test
    void firstSignInLinksTheInvitedUserByPhoneThenMatchesBySubject() throws Exception {
        var tenant = onboard();
        var phone = phone();
        invite(tenant, phone, DefaultRoles.COUNSELLOR);
        var subject = UUID.randomUUID().toString();

        mvc.perform(get("/api/v1/me").with(signedIn(subject, phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.slug").value(tenant.getSlug()))
                .andExpect(jsonPath("$.role.code").value(DefaultRoles.COUNSELLOR))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.permissions", hasItem("LEAD_VIEW")))
                .andExpect(jsonPath("$.permissions", not(hasItem("LEAD_ASSIGN"))));

        // Later tokens are matched by subject alone (e.g. the phone claim is absent).
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value(DefaultRoles.COUNSELLOR));
    }

    @Test
    void adminGetsEveryPermission() throws Exception {
        var tenant = onboard();
        var phone = phone();
        invite(tenant, phone, DefaultRoles.ADMIN);

        mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID().toString(), phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasSize(Permission.values().length)));
    }

    @Test
    void reportsTheTenantsEnabledFeatures() throws Exception {
        var tenant = onboard();
        var phone = phone();
        invite(tenant, phone, DefaultRoles.ADMIN);

        mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID().toString(), phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features", containsInAnyOrder("LEADS_CORE", "TEAM_AND_STUDENTS")));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void signedInButNotStaffAnywhereIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID().toString(), phone())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not active staff of any tenant"));
    }

    @Test
    void disabledUserIsForbidden() throws Exception {
        var tenant = onboard();
        var phone = phone();
        var userId = invite(tenant, phone, DefaultRoles.RECEPTIONIST);
        TenantContext.run(tenant.getId(), () -> tx.executeWithoutResult(s -> users.findById(userId).orElseThrow().disable()));

        mvc.perform(get("/api/v1/me").with(signedIn(UUID.randomUUID().toString(), phone)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffOfSeveralTenantsMustPickOneWithTheTenantHeader() throws Exception {
        var a = onboard();
        var b = onboard();
        var stranger = onboard();
        var phone = phone();
        invite(a, phone, DefaultRoles.ADMIN);
        invite(b, phone, DefaultRoles.RECEPTIONIST);
        var token = signedIn(UUID.randomUUID().toString(), phone);

        mvc.perform(get("/api/v1/me/memberships").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tenant.slug", containsInAnyOrder(a.getSlug(), b.getSlug())));

        mvc.perform(get("/api/v1/me").with(token))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/me").with(token).header("X-Tenant-ID", b.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.id").value(b.getId().toString()))
                .andExpect(jsonPath("$.role.code").value(DefaultRoles.RECEPTIONIST));

        mvc.perform(get("/api/v1/me").with(token).header("X-Tenant-ID", stranger.getId()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me").with(token).header("X-Tenant-ID", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPhoneClaimWithoutOtpProofDoesNotClaimAnInvitation() throws Exception {
        var tenant = onboard();
        var phone = phone();
        invite(tenant, phone, DefaultRoles.ADMIN);

        // e.g. phone + password sign-up with confirmations off: the phone was never proven
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("attacker").claim("phone", phone.substring(1))
                        .claim("amr", java.util.List.of(java.util.Map.of("method", "password"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("attacker").claim("phone", phone.substring(1)))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me").with(signedIn("owner", phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value(DefaultRoles.ADMIN));
    }

    @Test
    void anInvitationFromASecondTenantIsLinkedOnTheNextSignIn() throws Exception {
        var first = onboard();
        var second = onboard();
        var phone = phone();
        var subject = UUID.randomUUID().toString();
        invite(first, phone, DefaultRoles.COUNSELLOR);
        mvc.perform(get("/api/v1/me/memberships").with(signedIn(subject, phone))).andExpect(jsonPath("$", hasSize(1)));

        invite(second, phone, DefaultRoles.RECEPTIONIST);
        mvc.perform(get("/api/v1/me/memberships").with(signedIn(subject, phone)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].tenant.slug", containsInAnyOrder(first.getSlug(), second.getSlug())));
    }

    private Tenant onboard() {
        return onboarding.onboard(slug(), "Acme Visas", "IN", "Asia/Kolkata");
    }

    private UUID invite(Tenant tenant, String phone, String roleCode) {
        return TenantContext.call(tenant.getId(), () -> tx.execute(s -> {
            var role = roles.findByCode(roleCode).orElseThrow();
            return users.save(new AppUser("Test " + roleCode, phone, null, role, null)).getId();
        }));
    }

    private static RequestPostProcessor signedIn(String subject, String phoneE164) {
        return com.softzenith.crm.TestData.otpToken(subject, phoneE164);
    }
}
