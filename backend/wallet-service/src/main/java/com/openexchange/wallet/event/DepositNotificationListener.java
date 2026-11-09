package com.openexchange.wallet.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 充值到账通知监听器：异步监听充值入账完成事件，
 * 通过 WebSocket/推送等渠道告知用户资产变化。
 * 当前以日志占位，接入具体推送通道后在此分发。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Component
public class DepositNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DepositNotificationListener.class);

    /**
     * 充值到账异步通知：仅在入账事务提交成功后触发（发布方注册在 afterCommit），
     * 因此此处执行失败不影响账本数据一致性。
     */
    @Async
    @EventListener
    public void onDepositCredited(DepositCreditedEvent event) {
        // TODO: 接入 WebSocket/推送通道后在此下发用户通知
        log.info("[deposit-notify] user={} asset={} amount={} txHash={}",
                event.userId(), event.asset(), event.amount().toPlainString(), event.txHash());
    }
}
