package com.freshlink.notification.domain.notification;

/** 알림이 저장됐다. 커밋된 뒤 NotificationStream이 그 받는 사람의 SSE 연결로 보낸다 */
public record NotificationCreatedEvent(Recipient recipient, NotificationResponse notification) {
}
