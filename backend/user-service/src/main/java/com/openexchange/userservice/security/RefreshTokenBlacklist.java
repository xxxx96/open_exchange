package com.openexchange.userservice.security;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷新令牌黑名单（内存实现）
 * <p>
 * 用于登出后使 refresh token 失效。后续可替换为 Redis 实现。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Component
public class RefreshTokenBlacklist {

    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 吊销 refresh token
     */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            revokedTokens.add(token);
        }
    }

    /**
     * 判断 refresh token 是否已被吊销
     */
    public boolean isRevoked(String token) {
        return token != null && revokedTokens.contains(token);
    }
}
