package com.openexchange.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.wallet.entity.AssetTotalSupply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * <p>
 * 全局资产发行量 Mapper 接口
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Mapper
public interface AssetTotalSupplyMapper extends BaseMapper<AssetTotalSupply> {

    /**
     * 原子累加全局资产发行量：
     * 若该资产尚无记录则插入初始值，已存在则在 SQL 层执行 total_supply = total_supply + amount（PostgreSQL upsert），
     * 保证并发入账时的累加原子性。
     *
     * @param currencyId 资产 ID
     * @param amount     本次累加金额（人可读单位）
     * @return 受影响行数
     */
    int addTotalSupply(@Param("currencyId") Long currencyId, @Param("amount") BigDecimal amount);
}
