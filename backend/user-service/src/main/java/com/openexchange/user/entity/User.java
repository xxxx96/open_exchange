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
 * 用户主表：存储账号基础信息、KYC 等级、邀请关系与账号状态。
 * 敏感字段（密码哈希、2FA 密钥）严禁明文存储，接口层不回传。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Getter
@Setter
@ToString
@TableName("users")
@Schema(name = "User", description = "用户主表：账号基础信息、KYC、邀请关系")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 是否已开启谷歌二步验证（TOTP 动态口令），默认关闭
     */
    @Schema(description = "是否已开启谷歌二步验证（2FA）")
    @TableField("google_auth_enabled")
    private Boolean googleAuthEnabled;

    /**
     * 邀请人用户 ID（users.id 自引用），形成邀请链，用于邀请返佣
     */
    @Schema(description = "邀请人用户 ID，形成邀请链")
    @TableField("referred_by")
    private Long referredBy;

    /**
     * 本用户的邀请码（全局唯一），供他人注册时填写
     */
    @Schema(description = "本用户的邀请码，供他人注册时填写")
    @TableField("ref_code")
    private String refCode;

    /**
     * 手机号（可选，用于短信验证/找回）
     */
    @Schema(description = "手机号（可选）")
    @TableField("phone")
    private String phone;

    /**
     * 账号状态：ACTIVE（正常）/ FROZEN（冻结）/ BANNED（封禁）
     */
    @Schema(description = "账号状态：ACTIVE / FROZEN / BANNED")
    @TableField("status")
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 谷歌验证器（2FA）密钥种子，用于 TOTP 动态口令校验（敏感，严禁回传前端）
     */
    @Schema(description = "谷歌验证器密钥种子（敏感，严禁回传）")
    @TableField("google_auth_secret")
    private String googleAuthSecret;

    /**
     * 对外展示的用户唯一标识（脱敏 ID，不暴露自增主键）
     */
    @Schema(description = "对外展示的用户唯一标识（脱敏 ID）")
    @TableField("uid")
    private String uid;

    /**
     * 邮箱，登录凭证之一，全表唯一
     */
    @Schema(description = "邮箱，登录凭证之一，全表唯一")
    @TableField("email")
    private String email;

    /**
     * KYC 认证等级：NONE（未认证）→ BASIC → ADVANCED，提现/交易额度随等级提升
     */
    @Schema(description = "KYC 认证等级：NONE / BASIC / ADVANCED")
    @TableField("kyc_level")
    private KycLevel kycLevel = KycLevel.NONE;

    /**
     * 密码哈希（BCrypt 等单向哈希，严禁明文存储）
     */
    @Schema(description = "密码哈希（单向哈希存储）")
    @TableField("password_hash")
    private String passwordHash;

    /**
     * KYC 认证等级枚举：等级越高，可提现/交易额度越大
     */
    public enum KycLevel {
        /** 未认证 */
        NONE,
        /** 基础认证 */
        BASIC,
        /** 高级认证 */
        ADVANCED
    }

    /**
     * 账号状态枚举：冻结/封禁后禁止登录与交易
     */
    public enum UserStatus {
        /** 正常 */
        ACTIVE,
        /** 冻结 */
        FROZEN,
        /** 封禁 */
        BANNED
    }
}
