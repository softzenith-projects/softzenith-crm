package com.softzenith.crm.identity;

import com.softzenith.crm.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A staff member of a tenant, with basic employee record fields. Signs in with their phone number. */
@Entity
@Table(name = "app_users")
public class AppUser extends TenantScopedEntity {

    /** Identity-provider user id; set on first sign-in. */
    private String authSubject;

    @Column(nullable = false)
    private String fullName;

    @Column(name = "phone_e164", nullable = false)
    private String phoneE164;

    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private String employeeCode;

    private String designation;

    private LocalDate joinedOn;

    /** bcrypt hash of the sign-in code the tenant's Admin set (see {@link SigninCodes}); null when none. */
    private String signinCodeHash;

    /** When the code was last set: sessions opened with an older code end (see {@code StaffDirectory}). */
    private Instant signinCodeSetAt;

    protected AppUser() {
    }

    /** @param phoneE164 already normalised, see {@code PhoneNumbers.toE164} */
    public AppUser(String fullName, String phoneE164, String email, Role role, Branch branch) {
        this.fullName = fullName;
        this.phoneE164 = phoneE164;
        this.email = email;
        this.role = role;
        this.branch = branch;
        this.status = UserStatus.INVITED;
    }

    /** For tenant onboarding, which runs in system context (see {@link OrganisationProvisioner}). */
    AppUser(java.util.UUID tenantId, String fullName, String phoneE164, String email, Role role, Branch branch) {
        super(tenantId);
        this.fullName = fullName;
        this.phoneE164 = phoneE164;
        this.email = email;
        this.role = role;
        this.branch = branch;
        this.status = UserStatus.INVITED;
    }

    public void linkAuthSubject(String subject) {
        this.authSubject = subject;
        if (status == UserStatus.INVITED) {
            status = UserStatus.ACTIVE;
        }
    }

    public void updateProfile(String fullName, String email, Role role, Branch branch) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.branch = branch;
    }

    public void updateEmployeeRecord(String employeeCode, String designation, LocalDate joinedOn) {
        this.employeeCode = employeeCode;
        this.designation = designation;
        this.joinedOn = joinedOn;
    }

    /** The phone is the sign-in identity, so it can only change before the user has signed in. */
    public void changePhone(String phoneE164) {
        if (authSubject != null) {
            throw new com.softzenith.crm.shared.web.ConflictException("Phone cannot be changed after the user has signed in");
        }
        this.phoneE164 = phoneE164;
    }

    /**
     * Forgets the linked identity so the next OTP sign-in with this row's phone links again, e.g. after the person's
     * identity-provider account was recreated or their phone number changed. The phone becomes editable again.
     */
    public void resetSignIn() {
        this.authSubject = null;
        if (status == UserStatus.ACTIVE) {
            status = UserStatus.INVITED;
        }
    }

    /** @param hash bcrypt hash of a code that passed {@link SigninCodes#problem} */
    void setSigninCode(String hash, Instant now) {
        this.signinCodeHash = hash;
        this.signinCodeSetAt = now;
    }

    /** A correct sign-in code proves the phone like an OTP does: an invited member becomes active. */
    void signedInWithCode() {
        if (status == UserStatus.INVITED) {
            status = UserStatus.ACTIVE;
        }
    }

    public boolean hasSigninCode() {
        return signinCodeHash != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    public void enable() {
        status = authSubject == null ? UserStatus.INVITED : UserStatus.ACTIVE;
    }

    public boolean can(Permission permission) {
        return status == UserStatus.ACTIVE && role.has(permission);
    }

    public String getAuthSubject() { return authSubject; }
    public String getFullName() { return fullName; }
    public String getPhoneE164() { return phoneE164; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Branch getBranch() { return branch; }
    public UserStatus getStatus() { return status; }
    public String getEmployeeCode() { return employeeCode; }
    public String getDesignation() { return designation; }
    public LocalDate getJoinedOn() { return joinedOn; }
    String getSigninCodeHash() { return signinCodeHash; }
    public Instant getSigninCodeSetAt() { return signinCodeSetAt; }
}
