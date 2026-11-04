package com.softzenith.crm.onboarding;

import com.softzenith.crm.identity.OrganisationProvisioner;
import com.softzenith.crm.identity.RoleProvisioner;
import com.softzenith.crm.onboarding.BlueprintValidator.Validated;
import com.softzenith.crm.onboarding.OnboardingPlan.OnboardingProblem;
import com.softzenith.crm.onboarding.OnboardingPlan.OnboardingStaffLine;
import com.softzenith.crm.shared.logging.Mask;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.web.ConflictException;
import com.softzenith.crm.shared.web.InvalidInputException;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Onboards a new business. {@link #preview} checks a {@link TenantBlueprint} and does a full trial run that is rolled
 * back; {@link #goLive} creates the tenant, its settings, default roles, branches and invited staff in one transaction,
 * so a business is either completely set up or not at all. Nobody is messaged: staff activate on first sign-in.
 */
@Service
public class TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(TenantOnboardingService.class);

    private final TenantRepository tenants;
    private final RoleProvisioner roleProvisioner;
    private final OrganisationProvisioner organisation;
    private final TransactionTemplate tx;

    TenantOnboardingService(TenantRepository tenants, RoleProvisioner roleProvisioner, OrganisationProvisioner organisation,
                            TransactionTemplate tx) {
        this.tenants = tenants;
        this.roleProvisioner = roleProvisioner;
        this.organisation = organisation;
        this.tx = tx;
    }

    public record Onboarded(UUID tenantId, String slug, String name, int branches, int staff) {
    }

    /** A bare tenant with default roles only (dev seed data and tests). Real businesses go through {@link #goLive}. */
    public Tenant onboard(String slug, String name, String defaultRegion, String timezone) {
        // System context: rows for the new tenant are written before any user of it exists.
        return TenantContext.callAsSystem(() -> tx.execute(status -> {
            requireFreeSlug(slug);
            var tenant = tenants.save(new Tenant(slug, name, defaultRegion, timezone));
            roleProvisioner.createDefaultRoles(tenant.getId());
            return tenant;
        }));
    }

    public OnboardingPlan preview(TenantBlueprint blueprint) {
        var v = BlueprintValidator.validate(blueprint);
        if (v.slug() != null && v.problems().stream().noneMatch(p -> "business.slug".equals(p.field())) && slugTaken(v.slug())) {
            return notReady(v, List.of(new OnboardingProblem("business.slug", "Web address '" + v.slug() + "' is already in use")));
        }
        if (!v.problems().isEmpty()) {
            return notReady(v, List.of());
        }
        try {
            // The same code and database constraints as going live, then rolled back: a "ready" preview will go live.
            TenantContext.callAsSystem(() -> tx.execute(status -> {
                create(v);
                tenants.flush();
                status.setRollbackOnly();
                return null;
            }));
        } catch (ConflictException | DataIntegrityViolationException e) {
            log.info("Onboarding preview for '{}' refused by the database: {}", v.slug(), e.getClass().getSimpleName());
            return notReady(v, List.of(new OnboardingProblem(null, "The database refused this setup (for example a duplicate value); check the lists and try again")));
        }
        return new OnboardingPlan(true, List.of(), v.warnings(), summary(v));
    }

    public Onboarded goLive(TenantBlueprint blueprint) {
        var v = BlueprintValidator.validate(blueprint);
        if (!v.problems().isEmpty()) {
            throw new InvalidInputException(v.problems().stream().map(OnboardingProblem::message).collect(Collectors.joining("; ")));
        }
        var tenant = TenantContext.callAsSystem(() -> tx.execute(status -> create(v)));
        log.info("Tenant '{}' ({}) is live: {} branches, {} staff invited (admins: {})", v.slug(), tenant.getId(),
                v.branches().size(), v.staff().size(), v.staff().stream()
                        .filter(s -> "ADMIN".equals(s.roleCode())).map(s -> Mask.phone(s.phoneE164())).toList());
        return new Onboarded(tenant.getId(), v.slug(), v.name(), v.branches().size(), v.staff().size());
    }

    private Tenant create(Validated v) {
        requireFreeSlug(v.slug());
        var tenant = tenants.save(new Tenant(v.slug(), v.name(), v.defaultRegion(), v.timezone()));
        tenant.updateSettings(v.settings());
        organisation.provision(tenant.getId(), v.branches(), v.staff());
        return tenant;
    }

    private void requireFreeSlug(String slug) {
        if (tenants.existsBySlug(slug)) {
            throw new ConflictException("Tenant slug already taken: " + slug);
        }
    }

    private boolean slugTaken(String slug) {
        return TenantContext.callAsSystem(() -> tenants.existsBySlug(slug));
    }

    private static OnboardingPlan notReady(Validated v, List<OnboardingProblem> more) {
        var problems = new java.util.ArrayList<>(v.problems());
        problems.addAll(more);
        return new OnboardingPlan(false, List.copyOf(problems), v.warnings(), null);
    }

    private static OnboardingPlan.OnboardingSummary summary(Validated v) {
        var roleNames = BlueprintValidator.roleNames();
        var settings = v.settings();
        return new OnboardingPlan.OnboardingSummary(v.name(), v.slug(), v.defaultRegion(), v.timezone(),
                settings.leadNumberPrefix() + "-000001", "/enquiry/" + v.slug(),
                List.copyOf(roleNames.values()),
                v.branches().stream().map(b -> b.city() == null ? b.name() : b.name() + " (" + b.city() + ")").toList(),
                v.staff().stream().map(s -> new OnboardingStaffLine(s.fullName(), s.phoneE164(), roleNames.get(s.roleCode()),
                        s.branchName(), s.signinCode() != null)).toList(),
                settings.enabledFeatures().stream().sorted().map(FeatureGate::name).toList());
    }
}
