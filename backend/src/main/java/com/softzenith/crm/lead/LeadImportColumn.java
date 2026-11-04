package com.softzenith.crm.lead;

import com.softzenith.crm.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** The lead field a business last matched a spreadsheet header to, so its next file is matched the same way. */
@Entity
@Table(name = "lead_import_columns")
class LeadImportColumn extends TenantScopedEntity {

    /** {@link LeadImports#headerKey}: the header lower-case, letters and digits only. */
    @Column(nullable = false, updatable = false)
    private String headerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadImports.ImportField target;

    protected LeadImportColumn() {
    }

    LeadImportColumn(String headerKey, LeadImports.ImportField target) {
        this.headerKey = headerKey;
        this.target = target;
    }

    String getHeaderKey() { return headerKey; }
    LeadImports.ImportField getTarget() { return target; }

    void setTarget(LeadImports.ImportField target) { this.target = target; }
}
