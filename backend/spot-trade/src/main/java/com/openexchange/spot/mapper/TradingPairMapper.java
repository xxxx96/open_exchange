package com.openexchange.spot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.spot.entity.TradingPair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Mapper
public interface TradingPairMapper extends BaseMapper<TradingPair> {

    /**
     * 按符号查询交易对（忽略大小写与斜杠：BTCUSDT / btc/usdt / BTC/USDT 均可命中）
     */
    TradingPair selectBySymbolAny(@Param("symbol") String normalizedSymbol);
}
