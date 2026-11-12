package com.openexchange.spot.service;

import com.openexchange.spot.dto.ResolvedPair;

/**
 * 交易对服务：符号归一化解析、上线状态校验、基础币/计价币解析。
 */
public interface ITradingPairService {

    /**
     * 按符号解析已上线交易对（不存在或未上线抛业务异常）
     */
    ResolvedPair resolveBySymbol(String symbol);

    /**
     * 按主键解析交易对（撤单/查单由 pair_id 反查符号，不校验上线状态）
     */
    ResolvedPair resolveById(Long pairId);
}
