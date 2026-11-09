package com.openexchange.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.wallet.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * <p>
 * 用户钱包（余额账本）Mapper 接口
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * 原子入账：可用余额 balance = balance + amount。
     * 累加直接在 SQL 层完成，避免"查询-计算-回写"在并发下丢失更新。
     *
     * @param userId     用户 ID
     * @param currencyId 资产 ID
     * @param amount     入账金额（人可读单位，正数）
     * @return 受影响行数（0 表示该用户该资产的现货钱包不存在）
     */
    int creditBalance(@Param("userId") Long userId,
                      @Param("currencyId") Long currencyId,
                      @Param("amount") BigDecimal amount);

    /**
     * 原子冻结：可用余额 -= amount，冻结余额 += amount。
     * WHERE 中附带 balance >= required 条件，将「余额校验 + 冻结」合并为一次原子更新，
     * 从数据库层面杜绝并发提现导致的超扣。
     *
     * @param userId     用户 ID
     * @param currencyId 资产 ID
     * @param amount     本次冻结金额（人可读单位）
     * @param required   冻结前可用余额必须满足的下限（一般为 amount + fee）
     * @return 受影响行数（0 表示余额不足或钱包不存在）
     */
    int freezeBalance(@Param("userId") Long userId,
                      @Param("currencyId") Long currencyId,
                      @Param("amount") BigDecimal amount,
                      @Param("required") BigDecimal required);
}
