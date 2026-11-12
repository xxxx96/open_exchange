package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import com.openexchange.spot.config.SpotTradeProperties;
import com.openexchange.spot.dto.OrderCancelRequest;
import com.openexchange.spot.dto.OrderCreateRequest;
import com.openexchange.spot.dto.OrderVO;
import com.openexchange.spot.dto.ResolvedPair;
import com.openexchange.spot.entity.Order;
import com.openexchange.spot.enums.OrderSide;
import com.openexchange.spot.enums.OrderStatus;
import com.openexchange.spot.enums.OrderType;
import com.openexchange.spot.enums.TimeInForce;
import com.openexchange.spot.event.AfterCommitExecutor;
import com.openexchange.spot.event.MatchingQueueProducer;
import com.openexchange.spot.event.OrderCommand;
import com.openexchange.spot.event.OrderUpdateData;
import com.openexchange.spot.event.UserEventFactory;
import com.openexchange.spot.event.UserEventPublisher;
import com.openexchange.spot.mapper.OrderMapper;
import com.openexchange.spot.mapper.SpotSerialMapper;
import com.openexchange.spot.mapper.TradeMapper;
import com.openexchange.spot.service.IOrderService;
import com.openexchange.spot.service.IRiskControlService;
import com.openexchange.spot.service.ISpotBalanceService;
import com.openexchange.spot.service.ITradingPairService;
import com.openexchange.spot.util.AmountCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 订单服务实现。
 *
 * <p>下单链路：幂等查询 → 交易对解析 → 金额换算 → 规则/风控校验 → 冻结资产 → 落库 NEW
 * → 事务提交后投递撮合队列。撮合引擎异步处理命令，成交/撤单结果经清算事件回写，
 * 因此本服务只做「受理」，不直接改已成交数量。</p>
 *
 * <p>冻结金额口径：</p>
 * <ul>
 *   <li>限价买单：price × quantity（计价币）</li>
 *   <li>市价买单（quoteQuantity）：quoteQuantity（计价币）</li>
 *   <li>市价买单（quantity）：参考价 × quantity × (1 + 最大偏离)，向上取整兜底</li>
 *   <li>卖单：quantity（基础币）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final String BIZ_TYPE_ORDER = "ORDER";
    private static final DateTimeFormatter ORDER_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderMapper orderMapper;
    private final SpotSerialMapper spotSerialMapper;
    private final TradeMapper tradeMapper;
    private final ITradingPairService tradingPairService;
    private final IRiskControlService riskControlService;
    private final ISpotBalanceService spotBalanceService;
    private final MatchingQueueProducer matchingQueueProducer;
    private final UserEventPublisher userEventPublisher;
    private final UserEventFactory userEventFactory;
    private final AfterCommitExecutor afterCommitExecutor;
    private final SpotTradeProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO submitOrder(Long userId, OrderCreateRequest request) {
        OrderSide side = OrderSide.parse(request.getSide());
        OrderType type = OrderType.parse(request.getType());
        TimeInForce timeInForce = TimeInForce.parseOrDefault(request.getTimeInForce());
        String clientOrderId = requireClientOrderId(request.getClientOrderId());

        // ① 幂等：同一 clientOrderId 直接返回既有订单，不重复下单、不重复冻结
        Order existing = orderMapper.selectByClientOrderId(userId, clientOrderId);
        if (existing != null) {
            log.info("[order] 幂等命中 userId={} clientOrderId={} orderId={}", userId, clientOrderId, existing.getOrderId());
            return toVO(existing);
        }

        // ② 交易对解析：不存在 / 未上线直接拒绝
        ResolvedPair pair = tradingPairService.resolveBySymbol(request.getSymbol());

        // ③ 金额解析：对外契约是最小单位整数，落库前人可读单位
        BigDecimal price = parseOptional(request.getPrice(), pair.quoteDecimals(), "price");
        BigDecimal quantity = parseOptional(request.getQuantity(), pair.baseDecimals(), "quantity");
        BigDecimal quoteQty = parseOptional(request.getQuoteQuantity(), pair.quoteDecimals(), "quoteQuantity");
        validateAmounts(type, side, price, quantity, quoteQty);

        // ④ 参考价 + 交易规则 + 限额风控
        BigDecimal refPrice = tradeMapper.selectLastPrice(pair.pairId());
        riskControlService.validatePairRules(pair, side, type, price, quantity, quoteQty, refPrice);
        riskControlService.checkRiskLimits(userId, pair, side, type, price, quantity, quoteQty, refPrice);

        // ⑤ 冻结资产：余额不足抛 60001，随事务回滚，不会留下「已冻结无订单」的脏账
        FreezePlan freeze = planFreeze(pair, side, type, price, quantity, quoteQty, refPrice);
        spotBalanceService.freeze(userId, freeze.currencyId(), freeze.amount());
        // ⑥ 落库：状态 NEW，成交相关列清零
        Order order = new Order();
        order.setOrderId(nextOrderId());
        order.setClientOrderId(clientOrderId);
        order.setPairId(pair.pairId());
        order.setUserId(userId);
        order.setSide(side.name());
        order.setOrderType(type.name());
        order.setStatus(OrderStatus.NEW.name());
        order.setTimeInForce(timeInForce.name());
        order.setPrice(type == OrderType.LIMIT ? price : null);
        order.setOriginalQty(quantity);
        order.setQuoteQty(type == OrderType.MARKET && side == OrderSide.BUY && quoteQty != null ? quoteQty : null);
        order.setExecutedQty(BigDecimal.ZERO);
        order.setExecutedQuote(BigDecimal.ZERO);
        order.setFee(BigDecimal.ZERO);
        order.setFeeAsset(side == OrderSide.BUY ? pair.baseSymbol() : pair.quoteSymbol());
        // 记录冻结口径：清算时据此解冻剩余部分（市价买单按数量下单为预估上浮值）
        order.setFrozenAmount(freeze.amount());
        order.setFrozenCurrencyId(freeze.currencyId());
        save(order);

        // ⑦ 提交后再投递撮合队列：避免「消息已发但事务回滚」导致引擎持有不存在的订单
        afterCommitExecutor.run(() -> {
            matchingQueueProducer.publishOrderCommand(pair.displaySymbol(), orderCommand("NEW", order, pair));
            userEventPublisher.publish(userEventFactory.orderUpdate(userId, orderUpdateData(order, pair)));
        });

        log.info("[order] 下单受理 userId={} orderId={} {} {} {} qty={} price={}",
                userId, order.getOrderId(), pair.displaySymbol(), side, type,
                order.getOriginalQty(), order.getPrice());
        return toVO(order, pair.displaySymbol());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancelOrder(Long userId, OrderCancelRequest request) {
        Order order = loadOwnOrder(userId, request);

        // 仅 NEW / PARTIALLY_FILLED 可撤；已终态订单重复撤单直接拒绝（不重复投递命令）
        OrderStatus status = parseStatus(order.getStatus());
        if (status == null || !status.cancelable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE,
                    "order not cancelable in status " + order.getStatus());
        }

        ResolvedPair pair = tradingPairService.resolveById(order.getPairId());

        // 撤单只受理：命令进入同一 symbol 分区（key = symbol），保证不早于下单被引擎处理；
        // 订单状态置 CANCELED 与剩余资产解冻由引擎确认后的清算事件完成
        afterCommitExecutor.run(() -> matchingQueueProducer.publishOrderCommand(pair.displaySymbol(),
                orderCommand("CANCEL", order, pair)));

        log.info("[order] 撤单受理 userId={} orderId={} symbol={}", userId, order.getOrderId(), pair.displaySymbol());
        // 对外语义遵循行业惯例（Binance）：返回 CANCELED 表示撤单请求已受理
        return toVO(order, pair.displaySymbol(), OrderStatus.CANCELED.name());
    }

    @Override
    public OrderVO getOrder(Long userId, String orderId, String clientOrderId) {
        Order order;
        if (orderId != null && !orderId.isBlank()) {
            order = orderMapper.selectByOrderIdAndUser(userId, orderId.trim());
        } else if (clientOrderId != null && !clientOrderId.isBlank()) {
            order = orderMapper.selectByClientOrderId(userId, clientOrderId.trim());
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "orderId or clientOrderId is required");
        }
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "order not found");
        }
        return toVO(order);
    }

    // ------------------------------------------------------------------ 内部实现

    /** 冻结计划：冻结哪个币种、冻多少人可读数量 */
    private record FreezePlan(Long currencyId, BigDecimal amount) {
    }

    private FreezePlan planFreeze(ResolvedPair pair, OrderSide side, OrderType type,
                                  BigDecimal price, BigDecimal quantity, BigDecimal quoteQty, BigDecimal refPrice) {
        if (side == OrderSide.SELL) {
            // 卖单冻结基础币（要卖出去的币）
            return new FreezePlan(pair.getBaseCurrency().getId(), quantity);
        }
        // 买单冻结计价币（要花出去的币）
        BigDecimal quoteAmount;
        if (type == OrderType.LIMIT) {
            quoteAmount = price.multiply(quantity);
        } else if (quoteQty != null) {
            quoteAmount = quoteQty;
        } else {
            // 市价按数量买：以参考价 × (1 + 最大偏离) 向上取整兜底，
            // 多冻部分在成交/撤单后由清算解冻，避免真实成交价更高时冻结不足
            if (refPrice == null || refPrice.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "MARKET BUY by quantity requires quoteQuantity (no reference price for this pair yet)");
            }
            BigDecimal maxDeviation = properties.getRisk().getMaxPriceDeviation();
            BigDecimal factor = maxDeviation == null ? BigDecimal.ONE : BigDecimal.ONE.add(maxDeviation);
            quoteAmount = refPrice.multiply(quantity).multiply(factor)
                    .setScale(pair.quoteDecimals(), RoundingMode.CEILING);
        }
        return new FreezePlan(pair.getQuoteCurrency().getId(), quoteAmount);
    }

    private void validateAmounts(OrderType type, OrderSide side, BigDecimal price,
                                 BigDecimal quantity, BigDecimal quoteQty) {
        if (type == OrderType.LIMIT) {
            if (price == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "price is required for LIMIT order");
            }
            if (quantity == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "quantity is required for LIMIT order");
            }
        } else {
            if (side == OrderSide.SELL && quantity == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "quantity is required for MARKET SELL order");
            }
            if (side == OrderSide.BUY && quantity == null && quoteQty == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "quantity or quoteQuantity is required for MARKET BUY order");
            }
        }
        if (price != null && price.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "price must be positive");
        }
        if (quantity != null && quantity.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "quantity must be positive");
        }
        if (quoteQty != null && quoteQty.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "quoteQuantity must be positive");
        }
    }

    private String requireClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "clientOrderId is required");
        }
        return clientOrderId.trim();
    }

    private BigDecimal parseOptional(String value, int decimals, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AmountCodec.toHuman(value.trim(), decimals, field);
    }

    /** 取对外订单号：o_yyyyMMdd_8位序号（序号按 UTC 日原子自增） */
    private String nextOrderId() {
        LocalDate bizDate = LocalDate.now(ZoneOffset.UTC);
        long seq = spotSerialMapper.nextSerial(bizDate, BIZ_TYPE_ORDER);
        return "o_" + ORDER_ID_DATE.format(bizDate) + String.format("%08d", seq);
    }

    private Order loadOwnOrder(Long userId, OrderCancelRequest request) {
        Order order = null;
        if (request.getOrderId() != null && !request.getOrderId().isBlank()) {
            order = orderMapper.selectByOrderIdAndUser(userId, request.getOrderId().trim());
        } else if (request.getClientOrderId() != null && !request.getClientOrderId().isBlank()) {
            order = orderMapper.selectByClientOrderId(userId, request.getClientOrderId().trim());
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "orderId or clientOrderId is required");
        }
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "order not found");
        }
        return order;
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private OrderCommand orderCommand(String action, Order order, ResolvedPair pair) {
        return OrderCommand.builder()
                .action(action)
                .orderId(order.getOrderId())
                .clientOrderId(order.getClientOrderId())
                .userId(order.getUserId())
                .symbol(pair.displaySymbol())
                .side(order.getSide())
                .type(order.getOrderType())
                .price(order.getPrice() == null ? null : AmountCodec.toMinimal(order.getPrice(), pair.quoteDecimals()))
                .quantity(order.getOriginalQty() == null
                        ? null : AmountCodec.toMinimal(order.getOriginalQty(), pair.baseDecimals()))
                .quoteQuantity(order.getQuoteQty() == null
                        ? null : AmountCodec.toMinimal(order.getQuoteQty(), pair.quoteDecimals()))
                .timeInForce(order.getTimeInForce())
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }

    private OrderUpdateData orderUpdateData(Order order, ResolvedPair pair) {
        int feeDecimals = feeDecimals(pair, order);
        return OrderUpdateData.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus())
                .filledQuantity(AmountCodec.toMinimal(
                        order.getExecutedQty() == null ? BigDecimal.ZERO : order.getExecutedQty(), pair.baseDecimals()))
                .avgPrice(order.getAvgFillPrice() == null
                        ? "" : AmountCodec.toMinimal(order.getAvgFillPrice(), pair.quoteDecimals()))
                .fee(AmountCodec.toMinimal(
                        order.getFee() == null ? BigDecimal.ZERO : order.getFee(), feeDecimals))
                .timestamp(order.getUpdatedAt() == null
                        ? Instant.now().toEpochMilli() : order.getUpdatedAt().toEpochMilli())
                .build();
    }

    /** 手续费币种精度：买单收基础币，卖单收计价币 */
    private int feeDecimals(ResolvedPair pair, Order order) {
        return order.getSide() != null && OrderSide.BUY.name().equals(order.getSide())
                ? pair.baseDecimals() : pair.quoteDecimals();
    }

    private OrderVO toVO(Order order) {
        return toVO(order, tradingPairService.resolveById(order.getPairId()), order.getStatus());
    }

    private OrderVO toVO(Order order, String displaySymbol) {
        return toVO(order, tradingPairService.resolveById(order.getPairId()), order.getStatus(), displaySymbol);
    }

    private OrderVO toVO(Order order, String displaySymbol, String status) {
        return toVO(order, tradingPairService.resolveById(order.getPairId()), status, displaySymbol);
    }

    private OrderVO toVO(Order order, ResolvedPair pair, String status) {
        return toVO(order, pair, status, pair.displaySymbol());
    }

    private OrderVO toVO(Order order, ResolvedPair pair, String status, String displaySymbol) {
        int quoteDecimals = pair.quoteDecimals();
        int baseDecimals = pair.baseDecimals();
        BigDecimal executedQty = order.getExecutedQty() == null ? BigDecimal.ZERO : order.getExecutedQty();
        return OrderVO.builder()
                .orderId(order.getOrderId())
                .clientOrderId(order.getClientOrderId())
                .symbol(displaySymbol)
                .side(order.getSide())
                .type(order.getOrderType())
                .status(status)
                .price(order.getPrice() == null ? null : AmountCodec.toMinimal(order.getPrice(), quoteDecimals))
                .quantity(order.getOriginalQty() == null
                        ? null : AmountCodec.toMinimal(order.getOriginalQty(), baseDecimals))
                .filledQuantity(AmountCodec.toMinimal(executedQty, baseDecimals))
                .avgPrice(order.getAvgFillPrice() == null
                        ? null : AmountCodec.toMinimal(order.getAvgFillPrice(), quoteDecimals))
                .fee(AmountCodec.toMinimal(order.getFee() == null ? BigDecimal.ZERO : order.getFee(),
                        feeDecimals(pair, order)))
                .feeAsset(order.getFeeAsset())
                .createdAt(order.getCreatedAt() == null ? null : order.getCreatedAt().toEpochMilli())
                .updatedAt(order.getUpdatedAt() == null ? null : order.getUpdatedAt().toEpochMilli())
                .build();
    }
}
