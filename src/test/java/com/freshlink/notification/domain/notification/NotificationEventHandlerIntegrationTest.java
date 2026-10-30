package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationKind;
import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.notification.TestcontainersConfiguration;
import com.freshlink.notification.domain.restock.RestockSubscriptionService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** NOTIFICATION 쪽지 → 알림 저장. 실제 MySQL. 점포·상품은 테스트마다 새 번호를 써서 섞이지 않게 한다 */
@SpringBootTest(properties = {"app.queue.notifications=test-notification-events",
        "spring.cloud.aws.sqs.listener.auto-startup=false"})
@Import(TestcontainersConfiguration.class)
class NotificationEventHandlerIntegrationTest {

    @Autowired
    NotificationEventHandler handler;
    @Autowired
    RestockSubscriptionService restock;
    @Autowired
    JdbcTemplate jdbc;

    static long nextId() {
        return 1_000_000L + (System.nanoTime() % 1_000_000_000L);
    }

    List<String> recipients(String messageId) {
        return jdbc.queryForList("SELECT recipient FROM notification WHERE source_message_id = ? ORDER BY id",
                String.class, messageId);
    }

    @Test
    void 유통기한_임박은_그_점포_점주에게_문구와_링크를_붙여_한_건() {
        long store = nextId();
        NotificationMessage m = NotificationMessage.expiryImminent(store, 15L, "매운 컵라면 115g", 300L,
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"), 4);

        assertThat(handler.handle(m)).isEqualTo(1);

        assertThat(recipients(m.messageId())).containsExactly("STORE:" + store);
        var row = jdbc.queryForMap("SELECT kind, title, body, link, read_at FROM notification WHERE source_message_id = ?",
                m.messageId());
        assertThat(row.get("kind")).isEqualTo("EXPIRY_IMMINENT");
        assertThat(row.get("title")).isEqualTo("유통기한 임박");
        assertThat(row.get("body")).isEqualTo("매운 컵라면 115g 4개의 유통기한이 10월 31일 23:59에 끝나요");
        assertThat(row.get("link")).isEqualTo("/owner/expiring");
        assertThat(row.get("read_at")).isNull();
    }

    @Test
    void 같은_쪽지를_두_번_받아도_알림은_한_번() {
        NotificationMessage m = NotificationMessage.expired(nextId(), 15L, "우유", 301L,
                OffsetDateTime.parse("2026-10-30T23:59:59+09:00"), 2);

        assertThat(handler.handle(m)).isEqualTo(1);
        assertThat(handler.handle(m)).isZero();

        assertThat(recipients(m.messageId())).hasSize(1);
    }

    @Test
    void 재입고는_신청한_고객에게만_가고_신청은_지워진다() {
        long store = nextId();
        long product = nextId();
        String alice = UUID.randomUUID().toString();
        String bob = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        restock.subscribe(alice, store, product);
        restock.subscribe(bob, store, product);
        restock.subscribe(other, store, product + 1);   // 다른 상품

        NotificationMessage m = NotificationMessage.restocked(store, product, "바나나우유", 20);
        assertThat(handler.handle(m)).isEqualTo(2);

        assertThat(recipients(m.messageId())).containsExactlyInAnyOrder("USER:" + alice, "USER:" + bob);
        assertThat(jdbc.queryForObject("SELECT link FROM notification WHERE source_message_id = ? LIMIT 1",
                String.class, m.messageId())).isEqualTo("/customer/stores/" + store + "/products/" + product);
        assertThat(restock.status(alice, store, product).subscribed()).isFalse();
        assertThat(restock.status(other, store, product + 1).subscribed()).isTrue();

        // 한 번만 알림: 다음 재입고에는 신청이 없어 알림이 없다
        assertThat(handler.handle(NotificationMessage.restocked(store, product, "바나나우유", 5))).isZero();
    }

    @Test
    void userId가_있으면_그_고객_한_명에게만() {
        String user = UUID.randomUUID().toString();
        NotificationMessage m = new NotificationMessage(UUID.randomUUID().toString(), OffsetDateTime.now(),
                NotificationKind.RESTOCKED, nextId(), user, 15L, "우유", null, null, 3);

        assertThat(handler.handle(m)).isEqualTo(1);
        assertThat(recipients(m.messageId())).containsExactly("USER:" + user);
    }
}
