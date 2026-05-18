package com.manhduc205.meetingplatform.services.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.meetingplatform.services.RecordingService;
import com.manhduc205.meetingplatform.utils.UserContext;
import io.livekit.server.EgressServiceClient;
import livekit.LivekitEgress.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingServiceImpl implements RecordingService {

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;
    private final EgressServiceClient egressServiceClient;
    @Value("${app.livekit.api-key}") private String apiKey;
    @Value("${app.livekit.api-secret}") private String apiSecret;
    @Value("${app.livekit.host}") private String liveKitHost;

    @Value("${app.minio.access-key}") private String minioAccessKey;
    @Value("${app.minio.secret-key}") private String minioSecretKey;
    @Value("${app.minio.endpoint}") private String minioEndpoint;
    @Value("${app.minio.bucket}") private String minioBucket;

    @Override
    @Transactional
    public RecordingResponse startRecording(String meetingCode) {
        verifyHostPrivilege(meetingCode);

        try {
            // 1. Cấu hình S3 Output (Giữ nguyên)
            S3Upload s3Output = S3Upload.newBuilder()
                    .setAccessKey(minioAccessKey)
                    .setSecret(minioSecretKey)
                    .setEndpoint(minioEndpoint)
                    .setBucket(minioBucket)
                    .setForcePathStyle(true)
                    .build();

            // 2. Build EncodedFileOutput (Giữ nguyên)
            EncodedFileOutput fileOutput = EncodedFileOutput.newBuilder()
                    .setFileType(EncodedFileType.MP4)
                    .setS3(s3Output)
                    .build();

            // 3. SỬA TẠI ĐÂY: Gọi trực tiếp với 2 tham số (RoomName và FileOutput)
            // SDK của em không nhận đối tượng Request, nó nhận trực tiếp tham số
            var call = egressServiceClient.startRoomCompositeEgress(meetingCode, fileOutput);

            EgressInfo info = call.execute().body();

            if (info == null) {
                throw new RuntimeException("LiveKit Server không trả về thông tin Egress.");
            }

            RecordingEntity entity = RecordingEntity.builder()
                    .meetingCode(meetingCode)
                    .egressId(info.getEgressId())
                    .status(RecordingStatus.STARTING)
                    .build();

            return mapToResponse(recordingRepository.save(entity));
        } catch (Exception e) {
            log.error("Lỗi khởi tạo Egress: {}", e.getMessage());
            throw new RuntimeException("Không thể bắt đầu ghi hình cuộc họp: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void stopRecording(String meetingCode, String egressId) {
        // 2. Kiểm tra quyền Host (Chỉ Host mới được dừng ghi âm)
        verifyHostPrivilege(meetingCode);

        try {
            EgressServiceClient egressClient = EgressServiceClient.create(liveKitHost, apiKey, apiSecret);
            egressClient.stopEgress(egressId).execute();
            log.info("Host đã dừng ghi âm cho EgressID: {}", egressId);
        } catch (Exception e) {
            log.error("Lỗi khi dừng Egress: {}", e.getMessage());
            throw new RuntimeException("Không thể dừng ghi hình.");
        }
    }

    @Override
    public List<RecordingResponse> getMeetingRecordings(String meetingCode) {
        // 3. Quyền xem lại: Mọi người trong phòng đều có thể lấy danh sách bản ghi
        return recordingRepository.findAllByMeetingCodeOrderByCreatedAtDesc(meetingCode)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void handleEgressWebhook(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode egressInfo = root.path("egressInfo");
            String egressId = egressInfo.path("egressId").asText();

            RecordingEntity recording = recordingRepository.findByEgressId(egressId)
                    .orElseThrow(() -> new IllegalArgumentException("Bản ghi không tồn tại"));

            int statusInt = egressInfo.path("status").asInt();
            if (statusInt == 3) {
                recording.setStatus(RecordingStatus.RECORDING);
            } else if (statusInt == 4) {
                recording.setStatus(RecordingStatus.COMPLETED);
                JsonNode fileNode = egressInfo.path("file");
                if (!fileNode.isMissingNode()) {
                    recording.setFileUrl(fileNode.path("location").asText());
                    recording.setDuration(fileNode.path("duration").asLong() / 1000000000L);
                }
            } else if (statusInt == 5) {
                recording.setStatus(RecordingStatus.FAILED);
            }
            recordingRepository.save(recording);
        } catch (Exception e) {
            log.error("Lỗi Webhook Egress: {}", e.getMessage());
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
                .egressId(entity.getEgressId())
                .meetingCode(entity.getMeetingCode())
                .status(entity.getStatus().name())
                .fileUrl(entity.getFileUrl())
                .duration(entity.getDuration())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}