package com.openexchange.wallet.controller;

import com.openexchange.wallet.dto.DepositAddressResponse;
import com.openexchange.wallet.dto.DepositCallbackRequest;
import com.openexchange.wallet.service.IDepositAddressService;
import com.openexchange.wallet.service.IDepositService;
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
 * 充值接口：充值地址分配 与 链上充值到账回调。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@RestController
@RequestMapping("/api/v1/deposit")
@RequiredArgsConstructor
@Tag(name = "充值", description = "充值地址分配、充值到账回调")
public class DepositController {

    private final IDepositAddressService depositAddressService;
    private final IDepositService depositService;

    /**
     * 获取充值地址：为用户分配一个专属充值地址（同用户同链同资产固定不变）。
     * 内部逻辑：查 deposit_addresses 表（userId + chain + asset 对应的 currency_id），
     * 已存在则直接返回；否则从地址池（按 BIP44 预派生，状态 available）取一个未使用地址，
     * 插入分配记录并将地址池地址标记为已使用。
     */
    @Operation(summary = "获取充值地址",
            description = "为用户分配专属充值地址（同用户同链同资产固定不变）；首次分配从地址池取出未使用地址")
    @GetMapping("/address")
    public ApiResponse<DepositAddressResponse> getAddress(@RequestParam Long userId,
                                                          @RequestParam String chain,
                                                          @RequestParam String asset) {
        return ApiResponse.success(depositAddressService.getOrCreateDepositAddress(userId, chain, asset));
    }

    /**
     * 充值到账回调：链上监听服务发现充值交易后调用（内部接口）。
     * 内部逻辑：幂等键 = chain + txHash + logIndex，已处理过直接返回成功；
     * 首次回调在数据库事务内：插入充值日志（credited）→ 更新用户余额 → 更新全局发行量，
     * 事务提交后异步发送通知（WebSocket/推送）。
     */
    @Operation(summary = "充值到账回调",
            description = "内部接口：链上监听服务调用；幂等键 chain+txHash+logIndex，重复回调幂等返回成功")
    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestBody DepositCallbackRequest request) {
        depositService.handleDepositCallback(request);
        return ApiResponse.success();
    }
}
