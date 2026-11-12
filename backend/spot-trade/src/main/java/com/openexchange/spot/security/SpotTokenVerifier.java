package com.openexchange.spot.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * WS 握手 token 校验：浏览器无法为 WebSocket 握手设置 Authorization 头，
 * token 经 query 参数传递，由本组件用与 gateway / user-service 同源的
 * access token 公钥（RSA，Base64(X.509)）验签，非法 token 拒绝连接（4001）。
 */
@Slf4j
@Component
public class SpotTokenVerifier {

    private final PublicKey accessTokenPublicKey;

    public SpotTokenVerifier(@Value("${jwt.access-token-public-secret}") String accessTokenPublicKeyB64)
            throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] accessPublicBytes = Decoders.BASE64.decode(accessTokenPublicKeyB64);
        this.accessTokenPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(accessPublicBytes));
    }

    /**
     * 校验 access token 并返回 userId（subject）；非法/过期返回 null。
     */
    public Long verifyAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jws<io.jsonwebtoken.Claims> jws = Jwts.parser()
                    .verifyWith(accessTokenPublicKey)
                    .build()
                    .parseSignedClaims(token);
            String subject = jws.getPayload().getSubject();
            return subject == null ? null : Long.parseLong(subject);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("WS token 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
