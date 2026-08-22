package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.exceptions.ResourceNotFoundException;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.services.HostService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import com.manhduc205.meetingplatform.utils.UserContext;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.TrackInfo;
import livekit.LivekitModels.TrackSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class HostServiceImpl implements HostService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantService participantService;
    private final RoomServiceClient roomServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /** Prevent a removed participant from immediately minting a new media token. */
    private static final String KICKED_PARTICIPANT_PREFIX = "meeting:kicked:";
    private static final long KICKED_PARTICIPANT_TTL_HOURS = 12;

    private MeetingEntity getValidatedMeeting(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getHostId().equals(internalUserId)) {
            log.warn("Cảnh báo bảo mật: User [{}] cố gắng dùng quyền Host tại phòng [{}]", internalUserId, meetingCode);
            throw new SecurityException("Chỉ Host mới có quyền thực hiện hành động này");
        }
        return meeting;
    }

    @Override
    @Transactional
    public void updateSecuritySetting(String meetingCode, String type, boolean enabled) {
        MeetingEntity meeting = getValidatedMeeting(meetingCode);

        switch (type) {
            case "LOCK_MEETING":
                meeting.setIsLocked(enabled);
                break;
            case "WAITING_ROOM":
                meeting.setIsWaitingRoomEnabled(enabled);
                break;
            case "DISABLE_SCREEN_SHARE":
                meeting.setIsScreenShareDisabled(enabled);
                break;
        }
        meetingRepository.save(meeting);

        sendHostCommand(meetingCode, "SETTING_CHANGED", null, Map.of("type", type, "enabled", enabled));
    }

    @Override
    public void muteAll(String meetingCode) {
        MeetingEntity meeting = getValidatedMeeting(meetingCode);
        List<ParticipantInfo> participants = listLiveKitParticipants(meetingCode);
        for (ParticipantInfo participant : participants) {
            if (!meeting.getHostId().equals(participant.getIdentity())) {
                muteMicrophoneTracks(meetingCode, participant);
            }
        }
        sendHostCommand(meetingCode, "MUTE_ALL", null, null);
    }

    @Override
    public void muteParticipant(String meetingCode, String targetUserId) {
        MeetingEntity meeting = getValidatedMeeting(meetingCode);
        validateTarget(meeting, targetUserId);

        ParticipantInfo participant = getLiveKitParticipant(meetingCode, targetUserId);
        if (participant != null) {
            muteMicrophoneTracks(meetingCode, participant);
        }
        sendHostCommand(meetingCode, "MUTE_PARTICIPANT", targetUserId, null);
    }

    @Override
    public void kickUser(String meetingCode, String targetUserId) {
        MeetingEntity meeting = getValidatedMeeting(meetingCode);
        validateTarget(meeting, targetUserId);

        // Block re-entry before the notification/removal. A stale page refresh
        // must not be able to issue a fresh LiveKit token after being kicked.
        stringRedisTemplate.opsForValue().set(
                KICKED_PARTICIPANT_PREFIX + meetingCode + ":" + targetUserId,
                "1",
                KICKED_PARTICIPANT_TTL_HOURS,
                TimeUnit.HOURS
        );

        // Notify first so a well-behaved client can show the reason before LiveKit
        // closes its media connection. The server-side removal remains authoritative.
        sendHostCommand(meetingCode, "KICK_PARTICIPANT", targetUserId, null);
        removeLiveKitParticipant(meetingCode, targetUserId);
        participantService.removeParticipantByHost(meetingCode, targetUserId);
    }

    private void validateTarget(MeetingEntity meeting, String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("Thiếu người tham gia cần thao tác");
        }
        if (meeting.getHostId().equals(targetUserId)) {
            throw new IllegalArgumentException("Host không thể tự thao tác bằng lệnh này");
        }
    }

    private List<ParticipantInfo> listLiveKitParticipants(String meetingCode) {
        try {
            Response<List<ParticipantInfo>> response = roomServiceClient.listParticipants(meetingCode).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body();
            }
            log.warn("Không lấy được participant LiveKit của phòng {}: HTTP {}", meetingCode, response.code());
        } catch (IOException e) {
            log.error("Không kết nối được LiveKit khi mute phòng {}", meetingCode, e);
        }
        return List.of();
    }

    private ParticipantInfo getLiveKitParticipant(String meetingCode, String targetUserId) {
        try {
            Response<ParticipantInfo> response = roomServiceClient.getParticipant(meetingCode, targetUserId).execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            if (response.code() != 404) {
                log.warn("Không lấy được participant {} của LiveKit: HTTP {}", targetUserId, response.code());
            }
        } catch (IOException e) {
            log.error("Không kết nối được LiveKit khi mute participant {}", targetUserId, e);
        }
        return null;
    }

    private void muteMicrophoneTracks(String meetingCode, ParticipantInfo participant) {
        for (TrackInfo track : participant.getTracksList()) {
            if (track.getSource() != TrackSource.MICROPHONE || track.getMuted()) {
                continue;
            }
            try {
                Response<TrackInfo> response = roomServiceClient
                        .mutePublishedTrack(meetingCode, participant.getIdentity(), track.getSid(), true)
                        .execute();
                if (!response.isSuccessful()) {
                    log.warn("Không mute được track {} của {}: HTTP {}", track.getSid(), participant.getIdentity(), response.code());
                }
            } catch (IOException e) {
                log.error("Không kết nối được LiveKit khi mute track {}", track.getSid(), e);
            }
        }
    }

    private void removeLiveKitParticipant(String meetingCode, String targetUserId) {
        try {
            Response<Void> response = roomServiceClient.removeParticipant(meetingCode, targetUserId).execute();
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IllegalStateException("Không thể kick participant khỏi LiveKit (HTTP " + response.code() + ")");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể kết nối LiveKit để kick participant", e);
        }
    }

    private void sendHostCommand(String meetingCode, String action, String targetId, Map<String, Object> settings) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("action", action);
        if (targetId != null) body.put("targetId", targetId);
        if (settings != null) body.putAll(settings);
        body.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/meeting." + meetingCode + ".host-commands", body);
    }
}
