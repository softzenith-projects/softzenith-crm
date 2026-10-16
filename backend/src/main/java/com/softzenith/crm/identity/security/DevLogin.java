package com.softzenith.crm.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * DEVELOPMENT ONLY ({@code crm.auth.dev-login.enabled=true}): verifies tokens from {@link DevLoginController} (phone
 * number, no OTP), next to the real issuers in {@link StaffTokenDecoders}. Lets the UI be used without sign-in codes.
 */
class DevLogin {

    private static final Logger log = LoggerFactory.getLogger(DevLogin.class);
    static final String ISSUER = "crm-dev-login";

    static SecretKey key(String secret) {
        if (secret.length() < 32) {
            throw new IllegalStateException("crm.auth.dev-login.secret must be at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    JwtDecoder devJwtDecoder(String secret, Environment env) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            // Belt and braces: a copied config or SPRING_PROFILES_ACTIVE=dev,prod must never open production.
            throw new IllegalStateException("crm.auth.dev-login.enabled must not be set with the prod profile");
        }
        log.warn("DEV LOGIN IS ENABLED: anyone can sign in as any phone number. Never enable this in production.");
        var decoder = NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
