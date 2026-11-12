package com.openexchange.spot.enums;

import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;

/**
 * 订单类型：LIMIT（限价）/ MARKET（市价）
 */
public enum OrderType {
    LIMIT,
    MARKET;

    public static OrderType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "type is required");
        }
        try {
            return OrderType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "type must be LIMIT or MARKET");
        }
    }
}
