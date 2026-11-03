package org.openexchange.common.web.autoconfigure;

import org.openexchange.common.web.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
}
