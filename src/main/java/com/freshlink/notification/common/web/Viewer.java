package com.freshlink.notification.common.web;

import com.freshlink.common.error.BusinessException;
import com.freshlink.notification.common.error.NotificationErrorCode;
import com.freshlink.notification.domain.notification.Recipient;
import java.util.ArrayList;
import java.util.List;

/**
 * 지금 요청한 사람. gateway 헤더(X-User-Id·X-User-Role·X-Store-Id)로 만든다.
 * 고객은 자기 알림(USER), 점주는 자기 점포 알림(STORE)도 같이 본다.
 */
public record Viewer(String userId, String roles, Long storeId) {

    public static Viewer of(String userId, String roles, String storeId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(NotificationErrorCode.LOGIN_REQUIRED);
        }
        return new Viewer(userId, roles, parseStoreId(storeId));
    }

    public boolean isCustomer() {
        return AuthHeaders.hasRole(roles, AuthHeaders.ROLE_CUSTOMER);
    }

    public boolean isOwner() {
        return AuthHeaders.hasRole(roles, AuthHeaders.ROLE_OWNER);
    }

    /** 이 사람이 받는 알림의 받는 사람 목록. 점포가 연결된 점주면 그 점포가 더해진다 */
    public List<Recipient> recipients() {
        List<Recipient> list = new ArrayList<>(2);
        list.add(Recipient.user(userId));
        if (isOwner() && storeId != null) {
            list.add(Recipient.store(storeId));
        }
        return List.copyOf(list);
    }

    /** 재입고 알림 신청은 고객만 한다 */
    public void requireCustomer() {
        if (!isCustomer()) {
            throw new BusinessException(NotificationErrorCode.CUSTOMER_ONLY);
        }
    }

    private static Long parseStoreId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
