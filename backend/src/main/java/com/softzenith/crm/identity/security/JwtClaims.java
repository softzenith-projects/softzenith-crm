package com.softzenith.crm.identity.security;

import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.web.InvalidInputException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads identity-provider specific claims. Supabase puts the phone, without '+', in {@code phone}. */
public final class JwtClaims {

    /** Supabase {@code amr} methods that prove the user received a code on that phone. */
    private static final Set<String> PHONE_PROOF = Set.of("otp", "sms");

    private JwtClaims() {
    }

    /**
     * The phone in E.164 if the session was established by a phone OTP, else null. The {@code phone} claim alone
     * is not proof: depending on the identity provider's settings a phone can be set without a code (e.g. phone +
     * password sign-up with confirmations off), and this value links invited staff rows to the identity.
     */
    public static String verifiedPhone(Jwt jwt) {
        var phone = jwt.getClaimAsString("phone");
        if (phone == null || phone.isBlank() || !signedInWithOtp(jwt)) {
            return null;
        }
        try {
            return PhoneNumbers.toE164(phone.startsWith("+") ? phone : "+" + phone, "ZZ");
        } catch (InvalidInputException e) {
            return null;
        }
    }

    /**
     * The staff rows a phone + code sign-in proved ({@link CrmTokens}), or null for any other token. Only these
     * memberships may be opened with it.
     */
    public static List<UUID> codeSignInUsers(Jwt jwt) {
        if (!CrmTokens.ISSUER.equals(jwt.getClaimAsString("iss"))) {
            return null;
        }
        var ids = jwt.getClaimAsStringList(CrmTokens.USERS_CLAIM);
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    /** When the token was issued; a code changed after this ends the session. */
    public static Instant issuedAt(Jwt jwt) {
        return jwt.getIssuedAt();
    }

    /** {@code amr} is a list of {@code {"method": "otp", ...}} objects (Supabase) or plain strings (RFC 8176). */
    private static boolean signedInWithOtp(Jwt jwt) {
        if (!(jwt.getClaims().get("amr") instanceof List<?> amr)) {
            return false;
        }
        return amr.stream().anyMatch(entry -> {
            var method = entry instanceof Map<?, ?> m ? m.get("method") : entry;
            return method instanceof String s && PHONE_PROOF.contains(s);
        });
    }
}
