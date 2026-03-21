package com.manhduc205.meetingplatform.dtos.mappers;

import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MeetingMapper {
    MeetingResponse ToMeetingResponse(MeetingEntity meetingEntity);
}

