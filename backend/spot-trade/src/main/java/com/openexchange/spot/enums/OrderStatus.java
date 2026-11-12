package com.openexchange.spot.enums;

/**
 * 订单状态：
 * NEW（已受理，未成交）/ PARTIALLY_FILLED（部分成交）/ FILLED（全部成交）/
 * CANCELED（已撤销）/ REJECTED（被拒绝）
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED;

    /**
     * 是否可撤销：仅 NEW / PARTIALLY_FILLED 可撤
     */
    public boolean cancelable() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
}
