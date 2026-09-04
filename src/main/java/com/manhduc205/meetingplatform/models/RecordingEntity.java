package com.manhduc205.meetingplatform.models;

import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.meetingplatform.enums.RecordingVisibility;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "recordings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_code", nullable = false)
    private String meetingCode;

    @Column(name = "egress_id", unique = true, nullable = false)
    private String egressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordingStatus status;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "duration")
    private Long duration; // Độ dài video (giây)

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private RecordingVisibility visibility;

    /**
     * Prefix inside the MinIO bucket. All assets belonging to this recording
     * are written below this prefix, for example recordings/{uuid}/video/source.mp4.
     */
    @Column(name = "storage_prefix")
    private String storagePrefix;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
