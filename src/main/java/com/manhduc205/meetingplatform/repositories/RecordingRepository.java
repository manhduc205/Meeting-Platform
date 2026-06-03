package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.RecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecordingRepository extends JpaRepository<RecordingEntity, Long> {

    Optional<RecordingEntity> findByEgressId(String egressId);

    List<RecordingEntity> findAllByMeetingCodeOrderByCreatedAtDesc(String meetingCode);

    /**
     * 🎯 1. LỌC THEO PHÒNG: Lấy danh sách video ĐƯỢC PHÉP XEM trong 1 phòng họp cụ thể
     */
    @Query(value = "SELECT r.* FROM recordings r " +
            "LEFT JOIN meetings m ON r.meeting_code = m.meeting_code " +
            "WHERE r.meeting_code = :meetingCode AND (" +
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
            "WHERE ( " +
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
}