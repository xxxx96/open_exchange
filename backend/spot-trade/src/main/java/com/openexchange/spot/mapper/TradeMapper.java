package com.openexchange.spot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.spot.dto.TradeRow;
import com.openexchange.spot.entity.Trade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 成交 Mapper：「我的成交」游标分页查询。
 */
@Mapper
public interface TradeMapper extends BaseMapper<Trade> {

    /**
     * 我的成交：按用户（maker 或 taker）+ 交易对/订单/时间范围过滤，按 id 倒序游标分页。
     */
    List<TradeRow> selectMyTrades(@Param("userId") Long userId,
                                  @Param("pairId") Long pairId,
                                  @Param("orderPkId") Long orderPkId,
                                  @Param("startTime") java.time.LocalDateTime startTime,
                                  @Param("endTime") java.time.LocalDateTime endTime,
                                  @Param("beforeId") Long beforeId,
                                  @Param("limit") int limit);

    /**
     * 游标定位：按对外成交号取内部主键（游标分页用）
     */
    Long selectIdByTradeId(@Param("tradeId") String tradeId);

    /**
     * 该交易对最近一笔成交价（风控偏离校验的参考价），无成交返回 null
     */
    java.math.BigDecimal selectLastPrice(@Param("pairId") Long pairId);

    /**
     * 成交自然键去重：撮合引擎 at-least-once 投递时防止重复结算
     */
    int countByNaturalKey(@Param("makerOrderId") Long makerOrderId,
                          @Param("takerOrderId") Long takerOrderId,
                          @Param("price") java.math.BigDecimal price,
                          @Param("qty") java.math.BigDecimal qty,
                          @Param("executedAt") java.time.LocalDateTime executedAt);
}
