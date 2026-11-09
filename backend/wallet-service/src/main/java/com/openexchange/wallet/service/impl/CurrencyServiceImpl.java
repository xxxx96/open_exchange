package com.openexchange.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.mapper.CurrencyMapper;
import com.openexchange.wallet.service.ICurrencyService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 币种服务实现：负责「链标识 + 资产符号」到 currencies 表唯一资产的解析，
 * 供充值地址分配、充值回调入账、提现等业务共用。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements ICurrencyService {

    /**
     * 链标识别名归一化：外部传入的链标识可能是 ETH/TRX/ERC20 等别名，
     * 统一映射到 currencies.chain 中的规范值（Ethereum/TRON 等）后再匹配。
     */
    private static final Map<String, String> CHAIN_ALIAS = Map.of(
            "ETH", "ETHEREUM", "ETHEREUM", "ETHEREUM", "ERC20", "ETHEREUM",
            "TRX", "TRON", "TRON", "TRON", "TRC20", "TRON",
            "BTC", "BITCOIN", "BITCOIN", "BITCOIN",
            "BSC", "BSC", "BNB", "BSC");

    /**
     * 资产启用状态（currencies.status）
     */
    private static final String STATUS_ACTIVE = "ACTIVE";

    @Override
    public Currency resolveCurrency(String chain, String asset) {
        // 1. 先按资产符号 + 启用状态筛出候选资产（同一符号可能存在多条链的映射，如 USDT / USDT_TRC20）
        List<Currency> candidates = list(Wrappers.<Currency>lambdaQuery()
                .eq(Currency::getSymbol, asset)
                .eq(Currency::getStatus, STATUS_ACTIVE));
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "unsupported asset " + asset);
        }
        // 2. 链标识归一化后精确匹配
        String normalizedChain = normalizeChain(chain);
        Currency matched = candidates.stream()
                .filter(c -> normalizeChain(c.getChain()).equals(normalizedChain))
                .findFirst()
                .orElse(null);
        // 3. 同一符号仅存在一个启用资产时放宽链匹配（兼容 BTC 等单链资产）
        if (matched == null && candidates.size() == 1) {
            matched = candidates.get(0);
        }
        if (matched == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "asset " + asset + " not found on chain " + chain);
        }
        return matched;
    }

    @Override
    public Currency resolveBySymbol(String asset) {
        if (asset == null || asset.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "asset is required");
        }
        // 余额查询仅按资产符号解析：优先精确匹配 symbol，账本以资产维度记账，不区分链
        Currency currency = getOne(Wrappers.<Currency>lambdaQuery()
                .eq(Currency::getSymbol, asset)
                .eq(Currency::getStatus, STATUS_ACTIVE), false);
        if (currency == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "unsupported asset " + asset);
        }
        return currency;
    }

    /**
     * 链标识归一化：别名映射 + 大写统一
     */
    private String normalizeChain(String chain) {
        if (chain == null || chain.isBlank()) {
            return "";
        }
        String upper = chain.trim().toUpperCase();
        return CHAIN_ALIAS.getOrDefault(upper, upper);
    }
}
