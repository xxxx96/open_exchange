package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * 提现状态查询响应体：仅暴露 pending/success/failed 三种对外状态，
 * 不暴露内部处理细节（如是否正在签名、gas 消耗等）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Builder
@Schema(name = "WithdrawalStatusResponse", description = "提现状态查询响应（对外仅暴露 pending/success/failed 三种状态）")
public class WithdrawalStatusResponse {

    @Schema(description = "提现单对外 ID", example = "wd_20261106_001")
    private String withdrawalId;

    @Schema(description = "对外状态：pending-处理中；success-已完成；failed-失败（含风控拒绝、链上失败等）", example = "pending")
    private String status;

    @Schema(description = "链上交易哈希，广播成功后才有值", example = "0x...")
    private String txHash;

    @Schema(description = "提现申请创建时间（ISO-8601 UTC）", example = "2026-11-06T10:00:00Z")
    private String createdAt;
}
