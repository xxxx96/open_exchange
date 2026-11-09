package org.openexchange.common.web.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * <p>
 * 实体基类：所有业务实体的公共字段（主键 + 审计时间），按 MyBatis-Plus 规范定义。
 * 代码生成器生成的实体必须继承本类，且不再重复生成 id / created_at / updated_at 字段。
 * </p>
 * <ul>
 *   <li>主键：{@link IdType#AUTO} 数据库自增（建表统一使用 BIGSERIAL），
 *       若不显式声明，MP 将按全局默认 ASSIGN_ID（雪花 ID）处理，与自增主键冲突；</li>
 *   <li>created_at：INSERT 时由 {@code AuditMetaObjectHandler} 自动填充
 *       （替代 JPA 的 @PrePersist，MyBatis-Plus 不会触发 JPA 生命周期回调）；</li>
 *   <li>updated_at：INSERT / UPDATE 时自动填充。</li>
 * </ul>
 *
 * @author yanglei
 * @since 2026-11-05
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键：数据库自增（BIGSERIAL），插入成功后由 MP 回填生成的主键值
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间（TIMESTAMPTZ）：插入时自动填充，更新语句不会修改该列
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 最后更新时间（TIMESTAMPTZ）：插入与更新时自动填充
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
