package com.openexchange.spot.enums;

import com.openexchange.spot.enums.OrderSide;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;

/**
 * 订单方向：BUY（买入）/ SELL（卖出）
 */
public enum OrderSide {
    BUY,
    SELL;

    public static OrderSide parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "side is required");
        }
        try {
            return OrderSide.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "side must be BUY or SELL");
        }
    }
}
