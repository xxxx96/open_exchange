package com.openexchange.spot.util;

import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 最小单位整数字符串 ↔ 人可读 BigDecimal 的换算。
 * 对外契约（REST / WS / Kafka）统一使用最小单位整数，落库前人可读单位。
 */
public final class AmountCodec {

    private AmountCodec() {
    }

    /**
     * 校验并解析最小单位整数字符串（仅接受非负整数字面量，拒绝小数/科学计数/负数）。
     */
    public static BigInteger parseMinimal(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + " is required");
        }
        String v = value.trim();
        if (!v.matches("\\d+")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + " must be a non-negative integer in minimal units");
        }
        return new BigInteger(v);
    }

    /**
     * 最小单位整数 → 人可读金额（除以 10^decimals）
     */
    public static BigDecimal toHuman(String minimal, int decimals, String field) {
        return new BigDecimal(parseMinimal(minimal, field))
                .movePointLeft(decimals);
    }

    /**
     * 人可读金额 → 最小单位整数字符串（乘以 10^decimals 后向下截断）
     */
    public static String toMinimal(BigDecimal human, int decimals) {
        if (human == null) {
            return "0";
        }
        return human.multiply(BigDecimal.TEN.pow(decimals))
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger()
                .toString();
    }

    /**
     * 人可读金额 → 最小单位整数字符串（向上取整，用于冻结预估等需兜底的场景）
     */
    public static String toMinimalCeil(BigDecimal human, int decimals) {
        if (human == null) {
            return "0";
        }
        return human.multiply(BigDecimal.TEN.pow(decimals))
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger()
                .toString();
    }
}
