package com.manhduc205.meetingplatform.models;

import com.manhduc205.meetingplatform.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "meetings")
@Builder
public class MeetingEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "meeting_code", unique = true, nullable = false)
    private String meetingCode;

    @Column(name = "host_id", nullable = false)
    private String hostId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(length = 20)
    @Builder.Default
    private String status = MeetingStatus.SCHEDULED.name();

    @Column(name = "is_waiting_room_enabled")
    @Builder.Default
    private Boolean isWaitingRoomEnabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
    }
}
