package com.softzenith.crm.leadsheet;

import com.softzenith.crm.lead.LeadEvents.LeadCreated;
import com.softzenith.crm.lead.LeadEvents.LeadRepeatEnquiry;
import com.softzenith.crm.lead.LeadSourceType;
import com.softzenith.crm.shared.logging.LogContext;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Copies every enquiry (new or repeat) into the business's Google Sheet, when it has one (tenant setting
 * {@code leadSheet}). A stopgap so a business can follow its enquiries in a spreadsheet; the CRM stays the source of
 * truth. Runs after the lead commits, off the request thread. A failure is logged and rethrown, so Spring Modulith's
 * event registry re-delivers the event on the next start; the sheet script skips a row it already has (column
 * {@code Ref} = the event id), so a re-delivery never adds a duplicate.
 */
@Component
class LeadSheetSync {

    private static final Logger log = LoggerFactory.getLogger(LeadSheetSync.class);

    /** The sheet's header row; every row has one value per column, in this order. */
    static final List<String> COLUMNS = List.of("Received", "Lead number", "Enquiry", "Name", "Mobile", "Email",
            "Service", "Country", "Branch", "Source", "Form / page", "Message", "Ref");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenants;
    private final TransactionTemplate tx;
    private final SheetClient sheet;

    LeadSheetSync(TenantRepository tenants, TransactionTemplate tx, SheetClient sheet) {
        this.tenants = tenants;
        this.tx = tx;
        this.sheet = sheet;
    }

    /** One enquiry as the sheet shows it. */
    record Enquiry(UUID tenantId, UUID eventId, Instant at, String leadNumber, String kind, String name, String phone,
                   String email, String service, String country, String branch, LeadSourceType source,
                   String sourceDetail, String message) {
    }

    @Async
    @TransactionalEventListener
    void on(LeadCreated e) {
        copy(new Enquiry(e.tenantId(), e.eventId(), e.occurredAt(), e.leadNumber(), "New", e.fullName(), e.phoneE164(),
                e.email(), e.serviceInterest(), e.preferredCountry(), e.branchName(), e.sourceType(), e.sourceDetail(),
                e.message()));
    }

    @Async
    @TransactionalEventListener
    void on(LeadRepeatEnquiry e) {
        copy(new Enquiry(e.tenantId(), e.eventId(), e.occurredAt(), e.leadNumber(), "Repeat #" + e.enquiryCount(),
                e.fullName(), e.phoneE164(), e.email(), e.serviceInterest(), e.preferredCountry(), null, e.sourceType(),
                e.sourceDetail(), e.message()));
    }

    void copy(Enquiry q) {
        MDC.put(LogContext.TENANT_ID, q.tenantId().toString());
        try {
            var tenant = TenantContext.call(q.tenantId(), () -> tx.execute(s -> tenants.findById(q.tenantId()).orElseThrow()));
            var target = tenant.getSettings().leadSheet();
            if (target == null || !target.enabled()) {
                return;
            }
            sheet.append(target.url(), target.secret(), COLUMNS, row(q, zone(tenant)), q.eventId().toString());
            log.info("Lead {} ({}) copied to the business's sheet", q.leadNumber(), q.kind());
        } catch (RuntimeException ex) {
            log.error("Copying lead {} to the business's sheet failed; the event will be retried on restart", q.leadNumber(), ex);
            throw ex;
        } finally {
            MDC.remove(LogContext.TENANT_ID);
        }
    }

    static List<String> row(Enquiry q, ZoneId zone) {
        var at = q.at() == null ? Instant.now() : q.at();
        return Stream.of(WHEN.format(at.atZone(zone)), q.leadNumber(), q.kind(), q.name(), q.phone(), q.email(),
                        q.service(), q.country(), q.branch(), q.source() == null ? null : label(q.source()),
                        q.sourceDetail(), q.message(), q.eventId().toString())
                .map(v -> v == null ? "" : v)
                .toList();
    }

    /** WEBSITE_FORM → "Website form". */
    static String label(LeadSourceType source) {
        var words = source.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static ZoneId zone(Tenant tenant) {
        try {
            return tenant.getTimezone() == null ? FALLBACK_ZONE : ZoneId.of(tenant.getTimezone());
        } catch (DateTimeException e) {
            return FALLBACK_ZONE;
        }
    }
}
