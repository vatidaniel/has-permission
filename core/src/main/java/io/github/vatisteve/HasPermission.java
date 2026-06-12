package io.github.vatisteve;

import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method or class requires permission checks before execution.
 * This annotation can be applied to methods or types to specify the permissions
 * required for access. Permissions can be defined using a single permission,
 * multiple permissions, or a combination of permissions.
 *
 * <p>The annotation supports SpEL (Spring Expression Language) for dynamic
 * permission evaluation through the {@code subject} attribute.
 *
 * @author tinhnv - Jan 15, 2025
 * @see PermissionService#getPermissions(Serializable)
 * @see org.springframework.expression.spel.standard.SpelExpressionParser
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HasPermission {

    /**
     * The identifier used to retrieve permissions dynamically.
     * This attribute supports SpEL (Spring Expression Language) for flexible
     * permission resolution.
     *
     * @return the subject to be used with {@link PermissionService#getPermissions(Serializable)}
     */
    String subject() default "";

    /**
     * An alias for {@link #of()}. Specifies a single permission that must be granted.
     *
     * @return the permission that must be granted
     */
    String value() default "";

    /**
     * Specifies a single permission that must be granted.
     *
     * @return the permission that must be granted
     */
    String of() default "";

    /**
     * Specifies multiple permissions that must all be granted.
     *
     * @return an array of permissions that must all be granted
     */
    String[] allOf() default {};

    /**
     * Specifies multiple permissions, at least one of which must be granted.
     *
     * @return an array of permissions, at least one of which must be granted
     */
    String[] anyOf() default {};
}