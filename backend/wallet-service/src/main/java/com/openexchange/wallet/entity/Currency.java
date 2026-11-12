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
 * 币种字典：平台支持的所有链上资产定义。
 * 同一符号在不同链上是多行记录（如 USDT 与 USDT_TRC20），
 * 充值/提现/余额查询均以「chain + symbol」或「symbol」解析到唯一行。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("currencies")
@Schema(name = "Currency", description = "币种字典：链上资产定义（同符号不同链为多行）")
public class Currency extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 精度：最小单位换算基数（USDT=6，ETH=18），前端展示需除以 10^decimals
     */
    @Schema(description = "精度：最小单位换算基数（USDT=6，ETH=18）")
    @TableField("decimals")
    private Integer decimals;

    /**
     * 充值到账所需区块确认数（确认数达标后才入账，防双花/链回滚）
     */
    @Schema(description = "充值到账所需区块确认数，确认数达标后才入账")
    @TableField("deposit_confirm")
    private Integer depositConfirm;

    /**
     * 单次最小提现数量（低于该值拒绝提现）
     */
    @Schema(description = "单次最小提现数量，低于该值拒绝提现")
    @TableField("min_withdraw")
    private BigDecimal minWithdraw;

    /**
     * 提现手续费（固定值，按币种计）
     */
    @Schema(description = "提现手续费（固定值，按币种计）")
    @TableField("withdraw_fee")
    private BigDecimal withdrawFee;

    /**
     * 资产符号，全局唯一：BTC / ETH / USDT / USDT_TRC20
     */
    @Schema(description = "资产符号，全局唯一：BTC / ETH / USDT / USDT_TRC20")
    @TableField("symbol")
    private String symbol;

    /**
     * 代币合约地址（42 位 = 0x + 40 位十六进制；原生币如 ETH/BTC 为空）
     */
    @Schema(description = "代币合约地址（原生币为空）")
    @TableField("contract_addr")
    private String contractAddr;

    /**
     * 所属链的规范名：Bitcoin / Ethereum / TRON / BSC（地址池按该值取址）
     */
    @Schema(description = "所属链的规范名：Bitcoin / Ethereum / TRON / BSC")
    @TableField("chain")
    private String chain;

    /**
     * 资产全称，如 Tether USD，用于前端展示
     */
    @Schema(description = "资产全称，如 Tether USD")
    @TableField("full_name")
    private String fullName;

    /**
     * 币种状态：ACTIVE（启用，可充提）/ SUSPENDED（暂停）
     */
    @Schema(description = "币种状态：ACTIVE（启用）/ SUSPENDED（暂停）")
    @TableField("status")
    private String status;
}
