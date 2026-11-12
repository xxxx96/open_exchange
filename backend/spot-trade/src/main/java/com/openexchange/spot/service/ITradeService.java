package com.openexchange.spot.service;

import com.openexchange.spot.dto.TradePageVO;

/**
 * 「我的成交」查询服务：按当前用户视角（side / isMaker / fee）返回逐笔成交，
 * 时间倒序游标分页（cursor = 上一页最后一条 tradeId），避免深分页。
 */
public interface ITradeService {

    /**
     * 查询我的成交
     *
     * @param userId    当前用户
     * @param symbol    交易对过滤（可选），如 BTC/USDT
     * @param orderId   订单过滤（可选，对外订单号，且必须属于当前用户）
     * @param startTime 起始时间（epoch 毫秒，可选）
     * @param endTime   结束时间（epoch 毫秒，可选）
     * @param limit     每页条数（默认 50，最大 200）
     * @param cursor    游标：上一页返回的 nextCursor
     */
    TradePageVO myTrades(Long userId, String symbol, String orderId,
                         Long startTime, Long endTime, Integer limit, String cursor);
}
