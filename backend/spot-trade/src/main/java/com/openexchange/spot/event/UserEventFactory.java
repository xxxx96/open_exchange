package com.openexchange.spot.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户事件信封工厂：统一定义 channel / event 常量并序列化载荷为 JsonNode。
 * WS 网关消费 {@link UserEvent} 时只需透出 channel/event/data，并按用户补 sequence。
 */
@Component
@RequiredArgsConstructor
public class UserEventFactory {

    public static final String CHANNEL_ORDER = "order";
    public static final String CHANNEL_BALANCE = "balance";
    public static final String CHANNEL_TRADE = "trade";

    public static final String EVENT_ORDER_UPDATE = "ORDER_UPDATE";
    public static final String EVENT_BALANCE_UPDATE = "BALANCE_UPDATE";
    public static final String EVENT_TRADE = "TRADE";

    private final ObjectMapper objectMapper;

    public UserEvent orderUpdate(Long userId, OrderUpdateData data) {
        return build(userId, CHANNEL_ORDER, EVENT_ORDER_UPDATE, data);
    }

    public UserEvent balanceUpdate(Long userId, BalanceUpdateData data) {
        return build(userId, CHANNEL_BALANCE, EVENT_BALANCE_UPDATE, data);
    }

    public UserEvent trade(Long userId, TradeUpdateData data) {
        return build(userId, CHANNEL_TRADE, EVENT_TRADE, data);
    }

    private UserEvent build(Long userId, String channel, String event, Object data) {
        return UserEvent.builder()
                .userId(userId)
                .channel(channel)
                .event(event)
                .data(objectMapper.valueToTree(data))
                .build();
    }
}
