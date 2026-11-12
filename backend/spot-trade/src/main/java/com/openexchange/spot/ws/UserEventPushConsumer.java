package com.openexchange.spot.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import com.openexchange.spot.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 用户事件推送消费者：清算/下单产生的订单、余额、成交变动投递到用户事件主题，
 * 本消费者按 userId 路由到本节点的 WS 连接并下发（{@code {channel, event, data, sequence}}）。
 *
 * <p>消费者组 = {@code spot.ws.consumer-group-prefix + UUID}，即每个 WS 节点独立成组：
 * 多节点部署下每个节点都能收到全量用户事件，再各自按本地 userId→连接 映射路由，
 * 等价于多节点广播，无需额外引入 Redis pub/sub。用户不在线时丢弃，客户端用 REST 补齐。</p>
 */
@Slf4j
@Component
public class UserEventPushConsumer {

    private final String groupId;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final UserSessionRegistry sessionRegistry;

    public UserEventPushConsumer(SpotTradeProperties properties, ObjectMapper objectMapper,
                                 UserSessionRegistry sessionRegistry) {
        this.topic = properties.getTrade().getUserEventTopic();
        this.groupId = properties.getWs().getConsumerGroupPrefix() + UUID.randomUUID();
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTopic() {
        return topic;
    }

    @KafkaListener(topics = "#{@userEventPushConsumer.topic}", groupId = "#{@userEventPushConsumer.groupId}")
    public void onUserEvent(ConsumerRecord<String, String> record) {
        UserEvent event;
        try {
            event = objectMapper.readValue(record.value(), UserEvent.class);
        } catch (Exception e) {
            log.error("[ws] 用户事件解析失败 topic={} offset={}: {}",
                    record.topic(), record.offset(), e.getMessage());
            return;
        }
        if (event.getUserId() == null || event.getChannel() == null) {
            log.warn("[ws] 用户事件缺少 userId/channel，已丢弃: {}", record.value());
            return;
        }
        JsonNode data = event.getData();
        int sent = sessionRegistry.push(event.getUserId(), event.getChannel(), event.getEvent(), data);
        if (sent == 0) {
            // 用户不在线：正常场景，客户端通过 REST 查单/查成交/查余额补齐
            log.debug("[ws] 用户不在线，事件未推送 userId={} channel={}", event.getUserId(), event.getChannel());
        }
    }
}
