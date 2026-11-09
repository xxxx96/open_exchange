package org.openexchange.common.web.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Instant;

/**
 * <p>
 * 审计字段自动填充器：配合 {@link org.openexchange.common.web.entity.BaseEntity} 上的
 * {@code @TableField(fill = ...)} 注解，在 INSERT / UPDATE 时自动写入 createdAt / updatedAt，
 * 替代 JPA 的 @PrePersist / @PreUpdate（MyBatis-Plus 不会触发 JPA 生命周期回调）。
 * </p>
 * <p>
 * 填充采用 strict 语义：仅当字段被 fill 注解标记且当前值为 null 时才写入，
 * 不会覆盖业务代码手动设置的值（如 Deposit.creditedAt 等业务字段不受影响）。
 * </p>
 *
 * @author yanglei
 * @since 2026-11-09
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
    }
}
