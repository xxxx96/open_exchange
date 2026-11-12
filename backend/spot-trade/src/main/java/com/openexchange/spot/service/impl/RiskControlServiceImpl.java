package com.openexchange.spot.service.impl;

import com.openexchange.spot.config.SpotTradeProperties;
import com.openexchange.spot.dto.ResolvedPair;
import com.openexchange.spot.entity.TradingPair;
import com.openexchange.spot.enums.OrderSide;
import com.openexchange.spot.enums.OrderType;
import com.openexchange.spot.mapper.OrderMapper;
import com.openexchange.spot.service.IRiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 风控实现：规则校验（本地计算）+ 限额校验（单日限额需查库统计）。
 * 参考价取该交易对最近一笔成交价；无成交记录时跳过偏离校验（新交易对首单场景）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlServiceImpl implements IRiskControlService {

    private final OrderMapper orderMapper;
    private final SpotTradeProperties properties;

    @Override
    public void validatePairRules(ResolvedPair rp, OrderSide side, OrderType type,
                                  BigDecimal price, BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice) {
        TradingPair pair = rp.getPair();
        int pricePrecision = pair.getPricePrecision() == null ? 2 : pair.getPricePrecision();
        int qtyPrecision = pair.getQtyPrecision() == null ? 5 : pair.getQtyPrecision();

        // tickSize：价格小数位不得超过 price_precision
        if (price != null && price.stripTrailingZeros().scale() > pricePrecision) {
            throw new BusinessException(ErrorCode.INVALID_PRICE,
                    "price precision exceeds tickSize (max " + pricePrecision + " decimals)");
        }
        // lotSize：数量小数位不得超过 qty_precision
        if (qty != null && qty.stripTrailingZeros().scale() > qtyPrecision) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY,
                    "quantity precision exceeds lotSize (max " + qtyPrecision + " decimals)");
        }
        // 最小下单数量
        BigDecimal minQty = pair.getMinOrderQty();
        if (qty != null && minQty != null && qty.compareTo(minQty) < 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY,
                    "quantity below minOrderQty: " + minQty.stripTrailingZeros().toPlainString());
        }
        // 最小成交额（市价卖单无参考价时跳过，成交后由引擎保证）
        BigDecimal minNotional = pair.getMinOrderNotional();
        BigDecimal notional = estimateNotional(type, side, price, qty, quoteQty, refPrice);
        if (notional != null && minNotional != null && notional.compareTo(minNotional) < 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY,
                    "notional below minOrderNotional: " + minNotional.stripTrailingZeros().toPlainString());
        }
    }

    @Override
    public void checkRiskLimits(Long userId, ResolvedPair rp, OrderSide side, OrderType type,
                                BigDecimal price, BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice) {
        SpotTradeProperties.Risk risk = properties.getRisk();
        BigDecimal notional = estimateNotional(type, side, price, qty, quoteQty, refPrice);
        if (notional == null) {
            // 市价卖单且无参考价：下单时刻无法估算名义价值，跳过限额（成交额由引擎回写后纳入统计）
            log.debug("跳过限额校验：市价卖单无参考价 userId={}", userId);
            return;
        }
        // 单笔限额
        if (risk.getMaxOrderNotional() != null && notional.compareTo(risk.getMaxOrderNotional()) > 0) {
            throw new BusinessException(ErrorCode.SINGLE_ORDER_LIMIT_EXCEEDED,
                    "order notional exceeds single-order limit: " + risk.getMaxOrderNotional().toPlainString());
        }
        // 单日限额（自然日，UTC）
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal todaySum = orderMapper.sumTodayNotional(userId, dayStart);
        if (todaySum == null) {
            todaySum = BigDecimal.ZERO;
        }
        if (risk.getDailyOrderNotionalLimit() != null
                && todaySum.add(notional).compareTo(risk.getDailyOrderNotionalLimit()) > 0) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "daily order limit exceeded: " + risk.getDailyOrderNotionalLimit().toPlainString());
        }
        // 价格偏离（仅限价单；市价单以市场价成交，无偏离可言）
        BigDecimal maxDeviation = risk.getMaxPriceDeviation();
        if (type == OrderType.LIMIT && price != null && refPrice != null
                && refPrice.signum() > 0 && maxDeviation != null) {
            BigDecimal deviation = price.subtract(refPrice).abs().divide(refPrice, 18, java.math.RoundingMode.HALF_UP);
            if (deviation.compareTo(maxDeviation) > 0) {
                throw new BusinessException(ErrorCode.PRICE_DEVIATION_TOO_LARGE,
                        "price deviates from reference price by more than "
                                + maxDeviation.toPlainString());
            }
        }
    }

    @Override
    public BigDecimal estimateNotional(OrderType type, OrderSide side, BigDecimal price,
                                       BigDecimal qty, BigDecimal quoteQty, BigDecimal refPrice) {
        if (type == OrderType.LIMIT) {
            return price == null || qty == null ? null : price.multiply(qty);
        }
        if (side == OrderSide.BUY) {
            // 市价买单：指定报价金额则已知，按数量买则需参考价估算
            if (quoteQty != null) {
                return quoteQty;
            }
            return qty == null || refPrice == null ? null : refPrice.multiply(qty);
        }
        // 市价卖单：只能按参考价估算
        return qty == null || refPrice == null ? null : refPrice.multiply(qty);
    }
}
