package io.github.vatisteve.autoconfigure;

import io.github.vatisteve.HasPermissionAuthorizer;
import io.github.vatisteve.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class HasPermissionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HasPermissionAutoConfiguration.class));

    @Test
    void doesNotCreateAuthorizerWithoutPermissionService() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(HasPermissionAuthorizer.class));
    }

    @Test
    void createsAuthorizerWhenPermissionServicePresent() {
        runner.withUserConfiguration(PermissionServiceConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(HasPermissionAuthorizer.class));
    }

    @Test
    void respectsConfiguredProperties() {
        runner.withUserConfiguration(PermissionServiceConfig.class)
                .withPropertyValues(
                        "has-permission.default-subject-property=accountId",
                        "has-permission.deny-on-null-subject=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(HasPermissionAuthorizer.class);
                    HasPermissionProperties props = ctx.getBean(HasPermissionProperties.class);
                    assertThat(props.getDefaultSubjectProperty()).isEqualTo("accountId");
                    assertThat(props.isDenyOnNullSubject()).isFalse();
                });
    }

    @Test
    void backsOffWhenUserDefinesAuthorizer() {
        runner.withUserConfiguration(PermissionServiceConfig.class, CustomAuthorizerConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(HasPermissionAuthorizer.class);
                    assertThat(ctx).hasBean("customAuthorizer");
                });
    }

    @Configuration
    static class PermissionServiceConfig {
        @Bean
        PermissionService<String> permissionService() {
            return subject -> Collections.emptySet();
        }
    }

    @Configuration
    static class CustomAuthorizerConfig {
        @Bean
        HasPermissionAuthorizer<String> customAuthorizer(PermissionService<String> permissionService) {
            return new HasPermissionAuthorizer<>(permissionService, "custom");
        }
    }
}
