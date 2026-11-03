package com.freshlink.notification.domain.notification;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 화면 알림 SSE (GET /notifications/stream). 로그인 사용자의 받는 사람(고객 USER, 점주는 STORE도)마다 연결을 기억하고,
 * 새 알림이 커밋되면 그 받는 사람의 연결로 notification 이벤트를 보낸다(이벤트 id = 알림 id).
 * <ul>
 *   <li>연결하자마자 주석 한 줄을 보내 응답을 바로 시작한다(CloudFront는 첫 바이트를 30초까지만 기다린다)</li>
 *   <li>20초마다 빈 이벤트(heartbeat). CloudFront origin 응답 타임아웃 30초·ALB 유휴 타임아웃 60초보다 짧다</li>
 *   <li>다시 붙을 때 Last-Event-ID 뒤의 알림을 먼저 보낸다. 같은 알림이 두 번 갈 수 있어 화면이 id로 거른다</li>
 * </ul>
 *
 * <p>연결은 이 파드 메모리에만 있다. notification 파드는 1개로 운영한다(gitops). 늘리려면 Redis Pub/Sub 같은 방법을 먼저 붙인다.
 */
@Component
public class NotificationStream {

    /** 이벤트 이름. 화면은 이 이름으로 받는다 */
    static final String EVENT = "notification";
    /** 끊긴 뒤 다시 붙기까지 기다리는 시간(ms). EventSource가 이 값을 쓴다 */
    static final long RETRY_MS = 3000;

    private static final Logger log = LoggerFactory.getLogger(NotificationStream.class);

    private final NotificationService notificationService;
    private final long timeoutMs;
    private final int replayLimit;
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationStream(NotificationService notificationService,
                              @Value("${app.stream.timeout-ms:1800000}") long timeoutMs,
                              @Value("${app.stream.replay-limit:50}") int replayLimit) {
        this.notificationService = notificationService;
        this.timeoutMs = timeoutMs;
        this.replayLimit = replayLimit;
    }

    /** 연결을 더하고, lastEventId가 있으면 그 뒤 알림을 먼저 보낸다 */
    public SseEmitter subscribe(List<Recipient> recipients, Long lastEventId) {
        SseEmitter emitter = newEmitter();
        for (Recipient r : recipients) {
            emitters.computeIfAbsent(r.key(), k -> ConcurrentHashMap.newKeySet()).add(emitter);
        }
        Runnable remove = () -> remove(recipients, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        if (!sendRaw(emitter, SseEmitter.event().comment("connected").reconnectTime(RETRY_MS))) {
            remove.run();
            return emitter;
        }
        if (lastEventId != null && lastEventId > 0) {
            for (NotificationResponse missed : notificationService.after(recipients, lastEventId, replayLimit)) {
                if (!send(emitter, missed)) {
                    remove.run();
                    break;
                }
            }
        }
        return emitter;
    }

    /** 알림 저장이 커밋된 뒤 그 받는 사람의 연결로 보낸다. 실패해도 알림 저장에는 영향이 없다(목록·다시 붙기로 본다) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        Set<SseEmitter> targets = emitters.get(event.recipient().key());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            if (!send(emitter, event.notification())) {
                targets.remove(emitter);
            }
        }
    }

    /** 20초마다 빈 이벤트. CloudFront(30초)·ALB(60초)가 쉬는 연결을 끊지 않게 한다 */
    @Scheduled(fixedRateString = "${app.stream.heartbeat-ms:20000}")
    public void heartbeat() {
        emitters.forEach((key, targets) -> {
            for (SseEmitter emitter : targets) {
                if (!sendRaw(emitter, SseEmitter.event().comment("heartbeat"))) {
                    targets.remove(emitter);
                }
            }
        });
        emitters.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** 테스트·지표용: 받는 사람의 지금 연결 수 */
    int connections(Recipient recipient) {
        Set<SseEmitter> targets = emitters.get(recipient.key());
        return targets == null ? 0 : targets.size();
    }

    /** 테스트에서 보낸 이벤트를 잡으려고 바꿔 끼운다 */
    SseEmitter newEmitter() {
        return new SseEmitter(timeoutMs);
    }

    private boolean send(SseEmitter emitter, NotificationResponse n) {
        return sendRaw(emitter, SseEmitter.event().id(String.valueOf(n.id())).name(EVENT)
                .data(n, MediaType.APPLICATION_JSON));
    }

    private boolean sendRaw(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 전송 실패, 연결을 뺀다 ({})", e.toString());
            return false;
        }
    }

    private void remove(List<Recipient> recipients, SseEmitter emitter) {
        for (Recipient r : recipients) {
            emitters.computeIfPresent(r.key(), (k, targets) -> {
                targets.remove(emitter);
                return targets.isEmpty() ? null : targets;
            });
        }
    }
}
