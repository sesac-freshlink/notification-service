package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationKind;
import java.time.OffsetDateTime;

/** 알림 한 건. 목록 API와 SSE 이벤트 data가 같은 모양이다 */
public record NotificationResponse(
        Long id,
        NotificationKind kind,
        String title,
        String body,
        String link,
        boolean read,
        OffsetDateTime createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getLink(), n.isRead(),
                n.getCreatedAt());
    }
}
