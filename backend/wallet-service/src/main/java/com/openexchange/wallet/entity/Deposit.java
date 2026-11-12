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
 * 充值流水：链上监听服务发现充值交易后写入，作为入账依据。
 * 幂等键 = currency_id + tx_hash + log_index（唯一索引 uk_deposits_tx），
 * 同一笔链上交易可能包含多笔转账事件（如 ERC20 Transfer log），以 log_index 区分。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("deposits")
@Schema(name = "Deposit", description = "充值流水（幂等键：currency_id + tx_hash + log_index）")
public class Deposit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 充值金额（人可读单位，由回调的最小单位金额按 decimals 换算）
     */
    @Schema(description = "充值金额（人可读单位）")
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 当前区块确认数，随链增长
     */
    @Schema(description = "当前区块确认数，随链增长")
    @TableField("confirmations")
    private Integer confirmations;

    /**
     * 链上手续费（仅记录，不参与入账计算）
     */
    @Schema(description = "链上手续费（仅记录，不参与入账计算）")
    @TableField("fee")
    private BigDecimal fee;

    /**
     * 到账所需确认数（取自 currencies.deposit_confirm）
     */
    @Schema(description = "到账所需确认数（取自 currencies.deposit_confirm）")
    @TableField("required_confirm")
    private Integer requiredConfirm;

    /**
     * 交易所在区块高度
     */
    @Schema(description = "交易所在区块高度")
    @TableField("block_height")
    private Long blockHeight;

    /**
     * 入账时间（确认数达标、余额加到账后回填）
     */
    @Schema(description = "入账时间（余额加到账后回填）")
    @TableField("credited_at")
    private LocalDateTime creditedAt;

    /**
     * 资产 ID（currencies 表主键）
     */
    @Schema(description = "资产 ID（currencies 表主键）")
    @TableField("currency_id")
    private Long currencyId;

    /**
     * 充值用户 ID
     */
    @Schema(description = "充值用户 ID")
    @TableField("user_id")
    private Long userId;

    /**
     * 付款方链上地址
     */
    @Schema(description = "付款方链上地址")
    @TableField("from_address")
    private String fromAddress;

    /**
     * 收款方链上地址（应为该用户的充值地址）
     */
    @Schema(description = "收款方链上地址（应为该用户的充值地址）")
    @TableField("to_address")
    private String toAddress;

    /**
     * 交易所在区块哈希
     */
    @Schema(description = "交易所在区块哈希")
    @TableField("block_hash")
    private String blockHash;

    /**
     * 链上交易哈希：与 log_index 组合构成幂等键（同一交易多笔事件需区分）
     */
    @Schema(description = "链上交易哈希，与 log_index 组合构成幂等键")
    @TableField("tx_hash")
    private String txHash;

    /**
     * 交易日志索引：与 currency_id + tx_hash 共同构成充值幂等键。
     * 同一笔链上交易可能包含多笔转账事件（如 ERC20 Transfer log），需以 log_index 区分。
     */
    @TableField("log_index")
    private Long logIndex;

    /**
     * 状态：PENDING（待确认）→ CREDITED（已入账）/ FAILED
     */
    @Schema(description = "状态：PENDING（待确认）→ CREDITED（已入账）/ FAILED")
    @TableField("status")
    private String status;
}
