package com.softzenith.crm.notification;

import com.softzenith.crm.notification.NotificationLog.Channel;
import com.softzenith.crm.notification.NotificationLog.Kind;
import com.softzenith.crm.notification.NotificationLog.Status;
import com.softzenith.crm.shared.logging.Mask;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders a template, sends it on a channel and records the outcome in {@link NotificationLog}.
 * Never throws: one failed recipient must not stop the others.
 *
 * <p>Skips a message already delivered for the same event (re-delivery after a crash), and caps messages to an
 * enquirer: anyone can type anyone's email or phone into the public form, so at most
 * {@code crm.notifications.enquirer-daily-cap} messages of one kind go to one address per 24 hours.
 */
@Component
class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    /** Messages to the enquirer (as opposed to staff alerts). */
    private static final Set<Kind> TO_ENQUIRER = EnumSet.of(Kind.LEAD_WELCOME, Kind.REPEAT_ENQUIRY_ACK, Kind.LEAD_STATUS_UPDATE);
    private static final Set<Status> DELIVERED = EnumSet.of(Status.SENT, Status.DEMO);

    private final MessageTemplates templates;
    private final JavaMailSender mail;
    private final WhatsAppSender whatsApp;
    private final NotificationLogRepository logs;
    private final String fromAddress;
    private final int enquirerDailyCap;

    Notifier(MessageTemplates templates, JavaMailSender mail, WhatsAppSender whatsApp, NotificationLogRepository logs,
             @Value("${crm.notifications.mail-from}") String fromAddress,
             @Value("${crm.notifications.enquirer-daily-cap}") int enquirerDailyCap) {
        this.templates = templates;
        this.mail = mail;
        this.whatsApp = whatsApp;
        this.logs = logs;
        this.fromAddress = fromAddress;
        this.enquirerDailyCap = enquirerDailyCap;
    }

    /** Who an email is from: the business's name (on the platform's address) and where replies go (null = that address). */
    record Sender(String name, String replyTo) {
    }

    void email(UUID eventId, UUID leadId, Kind kind, String to, Sender sender, String template, Map<String, Object> model) {
        if (alreadyDelivered(eventId, Channel.EMAIL, kind, to)) {
            return;
        }
        var message = templates.render(template, model);
        if (overCap(kind, to)) {
            log.warn("Email {} to {} skipped: daily cap reached (lead {})", kind, Mask.email(to), leadId);
            logs.save(new NotificationLog(eventId, leadId, Channel.EMAIL, kind, to, message.subject(), message.body(),
                    Status.SKIPPED, "smtp", null, "Daily cap of " + enquirerDailyCap + " reached"));
            return;
        }
        try {
            var mime = mail.createMimeMessage();
            var helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, sender.name(), StandardCharsets.UTF_8.name()));
            if (sender.replyTo() != null) {
                helper.setReplyTo(sender.replyTo());
            }
            helper.setTo(to);
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            mail.send(mime);
            logs.save(new NotificationLog(eventId, leadId, Channel.EMAIL, kind, to, message.subject(), message.body(),
                    Status.SENT, "smtp", mime.getMessageID(), null));
            log.info("Email {} sent to {} (lead {})", kind, Mask.email(to), leadId);
        } catch (Exception e) {
            logFailure(Channel.EMAIL, kind, Mask.email(to), leadId, e);
            logs.save(new NotificationLog(eventId, leadId, Channel.EMAIL, kind, to, message.subject(), message.body(),
                    Status.FAILED, "smtp", null, truncate(e.toString())));
        }
    }

    void whatsApp(UUID eventId, UUID leadId, Kind kind, String toE164, String template, Map<String, Object> model) {
        if (alreadyDelivered(eventId, Channel.WHATSAPP, kind, toE164)) {
            return;
        }
        var message = templates.render(template, model);
        if (overCap(kind, toE164)) {
            log.warn("WhatsApp {} to {} skipped: daily cap reached (lead {})", kind, Mask.phone(toE164), leadId);
            logs.save(new NotificationLog(eventId, leadId, Channel.WHATSAPP, kind, toE164, null, message.body(),
                    Status.SKIPPED, whatsApp.getClass().getSimpleName(), null, "Daily cap of " + enquirerDailyCap + " reached"));
            return;
        }
        try {
            var result = whatsApp.send(toE164, message.body());
            logs.save(new NotificationLog(eventId, leadId, Channel.WHATSAPP, kind, toE164, null, message.body(),
                    result.status(), result.provider(), result.providerRef(), null));
            log.info("WhatsApp {} {} to {} via {} (lead {})", kind, result.status(), Mask.phone(toE164), result.provider(), leadId);
        } catch (Exception e) {
            logFailure(Channel.WHATSAPP, kind, Mask.phone(toE164), leadId, e);
            logs.save(new NotificationLog(eventId, leadId, Channel.WHATSAPP, kind, toE164, null, message.body(),
                    Status.FAILED, whatsApp.getClass().getSimpleName(), null, truncate(e.toString())));
        }
    }

    /**
     * A staff alert that did not go out means a lead may sit unseen, so it is logged as an error (Sentry turns that
     * into an emailed issue). A message to an enquirer can fail because the public typed a wrong address: a warning.
     */
    private void logFailure(Channel channel, Kind kind, String maskedRecipient, UUID leadId, Exception e) {
        if (TO_ENQUIRER.contains(kind)) {
            log.warn("{} {} to {} failed (lead {}): {}", channel, kind, maskedRecipient, leadId, e.toString(), e);
        } else {
            log.error("Staff alert not delivered: {} {} to {} failed (lead {}): {}", channel, kind, maskedRecipient,
                    leadId, e.toString(), e);
        }
    }

    private boolean alreadyDelivered(UUID eventId, Channel channel, Kind kind, String recipient) {
        var done = eventId != null
                && logs.existsByEventIdAndChannelAndKindAndRecipientAndStatusIn(eventId, channel, kind, recipient, DELIVERED);
        if (done) {
            log.info("{} {} for event {} already delivered; not sending again", channel, kind, eventId);
        }
        return done;
    }

    private boolean overCap(Kind kind, String recipient) {
        return TO_ENQUIRER.contains(kind) && logs.countByRecipientAndKindAndStatusInAndCreatedAtAfter(
                recipient, kind, DELIVERED, Instant.now().minus(Duration.ofDays(1))) >= enquirerDailyCap;
    }

    private static String truncate(String s) {
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
