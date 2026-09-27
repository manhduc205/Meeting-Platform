package com.manhduc205.AI_application.configuration;

import com.manhduc205.AI_application.entity.RecordingTranscriptSegmentDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordingTranscriptIndexMigration implements ApplicationRunner {
    private static final String OLD_INDEX = "uk_recording_language_sequence";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        var indexes = mongoTemplate.indexOps(RecordingTranscriptSegmentDocument.class);
        boolean oldIndexExists = indexes.getIndexInfo().stream()
                .anyMatch(index -> OLD_INDEX.equals(index.getName()));
        if (oldIndexExists) indexes.dropIndex(OLD_INDEX);
    }
}
