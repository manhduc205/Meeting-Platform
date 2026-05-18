package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.models.dtos.mappers.MeetingMapper;
import com.manhduc205.meetingplatform.models.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.enums.MeetingStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.RecordingService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingMapper meetingMapper;
    private final RecordingService recordingService;
    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        String internalUserId = UserContext.getUserId();
        log.info("ServiceImpl: Tạo cuộc họp cho host: {}", internalUserId);

        String meetingCode;
        do {
            meetingCode = generateNumericCode(10);
        } while (meetingRepository.existsByMeetingCode(meetingCode));

        MeetingEntity entity = meetingMapper.toEntity(request);
        entity.setMeetingCode(meetingCode);
        entity.setHostId(internalUserId);
        entity.setStatus(MeetingStatus.SCHEDULED.name());

        if (request.getIsWaitingRoomEnabled() == null) {
            entity.setIsWaitingRoomEnabled(true);
        }

        MeetingEntity saved = meetingRepository.save(entity);

        return meetingMapper.ToMeetingResponse(saved);
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

    @Override
    public MeetingResponse endMeeting(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        log.info("ServiceImpl: Kết thúc cuộc họp với ID: {} bởi host: {}", meetingCode, internalUserId);
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp với mã: " + meetingCode));

        if (!meeting.getHostId().equals(internalUserId)) {
            throw new SecurityException("Bạn không có quyền! Chỉ chủ phòng mới được kết thúc cuộc họp.");
        }
        if (meeting.getStatus().equals(MeetingStatus.ENDED.name())) {
            throw new IllegalStateException("Cuộc họp đã kết thúc trước đó.");
        }
        recordingService.stopActiveRecordings(meetingCode);
        meeting.setStatus(MeetingStatus.ENDED.name());
        meeting.setEndTime(java.time.LocalDateTime.now());

        MeetingEntity saved = meetingRepository.save(meeting);
        log.info("ServiceImpl: Cuộc họp [{}] đã được kết thúc thành công bởi host [{}]", meetingCode, internalUserId);
        return meetingMapper.ToMeetingResponse(saved);
    }
}
