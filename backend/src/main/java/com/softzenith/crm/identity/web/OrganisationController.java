package com.softzenith.crm.identity.web;

import com.softzenith.crm.identity.Branch;
import com.softzenith.crm.identity.DataScope;
import com.softzenith.crm.identity.Role;
import com.softzenith.crm.identity.StaffAdminService;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.tenant.FeatureGateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Branches and roles of the current tenant. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Organisation", description = "Branches and roles")
class OrganisationController {

    private final StaffAdminService admin;
    private final FeatureGateService featureGate;

    OrganisationController(StaffAdminService admin, FeatureGateService featureGate) {
        this.admin = admin;
        this.featureGate = featureGate;
    }

    @GetMapping("/branches")
    List<BranchResponse> branches() {
        return admin.branches().stream().map(BranchResponse::of).toList();
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    BranchResponse createBranch(@Valid @RequestBody BranchRequest request) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return BranchResponse.of(admin.createBranch(request.name(), request.city()));
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    BranchResponse updateBranch(@PathVariable UUID id, @Valid @RequestBody BranchRequest request) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return BranchResponse.of(admin.updateBranch(id, request.name(), request.city(),
                request.active() == null || request.active()));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    List<RoleResponse> roles() {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return admin.roles().stream().map(RoleResponse::of).toList();
    }

    record BranchRequest(@NotBlank @Size(max = 200) String name, @Size(max = 100) String city, Boolean active) {
    }

    record BranchResponse(UUID id, String name, String city, boolean active) {
        static BranchResponse of(Branch b) {
            return new BranchResponse(b.getId(), b.getName(), b.getCity(), b.isActive());
        }
    }

    record RoleResponse(UUID id, String code, String name, String description, DataScope dataScope,
                        boolean assignable, boolean system, List<String> permissions) {
        static RoleResponse of(Role r) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getDataScope(),
                    r.isAssignable(), r.isSystem(), r.getPermissions().stream().map(Enum::name).sorted().toList());
        }
    }
}
