package com.openexchange.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 注册请求
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Data
@Schema(name = "RegisterRequest", description = "注册请求")
public class RegisterRequest {

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "密码", example = "P@ssw0rd123")
    private String password;

    @Schema(description = "手机号（可选）", example = "13800138000")
    private String phone;

    @Schema(description = "邀请码（可选）", example = "A1B2C3")
    private String refCode;
}
