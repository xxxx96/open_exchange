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
 * 充值地址池：预先按 BIP44 派生的一批链上地址，供用户充值地址分配使用。
 * 状态为 available 的地址可分配给用户，分配后状态置为 used 且不再复用，
 * 从而保证「同用户 + 同链 + 同资产」的充值地址固定不变。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("address_pool")
@Schema(name = "AddressPool", description = "充值地址池：按 BIP44 预先派生的链上地址，available 状态可分配，分配后置为 used")
public class AddressPool extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 链标识，如 Ethereum、TRON
     */
    @Schema(description = "链标识，如 Ethereum、TRON")
    @TableField("chain")
    private String chain;

    /**
     * BIP44 派生路径，如 m/44'/60'/0'/0/1
     */
    @Schema(description = "BIP44 派生路径，如 m/44'/60'/0'/0/1")
    @TableField("derivation_path")
    private String derivationPath;

    /**
     * 链上地址
     */
    @Schema(description = "链上地址")
    @TableField("address")
    private String address;

    /**
     * 地址备注（部分链如 EOS/XRP 转账需要 memo）
     */
    @Schema(description = "地址备注，部分链（如 EOS/XRP）转账需要 memo")
    @TableField("memo")
    private String memo;

    /**
     * 状态：available-未分配可使用；used-已分配
     */
    @Schema(description = "状态：available-未分配可使用；used-已分配")
    @TableField("status")
    private String status;
}
