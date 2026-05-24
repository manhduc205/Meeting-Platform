package com.manhduc205.meetingplatform.models.dtos.mappers;

import com.manhduc205.meetingplatform.models.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.models.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper cho Participant
 * Chuyển đổi User sang Participant DTO nhẹ
 */
@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "fullName", target = "firstName")  // Tạm sử dụng fullName làm firstName
    @Mapping(source = "avatarUrl", target = "avatarUrl")
    @Mapping(target = "lastName", ignore = true)  // Có thể extend UserEntity nếu cần
    @Mapping(target = "status", ignore = true)     // Set status riêng từ Redis/Database
    ParticipantDto toParticipantDto(UserEntity userEntity);
}

