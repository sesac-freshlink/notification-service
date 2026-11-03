package com.freshlink.notification.domain.notification;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 목록 (최신순) */
    Page<Notification> findByRecipientInOrderByIdDesc(Collection<String> recipients, Pageable pageable);

    long countByRecipientInAndReadAtIsNull(Collection<String> recipients);

    Optional<Notification> findByIdAndRecipientIn(Long id, Collection<String> recipients);

    /** SSE 다시 붙기: Last-Event-ID 뒤의 알림 (오래된 순) */
    List<Notification> findByRecipientInAndIdGreaterThanOrderByIdAsc(Collection<String> recipients, Long id, Limit limit);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient in :recipients and n.readAt is null")
    int markAllRead(@Param("recipients") Collection<String> recipients, @Param("now") OffsetDateTime now);
}
