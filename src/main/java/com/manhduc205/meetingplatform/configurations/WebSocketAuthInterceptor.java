package com.manhduc205.meetingplatform.configurations;

import com.manhduc205.meetingplatform.services.JwtService;
import com.manhduc205.meetingplatform.services.UserIdCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
// Lớp này sẽ được sử dụng để kiểm tra xác thực khi kết nối WebSocket, đảm bảo chỉ người dùng đã đăng nhập mới có thể tham gia cuộc họp qua WebSocket
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final UserIdCacheService userIdCacheService;
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            Optional.ofNullable(authHeader)
                    .filter(header -> header.startsWith("Bearer "))
                    .map(header -> header.substring(7))
                    .ifPresentOrElse(
                            token -> {
                                String keycloakId = jwtService.validateTokenAndGetUserId(token);
                                String userId = userIdCacheService.getOrResolveInternalId(keycloakId);
                                // Xác thực thành công -> Gán User vào Session của WebSocket
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(userId, null, null);
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                accessor.setUser(auth);

                                log.info("Xác thực WebSocket thành công cho User: {}", userId);
                            },
                            () -> {
                                throw new IllegalArgumentException("Vui lòng cung cấp JWT Token hợp lệ!");
                            }
                    );
        }
        return message;
    }
}
