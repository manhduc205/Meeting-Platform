package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.RecordingAiContentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RecordingAiContentMongoRepository extends MongoRepository<RecordingAiContentDocument, String> {
    Optional<RecordingAiContentDocument> findByRecordingId(Long recordingId);

    void deleteByRecordingId(Long recordingId);
}
