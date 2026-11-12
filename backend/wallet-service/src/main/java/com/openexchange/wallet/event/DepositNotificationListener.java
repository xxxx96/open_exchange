package com.openexchange.wallet.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 充值到账通知消费者：异步消费 Kafka 上的充值入账消息（事务提交成功后由
 * DepositServiceImpl 发布），通过 WebSocket/推送等渠道告知用户资产变化。
 * 当前以日志占位，接入具体推送通道后在此分发。
 * </p>
 * <p>
 * 投递语义为 at-least-once：消息可能重复，消费端通知操作应保持幂等
 * （重复推送同一笔充值通知对用户无害）；消息按 userId 作为 key 分区，
 * 同一用户的通知在分区内有序。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Slf4j
@Component
public class DepositNotificationListener {

    /**
     * 充值到账消息消费：独立消费者组（wallet-notification），
     * 与生产配置解耦；处理失败可抛异常触发重试（默认错误处理器），重复消费由幂等语义兜底。
     */
    @KafkaListener(
            topics = "${wallet.notify.deposit-topic:wallet-deposit-credited}",
            groupId = "wallet-notification")
    public void onDepositCredited(String message) {
        // TODO: 接入 WebSocket/推送通道后在此解析消息体并下发用户通知
        log.info("[deposit-notify] received deposit credited message: {}", message);
    }
}
