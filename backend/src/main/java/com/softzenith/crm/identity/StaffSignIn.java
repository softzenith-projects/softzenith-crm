package com.softzenith.crm.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.softzenith.crm.shared.logging.Mask;
import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.web.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Phone + sign-in code check (AUTH1). A code opens only the memberships (tenants) whose own code it matches, so a
 * tenant Admin who sets a code for a phone can never sign in as that person at another tenant.
 *
 * <p>Only wrong codes count against the limits, so a whole office signing in at 9 am from one address is never
 * throttled: {@value #MAX_FAILURES_PER_PHONE} wrong codes lock a phone for 15 minutes, and {@value #MAX_FAILURES_PER_ADDRESS}
 * per address in 10 minutes stop one machine from trying many phones. Counters are in memory (one instance).
 */
@Service
public class StaffSignIn {

    static final int MAX_FAILURES_PER_PHONE = 5;
    static final Duration PHONE_LOCK = Duration.ofMinutes(15);
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration ADDRESS_WINDOW = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(StaffSignIn.class);

    /** The phone that signed in and the staff rows (one per tenant) whose code it matched. */
    public record Proven(String phoneE164, List<UUID> userIds) {
    }

    private final AppUserRepository users;
    private final SigninCodeHasher hasher;
    private final TransactionTemplate tx;
    /** Checked when the phone has no code anywhere, so that case costs as long as a wrong code. */
    private final String dummyHash;
    private final Cache<String, Bucket> phoneFailures = Caffeine.newBuilder().maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1)).build();
    private final Cache<String, Bucket> addressFailures = Caffeine.newBuilder().maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1)).build();

    StaffSignIn(AppUserRepository users, SigninCodeHasher hasher, TransactionTemplate tx) {
        this.users = users;
        this.hasher = hasher;
        this.tx = tx;
        this.dummyHash = hasher.hash(UUID.randomUUID().toString());
    }

    /**
     * @return the proven memberships, or empty when the phone and code do not match (the reason is only logged)
     * @throws TooManyRequestsException while the phone or the address is locked out
     * @throws com.softzenith.crm.shared.web.InvalidInputException when the phone is not a phone number
     */
    public Optional<Proven> signIn(String rawPhone, String code, String clientIp) {
        var address = addressFailures.get(clientIp, k -> bucket(MAX_FAILURES_PER_ADDRESS, ADDRESS_WINDOW));
        requireNotLocked(address, "Too many failed sign-ins from your network; please wait a few minutes");
        var phone = PhoneNumbers.toE164(rawPhone, "IN");
        var perPhone = phoneFailures.get(phone, k -> bucket(MAX_FAILURES_PER_PHONE, PHONE_LOCK));
        requireNotLocked(perPhone, "Too many wrong codes for this number; please wait 15 minutes and try again");

        List<UUID> matched = TenantContext.callAsSystem(() -> tx.execute(status -> {
            var rows = users.findWithSigninCodeByPhone(phone);
            if (rows.isEmpty()) {
                hasher.matches(code, dummyHash);
                return List.<UUID>of();
            }
            var ok = rows.stream().filter(u -> hasher.matches(code, u.getSigninCodeHash())).toList();
            ok.forEach(AppUser::signedInWithCode);
            return ok.stream().map(AppUser::getId).toList();
        }));

        if (matched.isEmpty()) {
            perPhone.tryConsume(1);
            address.tryConsume(1);
            log.warn("Code sign-in refused for {} from {}{}", Mask.phone(phone), clientIp,
                    perPhone.getAvailableTokens() == 0 ? "; phone locked for " + PHONE_LOCK.toMinutes() + " min" : "");
            return Optional.empty();
        }
        log.info("{} signed in with a code: {} membership(s)", Mask.phone(phone), matched.size());
        return Optional.of(new Proven(phone, matched));
    }

    private static Bucket bucket(int failures, Duration window) {
        return Bucket.builder().addLimit(Bandwidth.builder().capacity(failures).refillIntervally(failures, window).build()).build();
    }

    private static void requireNotLocked(Bucket bucket, String message) {
        var probe = bucket.estimateAbilityToConsume(1);
        if (!probe.canBeConsumed()) {
            throw new TooManyRequestsException(message, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        }
    }
}
