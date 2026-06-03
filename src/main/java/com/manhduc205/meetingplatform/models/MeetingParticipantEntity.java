package com.manhduc205.meetingplatform.models;

import com.manhduc205.meetingplatform.enums.ParticipantRole;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "meeting_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_user", columnNames = {"meeting_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_meeting_joined_time", columnList = "meeting_id,joined_once_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingParticipantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ParticipantRole role;

    @Column(name = "joined_once_at", nullable = false, updatable = false)
    private LocalDateTime joinedOnceAt;
}