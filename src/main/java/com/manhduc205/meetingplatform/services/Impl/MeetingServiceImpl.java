package com.manhduc205.meetingplatform.services.Impl;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request, String hostId) {
        log.info("ServiceImpl: Đang tạo cuộc họp cho host: {}", hostId);

        String meetingCode ;
        do {
            meetingCode = generateRandomCode();
        } while (meetingRepository.existsByMeetingCode(meetingCode));

        MeetingEntity entity = MeetingEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .hostId(hostId)
                .meetingCode(meetingCode)
                .isWaitingRoomEnabled(request.getIsWaitingRoomEnabled() != null ? request.getIsWaitingRoomEnabled() : true)
                .status(MeetingStatus.SCHEDULED.name())
                .build();

        MeetingEntity saved = meetingRepository.save(entity);
        return mapToResponse(saved);
    }
    private String generateRandomCode() {
        return IntStream.range(0, 3)
                .mapToObj(i -> IntStream.range(0, 3)
                        .mapToObj(j -> String.valueOf(CHARS.charAt(random.nextInt(CHARS.length()))))
                        .collect(Collectors.joining()))
                .collect(Collectors.joining("-"));
    }
    private MeetingResponse mapToResponse(MeetingEntity entity) {
        return MeetingResponse.builder()
                .id(entity.getId())
                .meetingCode(entity.getMeetingCode())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .hostId(entity.getHostId())
                .status(entity.getStatus())
                .startTime(entity.getStartTime())
                .isWaitingRoomEnabled(entity.getIsWaitingRoomEnabled())
                .createdAt(entity.getCreatedAt())
                .build();
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
        if(meeting .getStatus().equals(MeetingStatus.ENDED.name())){
            throw new IllegalStateException("Cuộc họp đã kết thúc trước đó.");
        }
        meeting.setStatus(MeetingStatus.ENDED.name());
        meeting.setEndTime(java.time.LocalDateTime.now());

        MeetingEntity saved = meetingRepository.save(meeting);
        log.info("ServiceImpl: Cuộc họp [{}] đã được kết thúc thành công bởi host [{}]", meetingCode, hostId);
        return mapToResponse(saved);
    }
}
