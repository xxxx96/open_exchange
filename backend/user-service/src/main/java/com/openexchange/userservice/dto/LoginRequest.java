package com.openexchange.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录请求
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Data
@Schema(name = "LoginRequest", description = "登录请求")
public class LoginRequest {

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "密码", example = "P@ssw0rd123")
    private String password;
}
