package com.openexchange.spot.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * WS 订单推送载荷（ORDER_UPDATE）：金额为最小单位整数字符串，时间戳为 epoch 毫秒。
 */
@Data
@Builder
@Schema(name = "OrderUpdateData", description = "订单推送载荷")
public class OrderUpdateData {

    @Schema(description = "订单 ID", example = "o_20261106_00000001")
    private String orderId;

    @Schema(description = "订单状态：NEW / PARTIALLY_FILLED / FILLED / CANCELED / REJECTED", example = "PARTIALLY_FILLED")
    private String status;

    @Schema(description = "已成交数量（最小单位整数）", example = "600000")
    private String filledQuantity;

    @Schema(description = "平均成交价（最小单位整数，未成交为空串）", example = "60010000000")
    private String avgPrice;

    @Schema(description = "累计手续费（最小单位整数）", example = "360000")
    private String fee;

    @Schema(description = "事件时间（epoch 毫秒）", example = "1730899260000")
    private Long timestamp;
}
