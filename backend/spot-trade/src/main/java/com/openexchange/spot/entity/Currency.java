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
 * 币种字典：平台支持的所有链上资产定义（与 wallet-service 共享 currencies 表）。
 * 同一符号在不同链上是多行记录（如 USDT 与 USDT_TRC20），
 * 现货交易按「symbol」解析到平台唯一交易资产行。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@Getter
@Setter
@ToString
@TableName("currencies")
@Schema(name = "Currency", description = "币种字典")
public class Currency extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 精度：最小单位换算基数（USDT=6，ETH=18），接口金额换算依据
     */
    @Schema(description = "精度：最小单位换算基数")
    @TableField("decimals")
    private Integer decimals;

    /**
     * 资产符号，全局唯一：BTC / ETH / USDT / USDT_TRC20
     */
    @Schema(description = "资产符号")
    @TableField("symbol")
    private String symbol;

    /**
     * 币种状态：ACTIVE（启用）/ SUSPENDED（暂停）
     */
    @Schema(description = "币种状态")
    @TableField("status")
    private String status;
}
