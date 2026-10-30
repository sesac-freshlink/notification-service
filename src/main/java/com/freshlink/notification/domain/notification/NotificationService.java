package com.freshlink.notification.domain.notification;

import com.freshlink.common.error.BusinessException;
import com.freshlink.notification.common.error.NotificationErrorCode;
import com.freshlink.notification.common.web.Viewer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 알림 조회·읽음 처리. 고객은 자기 알림, 점주는 자기 점포 알림까지 본다 (Viewer.recipients) */
@Service
public class NotificationService {

    static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Viewer viewer, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.of(repository.findByRecipientInOrderByIdDesc(keys(viewer.recipients()),
                PageRequest.of(safePage, safeSize)), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Viewer viewer) {
        return new UnreadCountResponse(repository.countByRecipientInAndReadAtIsNull(keys(viewer.recipients())));
    }

    /** 내 알림이 아니면 404 (남의 알림이 있는지도 알려 주지 않는다). 이미 읽었으면 그대로 성공 */
    @Transactional
    public void markRead(Viewer viewer, Long id) {
        Notification n = repository.findByIdAndRecipientIn(id, keys(viewer.recipients()))
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead(OffsetDateTime.now(clock));
    }

    @Transactional
    public void markAllRead(Viewer viewer) {
        repository.markAllRead(keys(viewer.recipients()), OffsetDateTime.now(clock));
    }

    /** SSE로 다시 붙었을 때 놓친 알림 (lastId 뒤, 오래된 순, 최대 limit건) */
    @Transactional(readOnly = true)
    public List<NotificationResponse> after(List<Recipient> recipients, long lastId, int limit) {
        return repository.findByRecipientInAndIdGreaterThanOrderByIdAsc(keys(recipients), lastId, Limit.of(limit))
                .stream().map(NotificationResponse::from).toList();
    }

    private static List<String> keys(List<Recipient> recipients) {
        return recipients.stream().map(Recipient::key).toList();
    }
}
