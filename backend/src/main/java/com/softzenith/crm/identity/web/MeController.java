package com.softzenith.crm.identity.web;

import com.softzenith.crm.identity.CurrentStaff;
import com.softzenith.crm.identity.DataScope;
import com.softzenith.crm.identity.StaffDirectory;
import com.softzenith.crm.identity.StaffPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "The signed-in staff user")
class MeController {

    private final StaffDirectory directory;

    MeController(StaffDirectory directory) {
        this.directory = directory;
    }

    @GetMapping
    @Operation(summary = "Current user in the selected tenant, with role and effective permissions")
    MeResponse me() {
        return MeResponse.from(CurrentStaff.require());
    }

    @GetMapping("/memberships")
    @Operation(summary = "Tenants the signed-in identity is staff of; call after sign-in to pick X-Tenant-ID")
    List<MembershipResponse> memberships(@AuthenticationPrincipal Jwt jwt) {
        return directory.memberships(jwt).stream()
                .map(MembershipResponse::from)
                .toList();
    }

    record MeTenant(UUID id, String slug, String name) {
        static MeTenant of(StaffPrincipal s) {
            return new MeTenant(s.tenantId(), s.tenantSlug(), s.tenantName());
        }
    }

    record MeRole(String code, String name) {
        static MeRole of(StaffPrincipal s) {
            return new MeRole(s.roleCode(), s.roleName());
        }
    }

    record MeBranch(UUID id, String name) {
    }

    record MeResponse(UUID userId, String fullName, String phone, String email, MeTenant tenant, MeRole role,
                      DataScope dataScope, List<String> permissions, MeBranch branch) {
        static MeResponse from(StaffPrincipal s) {
            return new MeResponse(s.userId(), s.fullName(), s.phoneE164(), s.email(), MeTenant.of(s), MeRole.of(s),
                    s.dataScope(), s.permissions().stream().map(Enum::name).sorted().toList(),
                    s.branchId() == null ? null : new MeBranch(s.branchId(), s.branchName()));
        }
    }

    record MembershipResponse(UUID userId, MeTenant tenant, MeRole role) {
        static MembershipResponse from(StaffPrincipal s) {
            return new MembershipResponse(s.userId(), MeTenant.of(s), MeRole.of(s));
        }
    }
}
