package com.openexchange.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.wallet.dto.BalanceResponse;
import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.entity.Wallet;
import com.openexchange.wallet.mapper.WalletMapper;
import com.openexchange.wallet.service.ICurrencyService;
import com.openexchange.wallet.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <p>
 * 钱包（余额账本）服务实现：余额查询与现货钱包的懒创建。
 * 本地账本（wallets 表）为唯一权威数据源，余额查询不做任何链上查询。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements IWalletService {

    /**
     * 现金钱包类型（wallets.wallet_type），现货余额记账均使用该类型
     */
    private static final String WALLET_TYPE_SPOT = "SPOT";

    private final ICurrencyService currencyService;

    @Override
    public BalanceResponse getBalance(Long userId, String asset) {
        // 1. 仅按资产符号解析币种（余额查询不区分链，账本以资产维度记账）
        Currency currency = currencyService.resolveBySymbol(asset);
        // 2. 查询现货钱包，账本权威、直接读库返回，不做任何链上查询
        Wallet wallet = getOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getUserId, userId)
                .eq(Wallet::getCurrencyId, currency.getId())
                .eq(Wallet::getWalletType, WALLET_TYPE_SPOT), false);
        BigDecimal available = wallet != null && wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
        BigDecimal frozen = wallet != null && wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO;
        // 3. total = available + frozen，全部换算为最小单位整数字符串，前端自行除以 decimals 展示
        int decimals = currency.getDecimals() != null ? currency.getDecimals() : 18;
        return BalanceResponse.builder()
                .userId(userId)
                .asset(asset)
                .available(toMinimalUnit(available, decimals))
                .frozen(toMinimalUnit(frozen, decimals))
                .total(toMinimalUnit(available.add(frozen), decimals))
                .build();
    }

    @Override
    public Wallet ensureSpotWallet(Long userId, Long currencyId) {
        Wallet wallet = getOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getUserId, userId)
                .eq(Wallet::getCurrencyId, currencyId)
                .eq(Wallet::getWalletType, WALLET_TYPE_SPOT), false);
        if (wallet != null) {
            return wallet;
        }
        // 钱包不存在则懒创建：UNIQUE(user_id, currency_id, wallet_type) 约束兜底并发创建，冲突后重查
        Wallet created = new Wallet();
        created.setUserId(userId);
        created.setCurrencyId(currencyId);
        created.setWalletType(WALLET_TYPE_SPOT);
        created.setBalance(BigDecimal.ZERO);
        created.setFrozenBalance(BigDecimal.ZERO);
        created.setVersion(0L);
        try {
            save(created);
            return created;
        } catch (DuplicateKeyException e) {
            return getOne(Wrappers.<Wallet>lambdaQuery()
                    .eq(Wallet::getUserId, userId)
                    .eq(Wallet::getCurrencyId, currencyId)
                    .eq(Wallet::getWalletType, WALLET_TYPE_SPOT), false);
        }
    }

    /**
     * 人可读金额 → 最小单位整数字符串（乘以 10^decimals 后向下截断），如 999.5 USDT → "999500000"
     */
    private String toMinimalUnit(BigDecimal amount, int decimals) {
        return amount.multiply(BigDecimal.TEN.pow(decimals))
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger()
                .toString();
    }
}
