package com.freshlink.notification.domain.restock;

import com.freshlink.notification.common.web.AuthHeaders;
import com.freshlink.notification.common.web.Viewer;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재입고 알림 신청 (고객만). 상품 번호는 경로, 점포는 ?storeId=로 받는다(같은 상품도 점포마다 재고가 다르다).
 * <ul>
 *   <li>GET: 신청했는지 (버튼 모양)</li>
 *   <li>POST: 신청. 이미 했으면 그대로 200</li>
 *   <li>DELETE: 취소. 신청이 없어도 204</li>
 * </ul>
 */
@RestController
@RequestMapping("/notifications/restock/{productId}")
public class RestockSubscriptionController {

    private final RestockSubscriptionService service;

    public RestockSubscriptionController(RestockSubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    public RestockSubscriptionResponse status(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @PathVariable @Positive Long productId,
            @RequestParam @Positive Long storeId) {
        Viewer viewer = customer(userId, role);
        return service.status(viewer.userId(), storeId, productId);
    }

    @PostMapping
    public RestockSubscriptionResponse subscribe(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @PathVariable @Positive Long productId,
            @RequestParam @Positive Long storeId) {
        Viewer viewer = customer(userId, role);
        return service.subscribe(viewer.userId(), storeId, productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @PathVariable @Positive Long productId,
            @RequestParam @Positive Long storeId) {
        Viewer viewer = customer(userId, role);
        service.unsubscribe(viewer.userId(), storeId, productId);
        return ResponseEntity.noContent().build();
    }

    private static Viewer customer(String userId, String role) {
        Viewer viewer = Viewer.of(userId, role, null);
        viewer.requireCustomer();
        return viewer;
    }
}
