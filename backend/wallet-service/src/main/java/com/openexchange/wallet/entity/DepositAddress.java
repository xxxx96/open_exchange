package com.openexchange.wallet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openexchange.common.web.entity.BaseEntity;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Getter
@Setter
@ToString
@TableName("deposit_address")
@Schema(name = "DepositAddress", description = "")
public class DepositAddress extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("currency_id")
    private Long currencyId;

    @TableField("user_id")
    private Long userId;

    @TableField("derivation_path")
    private String derivationPath;

    @TableField("address")
    private String address;
}
