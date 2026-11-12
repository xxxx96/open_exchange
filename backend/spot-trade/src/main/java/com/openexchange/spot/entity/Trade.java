package com.openexchange.spot.entity;

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
 * 成交记录：撮合引擎产出的逐笔成交明细，清算消费者据此结算双方余额。
 * maker/taker 双边用户 ID 冗余存储，「我的成交」查询无需回查订单表。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Getter
@Setter
@ToString
@TableName("trades")
@Schema(name = "Trade", description = "成交记录")
public class Trade extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 对外成交号（全局唯一，格式 t_yyyyMMdd_序号）
     */
    @Schema(description = "对外成交号")
    @TableField("trade_id")
    private String tradeId;

    @TableField("pair_id")
    private Long pairId;

    @TableField("maker_order_id")
    private Long makerOrderId;

    @TableField("taker_order_id")
    private Long takerOrderId;

    @TableField("maker_user_id")
    private Long makerUserId;

    @TableField("taker_user_id")
    private Long takerUserId;

    @TableField("price")
    private BigDecimal price;

    @TableField("qty")
    private BigDecimal qty;

    @TableField("quote_amount")
    private BigDecimal quoteAmount;

    @TableField("maker_fee")
    private BigDecimal makerFee;

    @TableField("taker_fee")
    private BigDecimal takerFee;

    /**
     * 买方是否为挂单方（K 线红绿/主动买卖方向判定依据，亦用于推导用户视角的 side）
     */
    @TableField("is_buyer_maker")
    private Boolean buyerMaker;

    /**
     * 成交时间（链下撮合时间）
     */
    @TableField("executed_at")
    private LocalDateTime executedAt;
}
