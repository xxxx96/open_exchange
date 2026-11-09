package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 发起提现请求体：POST /api/v1/withdrawal/create。
 * clientId 由客户端生成（UUID），服务端以其作为幂等键去重，防止重复提交。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Schema(name = "WithdrawalCreateRequest", description = "发起提现请求，clientId 为客户端生成的幂等键")
public class WithdrawalCreateRequest {

    @Schema(description = "用户 ID", example = "123")
    private Long userId;

    @Schema(description = "链标识，如 ETH、TRX", example = "ETH")
    private String chain;

    @Schema(description = "资产符号，如 USDT", example = "USDT")
    private String asset;

    @Schema(description = "提现目标外部地址", example = "0xTarget...")
    private String toAddress;

    @Schema(description = "提现金额（最小单位整数的字符串表示，如 500000 表示 0.5 USDT）", example = "500000")
    private String amount;

    @Schema(description = "客户端幂等键（UUID），服务端据此去重，防止重复提交", example = "uuid-xxxx-yyyy")
    private String clientId;
}
