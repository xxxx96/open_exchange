package com.openexchange.spot.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「我的成交」查询行：成交记录 + 交易对符号 + 双边对外订单号（JOIN 结果）。
 */
@Data
public class TradeRow {

    private Long id;
    private String tradeId;
    private Long pairId;
    private Long makerOrderId;
    private Long takerOrderId;
    private Long makerUserId;
    private Long takerUserId;
    private BigDecimal price;
    private BigDecimal qty;
    private BigDecimal quoteAmount;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private Boolean buyerMaker;
    private LocalDateTime executedAt;

    /** 挂单方对外订单号 */
    private String makerOrderNo;

    /** 吃单方对外订单号 */
    private String takerOrderNo;
}
