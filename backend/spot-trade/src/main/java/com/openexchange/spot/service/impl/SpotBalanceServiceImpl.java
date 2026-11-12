package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import com.openexchange.spot.dto.BalanceVO;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.entity.SpotBalance;
import com.openexchange.spot.mapper.SpotBalanceMapper;
import com.openexchange.spot.service.ICurrencyService;
import com.openexchange.spot.service.ISpotBalanceService;
import com.openexchange.spot.util.AmountCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 现货钱包服务实现：与 wallet-service 共享 wallets 表，按 wallet_type='SPOT' 分行记账。
 * 所有余额变动走 SpotBalanceMapper 原子 SQL（条件即校验），不依赖实体级乐观锁。
 *
 * <p>余额查询接口调用频率高，按文档建议加 Redis 短 TTL 缓存（默认 3 秒，
 * infrastructure/docker-compose.yml 的 exchange-redis），任何余额变动
 * （冻结/解冻/核销/入账）都会主动 DEL 该用户的缓存键；Redis 读写故障一律降级
 * 直读数据库，不影响主流程。多节点部署下缓存与失效天然全局一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotBalanceServiceImpl extends ServiceImpl<SpotBalanceMapper, SpotBalance> implements ISpotBalanceService {

    private static final String WALLET_TYPE_SPOT = "SPOT";
    private static final String BALANCE_CACHE_KEY_PREFIX = "spot:balance:";

    private final SpotBalanceMapper spotBalanceMapper;
    private final ICurrencyService currencyService;
    private final SpotTradeProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SpotBalance ensureSpotWallet(Long userId, Long currencyId) {
        SpotBalance wallet = getByUserAndCurrency(userId, currencyId);
        if (wallet != null) {
            return wallet;
        }
        SpotBalance created = new SpotBalance();
        created.setUserId(userId);
        created.setCurrencyId(currencyId);
        created.setWalletType(WALLET_TYPE_SPOT);
        created.setBalance(BigDecimal.ZERO);
        created.setFrozenBalance(BigDecimal.ZERO);
        created.setVersion(0L);   // wallets.version 为 NOT NULL，须显式置零
        try {
            save(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发懒创建：唯一约束兜底，冲突后重查
            return getByUserAndCurrency(userId, currencyId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId, Long currencyId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "freeze amount must be positive");
        }
        ensureSpotWallet(userId, currencyId);
        int rows = spotBalanceMapper.freeze(userId, currencyId, amount);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SPOT_INSUFFICIENT_BALANCE);
        }
        evictCache(userId);
    }

    @Override
    public void unfreeze(Long userId, Long currencyId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        int rows = spotBalanceMapper.unfreeze(userId, currencyId, amount);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "unfreeze failed, frozen balance insufficient: user=" + userId + " currency=" + currencyId);
        }
        evictCache(userId);
    }

    @Override
    public void consumeFrozen(Long userId, Long currencyId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        int rows = spotBalanceMapper.consumeFrozen(userId, currencyId, amount);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "consume frozen failed, frozen balance insufficient: user=" + userId + " currency=" + currencyId);
        }
        evictCache(userId);
    }

    @Override
    public void credit(Long userId, Long currencyId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        // 首次收到该资产的用户可能还没有 SPOT 钱包行，懒创建后再入账，避免资产静默丢失
        ensureSpotWallet(userId, currencyId);
        spotBalanceMapper.credit(userId, currencyId, amount);
        evictCache(userId);
    }

    @Override
    public List<BalanceVO> listBalances(Long userId, String asset) {
        List<BalanceVO> balances = cachedBalances(userId);
        if (asset == null || asset.isBlank()) {
            return balances;
        }
        String target = asset.trim();
        return balances.stream()
                .filter(b -> b.getAsset() != null && b.getAsset().equalsIgnoreCase(target))
                .toList();
    }

    /** 取该用户全量非零余额：先查 Redis，未命中或降级时读库并回填（TTL 默认 3 秒） */
    private List<BalanceVO> cachedBalances(Long userId) {
        long ttl = properties.getBalance().getCacheTtlMillis();
        if (ttl <= 0) {
            return loadBalances(userId);
        }
        String key = cacheKey(userId);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<BalanceVO>>() {
                });
            }
        } catch (Exception e) {
            // Redis 故障不阻断查询：降级直读数据库
            log.warn("[balance] Redis 读取失败，降级读库 userId={}: {}", userId, e.getMessage());
        }
        List<BalanceVO> balances = loadBalances(userId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(balances),
                    Duration.ofMillis(ttl));
        } catch (Exception e) {
            log.warn("[balance] Redis 写入失败 userId={}: {}", userId, e.getMessage());
        }
        return balances;
    }

    /** 读库构建全量非零余额列表（原 listBalances 主体） */
    private List<BalanceVO> loadBalances(Long userId) {
        List<SpotBalance> wallets = list(Wrappers.<SpotBalance>lambdaQuery()
                .eq(SpotBalance::getUserId, userId)
                .eq(SpotBalance::getWalletType, WALLET_TYPE_SPOT));
        if (wallets.isEmpty()) {
            return List.of();
        }
        // 批量解析币种（符号 + decimals），避免逐行查询
        Map<Long, Currency> currencyMap = new LinkedHashMap<>();
        for (SpotBalance w : wallets) {
            if (!currencyMap.containsKey(w.getCurrencyId())) {
                currencyMap.put(w.getCurrencyId(), currencyService.getById(w.getCurrencyId()));
            }
        }
        List<BalanceVO> result = new ArrayList<>();
        for (SpotBalance w : wallets) {
            Currency currency = currencyMap.get(w.getCurrencyId());
            if (currency == null) {
                continue;
            }
            BigDecimal available = w.getBalance() == null ? BigDecimal.ZERO : w.getBalance();
            BigDecimal locked = w.getFrozenBalance() == null ? BigDecimal.ZERO : w.getFrozenBalance();
            if (available.signum() == 0 && locked.signum() == 0) {
                continue;   // 零余额行不返回（交易所惯例，减少噪音）
            }
            int decimals = currency.getDecimals() == null ? 18 : currency.getDecimals();
            result.add(BalanceVO.builder()
                    .asset(currency.getSymbol())
                    .available(AmountCodec.toMinimal(available, decimals))
                    .locked(AmountCodec.toMinimal(locked, decimals))
                    .total(AmountCodec.toMinimal(available.add(locked), decimals))
                    .build());
        }
        return result;
    }

    /** 余额变动后主动失效缓存（freeze/unfreeze/consume/credit 后调用） */
    private void evictCache(Long userId) {
        try {
            redisTemplate.delete(cacheKey(userId));
        } catch (Exception e) {
            // 失效失败时残留数据最迟在 TTL 后自然过期，可接受
            log.warn("[balance] Redis 缓存失效失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private String cacheKey(Long userId) {
        return BALANCE_CACHE_KEY_PREFIX + userId;
    }

    @Override
    public SpotBalance getByUserAndCurrency(Long userId, Long currencyId) {
        return getOne(Wrappers.<SpotBalance>lambdaQuery()
                .eq(SpotBalance::getUserId, userId)
                .eq(SpotBalance::getCurrencyId, currencyId)
                .eq(SpotBalance::getWalletType, WALLET_TYPE_SPOT), false);
    }
}
