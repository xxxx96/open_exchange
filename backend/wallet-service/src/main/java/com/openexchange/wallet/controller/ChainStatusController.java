package com.openexchange.wallet.controller;

import com.openexchange.wallet.chain.BtcGatewayClient;
import com.openexchange.wallet.chain.ChainProperties;
import com.openexchange.wallet.chain.EthRpcClient;
import com.openexchange.wallet.dto.ChainStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 链上连接状态接口：探测 Sepolia 公共 RPC 与 Bitcoin testnet4 第三方网关的连通性。
 * 用途：服务启动自检、运维巡检、监控拨测。单链失败不影响另一链的结果。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chain")
@RequiredArgsConstructor
@Tag(name = "链上连接", description = "Sepolia 公共 RPC 与 testnet4 第三方网关连通状态")
public class ChainStatusController {

    private final EthRpcClient ethRpcClient;
    private final BtcGatewayClient btcGatewayClient;
    private final ChainProperties chainProperties;

    /**
     * 探测两条测试链的连通状态：
     * - Sepolia：eth_chainId + eth_blockNumber（校验链 ID 防连错网络）
     * - testnet4：mempool.space /api/blocks/tip/height
     */
    @Operation(summary = "链上连接状态",
            description = "探测 Sepolia 公共 RPC 与 Bitcoin testnet4 第三方网关连通性；单链失败不影响另一链结果")
    @GetMapping("/status")
    public ApiResponse<ChainStatusResponse> status() {
        return ApiResponse.success(ChainStatusResponse.builder()
                .eth(probeEth())
                .btc(probeBtc())
                .build());
    }

    /**
     * 探测 Sepolia：chainId 应等于配置的期望值（11155111），并附带最新区块高度
     */
    private ChainStatusResponse.EthStatus probeEth() {
        long start = System.currentTimeMillis();
        try {
            String chainId = ethRpcClient.chainId();
            String blockNumber = ethRpcClient.blockNumber();
            long expected = chainProperties.getEth().getExpectedChainId();
            if (Long.decode(chainId) != expected) {
                // 连错网络（如连到主网/其他测试网）属于配置事故，必须显式报错
                return ChainStatusResponse.EthStatus.builder()
                        .network("sepolia").rpcUrl(chainProperties.getEth().getRpcUrl())
                        .latencyMs(System.currentTimeMillis() - start)
                        .error("chainId mismatch: expected " + expected + " but got " + chainId)
                        .build();
            }
            return ChainStatusResponse.EthStatus.builder()
                    .network("sepolia")
                    .chainId(chainId)
                    .blockNumber(blockNumber)
                    .rpcUrl(chainProperties.getEth().getRpcUrl())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn("[chain-probe] sepolia 探测失败: {}", e.getMessage());
            return ChainStatusResponse.EthStatus.builder()
                    .network("sepolia").rpcUrl(chainProperties.getEth().getRpcUrl())
                    .latencyMs(System.currentTimeMillis() - start)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * 探测 testnet4：取最新区块高度
     */
    private ChainStatusResponse.BtcStatus probeBtc() {
        long start = System.currentTimeMillis();
        try {
            long tipHeight = btcGatewayClient.tipHeight();
            return ChainStatusResponse.BtcStatus.builder()
                    .network("testnet4")
                    .tipHeight(tipHeight)
                    .gatewayUrl(chainProperties.getBtc().getGatewayBaseUrl())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn("[chain-probe] testnet4 探测失败: {}", e.getMessage());
            return ChainStatusResponse.BtcStatus.builder()
                    .network("testnet4").gatewayUrl(chainProperties.getBtc().getGatewayBaseUrl())
                    .latencyMs(System.currentTimeMillis() - start)
                    .error(e.getMessage())
                    .build();
        }
    }
}
