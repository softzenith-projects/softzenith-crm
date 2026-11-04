package com.softzenith.crm.platform;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SoftZenith platform console's API end to end: sign-in (and its protections), isolation from tenant staff, and
 * onboarding a business from blueprint to a working tenant whose admin can sign in and whose website form takes enquiries.
 */
@IntegrationTest
class PlatformOnboardingTests {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired PlatformAuthService auth;
    @Autowired JdbcClient jdbc;

    /** A fresh admin; each test signs in from its own address so the per-address limit never spills between tests. */
    private String newAdmin() {
        var username = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        auth.createAdmin(username, PASSWORD);
        return username;
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String freshIp() {
        return "10.9." + (IP.get() / 250) + "." + (IP.getAndIncrement() % 250 + 1);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password, String ip) throws Exception {
        return mvc.perform(post("/api/platform/auth/login").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String token() throws Exception {
        var body = login(newAdmin(), PASSWORD, freshIp()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    @Nested
    class SignIn {
        @Test
        void aPlatformAdminSignsInAndIsRecordedInTheAuditLog() throws Exception {
            var username = newAdmin();
            var body = login(" " + username.toUpperCase() + " ", PASSWORD, freshIp())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            mvc.perform(get("/api/platform/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username));
            assertThat(audit(username, "LOGIN")).isEqualTo(1);
        }

        @Test
        void unknownUsersAndWrongPasswordsGetTheSameAnswer() throws Exception {
            var username = newAdmin();
            var ip = freshIp();
            var wrong = login(username, "wrong-password-123", ip).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            var unknown = login("nobody-" + UUID.randomUUID().toString().substring(0, 6), PASSWORD, ip)
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<String>read(wrong, "$.detail")).isEqualTo(JsonPath.read(unknown, "$.detail"));
            assertThat(audit(username, "LOGIN_REFUSED")).isEqualTo(1);
        }

        @Test
        void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightOne() throws Exception {
            var username = newAdmin();
            var ip = freshIp();
            for (int i = 0; i < 5; i++) login(username, "wrong-password-123", ip).andExpect(status().isUnauthorized());
            login(username, PASSWORD, ip).andExpect(status().isUnauthorized());
        }

        @Test
        void oneAddressIsThrottledAfterTenAttempts() throws Exception {
            var ip = freshIp();
            for (int i = 0; i < 10; i++) login("nobody", "whatever-password", ip).andExpect(status().isUnauthorized());
            login("nobody", "whatever-password", ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.detail", containsString("Too many sign-in attempts")));
        }

        @Test
        void aStaleTokenDoesNotBlockSigningIn() throws Exception {
            var username = newAdmin();
            mvc.perform(post("/api/platform/auth/login").with(from(freshIp())).header("Authorization", "Bearer expired-garbage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        void adminAccountsNeedAProperUsernameAndALongPassword() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.createAdmin("Bad Name!", PASSWORD))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("username");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.createAdmin("valid-name", "short"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("12 characters");
        }

        @Test
        void theBootstrapNeverTouchesExistingAdmins() {
            newAdmin();
            new PlatformAdminBootstrap(auth, "someone-new", "a-long-enough-password").run(null);
            assertThat(jdbc.sql("select count(*) from platform_admins where username = 'someone-new'")
                    .query(Long.class).single()).isZero();
        }

        @Test
        void theBodyIsValidated() throws Exception {
            mvc.perform(post("/api/platform/auth/login").with(from(freshIp())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"\",\"password\":\"\"}")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Isolation {
        @Test
        void everythingButSignInNeedsAPlatformToken() throws Exception {
            mvc.perform(get("/api/platform/tenants")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/platform/tenants/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aTokenSignedWithAnyOtherKeyIsRefused() throws Exception {
            var key = new SecretKeySpec("some-other-key-that-is-at-least-32-chars".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            var forged = new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder().issuer(PlatformTokens.ISSUER).audience(List.of(PlatformTokens.ISSUER))
                            .subject("intruder").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build()))
                    .getTokenValue();
            mvc.perform(get("/api/platform/tenants").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        }

        @Test
        void tenantStaffCannotUseThePlatformApi() throws Exception {
            mvc.perform(get("/api/platform/tenants").with(TestData.otpToken("staff-sub", TestData.phone())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aPlatformTokenOpensNoTenantData() throws Exception {
            mvc.perform(get("/api/v1/me").header("Authorization", token())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/leads").header("Authorization", token())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Onboarding {
        private String blueprint(String slug, String adminPhone, String counsellorPhone) {
            return """
                    {"business": {"name": "Western World Visa Services", "slug": "%s", "defaultRegion": "IN", "timezone": "Asia/Kolkata"},
                     "settings": {"leadNumberPrefix": "WWV", "serviceInterests": ["Study Visa", "IELTS Coaching"],
                                  "countries": ["Canada", "Australia"], "senderName": "Western World", "welcomeEmail": true,
                                  "welcomeWhatsApp": true, "studentUpdates": true, "features": ["LEADS_CORE", "TEAM_AND_STUDENTS"]},
                     "branches": [{"name": "Rohtak", "city": "Rohtak"}, {"name": "Hisar", "city": "Hisar"}],
                     "staff": [{"fullName": "Anuj Kumar", "phone": "%s", "email": "anuj-%s@westernworld.test", "role": "Admin",
                                "branch": "Rohtak", "designation": "Director", "signinCode": "739164"},
                               {"fullName": "Priya Sharma", "phone": "%s", "role": "Counsellor", "branch": "Hisar"}]}
                    """.formatted(slug, adminPhone, slug, counsellorPhone);
        }

        @Test
        void previewShowsWhatWillBeCreatedAndChangesNothing() throws Exception {
            var slug = TestData.slug();
            var auth = token();
            mvc.perform(post("/api/platform/tenants/preview").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content(blueprint(slug, TestData.phone(), TestData.phone())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(true))
                    .andExpect(jsonPath("$.summary.leadNumberExample").value("WWV-000001"))
                    .andExpect(jsonPath("$.summary.staff[0].hasSigninCode").value(true))
                    .andExpect(jsonPath("$.summary.staff[1].hasSigninCode").value(false))
                    .andExpect(jsonPath("$.summary.publicFormPath").value("/enquiry/" + slug))
                    .andExpect(jsonPath("$.summary.branches[0]").value("Rohtak (Rohtak)"))
                    .andExpect(jsonPath("$.summary.staff[1].role").value("Counsellor"))
                    .andExpect(jsonPath("$.summary.roles", hasItem("Receptionist")));

            // The trial run was rolled back: the web address is still free.
            mvc.perform(get("/api/platform/tenants").header("Authorization", auth))
                    .andExpect(jsonPath("$[?(@.slug == '" + slug + "')]").isEmpty());
            mvc.perform(get("/api/v1/public/tenants/{slug}/enquiry-form", slug)).andExpect(status().isNotFound());
        }

        @Test
        void previewListsEveryProblemWithItsField() throws Exception {
            mvc.perform(post("/api/platform/tenants/preview").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"business\":{\"name\":\"\",\"slug\":\"Bad Slug\"},\"staff\":[{\"fullName\":\"X\",\"phone\":\"12\",\"role\":\"Pilot\"}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(false))
                    .andExpect(jsonPath("$.summary").doesNotExist())
                    .andExpect(jsonPath("$.problems[*].field", hasItem("business.name")))
                    .andExpect(jsonPath("$.problems[*].field", hasItem("business.slug")))
                    .andExpect(jsonPath("$.problems[*].field", hasItem("staff[0].phone")))
                    .andExpect(jsonPath("$.problems[*].field", hasItem("staff[0].role")))
                    .andExpect(jsonPath("$.problems[*].field", hasItem("staff")));
        }

        @Test
        void goLiveCreatesAWorkingTenant() throws Exception {
            var slug = TestData.slug();
            var adminPhone = TestData.phone();
            var auth = token();
            mvc.perform(post("/api/platform/tenants").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content(blueprint(slug, adminPhone, TestData.phone())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.slug").value(slug))
                    .andExpect(jsonPath("$.branches").value(2))
                    .andExpect(jsonPath("$.staff").value(2));

            // It is on the platform list, audited, and its public form shows its own options and branches.
            mvc.perform(get("/api/platform/tenants").header("Authorization", auth))
                    .andExpect(jsonPath("$[?(@.slug == '" + slug + "')].name", hasItem("Western World Visa Services")));
            assertThat(jdbc.sql("select count(*) from platform_audit where action = 'TENANT_ONBOARDED' and target = ?")
                    .param(slug).query(Long.class).single()).isEqualTo(1);
            mvc.perform(get("/api/v1/public/tenants/{slug}/enquiry-form", slug))
                    .andExpect(jsonPath("$.tenantName").value("Western World Visa Services"))
                    .andExpect(jsonPath("$.serviceInterests[1]").value("IELTS Coaching"))
                    .andExpect(jsonPath("$.branches[*].name", hasItem("Hisar")));

            // The invited admin signs in with their phone for the first time and runs the business.
            var admin = TestData.otpToken("sub-" + slug, adminPhone);
            mvc.perform(get("/api/v1/me").with(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role.code").value("ADMIN"))
                    .andExpect(jsonPath("$.branch.name").value("Rohtak"))
                    .andExpect(jsonPath("$.tenant.slug").value(slug));

            // ...or, with no SMS or email provider at all, with the sign-in code set at onboarding (only its hash is kept).
            mvc.perform(post("/api/v1/auth/phone-code").with(from(freshIp())).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"%s\",\"code\":\"739164\"}".formatted(adminPhone)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
            assertThat(jdbc.sql("select signin_code_hash from app_users where phone_e164 = ?").param(adminPhone)
                    .query(String.class).single()).startsWith("{bcrypt}").doesNotContain("739164");

            // A website enquiry becomes a lead numbered with the tenant's own prefix.
            var enquirer = TestData.phone();
            mvc.perform(post("/api/v1/public/tenants/{slug}/enquiries", slug).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fullName\":\"First Student\",\"phone\":\"%s\"}".formatted(enquirer))).andExpect(status().isCreated());
            mvc.perform(get("/api/v1/leads").param("q", enquirer).with(admin))
                    .andExpect(jsonPath("$.items[0].leadNumber").value("WWV-000001"));
        }

        @Test
        void aTakenWebAddressOrAnInvalidBlueprintCannotGoLive() throws Exception {
            var slug = TestData.slug();
            var auth = token();
            mvc.perform(post("/api/platform/tenants").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content(blueprint(slug, TestData.phone(), TestData.phone()))).andExpect(status().isCreated());

            mvc.perform(post("/api/platform/tenants/preview").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content(blueprint(slug, TestData.phone(), TestData.phone())))
                    .andExpect(jsonPath("$.ready").value(false))
                    .andExpect(jsonPath("$.problems[0].message", containsString("already in use")));
            mvc.perform(post("/api/platform/tenants").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content(blueprint(slug, TestData.phone(), TestData.phone()))).andExpect(status().isConflict());
            mvc.perform(post("/api/platform/tenants").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"business\":{\"name\":\"No Staff\",\"slug\":\"" + TestData.slug() + "\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("at least one Admin")));
        }
    }

    private long audit(String admin, String action) {
        return jdbc.sql("select count(*) from platform_audit where admin = ? and action = ?")
                .params(admin, action).query(Long.class).single();
    }
}
