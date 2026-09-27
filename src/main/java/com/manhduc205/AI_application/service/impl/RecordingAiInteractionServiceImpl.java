package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.AI_application.dto.request.RecordingChatRequest;
import com.manhduc205.AI_application.dto.response.ArtifactUrlResponse;
import com.manhduc205.AI_application.dto.response.RecordingSummaryResponse;
import com.manhduc205.AI_application.entity.RecordingAiContentDocument;
import com.manhduc205.AI_application.enums.AiContentStatus;
import com.manhduc205.AI_application.repository.RecordingAiContentMongoRepository;
import com.manhduc205.AI_application.service.RecordingAiInteractionService;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.meetingplatform.utils.UserContext;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RecordingAiInteractionServiceImpl implements RecordingAiInteractionService {
    private static final int PRESIGNED_URL_MINUTES = 10;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final RecordingRepository recordingRepository;
    private final RecordingAiContentMongoRepository aiContentRepository;
    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final Set<Long> activeSummaryStreams = ConcurrentHashMap.newKeySet();

    @Value("${app.minio.bucket}")
    private String minioBucket;

    @Value("${app.minio.max-summary-bytes:5242880}")
    private int maxSummaryBytes;

    @Value("${app.taskflow.base-url:http://localhost:8090}")
    private String taskflowBaseUrl;

    @Value("${app.taskflow.internal-token:change-me}")
    private String taskflowInternalToken;

    public RecordingAiInteractionServiceImpl(
            RecordingRepository recordingRepository,
            RecordingAiContentMongoRepository aiContentRepository,
            @Qualifier("minioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            ObjectMapper objectMapper) {
        this.recordingRepository = recordingRepository;
        this.aiContentRepository = aiContentRepository;
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(10))
                .build();
    }

    @Override
    public RecordingSummaryResponse getSummary(Long recordingId) {
        RecordingAiContentDocument content = requireContent(recordingId, true);
        if (content.getSummaryStatus() != AiContentStatus.READY || content.getSummary() == null) {
            throw new IllegalStateException("Bản tóm tắt chưa sẵn sàng");
        }
        return toSummaryResponse(content);
    }

    @Override
    public void deleteSummary(Long recordingId) {
        RecordingAiContentDocument content = requireContent(recordingId, true);
        if (activeSummaryStreams.contains(recordingId)) {
            throw new IllegalStateException("Bản tóm tắt đang được tạo, chưa thể xóa");
        }
        try {
            if (content.getSummaryObjectKey() != null && !content.getSummaryObjectKey().isBlank()) {
                internalMinioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(content.getSummaryObjectKey())
                        .build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể xóa file summary", exception);
        }
        content.setSummary(null);
        content.setSummarySha256(null);
        content.setSummaryStatus(AiContentStatus.NOT_REQUESTED);
        content.setGeneratedAt(null);
        content.setKeyMoments(java.util.List.of());
        aiContentRepository.save(content);
    }

    @Override
    public StreamingResponseBody streamSummary(Long recordingId) {
        RecordingAiContentDocument content = requireContent(recordingId, true);
        if (content.getSummaryStatus() == AiContentStatus.READY && content.getSummary() != null) {
            byte[] cached = content.getSummary().getBytes(StandardCharsets.UTF_8);
            return output -> output.write(cached);
        }

        if (!activeSummaryStreams.add(recordingId)) {
            throw new IllegalStateException("Bản tóm tắt đang được tạo");
        }

        try {
            content.setSummaryStatus(AiContentStatus.PROCESSING);
            aiContentRepository.save(content);
        } catch (RuntimeException exception) {
            activeSummaryStreams.remove(recordingId);
            throw exception;
        }
        Map<String, Object> payload = basePayload(recordingId, content);

        return output -> {
            try {
                ByteArrayOutputStream markdown = new ByteArrayOutputStream();
                boolean clientConnected = true;
                try (InputStream input = sendStreamRequest(
                        "/api/v1/recordings/" + recordingId + "/summary/stream", payload)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        if (markdown.size() + read > maxSummaryBytes) {
                            throw new IOException("Nội dung summary vượt quá giới hạn cho phép");
                        }
                        markdown.write(buffer, 0, read);
                        if (clientConnected) {
                            try {
                                output.write(buffer, 0, read);
                                output.flush();
                            } catch (IOException disconnected) {
                                clientConnected = false;
                            }
                        }
                    }
                }
                saveCompletedSummary(recordingId, content.getVersion(), markdown.toByteArray());
            } catch (Exception exception) {
                markSummaryFailed(recordingId, content.getVersion());
                throw asIOException(exception);
            } finally {
                activeSummaryStreams.remove(recordingId);
            }
        };
    }

    @Override
    public StreamingResponseBody streamChat(Long recordingId, RecordingChatRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Câu hỏi không được để trống");
        }
        RecordingAiContentDocument content = requireContent(recordingId, true);
        Map<String, Object> payload = basePayload(recordingId, content);
        payload.put("query", request.query().trim());
        payload.put("conversationHistory", request.conversationHistory() == null
                ? java.util.List.of() : request.conversationHistory());

        return output -> {
            try (InputStream input = sendStreamRequest(
                    "/api/v1/recordings/" + recordingId + "/chat/stream", payload)) {
                input.transferTo(output);
            }
        };
    }

    @Override
    public ArtifactUrlResponse createArtifactUrl(Long recordingId, String type) {
        RecordingAiContentDocument content = requireContent(recordingId, true);
        String normalized = type == null ? "" : type.trim().toLowerCase();
        String objectKey = switch (normalized) {
            case "caption", "captions", "vtt" -> content.getCaptionObjectKey();
            case "transcript", "json" -> content.getRawTranscriptObjectKey();
            case "summary", "md" -> {
                if (content.getSummaryStatus() != AiContentStatus.READY) {
                    throw new IllegalStateException("Bản tóm tắt chưa sẵn sàng");
                }
                yield content.getSummaryObjectKey();
            }
            default -> throw new IllegalArgumentException("Loại artifact không hợp lệ");
        };
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("Artifact chưa sẵn sàng");
        }

        try {
            internalMinioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectKey)
                    .build());
            String url = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioBucket)
                    .object(objectKey)
                    .expiry(PRESIGNED_URL_MINUTES, TimeUnit.MINUTES)
                    .build());
            return new ArtifactUrlResponse(normalized, url, Instant.now().plusSeconds(PRESIGNED_URL_MINUTES * 60L));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo đường dẫn tải artifact", exception);
        }
    }

    private RecordingAiContentDocument requireContent(Long recordingId, boolean requireTranscript) {
        recordingRepository.findAccessibleRecordingById(UserContext.getUserId(), recordingId)
                .orElseThrow(() -> new SecurityException("Bạn không có quyền xem bản ghi này hoặc bản ghi không tồn tại"));
        RecordingAiContentDocument content = aiContentRepository.findByRecordingId(recordingId)
                .orElseThrow(() -> new IllegalStateException("Transcript chưa sẵn sàng"));
        if (requireTranscript && (content.getTranscriptStatus() != AiContentStatus.READY
                || content.getRawTranscriptObjectKey() == null)) {
            throw new IllegalStateException("Transcript chưa sẵn sàng");
        }
        return content;
    }

    private Map<String, Object> basePayload(Long recordingId, RecordingAiContentDocument content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordingId", recordingId);
        payload.put("version", content.getVersion());
        payload.put("language", content.getSourceLanguage());
        payload.put("transcriptObjectKey", content.getRawTranscriptObjectKey());
        payload.put("summaryObjectKey", content.getSummaryObjectKey());
        return payload;
    }

    private InputStream sendStreamRequest(String path, Object payload) throws IOException {
        byte[] jsonBody = objectMapper.writeValueAsBytes(payload);
        Request request = new Request.Builder()
                .url(taskflowBaseUrl + path)
                .header("Accept", "text/plain")
                .header("X-Internal-Token", taskflowInternalToken)
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        Response response = httpClient.newCall(request).execute();
        ResponseBody responseBody = response.body();
        if (!response.isSuccessful()) {
            int statusCode = response.code();
            String message = "";
            if (responseBody != null) {
                try (InputStream error = responseBody.byteStream()) {
                    message = new String(error.readNBytes(4096), StandardCharsets.UTF_8);
                }
            }
            response.close();
            throw new IOException("TaskFlow trả về HTTP " + statusCode + ": " + message);
        }
        if (responseBody == null) {
            response.close();
            throw new IOException("TaskFlow trả về response rỗng");
        }
        return responseBody.byteStream();
    }

    private void saveCompletedSummary(Long recordingId, Integer version, byte[] markdownBytes) {
        RecordingAiContentDocument latest = aiContentRepository.findByRecordingId(recordingId).orElse(null);
        if (latest == null || !java.util.Objects.equals(latest.getVersion(), version)) return;
        latest.setSummary(new String(markdownBytes, StandardCharsets.UTF_8));
        latest.setSummarySha256(sha256(markdownBytes));
        latest.setSummaryStatus(AiContentStatus.READY);
        latest.setGeneratedAt(Instant.now());
        aiContentRepository.save(latest);
    }

    private void markSummaryFailed(Long recordingId, Integer version) {
        aiContentRepository.findByRecordingId(recordingId).ifPresent(latest -> {
            if (java.util.Objects.equals(latest.getVersion(), version)) {
                latest.setSummaryStatus(AiContentStatus.FAILED);
                aiContentRepository.save(latest);
            }
        });
    }

    private RecordingSummaryResponse toSummaryResponse(RecordingAiContentDocument content) {
        return new RecordingSummaryResponse(
                content.getRecordingId(), content.getSourceLanguage(), content.getVersion(),
                content.getSummary(), content.getModel(), content.getGeneratedAt());
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tính SHA-256", exception);
        }
    }

    private IOException asIOException(Exception exception) {
        return exception instanceof IOException io ? io : new IOException(exception.getMessage(), exception);
    }
}
