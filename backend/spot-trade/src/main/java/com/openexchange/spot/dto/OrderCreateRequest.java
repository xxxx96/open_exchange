package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 下单请求：所有金额均为「最小单位整数字符串」。
 * price / quoteQuantity 按计价币 decimals 换算，quantity 按基础币 decimals 换算。
 */
@Data
@Schema(name = "OrderCreateRequest", description = "下单请求")
public class OrderCreateRequest {

    @Schema(description = "交易对，如 BTC/USDT", example = "BTC/USDT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;

    @Schema(description = "方向：BUY / SELL", example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String side;

    @Schema(description = "类型：LIMIT / MARKET", example = "LIMIT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "委托价格（最小单位整数，限价单必填，市价单不传）", example = "60000000000")
    private String price;

    @Schema(description = "委托数量（最小单位整数；市价卖单必填，市价买单可改用 quoteQuantity）", example = "1000000")
    private String quantity;

    @Schema(description = "市价买单专用：花多少计价币（最小单位整数）", example = "600000000")
    private String quoteQuantity;

    @Schema(description = "有效期：GTC / IOC / FOK，默认 GTC", example = "GTC")
    private String timeInForce;

    @Schema(description = "客户端生成的 UUID，用于幂等", example = "7f3a2b1c-9d4e-4a11-8e22-abc123456789",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientOrderId;
}
