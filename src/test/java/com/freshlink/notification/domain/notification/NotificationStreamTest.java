package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationKind;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 알림 SSE: 연결 시작 주석, 받는 사람 연결에만 전송, 20초 heartbeat, Last-Event-ID 다시 보내기, 끊긴 연결 정리 */
class NotificationStreamTest {

    static final Recipient OWNER_STORE = Recipient.store(7L);
    static final Recipient OWNER_USER = Recipient.user("owner-sub");
    static final Recipient CUSTOMER = Recipient.user("customer-sub");

    NotificationService service;
    List<RecordingEmitter> created;
    NotificationStream stream;

    /** 보낸 이벤트를 글자로 모아 두는 emitter. fail이면 보내기에서 IOException */
    static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (fail) {
                throw new IOException("끊김");
            }
            sent.add(builder.build().stream().map(d -> String.valueOf(d.getData())).collect(Collectors.joining()));
        }
    }

    static NotificationResponse notification(long id) {
        return new NotificationResponse(id, NotificationKind.EXPIRY_IMMINENT, "유통기한 임박", "우유 1개", "/owner/expiring",
                false, OffsetDateTime.parse("2026-10-31T00:00:00+09:00"));
    }

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        created = new ArrayList<>();
        stream = new NotificationStream(service, 60_000, 50) {
            @Override
            SseEmitter newEmitter() {
                RecordingEmitter e = new RecordingEmitter();
                created.add(e);
                return e;
            }
        };
    }

    @Test
    void 연결하면_응답을_바로_시작하는_주석과_재연결_간격을_보낸다() {
        stream.subscribe(List.of(OWNER_USER, OWNER_STORE), null);

        assertThat(created.get(0).sent).hasSize(1);
        assertThat(created.get(0).sent.get(0)).contains(":connected").contains("retry:3000");
        assertThat(stream.connections(OWNER_STORE)).isEqualTo(1);
        assertThat(stream.connections(OWNER_USER)).isEqualTo(1);
        verifyNoInteractions(service);
    }

    @Test
    void 새_알림은_그_받는_사람_연결에만_id와_이벤트_이름을_붙여_보낸다() {
        stream.subscribe(List.of(OWNER_USER, OWNER_STORE), null);
        stream.subscribe(List.of(CUSTOMER), null);

        stream.onCreated(new NotificationCreatedEvent(OWNER_STORE, notification(41)));

        assertThat(created.get(0).sent).hasSize(2);
        assertThat(created.get(0).sent.get(1)).contains("id:41").contains("event:notification")
                .contains("title=유통기한 임박");
        assertThat(created.get(1).sent).hasSize(1);
    }

    @Test
    void Last_Event_ID가_있으면_그_뒤_알림을_먼저_보낸다() {
        when(service.after(List.of(CUSTOMER), 40L, 50)).thenReturn(List.of(notification(41), notification(42)));

        stream.subscribe(List.of(CUSTOMER), 40L);

        assertThat(created.get(0).sent).hasSize(3);
        assertThat(created.get(0).sent.get(1)).contains("id:41");
        assertThat(created.get(0).sent.get(2)).contains("id:42");
    }

    @Test
    void heartbeat는_모든_연결에_빈_이벤트를_보내고_끊긴_연결은_뺀다() {
        stream.subscribe(List.of(CUSTOMER), null);
        stream.subscribe(List.of(CUSTOMER), null);
        created.get(1).fail = true;

        stream.heartbeat();

        assertThat(created.get(0).sent).hasSize(2);
        assertThat(created.get(0).sent.get(1)).contains(":heartbeat");
        assertThat(stream.connections(CUSTOMER)).isEqualTo(1);
    }

    @Test
    void 보내기에_실패한_연결은_빼고_밖으로_던지지_않는다() {
        stream.subscribe(List.of(CUSTOMER), null);
        created.get(0).fail = true;

        stream.onCreated(new NotificationCreatedEvent(CUSTOMER, notification(50)));

        assertThat(stream.connections(CUSTOMER)).isZero();
    }

    @Test
    void 연결이_없는_받는_사람의_알림은_아무것도_하지_않는다() {
        stream.onCreated(new NotificationCreatedEvent(CUSTOMER, notification(51)));

        assertThat(created).isEmpty();
        verify(service, org.mockito.Mockito.never()).after(anyList(), anyLong(), anyInt());
    }
}
