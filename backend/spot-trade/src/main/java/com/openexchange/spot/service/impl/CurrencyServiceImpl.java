package com.openexchange.spot.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.mapper.CurrencyMapper;
import com.openexchange.spot.service.ICurrencyService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 币种字典服务实现：现货与钱包共享 currencies 表，按 symbol 解析唯一资产行。
 */
@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements ICurrencyService {

    private final CurrencyMapper currencyMapper;

    @Override
    public Currency resolveBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "asset is required");
        }
        Currency currency = currencyMapper.selectBySymbol(symbol.trim().toUpperCase());
        if (currency == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "asset not found: " + symbol);
        }
        return currency;
    }

    @Override
    public Currency getById(Long id) {
        Currency currency = currencyMapper.selectById(id);
        if (currency == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "currency not found: " + id);
        }
        return currency;
    }
}
