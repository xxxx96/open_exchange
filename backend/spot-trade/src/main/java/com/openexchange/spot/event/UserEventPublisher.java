package com.openexchange.spot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户事件生产者：订单/余额/成交变动投递到用户事件主题，供 WS 网关推送。
 * key = userId，保证同一用户的事件在分区内有序（sequence 才能连续）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SpotTradeProperties properties;

    public void publish(UserEvent event) {
        String topic = properties.getTrade().getUserEventTopic();
        String key = String.valueOf(event.getUserId());
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    // 推送失败不阻塞清算；离线用户可通过 REST 查单/查成交补齐
                    log.error("[user-event] Kafka 发送失败 userId={} channel={}: {}",
                            event.getUserId(), event.getChannel(), ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("[user-event] 消息序列化失败 userId={}: {}", event.getUserId(), e.getMessage());
        }
    }
}
