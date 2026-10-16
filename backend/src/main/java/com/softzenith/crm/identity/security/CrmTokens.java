package com.softzenith.crm.identity.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.softzenith.crm.identity.StaffSignIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Staff session tokens for a phone + code sign-in: HS256 with our own key, issuer {@value #ISSUER}, audience
 * {@code authenticated} like Supabase's. The token lists the memberships the code proved ({@value #USERS_CLAIM}); only
 * those are opened (see {@code StaffDirectory#memberships(Jwt)}), never every tenant the phone belongs to.
 */
@Component
class CrmTokens {

    static final String ISSUER = "crm";
    static final String USERS_CLAIM = "staff_user_ids";
    static final Duration LIFETIME = Duration.ofHours(12);

    private static final Logger log = LoggerFactory.getLogger(CrmTokens.class);

    record Issued(String accessToken, Instant expiresAt) {
    }

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    CrmTokens(@Value("${crm.auth.token-secret:}") String secret) {
        var key = key(secret);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("authenticated"))));
    }

    Issued issue(StaffSignIn.Proven proven) {
        // Whole seconds, as a JWT stores them, so the expiry we report is exactly the token's.
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var expires = now.plus(LIFETIME);
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER).audience(List.of("authenticated"))
                .subject("code|" + proven.phoneE164())
                .claim(USERS_CLAIM, proven.userIds().stream().map(Object::toString).toList())
                .issuedAt(now).expiresAt(expires)
                .build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new Issued(token.getTokenValue(), expires);
    }

    JwtDecoder decoder() {
        return decoder;
    }

    private static SecretKey key(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("crm.auth.token-secret is not set: using a random key, so staff code sign-ins end on restart");
            var random = new byte[32];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, "HmacSHA256");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("crm.auth.token-secret must be at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
