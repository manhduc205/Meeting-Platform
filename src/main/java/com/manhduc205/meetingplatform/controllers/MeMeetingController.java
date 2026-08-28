package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.response.CalendarMeetingResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeMeetingController {
    private final MeetingService meetingService;

    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarMeetingResponse>> getCalendar(
            @RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(meetingService.getCalendar(from, to));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<CalendarMeetingResponse>> getUpcoming(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(meetingService.getUpcoming(limit));
    }
}
