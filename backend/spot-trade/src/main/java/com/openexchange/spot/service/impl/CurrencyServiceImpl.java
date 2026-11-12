package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.spot.cache.SpotDictionaryCache;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.mapper.CurrencyMapper;
import com.openexchange.spot.service.ICurrencyService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 币种字典服务实现：现货与钱包共享 currencies 表，按 symbol 解析唯一资产行。
 *
 * <p>币种是只读字典，按 id / symbol 双维度缓存到 Redis（spot:dict:currency:*，默认 60 秒），
 * 供交易对解析、成交列表、余额列表等热路径复用，避免重复查库。</p>
 */
@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements ICurrencyService {

    private final CurrencyMapper currencyMapper;
    private final SpotDictionaryCache dictionaryCache;

    @Override
    public Currency resolveBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "asset is required");
        }
        String normalized = symbol.trim().toUpperCase();
        Currency currency = dictionaryCache.get("currency:symbol:" + normalized, Currency.class,
                () -> currencyMapper.selectBySymbol(normalized));
        if (currency == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "asset not found: " + symbol);
        }
        return currency;
    }

    @Override
    public Currency getById(Long id) {
        Currency currency = dictionaryCache.get("currency:id:" + id, Currency.class,
                () -> currencyMapper.selectById(id));
        if (currency == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "currency not found: " + id);
        }
        return currency;
    }
}
