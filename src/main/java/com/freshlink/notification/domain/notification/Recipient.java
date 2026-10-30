package com.freshlink.notification.domain.notification;

/**
 * 알림 받는 사람. 표 notification.recipient에 이 key가 들어간다.
 * <ul>
 *   <li>{@code USER:<Cognito sub>}: 고객 한 명 (재입고 알림)</li>
 *   <li>{@code STORE:<storeId>}: 그 점포 점주 (유통기한 임박·경과)</li>
 * </ul>
 */
public record Recipient(String key) {

    public static Recipient user(String userId) {
        return new Recipient("USER:" + userId);
    }

    public static Recipient store(Long storeId) {
        return new Recipient("STORE:" + storeId);
    }
}
