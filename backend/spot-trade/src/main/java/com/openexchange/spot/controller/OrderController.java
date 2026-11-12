package com.openexchange.spot.controller;

import com.openexchange.spot.dto.OrderCancelRequest;
import com.openexchange.spot.dto.OrderCreateRequest;
import com.openexchange.spot.dto.OrderVO;
import com.openexchange.spot.security.CurrentUserId;
import com.openexchange.spot.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 现货订单接口：下单 / 撤单 / 查单。
 *
 * <p>下单与撤单均为「受理」语义：接口返回即表示参数校验、资产冻结（下单）与命令投递已完成，
 * 撮合由引擎异步执行，订单状态变动（成交/撤销/拒绝）通过 WS /ws/user 推送，
 * 客户端也可用 GET 轮询兜底。</p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Tag(name = "现货订单", description = "下单 / 撤单 / 查单")
public class OrderController {

    private final IOrderService orderService;

    @Operation(summary = "下单",
            description = "LIMIT/MARKET × BUY/SELL × GTC/IOC/FOK；金额均为最小单位整数字符串。"
                    + "同一 clientOrderId 重复提交返回同一订单（幂等）。余额不足返回 60001。")
    @PostMapping
    public ApiResponse<OrderVO> create(@CurrentUserId Long userId, @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.submitOrder(userId, request));
    }

    @Operation(summary = "撤单",
            description = "仅能撤销本人且处于 NEW / PARTIALLY_FILLED 的订单（否则 60004）。"
                    + "参数支持 Body 或 Query 二选一（Query 参数仅在未提供 Body 时生效）。"
                    + "返回 status=CANCELED 表示撤单已受理，最终状态以 WS 推送为准。")
    @DeleteMapping
    public ApiResponse<OrderVO> cancel(@CurrentUserId Long userId,
                                       @RequestBody(required = false) OrderCancelRequest request,
                                       @RequestParam(required = false) String symbol,
                                       @RequestParam(required = false) String orderId,
                                       @RequestParam(required = false) String clientOrderId) {
        OrderCancelRequest req = request != null ? request : new OrderCancelRequest();
        if (req.getSymbol() == null) {
            req.setSymbol(symbol);
        }
        if (req.getOrderId() == null) {
            req.setOrderId(orderId);
        }
        if (req.getClientOrderId() == null) {
            req.setClientOrderId(clientOrderId);
        }
        return ApiResponse.success(orderService.cancelOrder(userId, req));
    }

    @Operation(summary = "查订单",
            description = "orderId 与 clientOrderId 二选一；强制校验归属，防止越权查询他人订单")
    @GetMapping
    public ApiResponse<OrderVO> get(@CurrentUserId Long userId,
                                    @RequestParam(required = false) String orderId,
                                    @RequestParam(required = false) String clientOrderId) {
        return ApiResponse.success(orderService.getOrder(userId, orderId, clientOrderId));
    }
}
