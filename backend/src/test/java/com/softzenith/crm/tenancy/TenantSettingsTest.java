package com.softzenith.crm.tenancy;

import com.softzenith.crm.shared.tenant.FeatureGate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-tenant settings: safe defaults for anything missing, and the JSON form stored in tenants.settings. */
class TenantSettingsTest {

    private final TenantSettingsConverter converter = new TenantSettingsConverter();

    @Test
    void anEmptyTenantGetsPlatformDefaults() {
        var s = TenantSettings.defaults();
        assertThat(s.leadNumberPrefix()).isEqualTo("LD");
        assertThat(s.enquiryForm().serviceInterests()).isEmpty();
        assertThat(s.enquiryForm().countries()).isEmpty();
        assertThat(s.notifications().senderName()).isNull();
        assertThat(s.notifications().welcomeEmail()).isTrue();
        assertThat(s.notifications().welcomeWhatsApp()).isTrue();
        assertThat(s.notifications().studentUpdates()).isTrue();
        assertThat(s.enabledFeatures()).containsExactlyInAnyOrder(FeatureGate.LEADS_CORE, FeatureGate.TEAM_AND_STUDENTS);
    }

    @Test
    void aBlankPrefixFallsBackToLd() {
        assertThat(new TenantSettings("  ", null, null, null).leadNumberPrefix()).isEqualTo("LD");
        assertThat(new TenantSettings("WWV", null, null, null).leadNumberPrefix()).isEqualTo("WWV");
    }

    @Test
    void switchedOffNotificationsStayOff() {
        var n = new TenantSettings.Notifications("Western World", false, false, false);
        assertThat(n.welcomeEmail()).isFalse();
        assertThat(n.welcomeWhatsApp()).isFalse();
        assertThat(n.studentUpdates()).isFalse();
    }

    @Test
    void anExplicitEmptyFeatureSetMeansNoFeatures() {
        assertThat(new TenantSettings(null, null, null, Set.of()).enabledFeatures()).isEmpty();
    }

    @Test
    void settingsRoundTripThroughJson() {
        var settings = new TenantSettings("WWV", new TenantSettings.EnquiryForm(List.of("Study Abroad"), List.of("Canada")),
                new TenantSettings.Notifications("Western World", true, false, true), Set.of(FeatureGate.LEADS_CORE));
        var back = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(settings));
        assertThat(back).isEqualTo(settings);
    }

    @Test
    void aLeadSheetRoundTripsAndIsOffUntilItHasAnAddress() {
        var sheet = new TenantSettings.LeadSheet("https://script.google.com/macros/s/abc/exec", "s3cret");
        var settings = new TenantSettings("WWV", null, null, null, sheet);
        var back = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(settings));
        assertThat(back.leadSheet()).isEqualTo(sheet);
        assertThat(back.leadSheet().enabled()).isTrue();
        assertThat(new TenantSettings.LeadSheet(null, "s").enabled()).isFalse();
        assertThat(new TenantSettings.LeadSheet("  ", "s").enabled()).isFalse();
        assertThat(TenantSettings.defaults().leadSheet()).isNull();
        // A tenant saved before lead sheets existed has no leadSheet key.
        assertThat(converter.convertToEntityAttribute("{\"leadNumberPrefix\":\"WWV\"}").leadSheet()).isNull();
    }

    @Test
    void aLeadSheetNeverPrintsItsSecret() {
        assertThat(new TenantSettings.LeadSheet("https://x.test/exec", "s3cret").toString())
                .doesNotContain("s3cret").contains("secret=***");
        assertThat(new TenantSettings.LeadSheet("https://x.test/exec", null).toString()).contains("secret=none");
    }

    @Test
    void missingOrOldJsonReadsAsDefaults() {
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(TenantSettings.defaults());
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(TenantSettings.defaults());
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo(converter.convertToDatabaseColumn(TenantSettings.defaults()));
        // A tenant saved before feature gates existed has no enabledFeatures key.
        assertThat(converter.convertToEntityAttribute("{\"leadNumberPrefix\":\"WWV\"}").enabledFeatures())
                .containsExactlyInAnyOrder(FeatureGate.LEADS_CORE, FeatureGate.TEAM_AND_STUDENTS);
    }
}
