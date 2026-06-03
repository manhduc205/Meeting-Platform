package com.manhduc205.meetingplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MeetingplatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeetingplatformApplication.class, args);
	}

}
