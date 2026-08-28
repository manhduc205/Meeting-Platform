package com.manhduc205.meetingplatform.models;

import com.manhduc205.meetingplatform.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meeting_invitations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_meeting_invitee_email", columnNames = {"meeting_id", "invitee_email"})
}, indexes = {
        @Index(name = "idx_invitations_invitee_status", columnList = "invitee_email,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingInvitationEntity {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "invitee_email", nullable = false, length = 320)
    private String inviteeEmail;

    @Column(name = "invitee_user_id")
    private String inviteeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
