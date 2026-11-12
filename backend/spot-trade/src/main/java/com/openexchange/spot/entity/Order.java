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

/**
 * <p>
 * 现货订单：用户买卖委托单的全生命周期记录。
 * 金额列（price / original_qty / executed_qty / executed_quote / avg_fill_price / quote_qty / fee）
 * 均为人可读单位（DECIMAL(32,18)），与 currencies.decimals 配合换算；
 * 对外接口（REST/WS/Kafka）统一使用最小单位整数字符串，边界层转换。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Getter
@Setter
@ToString
@TableName("orders")
@Schema(name = "Order", description = "现货订单")
public class Order extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 对外订单号（全局唯一，格式 o_yyyyMMdd_序号）
     */
    @Schema(description = "对外订单号")
    @TableField("order_id")
    private String orderId;

    /**
     * 客户端幂等键：同一用户重复提交返回同一订单，(user_id, client_order_id) 唯一索引兜底
     */
    @Schema(description = "客户端幂等键")
    @TableField("client_order_id")
    private String clientOrderId;

    @TableField("avg_fill_price")
    private BigDecimal avgFillPrice;

    @TableField("executed_qty")
    private BigDecimal executedQty;

    @TableField("executed_quote")
    private BigDecimal executedQuote;

    @TableField("original_qty")
    private BigDecimal originalQty;

    @TableField("price")
    private BigDecimal price;

    @TableField("stop_price")
    private BigDecimal stopPrice;

    @TableField("pair_id")
    private Long pairId;

    @TableField("user_id")
    private Long userId;

    @TableField("order_type")
    private String orderType;

    @TableField("side")
    private String side;

    @TableField("status")
    private String status;

    @TableField("time_in_force")
    private String timeInForce;

    /**
     * 市价买单报价数量（花多少计价币买）；其余类型为 NULL
     */
    @Schema(description = "市价买单报价数量")
    @TableField("quote_qty")
    private BigDecimal quoteQty;

    /**
     * 累计手续费（人可读单位）
     */
    @Schema(description = "累计手续费")
    @TableField("fee")
    private BigDecimal fee;

    /**
     * 手续费币种符号：买单收基础币（BTC），卖单收计价币（USDT）
     */
    @Schema(description = "手续费币种")
    @TableField("fee_asset")
    private String feeAsset;

    /**
     * 下单时冻结的币种与数量（人可读单位）。
     * 成交/撤单时据此解冻剩余部分：剩余 = frozen_amount - 已花费（买单按成交金额、卖单按成交数量）。
     */
    @Schema(description = "下单冻结数量")
    @TableField("frozen_amount")
    private BigDecimal frozenAmount;

    @Schema(description = "下单冻结币种 ID")
    @TableField("frozen_currency_id")
    private Long frozenCurrencyId;
}
