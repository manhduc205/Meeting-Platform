package com.manhduc205.meetingplatform.models;

import com.manhduc205.meetingplatform.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "meetings", indexes = {
        @Index(name = "idx_meeting_code", columnList = "meeting_code"),
        @Index(name = "idx_host_id", columnList = "host_id")
})
@Builder
public class MeetingEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "meeting_code", unique = true, nullable = false)
    private String meetingCode;

    @Column(name = "meeting_password")
    private String meetingPassword;

    @Column(name = "host_id", nullable = false)
    private String hostId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "planned_start_time", nullable = false)
    private Instant plannedStartTime;

    @Column(name = "planned_end_time", nullable = false)
    private Instant plannedEndTime;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "google_event_id")
    private String googleEventId;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "is_screen_share_disabled")
    @Builder.Default
    private Boolean isScreenShareDisabled = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "is_waiting_room_enabled")
    @Builder.Default
    private Boolean isWaitingRoomEnabled = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = Instant.now();
    }
}
