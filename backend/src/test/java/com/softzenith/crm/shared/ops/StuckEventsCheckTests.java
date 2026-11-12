package com.softzenith.crm.shared.ops;

import com.softzenith.crm.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class StuckEventsCheckTests {

    @Autowired StuckEventsCheck check;
    @Autowired JdbcTemplate jdbc;

    private final String listener = "test.Listener.stuck-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from event_publication where listener_id = ?", listener);
    }

    @Test
    void anOldUnfinishedDeliveryIsReportedAsAnError(CapturedOutput output) {
        insert(OffsetDateTime.now().minusHours(2), null);

        assertThat(check.check()).isPositive();
        assertThat(output.getAll()).containsPattern("ERROR .*event deliveries not completed after PT30M")
                .contains(listener);
    }

    @Test
    void finishedOrRecentDeliveriesAreNotReported(CapturedOutput output) {
        insert(OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(2));
        insert(OffsetDateTime.now().minusMinutes(5), null);

        check.check();
        assertThat(output.getAll()).doesNotContain(listener);
    }

    @Test
    void theScheduledRunDoesTheSameCheck(CapturedOutput output) {
        insert(OffsetDateTime.now().minusHours(2), null);
        check.run();
        assertThat(output.getAll()).contains(listener);
    }

    private void insert(OffsetDateTime published, OffsetDateTime completed) {
        jdbc.update("""
                insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date)
                values (?, ?, 'test.Event', '{}', ?, ?)""", UUID.randomUUID(), listener, published, completed);
    }
}
