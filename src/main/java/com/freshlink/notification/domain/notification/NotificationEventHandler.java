package com.freshlink.notification.domain.notification;

import com.freshlink.common.idempotency.ProcessedMessages;
import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.notification.domain.restock.RestockSubscriptionService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NOTIFICATION 쪽지 하나로 알림을 만든다. 받는 사람은 종류로 정한다.
 * <ul>
 *   <li>EXPIRY_IMMINENT·EXPIRED: 그 점포 점주 (STORE)</li>
 *   <li>RESTOCKED: 그 점포·상품에 재입고 알림을 신청한 고객 전부 (USER). 알림을 만들면 신청을 지운다(한 번만 알림)</li>
 *   <li>userId가 있으면 그 고객 한 명에게만 (지금 보내는 곳은 없다)</li>
 * </ul>
 * 처리 기록(processed_message)·알림 저장·신청 삭제가 한 트랜잭션이라 중간에 실패하면 전부 되돌아가고 SQS가 다시 보낸다.
 * 같은 쪽지가 두 번 와도 알림은 한 번만 생긴다. 화면으로 보내기는 커밋된 뒤(NotificationStream)에 한다.
 */
@Service
public class NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final ProcessedMessages processedMessages;
    private final NotificationRepository repository;
    private final RestockSubscriptionService restockSubscriptions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationEventHandler(ProcessedMessages processedMessages, NotificationRepository repository,
                                    RestockSubscriptionService restockSubscriptions, ApplicationEventPublisher events,
                                    Clock clock) {
        this.processedMessages = processedMessages;
        this.repository = repository;
        this.restockSubscriptions = restockSubscriptions;
        this.events = events;
        this.clock = clock;
    }

    /** @return 만든 알림 수 (이미 처리한 쪽지면 0) */
    @Transactional
    public int handle(NotificationMessage message) {
        if (!processedMessages.markIfNew(message.messageId())) {
            log.info("이미 처리한 알림 쪽지 messageId={}", message.messageId());
            return 0;
        }
        List<Recipient> recipients = recipients(message);
        NotificationText text = NotificationText.of(message);
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Recipient recipient : recipients) {
            Notification saved = repository.save(Notification.create(recipient, message.kind(), message.storeId(),
                    message.productId(), text, message.messageId(), now));
            events.publishEvent(new NotificationCreatedEvent(recipient, NotificationResponse.from(saved)));
        }
        log.info("알림 {}건 생성 kind={} storeId={} productId={} messageId={}", recipients.size(), message.kind(),
                message.storeId(), message.productId(), message.messageId());
        return recipients.size();
    }

    private List<Recipient> recipients(NotificationMessage m) {
        if (m.userId() != null && !m.userId().isBlank()) {
            return List.of(Recipient.user(m.userId()));
        }
        return switch (m.kind()) {
            case EXPIRY_IMMINENT, EXPIRED -> List.of(Recipient.store(m.storeId()));
            case RESTOCKED -> restockSubscriptions.takeSubscribers(m.storeId(), m.productId()).stream()
                    .map(Recipient::user).toList();
        };
    }
}
