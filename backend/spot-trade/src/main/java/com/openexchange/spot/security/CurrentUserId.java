package com.openexchange.spot.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户 ID：由网关完成 JWT 校验后以 X-User-Id 请求头透传，
 * 服务内不重复验签，缺失/非法时直接拒绝（AUTH_REQUIRED）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
