package com.openexchange.spot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 现货钱包（交易账户余额账本）：映射 wallets 表 wallet_type='SPOT' 行。
 * 现货下单冻结/成交结算/撤单解冻均在此账本上以原子 SQL 完成，
 * 与 wallet-service 的充值/提现货账本同表不同行（wallet_type 区分），
 * 内部划转（资金账户 → 交易账户）后续通过账本行间转账实现。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Getter
@Setter
@ToString
@TableName("wallets")
@Schema(name = "SpotBalance", description = "现货钱包（交易账户账本）")
public class SpotBalance extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 钱包类型：固定 SPOT（现货交易账户）
     */
    @Schema(description = "钱包类型：SPOT")
    @TableField("wallet_type")
    private String walletType;

    @TableField("balance")
    private BigDecimal balance;

    @TableField("frozen_balance")
    private BigDecimal frozenBalance;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("user_id")
    private Long userId;

    /**
     * 乐观锁版本号（MyBatis-Plus @Version）：本服务的余额变动全部走原子 SQL，
     * 不依赖实体级乐观锁，字段仅为兼容 wallets 表 NOT NULL 约束而存在。
     */
    @TableField("version")
    private Long version;
}
