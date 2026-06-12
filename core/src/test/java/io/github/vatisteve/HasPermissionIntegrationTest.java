package io.github.vatisteve;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test exercising a real Spring AOP proxy (no mocking of the join point or annotation).
 * This verifies that the aspect actually intercepts annotated methods and that {@code #userId} resolves
 * from the real method parameter name through the proxy — the path that unit tests with mocked
 * {@code MethodSignature} cannot cover.
 */
class HasPermissionIntegrationTest {

    private AnnotationConfigApplicationContext ctx;
    private SecuredService service;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(TestConfig.class);
        service = ctx.getBean(SecuredService.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void grantsAccessWhenSubjectHasPermission() {
        assertEquals("ok:alice", service.read("alice", "doc-1"));
    }

    @Test
    void deniesAccessWhenSubjectLacksPermission() {
        // bob has no permissions
        assertThrows(PermissionDeniedException.class, () -> service.read("bob", "doc-1"));
    }

    @Test
    void resolvesSubjectFromNamedParameterThroughProxy() {
        // alice lacks ADMIN -> denied; carol has it -> allowed.
        // Proves the default "#userId" expression binds to the real parameter name.
        assertThrows(PermissionDeniedException.class, () -> service.adminOnly("alice"));
        assertEquals("admin:carol", service.adminOnly("carol"));
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        PermissionService<String> permissionService() {
            return subject -> {
                if (subject == null) {
                    return Collections.emptySet();
                }
                Map<String, Set<String>> perms = Map.of(
                        "alice", Set.of("READ"),
                        "carol", Set.of("READ", "ADMIN"));
                return perms.getOrDefault(subject, Collections.emptySet());
            };
        }

        @Bean
        HasPermissionAuthorizer<String> hasPermissionAuthorizer(PermissionService<String> ps) {
            return new HasPermissionAuthorizer<>(ps, "userId");
        }

        @Bean
        SecuredService securedService() {
            return new SecuredService();
        }
    }

    static class SecuredService {

        @HasPermission(of = "READ")
        public String read(String userId, String docId) {
            return "ok:" + userId;
        }

        @HasPermission(of = "ADMIN")
        public String adminOnly(String userId) {
            return "admin:" + userId;
        }
    }
}
