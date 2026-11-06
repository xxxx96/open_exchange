package com.openexchange.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openexchange.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
