package com.openexchange.spot.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 成交事件（撮合引擎 → 清算）：一笔撮合结果的完整信息。
 * makerOrderId / takerOrderId 为对外订单号，金额为最小单位整数字符串，
 * isBuyerMaker = true 表示挂单方为买方（据此推导双边 side 与手续费币种）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSettledEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;

    private String makerOrderId;

    private String takerOrderId;

    private String price;

    private String quantity;

    /** 成交金额 = price × quantity（计价币最小单位） */
    private String quoteQuantity;

    /** 挂单方手续费（基础币最小单位） */
    private String makerFee;

    /** 吃单方手续费（计价币最小单位） */
    private String takerFee;

    private Boolean isBuyerMaker;

    /** 成交时间（epoch 毫秒） */
    private Long executedAt;
}
