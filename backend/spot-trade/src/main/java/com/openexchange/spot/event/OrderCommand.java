package com.openexchange.spot.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 撮合队列命令（spot-trade → 撮合引擎）：
 * action = NEW（新订单）/ CANCEL（撤单）。同一 symbol 的命令使用相同 key 投递，
 * 保证同一分区内时序一致（撤单不早于下单被引擎处理）。
 * 金额字段均为最小单位整数字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** NEW / CANCEL */
    private String action;

    private String orderId;

    private String clientOrderId;

    private Long userId;

    /** 交易对展示符号，如 BTC/USDT */
    private String symbol;

    private String side;

    private String type;

    private String price;

    private String quantity;

    private String quoteQuantity;

    private String timeInForce;

    private Long timestamp;
}
