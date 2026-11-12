package com.openexchange.spot.service;

import com.openexchange.spot.event.OrderCanceledEvent;
import com.openexchange.spot.event.TradeSettledEvent;

/**
 * 清算服务：撮合引擎事件（成交 / 撤单完成）驱动账本结算。
 *
 * <p>与下单/撤单的「受理」语义配套：REST 只负责冻结与投递命令，真正的资产划转
 * 在这里按引擎确认的结果执行。所有方法幂等（自然键去重 + 状态 CAS），
 * 可安全接收 at-least-once 投递。</p>
 */
public interface IClearingService {

    /**
     * 成交清算：落成交明细 → CAS 回写双边订单 → 结算双边余额 → 解冻剩余 → 推送用户事件。
     */
    void onTradeSettled(TradeSettledEvent event);

    /**
     * 撤单清算：订单置 CANCELED → 解冻剩余冻结资产 → 推送用户事件。
     */
    void onOrderCanceled(OrderCanceledEvent event);
}
