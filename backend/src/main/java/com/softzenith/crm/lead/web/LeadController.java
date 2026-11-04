package com.softzenith.crm.lead.web;

import com.softzenith.crm.identity.StaffLookup;
import com.softzenith.crm.identity.StaffSummary;
import com.softzenith.crm.lead.Lead;
import com.softzenith.crm.lead.LeadActivity;
import com.softzenith.crm.lead.LeadFilter;
import com.softzenith.crm.lead.LeadService;
import com.softzenith.crm.lead.LeadSourceType;
import com.softzenith.crm.lead.LeadStats;
import com.softzenith.crm.lead.LeadStatus;
import com.softzenith.crm.lead.NewLead;
import com.softzenith.crm.shared.tenant.FeatureGate;
import com.softzenith.crm.shared.tenant.FeatureGateService;
import com.softzenith.crm.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Staff lead management")
class LeadController {

    private final LeadService leads;
    private final StaffLookup staff;
    private final FeatureGateService featureGate;

    LeadController(LeadService leads, StaffLookup staff, FeatureGateService featureGate) {
        this.leads = leads;
        this.staff = staff;
        this.featureGate = featureGate;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    @Operation(summary = "Search leads, newest first")
    PageResponse<LeadResponse> list(@RequestParam(required = false) LeadStatus status,
                                    @RequestParam(required = false) LeadSourceType sourceType,
                                    @RequestParam(required = false) UUID branchId,
                                    @RequestParam(required = false) UUID assignedTo,
                                    @RequestParam(required = false) Boolean unassigned,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Instant createdFrom,
                                    @RequestParam(required = false) Instant createdTo,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        var filter = new LeadFilter(status, sourceType, branchId, assignedTo, unassigned, q, createdFrom, createdTo);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "lastEnquiryAt", "createdAt"));
        return PageResponse.of(leads.search(filter, pageable), this::toResponses);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    @Operation(summary = "Lead counts by status, source and assignee for leads created in [from, to) (omit for all time)")
    LeadStats stats(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        return leads.stats(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    LeadResponse get(@PathVariable UUID id) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        return toResponses(List.of(leads.get(id))).getFirst();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAD_CREATE')")
    @Operation(summary = "Record a lead taken by staff (walk-in, phone, referral...). A repeat enquiry returns the existing open lead.")
    LeadResponse create(@Valid @RequestBody CreateLeadRequest r) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        var result = leads.intake(new NewLead(r.fullName(), r.phone(), r.email(), r.serviceInterest(), r.preferredCountry(),
                r.message(), r.branchId(), r.sourceType(), r.sourceDetail(), null, null, null, null, r.customFields()));
        return toResponses(List.of(result.lead())).getFirst();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_EDIT')")
    @Operation(summary = "Edit contact details (not status or assignment)")
    LeadResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadRequest r) {
        var lead = leads.updateContact(id, r.fullName(), r.phone(), r.email(), r.serviceInterest(), r.preferredCountry(),
                r.branchId(), r.customFields());
        return toResponses(List.of(lead)).getFirst();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('LEAD_CHANGE_STATUS')")
    @Operation(summary = "Change status to NEW, CONTACTED or CLOSED (reason required). Leaving CLOSED needs LEAD_REOPEN.")
    LeadResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        featureGate.requireFeature(FeatureGate.LEADS_CORE);
        return toResponses(List.of(leads.changeStatus(id, r.status(), r.reason()))).getFirst();
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    @Operation(summary = "Assign or reassign to a user whose role is assignable (e.g. counsellor); sets ASSIGNED")
    LeadResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest r) {
        featureGate.requireFeature(FeatureGate.TEAM_AND_STUDENTS);
        return toResponses(List.of(leads.assign(id, r.userId()))).getFirst();
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('LEAD_VIEW')")
    List<ActivityResponse> activities(@PathVariable UUID id) {
        var list = leads.activities(id);
        var ids = new HashSet<UUID>();
        for (var a : list) {
            if (a.getCreatedBy() != null) ids.add(a.getCreatedBy());
            if (carriesUserIds(a)) {
                Stream.of(a.getFromValue(), a.getToValue()).filter(Objects::nonNull).map(UUID::fromString).forEach(ids::add);
            }
        }
        var users = staff.users(ids);
        return list.stream().map(a -> ActivityResponse.of(a, users)).toList();
    }

    private static boolean carriesUserIds(LeadActivity a) {
        return a.getType() == LeadActivity.Type.ASSIGNED || a.getType() == LeadActivity.Type.UNASSIGNED;
    }

    private List<LeadResponse> toResponses(List<Lead> list) {
        var users = staff.users(list.stream().map(Lead::getAssignedTo).filter(Objects::nonNull).toList());
        var branches = staff.branchNames(list.stream().map(Lead::getBranchId).filter(Objects::nonNull).toList());
        return list.stream().map(l -> LeadResponse.of(l, users, branches)).toList();
    }

    record CreateLeadRequest(@NotBlank @Size(max = 200) @Pattern(regexp = NewLead.NAME_PATTERN, message = NewLead.NAME_MESSAGE)
                             String fullName,
                             @NotBlank @Size(max = 30) String phone,
                             @Email String email,
                             @Size(max = 200) String serviceInterest,
                             @Size(max = 100) String preferredCountry,
                             @Size(max = 2000) String message,
                             UUID branchId,
                             @NotNull LeadSourceType sourceType,
                             @Size(max = 200) String sourceDetail,
                             Map<String, Object> customFields) {
    }

    record UpdateLeadRequest(@NotBlank @Size(max = 200) @Pattern(regexp = NewLead.NAME_PATTERN, message = NewLead.NAME_MESSAGE)
                             String fullName,
                             @NotBlank @Size(max = 30) String phone,
                             @Email String email,
                             @Size(max = 200) String serviceInterest,
                             @Size(max = 100) String preferredCountry,
                             UUID branchId,
                             Map<String, Object> customFields) {
    }

    record StatusRequest(@NotNull LeadStatus status, @Size(max = 500) String reason) {
    }

    record AssignRequest(@NotNull UUID userId) {
    }

    record NamedRef(UUID id, String name) {
    }

    record LeadResponse(UUID id, String leadNumber, String fullName, String phone, String email,
                        String serviceInterest, String preferredCountry, String message, NamedRef branch,
                        LeadSourceType sourceType, String sourceDetail, String utmSource, String utmMedium,
                        String utmCampaign, LeadStatus status, NamedRef assignedTo, Instant assignedAt,
                        String closedReason, Instant closedAt, Map<String, Object> customFields,
                        Instant lastEnquiryAt, int enquiryCount, Instant createdAt, Instant updatedAt) {
        static LeadResponse of(Lead l, Map<UUID, StaffSummary> users, Map<UUID, String> branches) {
            var assignee = l.getAssignedTo() == null ? null : users.get(l.getAssignedTo());
            return new LeadResponse(l.getId(), l.getLeadNumber(), l.getFullName(), l.getPhoneE164(), l.getEmail(),
                    l.getServiceInterest(), l.getPreferredCountry(), l.getMessage(),
                    l.getBranchId() == null ? null : new NamedRef(l.getBranchId(), branches.get(l.getBranchId())),
                    l.getSourceType(), l.getSourceDetail(), l.getUtmSource(), l.getUtmMedium(), l.getUtmCampaign(),
                    l.getStatus(), assignee == null ? null : new NamedRef(assignee.id(), assignee.fullName()),
                    l.getAssignedAt(), l.getClosedReason(), l.getClosedAt(), l.getCustomFields(),
                    l.getLastEnquiryAt(), l.getEnquiryCount(), l.getCreatedAt(), l.getUpdatedAt());
        }
    }

    /** For ASSIGNED and UNASSIGNED, from/to are user ids and the names are filled in. */
    record ActivityResponse(UUID id, LeadActivity.Type type, String fromValue, String toValue, String note,
                            NamedRef actor, Instant at) {
        static ActivityResponse of(LeadActivity a, Map<UUID, StaffSummary> users) {
            var actor = a.getCreatedBy() == null ? null : users.get(a.getCreatedBy());
            String from = a.getFromValue(), to = a.getToValue();
            if (carriesUserIds(a)) {
                from = name(from, users);
                to = name(to, users);
            }
            return new ActivityResponse(a.getId(), a.getType(), from, to, a.getNote(),
                    actor == null ? null : new NamedRef(actor.id(), actor.fullName()), a.getCreatedAt());
        }

        private static String name(String id, Map<UUID, StaffSummary> users) {
            if (id == null) return null;
            var u = users.get(UUID.fromString(id));
            return u == null ? id : u.fullName();
        }
    }
}
