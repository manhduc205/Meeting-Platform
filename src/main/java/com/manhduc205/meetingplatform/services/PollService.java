package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.request.PollCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.PollResponse;

public interface PollService {
    PollResponse createPoll(String meetingCode, PollCreateRequest request);
    void submitVote(String meetingCode, String pollId, String optionId);
    void closePoll(String meetingCode, String pollId);
}
