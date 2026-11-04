package com.softzenith.crm.notification;

import com.softzenith.crm.identity.Permission;
import com.softzenith.crm.identity.StaffLookup;
import com.softzenith.crm.identity.StaffSummary;
import com.softzenith.crm.lead.LeadEvents.LeadAssigned;
import com.softzenith.crm.lead.LeadEvents.LeadCreated;
import com.softzenith.crm.lead.LeadEvents.LeadRepeatEnquiry;
import com.softzenith.crm.lead.LeadEvents.LeadStatusChanged;
import com.softzenith.crm.lead.LeadStatus;
import com.softzenith.crm.notification.NotificationLog.Kind;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import com.softzenith.crm.tenancy.TenantRepository;
import com.softzenith.crm.shared.logging.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Who gets told what when leads change. Runs after the lead transaction commits, off the request thread;
 * Spring Modulith's event registry re-delivers events that were not completed (e.g. on a crash).
 *
 * <ul>
 *   <li>New lead: welcome email + WhatsApp to the enquirer (per tenant settings); alert email to every
 *       staff member whose role has {@link Permission#LEAD_NEW_ALERT} (Admin and Receptionist by default).</li>
 *   <li>Lead assigned: email to the assignee; email + WhatsApp to the enquirer naming their counsellor.</li>
 *   <li>Status changed by staff: email + WhatsApp to the enquirer (internal close reasons are never sent).</li>
 *   <li>Repeat enquiry: alert email to the assignee, or to the {@code LEAD_NEW_ALERT} staff if nobody owns the
 *       lead yet; acknowledgement email + WhatsApp to the enquirer.</li>
 * </ul>
 * Messages to the enquirer beyond the welcome follow the tenant's {@code notifications.studentUpdates} setting.
 */
@Component
class LeadNotifications {

    private static final Logger log = LoggerFactory.getLogger(LeadNotifications.class);

    private final Notifier notifier;
    private final StaffLookup staff;
    private final TenantRepository tenants;
    private final TransactionTemplate tx;
    private final String frontendUrl;

    LeadNotifications(Notifier notifier, StaffLookup staff, TenantRepository tenants, TransactionTemplate tx,
                      @Value("${crm.app.frontend-url}") String frontendUrl) {
        this.notifier = notifier;
        this.staff = staff;
        this.tenants = tenants;
        this.tx = tx;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener
    void on(LeadCreated e) {
        handle("LeadCreated", e.tenantId(), e.leadNumber(), () -> leadCreated(e));
    }

    @Async
    @TransactionalEventListener
    void on(LeadAssigned e) {
        handle("LeadAssigned", e.tenantId(), e.leadNumber(), () -> leadAssigned(e));
    }

    @Async
    @TransactionalEventListener
    void on(LeadStatusChanged e) {
        handle("LeadStatusChanged", e.tenantId(), e.leadNumber(), () -> statusChanged(e));
    }

    @Async
    @TransactionalEventListener
    void on(LeadRepeatEnquiry e) {
        handle("LeadRepeatEnquiry", e.tenantId(), e.leadNumber(), () -> repeatEnquiry(e));
    }

    /**
     * Runs one listener in the event's tenant and logs its outcome. A failure is logged and rethrown so the event
     * stays incomplete in the event registry and is re-delivered on restart.
     */
    private void handle(String event, UUID tenantId, String leadNumber, Runnable work) {
        MDC.put(LogContext.TENANT_ID, tenantId.toString());
        try {
            log.debug("Handling {} for lead {}", event, leadNumber);
            TenantContext.run(tenantId, () -> tx.executeWithoutResult(s -> work.run()));
        } catch (RuntimeException ex) {
            log.error("Notifications for {} on lead {} failed; the event will be retried on restart", event, leadNumber, ex);
            throw ex;
        } finally {
            MDC.remove(LogContext.TENANT_ID);
        }
    }

    private void leadCreated(LeadCreated e) {
        var tenant = tenants.findById(e.tenantId()).orElseThrow();
        var settings = tenant.getSettings().notifications();
        var model = baseModel(tenant, e.leadId(), e.leadNumber(), e.fullName(), e.phoneE164(), e.email());
        var form = tenant.getSettings().enquiryForm();
        // The enquirer's welcome only repeats values the tenant offers on its form: the public form accepts any
        // text, which would otherwise let anyone put their own words into a message sent under the tenant's name.
        var welcome = new HashMap<>(model);
        welcome.put("serviceInterest", offered(e.serviceInterest(), form.serviceInterests()));
        welcome.put("preferredCountry", offered(e.preferredCountry(), form.countries()));
        if (settings.welcomeEmail() && e.email() != null) {
            notifier.email(e.eventId(), e.leadId(), Kind.LEAD_WELCOME, e.email(), sender(tenant), "lead-welcome-email", welcome);
        }
        if (settings.welcomeWhatsApp()) {
            notifier.whatsApp(e.eventId(), e.leadId(), Kind.LEAD_WELCOME, e.phoneE164(), "lead-welcome-whatsapp", welcome);
        }

        model.put("serviceInterest", e.serviceInterest());
        model.put("preferredCountry", e.preferredCountry());
        model.put("branchName", e.branchName());
        model.put("source", e.sourceType().name());
        model.put("message", e.message());
        for (var recipient : staff.withPermission(Permission.LEAD_NEW_ALERT)) {
            if (recipient.email() != null) {
                var personal = new HashMap<>(model);
                personal.put("recipientName", recipient.fullName());
                notifier.email(e.eventId(), e.leadId(), Kind.NEW_LEAD_ALERT, recipient.email(), sender(tenant), "new-lead-alert-email", personal);
            }
        }
    }

    private void leadAssigned(LeadAssigned e) {
        var tenant = tenants.findById(e.tenantId()).orElseThrow();
        var assignee = staff.user(e.assigneeId());
        assignee.filter(u -> u.email() != null).ifPresent(u -> {
            var model = baseModel(tenant, e.leadId(), e.leadNumber(), e.fullName(), e.phoneE164(), e.email());
            model.put("recipientName", u.fullName());
            notifier.email(e.eventId(), e.leadId(), Kind.LEAD_ASSIGNED, u.email(), sender(tenant), "lead-assigned-email", model);
        });
        assignee.ifPresent(u -> {
            var model = baseModel(tenant, e.leadId(), e.leadNumber(), e.fullName(), e.phoneE164(), e.email());
            model.put("assigned", true);
            model.put("counsellorName", u.fullName());
            toEnquirer(tenant, e.eventId(), e.leadId(), e.phoneE164(), e.email(), Kind.LEAD_STATUS_UPDATE, "lead-status", model);
        });
    }

    private void statusChanged(LeadStatusChanged e) {
        var tenant = tenants.findById(e.tenantId()).orElseThrow();
        var model = baseModel(tenant, e.leadId(), e.leadNumber(), e.fullName(), e.phoneE164(), e.email());
        switch (e.to()) {
            case CONTACTED -> model.put("contacted", true);
            case CLOSED -> model.put("closed", true);
            case NEW -> {
                if (e.from() != LeadStatus.CLOSED) return; // only a reopen is news to the enquirer
                model.put("reopened", true);
            }
            case ASSIGNED -> {
                return; // announced by leadAssigned, with the counsellor's name
            }
        }
        toEnquirer(tenant, e.eventId(), e.leadId(), e.phoneE164(), e.email(), Kind.LEAD_STATUS_UPDATE, "lead-status", model);
    }

    private void repeatEnquiry(LeadRepeatEnquiry e) {
        var tenant = tenants.findById(e.tenantId()).orElseThrow();
        var model = baseModel(tenant, e.leadId(), e.leadNumber(), e.fullName(), e.phoneE164(), e.email());
        model.put("source", e.sourceType().name());
        model.put("message", e.message());
        model.put("enquiryCount", e.enquiryCount());
        var assignee = e.assigneeId() == null ? Optional.<StaffSummary>empty()
                : staff.user(e.assigneeId()).filter(u -> u.enabled());
        assignee.ifPresent(u -> model.put("counsellorName", u.fullName()));

        var recipients = assignee.map(List::of).orElseGet(() -> staff.withPermission(Permission.LEAD_NEW_ALERT));
        for (var recipient : recipients) {
            if (recipient.email() != null) {
                var personal = new HashMap<>(model);
                personal.put("recipientName", recipient.fullName());
                notifier.email(e.eventId(), e.leadId(), Kind.REPEAT_ENQUIRY_ALERT, recipient.email(), sender(tenant),
                        "repeat-enquiry-alert-email", personal);
            }
        }
        toEnquirer(tenant, e.eventId(), e.leadId(), e.phoneE164(), e.email(), Kind.REPEAT_ENQUIRY_ACK, "repeat-enquiry-ack", model);
    }

    /** Email (if the lead has one) + WhatsApp to the enquirer, when the tenant sends student updates. */
    private void toEnquirer(Tenant tenant, UUID eventId, UUID leadId, String phone, String email, Kind kind,
                            String templatePrefix, Map<String, Object> model) {
        if (!tenant.getSettings().notifications().studentUpdates()) {
            return;
        }
        if (email != null) {
            notifier.email(eventId, leadId, kind, email, sender(tenant), templatePrefix + "-email", model);
        }
        notifier.whatsApp(eventId, leadId, kind, phone, templatePrefix + "-whatsapp", model);
    }

    private Map<String, Object> baseModel(Tenant tenant, UUID leadId, String leadNumber, String fullName,
                                          String phone, String email) {
        var model = new HashMap<String, Object>();
        model.put("tenantName", tenant.getName());
        model.put("leadNumber", leadNumber);
        model.put("fullName", fullName);
        model.put("firstName", firstName(fullName));
        model.put("phone", phone);
        model.put("email", email);
        model.put("leadUrl", frontendUrl + "/leads/" + leadId);
        return model;
    }

    private static final Pattern FIRST_NAME = Pattern.compile("[\\p{L}\\p{M}]+(['’-][\\p{L}\\p{M}]+)*\\.?");

    /**
     * First word of the name for "Hi {{firstName}}". Anything that does not look like a name (e.g. a domain someone
     * typed as their name, which messaging apps would turn into a link) becomes a neutral greeting.
     */
    static String firstName(String fullName) {
        var first = fullName == null ? "" : fullName.strip().split("\\s+")[0];
        return FIRST_NAME.matcher(first).matches() ? first : "there";
    }

    /** The option as the tenant spells it, or null when it is not one of the tenant's form options. */
    private static String offered(String value, List<String> options) {
        return value == null ? null : options.stream().filter(value::equalsIgnoreCase).findFirst().orElse(null);
    }

    /** The business's name as the email "From" name (on the platform's address), and its reply-to address. */
    private static Notifier.Sender sender(Tenant tenant) {
        var notifications = tenant.getSettings().notifications();
        var configured = notifications.senderName();
        return new Notifier.Sender(configured == null || configured.isBlank() ? tenant.getName() : configured,
                notifications.replyTo());
    }
}
