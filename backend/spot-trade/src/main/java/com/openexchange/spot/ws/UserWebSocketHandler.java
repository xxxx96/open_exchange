package com.openexchange.spot.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户 WS 处理器：握手鉴权落地、连接数上限、心跳 ping/pong 与频道订阅。
 *
 * <p>协议（文本 JSON）：</p>
 * <ul>
 *   <li>客户端 → 服务端：{@code {"action":"ping"}}，服务端回 {@code {"action":"pong"}}；
 *       客户端应每 20s 发送一次，服务端 30s 未收到任何消息即断开（4003）</li>
 *   <li>客户端 → 服务端：{@code {"action":"subscribe","channels":["order","balance","trade"]}}；
 *       不订阅则默认推送全部频道</li>
 *   <li>服务端 → 客户端：{@code {"channel":"order","event":"ORDER_UPDATE","data":{...},"sequence":N}}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWebSocketHandler extends TextWebSocketHandler {

    /** token 无效/缺失 */
    public static final int CLOSE_INVALID_TOKEN = 4001;
    /** 超过单用户并发连接上限 */
    public static final int CLOSE_TOO_MANY_CONNECTIONS = 4002;
    /** 心跳超时 */
    public static final int CLOSE_HEARTBEAT_TIMEOUT = 4003;

    private static final String ACTION_PING = "ping";
    private static final String ACTION_SUBSCRIBE = "subscribe";
    private static final String PONG_PAYLOAD = "{\"action\":\"pong\"}";

    private final UserSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object userId = session.getAttributes().get(WsAuthHandshakeInterceptor.ATTR_USER_ID);
        if (!(userId instanceof Long uid)) {
            session.close(new CloseStatus(CLOSE_INVALID_TOKEN, "invalid or missing token"));
            return;
        }
        if (!sessionRegistry.register(uid, session)) {
            session.close(new CloseStatus(CLOSE_TOO_MANY_CONNECTIONS, "too many concurrent connections"));
            return;
        }
        log.info("[ws] 连接建立 userId={} session={} 当前连接数={}", uid, session.getId(),
                sessionRegistry.connectionCount(uid));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessionRegistry.touch(session.getId());
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.debug("[ws] 非 JSON 消息已忽略 session={}", session.getId());
            return;
        }
        String action = root.path("action").asText("");
        switch (action) {
            case ACTION_PING -> sendSafe(session, PONG_PAYLOAD);
            case ACTION_SUBSCRIBE -> {
                Set<String> channels = ConcurrentHashMap.newKeySet();
                JsonNode node = root.get("channels");
                if (node != null && node.isArray()) {
                    node.forEach(item -> {
                        if (item.isTextual()) {
                            channels.add(item.asText());
                        }
                    });
                }
                sessionRegistry.subscribe(session.getId(), channels);
                log.debug("[ws] 订阅频道 session={} channels={}", session.getId(), channels);
            }
            default -> log.debug("[ws] 未知指令已忽略 session={} action={}", session.getId(), action);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(WsAuthHandshakeInterceptor.ATTR_USER_ID);
        sessionRegistry.unregister(session);
        log.info("[ws] 连接关闭 userId={} session={} code={}", userId, session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[ws] 传输异常 session={}: {}", session.getId(), exception.getMessage());
        sessionRegistry.unregister(session);
    }

    private void sendSafe(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (Exception e) {
            log.warn("[ws] 发送失败 session={}: {}", session.getId(), e.getMessage());
        }
    }
}
