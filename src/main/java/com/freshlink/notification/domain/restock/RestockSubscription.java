package com.freshlink.notification.domain.restock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 재입고 알림 신청 한 건 (표 restock_subscription, V1). 고객·점포·상품마다 하나 */
@Entity
@Table(name = "restock_subscription")
public class RestockSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected RestockSubscription() {
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getProductId() {
        return productId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
