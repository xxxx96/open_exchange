package com.openexchange.wallet.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.wallet.dto.DepositAddressResponse;
import com.openexchange.wallet.entity.AddressPool;
import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.entity.DepositAddress;
import com.openexchange.wallet.mapper.AddressPoolMapper;
import com.openexchange.wallet.mapper.DepositAddressMapper;
import com.openexchange.wallet.service.ICurrencyService;
import com.openexchange.wallet.service.IDepositAddressService;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 充值地址分配服务实现：为用户分配专属充值地址。
 * 核心约束：同用户 + 同链 + 同资产的地址固定不变，分配过的地址永不复用。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class DepositAddressServiceImpl extends ServiceImpl<DepositAddressMapper, DepositAddress> implements IDepositAddressService {

    /**
     * 地址池分配重试次数上限：CAS 竞争失败后的最大重试次数
     */
    private static final int MAX_ALLOC_RETRY = 3;

    /**
     * 地址池状态：未分配可使用
     */
    private static final String POOL_STATUS_AVAILABLE = "available";

    /**
     * 地址池状态：已分配（分配后永不复用）
     */
    private static final String POOL_STATUS_USED = "used";

    private final ICurrencyService currencyService;
    private final AddressPoolMapper addressPoolMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositAddressResponse getOrCreateDepositAddress(Long userId, String chain, String asset) {
        // 1. 解析币种：chain + asset → currencies 表唯一资产记录（地址池按币种的规范 chain 值存储）
        Currency currency = currencyService.resolveCurrency(chain, asset);
        // 2. 已存在分配记录则直接返回已有地址，不再生成新地址（保证地址固定不变）
        DepositAddress existing = getOne(Wrappers.<DepositAddress>lambdaQuery()
                .eq(DepositAddress::getUserId, userId)
                .eq(DepositAddress::getCurrencyId, currency.getId()), false);
        if (existing != null) {
            // 已分配地址的 memo 保留在地址池中，回查补充返回
            AddressPool pool = addressPoolMapper.selectOne(Wrappers.<AddressPool>lambdaQuery()
                    .eq(AddressPool::getChain, currency.getChain())
                    .eq(AddressPool::getAddress, existing.getAddress()), false);
            return buildResponse(existing.getAddress(), pool != null ? pool.getMemo() : "", chain, asset);
        }
        // 3. 从地址池分配新地址：CAS 条件更新占用，失败（并发竞争）则重试
        for (int i = 0; i < MAX_ALLOC_RETRY; i++) {
            AddressPool pool = addressPoolMapper.selectOne(Wrappers.<AddressPool>lambdaQuery()
                    .eq(AddressPool::getChain, currency.getChain())
                    .eq(AddressPool::getStatus, POOL_STATUS_AVAILABLE)
                    .orderByAsc(AddressPool::getId)
                    .last("LIMIT 1"), false);
            if (pool == null) {
                // 地址池耗尽：需离线按 BIP44 继续派生新地址补充池子
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "deposit address pool exhausted for chain " + currency.getChain());
            }
            // 4. CAS 占用：仅当状态仍为 available 时置为 used，保证并发下同一地址只分配给一个用户
            boolean claimed = addressPoolMapper.update(null, Wrappers.<AddressPool>lambdaUpdate()
                    .set(AddressPool::getStatus, POOL_STATUS_USED)
                    .eq(AddressPool::getId, pool.getId())
                    .eq(AddressPool::getStatus, POOL_STATUS_AVAILABLE)) > 0;
            if (claimed) {
                // 5. 插入用户分配记录（UNIQUE(user_id, currency_id) 兜底并发重复分配的幂等性）
                DepositAddress depositAddress = new DepositAddress();
                depositAddress.setUserId(userId);
                depositAddress.setCurrencyId(currency.getId());
                depositAddress.setAddress(pool.getAddress());
                depositAddress.setDerivationPath(pool.getDerivationPath());
                save(depositAddress);
                return buildResponse(pool.getAddress(), pool.getMemo(), chain, asset);
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "failed to allocate deposit address, please retry");
    }

    /**
     * 组装响应：memo 为空时返回空字符串；链标识与资产符号回显请求入参
     */
    private DepositAddressResponse buildResponse(String address, String memo, String chain, String asset) {
        return DepositAddressResponse.builder()
                .address(address)
                .memo(memo != null ? memo : "")
                .chain(chain)
                .asset(asset)
                .build();
    }
}
