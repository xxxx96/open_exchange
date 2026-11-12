package com.openexchange.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷新令牌黑名单（Redis 实现）
 * <p>
 * 用于登出后使 refresh token 失效。key = auth:refresh:revoked:{token}，
 * TTL 取 refresh token 有效期（过期后自动清理，无需额外回收任务）。
 * 多实例共享同一份黑名单，实例重启后仍然有效。
 * </p>
 * <p>
 * Redis 不可用时降级为本进程内存集合：只保证当前实例的安全（多实例/重启场景不完整），
 * 同时打印告警，便于监控发现。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenBlacklist {

    /**
     * Redis key 前缀：与网关/其他服务共用 auth 命名空间
     */
    private static final String KEY_PREFIX = "auth:refresh:revoked:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis 故障时的本进程兜底集合（不做 TTL 回收，仅覆盖故障窗口）
     */
    private final Set<String> localFallback = ConcurrentHashMap.newKeySet();

    /**
     * 黑名单 TTL：与 refresh token 有效期一致，过期后自动失效
     */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long ttlMillis;

    /**
     * 吊销 refresh token
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            localFallback.add(token);
            log.warn("Refresh token 黑名单写入 Redis 失败，已降级为本进程内存（多实例失效不完整）: {}",
                    e.getMessage());
        }
    }

    /**
     * 判断 refresh token 是否已被吊销
     */
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Refresh token 黑名单查询 Redis 失败，使用本进程兜底: {}", e.getMessage());
        }
        return localFallback.contains(token);
    }
}
