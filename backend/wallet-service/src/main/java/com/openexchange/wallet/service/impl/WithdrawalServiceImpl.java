package com.openexchange.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.wallet.dto.WithdrawalCreateRequest;
import com.openexchange.wallet.dto.WithdrawalCreateResponse;
import com.openexchange.wallet.dto.WithdrawalStatusResponse;
import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.entity.Withdrawal;
import com.openexchange.wallet.mapper.WalletMapper;
import com.openexchange.wallet.mapper.WithdrawalMapper;
import com.openexchange.wallet.service.ICurrencyService;
import com.openexchange.wallet.service.IWalletService;
import com.openexchange.wallet.service.IWithdrawalService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Set;

/**
 * <p>
 * 提现服务实现：提现单创建（幂等 + 冻结余额）与状态查询（内部状态 → 对外状态映射）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl extends ServiceImpl<WithdrawalMapper, Withdrawal> implements IWithdrawalService {

    /**
     * 内部状态：已提交（进入风控/签名/广播/确认流水线前的初始状态）
     */
    private static final String STATUS_SUBMITTED = "SUBMITTED";

    /**
     * 内部状态：链上确认完成
     */
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    /**
     * 内部状态：失败（含风控拒绝、链上失败等）
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 映射为对外 pending 的内部中间状态集合
     */
    private static final Set<String> INTERNAL_PENDING = Set.of(
            STATUS_SUBMITTED, "RISK_CHECK", "SIGNING", "BROADCASTING");

    /**
     * 对外状态常量：仅暴露 pending / success / failed 三种
     */
    private static final String EXTERNAL_PENDING = "pending";
    private static final String EXTERNAL_SUCCESS = "success";
    private static final String EXTERNAL_FAILED = "failed";

    private final ICurrencyService currencyService;
    private final IWalletService walletService;
    private final WalletMapper walletMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawalCreateResponse createWithdrawal(WithdrawalCreateRequest request) {
        // 1. 参数校验：clientId 是幂等键，缺一不可
        if (request == null || request.getUserId() == null || request.getChain() == null
                || request.getAsset() == null || isBlank(request.getToAddress()) || isBlank(request.getClientId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "userId/chain/asset/toAddress/clientId are required");
        }
        Currency currency = currencyService.resolveCurrency(request.getChain(), request.getAsset());
        BigDecimal amount = parseMinimalUnitAmount(request.getAmount(), currency.getDecimals());
        if (currency.getMinWithdraw() != null && amount.compareTo(currency.getMinWithdraw()) < 0) {
            throw new BusinessException(ErrorCode.ORDER_QTY_TOO_SMALL,
                    "withdrawal amount below minimum " + currency.getMinWithdraw().toPlainString());
        }

        // 2. 幂等校验：clientId 持久化为 request_id（唯一索引），重复提交直接返回已有 withdrawalId
        Withdrawal existing = getOne(Wrappers.<Withdrawal>lambdaQuery()
                .eq(Withdrawal::getRequestId, request.getClientId()), false);
        if (existing != null) {
            return WithdrawalCreateResponse.builder().withdrawalId(toExternalId(existing)).build();
        }

        // 3. 校验并冻结余额：available >= amount + fee（fee 暂不冻结，广播时扣除）。
        //    冻结为单条原子 UPDATE（balance -= amount, frozen += amount, WHERE balance >= amount + fee），
        //    从数据库层面杜绝并发提现导致的超扣；影响行数为 0 即余额不足。
        BigDecimal fee = currency.getWithdrawFee() != null ? currency.getWithdrawFee() : BigDecimal.ZERO;
        walletService.ensureSpotWallet(request.getUserId(), currency.getId());
        int rows = walletMapper.freezeBalance(request.getUserId(), currency.getId(), amount, amount.add(fee));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 4. 创建提现单（状态 submitted）；后续异步流程：风控检查 → 签名 → 广播 → 确认
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUserId(request.getUserId());
        withdrawal.setCurrencyId(currency.getId());
        withdrawal.setRequestId(request.getClientId());
        withdrawal.setToAddress(request.getToAddress());
        withdrawal.setAmount(amount);
        withdrawal.setFee(fee);
        withdrawal.setActualAmount(amount.subtract(fee));
        withdrawal.setStatus(STATUS_SUBMITTED);
        save(withdrawal);

        // TODO: 接入异步任务/消息队列，触发后续流程（风控检查 → 签名 → 广播 → 确认）

        return WithdrawalCreateResponse.builder().withdrawalId(toExternalId(withdrawal)).build();
    }

    @Override
    public WithdrawalStatusResponse getWithdrawalStatus(String withdrawalId) {
        // 1. 对外单号反解为内部主键并查询
        Long id = parseExternalId(withdrawalId);
        Withdrawal withdrawal = getById(id);
        if (withdrawal == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "withdrawal " + withdrawalId + " not found");
        }
        // 2. 内部状态 → 对外状态：confirmed → success；failed → failed；其余中间状态统一 pending，
        //    不暴露内部细节（是否正在签名、gas 消耗等）
        String internal = withdrawal.getStatus() != null ? withdrawal.getStatus().trim().toUpperCase() : "";
        String external;
        if (STATUS_CONFIRMED.equals(internal)) {
            external = EXTERNAL_SUCCESS;
        } else if (STATUS_FAILED.equals(internal)) {
            external = EXTERNAL_FAILED;
        } else {
            external = EXTERNAL_PENDING;
        }
        return WithdrawalStatusResponse.builder()
                .withdrawalId(withdrawalId)
                .status(external)
                .txHash(withdrawal.getTxHash())
                .createdAt(withdrawal.getCreatedAt() != null ? withdrawal.getCreatedAt().toString() : null)
                .build();
    }

    /**
     * 内部主键 → 对外提现单号：wd_yyyyMMdd_序号（序号取自自增 id，零填充至 3 位，超过自然扩展）
     */
    private String toExternalId(Withdrawal withdrawal) {
        TemporalAccessor creationTime = withdrawal.getCreatedAt() != null
                ? withdrawal.getCreatedAt()
                : java.time.Instant.now();
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(creationTime);
        return String.format("wd_%s_%03d", date, withdrawal.getId());
    }

    /**
     * 对外提现单号 → 内部主键：取最后一段序号解析为 Long，格式非法时抛参数错误
     */
    private Long parseExternalId(String withdrawalId) {
        if (isBlank(withdrawalId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "withdrawalId is required");
        }
        int lastSeparator = withdrawalId.lastIndexOf('_');
        if (lastSeparator < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid withdrawalId format");
        }
        try {
            return Long.parseLong(withdrawalId.substring(lastSeparator + 1));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid withdrawalId format");
        }
    }

    /**
     * 解析最小单位金额字符串为人可读金额：amount / 10^decimals，如 "500000" + decimals=6 → 0.5 USDT
     */
    private BigDecimal parseMinimalUnitAmount(String amount, Integer decimals) {
        if (isBlank(amount)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "amount is required");
        }
        try {
            BigDecimal minimal = new BigDecimal(amount.trim());
            if (minimal.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "amount must be positive");
            }
            int scale = decimals != null ? decimals : 18;
            return minimal.movePointLeft(scale);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid amount format");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
