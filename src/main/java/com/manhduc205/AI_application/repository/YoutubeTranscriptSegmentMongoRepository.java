package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.YoutubeTranscriptSegmentDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface YoutubeTranscriptSegmentMongoRepository extends MongoRepository<YoutubeTranscriptSegmentDocument, String> {
    Slice<YoutubeTranscriptSegmentDocument> findBySummaryIdAndSequenceGreaterThanOrderBySequenceAsc(
            String summaryId, Long sequence, Pageable pageable);

    long countBySummaryId(String summaryId);

    void deleteBySummaryId(String summaryId);
}
