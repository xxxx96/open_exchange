package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * 链上节点连通状态响应：GET /api/v1/chain/status 的返回体。
 * 用于运维/启动自检：探测 Sepolia 公共 RPC 与 Bitcoin testnet4 第三方网关是否可达。
 * 单链探测失败不影响另一链的结果，失败信息记录在 error 字段。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
@Data
@Builder
@Schema(name = "ChainStatusResponse", description = "Sepolia 公共 RPC 与 Bitcoin testnet4 第三方网关连通状态")
public class ChainStatusResponse {

    @Schema(description = "以太坊 Sepolia 测试网 RPC 状态")
    private EthStatus eth;

    @Schema(description = "Bitcoin testnet4 网关状态")
    private BtcStatus btc;

    @Data
    @Builder
    @Schema(name = "EthStatus", description = "Sepolia 公共 RPC 探测结果")
    public static class EthStatus {

        @Schema(description = "网络标识，固定 sepolia")
        private String network;

        @Schema(description = "链 ID（hex），Sepolia 应为 0xaa36a7", example = "0xaa36a7")
        private String chainId;

        @Schema(description = "最新区块高度（hex）", example = "0x...")
        private String blockNumber;

        @Schema(description = "RPC 端点地址")
        private String rpcUrl;

        @Schema(description = "探测耗时（毫秒）", example = "230")
        private long latencyMs;

        @Schema(description = "探测失败原因，成功时为 null")
        private String error;
    }

    @Data
    @Builder
    @Schema(name = "BtcStatus", description = "Bitcoin testnet4 网关探测结果")
    public static class BtcStatus {

        @Schema(description = "网络标识，固定 testnet4")
        private String network;

        @Schema(description = "最新区块高度", example = "160108")
        private Long tipHeight;

        @Schema(description = "网关 REST API 基地址")
        private String gatewayUrl;

        @Schema(description = "探测耗时（毫秒）", example = "180")
        private long latencyMs;

        @Schema(description = "探测失败原因，成功时为 null")
        private String error;
    }
}
