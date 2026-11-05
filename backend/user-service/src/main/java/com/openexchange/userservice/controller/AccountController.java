package com.openexchange.userservice.controller;

import com.openexchange.userservice.dto.LoginRequest;
import com.openexchange.userservice.dto.LogoutRequest;
import com.openexchange.userservice.dto.RegisterRequest;
import com.openexchange.userservice.dto.TokenResponse;
import com.openexchange.userservice.dto.UserInfoResponse;
import com.openexchange.userservice.security.UserPrincipal;
import com.openexchange.userservice.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.openexchange.common.web.dto.ApiResponse;
import org.openexchange.common.web.exception.BusinessException;
import org.openexchange.common.web.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  账号管理：注册、登录、登出、我的信息
 * </p>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Tag(name = "账号管理", description = "注册、登录、登出、我的信息")
public class AccountController {

    private final IUserService userService;

    @Operation(summary = "注册", description = "注册成功后直接返回访问令牌")
    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    @Operation(summary = "登录", description = "邮箱密码登录，返回访问令牌与刷新令牌")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @Operation(summary = "登出", description = "吊销刷新令牌，客户端同时清除本地令牌")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        userService.logout(request != null ? request.getRefreshToken() : null);
        return ApiResponse.success();
    }

    @Operation(summary = "我的信息", description = "获取当前登录用户信息，需要携带 Bearer Token")
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me() {
        return ApiResponse.success(userService.getUserInfo(getCurrentUserId()));
    }

    // ---------- 私有方法 ----------

    /**
     * 从安全上下文获取当前登录用户ID（由 JwtAuthenticationFilter 写入）
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        throw new BusinessException(ErrorCode.AUTH_REQUIRED);
    }
}
