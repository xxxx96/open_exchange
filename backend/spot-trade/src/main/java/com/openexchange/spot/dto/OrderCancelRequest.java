package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 撤单请求：orderId 与 clientOrderId 二选一（均须属于当前用户）。
 */
@Data
@Schema(name = "OrderCancelRequest", description = "撤单请求")
public class OrderCancelRequest {

    @Schema(description = "交易对，如 BTC/USDT", example = "BTC/USDT")
    private String symbol;

    @Schema(description = "订单 ID", example = "o_20261106_00000001")
    private String orderId;

    @Schema(description = "客户端订单 ID（与 orderId 二选一）", example = "7f3a2b1c-9d4e-4a11-8e22-abc123456789")
    private String clientOrderId;
}
