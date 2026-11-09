package com.openexchange.wallet.entity;

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
 * 全局资产发行量表：充值入账时累加对应资产的 total_supply，
 * 作为账本层的全局资产统计，每行通过 currency_id 唯一对应一种资产。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("asset_total_supply")
@Schema(name = "AssetTotalSupply", description = "全局资产发行量：充值入账时累加，账本层全局资产统计")
public class AssetTotalSupply extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 资产 ID（currency 表主键），唯一索引保证每种资产一行
     */
    @Schema(description = "资产 ID（currencies 表主键）")
    @TableField("currency_id")
    private Long currencyId;

    /**
     * 累计发行量（人可读单位，非最小单位）
     */
    @Schema(description = "累计发行量（人可读单位）")
    @TableField("total_supply")
    private BigDecimal totalSupply;
}
