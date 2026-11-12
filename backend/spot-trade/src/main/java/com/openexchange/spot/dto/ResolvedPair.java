package com.openexchange.spot.dto;

import com.openexchange.spot.entity.Currency;
import com.openexchange.spot.entity.TradingPair;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * 交易对解析结果：交易对 + 基础币/计价币 + 展示符号（BTC/USDT）。
 * 展示符号由 base/quote 币种符号拼装，与库中紧凑符号（BTCUSDT）解耦。
 */
@Data
@Builder
@Jacksonized
public class ResolvedPair {

    private TradingPair pair;

    private Currency baseCurrency;

    private Currency quoteCurrency;

    /** 展示符号，如 BTC/USDT */
    private String displaySymbol;

    public Long pairId() {
        return pair.getId();
    }

    public String dbSymbol() {
        return pair.getSymbol();
    }

    public String displaySymbol() {
        return displaySymbol;
    }

    public int baseDecimals() {
        return baseCurrency.getDecimals() == null ? 18 : baseCurrency.getDecimals();
    }

    public int quoteDecimals() {
        return quoteCurrency.getDecimals() == null ? 18 : quoteCurrency.getDecimals();
    }

    public String baseSymbol() {
        return baseCurrency.getSymbol();
    }

    public String quoteSymbol() {
        return quoteCurrency.getSymbol();
    }
}
