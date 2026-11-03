package com.freshlink.notification.common.error;

import com.freshlink.common.error.ErrorCode;

/**
 * notification-service 오류 목록. 응답 code는 enum 이름이다.
 * 추가할 땐 여기에 한 줄만 넣는다: {@code XXX(404, "메시지")}. 핸들러는 안 건드린다.
 */
public enum NotificationErrorCode implements ErrorCode {

    LOGIN_REQUIRED(401, "로그인이 필요합니다"),
    CUSTOMER_ONLY(403, "고객만 재입고 알림을 신청할 수 있습니다"),
    NOTIFICATION_NOT_FOUND(404, "알림이 없습니다");

    private final int status;
    private final String message;

    NotificationErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
