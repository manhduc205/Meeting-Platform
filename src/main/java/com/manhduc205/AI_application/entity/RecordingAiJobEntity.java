package com.manhduc205.AI_application.entity;

import com.manhduc205.AI_application.enums.RecordingAiJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_ai_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingAiJobEntity {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "recording_id", nullable = false)
    private Long recordingId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(nullable = false, length = 40)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordingAiJobStatus status;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (requestedAt == null) requestedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
