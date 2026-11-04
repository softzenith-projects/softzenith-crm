package com.softzenith.crm.identity;

import com.softzenith.crm.shared.logging.Mask;
import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.web.ConflictException;
import com.softzenith.crm.shared.web.InvalidInputException;
import com.softzenith.crm.shared.web.NotFoundException;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Onboarding and record-keeping of staff (counsellors, receptionists, managers, ...) and branches. */
@Service
@Transactional
public class StaffAdminService {

    private static final Logger log = LoggerFactory.getLogger(StaffAdminService.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final TenantRepository tenants;
    private final SigninCodeHasher hasher;

    StaffAdminService(AppUserRepository users, RoleRepository roles, BranchRepository branches, TenantRepository tenants,
                      SigninCodeHasher hasher) {
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.tenants = tenants;
        this.hasher = hasher;
    }

    public record StaffDetails(String fullName, String phone, String email, UUID roleId, UUID branchId,
                               String employeeCode, String designation, LocalDate joinedOn) {
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllWithRoleAndBranch();
    }

    @Transactional(readOnly = true)
    public AppUser get(UUID id) {
        return users.findAllWithRoleAndBranch(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    /** Adds a staff member as INVITED; they become ACTIVE on first sign-in with this phone. */
    public AppUser invite(StaffDetails d) {
        var phone = normalise(d.phone());
        if (users.findByPhoneE164(phone).isPresent()) {
            throw new ConflictException("A staff member with this phone number already exists");
        }
        var role = role(d.roleId());
        requireGrantable(role);
        var user = new AppUser(d.fullName().trim(), phone, blankToNull(d.email()), role, branch(d.branchId()));
        user.updateEmployeeRecord(blankToNull(d.employeeCode()), blankToNull(d.designation()), d.joinedOn());
        var saved = users.save(user);
        log.info("Staff {} invited (phone {}, role {})", saved.getId(), Mask.phone(phone), d.roleId());
        return saved;
    }

    public AppUser update(UUID id, StaffDetails d) {
        var user = managed(id);
        var role = role(d.roleId());
        if (!role.getId().equals(user.getRole().getId())) {
            if (isSelf(id)) {
                throw new ConflictException("You cannot change your own role");
            }
            requireGrantable(role);
        }
        var phone = normalise(d.phone());
        if (!phone.equals(user.getPhoneE164())) {
            users.findByPhoneE164(phone).ifPresent(other -> {
                throw new ConflictException("A staff member with this phone number already exists");
            });
            user.changePhone(phone);
        }
        user.updateProfile(d.fullName().trim(), blankToNull(d.email()), role, branch(d.branchId()));
        user.updateEmployeeRecord(blankToNull(d.employeeCode()), blankToNull(d.designation()), d.joinedOn());
        log.info("Staff {} updated (role {}, branch {})", id, d.roleId(), d.branchId());
        return user;
    }

    public AppUser disable(UUID id) {
        if (isSelf(id)) {
            throw new ConflictException("You cannot disable your own account");
        }
        var user = managed(id);
        user.disable();
        log.info("Staff {} disabled", id);
        return user;
    }

    public AppUser enable(UUID id) {
        var user = managed(id);
        user.enable();
        log.info("Staff {} enabled", id);
        return user;
    }

    /** Unlinks the sign-in identity (see {@link AppUser#resetSignIn()}); the person signs in again with an OTP. */
    public AppUser resetSignIn(UUID id) {
        if (isSelf(id)) {
            throw new ConflictException("You cannot reset your own sign-in");
        }
        var user = managed(id);
        user.resetSignIn();
        log.info("Staff {} sign-in reset", id);
        return user;
    }

    /**
     * Sets (or replaces) the code this staff member signs in with, together with their phone. Only the hash is kept;
     * sessions opened with an earlier code end. Admins may set their own code too.
     */
    public AppUser setSigninCode(UUID id, String code) {
        SigninCodes.problem(code).ifPresent(problem -> {
            throw new InvalidInputException(problem);
        });
        var user = managed(id);
        user.setSigninCode(hasher.hash(code), Instant.now());
        log.info("Staff {} sign-in code set", id);
        return user;
    }

    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roles.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public List<Branch> branches() {
        return branches.findAllByOrderByName();
    }

    public Branch createBranch(String name, String city) {
        var branch = branches.save(new Branch(name.trim(), blankToNull(city)));
        log.info("Branch {} '{}' created", branch.getId(), branch.getName());
        return branch;
    }

    public Branch updateBranch(UUID id, String name, String city, boolean active) {
        var branch = branches.findById(id).orElseThrow(() -> new NotFoundException("Branch", id));
        branch.update(name.trim(), blankToNull(city), active);
        log.info("Branch {} updated (active {})", id, active);
        return branch;
    }

    /** A user the current staff member may manage: nobody can act on a user whose role outranks their own. */
    private AppUser managed(UUID id) {
        var user = get(id);
        requireGrantable(user.getRole());
        return user;
    }

    /**
     * No privilege escalation: you can only hand out (or manage holders of) permissions you have yourself. Together
     * with "not your own role / account" this also means a tenant cannot lose its last administrator via the API.
     */
    private static void requireGrantable(Role role) {
        CurrentStaff.find().ifPresent(actor -> {
            if (!actor.permissions().containsAll(role.getPermissions())) {
                log.warn("Refused: role {} has permissions the actor does not", role.getCode());
                throw new AccessDeniedException("You cannot grant or manage a role with more permissions than your own");
            }
        });
    }

    private static boolean isSelf(UUID id) {
        return CurrentStaff.find().map(s -> s.userId().equals(id)).orElse(false);
    }

    private Role role(UUID roleId) {
        return roles.findById(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));
    }

    private Branch branch(UUID branchId) {
        return branchId == null ? null : branches.findById(branchId).orElseThrow(() -> new NotFoundException("Branch", branchId));
    }

    private String normalise(String phone) {
        var region = tenants.findById(TenantContext.require()).orElseThrow().getDefaultRegion();
        return PhoneNumbers.toE164(phone, region);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
