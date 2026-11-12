package com.openexchange.spot.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * WS 余额推送载荷（BALANCE_UPDATE）：金额为最小单位整数字符串，
 * change 为本次变动量（可正可负，最小单位整数），reason 说明变动原因。
 */
@Data
@Builder
@Schema(name = "BalanceUpdateData", description = "余额推送载荷")
public class BalanceUpdateData {

    @Schema(description = "资产符号", example = "USDT")
    private String asset;

    @Schema(description = "变动后可用余额（最小单位整数）", example = "400000000")
    private String available;

    @Schema(description = "变动后冻结余额（最小单位整数）", example = "600000000")
    private String locked;

    @Schema(description = "本次变动量（最小单位整数，可正可负）", example = "-600000000")
    private String change;

    @Schema(description = "变动原因：ORDER_FREEZE / ORDER_UNFREEZE / TRADE_SETTLE / INTERNAL_TRANSFER", example = "ORDER_FREEZE")
    private String reason;
}
