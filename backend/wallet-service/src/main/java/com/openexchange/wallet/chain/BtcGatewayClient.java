package com.openexchange.wallet.chain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * <p>
 * Bitcoin testnet4 第三方网关客户端（mempool.space REST API，基于 RestClient）。
 * 覆盖区块高度探测、地址余额（UTXO 聚合统计）、原始交易广播；
 * 该网关为公共服务，有速率限制，生产环境建议自建全节点（bitcoind）或付费网关。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
@Slf4j
@Component
public class BtcGatewayClient {

    private final RestClient restClient;
    private final ChainProperties chainProperties;

    public BtcGatewayClient(ChainProperties chainProperties) {
        this.chainProperties = chainProperties;
        this.restClient = RestClient.builder()
                .baseUrl(chainProperties.getBtc().getGatewayBaseUrl())
                .requestFactory(requestFactory(chainProperties.getTimeoutMs()))
                .build();
    }

    /**
     * 探测网关连通性并返回 testnet4 最新区块高度
     */
    public long tipHeight() {
        String body = restClient.get()
                .uri("/api/blocks/tip/height")
                .retrieve()
                .body(String.class);
        try {
            return Long.parseLong(body != null ? body.trim() : "");
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "testnet4 gateway returned invalid tip height");
        }
    }

    /**
     * 查询测试网地址的余额统计（satoshis）
     *
     * @param address testnet4 地址（tb1q... / m... / n... 等）
     * @return 链上已确认与内存池中的收付统计
     */
    public AddressInfo getAddressInfo(String address) {
        try {
            AddressInfo info = restClient.get()
                    .uri("/api/address/{address}", address)
                    .retrieve()
                    .body(AddressInfo.class);
            if (info == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "testnet4 gateway returned empty address info");
            }
            return info;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[testnet4-gateway] 地址查询失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "testnet4 gateway unreachable");
        }
    }

    /**
     * 广播已签名的原始交易（hex 文本体），成功返回交易 ID（txid）。
     * mempool.space 失败时返回错误文本（如 min relay fee not met），此处透传给上层。
     */
    public String broadcastTx(String rawTxHex) {
        try {
            String txid = restClient.post()
                    .uri("/api/tx")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(rawTxHex)
                    .retrieve()
                    .body(String.class);
            if (txid == null || txid.isBlank()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "testnet4 broadcast returned empty txid");
            }
            return txid.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[testnet4-gateway] 交易广播失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "testnet4 broadcast failed: " + e.getMessage());
        }
    }

    /**
     * 公共网关超时控制：连接 5s / 读取按配置，避免拖垮调用线程
     */
    private SimpleClientHttpRequestFactory requestFactory(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout((int) timeoutMs);
        return factory;
    }

    /**
     * 地址余额统计（satoshis）：实际余额 = funded - spent
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressStats(long fundedTxoSum, long spentTxoSum) {

        /**
         * 净余额（sat）
         */
        public long balanceSat() {
            return fundedTxoSum - spentTxoSum;
        }
    }

    /**
     * mempool.space 地址查询响应：chain_stats 为已确认统计，mempool_stats 为待确认统计
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressInfo(AddressStats chainStats, AddressStats mempoolStats) {
    }
}
