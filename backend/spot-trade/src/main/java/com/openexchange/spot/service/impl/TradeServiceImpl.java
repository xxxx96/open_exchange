package com.openexchange.spot.service.impl;

import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import com.openexchange.spot.dto.TradePageVO;
import com.openexchange.spot.dto.TradeRow;
import com.openexchange.spot.dto.TradeVO;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.entity.Order;
import com.openexchange.spot.entity.TradingPair;
import com.openexchange.spot.enums.OrderSide;
import com.openexchange.spot.mapper.CurrencyMapper;
import com.openexchange.spot.mapper.OrderMapper;
import com.openexchange.spot.mapper.TradeMapper;
import com.openexchange.spot.mapper.TradingPairMapper;
import com.openexchange.spot.service.ITradeService;
import com.openexchange.spot.util.AmountCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「我的成交」实现：trades 表 maker/taker 双边冗余用户 ID，查询无需回查订单；
 * side / isMaker / fee 全部按当前查询用户推导（同一笔成交对买卖双方呈现不同）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements ITradeService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final TradeMapper tradeMapper;
    private final OrderMapper orderMapper;
    private final TradingPairMapper tradingPairMapper;
    private final CurrencyMapper currencyMapper;

    @Override
    public TradePageVO myTrades(Long userId, String symbol, String orderId,
                                Long startTime, Long endTime, Integer limit, String cursor) {
        int size = normalizeLimit(limit);
        Long pairId = resolvePairId(symbol);
        Long orderPkId = resolveOwnedOrderId(userId, orderId);
        LocalDateTime start = toLocalDateTime(startTime, "startTime");
        LocalDateTime end = toLocalDateTime(endTime, "endTime");
        Long beforeId = cursor == null || cursor.isBlank() ? null : tradeMapper.selectIdByTradeId(cursor.trim());
        if (beforeId == null && cursor != null && !cursor.isBlank()) {
            // 游标已过期（成交记录被清理）时从头开始，避免客户端卡死
            log.warn("[trade] 游标无效 userId={} cursor={}", userId, cursor);
        }

        // 多查一条判断是否还有下一页，避免额外 count 查询
        List<TradeRow> rows = tradeMapper.selectMyTrades(userId, pairId, orderPkId, start, end, beforeId, size + 1);
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return TradePageVO.builder().list(List.of()).nextCursor(null).hasMore(false).build();
        }

        Map<Long, PairMeta> pairMetaMap = loadPairMeta(rows);
        List<TradeVO> list = new ArrayList<>(rows.size());
        for (TradeRow row : rows) {
            list.add(toVO(userId, row, pairMetaMap.get(row.getPairId())));
        }
        String nextCursor = hasMore ? rows.get(rows.size() - 1).getTradeId() : null;
        return TradePageVO.builder().list(list).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    // ------------------------------------------------------------------ 内部实现

    /** 交易对展示信息：符号与双边精度（金额换算必需） */
    private record PairMeta(String baseSymbol, String quoteSymbol, int baseDecimals, int quoteDecimals) {

        String displaySymbol() {
            return baseSymbol + "/" + quoteSymbol;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long resolvePairId(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        TradingPair pair = tradingPairMapper.selectBySymbolAny(symbol.trim().toUpperCase().replace("/", ""));
        if (pair == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "trading pair not found: " + symbol);
        }
        return pair.getId();
    }

    /**
     * 订单过滤：必须属于当前用户，否则返回 null（按无过滤处理）而不是报错，
     * 避免通过订单号探测他人订单是否存在。
     */
    private Long resolveOwnedOrderId(Long userId, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        Order order = orderMapper.selectByOrderIdAndUser(userId, orderId.trim());
        return order == null ? null : order.getId();
    }

    private LocalDateTime toLocalDateTime(Long epochMilli, String field) {
        if (epochMilli == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /** 批量加载结果集涉及交易对的币种信息，避免逐行查询 */
    private Map<Long, PairMeta> loadPairMeta(List<TradeRow> rows) {
        List<Long> pairIds = rows.stream().map(TradeRow::getPairId).distinct().toList();
        List<TradingPair> pairs = tradingPairMapper.selectBatchIds(pairIds);
        List<Long> currencyIds = new ArrayList<>();
        for (TradingPair pair : pairs) {
            if (pair.getBaseCurrencyId() != null) {
                currencyIds.add(pair.getBaseCurrencyId());
            }
            if (pair.getQuoteCurrencyId() != null) {
                currencyIds.add(pair.getQuoteCurrencyId());
            }
        }
        Map<Long, Currency> currencyMap = new LinkedHashMap<>();
        if (!currencyIds.isEmpty()) {
            for (Currency currency : currencyMapper.selectBatchIds(currencyIds)) {
                currencyMap.put(currency.getId(), currency);
            }
        }
        Map<Long, PairMeta> result = new LinkedHashMap<>();
        for (TradingPair pair : pairs) {
            Currency base = currencyMap.get(pair.getBaseCurrencyId());
            Currency quote = currencyMap.get(pair.getQuoteCurrencyId());
            if (base == null || quote == null) {
                continue;
            }
            result.put(pair.getId(), new PairMeta(base.getSymbol(), quote.getSymbol(),
                    base.getDecimals() == null ? 18 : base.getDecimals(),
                    quote.getDecimals() == null ? 18 : quote.getDecimals()));
        }
        return result;
    }

    private TradeVO toVO(Long userId, TradeRow row, PairMeta meta) {
        boolean maker = userId.equals(row.getMakerUserId());
        // 买方是挂单方 ⇒ 挂单方买、吃单方卖；据此推导当前用户视角的买卖方向
        boolean userIsBuyer = maker == Boolean.TRUE.equals(row.getBuyerMaker());
        OrderSide side = userIsBuyer ? OrderSide.BUY : OrderSide.SELL;
        int feeDecimals = side == OrderSide.BUY ? meta.baseDecimals() : meta.quoteDecimals();
        String feeAsset = side == OrderSide.BUY ? meta.baseSymbol() : meta.quoteSymbol();

        LocalDateTime executedAt = row.getExecutedAt();
        return TradeVO.builder()
                .tradeId(row.getTradeId())
                .orderId(maker ? row.getMakerOrderNo() : row.getTakerOrderNo())
                .symbol(meta.displaySymbol())
                .side(side.name())
                .price(AmountCodec.toMinimal(row.getPrice(), meta.quoteDecimals()))
                .quantity(AmountCodec.toMinimal(row.getQty(), meta.baseDecimals()))
                .quoteQuantity(AmountCodec.toMinimal(row.getQuoteAmount(), meta.quoteDecimals()))
                .fee(AmountCodec.toMinimal(maker ? row.getMakerFee() : row.getTakerFee(), feeDecimals))
                .feeAsset(feeAsset)
                .isMaker(maker)
                .timestamp(executedAt == null ? null
                        : executedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }
}
