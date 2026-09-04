package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.RecordingTranscriptSegmentDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecordingTranscriptSegmentMongoRepository extends MongoRepository<RecordingTranscriptSegmentDocument, String> {
    Slice<RecordingTranscriptSegmentDocument> findByRecordingIdAndLanguageAndSequenceGreaterThanOrderBySequenceAsc(
            Long recordingId, String language, Long sequence, Pageable pageable);

    long countByRecordingIdAndLanguage(Long recordingId, String language);

    void deleteByRecordingIdAndLanguage(Long recordingId, String language);

    void deleteByRecordingId(Long recordingId);
}
