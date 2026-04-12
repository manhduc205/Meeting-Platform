package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.request.PollCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.PollResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.PollDocument;
import com.manhduc205.meetingplatform.models.PollOption;
import com.manhduc205.meetingplatform.models.PollVoteLog;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.PollMongoRepository;
import com.manhduc205.meetingplatform.repositories.PollVoteMongoRepository;
import com.manhduc205.meetingplatform.services.PollService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollServiceImpl implements PollService {

    private final MeetingRepository meetingRepository;
    private final PollMongoRepository pollMongoRepo;
    private final PollVoteMongoRepository voteMongoRepo;

    // 🔥 Inject thêm MongoTemplate để chốt hạ bài toán Atomic
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String POLL_VOTERS_PREFIX = "poll:voters:";
    private static final String POLL_COUNTS_PREFIX = "poll:counts:";
    private static final String POLL_TOPIC_PREFIX = "/topic/meeting.";
    private static final String POLL_TOPIC_SUFFIX = ".polls";
    private static final String ACTION_KEY = "action";

    @Override
    public PollResponse createPoll(String meetingCode, PollCreateRequest request) {
        String internalUserId = UserContext.getUserId();

        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

        if (!String.valueOf(meeting.getHostId()).equals(internalUserId)) {
            throw new SecurityException("Chỉ chủ phòng mới được tạo khảo sát");
        }

        List<PollOption> options = new ArrayList<>();
        for (String text : request.getOptions()) {
            options.add(new PollOption(UUID.randomUUID().toString(), text));
        }

        PollDocument poll = PollDocument.builder()
                .id(UUID.randomUUID().toString())
                .meetingCode(meetingCode)
                .question(request.getQuestion())
                .isMultipleChoice(request.getIsMultipleChoice())
                .createdBy(internalUserId)
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .options(options)
                .build();
        pollMongoRepo.save(poll);

        // 🔥 FIX 1: Chống lỗi Serialization sinh ra chuỗi ""0""
        String countsKey = POLL_COUNTS_PREFIX + poll.getId();
        for (PollOption opt : options) {
            stringRedisTemplate.opsForHash().put(countsKey, opt.getId(), "0");
        }

        PollResponse response = buildPollResponse(poll, options, internalUserId);
        messagingTemplate.convertAndSend(POLL_TOPIC_PREFIX + meetingCode + POLL_TOPIC_SUFFIX,
                Map.of(ACTION_KEY, "POLL_CREATED", "data", response));

        return response;
    }

    @Override
    public void submitVote(String meetingCode, String pollId, String optionId) {
        String internalUserId = UserContext.getUserId();
        log.info("🎯 [VOTE START] User: {}, Poll: {}, Option: {}", internalUserId, pollId, optionId);

        PollDocument poll = pollMongoRepo.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Khảo sát không tồn tại"));

        validatePollAndOption(poll, optionId);

        String countsKey = POLL_COUNTS_PREFIX + pollId;
        String votersKey = POLL_VOTERS_PREFIX + pollId;

        // 🔒 REDIS LOCK (Chống Spam)
        String lockKey = "lock:poll:" + pollId + ":user:" + internalUserId;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(3));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("🚫 [VOTE SPAM] Chặn request trùng lặp từ User: {}", internalUserId);
            return;
        }

        try {
            // 🔥 ĐỌC DATA & TỰ CHỮA LÀNH
            List<PollVoteLog> userLogs = voteMongoRepo.findByPollIdAndUserId(pollId, internalUserId);

            if (!userLogs.isEmpty()) {
                PollVoteLog existingLog = userLogs.get(0);
                cleanupDuplicateVotes(userLogs, internalUserId);

                String oldOptionId = existingLog.getOptionId();

                if (oldOptionId.equals(optionId)) {
                    log.info("⏸️ User click lại option cũ, không làm gì cả.");
                    return;
                }

                // 🔥 FIX 2: ATOMIC UPDATE TUYỆT ĐỐI BẰNG MONGO TEMPLATE
                Query query = new Query(Criteria.where("_id").is(existingLog.getId()).and("optionId").is(oldOptionId));
                Update update = new Update().set("optionId", optionId).set("votedAt", LocalDateTime.now());

                long modifiedCount = mongoTemplate.updateFirst(query, update, PollVoteLog.class).getModifiedCount();

                if (modifiedCount > 0) {
                    stringRedisTemplate.opsForHash().increment(countsKey, oldOptionId, -1);
                    stringRedisTemplate.opsForHash().increment(countsKey, optionId, 1);
                    log.info("✅ [UPDATE] Hoán đổi từ {} sang {}", oldOptionId, optionId);
                } else {
                    log.warn("❌ Race condition bị chặn khi Update.");
                    return; // Fail thì thoát, KHÔNG broadcast
                }

            } else {
                // 🔥 FIX 3: VOTE MỚI KÈM UNIQUE INDEX BẢO VỆ
                try {
                    voteMongoRepo.save(PollVoteLog.builder()
                            .id(UUID.randomUUID().toString())
                            .pollId(pollId)
                            .optionId(optionId)
                            .userId(internalUserId)
                            .votedAt(LocalDateTime.now())
                            .build());

                    stringRedisTemplate.opsForSet().add(votersKey, internalUserId);
                    stringRedisTemplate.opsForHash().increment(countsKey, optionId, 1);
                    log.info("✅ [INSERT] Vote mới thành công.");
                } catch (DuplicateKeyException e) {
                    log.warn("❌ Chặn Double Insert bằng Unique Index.");
                    return; // Fail thì thoát, KHÔNG broadcast
                }
            }

            // BROADCAST KẾT QUẢ ĐÃ ĐỒNG BỘ
            broadcastVoteUpdate(meetingCode, pollId, countsKey);

        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void validatePollAndOption(PollDocument poll, String optionId) {
        if ("CLOSED".equals(poll.getStatus())) throw new IllegalStateException("Bình chọn đã đóng!");
        if (poll.getOptions().stream().noneMatch(opt -> opt.getId().equals(optionId))) {
            throw new IllegalArgumentException("OptionId không hợp lệ!");
        }
    }

    private void cleanupDuplicateVotes(List<PollVoteLog> oldLogs, String internalUserId) {
        if (oldLogs.size() > 1) {
            log.error("⚠️ DỌN RÁC: Xóa {} bản ghi thừa của User {}", oldLogs.size() - 1, internalUserId);
            for (int i = 1; i < oldLogs.size(); i++) {
                voteMongoRepo.deleteById(oldLogs.get(i).getId());
            }
        }
    }

    private void broadcastVoteUpdate(String meetingCode, String pollId, String countsKey) {
        Map<Object, Object> updatedCounts = stringRedisTemplate.opsForHash().entries(countsKey);
        messagingTemplate.convertAndSend(POLL_TOPIC_PREFIX + meetingCode + POLL_TOPIC_SUFFIX,
                Map.of(ACTION_KEY, "VOTE_UPDATED", "pollId", pollId, "newCounts", updatedCounts));
    }

    @Override
    public void closePoll(String meetingCode, String pollId) {
        PollDocument poll = pollMongoRepo.findById(pollId).orElseThrow();
        poll.setStatus("CLOSED");
        pollMongoRepo.save(poll);

        messagingTemplate.convertAndSend(POLL_TOPIC_PREFIX + meetingCode + POLL_TOPIC_SUFFIX,
                Map.of(ACTION_KEY, "POLL_CLOSED", "pollId", pollId));
    }

    private PollResponse buildPollResponse(PollDocument poll, List<PollOption> options, String userId) {
        String countsKey = POLL_COUNTS_PREFIX + poll.getId();
        String votersKey = POLL_VOTERS_PREFIX + poll.getId();

        Map<Object, Object> allCounts = stringRedisTemplate.opsForHash().entries(countsKey);

        List<PollVoteLog> userVotes = voteMongoRepo.findByPollIdAndUserId(poll.getId(), userId);
        String votedOptionId = userVotes.isEmpty() ? null : userVotes.get(0).getOptionId();

        List<PollResponse.PollOptionDto> optionDtos = new ArrayList<>(options.size());
        long totalVotes = 0;

        for (PollOption opt : options) {
            Object countObj = allCounts.get(opt.getId());
            String cleanCount = (countObj != null) ? countObj.toString().replace("\"", "") : "0";
            long count = cleanCount.isEmpty() ? 0 : Long.parseLong(cleanCount);

            totalVotes += count;
            // Tối ưu Algorithm O(1) tra cứu
            boolean isVotedByMe = opt.getId().equals(votedOptionId);

            optionDtos.add(PollResponse.PollOptionDto.builder()
                    .id(opt.getId())
                    .text(opt.getText())
                    .voteCount(count)
                    .votedByMe(isVotedByMe)
                    .build());
        }

        Boolean hasVoted = stringRedisTemplate.opsForSet().isMember(votersKey, userId);

        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .isMultipleChoice(poll.getIsMultipleChoice())
                .status(poll.getStatus())
                .options(optionDtos)
                .totalVotes(totalVotes)
                .hasVoted(Boolean.TRUE.equals(hasVoted))
                .build();
    }
}