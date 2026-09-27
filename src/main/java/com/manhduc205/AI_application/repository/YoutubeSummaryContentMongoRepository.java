package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.YoutubeSummaryContentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface YoutubeSummaryContentMongoRepository extends MongoRepository<YoutubeSummaryContentDocument, String> {
    Optional<YoutubeSummaryContentDocument> findBySummaryId(String summaryId);
}
