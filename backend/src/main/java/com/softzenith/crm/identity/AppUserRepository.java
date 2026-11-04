package com.softzenith.crm.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByPhoneE164(String phoneE164);

    @Query("select u from AppUser u join fetch u.role left join fetch u.branch order by u.fullName")
    List<AppUser> findAllWithRoleAndBranch();

    @Query("select u from AppUser u join fetch u.role left join fetch u.branch where u.id in :ids")
    List<AppUser> findAllWithRoleAndBranch(Collection<UUID> ids);

    /** Non-disabled users whose role grants the permission. */
    @Query("""
            select u from AppUser u join fetch u.role r left join fetch u.branch
            where :permission member of r.permissions and u.status <> com.softzenith.crm.identity.UserStatus.DISABLED""")
    List<AppUser> findEnabledWithPermission(Permission permission);

    /** All memberships of a signed-in identity (one row per tenant). Call in system context. */
    @Query("select u from AppUser u join fetch u.role left join fetch u.branch where u.authSubject = :subject")
    List<AppUser> findWithRoleByAuthSubject(String subject);

    /** Enabled rows of this phone, in any tenant, that have a sign-in code. Call in system context. */
    @Query("""
            select u from AppUser u
            where u.phoneE164 = :phone and u.signinCodeHash is not null
              and u.status <> com.softzenith.crm.identity.UserStatus.DISABLED""")
    List<AppUser> findWithSigninCodeByPhone(String phone);

    /** Invited rows not yet linked to an identity. Call in system context. */
    @Query("""
            select u from AppUser u join fetch u.role left join fetch u.branch
            where u.phoneE164 = :phone and u.authSubject is null and u.status <> com.softzenith.crm.identity.UserStatus.DISABLED""")
    List<AppUser> findUnlinkedWithRoleByPhone(String phone);
}
