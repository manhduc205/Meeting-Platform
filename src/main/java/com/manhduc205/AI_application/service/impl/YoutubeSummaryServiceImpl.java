package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.AI_application.dto.request.YoutubeSummaryRequest;
import com.manhduc205.AI_application.dto.response.YoutubeSummaryResponse;
import com.manhduc205.AI_application.dto.response.YoutubeTranscriptPageResponse;
import com.manhduc205.AI_application.entity.YoutubeSummaryContentDocument;
import com.manhduc205.AI_application.entity.YoutubeSummaryEntity;
import com.manhduc205.AI_application.entity.YoutubeTranscriptSegmentDocument;
import com.manhduc205.AI_application.enums.YoutubeSummaryStatus;
import com.manhduc205.AI_application.repository.YoutubeSummaryContentMongoRepository;
import com.manhduc205.AI_application.repository.YoutubeSummaryRepository;
import com.manhduc205.AI_application.repository.YoutubeTranscriptSegmentMongoRepository;
import com.manhduc205.AI_application.service.YoutubeSummaryService;
import com.manhduc205.meetingplatform.enums.OutboxEventType;
import com.manhduc205.meetingplatform.models.OutboxEventEntity;
import com.manhduc205.meetingplatform.repositories.OutboxEventRepository;
import com.manhduc205.meetingplatform.utils.UserContext;
import com.manhduc205.meetingplatform.utils.YoutubeSummaryStoragePaths;
import com.manhduc205.routing.dto.YoutubeSummaryRequestedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YoutubeSummaryServiceImpl implements YoutubeSummaryService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Set<YoutubeSummaryStatus> ACTIVE = Set.of(
            YoutubeSummaryStatus.REQUESTED, YoutubeSummaryStatus.PUBLISHED);

    private final YoutubeSummaryRepository summaryRepository;
    private final YoutubeSummaryContentMongoRepository contentRepository;
    private final YoutubeTranscriptSegmentMongoRepository transcriptRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public YoutubeSummaryResponse create(YoutubeSummaryRequest request) {
        String userId = requireUser();
        YoutubeVideo video = parseVideo(request.url());
        String language = normalizeLanguage(request.language());

        var active = summaryRepository
                .findFirstByRequestedByAndVideoIdAndTargetLanguageAndStatusInOrderByRequestedAtDesc(
                        userId, video.id(), language, ACTIVE);
        if (active.isPresent()) return toResponse(active.get());

        String summaryId = UUID.randomUUID().toString();
        String prefix = YoutubeSummaryStoragePaths.prefix(summaryId);
        Instant now = Instant.now();
        YoutubeSummaryEntity summary = summaryRepository.save(YoutubeSummaryEntity.builder()
                .id(summaryId)
                .requestedBy(userId)
                .videoId(video.id())
                .sourceUrl(video.url())
                .targetLanguage(language)
                .storagePrefix(prefix)
                .status(YoutubeSummaryStatus.REQUESTED)
                .requestedAt(now)
                .build());

        String messageId = UUID.randomUUID().toString();
        YoutubeSummaryRequestedMessage command = new YoutubeSummaryRequestedMessage(
                1,
                messageId,
                "youtube-summary.requested",
                summaryId,
                video.id(),
                video.url(),
                prefix,
                YoutubeSummaryStoragePaths.rawTranscript(prefix),
                YoutubeSummaryStoragePaths.translatedTranscript(prefix, language),
                YoutubeSummaryStoragePaths.caption(prefix, language),
                YoutubeSummaryStoragePaths.summary(prefix, language),
                language,
                userId,
                now
        );
        outboxRepository.save(OutboxEventEntity.builder()
                .id(messageId)
                .eventType(OutboxEventType.YOUTUBE_SUMMARY_REQUESTED)
                .aggregateId(summaryId)
                .payload(writeJson(command))
                .build());
        return toResponse(summary);
    }

    @Override
    public YoutubeSummaryResponse get(String summaryId) {
        YoutubeSummaryEntity summary = summaryRepository.findByIdAndRequestedBy(summaryId, requireUser())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản tóm tắt YouTube"));
        return toResponse(summary);
    }

    @Override
    public YoutubeTranscriptPageResponse getTranscript(String summaryId, String cursor, int limit) {
        YoutubeSummaryEntity summary = summaryRepository.findByIdAndRequestedBy(summaryId, requireUser())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản tóm tắt YouTube"));
        if (summary.getStatus() != YoutubeSummaryStatus.COMPLETED) {
            throw new IllegalStateException("Transcript YouTube chưa xử lý xong");
        }
        long sequence = parseCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Slice<YoutubeTranscriptSegmentDocument> page = transcriptRepository
                .findBySummaryIdAndSequenceGreaterThanOrderBySequenceAsc(
                        summaryId, sequence, PageRequest.of(0, pageSize));
        List<YoutubeTranscriptPageResponse.Segment> items = page.getContent().stream()
                .map(item -> new YoutubeTranscriptPageResponse.Segment(
                        item.getId(), item.getSequence(), item.getStartMs(), item.getEndMs(), item.getText()))
                .toList();
        String nextCursor = page.hasNext() && !items.isEmpty()
                ? String.valueOf(items.getLast().sequence())
                : null;
        return new YoutubeTranscriptPageResponse(items, nextCursor, page.hasNext());
    }

    @Override
    @Transactional
    public void markPublished(String summaryId) {
        summaryRepository.findByIdForUpdate(summaryId).ifPresent(summary -> {
            if (summary.getStatus() == YoutubeSummaryStatus.REQUESTED) {
                summary.setStatus(YoutubeSummaryStatus.PUBLISHED);
                summary.setPublishedAt(Instant.now());
            }
        });
    }

    @Override
    @Transactional
    public void markInfrastructureFailed(String summaryId, String error) {
        summaryRepository.findByIdForUpdate(summaryId).ifPresent(summary -> {
            if (summary.getStatus() == YoutubeSummaryStatus.COMPLETED
                    || summary.getStatus() == YoutubeSummaryStatus.FAILED) return;
            summary.setStatus(YoutubeSummaryStatus.FAILED);
            summary.setLastError(truncate(error));
            summary.setCompletedAt(Instant.now());
        });
    }

    private YoutubeSummaryResponse toResponse(YoutubeSummaryEntity entity) {
        YoutubeSummaryContentDocument content = contentRepository.findBySummaryId(entity.getId()).orElse(null);
        List<YoutubeSummaryResponse.KeyMoment> moments = content == null || content.getKeyMoments() == null
                ? List.of()
                : content.getKeyMoments().stream()
                .map(item -> new YoutubeSummaryResponse.KeyMoment(item.getStartMs(), item.getEndMs(), item.getTopic()))
                .toList();
        long segmentCount = entity.getStatus() == YoutubeSummaryStatus.COMPLETED
                ? transcriptRepository.countBySummaryId(entity.getId())
                : 0;
        return new YoutubeSummaryResponse(
                entity.getId(),
                entity.getVideoId(),
                entity.getSourceUrl(),
                entity.getStatus().name(),
                entity.getTargetLanguage(),
                content == null ? null : content.getSourceLanguage(),
                content == null ? null : content.getSummary(),
                moments,
                segmentCount,
                entity.getLastError(),
                entity.getRequestedAt(),
                entity.getCompletedAt()
        );
    }

    private YoutubeVideo parseVideo(String value) {
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String id;
            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                id = firstPathPart(uri.getPath());
            } else if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
                id = queryValue(uri.getRawQuery(), "v");
                if (id == null) {
                    String path = uri.getPath() == null ? "" : uri.getPath();
                    id = path.startsWith("/shorts/") ? firstPathPart(path.substring(7))
                            : path.startsWith("/embed/") ? firstPathPart(path.substring(6)) : null;
                }
            } else {
                throw new IllegalArgumentException("Chỉ hỗ trợ URL youtube.com hoặc youtu.be");
            }
            if (id == null || !VIDEO_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("URL YouTube không chứa video ID hợp lệ");
            }
            return new YoutubeVideo(id, "https://www.youtube.com/watch?v=" + id);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("URL YouTube không hợp lệ");
        }
    }

    private String queryValue(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) return parts[1];
        }
        return null;
    }

    private String firstPathPart(String path) {
        if (path == null) return null;
        String clean = path.replaceFirst("^/+", "");
        int slash = clean.indexOf('/');
        return slash < 0 ? clean : clean.substring(0, slash);
    }

    private String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "vi" : language.trim().toLowerCase(Locale.ROOT);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return -1;
        try {
            long value = Long.parseLong(cursor);
            if (value < -1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cursor transcript không hợp lệ");
        }
    }

    private String requireUser() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) throw new SecurityException("Bạn cần đăng nhập");
        return userId;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo yêu cầu tóm tắt YouTube", exception);
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "Không thể gửi yêu cầu tới AI worker";
        return value.substring(0, Math.min(value.length(), 2000));
    }

    private record YoutubeVideo(String id, String url) {
    }
}
