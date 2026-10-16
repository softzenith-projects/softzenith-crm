package com.softzenith.crm;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED;

/**
 * Full application against an embedded PostgreSQL (Flyway-migrated, no Docker needed).
 * One shared context for all integration tests, so tests isolate themselves with fresh tenants.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// GreenMail's test SMTP port. Every test posts enquiries from 127.0.0.1 into the shared context, so the per-IP and
// per-tenant enquiry limits are lifted here; the per-phone limit stays at its real value (see PublicIntakeSecurityTests).
@SpringBootTest(properties = {"spring.mail.host=localhost", "spring.mail.port=3025",
        "crm.public-intake.rate-limit.per-ip-per-minute=100000", "crm.public-intake.rate-limit.per-ip-per-hour=100000",
        "crm.public-intake.rate-limit.per-tenant-per-hour=100000",
        "crm.trusted-proxy.secret=" + IntegrationTest.PROXY_SECRET})
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = EMBEDDED)
public @interface IntegrationTest {

    /** The shared secret a tenant website on another host sends with the visitor's address (TrustedProxyFilter). */
    String PROXY_SECRET = "test-proxy-secret-0123456789abcdef";
}
