package com.openexchange.wallet.controller;

import com.openexchange.wallet.dto.BalanceResponse;
import com.openexchange.wallet.service.IWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 余额接口：查询用户某资产的可用余额与冻结余额。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@RestController
@RequestMapping("/api/v1/balance")
@RequiredArgsConstructor
@Tag(name = "余额", description = "用户资产余额查询")
public class BalanceController {

    private final IWalletService walletService;

    /**
     * 查询用户余额：读取本地账本（user_balance 对应 wallets 表），
     * total = available + frozen，直接返回最小单位整数，不做任何链上查询（账本权威）。
     * 前端展示时自行除以 decimals（如 USDT 为 6，则除以 1e6）。
     */
    @Operation(summary = "查询用户余额",
            description = "账本权威，直接读库返回可用/冻结/总余额（最小单位整数字符串），不做链上查询")
    @GetMapping
    public ApiResponse<BalanceResponse> balance(@RequestParam Long userId, @RequestParam String asset) {
        return ApiResponse.success(walletService.getBalance(userId, asset));
    }
}
