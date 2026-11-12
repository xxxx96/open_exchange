package com.openexchange.spot.service;

import com.openexchange.spot.dto.OrderCancelRequest;
import com.openexchange.spot.dto.OrderCreateRequest;
import com.openexchange.spot.dto.OrderVO;

/**
 * 现货订单服务：下单（受理 + 冻结 + 投递撮合队列）、撤单（受理 + 投递撤单命令）、查单。
 * 下单/撤单均为「受理」语义：接口只保证资产冻结/校验与命令投递，撮合异步进行，
 * 最终状态经清算事件回写，客户端通过 WebSocket 获取推送。
 */
public interface IOrderService {

    /**
     * 下单：幂等 → 交易对/规则/风控校验 → 冻结资产 → 落库 NEW → 投递撮合队列
     *
     * @param userId  当前登录用户（网关注入的 X-User-Id）
     * @param request 下单请求（金额均为最小单位整数字符串）
     * @return 订单视图；同一 clientOrderId 重复提交返回同一订单
     */
    OrderVO submitOrder(Long userId, OrderCreateRequest request);

    /**
     * 撤单：校验归属与可撤状态 → 投递撤单命令到同一 symbol 队列（保证不早于下单被引擎处理）。
     * 返回的 status 为 CANCELED 表示「撤单已受理」，最终状态与解冻以清算事件为准。
     */
    OrderVO cancelOrder(Long userId, OrderCancelRequest request);

    /**
     * 查订单：orderId 与 clientOrderId 二选一，强制校验归属（防越权）
     */
    OrderVO getOrder(Long userId, String orderId, String clientOrderId);
}
