package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationMessage;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextTest {

    @Test
    void 유통기한_시각은_한국_시간으로_적는다() {
        NotificationText t = NotificationText.of(NotificationMessage.expiryImminent(1L, 15L, "우유", 3L,
                OffsetDateTime.parse("2026-10-31T00:30:00Z"), 3));

        assertThat(t.body()).isEqualTo("우유 3개의 유통기한이 10월 31일 09:30에 끝나요");
    }

    @Test
    void 상품_이름이_없어도_문구를_만든다() {
        NotificationText t = NotificationText.of(NotificationMessage.restocked(1L, 15L, null, 3));

        assertThat(t.title()).isEqualTo("재입고 알림");
        assertThat(t.body()).isEqualTo("신청한 상품이 다시 들어왔어요");
    }
}
