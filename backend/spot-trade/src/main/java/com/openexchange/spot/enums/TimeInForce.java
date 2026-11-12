package com.openexchange.spot.enums;

import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;

/**
 * 订单有效期：
 * GTC（一直有效直到成交或撤单）/ IOC（立即成交，剩余取消）/ FOK（全部成交或全部取消）
 */
public enum TimeInForce {
    GTC,
    IOC,
    FOK;

    public static TimeInForce parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return GTC;
        }
        try {
            return TimeInForce.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "timeInForce must be GTC, IOC or FOK");
        }
    }
}
