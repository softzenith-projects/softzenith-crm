package com.softzenith.crm.tenancy;

import com.softzenith.crm.shared.tenant.FeatureGate;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Per-tenant configuration, stored as JSON in {@code tenants.settings}. Everything a business customises
 * without code changes lives here; missing values fall back to platform defaults, so a new tenant works
 * with an empty object.
 */
public record TenantSettings(String leadNumberPrefix, EnquiryForm enquiryForm, Notifications notifications,
                             Set<FeatureGate> enabledFeatures, LeadSheet leadSheet) {

    /** What is actually built today (Phase 1 + 2); a tenant onboarded before feature gates existed gets the same. */
    private static final Set<FeatureGate> DEFAULT_FEATURES =
            Set.copyOf(EnumSet.of(FeatureGate.LEADS_CORE, FeatureGate.TEAM_AND_STUDENTS));

    public TenantSettings {
        leadNumberPrefix = leadNumberPrefix == null || leadNumberPrefix.isBlank() ? "LD" : leadNumberPrefix;
        enquiryForm = enquiryForm == null ? new EnquiryForm(null, null) : enquiryForm;
        notifications = notifications == null ? new Notifications(null, null, null, null) : notifications;
        enabledFeatures = enabledFeatures == null ? DEFAULT_FEATURES : Set.copyOf(enabledFeatures);
    }

    /** Settings without a lead sheet (onboarding, dev data). */
    public TenantSettings(String leadNumberPrefix, EnquiryForm enquiryForm, Notifications notifications,
                          Set<FeatureGate> enabledFeatures) {
        this(leadNumberPrefix, enquiryForm, notifications, enabledFeatures, null);
    }

    public static TenantSettings defaults() {
        return new TenantSettings(null, null, null, null);
    }

    /**
     * A Google Sheet that receives a copy of every enquiry (module {@code leadsheet}); null = none. Set by the
     * platform team (deploy/lead-sheet.sh) until the console can edit a live business.
     *
     * @param url    the sheet script's web-app address (https)
     * @param secret shared with the sheet script, which rejects posts without it
     */
    public record LeadSheet(String url, String secret) {

        public boolean enabled() {
            return url != null && !url.isBlank();
        }

        /** Keeps the secret out of logs. */
        @Override
        public String toString() {
            return "LeadSheet[url=" + url + ", secret=" + (secret == null ? "none" : "***") + "]";
        }
    }

    /** Options offered on the public enquiry form. Empty list = free-text field. */
    public record EnquiryForm(List<String> serviceInterests, List<String> countries) {
        public EnquiryForm {
            serviceInterests = serviceInterests == null ? List.of() : List.copyOf(serviceInterests);
            countries = countries == null ? List.of() : List.copyOf(countries);
        }
    }

    /**
     * @param senderName      shown as the email "From" name; defaults to the tenant name
     * @param welcomeEmail    send the enquirer a welcome email (default true)
     * @param welcomeWhatsApp send the enquirer a welcome WhatsApp (default true)
     * @param studentUpdates  tell the enquirer (email + WhatsApp) when their enquiry is assigned, changes status,
     *                        or when they enquire again (default true)
     */
    public record Notifications(String senderName, Boolean welcomeEmail, Boolean welcomeWhatsApp, Boolean studentUpdates) {
        public Notifications {
            welcomeEmail = welcomeEmail == null || welcomeEmail;
            welcomeWhatsApp = welcomeWhatsApp == null || welcomeWhatsApp;
            studentUpdates = studentUpdates == null || studentUpdates;
        }
    }
}
