package com.manhduc205.meetingplatform.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recording_shares",
        indexes = {
                @Index(name = "idx_share_recording_user", columnList = "recording_egress_id,shared_with_user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordingShareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_egress_id", nullable = false)
    private String recordingEgressId;

    @Column(name = "shared_with_user_id", nullable = false)
    private String sharedWithUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}