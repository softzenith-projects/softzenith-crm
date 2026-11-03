package com.softzenith.crm.identity;

/**
 * Everything a role can be allowed to do. Code checks permissions, never role names, so a tenant can
 * rename roles, create new ones ("Sales Rep", "Telecaller") or regrant permissions without code changes.
 * New modules add their permissions here.
 */
public enum Permission {

    // Leads
    LEAD_VIEW,
    LEAD_CREATE,
    LEAD_EDIT,
    LEAD_CHANGE_STATUS,
    LEAD_ASSIGN,
    LEAD_REOPEN,
    /** Receive an email when a new lead arrives. */
    LEAD_NEW_ALERT,
    /** Load existing enquiries from a spreadsheet (.xlsx / .csv). */
    LEAD_IMPORT,

    // Reporting
    /** Lead dashboard: counts by status, source and assignee. */
    REPORTS_VIEW,

    // Administration
    USER_VIEW,
    USER_MANAGE,
    ROLE_MANAGE,
    BRANCH_MANAGE,
    LEAD_SOURCE_MANAGE,
    TENANT_SETTINGS_MANAGE
}
