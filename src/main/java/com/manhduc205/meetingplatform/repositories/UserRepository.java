package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByKeycloakId(String keycloakId);
}