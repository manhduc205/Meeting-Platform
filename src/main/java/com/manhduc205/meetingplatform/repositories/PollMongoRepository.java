package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.PollDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PollMongoRepository extends MongoRepository<PollDocument, String> {
}