package com.openexchange.spot.controller;

import com.openexchange.spot.dto.BalanceVO;
import com.openexchange.spot.security.CurrentUserId;
import com.openexchange.spot.service.ISpotBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易账户余额接口。
 *
 * <p>注意：这里是<b>交易账户</b>余额，不是资金账户。充值/提现在资金账户完成，
 * 需先内部划转（资金账户 → 交易账户）才能用于下单。数据源为数据库账本
 * （wallets 表 wallet_type='SPOT' 行），不做任何链上查询，total = available + locked。</p>
 *
 * @author yanglei
 * @since 2026-11-12
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Tag(name = "现货账户", description = "交易账户余额查询")
public class SpotBalanceController {

    private final ISpotBalanceService spotBalanceService;

    @Operation(summary = "交易账户余额",
            description = "账本权威，直接读库；金额为最小单位整数字符串。"
                    + "asset 不传返回全部非零资产，传入则只返回该资产。")
    @GetMapping("/balance")
    public ApiResponse<List<BalanceVO>> balance(@CurrentUserId Long userId,
                                                @RequestParam(required = false) String asset) {
        return ApiResponse.success(spotBalanceService.listBalances(userId, asset));
    }
}
