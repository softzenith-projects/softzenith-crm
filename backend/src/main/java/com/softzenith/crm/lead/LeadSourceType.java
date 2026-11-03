package com.softzenith.crm.lead;

/** Where a lead came from. Retained on every lead (PRD Phase 1). */
public enum LeadSourceType {
    WEBSITE_FORM,
    META_LEAD_AD,
    WHATSAPP,
    WALK_IN,
    PHONE,
    REFERRAL,
    OTHER,
    /** Loaded from a spreadsheet by an Admin (lead import); source_detail holds the label they gave, e.g. "Portal". */
    IMPORT
}
