package com.softzenith.crm.identity.security;

import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one staff {@link JwtDecoder}: picks the verifier by the token's issuer, then that verifier checks signature,
 * expiry, issuer and audience. Staff tokens come from our phone + code sign-in ({@link CrmTokens}), Supabase Auth
 * (when {@code crm.auth.supabase-url} is set) and, in development only, {@link DevLogin}. An unknown issuer is refused.
 */
@Configuration(proxyBeanMethods = false)
class StaffTokenDecoders {

    @Bean
    JwtDecoder staffJwtDecoder(CrmTokens crm,
                               @Value("${crm.auth.supabase-url:}") String supabaseUrl,
                               @Value("${crm.auth.dev-login.enabled:false}") boolean devLogin,
                               @Value("${crm.auth.dev-login.secret:}") String devSecret,
                               Environment env) {
        Map<String, JwtDecoder> byIssuer = new HashMap<>();
        byIssuer.put(CrmTokens.ISSUER, crm.decoder());
        if (supabaseUrl != null && !supabaseUrl.isBlank()) {
            var issuer = supabaseUrl.replaceAll("/+$", "") + "/auth/v1";
            byIssuer.put(issuer, supabase(issuer));
        }
        if (devLogin) {
            byIssuer.put(DevLogin.ISSUER, new DevLogin().devJwtDecoder(devSecret, env));
        }
        return token -> {
            String issuer;
            try {
                issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            } catch (ParseException e) {
                throw new BadJwtException("Malformed token", e);
            }
            var decoder = issuer == null ? null : byIssuer.get(issuer);
            if (decoder == null) {
                throw new BadJwtException("Token issuer not accepted: " + issuer);
            }
            return decoder.decode(token);
        };
    }

    /** Supabase Auth: asymmetric keys from its JWKS endpoint, fetched on first use. */
    private static JwtDecoder supabase(String issuer) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("authenticated"))));
        return decoder;
    }
}
