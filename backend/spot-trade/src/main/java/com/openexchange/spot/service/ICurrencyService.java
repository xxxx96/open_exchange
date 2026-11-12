package com.openexchange.spot.service;

import com.openexchange.spot.entity.Currency;

/**
 * 币种字典服务：按符号解析资产（decimals / status）。
 */
public interface ICurrencyService {

    /**
     * 按符号解析币种；不存在抛 RESOURCE_NOT_FOUND
     */
    Currency resolveBySymbol(String symbol);

    /**
     * 按主键查询；不存在抛 RESOURCE_NOT_FOUND
     */
    Currency getById(Long id);
}
