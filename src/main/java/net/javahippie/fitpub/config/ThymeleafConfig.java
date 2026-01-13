package net.javahippie.fitpub.config;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Thymeleaf configuration for Layout Dialect support
 */
@Configuration
public class ThymeleafConfig {

    /**
     * Configure Thymeleaf Layout Dialect for template inheritance
     */
    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }
}
