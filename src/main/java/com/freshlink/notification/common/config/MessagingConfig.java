package com.freshlink.notification.common.config;

import com.freshlink.common.idempotency.ProcessedMessages;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 받은 쪽지 멱등 처리(common ProcessedMessages, 표 processed_message).
 * 이 서비스는 쪽지를 받기만 해서 common Outbox 발행기(freshlink.messaging.enabled)는 켜지 않고 이 빈만 직접 만든다.
 */
@Configuration
public class MessagingConfig {

    @Bean
    public ProcessedMessages processedMessages(DataSource dataSource) {
        return new ProcessedMessages(new JdbcTemplate(dataSource));
    }
}
