package com.freshlink.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.queue.notifications=test-notification-events",
		"spring.cloud.aws.sqs.listener.auto-startup=false"})
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
