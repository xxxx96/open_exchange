package com.openexchange.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.wallet.entity.AddressPool;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 充值地址池 Mapper 接口：地址的选取与占用通过 Service 层的条件更新（乐观锁式 CAS）完成，
 * 避免并发分配时同一地址被分配给多个用户。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-06
 */
@Mapper
public interface AddressPoolMapper extends BaseMapper<AddressPool> {

}
