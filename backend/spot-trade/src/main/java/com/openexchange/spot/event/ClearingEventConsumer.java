package com.openexchange.spot.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openexchange.spot.config.SpotTradeProperties;
import com.openexchange.spot.service.IClearingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 清算事件消费者：撮合引擎产出的成交 / 撤单完成事件。
 *
 * <p>事件类型按结构判别（无需引擎额外约定字段）：含 makerOrderId + takerOrderId 的视为成交事件，
 * 含 orderId 的视为撤单事件。清算逻辑全部幂等（自然键去重 + 状态 CAS），
 * 可安全接收 at-least-once 投递。</p>
 */
@Slf4j
@Component
public class ClearingEventConsumer {

    private final String topic;
    private final ObjectMapper objectMapper;
    private final IClearingService clearingService;

    public ClearingEventConsumer(SpotTradeProperties properties, ObjectMapper objectMapper,
                                 IClearingService clearingService) {
        this.topic = properties.getTrade().getClearingTopic();
        this.objectMapper = objectMapper;
        this.clearingService = clearingService;
    }

    public String getTopic() {
        return topic;
    }

    @KafkaListener(topics = "#{@clearingEventConsumer.topic}", groupId = "spot-trade-clearing")
    public void onClearingEvent(ConsumerRecord<String, String> record) {
        JsonNode root;
        try {
            root = objectMapper.readTree(record.value());
        } catch (Exception e) {
            log.error("[clearing] 事件解析失败 topic={} offset={}: {}",
                    record.topic(), record.offset(), e.getMessage());
            return;
        }
        if (root.has("makerOrderId") && root.has("takerOrderId")) {
            clearingService.onTradeSettled(objectMapper.convertValue(root, TradeSettledEvent.class));
        } else if (root.has("orderId")) {
            clearingService.onOrderCanceled(objectMapper.convertValue(root, OrderCanceledEvent.class));
        } else {
            log.warn("[clearing] 未知事件类型，已丢弃: {}", record.value());
        }
    }
}
