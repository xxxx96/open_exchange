package com.openexchange.spot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.spot.entity.Currency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 币种字典 Mapper：现货交易与 wallet-service 共享 currencies 表。
 */
@Mapper
public interface CurrencyMapper extends BaseMapper<Currency> {

    /**
     * 按符号精确解析币种（现货记账以资产维度，不区分链）
     */
    Currency selectBySymbol(@Param("symbol") String symbol);
}
