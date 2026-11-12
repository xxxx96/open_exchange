package com.openexchange.spot.controller;

import com.openexchange.spot.dto.TradePageVO;
import com.openexchange.spot.security.CurrentUserId;
import com.openexchange.spot.service.ITradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成交查询接口：「我的成交」。
 *
 * <p>side / isMaker / fee 均按当前登录用户推导——同一笔成交对挂单方与吃单方呈现不同的
 * 方向与手续费（maker/taker 费率不同）。时间倒序 + 游标分页，游标为上一页最后一条 tradeId，
 * 避免 offset 深分页。</p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Tag(name = "现货成交", description = "我的成交查询")
public class TradeController {

    private final ITradeService tradeService;

    @Operation(summary = "我的成交",
            description = "支持按交易对/订单/时间范围过滤；limit 默认 50 最大 200；"
                    + "cursor 传上一页返回的 nextCursor 翻页")
    @GetMapping("/my")
    public ApiResponse<TradePageVO> myTrades(@CurrentUserId Long userId,
                                             @RequestParam(required = false) String symbol,
                                             @RequestParam(required = false) String orderId,
                                             @RequestParam(required = false) Long startTime,
                                             @RequestParam(required = false) Long endTime,
                                             @RequestParam(required = false) Integer limit,
                                             @RequestParam(required = false) String cursor) {
        return ApiResponse.success(tradeService.myTrades(userId, symbol, orderId,
                startTime, endTime, limit, cursor));
    }
}
