package com.openexchange.wallet.service;

import com.openexchange.wallet.dto.DepositCallbackRequest;
import com.openexchange.wallet.entity.Deposit;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
public interface IDepositService extends IService<Deposit> {

    /**
     * 充值到账回调处理（内部接口，链上监听服务调用）。
     * 幂等键 = chain + txHash + logIndex，重复回调直接返回成功；
     * 首次回调时在数据库事务内：插入充值日志 → 更新用户余额 → 更新全局资产发行量，
     * 事务提交后异步发送用户通知（WebSocket/推送）。
     *
     * @param request 充值回调请求体
     */
    void handleDepositCallback(DepositCallbackRequest request);
}
