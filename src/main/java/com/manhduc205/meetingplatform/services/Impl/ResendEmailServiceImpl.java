package com.manhduc205.meetingplatform.services.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.models.OutboxEventEntity;
import com.manhduc205.meetingplatform.services.ResendEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResendEmailServiceImpl implements ResendEmailService {
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.notifications.resend.api-key}")
    private String apiKey;
    @Value("${app.notifications.resend.from}")
    private String from;

    @Override
    public void sendInvitation(OutboxEventEntity event) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("RESEND_API_KEY chưa được cấu hình.");
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String recipient = payload.required("recipientEmail").asText();
            String title = payload.required("title").asText();
            String hostName = payload.path("hostName").asText("Host");
            Instant start = Instant.parse(payload.required("plannedStartTime").asText());
            Instant end = Instant.parse(payload.required("plannedEndTime").asText());
            String meetingCode = payload.required("meetingCode").asText();
            String joinUrl = payload.required("joinUrl").asText();
            String meetingPassword = payload.path("meetingPassword").asText("");
            String ics = buildIcs(meetingCode, title, hostName, recipient, start, end, joinUrl);
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", List.of(recipient),
                    "subject", "Invitation: " + title,
                    "html", buildHtml(title, hostName, start, end, meetingCode, meetingPassword, joinUrl),
                    "text", buildText(title, meetingCode, meetingPassword, joinUrl),
                    "attachments", List.of(Map.of("filename", "meeting.ics", "content", Base64.getEncoder().encodeToString(ics.getBytes(StandardCharsets.UTF_8))))
            );
            ResponseEntity<Void> response = restClientBuilder.baseUrl("https://api.resend.com").build().post().uri("/emails")
                    .header("Authorization", "Bearer " + apiKey).header("Idempotency-Key", "meeting-invitation-" + event.getId())
                    .body(body).retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Resend từ chối email: HTTP " + response.getStatusCode().value());
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException) throw (IllegalStateException) exception;
            throw new IllegalStateException("Không thể gửi email qua Resend.", exception);
        }
    }

    private String buildHtml(String title, String hostName, Instant start, Instant end, String code, String meetingPassword, String joinUrl) {
        return "<h2>You're invited to " + escape(title) + "</h2><p>Host: " + escape(hostName) + "</p>" +
                "<p>Starts: " + start + "<br>Ends: " + end + "</p><p>Meeting code: <strong>" + escape(code) +
                "</strong></p>" + (meetingPassword.isBlank() ? "" : "<p>Passcode: <strong>" + escape(meetingPassword) +
                "</strong></p>") + "<p><a href=\"" + escape(joinUrl) + "\">Join meeting</a></p>";
    }

    private String buildText(String title, String code, String meetingPassword, String joinUrl) {
        return "You are invited to " + title + ". Meeting code: " + code +
                (meetingPassword.isBlank() ? "" : ". Passcode: " + meetingPassword) + ". Join: " + joinUrl;
    }

    private String buildIcs(String code, String title, String hostName, String recipient, Instant start, Instant end, String joinUrl) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Meeting Platform//EN\r\nMETHOD:REQUEST\r\nBEGIN:VEVENT\r\n" +
                "UID:" + escapeIcs(code) + "@meeting-platform\r\nDTSTAMP:" + ICS_TIME.format(Instant.now()) + "\r\n" +
                "DTSTART:" + ICS_TIME.format(start) + "\r\nDTEND:" + ICS_TIME.format(end) + "\r\n" +
                "SUMMARY:" + escapeIcs(title) + "\r\nORGANIZER;CN=" + escapeIcs(hostName) + ":mailto:noreply@meeting-platform\r\n" +
                "ATTENDEE;RSVP=TRUE:mailto:" + escapeIcs(recipient) + "\r\nURL:" + escapeIcs(joinUrl) + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeIcs(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
