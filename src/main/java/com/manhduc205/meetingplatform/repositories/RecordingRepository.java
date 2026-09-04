package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.RecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import jakarta.persistence.LockModeType;

@Repository
public interface RecordingRepository extends JpaRepository<RecordingEntity, Long> {

    Optional<RecordingEntity> findByEgressId(String egressId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recording from RecordingEntity recording where recording.egressId = :egressId")
    Optional<RecordingEntity> findByEgressIdForUpdate(@Param("egressId") String egressId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recording from RecordingEntity recording where recording.id = :recordingId")
    Optional<RecordingEntity> findByIdForUpdate(@Param("recordingId") Long recordingId);

    @Query(value = "SELECT r.* FROM recordings r " +
            "LEFT JOIN meetings m ON r.meeting_code = m.meeting_code " +
            "WHERE r.id = :recordingId AND r.purge_after IS NULL AND (" +
            "   m.host_id = :userId " +
            "   OR (r.visibility = 'MEETING_MEMBERS' AND EXISTS (" +
            "       SELECT 1 FROM meeting_participants p " +
            "       WHERE p.meeting_id = m.id AND p.user_id = :userId" +
            "   )) " +
            "   OR (r.visibility = 'SELECTED_USERS' AND EXISTS (" +
            "       SELECT 1 FROM recording_shares rs " +
            "       WHERE rs.recording_egress_id = r.egress_id AND rs.shared_with_user_id = :userId" +
            "   )) " +
            ")", nativeQuery = true)
    Optional<RecordingEntity> findAccessibleRecordingById(@Param("userId") String userId,
                                                            @Param("recordingId") Long recordingId);

    List<RecordingEntity> findAllByMeetingCodeOrderByCreatedAtDesc(String meetingCode);

    /**
     * 🎯 1. LỌC THEO PHÒNG: Lấy danh sách video ĐƯỢC PHÉP XEM trong 1 phòng họp cụ thể
     */
    @Query(value = "SELECT r.* FROM recordings r " +
            "LEFT JOIN meetings m ON r.meeting_code = m.meeting_code " +
            "WHERE r.meeting_code = :meetingCode AND r.purge_after IS NULL AND (" +
            // 1. Nếu tôi là Host -> Quét sạch toàn bộ video
            "   m.host_id = :userId " +
            // 2. Nếu video mở cho người trong phòng -> Quét trong sổ điểm danh
            "   OR (r.visibility = 'MEETING_MEMBERS' AND EXISTS (" +
            "       SELECT 1 FROM meeting_participants p " +
            "       WHERE p.meeting_id = m.id AND p.user_id = :userId" +
            "   )) " +
            // 3. Nếu video share riêng tư -> Khớp theo recording_egress_id String chuẩn Entity của Đức
            "   OR (r.visibility = 'SELECTED_USERS' AND EXISTS (" +
            "       SELECT 1 FROM recording_shares rs " +
            "       WHERE rs.recording_egress_id = r.egress_id AND rs.shared_with_user_id = :userId" +
            "   )) " +
            ") ORDER BY r.created_at DESC", nativeQuery = true)
    List<RecordingEntity> findAccessibleRecordingsByMeetingCode(@Param("userId") String userId, @Param("meetingCode") String meetingCode);

    /**
     * 🎯 2. KHO LƯU TRỮ TỔNG: Quét toàn bộ video trên hệ thống mà User có quyền xem
     */
    @Query(value = "SELECT r.* FROM recordings r " +
            "LEFT JOIN meetings m ON r.meeting_code = m.meeting_code " +
            "WHERE r.purge_after IS NULL AND ( " +
            // 1. Nếu tôi là Host của cuộc họp đó -> Xem được hết
            "   m.host_id = :userId " +
            // 2. Nếu video mở cho người trong phòng -> Quét trong sổ điểm danh
            "   OR (r.visibility = 'MEETING_MEMBERS' AND EXISTS (" +
            "       SELECT 1 FROM meeting_participants p " +
            "       WHERE p.meeting_id = m.id AND p.user_id = :userId" +
            "   )) " +
            // 3. Nếu video share riêng tư -> Khớp theo recording_egress_id String chuẩn Entity của Đức
            "   OR (r.visibility = 'SELECTED_USERS' AND EXISTS (" +
            "       SELECT 1 FROM recording_shares rs " +
            "       WHERE rs.recording_egress_id = r.egress_id AND rs.shared_with_user_id = :userId" +
            "   )) " +
            ") ORDER BY r.created_at DESC", nativeQuery = true)
    List<RecordingEntity> findAllAccessibleRecordings(@Param("userId") String userId);

    @Query(value = "SELECT r.* FROM recordings r " +
            "JOIN meetings m ON r.meeting_code = m.meeting_code " +
            "WHERE m.host_id = :userId AND r.purge_after > :now " +
            "ORDER BY r.purge_after", nativeQuery = true)
    List<RecordingEntity> findTrashOwnedBy(@Param("userId") String userId, @Param("now") Instant now);

    @Query(value = "SELECT id FROM recordings " +
            "WHERE purge_after <= :now " +
            "ORDER BY purge_after LIMIT 20", nativeQuery = true)
    List<Long> findPurgeableIds(@Param("now") Instant now);
}
