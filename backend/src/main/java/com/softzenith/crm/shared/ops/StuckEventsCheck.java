package com.softzenith.crm.shared.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Notices event deliveries (e.g. lead created -> staff alert) that never completed. Spring Modulith only retries
 * them on the next restart, so without this a crashed notification could wait unseen for days. Logged as an error,
 * which Sentry turns into an emailed issue; the rows themselves are not touched.
 */
@Component
class StuckEventsCheck {

    private static final Logger log = LoggerFactory.getLogger(StuckEventsCheck.class);

    private final JdbcTemplate jdbc;
    private final Duration olderThan;

    StuckEventsCheck(JdbcTemplate jdbc, @Value("${crm.ops.stuck-events.older-than:PT30M}") Duration olderThan) {
        this.jdbc = jdbc;
        this.olderThan = olderThan;
    }

    @Scheduled(initialDelayString = "${crm.ops.stuck-events.check-every:PT15M}",
               fixedDelayString = "${crm.ops.stuck-events.check-every:PT15M}")
    void run() {
        check();
    }

    /** @return how many deliveries are stuck (logged as an error when there are any) */
    int check() {
        var cutoff = OffsetDateTime.now().minus(olderThan);
        var stuck = jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null and publication_date < ?",
                Integer.class, cutoff);
        int count = stuck == null ? 0 : stuck;
        if (count > 0) {
            var listeners = jdbc.queryForList(
                    "select distinct listener_id from event_publication where completion_date is null and publication_date < ?",
                    String.class, cutoff);
            log.error("{} event deliveries not completed after {} (listeners: {}). They are retried on the next API "
                    + "restart; check the API log for why they failed.", count, olderThan, listeners);
        }
        return count;
    }
}
