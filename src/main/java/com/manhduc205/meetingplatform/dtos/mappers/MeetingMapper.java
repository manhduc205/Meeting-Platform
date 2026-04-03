package com.manhduc205.meetingplatform.dtos.mappers;

import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MeetingMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "meetingCode", ignore = true)
    @Mapping(target = "hostId", ignore = true) // Sẽ set thủ công trong Service
    @Mapping(target = "createdAt", ignore = true)
    MeetingEntity toEntity(MeetingCreateRequest request);
    @Named("formatMeetingCode")
    default String formatMeetingCode(String code) {
        if (code == null || code.length() < 10) return code;
        return String.format("%s-%s-%s",
                code.substring(0, 3),
                code.substring(3, 6),
                code.substring(6));
    }
    MeetingResponse ToMeetingResponse(MeetingEntity meetingEntity);
}

