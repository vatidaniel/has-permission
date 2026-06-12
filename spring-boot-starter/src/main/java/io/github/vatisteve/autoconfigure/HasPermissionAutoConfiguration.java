package io.github.vatisteve.autoconfigure;

import io.github.vatisteve.HasPermissionAuthorizer;
import io.github.vatisteve.PermissionService;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.Serializable;

/**
 * Auto-configuration that registers a {@link HasPermissionAuthorizer} aspect when the application
 * exposes a {@link PermissionService} bean. Bringing this starter onto the classpath therefore
 * enables {@code @HasPermission} without any manual bean wiring or {@code @EnableAspectJAutoProxy}
 * (Spring Boot's own AOP auto-configuration enables AspectJ auto-proxying once AspectJ is present).
 *
 * <p>Behaviour is tunable via {@link HasPermissionProperties} ({@code has-permission.*}).
 */
@AutoConfiguration
@ConditionalOnClass(Aspect.class)
@EnableConfigurationProperties(HasPermissionProperties.class)
public class HasPermissionAutoConfiguration {

    /**
     * Registers the authorizer aspect, wired to the user's {@link PermissionService} and the
     * configured {@link HasPermissionProperties}. Backs off if the user defines their own
     * {@link HasPermissionAuthorizer}.
     *
     * @param permissionService the application's permission lookup service
     * @param properties the bound {@code has-permission.*} properties
     * @param <T> the subject type handled by the permission service
     * @return the configured authorizer aspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PermissionService.class)
    public <T extends Serializable> HasPermissionAuthorizer<T> hasPermissionAuthorizer(
            PermissionService<T> permissionService, HasPermissionProperties properties) {
        return new HasPermissionAuthorizer<>(
                permissionService,
                properties.getDefaultSubjectProperty(),
                properties.isDenyOnNullSubject());
    }
}
