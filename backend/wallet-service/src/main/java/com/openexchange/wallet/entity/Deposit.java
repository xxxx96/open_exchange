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
import java.time.LocalDateTime;

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
@TableName("deposits")
@Schema(name = "Deposit", description = "")
public class Deposit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("confirmations")
    private Integer confirmations;

    @TableField("fee")
    private BigDecimal fee;

    @TableField("required_confirm")
    private Integer requiredConfirm;

    @TableField("block_height")
    private Long blockHeight;

    @TableField("credited_at")
    private LocalDateTime creditedAt;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("user_id")
    private Long userId;

    @TableField("from_address")
    private String fromAddress;

    @TableField("to_address")
    private String toAddress;

    @TableField("block_hash")
    private String blockHash;

    @TableField("tx_hash")
    private String txHash;

    /**
     * 交易日志索引：与 currency_id + tx_hash 共同构成充值幂等键。
     * 同一笔链上交易可能包含多笔转账事件（如 ERC20 Transfer log），需以 log_index 区分。
     */
    @TableField("log_index")
    private Long logIndex;

    @TableField("status")
    private String status;
}
