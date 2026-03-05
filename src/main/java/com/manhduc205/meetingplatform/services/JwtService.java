package com.manhduc205.meetingplatform.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtDecoder jwtDecoder;
    public String validateTokenAndGetUserId(String token) {
        try {
            //  Decoder sẽ tự động check Hạn sử dụng (EXP) và Chữ ký số (Signature)
            Jwt jwt = jwtDecoder.decode(token);

            String userId = jwt.getSubject();
            return userId;

        } catch (JwtException e) {
            throw new SecurityException("Xác thực thất bại! Token không hợp lệ hoặc đã hết hạn.");
        }
    }
}