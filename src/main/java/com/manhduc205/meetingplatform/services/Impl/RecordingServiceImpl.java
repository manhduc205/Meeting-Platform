package com.manhduc205.meetingplatform.services.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.meetingplatform.enums.RecordingVisibility;
import com.manhduc205.meetingplatform.exceptions.EgressRecordingNotReadyException;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.RecordingService;
import com.manhduc205.meetingplatform.utils.UserContext;
import com.manhduc205.meetingplatform.utils.RecordingStoragePaths;
import io.livekit.server.EgressServiceClient;
import livekit.LivekitEgress.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingServiceImpl implements RecordingService {

    private static final ZoneId RECORDING_DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter RECORDING_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(RECORDING_DISPLAY_ZONE);

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;
    private final EgressServiceClient egressServiceClient;
    private final UserRepository userRepository;
    @Value("${app.minio.access-key}") private String minioAccessKey;
    @Value("${app.minio.secret-key}") private String minioSecretKey;
    @Value("${app.minio.egress-endpoint}") private String egressMinioEndpoint;
    @Value("${app.minio.bucket}") private String minioBucket;

    @Override
    public RecordingResponse startRecording(String meetingCode) {
        verifyHostPrivilege(meetingCode);

        try {
            S3Upload s3Output = S3Upload.newBuilder()
                    .setAccessKey(minioAccessKey)
                    .setSecret(minioSecretKey)
                    .setEndpoint(egressMinioEndpoint)
                    .setBucket(minioBucket)
                    .setForcePathStyle(true)
                    .build();

            String storagePrefix = RecordingStoragePaths.newRecordingPrefix(UUID.randomUUID().toString());
            String filename = RecordingStoragePaths.videoSource(storagePrefix);

            EncodedFileOutput fileOutput = EncodedFileOutput.newBuilder()
                    .setFileType(EncodedFileType.MP4)
                    .setFilepath(filename)
                    .setS3(s3Output)
                    .build();
            EncodingOptionsPreset preset = EncodingOptionsPreset.H264_1080P_30;

            var call = egressServiceClient.startRoomCompositeEgress(
                    meetingCode,
                    fileOutput,
                    "grid",
                    preset
            );

            EgressInfo info = call.execute().body();

            if (info == null) {
                throw new RuntimeException("LiveKit Server không trả về thông tin Egress.");
            }
            String startedEgressId = info.getEgressId();

            RecordingEntity entity = RecordingEntity.builder()
                    .meetingCode(meetingCode)
                    .egressId(startedEgressId)
                    .status(RecordingStatus.STARTING)
                    .visibility(RecordingVisibility.MEETING_MEMBERS)
                    .storagePrefix(storagePrefix)
                    .build();

            // Flush trước khi trả response để phát hiện lỗi INSERT ngay trong
            // request hiện tại và có thể dừng Egress vừa tạo.
            RecordingEntity savedRecording;
            try {
                savedRecording = recordingRepository.saveAndFlush(entity);
            } catch (Exception persistenceException) {
                stopOrphanedEgress(startedEgressId);
                throw persistenceException;
            }
            return mapToResponse(savedRecording);
        } catch (Exception e) {
            log.error("Lỗi khởi tạo Egress với cấu hình High-Definition: {}", e.getMessage());
            throw new RuntimeException("Không thể bắt đầu ghi hình cuộc họp: " + e.getMessage());
        }
    }

    @Override
    public void stopRecording(String meetingCode, String egressId) {
        verifyHostPrivilege(meetingCode);
        executeStopEgress(egressId);
    }

    @Override
    public void stopActiveRecordings(String meetingCode) {
        List<RecordingEntity> recordings = recordingRepository.findAllByMeetingCodeOrderByCreatedAtDesc(meetingCode);
        for (RecordingEntity recording : recordings) {
            if (recording.getStatus() == RecordingStatus.STARTING || recording.getStatus() == RecordingStatus.RECORDING) {
                executeStopEgress(recording.getEgressId());
            }
        }
    }

    private void executeStopEgress(String egressId) {
        try {
            egressServiceClient.stopEgress(egressId).execute();
            log.info("Đã gửi lệnh dừng ghi âm cho EgressID: {}", egressId);
        } catch (Exception e) {
            log.error("Cảnh báo khi dừng Egress [{}]: {}", egressId, e.getMessage());
        }
    }

    @Override
    public List<RecordingResponse> getMeetingRecordings(String meetingCode) {
        String currentUserId = UserContext.getUserId();

        meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

        List<RecordingEntity> accessibleRecordings = recordingRepository
                .findAccessibleRecordingsByMeetingCode(currentUserId, meetingCode);

        return accessibleRecordings.stream()
                .map(this::mapToResponse)
                .toList();
    }
    @Override
    public List<RecordingResponse> getAllAccessibleRecordingsForCurrentUser() {
        String currentUserId = UserContext.getUserId();
        List<RecordingEntity> accessibleRecordings = recordingRepository
                .findAllAccessibleRecordings(currentUserId);

        return enrichRecordings(accessibleRecordings);
    }

    @Override
    public List<RecordingResponse> getTrashForCurrentUser() {
        return enrichRecordings(recordingRepository.findTrashOwnedBy(UserContext.getUserId(), Instant.now()));
    }

    private List<RecordingResponse> enrichRecordings(List<RecordingEntity> recordings) {
        if (recordings.isEmpty()) return Collections.emptyList();

        List<String> meetingCodes = recordings.stream()
                .map(RecordingEntity::getMeetingCode)
                .distinct()
                .toList();

        List<MeetingEntity> meetings = meetingRepository.findAllByMeetingCodeIn(meetingCodes);
        Map<String, MeetingEntity> meetingMap = meetings.stream()
                .collect(Collectors.toMap(MeetingEntity::getMeetingCode, m -> m));

        List<String> hostIds = meetings.stream().map(MeetingEntity::getHostId).distinct().toList();
        List<UserEntity> hosts = userRepository.findAllById(hostIds);
        Map<String, UserEntity> hostMap = hosts.stream()
                .collect(Collectors.toMap(UserEntity::getId, h -> h));

        return recordings.stream()
                .map(recording -> {
                    MeetingEntity meeting = meetingMap.get(recording.getMeetingCode());
                    UserEntity host = (meeting != null) ? hostMap.get(meeting.getHostId()) : null;

                    String meetingTitle = (meeting != null && meeting.getTitle() != null) ? meeting.getTitle() : "Cuộc họp #" + recording.getMeetingCode();
                    String displayName = meetingTitle + " - Bản ghi ngày " +
                            RECORDING_DATE_FORMAT.format(recording.getCreatedAt());

                    // Xử lý thông tin Host thật từ UserRepository
                    String hostName = (host != null && host.getFullName() != null) ? host.getFullName() : "Thành viên UTT";
                    String hostAvatar = (host != null && host.getAvatarUrl() != null) ? host.getAvatarUrl()
                            : "https://ui-avatars.com/api/?name=" + hostName.replace(" ", "+");

                    return RecordingResponse.builder()
                            .id(recording.getId())
                            .egressId(recording.getEgressId())
                            .meetingCode(recording.getMeetingCode())
                            .recordingName(displayName)
                            .hostId(meeting != null ? meeting.getHostId() : null)
                            .hostName(hostName)
                            .hostAvatar(hostAvatar)
                            .status(recording.getStatus().name())
                            .visibility(recording.getVisibility() != null ? recording.getVisibility().name() : "MEETING_MEMBERS") // 🟢 Trả về quyền để Angular hiện icon khóa/mở
                            .fileUrl(recording.getFileUrl())
                            .duration(recording.getDuration())
                            .createdAt(recording.getCreatedAt())
                            .purgeAfter(recording.getPurgeAfter())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void handleEgressWebhook(String payload) {
        EgressWebhook webhook = parseEgressWebhook(payload);
        String event = webhook.root().path("event").asText();

        if (!event.startsWith("egress_")) {
            log.debug("Bỏ qua LiveKit event không thuộc Egress: {}", event);
            return;
        }
        log.info("Webhook LiveKit nhận Egress event: {}", event);

        // Event không thuộc Egress hoặc payload thiếu egressId không thể được
        // sửa bằng retry, nên trả thành công để tránh LiveKit gửi lại vô ích.
        if (webhook.egressId().isBlank()) {
            log.warn("Webhook không chứa egressId; bỏ qua event [{}]", event);
            return;
        }

        var recordingResult = recordingRepository.findByEgressIdForUpdate(webhook.egressId());
        if (recordingResult.isEmpty()) {
            if (isTerminalWebhook(webhook.status())) {
                log.info("Bỏ qua webhook terminal cho recording không còn tồn tại: {}", webhook.egressId());
                return;
            }
            throw new EgressRecordingNotReadyException(webhook.egressId());
        }

        RecordingEntity recording = recordingResult.get();
        if (recording.getPurgeAfter() != null) {
            log.info("Bỏ qua webhook đến muộn cho recording đang/đã xóa: {}", webhook.egressId());
            return;
        }

        if (applyEgressWebhook(recording, webhook)) {
            recordingRepository.save(recording);
            log.info("DB cập nhật trạng thái [{}] cho EgressID: {}", recording.getStatus(), webhook.egressId());
        }
    }

    private void stopOrphanedEgress(String egressId) {
        try {
            egressServiceClient.stopEgress(egressId).execute();
            log.warn("Đã dừng Egress {} vì không thể lưu bản ghi vào DB", egressId);
        } catch (Exception cleanupException) {
            log.error("Không thể dừng Egress mồ côi [{}]: {}",
                    egressId, cleanupException.getMessage(), cleanupException);
        }
    }

    private EgressWebhook parseEgressWebhook(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Payload webhook LiveKit phải là một JSON object");
            }
            JsonNode egressInfo = root.path("egressInfo");
            String egressId = firstNonBlank(
                    valueAt(root, "egressId", "egressID", "egress_id"),
                    valueAt(egressInfo, "egressId", "egressID", "egress_id"));
            String status = firstNonBlank(
                    valueAt(root, "status"),
                    valueAt(egressInfo, "status"));
            return new EgressWebhook(egressId, status, root, egressInfo);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload webhook LiveKit không phải JSON hợp lệ", exception);
        }
    }

    private String valueAt(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private boolean applyEgressWebhook(RecordingEntity recording, EgressWebhook webhook) {
        log.info("Xử lý dữ liệu EgressID: {} | Khớp trạng thái: {}", webhook.egressId(), webhook.status());
        return switch (webhook.status()) {
            case "EGRESS_STARTING" -> updateNonTerminalStatus(recording, RecordingStatus.STARTING);
            case "EGRESS_ACTIVE", "EGRESS_ENDING" -> updateNonTerminalStatus(recording, RecordingStatus.RECORDING);
            case "EGRESS_COMPLETE" -> completeRecording(recording, webhook.egressInfo());
            case "EGRESS_FAILED", "EGRESS_ABORTED", "EGRESS_LIMIT_REACHED" -> failRecording(recording, webhook);
            default -> {
                log.warn("Webhook Egress có trạng thái không nhận diện được: {}", webhook.status());
                yield false;
            }
        };
    }

    private boolean updateNonTerminalStatus(RecordingEntity recording, RecordingStatus status) {
        if (isTerminal(recording.getStatus())) {
            log.info("Bỏ qua trạng thái không kết thúc đến muộn cho Egress đã kết thúc: {}", recording.getEgressId());
            return false;
        }
        recording.setStatus(status);
        return true;
    }

    private boolean completeRecording(RecordingEntity recording, JsonNode egressInfo) {
        if (recording.getStatus() == RecordingStatus.FAILED) {
            log.warn("Bỏ qua EGRESS_COMPLETE đến muộn sau Egress FAILED: {}", recording.getEgressId());
            return false;
        }
        recording.setStatus(RecordingStatus.COMPLETED);
        updateCompletedFileMetadata(recording, egressInfo);
        return true;
    }

    private boolean failRecording(RecordingEntity recording, EgressWebhook webhook) {
        if (recording.getStatus() == RecordingStatus.COMPLETED) {
            log.warn("Bỏ qua trạng thái lỗi đến muộn sau Egress COMPLETED: {}", recording.getEgressId());
            return false;
        }
        recording.setStatus(RecordingStatus.FAILED);
        log.warn("Egress kết thúc không thành công [{}]: {}",
                webhook.status(),
                firstNonBlank(valueAt(webhook.egressInfo(), "error"), valueAt(webhook.root(), "error")));
        return true;
    }

    private boolean isTerminal(RecordingStatus status) {
        return status == RecordingStatus.COMPLETED || status == RecordingStatus.FAILED;
    }

    private boolean isTerminalWebhook(String status) {
        return "EGRESS_COMPLETE".equals(status)
                || "EGRESS_FAILED".equals(status)
                || "EGRESS_ABORTED".equals(status)
                || "EGRESS_LIMIT_REACHED".equals(status);
    }

    private record EgressWebhook(String egressId, String status, JsonNode root, JsonNode egressInfo) {
    }

    /**
     * LiveKit mới trả file trong fileResults, còn các phiên bản cũ dùng file.
     * Hỗ trợ cả hai để URL video không bị rỗng sau khi egress hoàn tất.
     */
    private void updateCompletedFileMetadata(RecordingEntity recording, JsonNode egressInfo) {
        if (egressInfo.isMissingNode() || egressInfo.isNull()) {
            log.info("Egress hoàn tất nhưng webhook không có egressInfo/file metadata.");
            return;
        }

        JsonNode fileNode = egressInfo.path("file");
        if (fileNode.isMissingNode() || fileNode.isNull()) {
            JsonNode fileResults = egressInfo.path("fileResults");
            if (fileResults.isArray() && !fileResults.isEmpty()) {
                fileNode = fileResults.get(0);
            }
        }

        if (fileNode.isMissingNode() || fileNode.isNull()) {
            log.info("Egress hoàn tất nhưng webhook không có file metadata.");
            return;
        }

        String location = fileNode.path("location").asText();
        if (!location.isBlank()) {
            recording.setFileUrl(location);
        }
        if (fileNode.hasNonNull("duration")) {
            recording.setDuration(fileNode.path("duration").asLong() / 1_000_000_000L);
        }
    }

    private void verifyHostPrivilege(String meetingCode) {
        String currentUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại"));

        if (!meeting.getHostId().equals(currentUserId)) {
            throw new SecurityException("Chỉ chủ phòng mới có quyền thực hiện thao tác này!");
        }
    }

    private RecordingResponse mapToResponse(RecordingEntity entity) {
        return RecordingResponse.builder()
                .id(entity.getId())
                .egressId(entity.getEgressId())
                .meetingCode(entity.getMeetingCode())
                .status(entity.getStatus().name())
                .fileUrl(entity.getFileUrl())
                .duration(entity.getDuration())
                .createdAt(entity.getCreatedAt())
                .purgeAfter(entity.getPurgeAfter())
                .build();
    }
}
