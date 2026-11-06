package com.openexchange.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final PrivateKey accessTokenPrivateKey;
    private final PublicKey accessTokenPublicKey;
    private final PrivateKey refreshTokenPrivateKey;
    private final PublicKey refreshTokenPublicKey;

    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.access-token-private-key}") String accessTokenPrivateKeyB64,
            @Value("${jwt.access-token-public-key}") String accessTokenPublicKeyB64,
            @Value("${jwt.refresh-token-private-key}") String refreshTokenPrivateKeyB64,
            @Value("${jwt.refresh-token-public-key}") String refreshTokenPublicKeyB64,
            @Value("${jwt.access-token-expiration:1800000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) throws Exception {

        // 使用 Java 标准 KeyFactory 构造 RSA 密钥
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] accessPrivateBytes = Decoders.BASE64.decode(accessTokenPrivateKeyB64);
        byte[] accessPublicBytes  = Decoders.BASE64.decode(accessTokenPublicKeyB64);
        byte[] refreshPrivateBytes = Decoders.BASE64.decode(refreshTokenPrivateKeyB64);
        byte[] refreshPublicBytes  = Decoders.BASE64.decode(refreshTokenPublicKeyB64);

        this.accessTokenPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(accessPrivateBytes));
        this.accessTokenPublicKey  = keyFactory.generatePublic(new X509EncodedKeySpec(accessPublicBytes));
        this.refreshTokenPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(refreshPrivateBytes));
        this.refreshTokenPublicKey  = keyFactory.generatePublic(new X509EncodedKeySpec(refreshPublicBytes));

        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // ---------- 生成令牌 ----------
    public String generateAccessToken(Long userId, String uid, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("uid", uid)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiration)))
                .signWith(accessTokenPrivateKey)   // 直接使用 PrivateKey 签名
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenExpiration)))
                .signWith(refreshTokenPrivateKey)
                .compact();
    }

    // ---------- 提取信息 ----------
    public Long getUserIdFromAccessToken(String token) {
        return Long.parseLong(parseAccessToken(token).getPayload().getSubject());
    }

    public Long getUserIdFromRefreshToken(String token) {
        return Long.parseLong(parseRefreshToken(token).getPayload().getSubject());
    }

    public String getEmailFromAccessToken(String token) {
        return parseAccessToken(token).getPayload().get("email", String.class);
    }

    // ---------- 验证 ----------
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public boolean validateAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            parseRefreshToken(token);
            return true;
        } catch (JwtException e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    private Jws<Claims> parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessTokenPublicKey)   // 使用公钥验证
                .build()
                .parseSignedClaims(token);
    }

    private Jws<Claims> parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshTokenPublicKey)
                .build()
                .parseSignedClaims(token);
    }
}