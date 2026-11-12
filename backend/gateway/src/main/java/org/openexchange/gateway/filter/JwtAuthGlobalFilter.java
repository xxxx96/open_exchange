package org.openexchange.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final PublicKey accessTokenPublicKey;

    // 从配置读取 access token 密钥，与 user-service 的 JwtTokenProvider 使用同一个配置项
    public JwtAuthGlobalFilter(@Value("${jwt.access-token-public-secret}")String accessTokenPublicKeyB64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 使用 Java 标准 KeyFactory 构造 RSA 密钥
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] accessPublicBytes  = Decoders.BASE64.decode(accessTokenPublicKeyB64);
        this.accessTokenPublicKey  = keyFactory.generatePublic(new X509EncodedKeySpec(accessPublicBytes));
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单路径直接放行
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 2. 从请求头获取 token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);

        try {
            // 3. 解析 access token（与 JwtTokenProvider 使用相同密钥）
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(accessTokenPublicKey)
                    .build()
                    .parseSignedClaims(token);

            Claims claims = jws.getPayload();

            // 4. 提取 userId（subject 字段，由 JwtTokenProvider 的 generateAccessToken 写入）
            String userId = claims.getSubject();
            if (userId == null) {
                return unauthorized(exchange, "JWT missing subject (userId)");
            }

            // 5. 将 userId 写入请求头，转发给下游服务
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", claims.get("email", String.class))  // 可选，按需传递
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Access token expired: {}", e.getMessage());
            return unauthorized(exchange, "Token expired");
        } catch (JwtException e) {
            log.warn("Access token validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/account/register")
                || path.equals("/api/v1/account/login")
                || path.equals("/api/v1/account/refresh")
                || path.startsWith("/api/public/")
                || path.startsWith("/ws/")             // WS 握手用 query token 自行鉴权（如 /ws/user）
                || path.equals("/favicon.ico")
                || path.startsWith("/actuator/health");
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，在其他过滤器之前执行
    }
}