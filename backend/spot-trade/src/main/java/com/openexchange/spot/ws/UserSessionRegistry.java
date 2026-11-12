package com.openexchange.spot.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openexchange.spot.config.SpotTradeProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户 WS 会话注册表：维护 userId → 连接集合的反向索引，负责连接数上限、
 * 订阅频道、心跳活跃时间与按用户单调递增的推送序号。
 *
 * <p>多节点部署时每个节点各持一份本地映射：用户事件主题的每条消息都会被所有节点消费到
 * （每节点独立消费者组），再由本注册表按 userId 路由到本节点持有的连接上。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionById = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> channelsBySession = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActiveBySession = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> sequenceByUser = new ConcurrentHashMap<>();

    private final SpotTradeProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 注册连接。超过单用户并发上限时返回 false（调用方需关闭连接）。
     */
    public boolean register(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        synchronized (sessions) {
            if (sessions.size() >= properties.getWs().getMaxConnectionsPerUser()) {
                return false;
            }
            sessions.add(session);
        }
        userBySession.put(session.getId(), userId);
        sessionById.put(session.getId(), session);
        channelsBySession.put(session.getId(), ConcurrentHashMap.newKeySet());
        touch(session.getId());
        return true;
    }

    public void unregister(WebSocketSession session) {
        Long userId = userBySession.remove(session.getId());
        sessionById.remove(session.getId());
        channelsBySession.remove(session.getId());
        lastActiveBySession.remove(session.getId());
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId, sessions);
            }
        }
    }

    public void touch(String sessionId) {
        lastActiveBySession.put(sessionId, System.currentTimeMillis());
    }

    /** 记录订阅频道；空集合表示未订阅（推送时不过滤，默认全量下发） */
    public void subscribe(String sessionId, Set<String> channels) {
        Set<String> current = channelsBySession.get(sessionId);
        if (current != null) {
            current.clear();
            current.addAll(channels);
        }
    }

    public int connectionCount(Long userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    /** 用户在线（至少一个可用连接） */
    public boolean isOnline(Long userId) {
        return connectionCount(userId) > 0;
    }

    /**
     * 推送用户事件：按 userId 取本节点连接，补单调递增 sequence 后下发。
     *
     * @return 成功下发的连接数；0 表示用户不在线（客户端需靠 REST 补齐）
     */
    public int push(Long userId, String channel, String event, JsonNode data) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        long sequence = nextSequence(userId);
        String payload = buildPayload(channel, event, data, sequence);
        int sent = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen() || !accepts(session.getId(), channel)) {
                continue;
            }
            try {
                // 同一用户的事件由 Kafka key 保证有序，逐连接同步发送维持该顺序
                synchronized (session) {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(payload));
                }
                sent++;
            } catch (IOException e) {
                log.warn("[ws] 推送失败 userId={} session={}: {}", userId, session.getId(), e.getMessage());
            }
        }
        return sent;
    }

    private boolean accepts(String sessionId, String channel) {
        Set<String> channels = channelsBySession.get(sessionId);
        return channels == null || channels.isEmpty() || channels.contains(channel);
    }

    /** 心跳超时的会话（超过 N 秒没有任何客户端消息） */
    public List<WebSocketSession> findIdleSessions() {
        long timeoutMillis = properties.getWs().getHeartbeatTimeoutSeconds() * 1000L;
        long deadline = System.currentTimeMillis() - timeoutMillis;
        List<WebSocketSession> idle = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastActiveBySession.entrySet()) {
            if (entry.getValue() < deadline) {
                WebSocketSession session = sessionById.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    idle.add(session);
                }
            }
        }
        return idle;
    }

    private String buildPayload(String channel, String event, JsonNode data, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("channel", channel);
        payload.put("event", event);
        if (data != null) {
            payload.set("data", data);
        } else {
            payload.putObject("data");
        }
        payload.put("sequence", sequence);
        return payload.toString();
    }

    /** 同一用户的推送序号单调递增：客户端发现断层可用 REST 查单/查成交/查余额补齐 */
    private long nextSequence(Long userId) {
        return sequenceByUser.computeIfAbsent(userId, k -> new AtomicLong()).incrementAndGet();
    }

    @PreDestroy
    public void shutdown() {
        sessionsByUser.values().forEach(sessions -> sessions.forEach(session -> {
            try {
                session.close();
            } catch (IOException ignored) {
                // 停机时尽力关闭，失败不影响关机流程
            }
        }));
        sessionsByUser.clear();
        sessionById.clear();
        userBySession.clear();
        channelsBySession.clear();
        lastActiveBySession.clear();
    }
}
