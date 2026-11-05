package com.openexchange.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.openexchange.userservice.dto.LoginRequest;
import com.openexchange.userservice.dto.RegisterRequest;
import com.openexchange.userservice.dto.TokenResponse;
import com.openexchange.userservice.dto.UserInfoResponse;
import com.openexchange.userservice.entity.User;
import com.openexchange.userservice.mapper.UserMapper;
import com.openexchange.userservice.security.JwtTokenProvider;
import com.openexchange.userservice.security.RefreshTokenBlacklist;
import com.openexchange.userservice.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final String DEFAULT_KYC_LEVEL = "LEVEL_0";
    private static final String REF_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenBlacklist refreshTokenBlacklist;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateRegisterRequest(email, request.getPassword());

        // 邮箱唯一性校验
        Long count = this.baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUid(generateUid());
        user.setRefCode(generateRefCode());
        user.setStatus(DEFAULT_STATUS);
        user.setKycLevel(DEFAULT_KYC_LEVEL);
        user.setGoogleAuthEnabled(false);
        user.setReferredBy(resolveReferrer(request.getRefCode()));
        this.save(user);

        log.info("User registered, userId={}, email={}", user.getId(), email);
        return buildTokenResponse(user);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (!StringUtils.hasText(email) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "email/password required");
        }

        User user = this.baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!DEFAULT_STATUS.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return buildTokenResponse(user);
    }

    @Override
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenBlacklist.revoke(refreshToken);
        }
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return UserInfoResponse.from(user);
    }

    // ---------- 私有方法 ----------

    private void validateRegisterRequest(String email, String password) {
        if (!StringUtils.hasText(email) || !email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid email");
        }
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "password must be at least 8 characters");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * 生成用户唯一标识
     */
    private String generateUid() {
        return "EX" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 生成 6 位邀请码（去除易混淆字符）
     */
    private String generateRefCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(REF_CODE_CHARS.charAt(secureRandom.nextInt(REF_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 解析邀请码，返回推荐人 ID；邀请码无效则忽略
     */
    private Long resolveReferrer(String refCode) {
        if (!StringUtils.hasText(refCode)) {
            return null;
        }
        User referrer = this.baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getRefCode, refCode.trim().toUpperCase()));
        return referrer != null ? referrer.getId() : null;
    }

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUid(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }
}
