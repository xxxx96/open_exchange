package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * 充值地址分配响应：GET /api/v1/deposit/address 的返回体。
 * 同用户同链同资产的地址固定不变；部分链（如 EOS/XRP）需要 memo，其余为空字符串。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Builder
@Schema(name = "DepositAddressResponse", description = "用户专属充值地址（同用户同链同资产固定不变）")
public class DepositAddressResponse {

    @Schema(description = "充值地址", example = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B")
    private String address;

    @Schema(description = "地址备注，部分链（如 EOS/XRP）转账必填，其余为空字符串", example = "")
    private String memo;

    @Schema(description = "链标识", example = "ETH")
    private String chain;

    @Schema(description = "资产符号", example = "USDT")
    private String asset;
}
