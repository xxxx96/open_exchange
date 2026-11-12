package com.openexchange.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 交易账户余额：total = available + locked，金额为最小单位整数字符串。
 * 数据源为数据库账本（wallets 表 SPOT 行），不做任何链上查询。
 */
@Data
@Builder
@Schema(name = "BalanceVO", description = "交易账户余额")
public class BalanceVO {

    @Schema(description = "资产符号", example = "USDT")
    private String asset;

    @Schema(description = "总额 = 可用 + 冻结（最小单位整数）", example = "1000000000")
    private String total;

    @Schema(description = "可用余额（最小单位整数）", example = "400000000")
    private String available;

    @Schema(description = "冻结余额（最小单位整数）", example = "600000000")
    private String locked;
}
