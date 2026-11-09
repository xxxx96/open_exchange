package com.openexchange.wallet.service;

import com.openexchange.wallet.dto.WithdrawalCreateRequest;
import com.openexchange.wallet.dto.WithdrawalCreateResponse;
import com.openexchange.wallet.dto.WithdrawalStatusResponse;
import com.openexchange.wallet.entity.Withdrawal;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
public interface IWithdrawalService extends IService<Withdrawal> {

    /**
     * 发起提现：以客户端生成的 clientId 作为幂等键去重（重复提交返回已有 withdrawalId），
     * 校验余额充足（available >= amount + fee）后冻结资金，插入提现单（状态 submitted），
     * 后续异步进入：风控检查 → 签名 → 广播 → 确认。
     *
     * @param request 发起提现请求体
     * @return 提现单对外 ID
     */
    WithdrawalCreateResponse createWithdrawal(WithdrawalCreateRequest request);

    /**
     * 查询提现进度：将内部状态映射为对外三种状态
     * （pending-处理中 / success-已完成 / failed-失败），不暴露内部处理细节。
     *
     * @param withdrawalId 提现单对外 ID（wd_日期_序号）
     * @return 提现状态响应
     */
    WithdrawalStatusResponse getWithdrawalStatus(String withdrawalId);
}
