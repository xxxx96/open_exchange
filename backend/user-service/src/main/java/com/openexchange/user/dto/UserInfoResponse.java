package com.openexchange.user.dto;

import com.openexchange.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 用户信息响应
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Data
@Builder
@Schema(name = "UserInfoResponse", description = "用户信息")
public class UserInfoResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户唯一标识", example = "EX5f4d8a9c1b2e3f4a")
    private String uid;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "KYC 等级", example = "LEVEL_0")
    private String kycLevel;

    @Schema(description = "账号状态", example = "ACTIVE")
    private String status;

    @Schema(description = "邀请码", example = "A1B2C3")
    private String refCode;

    @Schema(description = "创建时间")
    private Instant createdAt;

    /**
     * 从实体构建响应对象（不暴露 passwordHash 等敏感字段）
     */
    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .uid(user.getUid())
                .email(user.getEmail())
                .phone(user.getPhone())
                .kycLevel(user.getKycLevel() != null ? user.getKycLevel().name() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .refCode(user.getRefCode())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
