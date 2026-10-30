package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.notification.TestcontainersConfiguration;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 HTTP로 SSE를 열고, 쪽지 처리(커밋) 뒤 이벤트가 바로 오는지·다시 붙을 때 놓친 알림이 오는지 본다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.queue.notifications=test-notification-events", "spring.cloud.aws.sqs.listener.auto-startup=false",
        "app.stream.heartbeat-ms=500"})
@Import(TestcontainersConfiguration.class)
class NotificationStreamHttpTest {

    @LocalServerPort
    int port;
    @Autowired
    NotificationEventHandler handler;

    final HttpClient http = HttpClient.newHttpClient();

    static long nextStore() {
        return 3_000_000L + (System.nanoTime() % 1_000_000_000L);
    }

    HttpResponse<InputStream> open(long store, String lastEventId) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/notifications/stream"))
                .header("Accept", "text/event-stream")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "OWNER")
                .header("X-Store-Id", String.valueOf(store)).GET();
        if (lastEventId != null) {
            req.header("Last-Event-ID", lastEventId);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    /** 빈 줄로 끝나는 이벤트 하나를 읽는다 (heartbeat 주석만 있는 이벤트는 건너뛴다) */
    static List<String> nextEvent(BufferedReader body) throws Exception {
        while (true) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = body.readLine()) != null && !line.isEmpty()) {
                lines.add(line);
            }
            if (line == null) {
                return lines;
            }
            if (lines.stream().anyMatch(l -> l.startsWith("data:"))) {
                return lines;
            }
        }
    }

    @Test
    void 연결한_점주에게_새_알림이_바로_오고_다시_붙으면_놓친_알림이_온다() throws Exception {
        long store = nextStore();
        HttpResponse<InputStream> res = open(store, null);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/event-stream"));

        try (BufferedReader body = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
            assertThat(body.readLine()).isEqualTo(":connected");
            assertThat(body.readLine()).isEqualTo("retry:3000");
            handler.handle(NotificationMessage.expired(store, 15L, "우유", 300L,
                    OffsetDateTime.parse("2026-10-30T23:59:59+09:00"), 2));

            List<String> event = CompletableFuture.supplyAsync(() -> {
                try {
                    return nextEvent(body);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).get(5, TimeUnit.SECONDS);
            assertThat(event).anyMatch(l -> l.equals("event:notification"));
            assertThat(event).anyMatch(l -> l.startsWith("data:") && l.contains("\"kind\":\"EXPIRED\"")
                    && l.contains("우유 2개의 유통기한이 지났어요"));
            String id = event.stream().filter(l -> l.startsWith("id:")).findFirst().orElseThrow().substring(3);

            // 끊긴 사이에 온 알림
            handler.handle(NotificationMessage.expiryImminent(store, 16L, "빵", 301L,
                    OffsetDateTime.parse("2026-10-31T09:00:00+09:00"), 1));
            HttpResponse<InputStream> again = open(store, id);
            try (BufferedReader body2 = new BufferedReader(new InputStreamReader(again.body(), StandardCharsets.UTF_8))) {
                List<String> missed = nextEvent(body2);
                assertThat(missed).anyMatch(l -> l.startsWith("data:") && l.contains("빵 1개"));
                assertThat(missed).anyMatch(l -> l.startsWith("id:") && Long.parseLong(l.substring(3)) > Long.parseLong(id));
            }
        }
    }

    @Test
    void 로그인_헤더가_없으면_SSE도_401_JSON() throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/notifications/stream"))
                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.body()).contains("LOGIN_REQUIRED");
    }
}
