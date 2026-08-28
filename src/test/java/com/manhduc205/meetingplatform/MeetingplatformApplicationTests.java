package com.manhduc205.meetingplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.models.dtos.response.CalendarMeetingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=none",
		"app.notifications.outbox.polling-enabled=false"
})
class MeetingplatformApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void calendarResponseExposesIsHostForTheFrontend() throws Exception {
		String json = objectMapper.writeValueAsString(CalendarMeetingResponse.builder().isHost(true).build());
		assertTrue(json.contains("\"isHost\":true"));
	}

}
