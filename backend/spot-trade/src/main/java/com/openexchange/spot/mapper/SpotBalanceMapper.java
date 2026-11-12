package com.openexchange.spot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.spot.entity.SpotBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 现货钱包（wallets 表 SPOT 行）原子操作 Mapper：
 * 余额变动全部在 SQL 层完成，杜绝并发丢失更新与超扣。
 */
@Mapper
public interface SpotBalanceMapper extends BaseMapper<SpotBalance> {

    /**
     * 冻结：available -= amount，frozen += amount；余额不足时影响行数为 0
     */
    int freeze(@Param("userId") Long userId, @Param("currencyId") Long currencyId, @Param("amount") BigDecimal amount);

    /**
     * 解冻：frozen -= amount，available += amount（撤单退回）
     */
    int unfreeze(@Param("userId") Long userId, @Param("currencyId") Long currencyId, @Param("amount") BigDecimal amount);

    /**
     * 核销冻结：frozen -= amount（成交时冻结部分被真实花费，不退回 available）
     */
    int consumeFrozen(@Param("userId") Long userId, @Param("currencyId") Long currencyId, @Param("amount") BigDecimal amount);

    /**
     * 入账：available += amount（成交收到资产）
     */
    int credit(@Param("userId") Long userId, @Param("currencyId") Long currencyId, @Param("amount") BigDecimal amount);
}
