package com.openexchange.wallet.service.impl;

import com.openexchange.wallet.entity.Currency;
import com.openexchange.wallet.mapper.CurrencyMapper;
import com.openexchange.wallet.service.ICurrencyService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Service
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements ICurrencyService {

}
