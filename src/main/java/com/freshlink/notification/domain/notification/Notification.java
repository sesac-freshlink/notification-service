package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 화면 알림 한 건 (표 notification, V1). id는 SSE 이벤트 id로도 쓴다 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    @Column(nullable = false)
    private Long storeId;

    private Long productId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    private String link;

    private OffsetDateTime readAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private String sourceMessageId;

    protected Notification() {
    }

    public static Notification create(Recipient recipient, NotificationKind kind, Long storeId, Long productId,
                                      NotificationText text, String sourceMessageId, OffsetDateTime now) {
        Notification n = new Notification();
        n.recipient = recipient.key();
        n.kind = kind;
        n.storeId = storeId;
        n.productId = productId;
        n.title = text.title();
        n.body = text.body();
        n.link = text.link();
        n.createdAt = now;
        n.sourceMessageId = sourceMessageId;
        return n;
    }

    /** 이미 읽었으면 그대로 둔다 (처음 읽은 시각을 남긴다) */
    public void markRead(OffsetDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }
}
