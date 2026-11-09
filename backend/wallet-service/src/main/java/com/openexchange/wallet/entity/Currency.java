package com.openexchange.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("currencies")
@Schema(name = "Currency", description = "")
public class Currency extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("decimals")
    private Integer decimals;

    @TableField("deposit_confirm")
    private Integer depositConfirm;

    @TableField("min_withdraw")
    private BigDecimal minWithdraw;

    @TableField("withdraw_fee")
    private BigDecimal withdrawFee;

    @TableField("symbol")
    private String symbol;

    @TableField("contract_addr")
    private String contractAddr;

    @TableField("chain")
    private String chain;

    @TableField("full_name")
    private String fullName;

    @TableField("status")
    private String status;
}
