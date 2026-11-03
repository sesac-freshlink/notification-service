-- 화면 알림 (받는 사람, 종류, 내용, 읽음 여부, 만든 시각)
-- 받는 사람(recipient): 'USER:<Cognito sub>' = 고객 한 명, 'STORE:<storeId>' = 그 점포 점주
-- id는 SSE 이벤트 id로도 쓴다(Last-Event-ID 뒤의 알림을 다시 보낸다)
CREATE TABLE notification (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient         VARCHAR(80)  NOT NULL,
    kind              VARCHAR(30)  NOT NULL,          -- EXPIRY_IMMINENT, EXPIRED, RESTOCKED
    store_id          BIGINT       NOT NULL,
    product_id        BIGINT       NULL,
    title             VARCHAR(50)  NOT NULL,
    body              VARCHAR(300) NOT NULL,
    link              VARCHAR(200) NULL,              -- 알림을 누르면 갈 화면 경로
    read_at           DATETIME(6)  NULL,              -- NULL이면 안 읽음
    created_at        DATETIME(6)  NOT NULL,
    source_message_id VARCHAR(36)  NOT NULL,          -- 이 알림을 만든 쪽지 messageId
    UNIQUE KEY uk_notification_source (source_message_id, recipient),
    KEY idx_notification_recipient (recipient, id)
);

-- 재입고 알림 신청 (고객·점포·상품 단위). 재입고 알림을 만들면 지운다(한 번만 알림)
CREATE TABLE restock_subscription (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,                  -- Cognito sub (X-User-Id)
    store_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_restock_subscription (user_id, store_id, product_id),
    KEY idx_restock_subscription_product (store_id, product_id)
);

-- 받은 쪽지 기록 (common ProcessedMessages). 같은 쪽지를 두 번 받아도 알림을 한 번만 만든다
CREATE TABLE processed_message (
    message_id   VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME(6) NOT NULL
);
