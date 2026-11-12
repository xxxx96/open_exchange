package com.openexchange.spot.service;

import com.openexchange.spot.dto.BalanceVO;
import com.openexchange.spot.entity.SpotBalance;

import java.util.List;

/**
 * 现货钱包（交易账户账本）服务：冻结/解冻/核销/入账 + 余额查询。
 * 账本权威数据源，直接读库，不做任何链上查询。
 */
public interface ISpotBalanceService {

    /**
     * 确保现货钱包存在（懒创建，UNIQUE(user_id, currency_id, wallet_type) 兜底并发）
     */
    SpotBalance ensureSpotWallet(Long userId, Long currencyId);

    /**
     * 冻结：available -= amount，frozen += amount；余额不足抛 60001
     */
    void freeze(Long userId, Long currencyId, java.math.BigDecimal amount);

    /**
     * 解冻：frozen -= amount，available += amount（撤单退回）
     */
    void unfreeze(Long userId, Long currencyId, java.math.BigDecimal amount);

    /**
     * 核销冻结：frozen -= amount（成交花费）
     */
    void consumeFrozen(Long userId, Long currencyId, java.math.BigDecimal amount);

    /**
     * 入账：available += amount（成交收到）
     */
    void credit(Long userId, Long currencyId, java.math.BigDecimal amount);

    /**
     * 查询交易账户余额（total = available + locked，最小单位字符串）
     */
    List<BalanceVO> listBalances(Long userId, String asset);

    /**
     * 查询钱包行（不存在返回 null）
     */
    SpotBalance getByUserAndCurrency(Long userId, Long currencyId);
}
