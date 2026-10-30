package com.freshlink.notification.domain.notification;

import com.freshlink.notification.common.web.AuthHeaders;
import com.freshlink.notification.common.web.Viewer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 화면 알림 (gateway /api/notifications/** → /notifications/**, 로그인 필요) */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStream notificationStream;

    public NotificationController(NotificationService notificationService, NotificationStream notificationStream) {
        this.notificationService = notificationService;
        this.notificationStream = notificationStream;
    }

    /** 내 알림 목록 (최신순, page는 0부터, size 최대 50) */
    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(value = AuthHeaders.STORE_ID, required = false) String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(Viewer.of(userId, role, storeId), page, size);
    }

    /** 안 읽은 알림 수 */
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(value = AuthHeaders.STORE_ID, required = false) String storeId) {
        return notificationService.unreadCount(Viewer.of(userId, role, storeId));
    }

    /** 알림 하나 읽음. 내 알림이 아니면 404 */
    @PatchMapping("/{id:\\d+}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(value = AuthHeaders.STORE_ID, required = false) String storeId,
            @PathVariable Long id) {
        notificationService.markRead(Viewer.of(userId, role, storeId), id);
        return ResponseEntity.noContent().build();
    }

    /** 내 알림 모두 읽음 */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(value = AuthHeaders.STORE_ID, required = false) String storeId) {
        notificationService.markAllRead(Viewer.of(userId, role, storeId));
        return ResponseEntity.noContent().build();
    }

    /**
     * 실시간 알림 SSE. 다시 붙을 때 브라우저가 보내는 Last-Event-ID 뒤의 알림을 먼저 보낸다.
     * 화면이 EventSource를 새로 만들 때(로그인 갱신 뒤 등)는 Last-Event-ID 헤더를 못 붙여서 ?lastEventId=로도 받는다
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestHeader(value = AuthHeaders.STORE_ID, required = false) String storeId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) String lastEventIdParam,
            HttpServletResponse response) {
        Viewer viewer = Viewer.of(userId, role, storeId);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        Long lastEventId = parseId(lastEventIdHeader != null ? lastEventIdHeader : lastEventIdParam);
        return notificationStream.subscribe(viewer.recipients(), lastEventId);
    }

    private static Long parseId(String raw) {
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
