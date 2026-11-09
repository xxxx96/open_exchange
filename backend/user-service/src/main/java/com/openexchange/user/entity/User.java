package com.openexchange.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

/**
 * <p>
 * 
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Getter
@Setter
@ToString
@TableName("users")
@Schema(name = "User", description = "")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("google_auth_enabled")
    private Boolean googleAuthEnabled;

    @TableField("referred_by")
    private Long referredBy;

    @TableField("ref_code")
    private String refCode;

    @TableField("phone")
    private String phone;

    @TableField("status")
    private UserStatus status = UserStatus.ACTIVE;

    @TableField("google_auth_secret")
    private String googleAuthSecret;

    @TableField("uid")
    private String uid;

    @TableField("email")
    private String email;

    @TableField("kyc_level")
    private KycLevel kycLevel= KycLevel.NONE;

    @TableField("password_hash")
    private String passwordHash;

    public enum KycLevel {
        NONE,
        BASIC,
        ADVANCED
    }

    public enum UserStatus {
        ACTIVE, FROZEN, BANNED
    }
}

