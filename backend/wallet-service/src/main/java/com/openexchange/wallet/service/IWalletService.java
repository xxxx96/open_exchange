package com.openexchange.wallet.service;

import com.openexchange.wallet.dto.BalanceResponse;
import com.openexchange.wallet.entity.Wallet;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
public interface IWalletService extends IService<Wallet> {

    /**
     * 查询用户某资产的可用/冻结余额。
     * 以本地账本（wallets 表）为准，直接读库返回，不做任何链上查询；
     * 金额以最小单位整数字符串返回，前端展示时自行除以 decimals。
     *
     * @param userId 用户 ID
     * @param asset  资产符号，如 USDT
     * @return 余额响应（available / frozen / total，均为最小单位）
     */
    BalanceResponse getBalance(Long userId, String asset);

    /**
     * 确保用户的现货钱包存在：不存在则创建初始钱包（余额为 0）。
     * 表上的 UNIQUE(user_id, currency_id, wallet_type) 约束兑底并发创建，冲突后重查。
     *
     * @param userId     用户 ID
     * @param currencyId 资产 ID
     * @return 已存在的或新建的现货钱包记录
     */
    Wallet ensureSpotWallet(Long userId, Long currencyId);
}
