package com.softzenith.crm.identity.web;

import com.softzenith.crm.identity.AppUser;
import com.softzenith.crm.identity.StaffAdminService;
import com.softzenith.crm.identity.StaffAdminService.StaffDetails;
import com.softzenith.crm.identity.UserStatus;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.tenant.FeatureGateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Staff", description = "Onboard and manage staff: counsellors, receptionists, managers, admins")
class StaffController {

    private final StaffAdminService staff;
    private final FeatureGateService featureGate;

    StaffController(StaffAdminService staff, FeatureGateService featureGate) {
        this.staff = staff;
        this.featureGate = featureGate;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Operation(summary = "List staff; assignable=true gives the 'assign lead to' candidates")
    List<StaffResponse> list(@RequestParam(required = false) Boolean assignable,
                             @RequestParam(required = false) UserStatus status) {
        return staff.list().stream()
                .filter(u -> assignable == null || u.getRole().isAssignable() == assignable)
                .filter(u -> status == null || u.getStatus() == status)
                .map(StaffResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    StaffResponse get(@PathVariable UUID id) {
        return StaffResponse.of(staff.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Add a staff member (INVITED until they first sign in with this phone)")
    StaffResponse invite(@Valid @RequestBody StaffRequest request) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return StaffResponse.of(staff.invite(request.toDetails()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    StaffResponse update(@PathVariable UUID id, @Valid @RequestBody StaffRequest request) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return StaffResponse.of(staff.update(id, request.toDetails()));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    StaffResponse disable(@PathVariable UUID id) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return StaffResponse.of(staff.disable(id));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    StaffResponse enable(@PathVariable UUID id) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return StaffResponse.of(staff.enable(id));
    }

    @PostMapping("/{id}/reset-sign-in")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Unlink the user's sign-in identity; their next OTP sign-in with the phone on file links again")
    StaffResponse resetSignIn(@PathVariable UUID id) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return StaffResponse.of(staff.resetSignIn(id));
    }

    @PutMapping("/{id}/signin-code")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Set or replace the code this staff member signs in with (with their mobile number); "
            + "only a hash is stored and sessions opened with an earlier code end")
    StaffResponse setSigninCode(@PathVariable UUID id, @Valid @RequestBody SigninCodeRequest request) {
        return StaffResponse.of(staff.setSigninCode(id, request.code()));
    }

    record SigninCodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    record StaffRequest(@NotBlank @Size(max = 200) String fullName,
                        @NotBlank String phone,
                        @Email String email,
                        @NotNull UUID roleId,
                        UUID branchId,
                        @Size(max = 50) String employeeCode,
                        @Size(max = 100) String designation,
                        LocalDate joinedOn) {
        StaffDetails toDetails() {
            return new StaffDetails(fullName, phone, email, roleId, branchId, employeeCode, designation, joinedOn);
        }
    }

    record StaffRole(UUID id, String code, String name, boolean assignable) {
    }

    record StaffBranch(UUID id, String name) {
    }

    record StaffResponse(UUID id, String fullName, String phone, String email, StaffRole role, StaffBranch branch,
                         UserStatus status, String employeeCode, String designation, LocalDate joinedOn,
                         OffsetDateTime createdAt, boolean hasSigninCode, OffsetDateTime signinCodeSetAt) {
        static StaffResponse of(AppUser u) {
            var r = u.getRole();
            var b = u.getBranch();
            return new StaffResponse(u.getId(), u.getFullName(), u.getPhoneE164(), u.getEmail(),
                    new StaffRole(r.getId(), r.getCode(), r.getName(), r.isAssignable()),
                    b == null ? null : new StaffBranch(b.getId(), b.getName()),
                    u.getStatus(), u.getEmployeeCode(), u.getDesignation(), u.getJoinedOn(),
                    u.getCreatedAt() == null ? null : u.getCreatedAt().atOffset(ZoneOffset.UTC),
                    u.hasSigninCode(), u.getSigninCodeSetAt() == null ? null : u.getSigninCodeSetAt().atOffset(ZoneOffset.UTC));
        }
    }
}
