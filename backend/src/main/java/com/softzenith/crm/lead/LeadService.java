package com.softzenith.crm.lead;

import com.softzenith.crm.identity.CurrentStaff;
import com.softzenith.crm.identity.DataScope;
import com.softzenith.crm.identity.Permission;
import com.softzenith.crm.identity.StaffLookup;
import com.softzenith.crm.identity.StaffPrincipal;
import com.softzenith.crm.lead.LeadEvents.LeadAssigned;
import com.softzenith.crm.lead.LeadEvents.LeadCreated;
import com.softzenith.crm.lead.LeadEvents.LeadRepeatEnquiry;
import com.softzenith.crm.lead.LeadEvents.LeadStatusChanged;
import com.softzenith.crm.shared.logging.Mask;
import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.text.Text;
import com.softzenith.crm.shared.web.ConflictException;
import com.softzenith.crm.shared.web.InvalidInputException;
import com.softzenith.crm.shared.web.NotFoundException;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantCounters;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All writes to leads go through here: every source, every status change, every assignment. */
@Service
@Transactional
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leads;
    private final LeadActivityRepository activities;
    private final TenantCounters counters;
    private final TenantRepository tenants;
    private final StaffLookup staff;
    private final ApplicationEventPublisher events;

    LeadService(LeadRepository leads, LeadActivityRepository activities, TenantCounters counters,
                TenantRepository tenants, StaffLookup staff, ApplicationEventPublisher events) {
        this.leads = leads;
        this.activities = activities;
        this.counters = counters;
        this.tenants = tenants;
        this.staff = staff;
        this.events = events;
    }

    /** @param created false when the enquiry was attached to the person's existing open lead */
    public record IntakeResult(Lead lead, boolean created) {
    }

    /**
     * The single entry point for new enquiries from any source. A repeat enquiry from a person who already
     * has an open lead is recorded on that lead instead of creating a duplicate; nothing is dropped.
     */
    public IntakeResult intake(NewLead d) {
        var tenant = currentTenant();
        var phone = PhoneNumbers.toE164(d.phone(), tenant.getDefaultRegion());
        var branchName = requireActiveBranch(d.branchId());
        validateCustomFields(d.customFields());
        var message = Text.multiLine(d.message());
        var sourceDetail = Text.singleLine(d.sourceDetail());

        leads.lockPerson(tenant.getId() + ":" + phone);
        var existing = leads.findOpenByPerson(phone, Lead.normaliseEmail(d.email()));
        if (existing.isPresent()) {
            var lead = existing.get();
            activities.save(new LeadActivity(lead.getId(), LeadActivity.Type.REPEAT_ENQUIRY, null,
                    d.sourceType().name(), message));
            leads.recordRepeatEnquiry(lead.getId(), Instant.now());
            // Not get(): intake records the enquiry whatever the caller's data scope.
            var bumped = leads.findById(lead.getId()).orElseThrow();
            events.publishEvent(new LeadRepeatEnquiry(UUID.randomUUID(), tenant.getId(), bumped.getId(), bumped.getLeadNumber(),
                    bumped.getFullName(), bumped.getPhoneE164(), bumped.getEmail(), bumped.getAssignedTo(),
                    bumped.getEnquiryCount(), d.sourceType(), message, Text.singleLine(d.serviceInterest()),
                    Text.singleLine(d.preferredCountry()), sourceDetail, Instant.now()));
            log.info("Repeat enquiry #{} on lead {} via {} (phone {})", bumped.getEnquiryCount(), bumped.getLeadNumber(),
                    d.sourceType(), Mask.phone(phone));
            return new IntakeResult(bumped, false);
        }

        var number = "%s-%06d".formatted(tenant.getSettings().leadNumberPrefix(), counters.next(tenant.getId(), "lead"));
        var lead = leads.save(new Lead(number, d, phone));
        activities.save(new LeadActivity(lead.getId(), LeadActivity.Type.CREATED, null, d.sourceType().name(), null));
        events.publishEvent(new LeadCreated(UUID.randomUUID(), tenant.getId(), lead.getId(), number, lead.getFullName(), phone,
                lead.getEmail(), lead.getServiceInterest(), lead.getPreferredCountry(), branchName,
                lead.getSourceType(), lead.getMessage(), lead.getSourceDetail(), lead.getCreatedAt()));
        log.info("Lead {} created via {}{} (phone {})", number, d.sourceType(),
                sourceDetail == null ? "" : " [" + sourceDetail + "]", Mask.phone(phone));
        return new IntakeResult(lead, true);
    }

    /** A lead the signed-in staff member may see (their role's data scope); others are reported as not found. */
    @Transactional(readOnly = true)
    public Lead get(UUID id) {
        return leads.findById(id).filter(LeadService::inScope).orElseThrow(() -> new NotFoundException("Lead", id));
    }

    @Transactional(readOnly = true)
    public Page<Lead> search(LeadFilter filter, Pageable pageable) {
        return leads.findAll(filter.toSpecification().and(scopeSpecification()), pageable);
    }

    @Transactional(readOnly = true)
    public List<LeadActivity> activities(UUID leadId) {
        get(leadId);
        return activities.findByLeadIdOrderByCreatedAt(leadId);
    }

    /** Dashboard counts for leads created in [from, to); null bounds mean "all time" / "now". */
    @Transactional(readOnly = true)
    public LeadStats stats(Instant from, Instant to) {
        var start = from == null ? Instant.EPOCH : from;
        var end = to == null ? Instant.now().plusSeconds(1) : to;

        var byStatus = new EnumMap<LeadStatus, Long>(LeadStatus.class);
        for (var s : LeadStatus.values()) byStatus.put(s, 0L);
        leads.countByStatus(start, end).forEach(r -> byStatus.put((LeadStatus) r[0], (Long) r[1]));

        var bySource = new EnumMap<LeadSourceType, Long>(LeadSourceType.class);
        for (var s : LeadSourceType.values()) bySource.put(s, 0L);
        leads.countBySource(start, end).forEach(r -> bySource.put((LeadSourceType) r[0], (Long) r[1]));

        var assigneeRows = leads.countByAssignee(start, end);
        var names = staff.users(assigneeRows.stream().map(r -> (UUID) r[0]).toList());
        var byAssignee = assigneeRows.stream()
                .map(r -> {
                    var user = names.get((UUID) r[0]);
                    return new LeadStats.AssigneeCount((UUID) r[0], user == null ? "Unknown" : user.fullName(), (Long) r[1]);
                })
                .sorted(Comparator.comparingLong(LeadStats.AssigneeCount::count).reversed())
                .toList();

        var total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new LeadStats(from, to, total, leads.countOpenUnassigned(start, end), byStatus, bySource, byAssignee);
    }

    public Lead updateContact(UUID id, String fullName, String phone, String email, String serviceInterest,
                              String preferredCountry, UUID branchId, Map<String, Object> customFields) {
        var lead = get(id);
        requireActiveBranch(branchId);
        validateCustomFields(customFields);
        var phoneE164 = PhoneNumbers.toE164(phone, currentTenant().getDefaultRegion());
        if (lead.getStatus() != LeadStatus.CLOSED) {
            requireNoOtherOpenLead(lead, phoneE164, Lead.normaliseEmail(email));
        }
        lead.updateContact(fullName, phone, phoneE164, email, serviceInterest, preferredCountry, branchId, customFields);
        activities.save(new LeadActivity(id, LeadActivity.Type.UPDATED, null, null, null));
        log.info("Lead {} contact details updated", lead.getLeadNumber());
        return lead;
    }

    public Lead changeStatus(UUID id, LeadStatus target, String reason) {
        var lead = get(id);
        var from = lead.getStatus();
        if (from == LeadStatus.CLOSED && !CurrentStaff.require().has(Permission.LEAD_REOPEN)) {
            log.warn("Reopen of lead {} refused: no LEAD_REOPEN", lead.getLeadNumber());
            throw new AccessDeniedException("Reopening a closed lead needs the LEAD_REOPEN permission");
        }
        if (from == LeadStatus.CLOSED && target != LeadStatus.CLOSED) {
            requireNoOtherOpenLead(lead, lead.getPhoneE164(), lead.getEmail());
        }
        var previousAssignee = lead.getAssignedTo();
        lead.changeStatus(target, reason);
        activities.save(new LeadActivity(id, LeadActivity.Type.STATUS_CHANGED, from.name(), target.name(),
                target == LeadStatus.CLOSED ? lead.getClosedReason() : null));
        if (previousAssignee != null && lead.getAssignedTo() == null) {
            activities.save(new LeadActivity(id, LeadActivity.Type.UNASSIGNED, previousAssignee.toString(), null, "Reopened"));
            log.info("Lead {} reopened: back in the unassigned queue (was {})", lead.getLeadNumber(), previousAssignee);
        }
        events.publishEvent(new LeadStatusChanged(UUID.randomUUID(), lead.getTenantId(), id, lead.getLeadNumber(), lead.getFullName(),
                lead.getPhoneE164(), lead.getEmail(), from, target));
        log.info("Lead {} status {} -> {}", lead.getLeadNumber(), from, target);
        return lead;
    }

    public Lead assign(UUID id, UUID assigneeId) {
        var lead = get(id);
        var assignee = staff.user(assigneeId).orElseThrow(() -> new NotFoundException("User", assigneeId));
        if (!assignee.enabled() || !assignee.assignable()) {
            log.info("Lead {} not assigned: {} is disabled or not assignable", lead.getLeadNumber(), assigneeId);
            throw new ConflictException(assignee.fullName() + " (" + assignee.roleName() + ") cannot be assigned leads");
        }
        var previous = lead.getAssignedTo();
        lead.assignTo(assigneeId, CurrentStaff.find().map(StaffPrincipal::userId).orElse(null));
        activities.save(new LeadActivity(id, LeadActivity.Type.ASSIGNED,
                previous == null ? null : previous.toString(), assigneeId.toString(), null));
        events.publishEvent(new LeadAssigned(UUID.randomUUID(), lead.getTenantId(), id, lead.getLeadNumber(), lead.getFullName(),
                lead.getPhoneE164(), lead.getEmail(), assigneeId));
        log.info("Lead {} assigned to {} (was {})", lead.getLeadNumber(), assigneeId, previous);
        return lead;
    }

    /**
     * Data scope of the signed-in staff member's role: ALL = every lead; BRANCH = leads of their branch plus leads
     * assigned to them; OWN = leads assigned to them. No signed-in staff (public intake, jobs) = unrestricted.
     */
    private static Specification<Lead> scopeSpecification() {
        var staff = CurrentStaff.find().orElse(null);
        if (staff == null || staff.dataScope() == DataScope.ALL) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> {
            var mine = cb.equal(root.get("assignedTo"), staff.userId());
            if (staff.dataScope() == DataScope.BRANCH && staff.branchId() != null) {
                return cb.or(mine, cb.equal(root.get("branchId"), staff.branchId()));
            }
            return mine;
        };
    }

    private static boolean inScope(Lead lead) {
        var staff = CurrentStaff.find().orElse(null);
        if (staff == null || staff.dataScope() == DataScope.ALL) {
            return true;
        }
        var mine = staff.userId().equals(lead.getAssignedTo());
        return switch (staff.dataScope()) {
            case BRANCH -> mine || (staff.branchId() != null && staff.branchId().equals(lead.getBranchId()));
            case OWN -> mine;
            case ALL -> true;
        };
    }

    /**
     * One open lead per person (leads_open_person_uq). Takes the same per-person lock as {@link #intake}, so an edit or
     * reopen can't race a new enquiry into a duplicate; the lock is keyed on the phone the lead will have afterwards.
     */
    private void requireNoOtherOpenLead(Lead lead, String phoneE164, String email) {
        leads.lockPerson(lead.getTenantId() + ":" + phoneE164);
        var other = leads.findOpenByPerson(phoneE164, email).filter(l -> !l.getId().equals(lead.getId()));
        if (other.isPresent()) {
            log.info("Lead {} not saved: open lead {} already exists for this person", lead.getLeadNumber(),
                    other.get().getLeadNumber());
            throw new ConflictException("Lead " + other.get().getLeadNumber()
                    + " is already open for this phone and email; update that lead instead");
        }
    }

    /** Tenant-specific fields are small key/value data, not a document store. */
    private static void validateCustomFields(Map<String, Object> fields) {
        if (fields == null) {
            return;
        }
        if (fields.size() > 50 || fields.keySet().stream().anyMatch(k -> k == null || k.length() > 64)
                || String.valueOf(fields).length() > 10_000) {
            throw new InvalidInputException("Custom fields are limited to 50 keys of up to 64 characters and 10 KB in total");
        }
    }

    private Tenant currentTenant() {
        return tenants.findById(TenantContext.require()).orElseThrow();
    }

    private String requireActiveBranch(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        var branch = staff.branch(branchId).filter(b -> b.isActive())
                .orElseThrow(() -> new InvalidInputException("Unknown branch"));
        return branch.getName();
    }
}
