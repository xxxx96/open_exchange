package com.openexchange.spot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 现货交易业务配置（application.yml 的 spot 前缀）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "spot")
public class SpotTradeProperties {

    private final Trade trade = new Trade();
    private final Risk risk = new Risk();
    private final Ws ws = new Ws();
    private final Balance balance = new Balance();
    private final Cache cache = new Cache();

    @Data
    public static class Trade {
        /** 撮合队列主题前缀：完整主题 = 前缀 + symbol 小写（BTC/USDT → order-btc-usdt） */
        private String orderTopicPrefix = "order-";
        /** 清算事件主题（撮合引擎产出：撤单完成、成交） */
        private String clearingTopic = "spot.clearing";
        /** 用户事件推送主题（WS 网关消费，key = userId 保证同用户有序） */
        private String userEventTopic = "spot.user.events";
    }

    @Data
    public static class Risk {
        /** 单笔下单名义价值上限（计价币人可读单位） */
        private BigDecimal maxOrderNotional = new BigDecimal("1000000");
        /** 单用户单日累计下单名义价值上限（计价币人可读单位） */
        private BigDecimal dailyOrderNotionalLimit = new BigDecimal("10000000");
        /** 限价单相对市场参考价的最大偏离比例（0.10 = 10%） */
        private BigDecimal maxPriceDeviation = new BigDecimal("0.10");
    }

    @Data
    public static class Ws {
        /** 单用户最大并发连接数 */
        private int maxConnectionsPerUser = 5;
        /** 心跳超时（秒）：超过该时长未收到任何客户端消息则断开 */
        private int heartbeatTimeoutSeconds = 30;
        /**
         * 用户事件消费者组前缀。每个 WS 节点拼上唯一后缀成为独立消费者组，
         * 使多节点部署下每个节点都消费全量用户事件、再按本地 userId→连接 路由，
         * 等价于多节点广播（无需额外引入 Redis pub/sub）。
         */
        private String consumerGroupPrefix = "spot-ws-";
    }

    @Data
    public static class Balance {
        /**
         * 余额查询 Redis 缓存 TTL（毫秒）。该接口调用频率高，读库前先查 Redis（key = spot:balance:{userId}）；
         * 任何余额变动（冻结/解冻/入账/核销）都会主动 DEL 对应缓存，最脏窗口 = TTL。0 表示关闭缓存。
         */
        private long cacheTtlMillis = 3000;
    }

    @Data
    public static class Cache {
        /**
         * 交易对 / 币种字典缓存 TTL（秒）。字典几乎不变但下单热路径每次都要读，
         * 缓存后可省掉每单 3~4 次数据库查询；后台改配置后最迟 TTL 生效。0 表示关闭缓存。
         */
        private long dictionaryTtlSeconds = 60;
    }
}
