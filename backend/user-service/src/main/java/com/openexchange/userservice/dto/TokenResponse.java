package com.openexchange.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 令牌响应
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Data
@Builder
@Schema(name = "TokenResponse", description = "令牌响应")
public class TokenResponse {

    @Schema(description = "访问令牌", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "刷新令牌", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "访问令牌有效期（毫秒）", example = "1800000")
    private Long expiresIn;
}
