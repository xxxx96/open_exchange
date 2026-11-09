package org.openexchange.common.web.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.openexchange.common.web.exception.GlobalExceptionHandler;
import org.openexchange.common.web.mybatis.AuditMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)  // 只在 Web 环境下生效
@ConditionalOnProperty(prefix = "common.web", name = "enabled", matchIfMissing = true)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "common.web.global-exception.enabled", havingValue = "true", matchIfMissing = true)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * MyBatis-Plus 审计字段自动填充器：BaseEntity 的 createdAt/updatedAt 依赖此 Bean 自动填充。
     * 仅在类路径存在 MyBatis-Plus 时注册（common-web 对 MP 的依赖为 optional，
     * 不引入持久层的服务不会被类加载问题影响）。
     */
    @Bean
    @ConditionalOnClass(MetaObjectHandler.class)
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
