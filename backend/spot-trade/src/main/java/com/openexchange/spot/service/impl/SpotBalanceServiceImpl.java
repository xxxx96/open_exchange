package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 现货钱包服务实现：与 wallet-service 共享 wallets 表，按 wallet_type='SPOT' 分行记账。
 * 所有余额变动走 SpotBalanceMapper 原子 SQL（条件即校验），不依赖实体级乐观锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotBalanceServiceImpl extends ServiceImpl<SpotBalanceMapper, SpotBalance> implements ISpotBalanceService {

    private static final String WALLET_TYPE_SPOT = "SPOT";

    private final SpotBalanceMapper spotBalanceMapper;
    private final ICurrencyService currencyService;

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
    }

    @Override
    public void credit(Long userId, Long currencyId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        // 首次收到该资产的用户可能还没有 SPOT 钱包行，懒创建后再入账，避免资产静默丢失
        ensureSpotWallet(userId, currencyId);
        spotBalanceMapper.credit(userId, currencyId, amount);
    }

    @Override
    public List<BalanceVO> listBalances(Long userId, String asset) {
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
            if (asset != null && !asset.isBlank()
                    && !currency.getSymbol().equalsIgnoreCase(asset.trim())) {
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

    @Override
    public SpotBalance getByUserAndCurrency(Long userId, Long currencyId) {
        return getOne(Wrappers.<SpotBalance>lambdaQuery()
                .eq(SpotBalance::getUserId, userId)
                .eq(SpotBalance::getCurrencyId, currencyId)
                .eq(SpotBalance::getWalletType, WALLET_TYPE_SPOT), false);
    }
}
