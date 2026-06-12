package io.github.vatisteve.autoconfigure;

import io.github.vatisteve.HasPermissionAuthorizer;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the has-permission auto-configuration, bound from the
 * {@code has-permission.*} namespace.
 */
@ConfigurationProperties(prefix = "has-permission")
public class HasPermissionProperties {

    /**
     * Default property name used to resolve the subject when an {@code @HasPermission}
     * does not specify an explicit {@code subject} SpEL expression. The aspect falls back
     * to {@code #<defaultSubjectProperty>}.
     */
    private String defaultSubjectProperty = "userId";

    /**
     * Whether to deny access when a constrained permission check resolves to a {@code null}
     * subject. When {@code false}, the {@code null} subject is passed to the
     * {@link HasPermissionAuthorizer}'s {@code PermissionService} instead.
     */
    private boolean denyOnNullSubject = true;

    public String getDefaultSubjectProperty() {
        return defaultSubjectProperty;
    }

    public void setDefaultSubjectProperty(String defaultSubjectProperty) {
        this.defaultSubjectProperty = defaultSubjectProperty;
    }

    public boolean isDenyOnNullSubject() {
        return denyOnNullSubject;
    }

    public void setDenyOnNullSubject(boolean denyOnNullSubject) {
        this.denyOnNullSubject = denyOnNullSubject;
    }
}
