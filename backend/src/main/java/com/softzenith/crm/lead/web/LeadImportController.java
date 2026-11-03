package com.softzenith.crm.lead.web;

import com.softzenith.crm.lead.LeadImports;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.tenant.FeatureGateService;
import com.softzenith.crm.shared.web.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Lead import from a spreadsheet: read the file, then check (dryRun) or import the matched rows. */
@RestController
@RequestMapping("/api/v1/lead-imports")
@Tag(name = "Lead import", description = "Load existing enquiries from an .xlsx or .csv file")
class LeadImportController {

    private final LeadImports imports;
    private final FeatureGateService featureGate;

    LeadImportController(LeadImports imports, FeatureGateService featureGate) {
        this.imports = imports;
        this.featureGate = featureGate;
    }

    @PostMapping(path = "/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('LEAD_IMPORT')")
    @Operation(summary = "Read an .xlsx or .csv file (first sheet, header = first non-blank row) and suggest a lead field per column. Saves nothing.")
    LeadImports.ImportPreview read(@RequestPart("file") MultipartFile file) throws IOException {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        if (file.isEmpty()) {
            throw new InvalidInputException("The file is empty");
        }
        return imports.read(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAD_IMPORT')")
    @Operation(summary = "Check (dryRun=true, saves nothing) or import the rows. Imported leads get source IMPORT and send no alerts or messages; a person with an open lead is skipped.")
    LeadImports.ImportReport run(@RequestBody LeadImports.ImportRequest request, @RequestParam(defaultValue = "true") boolean dryRun) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        return imports.run(request, dryRun);
    }
}
