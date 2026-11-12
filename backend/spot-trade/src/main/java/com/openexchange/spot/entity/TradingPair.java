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
 * 
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Getter
@Setter
@ToString
@TableName("trading_pairs")
@Schema(name = "TradingPair", description = "")
public class TradingPair extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("maker_fee_rate")
    private BigDecimal makerFeeRate;

    @TableField("min_order_notional")
    private BigDecimal minOrderNotional;

    @TableField("min_order_qty")
    private BigDecimal minOrderQty;

    @TableField("price_precision")
    private Integer pricePrecision;

    @TableField("qty_precision")
    private Integer qtyPrecision;

    @TableField("taker_fee_rate")
    private BigDecimal takerFeeRate;

    @TableField("base_currency_id")
    private Long baseCurrencyId;

    @TableField("quote_currency_id")
    private Long quoteCurrencyId;

    @TableField("symbol")
    private String symbol;

    @TableField("status")
    private String status;
}
