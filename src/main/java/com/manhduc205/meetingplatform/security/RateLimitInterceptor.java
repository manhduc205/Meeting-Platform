package com.manhduc205.meetingplatform.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
    Nếu counter >= 5 → return 429 Too Many Requests
    Nếu counter < 5 → increment counter + pass request
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    // Rate limit configuration
    private static final String RATE_LIMIT_PREFIX = "rate-limit:join:";
    private static final int MAX_REQUESTS = 5;  // 5 requests per window
    private static final long WINDOW_SECONDS = 10;  // 10 second window

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method) || !requestURI.matches(".*/api/v1/meetings/.*/join$")) {
            return true;  // Pass through - không apply rate limit
        }

        try {
            // Extract meetingCode từ URL path: /api/v1/meetings/{meetingCode}/join
            String[] pathParts = requestURI.split("/");
            if (pathParts.length < 5) {
                return true;  // Pass through
            }
            String meetingCode = pathParts[pathParts.length - 2];

            String userId = extractUserIdFromRequest(request);
            if (userId == null) {
                return true;  // Pass through
            }

            // Check rate limit
            String rateLimitKey = RATE_LIMIT_PREFIX + meetingCode + ":" + userId;
            Object counterObj = redisTemplate.opsForValue().get(rateLimitKey);
            int currentCount = counterObj != null ? Integer.parseInt(counterObj.toString()) : 0;

            if (currentCount >= MAX_REQUESTS) {
                log.warn(" RATE LIMIT EXCEEDED: User [{}] exceeded {} requests in {} seconds for room [{}]",
                        userId, MAX_REQUESTS, WINDOW_SECONDS, meetingCode);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                        "{\"error\": \"Rate limit exceeded. Max %d requests per %d seconds\"}",
                        MAX_REQUESTS, WINDOW_SECONDS
                ));
                return false;
            }
            redisTemplate.opsForValue().increment(rateLimitKey);

            if (currentCount == 0) {
                redisTemplate.expire(rateLimitKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            } else {
                log.debug("✅ Rate limit check passed: {}/{} requests for user [{}]",
                        currentCount + 1, MAX_REQUESTS, userId);
            }

            return true;

        } catch (Exception e) {
            return true;
        }
    }
    private String extractUserIdFromRequest(HttpServletRequest request) {
        try {
            // Lấy từ Spring Security Context (nếu đã authenticate)
            var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
            if (securityContext != null && securityContext.getAuthentication() != null) {
                Object principal = securityContext.getAuthentication().getPrincipal();
                if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                    return jwt.getSubject();
                }
            }

            return null;
        } catch (Exception e) {
            log.error("❌ Error extracting userId from request", e);
            return null;
        }
    }
}

