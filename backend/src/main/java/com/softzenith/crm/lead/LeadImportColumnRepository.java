package com.softzenith.crm.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface LeadImportColumnRepository extends JpaRepository<LeadImportColumn, UUID> {
}
