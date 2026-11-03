package com.freshlink.notification.domain.restock;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestockSubscriptionRepository extends JpaRepository<RestockSubscription, Long> {

    /** 이미 신청했으면 아무것도 하지 않는다 (두 번 눌러도, 동시에 눌러도 한 건) */
    @Modifying
    @Query(value = "INSERT IGNORE INTO restock_subscription (user_id, store_id, product_id, created_at) "
            + "VALUES (:userId, :storeId, :productId, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId, @Param("storeId") Long storeId,
                       @Param("productId") Long productId, @Param("now") OffsetDateTime now);

    boolean existsByUserIdAndStoreIdAndProductId(String userId, Long storeId, Long productId);

    @Modifying
    @Query("delete from RestockSubscription s where s.userId = :userId and s.storeId = :storeId and s.productId = :productId")
    int deleteOne(@Param("userId") String userId, @Param("storeId") Long storeId, @Param("productId") Long productId);

    List<RestockSubscription> findByStoreIdAndProductIdOrderByIdAsc(Long storeId, Long productId);
}
