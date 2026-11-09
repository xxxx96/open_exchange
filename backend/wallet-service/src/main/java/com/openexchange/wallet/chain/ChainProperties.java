package com.openexchange.wallet.chain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>
 * 链上节点连接配置：Sepolia 测试网公共 RPC + Bitcoin testnet4 第三方网关。
 * 端点均为公共免费节点，生产环境应替换为付费节点（Alchemy/Infura/自建节点）并配合密钥。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "chain")
public class ChainProperties {

    /**
     * 请求超时（毫秒）：公共节点偶发慢响应，超时后由调用方降级处理
     */
    private long timeoutMs = 10000;

    private Eth eth = new Eth();

    private Btc btc = new Btc();

    @Getter
    @Setter
    public static class Eth {

        /**
         * Sepolia 测试网公共 JSON-RPC 端点（PublicNode，无需 API Key）。
         * 实测 chainId 返回 0xaa36a7（11155111）。
         */
        private String rpcUrl = "https://ethereum-sepolia-rpc.publicnode.com";

        /**
         * 期望链 ID：探测时校验，防止连错网络（主网 1 / Sepolia 11155111）
         */
        private long expectedChainId = 11155111;
    }

    @Getter
    @Setter
    public static class Btc {

        /**
         * Bitcoin testnet4 第三方网关（mempool.space）REST API 基地址，
         * 提供区块/地址/交易查询与 raw tx 广播，无需 API Key（有速率限制）。
         */
        private String gatewayBaseUrl = "https://mempool.space/testnet4";
    }
}
