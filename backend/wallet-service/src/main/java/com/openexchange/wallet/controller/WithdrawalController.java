package com.openexchange.wallet.controller;

import com.openexchange.wallet.dto.WithdrawalCreateRequest;
import com.openexchange.wallet.dto.WithdrawalCreateResponse;
import com.openexchange.wallet.dto.WithdrawalStatusResponse;
import com.openexchange.wallet.service.IWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 提现接口：发起提现 与 提现状态查询。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@RestController
@RequestMapping("/api/v1/withdrawal")
@RequiredArgsConstructor
@Tag(name = "提现", description = "发起提现、提现状态查询")
public class WithdrawalController {

    private final IWithdrawalService withdrawalService;

    /**
     * 发起提现：用户申请将资产提现到外部地址。
     * 内部逻辑：clientId（客户端生成）作为幂等键去重，已存在直接返回已有 withdrawalId；
     * 校验 available >= amount + fee 后原子冻结（available -= amount, frozen += amount），
     * 插入 withdrawal 记录（状态 submitted），异步进入：风控检查 → 签名 → 广播 → 确认。
     */
    @Operation(summary = "发起提现",
            description = "clientId 由客户端生成，服务端据此去重防止重复提交；余额校验与冻结在同一原子操作中完成")
    @PostMapping("/create")
    public ApiResponse<WithdrawalCreateResponse> create(@RequestBody WithdrawalCreateRequest request) {
        return ApiResponse.success(withdrawalService.createWithdrawal(request));
    }

    /**
     * 查询提现状态：仅暴露 pending（submitted/risk_check/signing/broadcasting）、
     * success（confirmed）、failed（failed）三种对外状态，不暴露内部处理细节。
     */
    @Operation(summary = "查询提现状态",
            description = "对外仅暴露 pending/success/failed 三种状态，不暴露签名、gas 等内部细节")
    @GetMapping("/status")
    public ApiResponse<WithdrawalStatusResponse> status(@RequestParam String withdrawalId) {
        return ApiResponse.success(withdrawalService.getWithdrawalStatus(withdrawalId));
    }
}
