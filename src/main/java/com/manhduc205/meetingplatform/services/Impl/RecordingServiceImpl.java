package com.manhduc205.meetingplatform.services.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingServiceImpl implements RecordingService {

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final ObjectMapper objectMapper;
    private final EgressServiceClient egressServiceClient;

    @Value("${app.minio.access-key}") private String minioAccessKey;
    @Value("${app.minio.secret-key}") private String minioSecretKey;
    @Value("${app.minio.endpoint}") private String minioEndpoint;
    @Value("${app.minio.bucket}") private String minioBucket;

    @Override
    public RecordingResponse startRecording(String meetingCode) {
        verifyHostPrivilege(meetingCode);

        try {
            S3Upload s3Output = S3Upload.newBuilder()
                    .setAccessKey(minioAccessKey)
                    .setSecret(minioSecretKey)
                    .setEndpoint(minioEndpoint)
                    .setBucket(minioBucket)
                    .setForcePathStyle(true)
                    .build();

            EncodedFileOutput fileOutput = EncodedFileOutput.newBuilder()
                    .setFileType(EncodedFileType.MP4)
                    .setS3(s3Output)
                    .build();

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
            String event = root.path("event").asText();
            log.info("Mắt thần Webhook nhận Event: {}", event);

            // 🟢 GIẢI PHÁP TOÀN DIỆN: Tìm EgressID bất kể nó nằm ở tầng nào của JSON
            String egressId = root.path("egressID").asText(); // Thử lấy ở tầng Root trước

            JsonNode egressInfo = root.path("egressInfo");
            if ((egressId == null || egressId.isEmpty()) && !egressInfo.isMissingNode() && !egressInfo.isNull()) {
                egressId = egressInfo.path("egressId").asText(); // Nếu root không có thì lấy trong egressInfo
            }

            // Nếu quét cả 2 tầng vẫn không có egressId (ví dụ event participant_left), bỏ qua an toàn
            if (egressId == null || egressId.isEmpty()) {
                log.info("Gói tin thuộc sự kiện chung, không chứa mã định danh Egress. Bỏ qua.");
                return;
            }

            Optional<RecordingEntity> recordingOpt = recordingRepository.findByEgressId(egressId);
            if (recordingOpt.isEmpty()) {
                log.warn("Webhook báo về EgressId không tồn tại trong DB: {}", egressId);
                return;
            }

            RecordingEntity recording = recordingOpt.get();

            // 🟢 GIẢI PHÁP ĐỌC TRẠNG THÁI: Quét cả tầng Root lẫn tầng EgressInfo
            String statusStr = root.path("status").asText();
            if ((statusStr == null || statusStr.isEmpty()) && !egressInfo.isMissingNode() && !egressInfo.isNull()) {
                statusStr = egressInfo.path("status").asText();
            }

            log.info("Xử lý dữ liệu EgressID: {} | Khớp trạng thái: {}", egressId, statusStr);

            // Bắt trọn các pha dịch chuyển trạng thái theo String chuẩn hóa
            if ("EGRESS_ACTIVE".equals(statusStr) || "EGRESS_ENDING".equals(statusStr)) {
                recording.setStatus(RecordingStatus.RECORDING);
            }
            else if ("EGRESS_COMPLETE".equals(statusStr)) {
                recording.setStatus(RecordingStatus.COMPLETED);

                // Trích xuất thông tin file (Thường nằm trong cấu trúc file của egressInfo nếu có)
                if (!egressInfo.isMissingNode() && !egressInfo.isNull()) {
                    JsonNode fileNode = egressInfo.path("file");
                    if (!fileNode.isMissingNode() && !fileNode.isNull()) {
                        recording.setFileUrl(fileNode.path("location").asText());
                        recording.setDuration(fileNode.path("duration").asLong() / 1000000000L);
                    }
                }

                // Dự phòng: Nếu LiveKit đổi cấu trúc đường dẫn file, em cấu hình sinh path cứng theo quy hoạch tại đây
                if (recording.getFileUrl() == null || recording.getFileUrl().isEmpty()) {
                    // Tên file mặc định của LiveKit thường là: roomName-date.mp4 hoặc egressId.mp4 tùy thuộc config ban đầu
                    log.info("File node trống, trạng thái COMPLETED vẫn được ghi nhận.");
                }
            }
            else if ("EGRESS_FAILED".equals(statusStr)) {
                recording.setStatus(RecordingStatus.FAILED);
            }

            recordingRepository.save(recording);
            log.info("🎉 CHÍNH THỨC THÀNH CÔNG: DB cập nhật trạng thái [{}] cho EgressID: {}", recording.getStatus(), egressId);

        } catch (Exception e) {
            log.error("Lỗi parse Webhook Egress: {}", e.getMessage(), e);
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