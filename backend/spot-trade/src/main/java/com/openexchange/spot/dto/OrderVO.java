package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 订单视图：金额字段均为最小单位整数字符串，时间戳为 epoch 毫秒。
 */
@Data
@Builder
@Schema(name = "OrderVO", description = "订单信息")
public class OrderVO {

    @Schema(description = "订单 ID", example = "o_20261106_00000001")
    private String orderId;

    @Schema(description = "客户端订单 ID", example = "7f3a2b1c-9d4e-4a11-8e22-abc123456789")
    private String clientOrderId;

    @Schema(description = "交易对", example = "BTC/USDT")
    private String symbol;

    @Schema(description = "方向：BUY / SELL", example = "BUY")
    private String side;

    @Schema(description = "类型：LIMIT / MARKET", example = "LIMIT")
    private String type;

    @Schema(description = "状态：NEW / PARTIALLY_FILLED / FILLED / CANCELED / REJECTED", example = "NEW")
    private String status;

    @Schema(description = "委托价格（最小单位整数，市价单为空串）", example = "60000000000")
    private String price;

    @Schema(description = "委托数量（最小单位整数）", example = "1000000")
    private String quantity;

    @Schema(description = "已成交数量（最小单位整数）", example = "0")
    private String filledQuantity;

    @Schema(description = "平均成交价（最小单位整数，未成交为空串）", example = "")
    private String avgPrice;

    @Schema(description = "累计手续费（最小单位整数）", example = "0")
    private String fee;

    @Schema(description = "手续费币种（买单为基础币、卖单为计价币）", example = "BTC")
    private String feeAsset;

    @Schema(description = "创建时间（epoch 毫秒）", example = "1730899200000")
    private Long createdAt;

    @Schema(description = "更新时间（epoch 毫秒）", example = "1730899260000")
    private Long updatedAt;
}
