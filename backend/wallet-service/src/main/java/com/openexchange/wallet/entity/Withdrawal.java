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
@TableName("withdrawal")
@Schema(name = "Withdrawal", description = "")
public class Withdrawal extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("actual_amount")
    private BigDecimal actualAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("fee")
    private BigDecimal fee;

    @TableField("risk_score")
    private Integer riskScore;

    @TableField("approver_id")
    private Long approverId;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("user_id")
    private Long userId;

    @TableField("request_id")
    private String requestId;

    @TableField("to_address")
    private String toAddress;

    @TableField("memo")
    private String memo;

    @TableField("tx_hash")
    private String txHash;

    @TableField("status")
    private String status;
}
