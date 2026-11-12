package com.openexchange.spot.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户事件信封（清算/下单 → WS 网关）：
 * channel = order / balance / trade，event = ORDER_UPDATE / BALANCE_UPDATE / TRADE。
 * 以 userId 为 Kafka key 投递，保证同一用户的事件有序；
 * WS 推送时补上按用户单调递增的 sequence（客户端发现断层可调 REST 补齐）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String channel;

    private String event;

    private JsonNode data;
}
