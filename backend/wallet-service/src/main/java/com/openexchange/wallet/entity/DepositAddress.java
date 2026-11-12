package com.openexchange.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

import java.io.Serializable;

/**
 * <p>
 * 用户充值地址分配记录：同用户 + 同币种的充值地址固定不变
 * （UNIQUE(user_id, currency_id) 唯一约束保证），地址来自 address_pool 的 CAS 占用。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("deposit_addresses")
@Schema(name = "DepositAddress", description = "用户充值地址分配记录（同用户同币种地址固定不变）")
public class DepositAddress extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 资产 ID（currencies 表主键），「userId + currencyId」构成地址固定不变的查询键
     */
    @Schema(description = "资产 ID（currencies 表主键）")
    @TableField("currency_id")
    private Long currencyId;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    @TableField("user_id")
    private Long userId;

    /**
     * BIP44 派生路径（如 m/44'/60'/0'/0/1），离线签名时定位密钥
     */
    @Schema(description = "BIP44 派生路径，离线签名时定位密钥")
    @TableField("derivation_path")
    private String derivationPath;

    /**
     * 分配给用户的链上充值地址（从地址池中占用，分配后永不复用）
     */
    @Schema(description = "分配给用户的链上充值地址，从地址池中占用")
    @TableField("address")
    private String address;
}
