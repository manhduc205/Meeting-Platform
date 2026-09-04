package com.manhduc205.AI_application.service.impl;

import com.manhduc205.AI_application.enums.AiContentStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.AI_application.entity.RecordingAiContentDocument;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.AI_application.entity.RecordingAiJobEntity;
import com.manhduc205.AI_application.entity.RecordingTranscriptSegmentDocument;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.AI_application.dto.response.RecordingDetailResponse;
import com.manhduc205.AI_application.dto.response.TranscriptSegmentPageResponse;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.AI_application.repository.RecordingAiContentMongoRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.AI_application.repository.RecordingAiJobRepository;
import com.manhduc205.AI_application.repository.RecordingTranscriptSegmentMongoRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.AI_application.service.RecordingContentService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordingContentServiceImpl implements RecordingContentService {
    private static final int MAX_TRANSCRIPT_PAGE_SIZE = 200;

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final RecordingAiContentMongoRepository aiContentRepository;
    private final RecordingTranscriptSegmentMongoRepository transcriptSegmentRepository;
    private final RecordingAiJobRepository aiJobRepository;

    @Override
    public RecordingDetailResponse getRecordingDetail(Long recordingId) {
        RecordingEntity recording = getAccessibleRecording(recordingId);
        MeetingEntity meeting = meetingRepository.findByMeetingCode(recording.getMeetingCode())
                .orElseThrow(() -> new IllegalStateException("Bản ghi không còn gắn với cuộc họp hợp lệ"));
        UserEntity host = userRepository.findById(meeting.getHostId()).orElse(null);
        RecordingAiContentDocument aiContent = aiContentRepository.findByRecordingId(recordingId).orElse(null);
        RecordingAiJobEntity latestJob = aiJobRepository
                .findFirstByRecordingIdAndOperationOrderByVersionDesc(recordingId, RecordingAiJobServiceImpl.OPERATION)
                .orElse(null);

        String language = latestJob != null ? latestJob.getLanguage() : aiContent != null ? aiContent.getSourceLanguage() : null;
        AiContentStatus effectiveStatus = effectiveStatus(aiContent, latestJob);
        long segmentCount = language == null || effectiveStatus != AiContentStatus.READY
                ? 0
                : transcriptSegmentRepository.countByRecordingIdAndLanguage(recordingId, language);

        return RecordingDetailResponse.builder()
                .id(recording.getId())
                .egressId(recording.getEgressId())
                .status(recording.getStatus().name())
                .visibility(recording.getVisibility().name())
                .title(meeting.getTitle())
                .author(RecordingDetailResponse.Author.builder()
                        .id(meeting.getHostId())
                        .fullName(host != null ? host.getFullName() : null)
                        .avatarUrl(host != null ? host.getAvatarUrl() : null)
                        .build())
                .metadata(RecordingDetailResponse.Metadata.builder()
                        .createdAt(recording.getCreatedAt())
                        .durationSeconds(recording.getDuration())
                        .videoUrl(recording.getFileUrl())
                        .storagePrefix(recording.getStoragePrefix())
                        .build())
                .ai(toAiResponse(aiContent, latestJob, effectiveStatus))
                .transcript(RecordingDetailResponse.Transcript.builder()
                        .status(effectiveStatus.name())
                        .language(language)
                        .totalSegments(segmentCount)
                        .build())
                .build();
    }

    @Override
    public TranscriptSegmentPageResponse getTranscriptSegments(Long recordingId, String language, String cursor, int limit) {
        getAccessibleRecording(recordingId);

        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language là bắt buộc");
        }
        int pageSize = Math.min(Math.max(limit, 1), MAX_TRANSCRIPT_PAGE_SIZE);
        long sequence = parseCursor(cursor);

        Slice<RecordingTranscriptSegmentDocument> page = transcriptSegmentRepository
                .findByRecordingIdAndLanguageAndSequenceGreaterThanOrderBySequenceAsc(
                        recordingId, language, sequence, PageRequest.of(0, pageSize));

        List<TranscriptSegmentPageResponse.Segment> segments = page.getContent().stream()
                .map(this::toSegmentResponse)
                .toList();
        String nextCursor = page.hasNext() && !segments.isEmpty()
                ? String.valueOf(segments.getLast().getSequence())
                : null;

        return TranscriptSegmentPageResponse.builder()
                .items(segments)
                .hasNext(page.hasNext())
                .nextCursor(nextCursor)
                .build();
    }

    private RecordingEntity getAccessibleRecording(Long recordingId) {
        String currentUserId = UserContext.getUserId();
        return recordingRepository.findAccessibleRecordingById(currentUserId, recordingId)
                .orElseThrow(() -> new SecurityException("Bạn không có quyền xem bản ghi này hoặc bản ghi không tồn tại"));
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return -1;
        try {
            long sequence = Long.parseLong(cursor);
            if (sequence < -1) throw new NumberFormatException();
            return sequence;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cursor transcript không hợp lệ");
        }
    }

    private RecordingDetailResponse.AiContent toAiResponse(
            RecordingAiContentDocument content,
            RecordingAiJobEntity latestJob,
            AiContentStatus effectiveStatus) {
        boolean currentContent = content != null && (latestJob == null
                || content.getVersion() != null && content.getVersion() >= latestJob.getVersion());
        if (!currentContent) {
            return RecordingDetailResponse.AiContent.builder()
                    .transcriptStatus(effectiveStatus.name())
                    .summaryStatus(effectiveStatus.name())
                    .sourceLanguage(latestJob == null ? null : latestJob.getLanguage())
                    .keyMoments(List.of())
                    .build();
        }
        List<RecordingDetailResponse.KeyMoment> keyMoments = content.getKeyMoments() == null ? List.of()
                : content.getKeyMoments().stream()
                .map(moment -> RecordingDetailResponse.KeyMoment.builder()
                        .startMs(moment.getStartMs())
                        .endMs(moment.getEndMs())
                        .topic(moment.getTopic())
                        .build())
                .toList();
        return RecordingDetailResponse.AiContent.builder()
                .transcriptStatus(content.getTranscriptStatus().name())
                .summaryStatus(content.getSummaryStatus().name())
                .sourceLanguage(content.getSourceLanguage())
                .summary(content.getSummary())
                .keyMoments(keyMoments)
                .build();
    }

    private AiContentStatus effectiveStatus(RecordingAiContentDocument content, RecordingAiJobEntity latestJob) {
        if (latestJob == null) {
            return content == null ? AiContentStatus.NOT_REQUESTED : content.getTranscriptStatus();
        }
        boolean currentContent = content != null && content.getVersion() != null
                && content.getVersion() >= latestJob.getVersion();
        if (currentContent) return content.getTranscriptStatus();
        return switch (latestJob.getStatus()) {
            case REQUESTED -> AiContentStatus.REQUESTED;
            case PUBLISHED -> AiContentStatus.PROCESSING;
            case COMPLETED -> AiContentStatus.READY;
            case FAILED -> AiContentStatus.FAILED;
        };
    }

    private TranscriptSegmentPageResponse.Segment toSegmentResponse(RecordingTranscriptSegmentDocument segment) {
        String text = segment.getTranslatedText() == null || segment.getTranslatedText().isBlank()
                ? segment.getOriginalText()
                : segment.getTranslatedText();
        return TranscriptSegmentPageResponse.Segment.builder()
                .id(segment.getId())
                .sequence(segment.getSequence())
                .startMs(segment.getStartMs())
                .endMs(segment.getEndMs())
                .speakerId(segment.getSpeakerId())
                .speakerName(segment.getSpeakerName())
                .text(text)
                .confidence(segment.getConfidence())
                .build();
    }
}
