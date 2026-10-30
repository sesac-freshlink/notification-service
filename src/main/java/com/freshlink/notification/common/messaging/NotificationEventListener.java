package com.freshlink.notification.common.messaging;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageJson;
import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.notification.domain.notification.NotificationEventHandler;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * notification-events 큐(NOTIFICATION)를 받는다. 처리가 끝나야 쪽지를 지운다(기본 ON_SUCCESS).
 * 예외가 나면 쪽지가 남아 SQS가 다시 보내고, 3번 실패하면 DLQ로 간다(인프라 maxReceiveCount).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationEventHandler handler;

    public NotificationEventListener(NotificationEventHandler handler) {
        this.handler = handler;
    }

    @SqsListener("${app.queue.notifications}")
    public void onMessage(String body) {
        BaseMessage message = MessageJson.fromJson(body);
        if (message instanceof NotificationMessage notification) {
            handler.handle(notification);
            return;
        }
        // 다른 종류의 쪽지가 잘못 들어왔다. 다시 받아도 처리할 수 없으므로 지운다
        log.warn("알림 큐에 알림이 아닌 쪽지가 왔다 type={} messageId={}", message.type(), message.messageId());
    }
}
