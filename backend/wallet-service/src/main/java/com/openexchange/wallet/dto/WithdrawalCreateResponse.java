package com.openexchange.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * 发起提现响应体：仅返回提现单对外 ID，后续进度通过查询提现状态接口获取。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Data
@Builder
@Schema(name = "WithdrawalCreateResponse", description = "发起提现响应：返回提现单对外 ID")
public class WithdrawalCreateResponse {

    @Schema(description = "提现单对外 ID，格式 wd_日期_序号，可用作幂等返回值", example = "wd_20261106_001")
    private String withdrawalId;
}
