package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.MessageJson;
import com.freshlink.common.message.notification.NotificationMessage;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 알림 문구와 누르면 갈 화면. 보내는 서비스는 사실만 싣고 문구는 여기서 한 곳에서 정한다.
 * 화면 경로는 frontend routes.tsx와 같다.
 */
public record NotificationText(String title, String body, String link) {

    static final String OWNER_EXPIRING_PAGE = "/owner/expiring";
    static final String OWNER_ORDERS_PAGE = "/owner/orders";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    public static NotificationText of(NotificationMessage m) {
        boolean named = m.productName() != null && !m.productName().isBlank();
        String product = named ? m.productName() : "상품";
        return switch (m.kind()) {
            case EXPIRY_IMMINENT -> new NotificationText("유통기한 임박",
                    product + quantity(m) + "의 유통기한이 " + when(m.expiresAt()) + "에 끝나요",
                    OWNER_EXPIRING_PAGE);
            case EXPIRED -> new NotificationText("유통기한 경과",
                    product + quantity(m) + "의 유통기한이 지났어요. 폐기해 주세요",
                    OWNER_EXPIRING_PAGE);
            case RESTOCKED -> new NotificationText("재입고 알림",
                    (named ? product + " 상품이" : "신청한 상품이") + " 다시 들어왔어요",
                    "/customer/stores/" + m.storeId() + "/products/" + m.productId());
            case NEW_ORDER -> new NotificationText("새 픽업 주문",
                    product + quantity(m) + " 주문이 들어왔어요" + orderNo(m),
                    OWNER_ORDERS_PAGE);
            case PICKUP_READY -> new NotificationText("픽업 준비 완료",
                    store(m) + product + quantity(m) + " 준비가 끝났어요. 가게에서 받아 가세요",
                    m.orderId() == null ? "/customer/orders" : "/customer/orders/" + m.orderId());
        };
    }

    private static String quantity(NotificationMessage m) {
        return m.quantity() == null ? "" : " " + m.quantity() + "개";
    }

    private static String orderNo(NotificationMessage m) {
        return m.orderNo() == null || m.orderNo().isBlank() ? "" : " (" + m.orderNo() + ")";
    }

    private static String store(NotificationMessage m) {
        return m.storeName() == null || m.storeName().isBlank() ? "" : m.storeName() + " ";
    }

    private static String when(OffsetDateTime t) {
        return t == null ? "곧" : t.atZoneSameInstant(MessageJson.KST).format(WHEN);
    }
}
