package com.freshlink.notification.domain.restock;

/** 이 고객이 그 점포·상품에 재입고 알림을 신청했는지 */
public record RestockSubscriptionResponse(Long storeId, Long productId, boolean subscribed) {
}
