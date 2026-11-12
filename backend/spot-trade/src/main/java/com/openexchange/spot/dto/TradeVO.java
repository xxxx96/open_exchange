package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 成交视图（用户视角）：side/isMaker/fee 均按当前查询用户推导，金额为最小单位整数字符串。
 */
@Data
@Builder
@Schema(name = "TradeVO", description = "成交记录")
public class TradeVO {

    @Schema(description = "成交号", example = "t_20261106_000099")
    private String tradeId;

    @Schema(description = "订单 ID", example = "o_20261106_00000001")
    private String orderId;

    @Schema(description = "交易对", example = "BTC/USDT")
    private String symbol;

    @Schema(description = "方向（当前用户视角）：BUY / SELL", example = "BUY")
    private String side;

    @Schema(description = "成交价格（最小单位整数）", example = "60010000000")
    private String price;

    @Schema(description = "成交数量（最小单位整数）", example = "600000")
    private String quantity;

    @Schema(description = "成交金额 = 价格 × 数量（最小单位整数）", example = "36006000000")
    private String quoteQuantity;

    @Schema(description = "手续费（最小单位整数）", example = "360000")
    private String fee;

    @Schema(description = "手续费币种（买单为基础币、卖单为计价币）", example = "BTC")
    private String feeAsset;

    @Schema(description = "当前用户是否为挂单方", example = "false")
    private Boolean isMaker;

    @Schema(description = "成交时间（epoch 毫秒）", example = "1730899260000")
    private Long timestamp;
}
