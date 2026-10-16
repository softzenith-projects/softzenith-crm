package com.softzenith.crm.identity.security;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.identity.AppUser;
import com.softzenith.crm.identity.AppUserRepository;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.identity.RoleRepository;
import com.softzenith.crm.identity.StaffAdminService;
import com.softzenith.crm.identity.UserStatus;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phone + sign-in code (AUTH1) end to end with real signed tokens: code set by an Admin → sign in → workspace. Each
 * test signs in from its own address and with fresh phones, so the failure limits never spill between tests.
 */
@IntegrationTest
class CodeSignInFlowTests {

    private static final String CODE = "482917";
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired Fixtures fixtures;
    @Autowired StaffAdminService staffAdmin;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TransactionTemplate tx;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String freshIp() {
        return "10.8." + (IP.get() / 250) + "." + (IP.getAndIncrement() % 250 + 1);
    }

    private ResultActions signIn(String phone, String code, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/phone-code").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\",\"code\":\"%s\"}".formatted(phone, code)));
    }

    private String token(String phone, String code) throws Exception {
        var body = signIn(phone, code, freshIp()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private void setCode(Tenant tenant, UUID userId, String code) {
        TenantContext.call(tenant.getId(), () -> staffAdmin.setSigninCode(userId, code));
    }

    /** A staff member of {@code tenant} with this exact phone (the same person working for two businesses). */
    private UUID staffWithPhone(Tenant tenant, String roleCode, String phone) {
        return TenantContext.call(tenant.getId(), () -> tx.execute(s ->
                users.save(new AppUser("Same Person", phone, null, roles.findByCode(roleCode).orElseThrow(), null)).getId()));
    }

    private UserStatus statusOf(Tenant tenant, UUID userId) {
        return TenantContext.call(tenant.getId(), () -> users.findById(userId).orElseThrow().getStatus());
    }

    @Test
    void anInvitedStaffMemberSignsInWithTheirCodeAndBecomesActive() throws Exception {
        var tenant = fixtures.tenant();
        var receptionist = fixtures.staff(tenant, DefaultRoles.RECEPTIONIST);
        setCode(tenant, receptionist.id(), CODE);
        assertThat(statusOf(tenant, receptionist.id())).isEqualTo(UserStatus.INVITED);

        // Typed the way people type it: 10 digits with a space, no +91.
        var local = receptionist.phone().substring(3);
        var body = signIn(local.substring(0, 5) + " " + local.substring(5), CODE, freshIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var token = "Bearer " + JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(receptionist.id().toString()))
                .andExpect(jsonPath("$.role.code").value(DefaultRoles.RECEPTIONIST));
        assertThat(statusOf(tenant, receptionist.id())).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void aWrongCodeAnUnknownPhoneAndAPhoneWithoutACodeGetTheSameAnswer() throws Exception {
        var tenant = fixtures.tenant();
        var withCode = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        setCode(tenant, withCode.id(), CODE);
        var withoutCode = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);

        for (var attempt : List.of(List.of(withCode.phone(), "739164"), List.of(TestData.phone(), CODE),
                List.of(withoutCode.phone(), CODE))) {
            signIn(attempt.get(0), attempt.get(1), freshIp())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Wrong mobile number or sign-in code"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }
    }

    @Test
    void fiveWrongCodesLockThePhoneEvenForTheRightCode() throws Exception {
        var tenant = fixtures.tenant();
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        setCode(tenant, counsellor.id(), CODE);
        for (int i = 0; i < 5; i++) {
            signIn(counsellor.phone(), "739164", freshIp()).andExpect(status().isUnauthorized());
        }
        signIn(counsellor.phone(), CODE, freshIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("wait 15 minutes")));
    }

    @Test
    void oneAddressTryingManyPhonesIsSlowedDown() throws Exception {
        var ip = freshIp();
        for (int i = 0; i < 20; i++) {
            signIn(TestData.phone(), CODE, ip).andExpect(status().isUnauthorized());
        }
        signIn(TestData.phone(), CODE, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", containsString("network")));
        // Other addresses are unaffected.
        signIn(TestData.phone(), CODE, freshIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void behindATrustedProxyEachVisitorIsCountedOnTheirOwnAddress() throws Exception {
        var proxy = freshIp();
        var visitor = freshIp();
        RequestPostProcessor viaProxy = request -> {
            request.setRemoteAddr(proxy);
            request.addHeader("X-Crm-Proxy-Secret", IntegrationTest.PROXY_SECRET);
            request.addHeader("X-Crm-Client-Ip", visitor);
            return request;
        };
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/v1/auth/phone-code").with(viaProxy).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"%s\",\"code\":\"%s\"}".formatted(TestData.phone(), CODE)))
                    .andExpect(status().isUnauthorized());
        }
        // That visitor is now paused, but the proxy itself and its other visitors are not.
        mvc.perform(post("/api/v1/auth/phone-code").with(viaProxy).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"code\":\"%s\"}".formatted(TestData.phone(), CODE)))
                .andExpect(status().isTooManyRequests());
        signIn(TestData.phone(), CODE, proxy).andExpect(status().isUnauthorized());
    }

    @Test
    void aCodeOpensOnlyTheBusinessWhoseAdminSetIt() throws Exception {
        var a = fixtures.tenant();
        var b = fixtures.tenant();
        var phone = TestData.phone();
        var inA = staffWithPhone(a, DefaultRoles.COUNSELLOR, phone);
        var inB = staffWithPhone(b, DefaultRoles.ADMIN, phone);
        setCode(a, inA, CODE);
        setCode(b, inB, "739164");

        // Business A's code: only A, even though the same phone is an Admin at B.
        var tokenA = token(phone, CODE);
        mvc.perform(get("/api/v1/me/memberships").header("Authorization", tokenA))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tenant.id").value(a.getId().toString()));
        mvc.perform(get("/api/v1/me").header("Authorization", tokenA).header("X-Tenant-ID", b.getId().toString()))
                .andExpect(status().isForbidden());
        assertThat(statusOf(b, inB)).isEqualTo(UserStatus.INVITED);

        // The same code at both businesses opens both, and the person picks one.
        setCode(b, inB, CODE);
        mvc.perform(get("/api/v1/me/memberships").header("Authorization", token(phone, CODE)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void disabledStaffCannotSignIn() throws Exception {
        var tenant = fixtures.tenant();
        var admin = fixtures.staff(tenant, DefaultRoles.ADMIN);
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        setCode(tenant, counsellor.id(), CODE);
        mvc.perform(post("/api/v1/users/{id}/disable", counsellor.id()).with(admin.token())).andExpect(status().isOk());

        signIn(counsellor.phone(), CODE, freshIp()).andExpect(status().isUnauthorized());
    }

    @Test
    void settingANewCodeEndsSessionsOpenedWithTheOldOne() throws Exception {
        var tenant = fixtures.tenant();
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        setCode(tenant, counsellor.id(), CODE);
        var old = token(counsellor.phone(), CODE);
        mvc.perform(get("/api/v1/me").header("Authorization", old)).andExpect(status().isOk());

        Thread.sleep(1_100); // tokens carry whole seconds; a code set within the same second still counts as before
        setCode(tenant, counsellor.id(), "739164");

        mvc.perform(get("/api/v1/me").header("Authorization", old)).andExpect(status().isForbidden());
        signIn(counsellor.phone(), CODE, freshIp()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", token(counsellor.phone(), "739164"))).andExpect(status().isOk());
    }

    @Test
    void aTokenSignedWithAnotherKeyOrIssuerIsRefused() throws Exception {
        var tenant = fixtures.tenant();
        var counsellor = fixtures.staff(tenant, DefaultRoles.COUNSELLOR);
        var key = new SecretKeySpec("someone-elses-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        for (var issuer : List.of(CrmTokens.ISSUER, "https://evil.example/auth/v1")) {
            var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of("authenticated")).subject("code|x")
                    .claim(CrmTokens.USERS_CLAIM, List.of(counsellor.id().toString()))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
            var forged = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged.getTokenValue()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theRequestNeedsAPhoneAndACode() throws Exception {
        signIn("", CODE, freshIp()).andExpect(status().isBadRequest());
        signIn(TestData.phone(), "", freshIp()).andExpect(status().isBadRequest());
        signIn("12", CODE, freshIp()).andExpect(status().isBadRequest());
    }
}
