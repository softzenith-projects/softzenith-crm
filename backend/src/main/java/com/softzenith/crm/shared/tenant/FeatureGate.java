package com.softzenith.crm.shared.tenant;

/**
 * A subscription-plan entitlement, one per PRD phase. Orthogonal to {@code Permission}: a permission says who
 * within a tenant may act, a feature gate says whether the tenant's plan includes the feature at all.
 */
public enum FeatureGate {
    /** Phase 1: multi-source lead intake/dedupe, lead list/detail/activity, notifications, status, lead dashboard. Foundational. */
    LEADS_CORE,
    /**
     * Phase 2: branches, Branch Manager/Counsellor/Receptionist roles + data scope, assignment, staff admin.
     * Without it the business is run by its Admins alone: staff, roles and assignment are refused and hidden.
     */
    TEAM_AND_STUDENTS,
    /** Phase 3: remarks/tasks. Reserved; nothing built yet to enforce. */
    ENGAGEMENT,
    /** Phase 4: documents/applications/visa. Reserved; nothing built yet to enforce. */
    APPLICATIONS_DOCS,
    /** Phase 5: automation/reporting. Reserved; nothing built yet to enforce. */
    AUTOMATION_REPORTING
}
