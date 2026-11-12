package com.openexchange.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.wallet.dto.DepositCallbackRequest;
import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.entity.Deposit;
import com.openexchange.wallet.mapper.AssetTotalSupplyMapper;
import com.openexchange.wallet.mapper.DepositMapper;
import com.openexchange.wallet.mapper.WalletMapper;
import com.openexchange.wallet.service.ICurrencyService;
import com.openexchange.wallet.service.IDepositService;
import com.openexchange.wallet.service.IWalletService;
import com.openexchange.wallet.config.WalletNotifyProperties;
import com.openexchange.wallet.event.DepositCreditedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 充值入账服务实现：处理链上监听服务的充值到账回调。
 * 幂等键 = chain + txHash + logIndex（持久化为 currency_id + tx_hash + log_index 唯一索引），
 * 入账（充值日志 / 用户余额 / 全局发行量）在同一个数据库事务内完成。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl extends ServiceImpl<DepositMapper, Deposit> implements IDepositService {

    /**
     * 充值日志状态：已入账
     */
    private static final String STATUS_CREDITED = "CREDITED";

    private final ICurrencyService currencyService;
    private final IWalletService walletService;
    private final WalletMapper walletMapper;
    private final AssetTotalSupplyMapper assetTotalSupplyMapper;
    /** 充值到账消息生产者：入账事务提交后向 Kafka 发送通知消息 */
    private final KafkaTemplate<String, String> kafkaTemplate;
    /** 消息体 JSON 序列化器 */
    private final ObjectMapper objectMapper;
    /** 消息通知配置（充值到账主题名） */
    private final WalletNotifyProperties notifyProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDepositCallback(DepositCallbackRequest request) {
        // 1. 参数校验：幂等键三元组与金额缺一不可
        if (request == null || request.getUserId() == null || request.getChain() == null
                || request.getAsset() == null || request.getTxHash() == null
                || request.getLogIndex() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "userId/chain/asset/txHash/logIndex are required");
        }
        Currency currency = currencyService.resolveCurrency(request.getChain(), request.getAsset());
        BigDecimal amount = parseMinimalUnitAmount(request.getAmount(), currency.getDecimals());

        // 2. 幂等检查：chain + txHash + logIndex 已处理过则直接返回成功（不重复入账）
        long processed = count(Wrappers.<Deposit>lambdaQuery()
                .eq(Deposit::getCurrencyId, currency.getId())
                .eq(Deposit::getTxHash, request.getTxHash())
                .eq(Deposit::getLogIndex, request.getLogIndex().longValue()));
        if (processed > 0) {
            return;
        }

        // 3. 同一事务内完成入账（任一步失败整体回滚）：
        //    3.1 更新用户余额 available += amount（SQL 层原子累加）
        walletService.ensureSpotWallet(request.getUserId(), currency.getId());
        int rows = walletMapper.creditBalance(request.getUserId(), currency.getId(), amount);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.WALLET_NOT_FOUND,
                    "spot wallet not found for user " + request.getUserId());
        }
        //    3.2 更新全局资产发行量 total_supply += amount（upsert 原子累加）
        assetTotalSupplyMapper.addTotalSupply(currency.getId(), amount);
        //    3.3 插入充值日志（状态 credited）
        Deposit deposit = new Deposit();
        deposit.setUserId(request.getUserId());
        deposit.setCurrencyId(currency.getId());
        deposit.setTxHash(request.getTxHash());
        deposit.setLogIndex(request.getLogIndex().longValue());
        deposit.setFromAddress(request.getFromAddress());
        deposit.setToAddress(request.getToAddress());
        deposit.setAmount(amount);
        deposit.setFee(BigDecimal.ZERO);
        deposit.setConfirmations(currency.getDepositConfirm());
        deposit.setRequiredConfirm(currency.getDepositConfirm());
        deposit.setStatus(STATUS_CREDITED);
        deposit.setCreditedAt(LocalDateTime.now());
        save(deposit);

        // 4. 事务提交成功后发送 Kafka 充值到账消息（异步通知下游 WebSocket/推送），
        //    避免通知先于入账对外可见；消息投递为 at-least-once，重复通知无害
        //    （账本入账的幂等性已在前置步骤由唯一索引保证）
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendDepositCreditedMessage(request.getUserId(), currency.getSymbol(), amount, request.getTxHash());
                }
            });
        }
    }

    /**
     * 发送充值到账 Kafka 消息：key 取 userId（同用户消息进入同一分区，保证消费端按用户顺序推送），
     * 消息体为 DepositCreditedEvent 的 JSON 序列化结果；
     * 发送失败仅记录日志、不抛异常——不影响已提交的账本事务，下游可通过幂等重试/对账补偿。
     */
    private void sendDepositCreditedMessage(Long userId, String asset, BigDecimal amount, String txHash) {
        try {
            String payload = objectMapper.writeValueAsString(new DepositCreditedEvent(userId, asset, amount, txHash));
            kafkaTemplate.send(notifyProperties.getDepositTopic(), String.valueOf(userId), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[deposit-notify] Kafka 发送失败 userId={} txHash={}: {}",
                                    userId, txHash, ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("[deposit-notify] 消息序列化失败 txHash={}: {}", txHash, e.getMessage());
        }
    }

    /**
     * 解析最小单位金额字符串为人可读金额：amount / 10^decimals，如 "1000000" + decimals=6 → 1 USDT
     */
    private BigDecimal parseMinimalUnitAmount(String amount, Integer decimals) {
        if (amount == null || amount.isBlank()) {
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
}
