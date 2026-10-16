package com.softzenith.crm.devdata;

import com.softzenith.crm.identity.AppUser;
import com.softzenith.crm.identity.AppUserRepository;
import com.softzenith.crm.identity.Branch;
import com.softzenith.crm.identity.BranchRepository;
import com.softzenith.crm.identity.DataScope;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.identity.RoleRepository;
import com.softzenith.crm.identity.StaffAdminService;
import com.softzenith.crm.lead.LeadFilter;
import com.softzenith.crm.lead.LeadService;
import com.softzenith.crm.lead.LeadSourceType;
import com.softzenith.crm.lead.LeadStatus;
import com.softzenith.crm.lead.NewLead;
import com.softzenith.crm.onboarding.TenantOnboardingService;
import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantRepository;
import com.softzenith.crm.tenancy.TenantSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev profile only: a neutral demo tenant, "Demo Visas" (slug {@code demo}), with the owner's mock organisation (26 Sep 2026)
 * and mock leads. Real tenants (Western World first) are created through the platform onboarding flow, never seeded.
 *
 * <p>Branches Rohtak, Bahadurgarh, Rohini. Anuj = Admin ("Super Admin", Rohtak); Naman = Branch Manager, Rohini;
 * Deepak = Branch Manager, Bahadurgarh; Natasha, Indu, Priya = Counsellors of Rohtak, Rohini, Bahadurgarh. Admin and the
 * Branch Managers can also be assigned leads, so each branch has two people who counsel. A receptionist covers the
 * front desk. Sign in with the phone numbers below (dev login, no OTP), or with phone + code {@value #SIGNIN_CODE}.
 *
 * <p>Additive and idempotent, so it also brings an older local database up to date: branches are created by name
 * (others are deactivated), staff are matched by phone, and the mock leads are added once.
 */
@Component
@ConditionalOnProperty("crm.dev-data.enabled")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String SLUG = "demo";
    /** Every demo staff member's sign-in code (phone + code login); dev login needs none. */
    static final String SIGNIN_CODE = "482917";
    private static final List<String> BRANCHES = List.of("Rohtak", "Bahadurgarh", "Rohini");

    private record Staff(String name, String phone, String role, String branch) {
    }

    private static final List<Staff> STAFF = List.of(
            new Staff("Anuj", "9000000001", DefaultRoles.ADMIN, "Rohtak"),
            new Staff("Naman", "9000000002", DefaultRoles.BRANCH_MANAGER, "Rohini"),
            new Staff("Natasha", "9000000003", DefaultRoles.COUNSELLOR, "Rohtak"),
            new Staff("Front Desk", "9000000004", DefaultRoles.RECEPTIONIST, "Rohtak"),
            new Staff("Deepak", "9000000005", DefaultRoles.BRANCH_MANAGER, "Bahadurgarh"),
            new Staff("Indu", "9000000006", DefaultRoles.COUNSELLOR, "Rohini"),
            new Staff("Priya", "9000000007", DefaultRoles.COUNSELLOR, "Bahadurgarh"));

    /** assignee = staff first name or null; branch null = website enquiry without a branch. */
    private record MockLead(String name, String phone, LeadSourceType source, String service, String country,
                            String branch, String assignee, LeadStatus status) {
    }

    private static final List<MockLead> LEADS = List.of(
            new MockLead("Aarav Sharma", "9810000001", LeadSourceType.WEBSITE_FORM, "Study Abroad", "Canada", "Rohtak", "Natasha", LeadStatus.CONTACTED),
            new MockLead("Simran Kaur", "9810000002", LeadSourceType.WALK_IN, "Student Visa", "United Kingdom", "Rohtak", "Anuj", LeadStatus.ASSIGNED),
            new MockLead("Rohit Malik", "9810000003", LeadSourceType.PHONE, "IELTS / PTE Coaching", null, "Rohtak", null, LeadStatus.NEW),
            new MockLead("Kavya Jain", "9810000004", LeadSourceType.REFERRAL, "Study Abroad", "Australia", "Rohtak", "Natasha", LeadStatus.CLOSED),
            new MockLead("Mohit Dahiya", "9810000005", LeadSourceType.WEBSITE_FORM, "Study Abroad", "Germany", "Rohtak", "Natasha", LeadStatus.ASSIGNED),
            new MockLead("Priyanka Verma", "9810000006", LeadSourceType.WEBSITE_FORM, "Student Visa", "Canada", "Rohini", "Indu", LeadStatus.ASSIGNED),
            new MockLead("Arjun Mehta", "9810000007", LeadSourceType.WALK_IN, "Study Abroad", "USA", "Rohini", "Naman", LeadStatus.CONTACTED),
            new MockLead("Sneha Gupta", "9810000008", LeadSourceType.PHONE, "Visitor Visa", "Australia", "Rohini", null, LeadStatus.NEW),
            new MockLead("Kunal Arora", "9810000009", LeadSourceType.REFERRAL, "Study Abroad", "Ireland", "Rohini", "Indu", LeadStatus.CONTACTED),
            new MockLead("Vikas Rathi", "9810000010", LeadSourceType.WALK_IN, "Student Visa", "New Zealand", "Bahadurgarh", "Deepak", LeadStatus.ASSIGNED),
            new MockLead("Pooja Yadav", "9810000011", LeadSourceType.WEBSITE_FORM, "Study Abroad", "Canada", "Bahadurgarh", "Priya", LeadStatus.CONTACTED),
            new MockLead("Neha Saini", "9810000012", LeadSourceType.PHONE, "IELTS / PTE Coaching", null, "Bahadurgarh", null, LeadStatus.NEW),
            new MockLead("Aman Chauhan", "9810000013", LeadSourceType.WEBSITE_FORM, "Student Visa", "United Kingdom", "Bahadurgarh", "Priya", LeadStatus.ASSIGNED),
            new MockLead("Ritika Bansal", "9810000014", LeadSourceType.WEBSITE_FORM, "Study Abroad", "Canada", null, null, LeadStatus.NEW),
            new MockLead("Harsh Vardhan", "9810000015", LeadSourceType.WEBSITE_FORM, "Study Abroad", "Australia", null, "Natasha", LeadStatus.ASSIGNED));

    private final TenantRepository tenants;
    private final TenantOnboardingService onboarding;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final AppUserRepository users;
    private final LeadService leads;
    private final TransactionTemplate tx;
    private final StaffAdminService staffAdmin;

    DevDataSeeder(TenantRepository tenants, TenantOnboardingService onboarding, RoleRepository roles,
                  BranchRepository branches, AppUserRepository users, LeadService leads, TransactionTemplate tx,
                  StaffAdminService staffAdmin) {
        this.tenants = tenants;
        this.onboarding = onboarding;
        this.roles = roles;
        this.branches = branches;
        this.users = users;
        this.leads = leads;
        this.tx = tx;
        this.staffAdmin = staffAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        var tenant = tenants.findBySlug(SLUG).orElseGet(this::onboardTenant);
        TenantContext.run(tenant.getId(), () -> {
            var branchIds = tx.execute(status -> organisation(tenant));
            seedLeads(branchIds);
        });
    }

    private Tenant onboardTenant() {
        var tenant = onboarding.onboard(SLUG, "Demo Visas", "IN", "Asia/Kolkata");
        // Tenant-specific behaviour is data, not code: this is all the demo tenant needs beyond the platform defaults.
        // Demo values until the client confirms the final form fields (PRD Phase 1).
        tx.executeWithoutResult(status -> tenants.findById(tenant.getId()).orElseThrow().updateSettings(new TenantSettings("DEMO",
                new TenantSettings.EnquiryForm(
                        List.of("Study Abroad", "Student Visa", "Visitor Visa", "IELTS / PTE Coaching", "Other"),
                        List.of("Canada", "United Kingdom", "Australia", "USA", "Germany", "Ireland", "New Zealand", "Other")),
                new TenantSettings.Notifications("Demo Visas", true, true, true), null)));
        log.info("Seeded dev tenant '{}' ({})", SLUG, tenant.getId());
        return tenant;
    }

    /** Branches, the "Super Admin" label and staff; returns branch ids by name. */
    private Map<String, UUID> organisation(Tenant tenant) {
        var byName = new HashMap<String, Branch>();
        for (var b : branches.findAllByOrderByName()) {
            if (BRANCHES.contains(b.getName())) {
                byName.put(b.getName(), b);
                if (!b.isActive()) b.update(b.getName(), b.getCity(), true);
            } else if (b.isActive()) {
                b.update(b.getName(), b.getCity(), false); // older demo branches ("Main Branch", ...)
            }
        }
        for (var name : BRANCHES) {
            byName.computeIfAbsent(name, n -> branches.save(new Branch(n, n)));
        }

        // The demo tenant calls its administrator "Super Admin", who also takes students (tenant data, not code).
        var admin = roles.findByCode(DefaultRoles.ADMIN).orElseThrow();
        if (!admin.getName().equals("Super Admin") || !admin.isAssignable()) {
            admin.update("Super Admin", admin.getDescription(), DataScope.ALL, true);
        }

        for (var s : STAFF) {
            var phone = PhoneNumbers.toE164(s.phone(), tenant.getDefaultRegion());
            var email = s.name().toLowerCase().replace(' ', '.') + "@demo.test";
            var role = roles.findByCode(s.role()).orElseThrow();
            var branch = byName.get(s.branch());
            var user = users.findByPhoneE164(phone).map(u -> {
                u.updateProfile(s.name(), email, role, branch);
                return u;
            }).orElseGet(() -> users.save(new AppUser(s.name(), phone, email, role, branch)));
            if (!user.hasSigninCode()) {
                staffAdmin.setSigninCode(user.getId(), SIGNIN_CODE);
            }
        }
        users.flush();

        var ids = new HashMap<String, UUID>();
        byName.forEach((name, b) -> ids.put(name, b.getId()));
        return ids;
    }

    /** Once: skipped when the first mock lead's phone is already on a lead. */
    private void seedLeads(Map<String, UUID> branchIds) {
        var marker = new LeadFilter(null, null, null, null, null, LEADS.getFirst().phone(), null, null);
        if (leads.search(marker, Pageable.ofSize(1)).hasContent()) {
            return;
        }
        var staffIds = tx.execute(status -> {
            var ids = new HashMap<String, UUID>();
            users.findAllWithRoleAndBranch().forEach(u -> ids.put(u.getFullName(), u.getId()));
            return ids;
        });
        for (var m : LEADS) {
            var email = m.name().toLowerCase().replace(' ', '.') + "@mail.test";
            var lead = leads.intake(new NewLead(m.name(), m.phone(), email, m.service(), m.country(), null,
                    m.branch() == null ? null : branchIds.get(m.branch()), m.source(), "Mock data", null, null, null, null, null)).lead();
            if (m.assignee() != null) {
                leads.assign(lead.getId(), staffIds.get(m.assignee()));
            }
            if (m.status() == LeadStatus.CONTACTED) {
                leads.changeStatus(lead.getId(), LeadStatus.CONTACTED, null);
            } else if (m.status() == LeadStatus.CLOSED) {
                leads.changeStatus(lead.getId(), LeadStatus.CLOSED, "Budget too low for now");
            }
        }
        log.info("Seeded {} mock leads", LEADS.size());
    }
}
