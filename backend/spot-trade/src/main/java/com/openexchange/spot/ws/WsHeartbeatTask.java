package com.openexchange.spot.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * WS 心跳巡检：客户端每 20s 发送 {@code {"action":"ping"}}，服务端 30s 未收到任何消息
 * 即断开（连续 3 次未心跳的兜底），释放无效连接与注册表条目。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsHeartbeatTask {

    private final UserSessionRegistry sessionRegistry;

    @Scheduled(fixedDelayString = "${spot.ws.heartbeat-check-interval-ms:10000}")
    public void evictIdleSessions() {
        List<WebSocketSession> idle = sessionRegistry.findIdleSessions();
        for (WebSocketSession session : idle) {
            try {
                session.close(new org.springframework.web.socket.CloseStatus(
                        UserWebSocketHandler.CLOSE_HEARTBEAT_TIMEOUT, "heartbeat timeout"));
            } catch (Exception e) {
                sessionRegistry.unregister(session);
            }
        }
        if (!idle.isEmpty()) {
            log.info("[ws] 心跳超时断开 {} 个连接", idle.size());
        }
    }
}
