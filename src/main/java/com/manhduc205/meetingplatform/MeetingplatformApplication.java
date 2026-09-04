package com.manhduc205.meetingplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.manhduc205")
@EntityScan(basePackages = {
        "com.manhduc205.meetingplatform.models",
        "com.manhduc205.AI_application.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.manhduc205.meetingplatform.repositories",
        "com.manhduc205.AI_application.repository"
})
@EnableMongoRepositories(basePackages = {
        "com.manhduc205.meetingplatform.repositories",
        "com.manhduc205.AI_application.repository"
})
@EnableAsync
public class MeetingplatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeetingplatformApplication.class, args);
	}

}
