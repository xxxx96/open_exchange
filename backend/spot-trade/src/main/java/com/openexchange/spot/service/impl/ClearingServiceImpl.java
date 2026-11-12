package com.openexchange.spot.service.impl;

import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.entity.Order;
import com.openexchange.spot.entity.SpotBalance;
import com.openexchange.spot.entity.Trade;
import com.openexchange.spot.entity.TradingPair;
import com.openexchange.spot.enums.OrderSide;
import com.openexchange.spot.enums.OrderStatus;
import com.openexchange.spot.event.AfterCommitExecutor;
import com.openexchange.spot.event.BalanceUpdateData;
import com.openexchange.spot.event.OrderCanceledEvent;
import com.openexchange.spot.event.OrderUpdateData;
import com.openexchange.spot.event.TradeSettledEvent;
import com.openexchange.spot.event.TradeUpdateData;
import com.openexchange.spot.event.UserEventFactory;
import com.openexchange.spot.event.UserEventPublisher;
import com.openexchange.spot.mapper.CurrencyMapper;
import com.openexchange.spot.mapper.OrderMapper;
import com.openexchange.spot.mapper.SpotSerialMapper;
import com.openexchange.spot.mapper.TradeMapper;
import com.openexchange.spot.mapper.TradingPairMapper;
import com.openexchange.spot.service.IClearingService;
import com.openexchange.spot.service.ISpotBalanceService;
import com.openexchange.spot.util.AmountCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 清算实现。
 *
 * <p><b>成交结算口径</b>（以 isBuyerMaker 推导双边身份）：</p>
 * <ul>
 *   <li>买方：核销冻结的计价币（成交金额），入账基础币（成交数量 - 手续费，手续费收基础币）</li>
 *   <li>卖方：核销冻结的基础币（成交数量），入账计价币（成交金额 - 手续费，手续费收计价币）</li>
 *   <li>订单进入终态（FILLED）时解冻剩余冻结：市价买单按数量下单时冻结的是预估上浮值，
 *       必须把没用完的部分退给用户</li>
 * </ul>
 *
 * <p><b>幂等</b>：成交按自然键（maker/taker 订单 + 价格 + 数量 + 成交时间）去重，
 * 订单回写带 status CAS（命中 0 行说明已终态，整体跳过），撤单同理。
 * 用户事件在事务提交后推送，推送失败仅记日志，不阻塞清算。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearingServiceImpl implements IClearingService {

    private static final String BIZ_TYPE_TRADE = "TRADE";
    private static final DateTimeFormatter TRADE_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SpotSerialMapper spotSerialMapper;
    private final TradingPairMapper tradingPairMapper;
    private final CurrencyMapper currencyMapper;
    private final ISpotBalanceService spotBalanceService;
    private final UserEventPublisher userEventPublisher;
    private final UserEventFactory userEventFactory;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onTradeSettled(TradeSettledEvent event) {
        PairContext ctx = resolvePair(event.getSymbol());
        BigDecimal price = AmountCodec.toHuman(event.getPrice(), ctx.quoteDecimals(), "price");
        BigDecimal qty = AmountCodec.toHuman(event.getQuantity(), ctx.baseDecimals(), "quantity");
        BigDecimal quoteAmount = AmountCodec.toHuman(event.getQuoteQuantity(), ctx.quoteDecimals(), "quoteQuantity");
        BigDecimal makerFee = AmountCodec.toHuman(event.getMakerFee(), ctx.baseDecimals(), "makerFee");
        BigDecimal takerFee = AmountCodec.toHuman(event.getTakerFee(), ctx.quoteDecimals(), "takerFee");
        LocalDateTime executedAt = toLocalDateTime(event.getExecutedAt());

        Order makerOrder = orderMapper.selectByOrderId(event.getMakerOrderId());
        Order takerOrder = orderMapper.selectByOrderId(event.getTakerOrderId());
        if (makerOrder == null || takerOrder == null) {
            log.error("[clearing] 成交事件订单不存在 maker={} taker={}",
                    event.getMakerOrderId(), event.getTakerOrderId());
            return;
        }

        // 自然键去重：引擎 at-least-once 投递时防止重复入账
        if (tradeMapper.countByNaturalKey(makerOrder.getId(), takerOrder.getId(), price, qty, executedAt) > 0) {
            log.warn("[clearing] 重复成交事件已跳过 maker={} taker={} price={} qty={}",
                    event.getMakerOrderId(), event.getTakerOrderId(), price, qty);
            return;
        }

        // 买方是否为挂单方：据此推导双边身份与手续费币种
        boolean buyerIsMaker = Boolean.TRUE.equals(event.getIsBuyerMaker());
        Order buyOrder = buyerIsMaker ? makerOrder : takerOrder;
        Order sellOrder = buyerIsMaker ? takerOrder : makerOrder;
        BigDecimal buyFee = buyerIsMaker ? makerFee : takerFee;    // 买方手续费：基础币
        BigDecimal sellFee = buyerIsMaker ? takerFee : makerFee;   // 卖方手续费：计价币

        BigDecimal buyFilledQty = nz(buyOrder.getExecutedQty()).add(qty);
        BigDecimal buyFilledQuote = nz(buyOrder.getExecutedQuote()).add(quoteAmount);
        BigDecimal sellFilledQty = nz(sellOrder.getExecutedQty()).add(qty);
        boolean buyFilled = isFilled(buyOrder, buyFilledQty, buyFilledQuote);
        boolean sellFilled = isFilled(sellOrder, sellFilledQty, buyFilledQuote);

        // ① CAS 回写双边订单：任一方命中 0 行（已终态）即整体跳过，避免重复结算
        if (orderMapper.applyFill(buyOrder.getId(), qty, quoteAmount, buyFee, buyFilled) == 0
                || orderMapper.applyFill(sellOrder.getId(), qty, quoteAmount, sellFee, sellFilled) == 0) {
            log.warn("[clearing] 订单已终态，成交事件跳过 maker={} taker={}",
                    event.getMakerOrderId(), event.getTakerOrderId());
            return;
        }

        // ② 成交明细（trades 表）
        Trade trade = new Trade();
        trade.setTradeId(nextTradeId());
        trade.setPairId(ctx.pairId());
        trade.setMakerOrderId(makerOrder.getId());
        trade.setTakerOrderId(takerOrder.getId());
        trade.setMakerUserId(makerOrder.getUserId());
        trade.setTakerUserId(takerOrder.getUserId());
        trade.setPrice(price);
        trade.setQty(qty);
        trade.setQuoteAmount(quoteAmount);
        trade.setMakerFee(makerFee);
        trade.setTakerFee(takerFee);
        trade.setBuyerMaker(buyerIsMaker);
        trade.setExecutedAt(executedAt);
        tradeMapper.insert(trade);

        // ③ 双边账本结算：核销冻结 + 入账（扣手续费）
        BalanceLedger ledger = new BalanceLedger();
        // 买方：花掉计价币，收到基础币
        spotBalanceService.consumeFrozen(buyOrder.getUserId(), ctx.quoteCurrencyId(), quoteAmount);
        spotBalanceService.credit(buyOrder.getUserId(), ctx.baseCurrencyId(), qty.subtract(buyFee));
        ledger.record(buyOrder.getUserId(), ctx.quoteCurrencyId(), ctx.quoteSymbol(), ctx.quoteDecimals(),
                quoteAmount.negate(), "TRADE_SETTLE");
        ledger.record(buyOrder.getUserId(), ctx.baseCurrencyId(), ctx.baseSymbol(), ctx.baseDecimals(),
                qty.subtract(buyFee), "TRADE_SETTLE");
        // 卖方：花掉基础币，收到计价币
        spotBalanceService.consumeFrozen(sellOrder.getUserId(), ctx.baseCurrencyId(), qty);
        spotBalanceService.credit(sellOrder.getUserId(), ctx.quoteCurrencyId(), quoteAmount.subtract(sellFee));
        ledger.record(sellOrder.getUserId(), ctx.baseCurrencyId(), ctx.baseSymbol(), ctx.baseDecimals(),
                qty.negate(), "TRADE_SETTLE");
        ledger.record(sellOrder.getUserId(), ctx.quoteCurrencyId(), ctx.quoteSymbol(), ctx.quoteDecimals(),
                quoteAmount.subtract(sellFee), "TRADE_SETTLE");

        // ④ 内存态订单推进到最新（供解冻剩余与事件载荷使用）
        applyInMemory(buyOrder, qty, quoteAmount, buyFee, buyFilled);
        applyInMemory(sellOrder, qty, quoteAmount, sellFee, sellFilled);

        // ⑤ 终态订单解冻剩余冻结（市价买单按数量下单的预估上浮部分）
        releaseRemainder(buyOrder, buyFilled, ledger);
        releaseRemainder(sellOrder, sellFilled, ledger);

        // ⑥ 回查最终余额（同一事务内可见未提交改动），提交后一次性推送
        ledger.finalize(spotBalanceService);
        Order freshBuy = orderMapper.selectById(buyOrder.getId());
        Order freshSell = orderMapper.selectById(sellOrder.getId());
        String symbol = ctx.displaySymbol();
        Long buyUserId = buyOrder.getUserId();
        Long sellUserId = sellOrder.getUserId();
        String buyOrderId = buyOrder.getOrderId();
        String sellOrderId = sellOrder.getOrderId();
        afterCommitExecutor.run(() -> {
            userEventPublisher.publish(userEventFactory.orderUpdate(buyUserId, orderUpdateData(freshBuy, ctx)));
            userEventPublisher.publish(userEventFactory.orderUpdate(sellUserId, orderUpdateData(freshSell, ctx)));
            userEventPublisher.publish(userEventFactory.trade(buyUserId, tradeUpdateData(
                    trade.getTradeId(), buyOrderId, symbol, price, qty,
                    buyerIsMaker, buyFee, true, ctx, executedAt)));
            userEventPublisher.publish(userEventFactory.trade(sellUserId, tradeUpdateData(
                    trade.getTradeId(), sellOrderId, symbol, price, qty,
                    !buyerIsMaker, sellFee, false, ctx, executedAt)));
            for (BalanceLedger.Entry entry : ledger.entries()) {
                userEventPublisher.publish(userEventFactory.balanceUpdate(entry.userId, entry.data()));
            }
        });

        log.info("[clearing] 成交结算 tradeId={} {} price={} qty={} buyOrder={} sellOrder={}",
                trade.getTradeId(), symbol, price, qty, buyOrderId, sellOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onOrderCanceled(OrderCanceledEvent event) {
        Order order = orderMapper.selectByOrderId(event.getOrderId());
        if (order == null) {
            log.error("[clearing] 撤单事件订单不存在 orderId={}", event.getOrderId());
            return;
        }
        if (event.getUserId() != null && !event.getUserId().equals(order.getUserId())) {
            log.error("[clearing] 撤单事件用户不匹配 orderId={} eventUserId={} orderUserId={}",
                    event.getOrderId(), event.getUserId(), order.getUserId());
            return;
        }
        // CAS：重复撤单事件或订单已被成交事件置终态时命中 0 行，直接跳过
        if (orderMapper.markCanceled(order.getId()) == 0) {
            log.info("[clearing] 订单已终态，撤单事件跳过 orderId={} status={}", order.getOrderId(), order.getStatus());
            return;
        }

        PairContext ctx = resolvePair(event.getSymbol());
        BalanceLedger ledger = new BalanceLedger();

        // 解冻剩余冻结资产（已成交部分已在成交结算中核销）
        BigDecimal remainder = remainder(order);
        if (remainder.signum() > 0) {
            Long currencyId = order.getFrozenCurrencyId() != null
                    ? order.getFrozenCurrencyId()
                    : (OrderSide.SELL.name().equals(order.getSide()) ? ctx.baseCurrencyId() : ctx.quoteCurrencyId());
            Currency currency = currencyMapper.selectById(currencyId);
            if (currency == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "frozen currency not found: " + currencyId);
            }
            spotBalanceService.unfreeze(order.getUserId(), currencyId, remainder);
            ledger.record(order.getUserId(), currency.getId(), currency.getSymbol(),
                    currency.getDecimals() == null ? 18 : currency.getDecimals(), remainder, "ORDER_UNFREEZE");
        }

        ledger.finalize(spotBalanceService);
        Order fresh = orderMapper.selectById(order.getId());
        Long userId = order.getUserId();
        String orderId = order.getOrderId();
        afterCommitExecutor.run(() -> {
            userEventPublisher.publish(userEventFactory.orderUpdate(userId, orderUpdateData(fresh, ctx)));
            for (BalanceLedger.Entry entry : ledger.entries()) {
                userEventPublisher.publish(userEventFactory.balanceUpdate(entry.userId, entry.data()));
            }
        });

        log.info("[clearing] 撤单结算 orderId={} symbol={} reason={} 解冻={}",
                orderId, ctx.displaySymbol(), event.getReason(), remainder);
    }

    // ------------------------------------------------------------------ 内部实现

    /** 交易对上下文：符号 + 双边币种 ID/符号/精度 */
    private record PairContext(Long pairId, Long baseCurrencyId, Long quoteCurrencyId,
                               String baseSymbol, String quoteSymbol,
                               int baseDecimals, int quoteDecimals) {

        String displaySymbol() {
            return baseSymbol + "/" + quoteSymbol;
        }
    }

    /**
     * 余额变动流水：同一 (用户, 币种) 的多次变动聚合为一条，
     * 结算结束后回查最终 available/locked，避免连发多条中间态推送。
     */
    private static final class BalanceLedger {

        private final Map<String, Entry> entries = new LinkedHashMap<>();

        void record(Long userId, Long currencyId, String symbol, int decimals,
                    BigDecimal availableDelta, String reason) {
            Entry entry = entries.computeIfAbsent(userId + ":" + currencyId,
                    k -> new Entry(userId, currencyId, symbol, decimals, reason));
            entry.availableDelta = entry.availableDelta.add(availableDelta);
        }

        /** 结算完成后回查最终余额（同一事务内可看到未提交改动） */
        void finalize(ISpotBalanceService balanceService) {
            for (Entry entry : entries.values()) {
                SpotBalance wallet = balanceService.getByUserAndCurrency(entry.userId, entry.currencyId);
                entry.available = wallet == null || wallet.getBalance() == null
                        ? BigDecimal.ZERO : wallet.getBalance();
                entry.locked = wallet == null || wallet.getFrozenBalance() == null
                        ? BigDecimal.ZERO : wallet.getFrozenBalance();
            }
        }

        List<Entry> entries() {
            return new ArrayList<>(entries.values());
        }

        private static final class Entry {

            private final Long userId;
            private final Long currencyId;
            private final String symbol;
            private final int decimals;
            private final String reason;
            private BigDecimal availableDelta = BigDecimal.ZERO;
            private BigDecimal available = BigDecimal.ZERO;
            private BigDecimal locked = BigDecimal.ZERO;

            private Entry(Long userId, Long currencyId, String symbol, int decimals, String reason) {
                this.userId = userId;
                this.currencyId = currencyId;
                this.symbol = symbol;
                this.decimals = decimals;
                this.reason = reason;
            }

            private BalanceUpdateData data() {
                return BalanceUpdateData.builder()
                        .asset(symbol)
                        .available(AmountCodec.toMinimal(available, decimals))
                        .locked(AmountCodec.toMinimal(locked, decimals))
                        .change(AmountCodec.toMinimal(availableDelta, decimals))
                        .reason(reason)
                        .build();
            }
        }
    }

    private PairContext resolvePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "symbol is required in clearing event");
        }
        TradingPair pair = tradingPairMapper.selectBySymbolAny(symbol.trim().toUpperCase().replace("/", ""));
        if (pair == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "trading pair not found: " + symbol);
        }
        Currency base = currencyMapper.selectById(pair.getBaseCurrencyId());
        Currency quote = currencyMapper.selectById(pair.getQuoteCurrencyId());
        if (base == null || quote == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "pair currency missing: " + symbol);
        }
        return new PairContext(pair.getId(), base.getId(), quote.getId(), base.getSymbol(), quote.getSymbol(),
                base.getDecimals() == null ? 18 : base.getDecimals(),
                quote.getDecimals() == null ? 18 : quote.getDecimals());
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 是否全部成交：按委托数量判断，市价买单按报价金额判断 */
    private boolean isFilled(Order order, BigDecimal filledQty, BigDecimal filledQuote) {
        if (order.getOriginalQty() != null && order.getOriginalQty().signum() > 0) {
            return order.getOriginalQty().subtract(filledQty).signum() <= 0;
        }
        if (order.getQuoteQty() != null && order.getQuoteQty().signum() > 0) {
            return order.getQuoteQty().subtract(filledQuote).signum() <= 0;
        }
        return false;
    }

    /** 把本次成交累加到内存态订单，供解冻剩余与事件载荷使用 */
    private void applyInMemory(Order order, BigDecimal qty, BigDecimal quoteAmount,
                               BigDecimal fee, boolean filled) {
        BigDecimal filledQty = nz(order.getExecutedQty()).add(qty);
        BigDecimal filledQuote = nz(order.getExecutedQuote()).add(quoteAmount);
        order.setExecutedQty(filledQty);
        order.setExecutedQuote(filledQuote);
        order.setFee(nz(order.getFee()).add(fee));
        if (filledQty.signum() > 0) {
            order.setAvgFillPrice(filledQuote.divide(filledQty, 18, RoundingMode.HALF_UP));
        }
        order.setStatus(filled ? OrderStatus.FILLED.name() : OrderStatus.PARTIALLY_FILLED.name());
        order.setUpdatedAt(Instant.now());
    }

    /** 终态订单解冻剩余冻结：剩余 = 冻结量 - 已花费（买单按成交金额、卖单按成交数量） */
    private void releaseRemainder(Order order, boolean filled, BalanceLedger ledger) {
        if (!filled) {
            return;
        }
        BigDecimal remainder = remainder(order);
        if (remainder.signum() <= 0 || order.getFrozenCurrencyId() == null) {
            return;
        }
        spotBalanceService.unfreeze(order.getUserId(), order.getFrozenCurrencyId(), remainder);
        Currency currency = currencyMapper.selectById(order.getFrozenCurrencyId());
        if (currency == null) {
            return;
        }
        ledger.record(order.getUserId(), currency.getId(), currency.getSymbol(),
                currency.getDecimals() == null ? 18 : currency.getDecimals(), remainder, "ORDER_UNFREEZE");
    }

    private BigDecimal remainder(Order order) {
        BigDecimal frozen = nz(order.getFrozenAmount());
        BigDecimal consumed = OrderSide.SELL.name().equals(order.getSide())
                ? nz(order.getExecutedQty()) : nz(order.getExecutedQuote());
        return frozen.subtract(consumed).max(BigDecimal.ZERO);
    }

    private String nextTradeId() {
        LocalDate bizDate = LocalDate.now(ZoneOffset.UTC);
        long seq = spotSerialMapper.nextSerial(bizDate, BIZ_TYPE_TRADE);
        return "t_" + TRADE_ID_DATE.format(bizDate) + String.format("%08d", seq);
    }

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    private OrderUpdateData orderUpdateData(Order order, PairContext ctx) {
        int feeDecimals = OrderSide.BUY.name().equals(order.getSide())
                ? ctx.baseDecimals() : ctx.quoteDecimals();
        return OrderUpdateData.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus())
                .filledQuantity(AmountCodec.toMinimal(nz(order.getExecutedQty()), ctx.baseDecimals()))
                .avgPrice(order.getAvgFillPrice() == null
                        ? "" : AmountCodec.toMinimal(order.getAvgFillPrice(), ctx.quoteDecimals()))
                .fee(AmountCodec.toMinimal(nz(order.getFee()), feeDecimals))
                .timestamp(order.getUpdatedAt() == null
                        ? Instant.now().toEpochMilli() : order.getUpdatedAt().toEpochMilli())
                .build();
    }

    /** @param isBuyer 当前用户是否为买方（决定手续费币种：买方基础币、卖方计价币） */
    private TradeUpdateData tradeUpdateData(String tradeId, String orderId, String symbol, BigDecimal price,
                                            BigDecimal qty, boolean isMaker, BigDecimal fee,
                                            boolean isBuyer, PairContext ctx, LocalDateTime executedAt) {
        int feeDecimals = isBuyer ? ctx.baseDecimals() : ctx.quoteDecimals();
        return TradeUpdateData.builder()
                .tradeId(tradeId)
                .orderId(orderId)
                .symbol(symbol)
                .price(AmountCodec.toMinimal(price, ctx.quoteDecimals()))
                .quantity(AmountCodec.toMinimal(qty, ctx.baseDecimals()))
                .isMaker(isMaker)
                .fee(AmountCodec.toMinimal(fee, feeDecimals))
                .timestamp(executedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }
}
