package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.entity.YoutubeSummaryEntity;
import com.manhduc205.AI_application.enums.YoutubeSummaryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface YoutubeSummaryRepository extends JpaRepository<YoutubeSummaryEntity, String> {
    Optional<YoutubeSummaryEntity> findFirstByRequestedByAndVideoIdAndTargetLanguageAndStatusInOrderByRequestedAtDesc(
            String requestedBy, String videoId, String targetLanguage, Collection<YoutubeSummaryStatus> statuses);

    Optional<YoutubeSummaryEntity> findByIdAndRequestedBy(String id, String requestedBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select summary from YoutubeSummaryEntity summary where summary.id = :id")
    Optional<YoutubeSummaryEntity> findByIdForUpdate(@Param("id") String id);
}
