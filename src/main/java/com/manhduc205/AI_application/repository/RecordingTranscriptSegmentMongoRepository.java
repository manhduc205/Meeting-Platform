package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.RecordingTranscriptSegmentDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecordingTranscriptSegmentMongoRepository extends MongoRepository<RecordingTranscriptSegmentDocument, String> {
    Slice<RecordingTranscriptSegmentDocument> findByRecordingIdAndLanguageAndVersionAndSequenceGreaterThanOrderBySequenceAsc(
            Long recordingId, String language, Integer version, Long sequence, Pageable pageable);

    long countByRecordingIdAndLanguageAndVersion(Long recordingId, String language, Integer version);

    void deleteByRecordingId(Long recordingId);
}
