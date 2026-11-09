package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * 用户余额查询响应体：金额均为最小单位整数的字符串表示（账本权威，不做链上查询），
 * 前端展示时自行除以资产 decimals（如 USDT 为 6，则除以 1e6）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Builder
@Schema(name = "BalanceResponse", description = "用户资产余额（最小单位整数字符串，前端自行按 decimals 换算展示）")
public class BalanceResponse {

    @Schema(description = "用户 ID", example = "123")
    private Long userId;

    @Schema(description = "资产符号", example = "USDT")
    private String asset;

    @Schema(description = "可用余额（最小单位）", example = "999500000")
    private String available;

    @Schema(description = "冻结余额（最小单位）", example = "500000")
    private String frozen;

    @Schema(description = "总余额 = available + frozen（最小单位）", example = "1000000000")
    private String total;
}
