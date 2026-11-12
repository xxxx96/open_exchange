package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.spot.cache.SpotDictionaryCache;
import com.openexchange.spot.dto.ResolvedPair;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.entity.TradingPair;
import com.openexchange.spot.mapper.TradingPairMapper;
import com.openexchange.spot.service.ICurrencyService;
import com.openexchange.spot.service.ITradingPairService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 交易对服务实现：符号在 SQL 层归一化匹配，展示符号由 base/quote 币种符号拼装，
 * 与库中紧凑符号解耦（BTCUSDT ⇄ BTC/USDT）。
 *
 * <p>解析结果（交易对 + 双边币种）整体缓存在 Redis（spot:dict:pair:*，默认 60 秒），
 * 下单热路径不再每单读 3~4 次库；Redis 异常自动降级为直接查库。</p>
 */
@Service
@RequiredArgsConstructor
public class TradingPairServiceImpl extends ServiceImpl<TradingPairMapper, TradingPair> implements ITradingPairService {

    private final TradingPairMapper tradingPairMapper;
    private final ICurrencyService currencyService;
    private final SpotDictionaryCache dictionaryCache;

    @Override
    public ResolvedPair resolveBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "symbol is required");
        }
        String normalized = symbol.trim().toUpperCase().replace("/", "");
        ResolvedPair resolved = dictionaryCache.get("pair:symbol:" + normalized, ResolvedPair.class, () -> {
            TradingPair pair = tradingPairMapper.selectBySymbolAny(normalized);
            // 不存在不缓存；下线交易对在 build 中抛 60007，也不会写入缓存
            return pair == null ? null : build(pair);
        });
        if (resolved == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "trading pair not found: " + symbol);
        }
        return resolved;
    }

    @Override
    public ResolvedPair resolveById(Long pairId) {
        // 纯解析：不校验上线状态。撤单/查单需要能展示已下线交易对的历史订单
        ResolvedPair resolved = dictionaryCache.get("pair:id:" + pairId, ResolvedPair.class, () -> {
            TradingPair pair = getById(pairId);
            return pair == null ? null : build(pair);
        });
        if (resolved == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "trading pair not found: " + pairId);
        }
        return resolved;
    }

    private ResolvedPair build(TradingPair pair) {
        // 状态校验：种子数据为 ACTIVE，兼容 ONLINE 写法；其余状态一律不可交易
        String status = pair.getStatus();
        boolean online = status != null && (status.equalsIgnoreCase("ACTIVE") || status.equalsIgnoreCase("ONLINE"));
        if (!online) {
            throw new BusinessException(ErrorCode.TRADING_PAIR_OFFLINE, "trading pair offline: " + pair.getSymbol());
        }
        Currency base = currencyService.getById(pair.getBaseCurrencyId());
        Currency quote = currencyService.getById(pair.getQuoteCurrencyId());
        return ResolvedPair.builder()
                .pair(pair)
                .baseCurrency(base)
                .quoteCurrency(quote)
                .displaySymbol(base.getSymbol() + "/" + quote.getSymbol())
                .build();
    }
}
