package com.softzenith.crm.identity;

import com.softzenith.crm.identity.security.JwtClaims;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves which tenants a signed-in identity is staff of. A phone + code sign-in opens exactly the rows its code
 * matched ({@link #membershipsByCode}); identity-provider sign-ins work as below.
 *
 * <p>Staff are invited by phone number. The first time someone signs in with a verified phone
 * (identity-provider OTP), every invited {@link AppUser} row with that phone is linked to their
 * identity-provider subject; from then on they are matched by subject.
 */
@Service
public class StaffDirectory {

    private static final Logger log = LoggerFactory.getLogger(StaffDirectory.class);

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final TransactionTemplate tx;

    StaffDirectory(AppUserRepository users, TenantRepository tenants, TransactionTemplate tx) {
        this.users = users;
        this.tenants = tenants;
        this.tx = tx;
    }

    /** The memberships a verified staff token opens: by proven rows for a code sign-in, else by identity. */
    public List<StaffPrincipal> memberships(Jwt jwt) {
        var codeUsers = JwtClaims.codeSignInUsers(jwt);
        return codeUsers != null
                ? membershipsByCode(codeUsers, JwtClaims.issuedAt(jwt))
                : memberships(jwt.getSubject(), JwtClaims.verifiedPhone(jwt));
    }

    /**
     * Rows a phone + code sign-in proved, while still active and their code unchanged since the token was issued
     * (an Admin resetting a code ends sessions opened with the old one).
     */
    List<StaffPrincipal> membershipsByCode(Collection<UUID> userIds, Instant issuedAt) {
        if (userIds.isEmpty() || issuedAt == null) {
            return List.of();
        }
        return TenantContext.callAsSystem(() -> tx.execute(status -> toActivePrincipals(
                users.findAllWithRoleAndBranch(userIds).stream()
                        .filter(u -> u.hasSigninCode()
                                && !u.getSigninCodeSetAt().truncatedTo(ChronoUnit.SECONDS).isAfter(issuedAt))
                        .toList())));
    }

    /**
     * @param authSubject    identity-provider user id (JWT {@code sub})
     * @param verifiedPhone  E.164 phone verified by the identity provider, or null
     * @return one principal per tenant where the user is active staff and the tenant is active
     */
    public List<StaffPrincipal> memberships(String authSubject, String verifiedPhone) {
        // Sign-in is the one place that legitimately looks across tenants.
        return TenantContext.callAsSystem(() -> tx.execute(status -> {
            var rows = new ArrayList<>(users.findWithRoleByAuthSubject(authSubject));
            if (verifiedPhone != null) {
                // Also on later sign-ins: the person may since have been invited by another tenant (or re-invited
                // after a sign-in reset). Skip tenants where this identity already has a row.
                var linkedTenants = rows.stream().map(AppUser::getTenantId).collect(Collectors.toSet());
                var invited = users.findUnlinkedWithRoleByPhone(verifiedPhone).stream()
                        .filter(u -> !linkedTenants.contains(u.getTenantId()))
                        .toList();
                invited.forEach(u -> u.linkAuthSubject(authSubject));
                if (!invited.isEmpty()) {
                    log.info("Linked identity {} to {} staff membership(s)", authSubject, invited.size());
                }
                rows.addAll(invited);
            }
            return toActivePrincipals(rows);
        }));
    }

    /** Active staff of active tenants. Call in system context. */
    private List<StaffPrincipal> toActivePrincipals(List<AppUser> rows) {
        var active = rows.stream().filter(AppUser::isActive).toList();
        Map<UUID, Tenant> tenantById = tenants.findAllById(active.stream().map(AppUser::getTenantId).toList())
                .stream().collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return active.stream()
                .filter(u -> tenantById.get(u.getTenantId()).getStatus() == Tenant.Status.ACTIVE)
                .map(u -> toPrincipal(u, tenantById.get(u.getTenantId())))
                .toList();
    }

    private static StaffPrincipal toPrincipal(AppUser u, Tenant tenant) {
        var role = u.getRole();
        var branch = u.getBranch();
        return new StaffPrincipal(u.getId(), tenant.getId(), tenant.getSlug(), tenant.getName(),
                u.getFullName(), u.getPhoneE164(), u.getEmail(),
                role.getCode(), role.getName(), role.getDataScope(), role.getPermissions(),
                branch == null ? null : branch.getId(), branch == null ? null : branch.getName());
    }
}
