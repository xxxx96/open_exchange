package com.openexchange.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.openexchange.user.dto.LoginRequest;
import com.openexchange.user.dto.RegisterRequest;
import com.openexchange.user.dto.TokenResponse;
import com.openexchange.user.dto.UserInfoResponse;
import com.openexchange.user.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
public interface IUserService extends IService<User> {

    /**
     * 注册新用户
     *
     * @param request 注册请求
     * @return 令牌响应（注册成功即登录）
     */
    TokenResponse register(RegisterRequest request);

    /**
     * 邮箱密码登录
     *
     * @param request 登录请求
     * @return 令牌响应
     */
    TokenResponse login(LoginRequest request);

    /**
     * 登出，吊销 refresh token
     *
     * @param refreshToken 刷新令牌
     */
    void logout(String refreshToken);

    /**
     * 查询当前登录用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfoResponse getUserInfo(Long userId);
}
