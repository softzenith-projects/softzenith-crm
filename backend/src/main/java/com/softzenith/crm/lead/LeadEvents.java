package com.softzenith.crm.lead;

import java.time.Instant;
import java.util.UUID;

/**
 * Events other modules react to (notifications, the lead sheet; feed, automation later). They carry what listeners
 * need, so no other module reads the leads table. Published within the lead transaction and delivered
 * after commit via Spring Modulith's event publication registry, which retries incomplete deliveries.
 * {@code eventId} is fixed when the event is published, so a re-delivered event can be recognised (listeners use it
 * to avoid sending the same message twice). {@code occurredAt} is when the enquiry arrived; it is null on events
 * stored before it existed.
 */
public final class LeadEvents {

    private LeadEvents() {
    }

    public record LeadCreated(UUID eventId, UUID tenantId, UUID leadId, String leadNumber, String fullName, String phoneE164,
                              String email, String serviceInterest, String preferredCountry, String branchName,
                              LeadSourceType sourceType, String message, String sourceDetail, Instant occurredAt) {
    }

    public record LeadAssigned(UUID eventId, UUID tenantId, UUID leadId, String leadNumber, String fullName, String phoneE164,
                               String email, UUID assigneeId) {
    }

    /**
     * Someone with an open lead enquired again. {@code assigneeId} is null when nobody owns the lead yet;
     * {@code email} is the address on the lead (the enquirer may have typed a different one).
     * {@code serviceInterest}, {@code preferredCountry} and {@code sourceDetail} are what this enquiry said.
     */
    public record LeadRepeatEnquiry(UUID eventId, UUID tenantId, UUID leadId, String leadNumber, String fullName, String phoneE164,
                                    String email, UUID assigneeId, int enquiryCount, LeadSourceType sourceType,
                                    String message, String serviceInterest, String preferredCountry, String sourceDetail,
                                    Instant occurredAt) {
    }

    /** A staff member moved the lead to another status (NEW / CONTACTED / CLOSED; ASSIGNED comes as LeadAssigned). */
    public record LeadStatusChanged(UUID eventId, UUID tenantId, UUID leadId, String leadNumber, String fullName, String phoneE164,
                                    String email, LeadStatus from, LeadStatus to) {
    }
}
