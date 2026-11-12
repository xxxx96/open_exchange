package com.openexchange.spot.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 交易对 / 币种字典的 Redis 缓存。
 *
 * <p>字典数据几乎不变，但下单、撤单、查单、成交查询的每条链路都要解析，
 * 不缓存时每单要读 3~4 次数据库。缓存 key 统一 {@code spot:dict:*}，
 * TTL 默认 60 秒（{@code spot.cache.dictionary-ttl-seconds}），
 * 后台修改交易对/币种配置后最迟 TTL 生效。</p>
 *
 * <p>Redis 读写异常一律降级为直接查库，不影响下单主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotDictionaryCache {

    private static final String KEY_PREFIX = "spot:dict:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SpotTradeProperties properties;

    /**
     * 读缓存，未命中则调用 loader 查库并回填；loader 返回 null（数据不存在）时不缓存空值，
     * 避免缓存穿透掩盖后续新增的数据。
     */
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        long ttlSeconds = properties.getCache().getDictionaryTtlSeconds();
        if (ttlSeconds <= 0) {
            return loader.get();
        }
        String redisKey = KEY_PREFIX + key;
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                return objectMapper.readValue(cached, type);
            }
        } catch (Exception e) {
            log.warn("[dict] Redis 读取失败，降级读库 key={}: {}", redisKey, e.getMessage());
        }
        T value = loader.get();
        if (value != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(value),
                        Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                log.warn("[dict] Redis 写入失败 key={}: {}", redisKey, e.getMessage());
            }
        }
        return value;
    }
}
