package com.softzenith.crm.onboarding;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.softzenith.crm.identity.DefaultRoles;
import com.softzenith.crm.identity.OrganisationProvisioner.NewBranch;
import com.softzenith.crm.identity.OrganisationProvisioner.NewStaff;
import com.softzenith.crm.identity.SigninCodes;
import com.softzenith.crm.onboarding.OnboardingPlan.OnboardingProblem;
import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.web.InvalidInputException;
import com.softzenith.crm.tenancy.TenantSettings;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a {@link TenantBlueprint} and normalises it (trimmed text, E.164 phones, role codes). Reports every problem at
 * once, each with the field it belongs to, so the form can show them all next to the right inputs.
 */
final class BlueprintValidator {

    /** Same rule as the tenants.slug check constraint (V1). */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$");
    /** Words that would read as part of the product rather than a business. */
    private static final Set<String> RESERVED_SLUGS = Set.of("platform", "admin", "api", "www", "app", "login");
    private static final Pattern PREFIX = Pattern.compile("^[A-Z][A-Z0-9]{0,9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> REGIONS = PhoneNumberUtil.getInstance().getSupportedRegions();

    /** The blueprint, checked and normalised; ready to create when {@code problems} is empty. */
    record Validated(String name, String slug, String defaultRegion, String timezone, TenantSettings settings,
                     List<NewBranch> branches, List<NewStaff> staff, List<OnboardingProblem> problems, List<String> warnings) {
    }

    private final List<OnboardingProblem> problems = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    static Validated validate(TenantBlueprint blueprint) {
        return new BlueprintValidator().run(blueprint == null ? new TenantBlueprint(null, null, null, null) : blueprint);
    }

    private Validated run(TenantBlueprint bp) {
        var business = bp.business() == null ? new TenantBlueprint.OnboardingBusiness(null, null, null, null) : bp.business();
        var name = required(business.name(), "business.name", "Business name", 200);
        var slug = slug(business.slug());
        var region = region(business.defaultRegion());
        var timezone = timezone(business.timezone());
        var settings = settings(bp.settings(), name);
        var branches = branches(bp.branches());
        var staff = staff(bp.staff(), region, branches);
        return new Validated(name, slug, region, timezone, settings, branches, staff, List.copyOf(problems), List.copyOf(warnings));
    }

    private String slug(String raw) {
        var slug = trim(raw);
        if (slug == null) {
            problem("business.slug", "Web address is required, e.g. westernworld");
        } else if (!SLUG.matcher(slug).matches()) {
            problem("business.slug", "Web address: 3–50 lower-case letters, digits or '-', not starting or ending with '-'");
        } else if (RESERVED_SLUGS.contains(slug)) {
            problem("business.slug", "'" + slug + "' is reserved; choose another web address");
        }
        return slug;
    }

    private String region(String raw) {
        var region = trim(raw) == null ? "IN" : trim(raw).toUpperCase(Locale.ROOT);
        if (!REGIONS.contains(region)) {
            problem("business.defaultRegion", "Unknown country code '" + region + "' (use a 2-letter code such as IN)");
        }
        return region;
    }

    private String timezone(String raw) {
        var zone = trim(raw) == null ? "Asia/Kolkata" : trim(raw);
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            problem("business.timezone", "Unknown time zone '" + zone + "' (e.g. Asia/Kolkata)");
        }
        return zone;
    }

