package com.softzenith.crm.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates a new tenant's default roles, branches and staff. Runs inside the onboarding transaction in system context,
 * so every row carries the new tenant's id explicitly; the caller has already validated the input.
 */
@Service
public class OrganisationProvisioner {

    public record NewBranch(String name, String city) {
    }

    /**
     * @param roleCode   one of the {@link DefaultRoles} codes; @param branchName one of the new branches, or null
     * @param signinCode a code that passed {@link SigninCodes#problem}, or null; hashed when provisioned
     */
    public record NewStaff(String fullName, String phoneE164, String email, String roleCode, String branchName,
                           String employeeCode, String designation, LocalDate joinedOn, String signinCode) {

        @Override
        public String toString() {
            return "NewStaff[" + fullName + ", " + roleCode + ", signinCode " + (signinCode == null ? "none" : "***") + "]";
        }
    }

    private final RoleProvisioner roleProvisioner;
    private final BranchRepository branches;
    private final AppUserRepository users;
    private final SigninCodeHasher hasher;

    OrganisationProvisioner(RoleProvisioner roleProvisioner, BranchRepository branches, AppUserRepository users,
                            SigninCodeHasher hasher) {
        this.roleProvisioner = roleProvisioner;
        this.branches = branches;
        this.users = users;
        this.hasher = hasher;
    }

    /** Staff are created INVITED: each becomes ACTIVE on their first sign-in (code or OTP). Nobody is messaged. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void provision(UUID tenantId, List<NewBranch> newBranches, List<NewStaff> newStaff) {
        var roles = new HashMap<String, Role>();
        roleProvisioner.createDefaultRoles(tenantId).forEach(r -> roles.put(r.getCode(), r));

        Map<String, Branch> byName = new HashMap<>();
        for (var b : newBranches) {
            byName.put(b.name().toLowerCase(), branches.save(new Branch(tenantId, b.name(), b.city())));
        }
        for (var s : newStaff) {
            var branch = s.branchName() == null ? null : byName.get(s.branchName().toLowerCase());
            var user = new AppUser(tenantId, s.fullName(), s.phoneE164(), s.email(), roles.get(s.roleCode()), branch);
            user.updateEmployeeRecord(s.employeeCode(), s.designation(), s.joinedOn());
            if (s.signinCode() != null) {
                user.setSigninCode(hasher.hash(s.signinCode()), java.time.Instant.now());
            }
            users.save(user);
        }
    }
}
