package com.openexchange.spot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 撮合队列生产者：把下单/撤单命令投递到 symbol 对应的 Kafka 主题。
 * 主题名 = 前缀 + 展示符号小写（BTC/USDT → order-btc-usdt），
 * key = 展示符号，保证同一交易对的命令落在同一分区、时序一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingQueueProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SpotTradeProperties properties;

    public void publishOrderCommand(String displaySymbol, OrderCommand command) {
        String topic = properties.getTrade().getOrderTopicPrefix()
                + displaySymbol.toLowerCase().replace("/", "-");
        send(topic, displaySymbol, command, "order-command");
    }

    private void send(String topic, String key, Object payload, String tag) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    // 投递失败不阻塞已提交的账本/订单事务，由对账补偿或客户端重试兜底
                    log.error("[{}] Kafka 发送失败 topic={} key={}: {}", tag, topic, key, ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("[{}] 消息序列化失败 topic={} key={}: {}", tag, topic, key, e.getMessage());
        }
    }
}
