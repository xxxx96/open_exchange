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
 * 提现流水：用户提现申请单的全生命周期记录。
 * request_id 存储客户端幂等键（clientId，唯一索引保证重复提交去重）；
 * 状态流转：SUBMITTED → RISK_CHECK → SIGNING → BROADCASTING → CONFIRMED / FAILED，
 * 对外仅映射为 pending / success / failed 三种状态（见 WithdrawalServiceImpl）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("withdrawals")
@Schema(name = "Withdrawal", description = "提现流水（request_id 幂等去重，对外映射 pending/success/failed）")
public class Withdrawal extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 实际到账数量 = amount - fee
     */
    @Schema(description = "实际到账数量 = amount - fee")
    @TableField("actual_amount")
    private BigDecimal actualAmount;

    /**
     * 申请提现数量（人可读单位，由请求的最小单位金额按 decimals 换算）
     */
    @Schema(description = "申请提现数量（人可读单位）")
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 提现手续费（取自 currencies.withdraw_fee，广播时扣除）
     */
    @Schema(description = "提现手续费（取自 currencies.withdraw_fee）")
    @TableField("fee")
    private BigDecimal fee;

    /**
     * 风控评分，超过阈值进入人工审核
     */
    @Schema(description = "风控评分，超过阈值进入人工审核")
    @TableField("risk_score")
    private Integer riskScore;

    /**
     * 人工审核人 ID（大额提现需审批）
     */
    @Schema(description = "人工审核人 ID（大额提现需审批）")
    @TableField("approver_id")
    private Long approverId;

    /**
     * 资产 ID（currencies 表主键）
     */
    @Schema(description = "资产 ID（currencies 表主键）")
    @TableField("currency_id")
    private Long currencyId;

    /**
     * 提现用户 ID
     */
    @Schema(description = "提现用户 ID")
    @TableField("user_id")
    private Long userId;

    /**
     * 客户端幂等键（clientId）：唯一索引保证重复提交去重，重复请求返回已有提现单号
     */
    @Schema(description = "客户端幂等键（clientId），唯一索引去重")
    @TableField("request_id")
    private String requestId;

    /**
     * 提现目标外部地址
     */
    @Schema(description = "提现目标外部地址")
    @TableField("to_address")
    private String toAddress;

    /**
     * 备注（部分链如 EOS/XRP 提现需要 memo）
     */
    @Schema(description = "备注（部分链如 EOS/XRP 提现需要 memo）")
    @TableField("memo")
    private String memo;

    /**
     * 广播后的链上交易哈希（广播成功后回填）
     */
    @Schema(description = "广播后的链上交易哈希（广播成功后回填）")
    @TableField("tx_hash")
    private String txHash;

    /**
     * 内部状态：SUBMITTED（已提交）→ RISK_CHECK（风控）→ SIGNING（签名）→
     * BROADCASTING（广播）→ CONFIRMED（确认）/ FAILED（失败，含风控拒绝、链上失败）
     */
    @Schema(description = "内部状态：SUBMITTED → RISK_CHECK → SIGNING → BROADCASTING → CONFIRMED / FAILED")
    @TableField("status")
    private String status;
}
