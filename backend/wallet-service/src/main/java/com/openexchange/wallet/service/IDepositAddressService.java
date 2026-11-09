package com.openexchange.wallet.service;

import com.openexchange.wallet.dto.DepositAddressResponse;
import com.openexchange.wallet.entity.DepositAddress;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
public interface IDepositAddressService extends IService<DepositAddress> {

    /**
     * 为用户分配专属充值地址（同用户 + 同链 + 同资产固定不变）。
     * 已存在分配记录则直接返回；否则从地址池取一个未使用地址（CAS 占用），
     * 插入分配记录并标记地址池地址为已使用。
     *
     * @param userId 用户 ID
     * @param chain  链标识，如 ETH、TRX
     * @param asset  资产符号，如 USDT
     * @return 充值地址、memo、链标识与资产符号
     */
    DepositAddressResponse getOrCreateDepositAddress(Long userId, String chain, String asset);
}
