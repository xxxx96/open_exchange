package com.openexchange.wallet.service;

import com.openexchange.wallet.entity.Currency;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
public interface ICurrencyService extends IService<Currency> {

    /**
     * 根据链标识 + 资产符号解析唯一的启⽤资产。
     * 入参链标识（如 ETH/TRX/ERC20）会先经过别名归一化，再与 currencies.chain 匹配；
     * 同符号仅存在一个启用资产时放宽链匹配（兼容 BTC 等单链资产）。
     *
     * @param chain 链标识，如 ETH、TRX
     * @param asset 资产符号，如 USDT
     * @return 匹配的资产记录
     * @throws BusinessException 资产不存在或该链下资产不唯一无法确定时抛出
     */
    Currency resolveCurrency(String chain, String asset);

    /**
     * 仅根据资产符号解析启⽤资产（余额查询不区分链，账本以资产维度记账）。
     *
     * @param asset 资产符号，如 USDT
     * @return 匹配的资产记录
     * @throws BusinessException 资产不存在时抛出
     */
    Currency resolveBySymbol(String asset);
}
