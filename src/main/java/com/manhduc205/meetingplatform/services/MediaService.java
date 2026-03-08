package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;

public interface MediaService {
    MediaJoinResponse prepareMediaConnection(String meetingCode, String userId);
}