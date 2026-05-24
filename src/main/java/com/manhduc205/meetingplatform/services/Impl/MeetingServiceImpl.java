package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.MeetingStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.dtos.mappers.MeetingMapper;
import com.manhduc205.meetingplatform.models.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.RecordingService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMapper meetingMapper;
    private final RecordingService recordingService;

    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        String internalUserId = UserContext.getUserId();
        log.info("ServiceImpl: Xử lý yêu cầu tạo cuộc họp cho host: {}", internalUserId);

        Instant now = Instant.now();
        String meetingStatus;

        if (request.getStartTime() != null) {
            // Luồng 1: Lên lịch (Scheduled)
            if (request.getStartTime().isBefore(now)) {
                throw new IllegalArgumentException("Thời gian bắt đầu không được nằm trong quá khứ.");
            }
            if (request.getEndTime() != null && request.getEndTime().isBefore(request.getStartTime())) {
                throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
            }
            if (request.getEndTime() == null) {
                //  định 2 tiếng nếu User lười không điền lúc lên lịch
                request.setEndTime(request.getStartTime().plus(2, ChronoUnit.HOURS));
            }
            meetingStatus = MeetingStatus.SCHEDULED.name();
        } else {
            request.setStartTime(now);
            request.setEndTime(now.plus(24, ChronoUnit.HOURS));
            meetingStatus = MeetingStatus.ONGOING.name();
        }

        String meetingCode;
        do {
            meetingCode = generateNumericCode(10);
        } while (meetingRepository.existsByMeetingCode(meetingCode));

        MeetingEntity entity = meetingMapper.toEntity(request);
        entity.setMeetingCode(meetingCode);
        entity.setHostId(internalUserId);
        entity.setStatus(meetingStatus);
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());

        if (request.getIsWaitingRoomEnabled() == null) {
            entity.setIsWaitingRoomEnabled(true);
        }

        MeetingEntity saved = meetingRepository.save(entity);
        return meetingMapper.ToMeetingResponse(saved);
    }

    @Override
    @Transactional
    public MeetingResponse endMeeting(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp với mã: " + meetingCode));

        if (!meeting.getHostId().equals(internalUserId)) {
            throw new SecurityException("Chỉ chủ phòng mới được kết thúc cuộc họp.");
        }
        if (meeting.getStatus().equals(MeetingStatus.ENDED.name())) {
            throw new IllegalStateException("Cuộc họp đã kết thúc trước đó.");
        }

        recordingService.stopActiveRecordings(meetingCode);

        meeting.setStatus(MeetingStatus.ENDED.name());
        meeting.setEndTime(Instant.now());

        MeetingEntity saved = meetingRepository.save(meeting);
        log.info("ServiceImpl: Cuộc họp [{}] đã được chốt sổ bởi host [{}]", meetingCode, internalUserId);
        return meetingMapper.ToMeetingResponse(saved);
    }

    @Override
    public List<MeetingResponse> getMyMeetings() {
        String hostId = UserContext.getUserId();
        // Dùng stream API tối ưu loop lấy dữ liệu
        return meetingRepository.findAllByHostIdOrderByStartTimeAsc(hostId)
                .stream()
                .map(meetingMapper::ToMeetingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MeetingResponse updateMeeting(String meetingCode, MeetingCreateRequest request) {
        String hostId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

        if (!meeting.getHostId().equals(hostId)) {
            throw new SecurityException("Chỉ chủ phòng mới được sửa lịch họp.");
        }

        if (MeetingStatus.ENDED.name().equals(meeting.getStatus()) || "CANCELLED".equals(meeting.getStatus())) {
            throw new IllegalStateException("Không thể sửa cuộc họp đã kết thúc hoặc bị hủy.");
        }

        if (request.getStartTime() != null && request.getStartTime().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Thời gian dời lịch không được nằm trong quá khứ.");
        }
        if (request.getEndTime() != null && request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("Thời gian kết thúc không hợp lệ.");
        }

        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        if (request.getStartTime() != null) meeting.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) meeting.setEndTime(request.getEndTime());
        if (request.getIsWaitingRoomEnabled() != null) meeting.setIsWaitingRoomEnabled(request.getIsWaitingRoomEnabled());
        if (request.getMeetingPassword() != null) meeting.setMeetingPassword(request.getMeetingPassword());

        return meetingMapper.ToMeetingResponse(meetingRepository.save(meeting));
    }

    @Override
    @Transactional
    public void cancelMeeting(String meetingCode) {
        String hostId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

        if (!meeting.getHostId().equals(hostId)) {
            throw new SecurityException("Chỉ chủ phòng mới được hủy cuộc họp.");
        }

        meeting.setStatus("CANCELLED");
        meetingRepository.save(meeting);
        log.info("ServiceImpl: Cuộc họp [{}] đã bị hủy bỏ bởi host.", meetingCode);
    }

    private String generateNumericCode(int length) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i == 0) {
                sb.append(random.nextInt(9) + 1);
            } else {
                sb.append(random.nextInt(10));
            }
        }
        return sb.toString();
    }
}