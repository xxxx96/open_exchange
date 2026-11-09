package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 充值到账回调请求体：由链上监听服务在发现充值交易后调用（内部接口）。
 * 幂等键 = chain + txHash + logIndex，重复回调直接返回成功。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Schema(name = "DepositCallbackRequest", description = "充值到账回调请求（内部接口，链上监听服务调用）")
public class DepositCallbackRequest {

    @Schema(description = "用户 ID", example = "123")
    private Long userId;

    @Schema(description = "链标识，如 ETH、TRX", example = "ETH")
    private String chain;

    @Schema(description = "充值交易哈希", example = "0xabc123...")
    private String txHash;

    @Schema(description = "交易日志索引：同一笔交易可能包含多笔转账事件，与 chain+txHash 共同构成幂等键", example = "2")
    private Integer logIndex;

    @Schema(description = "资产符号，如 USDT", example = "USDT")
    private String asset;

    @Schema(description = "充值金额（最小单位整数的字符串表示，如 1000000 表示 1 USDT）", example = "1000000")
    private String amount;

    @Schema(description = "付款方链上地址", example = "0xSender...")
    private String fromAddress;

    @Schema(description = "收款方链上地址（应与该用户的充值地址一致）", example = "0xReceiver...")
    private String toAddress;
}
