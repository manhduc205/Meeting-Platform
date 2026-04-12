package com.manhduc205.meetingplatform.repositories;


import com.manhduc205.meetingplatform.models.PollVoteLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PollVoteMongoRepository extends MongoRepository<PollVoteLog, String> {
    List<PollVoteLog> findByPollIdAndUserId(String pollId, String userId);
    @Query("{ '_id': ?0, 'optionId': ?1 }")
    @Update("{ '$set': { 'optionId': ?2, 'votedAt': ?3 } }")
    long updateOptionIdIfMatch(String id, String oldOptionId, String newOptionId, LocalDateTime now);
}
