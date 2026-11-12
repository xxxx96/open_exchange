package com.openexchange.spot.service;

import com.openexchange.spot.dto.ResolvedPair;
import com.openexchange.spot.enums.OrderSide;
import com.openexchange.spot.enums.OrderType;

import java.math.BigDecimal;

/**
 * 下单风控：交易规则校验（tickSize / lotSize / 最小数量与成交额）与限额校验
 * （单笔限额、单日限额、价格偏离）。
 */
public interface IRiskControlService {

    /**
     * 交易对规则校验：价格精度（tickSize）、数量精度（lotSize）、最小下单数量、最小成交额
     */
    void validatePairRules(ResolvedPair pair, OrderSide side, OrderType type,
                           BigDecimal price, BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice);

    /**
     * 限额校验：单笔名义价值上限、单用户单日累计上限、限价单价格偏离
     */
    void checkRiskLimits(Long userId, ResolvedPair pair, OrderSide side, OrderType type,
                         BigDecimal price, BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice);

    /**
     * 下单名义价值（计价币人可读单位）；市价卖单无参考价时无法估算，返回 null 表示跳过限额校验
     */
    BigDecimal estimateNotional(OrderType type, OrderSide side, BigDecimal price,
                                BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice);
}
