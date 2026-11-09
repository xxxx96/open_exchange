package com.openexchange.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("wallets")
@Schema(name = "Wallet", description = "")
public class Wallet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("balance")
    private BigDecimal balance;

    @TableField("frozen_balance")
    private BigDecimal frozenBalance;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("user_id")
    private Long userId;

    @Version
    @TableField("version")
    private Long version;

    @TableField("wallet_type")
    private String walletType;
}
