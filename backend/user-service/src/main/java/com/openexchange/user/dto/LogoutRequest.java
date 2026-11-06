package com.openexchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登出请求
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Data
@Schema(name = "LogoutRequest", description = "登出请求")
public class LogoutRequest {

    @Schema(description = "刷新令牌", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String refreshToken;
}
