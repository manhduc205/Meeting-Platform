package com.manhduc205.meetingplatform.filters;

import com.manhduc205.meetingplatform.services.UserIdCacheService;
import com.manhduc205.meetingplatform.utils.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserMappingFilter extends OncePerRequestFilter {

    private final UserIdCacheService userIdCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Bóc tách JWT và lấy "sub" (Keycloak ID)
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                String keycloakId = jwt.getSubject();

                // Dịch ID và tiêm vào ThreadLocal
                String internalId = userIdCacheService.getOrResolveInternalId(keycloakId);
                log.info("✅ Filter Mapping: KeycloakID [{}] -> InternalID [{}]", keycloakId, internalId);
                UserContext.setUserId(internalId);
            }

            // Tiếp tục chuỗi Filter
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("🚨 Lỗi trong quá trình Mapping User ID: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid User Identity");
        } finally {
            // Tối ưu quản lý rủi ro rò rỉ bộ nhớ (Memory Leak)
            UserContext.clear();
        }
    }
}