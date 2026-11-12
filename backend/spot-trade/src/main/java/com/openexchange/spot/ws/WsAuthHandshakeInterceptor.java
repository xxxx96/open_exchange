package com.openexchange.spot.ws;

import com.openexchange.spot.security.SpotTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WS 握手鉴权：浏览器无法为 WebSocket 握手设置 Authorization 头，token 经 query 参数传递
 * （wss://api.example.com/ws/user?token=xxx），用与 gateway / user-service 同源的
 * access token 公钥验签。
 *
 * <p>验签失败时不在拦截器内拒绝握手——Spring 的握手拒绝只能返回 HTTP 403，
 * 无法携带业务约定的 close code。因此统一放行，由 {@link UserWebSocketHandler}
 * 在连接建立后立即以 4001 关闭。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手属性：登录用户 ID（验签失败时不存在） */
    public static final String ATTR_USER_ID = "WS_USER_ID";

    private final SpotTokenVerifier tokenVerifier;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            Long userId = tokenVerifier.verifyAccessToken(token);
            if (userId != null) {
                attributes.put(ATTR_USER_ID, userId);
            } else {
                log.debug("[ws] 握手 token 无效，连接将被关闭 code=4001");
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
