package com.openexchange.wallet.chain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.util.List;

/**
 * <p>
 * 以太坊 Sepolia 测试网 JSON-RPC 客户端（基于 RestClient，无需额外依赖）。
 * 使用公共免费节点（PublicNode），覆盖节点连通探测、余额查询、原始交易广播，
 * 交易签名（需要密钥管理/web3j）不在本类范围内，见提现流程 TODO。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
@Slf4j
@Component
public class EthRpcClient {

    /**
     * JSON-RPC 2.0 协议版本号
     */
    private static final String JSONRPC_VERSION = "2.0";

    /**
     * 查询最新已确认余额/区块的区块参数
     */
    private static final String BLOCK_TAG_LATEST = "latest";

    private final RestClient restClient;
    private final ChainProperties chainProperties;

    public EthRpcClient(ChainProperties chainProperties) {
        this.chainProperties = chainProperties;
        this.restClient = RestClient.builder()
                .baseUrl(chainProperties.getEth().getRpcUrl())
                .requestFactory(requestFactory(chainProperties.getTimeoutMs()))
                .build();
    }

    /**
     * 探测节点连通性并返回链 ID（hex 字符串，如 0xaa36a7 = Sepolia）
     */
    public String chainId() {
        return call("eth_chainId", List.of());
    }

    /**
     * 查询最新区块高度（hex 字符串）
     */
    public String blockNumber() {
        return call("eth_blockNumber", List.of());
    }

    /**
     * 查询地址原生币（Sepolia ETH）余额，单位 wei（hex 字符串）
     *
     * @param address 以太坊地址（0x 开头）
     */
    public String getBalance(String address) {
        return call("eth_getBalance", List.of(address, BLOCK_TAG_LATEST));
    }

    /**
     * 广播已签名的原始交易，返回交易哈希（0x 开头）。
     * 签名环节由离线签名服务完成后传入，本客户端只负责广播。
     */
    public String sendRawTransaction(String signedTxHex) {
        return call("eth_sendRawTransaction", List.of(signedTxHex));
    }

    /**
     * hex 数量字符串 → BigInteger（如 0x1bc16d674ec800 → 2 ETH 的 wei 数）
     */
    public BigInteger decodeHexQuantity(String hexQuantity) {
        String normalized = hexQuantity != null && hexQuantity.startsWith("0x") ? hexQuantity.substring(2) : hexQuantity;
        return new BigInteger(normalized, 16);
    }

    /**
     * 通用 JSON-RPC 2.0 调用：POST 单条请求，返回 result 字符串。
     * 节点返回 error 对象或网络异常时统一抛业务异常（系统错误），由上层决定降级策略。
     */
    private String call(String method, List<Object> params) {
        try {
            RpcResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RpcRequest(JSONRPC_VERSION, method, params, 1))
                    .retrieve()
                    .body(RpcResponse.class);
            if (response == null || response.error() != null) {
                String message = response != null && response.error() != null
                        ? response.error().code() + ": " + response.error().message()
                        : "empty response";
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "sepolia rpc " + method + " failed: " + message);
            }
            return response.result();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[sepolia-rpc] {} 调用失败: {}", method, e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sepolia rpc " + method + " unreachable");
        }
    }

    /**
     * 公共节点超时控制：连接 5s / 读取按配置（默认 10s），避免拖垮调用线程
     */
    private SimpleClientHttpRequestFactory requestFactory(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout((int) timeoutMs);
        return factory;
    }

    /**
     * JSON-RPC 2.0 请求体：{"jsonrpc":"2.0","method":..,"params":[..],"id":1}
     */
    record RpcRequest(String jsonrpc, String method, List<Object> params, int id) {
    }

    /**
     * JSON-RPC 2.0 响应体：成功时 result 有值，失败时 error 有值，二者互斥
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RpcResponse(String jsonrpc, String result, RpcError error, int id) {
    }

    /**
     * JSON-RPC 2.0 错误对象：code + message
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RpcError(int code, String message) {
    }
}
