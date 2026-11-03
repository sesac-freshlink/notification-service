package com.freshlink.notification.domain.restock;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재입고 알림 신청. 품절인지는 이 서비스가 모른다(재고는 inventory). 화면이 품절 상품 상세에서만 버튼을 보여 준다.
 * RESTOCKED 쪽지가 오면 신청한 고객을 꺼내고 그 신청을 지운다(한 번만 알림, 다시 받으려면 다시 신청).
 */
@Service
public class RestockSubscriptionService {

    private final RestockSubscriptionRepository repository;
    private final Clock clock;

    public RestockSubscriptionService(RestockSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public RestockSubscriptionResponse subscribe(String userId, Long storeId, Long productId) {
        repository.insertIfAbsent(userId, storeId, productId, OffsetDateTime.now(clock));
        return new RestockSubscriptionResponse(storeId, productId, true);
    }

    @Transactional
    public void unsubscribe(String userId, Long storeId, Long productId) {
        repository.deleteOne(userId, storeId, productId);
    }

    @Transactional(readOnly = true)
    public RestockSubscriptionResponse status(String userId, Long storeId, Long productId) {
        return new RestockSubscriptionResponse(storeId, productId,
                repository.existsByUserIdAndStoreIdAndProductId(userId, storeId, productId));
    }

    /**
     * 재입고 알림을 받을 고객을 꺼내고 그 신청을 지운다. 알림 저장과 같은 트랜잭션에서만 부른다.
     * 읽은 행만 지우므로, 그 사이에 새로 들어온 신청은 다음 재입고 때 알림을 받는다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> takeSubscribers(Long storeId, Long productId) {
        List<RestockSubscription> subs = repository.findByStoreIdAndProductIdOrderByIdAsc(storeId, productId);
        if (subs.isEmpty()) {
            return List.of();
        }
        repository.deleteAllByIdInBatch(subs.stream().map(RestockSubscription::getId).toList());
        return subs.stream().map(RestockSubscription::getUserId).toList();
    }
}
