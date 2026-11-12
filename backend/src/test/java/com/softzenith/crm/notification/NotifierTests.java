package com.softzenith.crm.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.softzenith.crm.Fixtures;
import com.softzenith.crm.IntegrationTest;
import com.softzenith.crm.TestData;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.tenancy.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class NotifierTests {

    private static final Notifier.Sender ACME = new Notifier.Sender("Acme", null);

    @RegisterExtension
    static GreenMailExtension mail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired Notifier notifier;
    @Autowired NotificationLogRepository logs;
    @Autowired Fixtures fixtures;
    @Autowired TransactionTemplate tx;
    @Autowired MessageTemplates templates;
    @Autowired org.springframework.mail.javamail.JavaMailSender mailSender;

    Tenant tenant;
    Map<String, Object> model;

    @BeforeEach
    void setUp() {
        tenant = fixtures.tenant();
        model = model("Asha Verma");
    }

    @Test
    void aReDeliveredEventDoesNotSendAgain() {
        var eventId = UUID.randomUUID();
        var to = "once-" + UUID.randomUUID() + "@mail.test";
        send(() -> notifier.email(eventId, null, NotificationLog.Kind.LEAD_WELCOME, to, ACME, "lead-welcome-email", model));
        send(() -> notifier.email(eventId, null, NotificationLog.Kind.LEAD_WELCOME, to, ACME, "lead-welcome-email", model));

        assertThat(rowsTo(to)).extracting(n -> n.getStatus()).containsExactly(NotificationLog.Status.SENT);
    }

    @Test
    void messagesToOneEnquirerAreCappedPerDayButStaffAlertsAreNot() {
        var to = "target-" + UUID.randomUUID() + "@mail.test";
        for (int i = 0; i < 6; i++) {
            send(() -> notifier.email(UUID.randomUUID(), null, NotificationLog.Kind.LEAD_WELCOME, to, ACME, "lead-welcome-email", model));
        }
        assertThat(rowsTo(to)).extracting(n -> n.getStatus()).containsOnly(NotificationLog.Status.SENT, NotificationLog.Status.SKIPPED)
                .filteredOn(s -> s == NotificationLog.Status.SKIPPED).hasSize(1);

        var staff = "staff-" + UUID.randomUUID() + "@acme.test";
        for (int i = 0; i < 6; i++) {
            send(() -> notifier.email(UUID.randomUUID(), null, NotificationLog.Kind.NEW_LEAD_ALERT, staff, ACME,
                    "new-lead-alert-email", model));
        }
        assertThat(rowsTo(staff)).extracting(n -> n.getStatus()).containsOnly(NotificationLog.Status.SENT).hasSize(6);
    }

    @Test
    void templateValuesCannotBreakOutOfTheSubjectLine() {
        var to = "subject-" + UUID.randomUUID() + "@mail.test";
        send(() -> notifier.email(UUID.randomUUID(), null, NotificationLog.Kind.NEW_LEAD_ALERT, to, ACME, "new-lead-alert-email",
                model("Asha\nPay your fees at evil.example")));
        var row = rowsTo(to).getFirst();
        assertThat(row.getSubject()).isEqualTo("New lead LD-1: Asha Pay your fees at evil.example");
        assertThat(row.getBody()).doesNotStartWith("Pay your fees");
    }

    @Test
    void whatsAppIsAlsoSentOncePerEventAndCappedPerDay() {
        var phone = TestData.phone();
        var eventId = UUID.randomUUID();
        send(() -> notifier.whatsApp(eventId, null, NotificationLog.Kind.LEAD_WELCOME, phone, "lead-welcome-whatsapp", model));
        send(() -> notifier.whatsApp(eventId, null, NotificationLog.Kind.LEAD_WELCOME, phone, "lead-welcome-whatsapp", model));
        assertThat(rowsTo(phone)).extracting(NotificationLog::getStatus).containsExactly(NotificationLog.Status.DEMO);

        for (int i = 0; i < 5; i++) {
            send(() -> notifier.whatsApp(UUID.randomUUID(), null, NotificationLog.Kind.LEAD_WELCOME, phone, "lead-welcome-whatsapp", model));
        }
        assertThat(rowsTo(phone)).extracting(NotificationLog::getStatus)
                .filteredOn(s -> s == NotificationLog.Status.SKIPPED).hasSize(1);
    }

    @Test
    void messagesWithoutAnEventAreNeverTreatedAsDuplicates() {
        var to = "no-event-" + UUID.randomUUID() + "@mail.test";
        send(() -> notifier.email(null, null, NotificationLog.Kind.NEW_LEAD_ALERT, to, ACME, "new-lead-alert-email", model));
        send(() -> notifier.email(null, null, NotificationLog.Kind.NEW_LEAD_ALERT, to, ACME, "new-lead-alert-email", model));
        assertThat(rowsTo(to)).hasSize(2);
    }

    @Test
    void aFailingWhatsAppProviderIsRecordedAsFailedWithoutThrowing() {
        WhatsAppSender broken = (toE164, text) -> {
            throw new IllegalStateException("provider down");
        };
        var failing = new Notifier(templates, mailSender, broken, logs, "crm@acme.test", 5);
        var phone = TestData.phone();
        send(() -> failing.whatsApp(UUID.randomUUID(), null, NotificationLog.Kind.LEAD_WELCOME, phone, "lead-welcome-whatsapp", model));

        var row = rowsTo(phone).getFirst();
        assertThat(row.getStatus()).isEqualTo(NotificationLog.Status.FAILED);
        assertThat(row.getError()).contains("provider down");
    }

    @Test
    void aStaffAlertThatFailsIsAnErrorButAFailedMessageToAnEnquirerIsOnlyAWarning(CapturedOutput output) {
        WhatsAppSender broken = (toE164, text) -> {
            throw new IllegalStateException("provider down");
        };
        var failing = new Notifier(templates, mailSender, broken, logs, "crm@acme.test", 5);
        var enquirer = TestData.phone();
        send(() -> failing.whatsApp(UUID.randomUUID(), null, NotificationLog.Kind.LEAD_WELCOME, enquirer, "lead-welcome-whatsapp", model));
        assertThat(output.getAll()).containsPattern("WARN .*WHATSAPP LEAD_WELCOME to .* failed")
                .doesNotContain("Staff alert not delivered");

        var staff = TestData.phone();
        send(() -> failing.whatsApp(UUID.randomUUID(), null, NotificationLog.Kind.NEW_LEAD_ALERT, staff, "lead-welcome-whatsapp", model));
        assertThat(output.getAll()).containsPattern("ERROR .*Staff alert not delivered: WHATSAPP NEW_LEAD_ALERT");
        assertThat(rowsTo(staff)).extracting(NotificationLog::getStatus).containsExactly(NotificationLog.Status.FAILED);
    }

    /** Every key the notification templates use. */
    private static Map<String, Object> model(String fullName) {
        var m = new java.util.HashMap<String, Object>();
        for (var key : java.util.List.of("phone", "email", "leadUrl", "serviceInterest", "preferredCountry", "branchName",
                "source", "message", "recipientName")) {
            m.put(key, "");
        }
        m.put("tenantName", "Acme");
        m.put("fullName", fullName);
        m.put("firstName", "Asha");
        m.put("leadNumber", "LD-1");
        return m;
    }

    private void send(Runnable r) {
        TenantContext.run(tenant.getId(), () -> tx.executeWithoutResult(s -> r.run()));
    }

    private java.util.List<NotificationLog> rowsTo(String recipient) {
        return TenantContext.call(tenant.getId(), () -> logs.findAll().stream()
                .filter(n -> n.getRecipient().equals(recipient)).toList());
    }
}
