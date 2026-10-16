package com.softzenith.crm.onboarding;

import com.softzenith.crm.onboarding.OnboardingPlan.OnboardingProblem;
import com.softzenith.crm.onboarding.TenantBlueprint.OnboardingBranch;
import com.softzenith.crm.onboarding.TenantBlueprint.OnboardingBusiness;
import com.softzenith.crm.onboarding.TenantBlueprint.OnboardingSettings;
import com.softzenith.crm.onboarding.TenantBlueprint.OnboardingStaff;
import com.softzenith.crm.shared.tenant.FeatureGate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every rule of the onboarding form, without Spring or a database. */
class BlueprintValidatorTest {

    static final List<OnboardingStaff> ADMIN_ONLY = List.of(new OnboardingStaff("A", "9876543210", null, "Admin", null, null, null, null));

    static OnboardingStaff person(String name, String phone, String role, String branch) {
        return new OnboardingStaff(name, phone, null, role, branch, null, null, null);
    }

    static TenantBlueprint valid() {
        return new TenantBlueprint(
                new OnboardingBusiness("Western World Visa Services", "westernworld", "IN", "Asia/Kolkata"),
                new OnboardingSettings("wwv", List.of("Study Visa", " IELTS Coaching ", "study visa"), List.of("Canada"), null, true, true, true, null),
                List.of(new OnboardingBranch("Rohtak", "Rohtak"), new OnboardingBranch("Hisar", null)),
                List.of(new OnboardingStaff("Anuj", "98765 43210", "Anuj@WesternWorld.test", "Admin", "rohtak", "WW-1", "Director", null),
                        person("Priya", "9876543211", "COUNSELLOR", "Hisar")));
    }

    private static List<String> fields(TenantBlueprint bp) {
        return BlueprintValidator.validate(bp).problems().stream().map(OnboardingProblem::field).toList();
    }

    @Test
    void aCompleteBlueprintIsNormalised() {
        var v = BlueprintValidator.validate(valid());

        assertThat(v.problems()).isEmpty();
        assertThat(v.settings().leadNumberPrefix()).isEqualTo("WWV");
        assertThat(v.settings().enquiryForm().serviceInterests()).containsExactly("Study Visa", "IELTS Coaching"); // trimmed, de-duplicated
        assertThat(v.settings().notifications().senderName()).isEqualTo("Western World Visa Services"); // defaults to the name
        assertThat(v.settings().enabledFeatures()).containsExactlyInAnyOrder(FeatureGate.LEADS_CORE, FeatureGate.TEAM_AND_STUDENTS);
        var anuj = v.staff().getFirst();
        assertThat(anuj.phoneE164()).isEqualTo("+919876543210");
        assertThat(anuj.email()).isEqualTo("anuj@westernworld.test");
        assertThat(anuj.roleCode()).isEqualTo("ADMIN");
        assertThat(anuj.branchName()).isEqualTo("Rohtak"); // matched ignoring case, spelled as in the branch list
        assertThat(v.staff().get(1).roleCode()).isEqualTo("COUNSELLOR");
    }

    @Test
    void anEmptyBlueprintListsWhatIsMissing() {
        assertThat(fields(null)).containsExactlyInAnyOrder("business.name", "business.slug", "staff");
    }

    @Test
    void theWebAddressFollowsTheDatabaseRuleAndAvoidsReservedWords() {
        for (var bad : List.of("Western World", "-ww", "ww-", "a", "platform")) {
            var bp = new TenantBlueprint(new OnboardingBusiness("W", bad, null, null), null, null, ADMIN_ONLY);
            assertThat(fields(bp)).as(bad).containsExactly("business.slug");
        }
    }

    @Test
    void regionAndTimezoneDefaultToIndiaAndMustExist() {
        var ok = BlueprintValidator.validate(new TenantBlueprint(new OnboardingBusiness("W", "ww", null, null), null, null, ADMIN_ONLY));
        assertThat(ok.defaultRegion()).isEqualTo("IN");
        assertThat(ok.timezone()).isEqualTo("Asia/Kolkata");

        var bad = new TenantBlueprint(new OnboardingBusiness("W", "ww", "XX", "Mars/Base"), null, null, List.of());
        assertThat(fields(bad)).contains("business.defaultRegion", "business.timezone");
    }

    @Test
    void settingsAreChecked() {
        var bp = new TenantBlueprint(valid().business(),
                new OnboardingSettings("1ABC", null, null, "x".repeat(101), null, null, null, Set.of("TEAM_AND_STUDENTS", "TELEPORT")),
                null, ADMIN_ONLY);
        assertThat(fields(bp)).containsExactlyInAnyOrder(
                "settings.leadNumberPrefix", "settings.senderName", "settings.features", "settings.features");
    }

    @Test
    void branchesNeedUniqueNames() {
        var bp = new TenantBlueprint(valid().business(), null,
                List.of(new OnboardingBranch("Rohtak", null), new OnboardingBranch("ROHTAK", null), new OnboardingBranch(" ", null)),
                List.of(person("A", "9876543210", "Admin", null)));
        assertThat(fields(bp)).containsExactlyInAnyOrder("branches[1].name", "branches[2].name");
    }

