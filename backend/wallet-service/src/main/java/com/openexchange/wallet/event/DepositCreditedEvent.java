package com.openexchange.wallet.event;

import java.math.BigDecimal;

/**
 * <p>
 * 充值入账完成事件：在充值回调的数据库事务提交成功后发布，
 * 由异步监听器（WebSocket/推送）通知用户资产变化。
 * </p>
 *
 * @param userId   用户 ID
 * @param asset    资产符号
 * @param amount   入账金额（人可读单位）
 * @param txHash   充值交易哈希
 * @author yanglei
 * @since 2026-11-06
 */
public record DepositCreditedEvent(Long userId, String asset, BigDecimal amount, String txHash) {
}