    private TenantSettings settings(TenantBlueprint.OnboardingSettings s, String businessName) {
        if (s == null) {
            return new TenantSettings(null, null, new TenantSettings.Notifications(businessName, null, null, null), null);
        }
        var prefix = trim(s.leadNumberPrefix()) == null ? null : trim(s.leadNumberPrefix()).toUpperCase(Locale.ROOT);
        if (prefix != null && !PREFIX.matcher(prefix).matches()) {
            problem("settings.leadNumberPrefix", "Lead number prefix: 1–10 capital letters or digits, starting with a letter");
        }
        var services = options(s.serviceInterests(), "settings.serviceInterests", "Services");
        var countries = options(s.countries(), "settings.countries", "Countries");
        var sender = trim(s.senderName()) == null ? businessName : trim(s.senderName());
        if (sender != null && sender.length() > 100) {
            problem("settings.senderName", "Email sender name: at most 100 characters");
        }
        Set<FeatureGate> features = null;
        if (s.features() != null) {
            features = EnumSet.noneOf(FeatureGate.class);
            for (var f : s.features()) {
                try {
                    features.add(FeatureGate.valueOf(f));
                } catch (IllegalArgumentException | NullPointerException e) {
                    problem("settings.features", "Unknown feature '" + f + "'");
                }
            }
            if (!features.contains(FeatureGate.LEADS_CORE)) {
                problem("settings.features", "Lead capture (LEADS_CORE) is required: without it the tenant cannot receive enquiries");
            }
        }
        return new TenantSettings(prefix, new TenantSettings.EnquiryForm(services, countries),
                new TenantSettings.Notifications(sender, s.welcomeEmail(), s.welcomeWhatsApp(), s.studentUpdates()), features);
    }

    private List<String> options(List<String> raw, String field, String label) {
        var seen = new LinkedHashSet<String>();
        var lower = new HashSet<String>();
        for (var v : raw == null ? List.<String>of() : raw) {
            var option = trim(v);
            if (option == null) continue;
            if (option.length() > 100) problem(field, label + ": '" + option.substring(0, 20) + "…' is longer than 100 characters");
            if (lower.add(option.toLowerCase(Locale.ROOT))) seen.add(option);
        }
        if (seen.size() > 50) problem(field, label + ": at most 50 options");
        return List.copyOf(seen);
    }

