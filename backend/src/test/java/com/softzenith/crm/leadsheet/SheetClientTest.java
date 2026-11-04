package com.softzenith.crm.leadsheet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Posting rows to the sheet script: confirmation, retries, refusals, address rules, interruption. */
class SheetClientTest {

    private FakeSheetScript script;
    private final SheetClient client = new SheetClient(2, Duration.ofMillis(10));

    @BeforeEach
    void start() throws IOException {
        script = new FakeSheetScript();
    }

    @AfterEach
    void stop() {
        script.close();
    }

    @Test
    void aConfirmedRowIsSentOnceWithSecretColumnsAndRef() {
        client.append(script.url(), "s3cret", List.of("Name", "Mobile"), List.of("Riya", "+919876543210"), "ref-1");

        assertThat(script.posts).hasSize(1);
        var post = script.posts.getFirst();
        assertThat((String) JsonPath.read(post, "$.secret")).isEqualTo("s3cret");
        assertThat((List<String>) JsonPath.read(post, "$.columns")).containsExactly("Name", "Mobile");
        assertThat((List<String>) JsonPath.read(post, "$.row")).containsExactly("Riya", "+919876543210");
        assertThat((String) JsonPath.read(post, "$.ref")).isEqualTo("ref-1");
    }

    @Test
    void aMissingSecretIsSentEmpty() {
        client.append(script.url(), null, List.of("A"), List.of("1"), "ref-2");
        assertThat((String) JsonPath.read(script.posts.getFirst(), "$.secret")).isEmpty();
    }

    @Test
    void anErrorIsRetriedThenReported() {
        script.status = 500;
        assertThatThrownBy(() -> client.append(script.url(), "s", List.of("A"), List.of("1"), "ref-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after 2 attempts").hasMessageContaining("HTTP 500");
        assertThat(script.posts).hasSize(2);
    }

    @Test
    void aRefusalByTheScriptIsReportedWithItsReason() {
        script.reply = "{\"ok\":false,\"error\":\"wrong secret\"}";
        assertThatThrownBy(() -> client.append(script.url(), "bad", List.of("A"), List.of("1"), "ref-4"))
                .hasMessageContaining("HTTP 200, wrong secret");
    }

    @Test
    void anUnreadableReplyIsNotAConfirmation() {
        script.reply = "<html>Sign in to Google</html>";
        assertThatThrownBy(() -> client.append(script.url(), "s", List.of("A"), List.of("1"), "ref-5"))
                .hasMessageContaining("HTTP 200").hasMessageNotContaining(", ");
    }

    @Test
    void anUnreachableSheetIsReported() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        assertThatThrownBy(() -> client.append("http://localhost:" + closedPort + "/exec", "s", List.of("A"), List.of("1"), "r"))
                .hasMessageContaining("after 2 attempts").hasMessageContaining("Exception");
    }

    @Test
    void atLeastOneAttemptIsMade() {
        script.status = 503;
        assertThatThrownBy(() -> new SheetClient(0, Duration.ZERO).append(script.url(), "s", List.of("A"), List.of("1"), "r"))
                .hasMessageContaining("after 1 attempts");
    }

    @Test
    void onlyHttpsOrALocalScriptIsAccepted() {
        assertThatNoException().isThrownBy(() -> SheetClient.checkedUri("https://script.google.com/macros/s/abc/exec"));
        assertThatNoException().isThrownBy(() -> SheetClient.checkedUri(" http://127.0.0.1:8080/exec "));
        assertThatThrownBy(() -> SheetClient.checkedUri("http://example.com/exec")).hasMessageContaining("must be an https URL");
        assertThatThrownBy(() -> SheetClient.checkedUri("ftp://localhost/exec")).hasMessageContaining("must be an https URL");
        assertThatThrownBy(() -> SheetClient.checkedUri("https:///exec")).hasMessageContaining("must be an https URL");
        assertThatThrownBy(() -> SheetClient.checkedUri("https://bad host/exec")).hasMessageContaining("not a valid URL");
    }

    @Test
    void interruptionWhileWaitingToRetryStopsAndKeepsTheFlag() throws InterruptedException {
        script.status = 500;
        var slowRetry = new SheetClient(3, Duration.ofSeconds(30));
        assertInterrupted(() -> slowRetry.append(script.url(), "s", List.of("A"), List.of("1"), "r"));
    }

    @Test
    void interruptionWhileSendingStopsAndKeepsTheFlag() throws InterruptedException {
        script.delay = Duration.ofSeconds(30);
        assertInterrupted(() -> client.append(script.url(), "s", List.of("A"), List.of("1"), "r"));
    }

    private void assertInterrupted(Runnable call) throws InterruptedException {
        var error = new AtomicReference<Throwable>();
        var flag = new AtomicBoolean();
        var worker = Thread.ofVirtual().start(() -> {
            try {
                call.run();
            } catch (Throwable e) {
                error.set(e);
                flag.set(Thread.currentThread().isInterrupted());
            }
        });
        await().atMost(Duration.ofSeconds(10)).until(() -> !script.posts.isEmpty());
        Thread.sleep(300); // let the first attempt reach its wait (the pause, or the slow reply)
        worker.interrupt();
        worker.join(Duration.ofSeconds(10));

        assertThat(error.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("Interrupted");
        assertThat(flag).isTrue();
    }
}
