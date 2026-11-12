package com.openexchange.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 用户钱包（余额账本）：账本唯一权威数据源，余额查询直接读库、不做链上查询。
 * UNIQUE(user_id, currency_id, wallet_type) 保证一用户一资产一类型仅一个钱包，
 * 并兜底并发懒创建；余额变动通过 WalletMapper 的原子 SQL 完成。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("wallets")
@Schema(name = "Wallet", description = "用户钱包余额账本（账本权威数据源）")
public class Wallet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 可用余额（人可读单位；DECIMAL(32,18) 精度覆盖所有链的最小单位）
     */
    @Schema(description = "可用余额（人可读单位）")
    @TableField("balance")
    private BigDecimal balance;

    /**
     * 冻结余额（提现申请冻结、挂单占用等，不可使用）
     */
    @Schema(description = "冻结余额（提现冻结、挂单占用等，不可使用）")
    @TableField("frozen_balance")
    private BigDecimal frozenBalance;

    /**
     * 资产 ID（currencies 表主键）
     */
    @Schema(description = "资产 ID（currencies 表主键）")
    @TableField("currency_id")
    private Long currencyId;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    @TableField("user_id")
    private Long userId;

    /**
     * 乐观锁版本号：并发更新时防止丢失更新（MyBatis-Plus @Version）
     */
    @Schema(description = "乐观锁版本号，并发更新防丢失")
    @Version
    @TableField("version")
    private Long version;

    /**
     * 钱包类型：SPOT（现货），预留 FUNDING（资金）/ MARGIN（杠杆）
     */
    @Schema(description = "钱包类型：SPOT（现货），预留 FUNDING / MARGIN")
    @TableField("wallet_type")
    private WalletType walletType;

    public enum WalletType {
        SPOT,
        MARGIN,
        CONTRACT,
        EARN
    }
}
