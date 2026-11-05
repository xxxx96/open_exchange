package com.openexchange.userservice.service.impl;

import com.openexchange.userservice.entity.User;
import com.openexchange.userservice.mapper.UserMapper;
import com.openexchange.userservice.service.IUserService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
