package com.freshlink.notification.domain.notification;

import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.notification.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림 목록·안 읽은 수·읽음, 재입고 신청 API. 실제 MySQL */
@SpringBootTest(properties = {"app.queue.notifications=test-notification-events",
        "spring.cloud.aws.sqs.listener.auto-startup=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    NotificationEventHandler handler;
    @Autowired
    JdbcTemplate jdbc;

    static long nextId() {
        return 2_000_000L + (System.nanoTime() % 1_000_000_000L);
    }

    void expiry(long store, String product) {
        handler.handle(NotificationMessage.expiryImminent(store, 15L, product, 300L,
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"), 1));
    }

    @Test
    void 점주는_자기_점포_알림만_최신순으로_보고_안_읽은_수가_맞다() throws Exception {
        long store = nextId();
        expiry(store, "첫째");
        expiry(store, "둘째");
        expiry(store + 1, "남의 점포");

        mockMvc.perform(get("/notifications").header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].body").value("둘째 1개의 유통기한이 10월 31일 23:59에 끝나요"))
                .andExpect(jsonPath("$.content[0].kind").value("EXPIRY_IMMINENT"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[1].body").value("첫째 1개의 유통기한이 10월 31일 23:59에 끝나요"));

        mockMvc.perform(get("/notifications/unread-count").header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void 읽음_처리와_모두_읽음_남의_알림은_404() throws Exception {
        long store = nextId();
        expiry(store, "우유");
        expiry(store, "빵");
        long id = jdbc.queryForObject("SELECT MIN(id) FROM notification WHERE recipient = ?", Long.class,
                "STORE:" + store);
        String owner = UUID.randomUUID().toString();

        mockMvc.perform(patch("/notifications/{id}/read", id).header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store + 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(patch("/notifications/{id}/read", id).header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/notifications/{id}/read", id).header("X-User-Id", owner)
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/notifications/{id}/read", id).header("X-User-Id", owner)
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/notifications/unread-count").header("X-User-Id", owner)
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(patch("/notifications/read-all").header("X-User-Id", owner)
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/notifications/unread-count").header("X-User-Id", owner)
                        .header("X-User-Role", "OWNER").header("X-Store-Id", String.valueOf(store)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void 사용자_헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 고객은_재입고_알림을_신청_확인_취소하고_재입고되면_알림을_받는다() throws Exception {
        long store = nextId();
        long product = nextId();
        String user = UUID.randomUUID().toString();

        mockMvc.perform(get("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                            .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscribed").value(true))
                    .andExpect(jsonPath("$.productId").value(product))
                    .andExpect(jsonPath("$.storeId").value(store));
        }
        mockMvc.perform(get("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(jsonPath("$.subscribed").value(true));

        handler.handle(NotificationMessage.restocked(store, product, "바나나우유", 10));

        mockMvc.perform(get("/notifications").header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].kind").value("RESTOCKED"))
                .andExpect(jsonPath("$.content[0].body").value("바나나우유 상품이 다시 들어왔어요"))
                .andExpect(jsonPath("$.content[0].link").value("/customer/stores/" + store + "/products/" + product));
        mockMvc.perform(get("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(jsonPath("$.subscribed").value(false));

        mockMvc.perform(post("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/notifications/restock/{p}", product).param("storeId", String.valueOf(store))
                        .header("X-User-Id", user).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 재입고_신청은_고객만_점포가_없으면_400() throws Exception {
        mockMvc.perform(post("/notifications/restock/15").param("storeId", "1")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "OWNER")
                        .header("X-Store-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ONLY"));
        mockMvc.perform(post("/notifications/restock/15")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/notifications/restock/0").param("storeId", "1")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
