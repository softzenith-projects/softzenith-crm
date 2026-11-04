package com.softzenith.crm.identity;

import com.softzenith.crm.shared.web.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Staff account lifecycle (INVITED → ACTIVE → DISABLED) and sign-in identity rules, without Spring. */
class AppUserTest {

    private final Role counsellor = new Role(UUID.randomUUID(), "COUNSELLOR", "Counsellor", null, DataScope.OWN, true, true,
            EnumSet.of(Permission.LEAD_VIEW, Permission.LEAD_CHANGE_STATUS));

    @Test
    void aNewStaffMemberIsInvitedUntilTheyFirstSignIn() {
        var user = user();
        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(user.isActive()).isFalse();

        user.linkAuthSubject("supabase-1");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getAuthSubject()).isEqualTo("supabase-1");
    }

    @Test
    void signingInDoesNotReviveADisabledAccount() {
        var user = user();
        user.disable();
        user.linkAuthSubject("supabase-1");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void aCorrectSigninCodeActivatesAnInvitedMemberButNeverADisabledOne() {
        var user = user();
        assertThat(user.hasSigninCode()).isFalse();
        user.setSigninCode("{bcrypt}hash", java.time.Instant.now());
        assertThat(user.hasSigninCode()).isTrue();

        user.signedInWithCode();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        var disabled = user();
        disabled.disable();
        disabled.signedInWithCode();
        assertThat(disabled.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void permissionsOnlyApplyWhileActive() {
        var user = user();
        assertThat(user.can(Permission.LEAD_VIEW)).isFalse(); // invited
        user.linkAuthSubject("s");
        assertThat(user.can(Permission.LEAD_VIEW)).isTrue();
        assertThat(user.can(Permission.LEAD_ASSIGN)).isFalse(); // not in the role
        user.disable();
        assertThat(user.can(Permission.LEAD_VIEW)).isFalse();
    }

    @Test
    void enablingRestoresInvitedOrActiveDependingOnSignIn() {
        var neverSignedIn = user();
        neverSignedIn.disable();
        neverSignedIn.enable();
        assertThat(neverSignedIn.getStatus()).isEqualTo(UserStatus.INVITED);

        var signedIn = user();
        signedIn.linkAuthSubject("s");
        signedIn.disable();
        signedIn.enable();
        assertThat(signedIn.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void thePhoneCanOnlyChangeBeforeTheFirstSignIn() {
        var user = user();
        user.changePhone("+919000000099");
        assertThat(user.getPhoneE164()).isEqualTo("+919000000099");

        user.linkAuthSubject("s");
        assertThatThrownBy(() -> user.changePhone("+919000000098")).isInstanceOf(ConflictException.class);
    }

    @Test
    void resettingSignInUnlinksTheIdentityAndMakesThePhoneEditableAgain() {
        var user = user();
        user.linkAuthSubject("s");
        user.resetSignIn();
        assertThat(user.getAuthSubject()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED);
        user.changePhone("+919000000097");
        assertThat(user.getPhoneE164()).isEqualTo("+919000000097");
    }

    @Test
    void resettingADisabledUserKeepsThemDisabled() {
        var user = user();
        user.linkAuthSubject("s");
        user.disable();
        user.resetSignIn();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void profileAndEmployeeRecordAreUpdated() {
        var user = user();
        var branch = new Branch("Rohini", "Delhi");
        user.updateProfile("Natasha K", "natasha@acme.test", counsellor, branch);
        user.updateEmployeeRecord("EMP-1", "Senior Counsellor", LocalDate.of(2026, 9, 1));

        assertThat(user.getFullName()).isEqualTo("Natasha K");
        assertThat(user.getEmail()).isEqualTo("natasha@acme.test");
        assertThat(user.getBranch()).isSameAs(branch);
        assertThat(user.getEmployeeCode()).isEqualTo("EMP-1");
        assertThat(user.getDesignation()).isEqualTo("Senior Counsellor");
        assertThat(user.getJoinedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    private AppUser user() {
        return new AppUser("Natasha", "+919000000003", null, counsellor, null);
    }
}