    private List<NewBranch> branches(List<TenantBlueprint.OnboardingBranch> raw) {
        var result = new ArrayList<NewBranch>();
        var names = new HashSet<String>();
        var rows = raw == null ? List.<TenantBlueprint.OnboardingBranch>of() : raw;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i) == null ? new TenantBlueprint.OnboardingBranch(null, null) : rows.get(i);
            var field = "branches[" + i + "]";
            var name = required(row.name(), field + ".name", "Branch name", 200);
            var city = limit(trim(row.city()), field + ".city", "City", 100);
            if (name != null && !names.add(name.toLowerCase(Locale.ROOT))) {
                problem(field + ".name", "Branch '" + name + "' is listed twice");
            }
            result.add(new NewBranch(name, city));
        }
        return result;
    }

    private List<NewStaff> staff(List<TenantBlueprint.OnboardingStaff> raw, String region, List<NewBranch> branches) {
        var rows = raw == null ? List.<TenantBlueprint.OnboardingStaff>of() : raw;
        var branchNames = new HashMap<String, String>();
        branches.stream().filter(b -> b.name() != null).forEach(b -> branchNames.put(b.name().toLowerCase(Locale.ROOT), b.name()));
        var result = new ArrayList<NewStaff>();
        var phones = new HashMap<String, Integer>();
        var emails = new HashMap<String, Integer>();
        var codes = new HashMap<String, Integer>();
        var admins = 0;

        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i) == null ? new TenantBlueprint.OnboardingStaff(null, null, null, null, null, null, null, null, null) : rows.get(i);
            var field = "staff[" + i + "]";
            var fullName = required(row.fullName(), field + ".fullName", "Name", 200);
            var label = fullName == null ? "Row " + (i + 1) : fullName;

            String phone = null;
            if (trim(row.phone()) == null) {
                problem(field + ".phone", label + ": mobile number is required (it is how they sign in)");
            } else {
                try {
                    phone = PhoneNumbers.toE164(row.phone(), region);
                    var first = phones.putIfAbsent(phone, i);
                    if (first != null) problem(field + ".phone", label + ": same mobile number as row " + (first + 1));
                } catch (InvalidInputException e) {
                    problem(field + ".phone", label + ": '" + row.phone() + "' is not a valid mobile number");
                }
            }

            var email = trim(row.email()) == null ? null : trim(row.email()).toLowerCase(Locale.ROOT);
            if (email != null) {
                if (!EMAIL.matcher(email).matches() || email.length() > 200) {
                    problem(field + ".email", label + ": '" + email + "' is not a valid email");
                } else {
                    var first = emails.putIfAbsent(email, i);
                    if (first != null) problem(field + ".email", label + ": same email as row " + (first + 1));
                }
            }

            var role = role(row.role());
            if (role == null) {
                problem(field + ".role", label + ": role must be Admin, Branch Manager, Counsellor or Receptionist");
            } else if (role.equals(DefaultRoles.ADMIN)) {
                admins++;
            }

            String branch = null;
            if (trim(row.branch()) != null) {
                branch = branchNames.get(trim(row.branch()).toLowerCase(Locale.ROOT));
                if (branch == null) problem(field + ".branch", label + ": branch '" + trim(row.branch()) + "' is not in the branch list");
            } else if (DefaultRoles.BRANCH_MANAGER.equals(role)) {
                warnings.add(label + " is a Branch Manager without a branch, so will only see leads assigned to them.");
            }

            var code = limit(trim(row.employeeCode()), field + ".employeeCode", "Employee code", 50);
            if (code != null) {
                var first = codes.putIfAbsent(code, i);
                if (first != null) problem(field + ".employeeCode", label + ": same employee code as row " + (first + 1));
            }
            var designation = limit(trim(row.designation()), field + ".designation", "Designation", 100);
            var signinCode = trim(row.signinCode());
            if (signinCode != null) {
                SigninCodes.problem(signinCode).ifPresent(p -> problem(field + ".signinCode", label + ": " + p));
            }
            result.add(new NewStaff(fullName, phone, email, role, branch, code, designation, row.joinedOn(), signinCode));
        }

        if (admins == 0) {
            problem("staff", "Add at least one Admin: they manage staff, branches and settings after going live");
        }
        if (admins > 0 && result.stream().noneMatch(s -> DefaultRoles.ADMIN.equals(s.roleCode()) && s.signinCode() != null)) {
            warnings.add("No Admin has a sign-in code, so nobody can sign in with mobile + code until one is set.");
        }
        if (result.stream().noneMatch(s -> s.email() != null && (DefaultRoles.ADMIN.equals(s.roleCode())
                || DefaultRoles.RECEPTIONIST.equals(s.roleCode())))) {
            warnings.add("No Admin or Receptionist has an email address, so nobody will get new-enquiry alerts.");
        }
        return result;
    }

    /** A default role by code (BRANCH_MANAGER) or name (Branch Manager), any case; null if unknown. */
    private static String role(String raw) {
        var value = trim(raw);
        if (value == null) return null;
        var key = value.toUpperCase(Locale.ROOT).replace(' ', '_');
        return DefaultRoles.TEMPLATES.stream()
                .filter(t -> t.code().equals(key) || t.name().equalsIgnoreCase(value))
                .map(DefaultRoles.Template::code).findFirst().orElse(null);
    }

    private String required(String raw, String field, String label, int max) {
        var value = trim(raw);
        if (value == null) {
            problem(field, label + " is required");
            return null;
        }
        return limit(value, field, label, max);
    }

    private String limit(String value, String field, String label, int max) {
        if (value != null && value.length() > max) problem(field, label + ": at most " + max + " characters");
        return value;
    }

    private void problem(String field, String message) {
        problems.add(new OnboardingProblem(field, message));
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static Map<String, String> roleNames() {
        var names = new java.util.LinkedHashMap<String, String>();
        DefaultRoles.TEMPLATES.forEach(t -> names.put(t.code(), t.name()));
        return names;
    }
}
