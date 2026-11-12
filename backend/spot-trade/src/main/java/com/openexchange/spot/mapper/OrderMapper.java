package com.openexchange.spot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.spot.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单 Mapper：幂等查询、单日限额统计等自定义 SQL。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 幂等查询：同一用户同一 clientOrderId 的既有订单（命中则直接返回，不重复下单）
     */
    Order selectByClientOrderId(@Param("userId") Long userId, @Param("clientOrderId") String clientOrderId);

    /**
     * 按对外订单号查询（限定用户，防越权）
     */
    Order selectByOrderIdAndUser(@Param("userId") Long userId, @Param("orderId") String orderId);

    /**
     * 按对外订单号查询（不限用户，供清算消费者按引擎事件定位订单）
     */
    Order selectByOrderId(@Param("orderId") String orderId);

    /**
     * 单日累计下单名义价值（计价币人可读单位）：
     * 限价单 = price × original_qty；带 quote_qty 的市价买单 = quote_qty；市价卖单取已成交金额。
     */
    BigDecimal sumTodayNotional(@Param("userId") Long userId, @Param("dayStart") Instant dayStart);

    /**
     * 成交回写：累加已成交数量/金额/手续费并按进度推进状态。
     * status 条件（仅 NEW / PARTIALLY_FILLED 可回写）即乐观并发控制，
     * 撮合引擎 at-least-once 投递时防止重复事件把状态改回去。
     *
     * @param filled true → FILLED（全部成交），false → PARTIALLY_FILLED
     * @return 影响行数，0 表示订单已终态（重复事件，调用方应跳过）
     */
    int applyFill(@Param("orderId") Long orderId,
                  @Param("executedQty") BigDecimal executedQty,
                  @Param("executedQuote") BigDecimal executedQuote,
                  @Param("fee") BigDecimal fee,
                  @Param("filled") boolean filled);

    /**
     * 撤销回写：状态置 CANCELED（仅 NEW / PARTIALLY_FILLED 可撤销）。
     *
     * @return 影响行数，0 表示订单已是终态（重复撤单事件，调用方应跳过）
     */
    int markCanceled(@Param("orderId") Long orderId);
}
