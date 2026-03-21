package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.mappers.MeetingMapper;
import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.enums.MeetingStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.services.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMapper meetingMapper;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request, String hostId) {
        log.info("ServiceImpl: Đang tạo cuộc họp cho host: {}", hostId);

        String meetingCode;
        do {
            meetingCode = generateRandomCode();
        } while (meetingRepository.existsByMeetingCode(meetingCode));


        MeetingEntity entity = MeetingEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .hostId(hostId)
                .meetingCode(meetingCode)
                .meetingPassword(request.getMeetingPassword())
                .isWaitingRoomEnabled(request.getIsWaitingRoomEnabled() != null ? request.getIsWaitingRoomEnabled() : true)
                .status(MeetingStatus.SCHEDULED.name())
                .build();

        MeetingEntity saved = meetingRepository.save(entity);
        return meetingMapper.ToMeetingResponse(saved);
    }
    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                sb.append("-");
            }
            for (int j = 0; j < 3; j++) {
                sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }
        }
        return sb.toString();
    }

    @Override
    public MeetingResponse endMeeting(String meetingCode, String hostId) {
        log.info("ServiceImpl: Kết thúc cuộc họp với ID: {} bởi host: {}", meetingCode, hostId);
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp với mã: " + meetingCode));

        if (!meeting.getHostId().equals(hostId)) {
            log.warn("Cảnh báo bảo mật: User [{}] cố gắng kết thúc phòng [{}] trái phép!", hostId, meetingCode);
            throw new SecurityException("Bạn không có quyền! Chỉ chủ phòng mới được kết thúc cuộc họp.");
        }
        if(meeting.getStatus().equals(MeetingStatus.ENDED.name())){
            throw new IllegalStateException("Cuộc họp đã kết thúc trước đó.");
        }
        meeting.setStatus(MeetingStatus.ENDED.name());
        meeting.setEndTime(java.time.LocalDateTime.now());

        MeetingEntity saved = meetingRepository.save(meeting);
        log.info("ServiceImpl: Cuộc họp [{}] đã được kết thúc thành công bởi host [{}]", meetingCode, hostId);
        return meetingMapper.ToMeetingResponse(saved);
    }
}