    @Test
    void staffRowsAreCheckedOneByOne() {
        var bp = new TenantBlueprint(valid().business(), null, List.of(new OnboardingBranch("Rohtak", null)), List.of(
                person("Anuj", "9876543210", "Admin", "Rohtak"),
                person("Dup Phone", "+91 98765 43210", "Counsellor", null),
                person("Bad Phone", "12", "Counsellor", null),
                person("", null, "Janitor", "Nowhere"),
                new OnboardingStaff("Mail", "9876543212", "not-an-email", "Receptionist", null, null, null, null)));

        assertThat(fields(bp)).containsExactlyInAnyOrder(
                "staff[1].phone", "staff[2].phone", "staff[3].fullName", "staff[3].phone", "staff[3].role", "staff[3].branch",
                "staff[4].email");
    }

    @Test
    void thereMustBeAnAdminAndTheBlueprintWarnsAboutLikelyMistakes() {
        var noAdmin = new TenantBlueprint(valid().business(), null, null, List.of(person("C", "9876543210", "Counsellor", null)));
        assertThat(fields(noAdmin)).containsExactly("staff");

        var v = BlueprintValidator.validate(new TenantBlueprint(valid().business(), null, null, List.of(
                person("Boss", "9876543210", "Admin", null), person("Manager", "9876543211", "Branch Manager", null))));
        assertThat(v.problems()).isEmpty();
        assertThat(v.warnings()).anyMatch(w -> w.contains("Manager is a Branch Manager without a branch"))
                .anyMatch(w -> w.contains("nobody will get new-enquiry alerts"));
    }

    @Test
    void signinCodesAreCheckedAndAnAdminWithoutOneIsFlagged() {
        var admin = new OnboardingStaff("Boss", "9876543210", null, "Admin", null, null, null, null, " 739164 ");
        var weak = new OnboardingStaff("Weak", "9876543211", null, "Counsellor", null, null, null, null, "123456");
        var v = BlueprintValidator.validate(new TenantBlueprint(valid().business(), null, null, List.of(admin, weak)));
        assertThat(v.problems()).extracting(OnboardingProblem::field).containsExactly("staff[1].signinCode");
        assertThat(v.staff().getFirst().signinCode()).isEqualTo("739164"); // trimmed
        assertThat(v.staff().getFirst().toString()).doesNotContain("739164");

        var noCode = BlueprintValidator.validate(new TenantBlueprint(valid().business(), null, null, ADMIN_ONLY));
        assertThat(noCode.problems()).isEmpty();
        assertThat(noCode.warnings()).anyMatch(w -> w.contains("No Admin has a sign-in code"));
        assertThat(BlueprintValidator.validate(new TenantBlueprint(valid().business(), null, null, List.of(admin))).warnings())
                .noneMatch(w -> w.contains("sign-in code"));
    }

    @Test
    void withoutSettingsThePlatformDefaultsApplyAndEmailsComeFromTheBusinessName() {
        var v = BlueprintValidator.validate(new TenantBlueprint(valid().business(), null, null, ADMIN_ONLY));
        assertThat(v.problems()).isEmpty();
        assertThat(v.settings().leadNumberPrefix()).isEqualTo("LD");
        assertThat(v.settings().notifications().senderName()).isEqualTo("Western World Visa Services");
        assertThat(v.settings().notifications().welcomeEmail()).isTrue();
    }

    @Test
    void formOptionsAreLimitedInLengthAndNumber() {
        var tooMany = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "Option " + i).toList();
        var bp = new TenantBlueprint(valid().business(),
                new OnboardingSettings(null, List.of("x".repeat(101)), tooMany, null, null, null, null, null), null, ADMIN_ONLY);
        assertThat(fields(bp)).containsExactlyInAnyOrder("settings.serviceInterests", "settings.countries");
    }

    @Test
    void emptyRowsAreReportedNotSkipped() {
        var branches = new java.util.ArrayList<OnboardingBranch>();
        branches.add(null);
        var staff = new java.util.ArrayList<OnboardingStaff>(ADMIN_ONLY);
        staff.add(null);
        assertThat(fields(new TenantBlueprint(valid().business(), null, branches, staff)))
                .containsExactlyInAnyOrder("branches[0].name", "staff[1].fullName", "staff[1].phone", "staff[1].role");
    }

    @Test
    void longTextIsRefused() {
        var bp = new TenantBlueprint(valid().business(), null, List.of(new OnboardingBranch("Rohtak", "c".repeat(101))), List.of(
                new OnboardingStaff("A", "9876543210", null, "Admin", null, "e".repeat(51), "d".repeat(101), null)));
        assertThat(fields(bp)).containsExactlyInAnyOrder("branches[0].city", "staff[0].employeeCode", "staff[0].designation");
    }

    @Test
    void duplicateEmailsAndEmployeeCodesAreRefused() {
        var bp = new TenantBlueprint(valid().business(), null, null, List.of(
                new OnboardingStaff("A", "9876543210", "same@ww.test", "Admin", null, "E1", null, null),
                new OnboardingStaff("B", "9876543211", "SAME@ww.test", "Counsellor", null, "E1", null, null)));
        assertThat(fields(bp)).containsExactlyInAnyOrder("staff[1].email", "staff[1].employeeCode");
    }
}
