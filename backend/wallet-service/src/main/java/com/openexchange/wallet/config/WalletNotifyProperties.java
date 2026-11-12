package com.openexchange.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * 消息通知配置：充值到账等账本事件通过 Kafka 异步分发（WebSocket/推送等下游渠道消费）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-10
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wallet.notify")
public class WalletNotifyProperties {

    /**
     * 充值到账通知主题：入账事务提交后由 DepositServiceImpl 发送，
     * 消费端为 DepositNotificationListener（本服务内占位，生产环境可由独立通知服务消费）
     */
    private String depositTopic = "wallet-deposit-credited";
}
