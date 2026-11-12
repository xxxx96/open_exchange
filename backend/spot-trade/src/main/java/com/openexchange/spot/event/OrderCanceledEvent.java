package com.openexchange.spot.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 撤单完成事件（撮合引擎 → 清算）：引擎已从订单簿移除订单，
 * 清算据此解冻剩余冻结资产并将会话状态置为 CANCELED。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCanceledEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;

    private Long userId;

    private String symbol;

    /** 撤销原因：USER_REQUEST / IOC_UNFILLED / FOK_UNFILLED / SYSTEM */
    private String reason;

    private Long timestamp;
}
