package com.openexchange.spot.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * WS 成交推送载荷（TRADE）：金额为最小单位整数字符串。
 */
@Data
@Builder
@Schema(name = "TradeUpdateData", description = "成交推送载荷")
public class TradeUpdateData {

    @Schema(description = "成交号", example = "t_20261106_000099")
    private String tradeId;

    @Schema(description = "订单 ID", example = "o_20261106_00000001")
    private String orderId;

    @Schema(description = "交易对", example = "BTC/USDT")
    private String symbol;

    @Schema(description = "成交价格（最小单位整数）", example = "60010000000")
    private String price;

    @Schema(description = "成交数量（最小单位整数）", example = "600000")
    private String quantity;

    @Schema(description = "当前用户是否为挂单方（决定手续费率）", example = "false")
    private Boolean isMaker;

    @Schema(description = "手续费（最小单位整数）", example = "360000")
    private String fee;

    @Schema(description = "成交时间（epoch 毫秒）", example = "1730899260000")
    private Long timestamp;
}
