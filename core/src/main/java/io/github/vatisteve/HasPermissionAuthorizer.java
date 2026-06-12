package io.github.vatisteve;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An aspect that authorizes method or class execution based on the permissions specified in the {@link HasPermission} annotation.
 * This class evaluates the permissions dynamically using SpEL (Spring Expression Language) and checks them against
 * the permissions provided by the {@link PermissionService}.
 *
 * <p>This aspect can be applied to methods or classes annotated with {@link HasPermission}.
 * It supports both method-level and class-level permission checks.
 *
 * <p><strong>Evaluation order.</strong> An {@code @HasPermission} that declares no constraints
 * ({@code of}/{@code value}/{@code allOf}/{@code anyOf} all empty) is a no-op and always allows access,
 * regardless of the resolved subject. When constraints <em>are</em> declared but the subject cannot be
 * resolved (the SpEL expression evaluates to {@code null} or fails to parse/evaluate), behaviour is
 * controlled by {@code denyOnNullSubject}: when {@code true} (the default) access is denied
 * (fail-closed); when {@code false} the permission check proceeds with a {@code null} subject, letting
 * the {@link PermissionService} decide.
 *
 * @param <T> the type of the subject for which permissions are checked, must be {@link Serializable}
 * @author tinhnv - Mar 23, 2025
 * @see HasPermission
 * @see PermissionService
 * @see org.springframework.expression.spel.standard.SpelExpressionParser
 */
@Slf4j
@Aspect
public class HasPermissionAuthorizer<T extends Serializable> {

    private final PermissionService<T> permissionService;
    private final String defaultSubjectPropertyName;
    private final boolean denyOnNullSubject;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link HasPermissionAuthorizer} using the fail-closed default
     * ({@code denyOnNullSubject = true}).
     *
     * @param permissionService the service used to retrieve permissions for a subject
     * @param defaultSubjectPropertyName the default property name used to resolve the subject
     * @throws NullPointerException if {@code permissionService} or {@code defaultSubjectPropertyName} is {@code null}
     */
    public HasPermissionAuthorizer(PermissionService<T> permissionService, String defaultSubjectPropertyName) {
        this(permissionService, defaultSubjectPropertyName, true);
    }

    /**
     * Constructs a new {@link HasPermissionAuthorizer} with the specified {@link PermissionService},
     * default subject property name and null-subject policy.
     *
     * @param permissionService the service used to retrieve permissions for a subject
     * @param defaultSubjectPropertyName the default property name used to resolve the subject
     * @param denyOnNullSubject when {@code true}, deny access if a constrained check has a {@code null} subject;
     *                          when {@code false}, evaluate permissions with the {@code null} subject instead
     * @throws NullPointerException if {@code permissionService} or {@code defaultSubjectPropertyName} is {@code null}
     */
    public HasPermissionAuthorizer(PermissionService<T> permissionService, String defaultSubjectPropertyName, boolean denyOnNullSubject) {
        Objects.requireNonNull(permissionService, "permissionService cannot be null");
        Objects.requireNonNull(defaultSubjectPropertyName, "subjectPropertyName cannot be null");
        this.permissionService = permissionService;
        this.defaultSubjectPropertyName = defaultSubjectPropertyName;
        this.denyOnNullSubject = denyOnNullSubject;
    }

    /**
     * Pointcut for methods annotated with {@link HasPermission}.
     *
     * @param hasPermission the annotation instance
     */
    @Pointcut("@annotation(hasPermission)")
    public void hasPermissionOnMethod(HasPermission hasPermission) {}

    /**
     * Pointcut for classes annotated with {@link HasPermission}.
     *
     * @param hasPermission the annotation instance
     */
    @Pointcut("@within(hasPermission)")
    public void hasPermissionOnClass(HasPermission hasPermission) {}

    /**
     * Checks permissions before executing a method annotated with {@link HasPermission}.
     *
     * @param joinPoint the join point representing the method execution
     * @param hasPermission the annotation instance
     * @throws PermissionDeniedException if the permission check fails
     */
    @Before(value = "hasPermissionOnMethod(hasPermission)", argNames = "joinPoint,hasPermission")
    public void checkPermissionOnMethod(JoinPoint joinPoint, HasPermission hasPermission) {
        T subjectValue = getSubject(hasPermission.subject(), joinPoint);
        checkPermission(hasPermission, subjectValue, joinPoint.getSignature().getName());
    }

    /**
     * Checks permissions before executing a method within a class annotated with {@link HasPermission}.
     *
     * @param joinPoint the join point representing the method execution
     * @param hasPermission the annotation instance
     * @throws PermissionDeniedException if the permission check fails
     */
    @Before(value = "hasPermissionOnClass(hasPermission)", argNames = "joinPoint,hasPermission")
    public void checkPermissionOnClass(JoinPoint joinPoint, HasPermission hasPermission) {
        T subjectValue = joinPoint.getKind().equals(JoinPoint.METHOD_EXECUTION)
                ? getSubject(hasPermission.subject(), joinPoint)
                : null;
        checkPermission(hasPermission, subjectValue, joinPoint.getSignature().getName());
    }

    /**
     * Checks if the subject has the required permissions specified in the {@link HasPermission} annotation.
     *
     * @param hasPermission the annotation instance
     * @param subjectValue the subject for which permissions are checked
     * @param signatureName the name of the method or class being checked
     * @throws PermissionDeniedException if the permission check fails
     */
    private void checkPermission(final HasPermission hasPermission, final T subjectValue, String signatureName) {
        if (hasNoConstraints(hasPermission)) {
            log.debug("No permission constraints specified on [{}], allowing access", signatureName);
            return;
        }
        if (subjectValue == null && denyOnNullSubject) {
            log.warn("Subject value is null for [{}], cannot check permissions", signatureName);
            throw new PermissionDeniedException("Cannot determine subject for permission check on " + signatureName);
        }
        if (doCheckPermission(hasPermission, subjectValue)) return;
        // Subject is intentionally omitted from the message to avoid leaking subject details; it is logged at debug.
        throw new PermissionDeniedException(String.format("Access denied on [%s]", signatureName));
    }

    /**
     * Determines whether the annotation declares any permission constraint at all.
     *
     * @param context the annotation instance
     * @return {@code true} if no {@code of}/{@code value}/{@code allOf}/{@code anyOf} constraint is specified
     */
    private boolean hasNoConstraints(final HasPermission context) {
        return resolveOf(context).isEmpty() && context.allOf().length == 0 && context.anyOf().length == 0;
    }

    /**
     * Normalizes the single-permission constraint, preferring the explicit {@code of} over its {@code value} alias.
     *
     * @param context the annotation instance
     * @return the resolved single permission, or an empty string if none is specified
     */
    private String resolveOf(final HasPermission context) {
        return context.of().isEmpty() ? context.value() : context.of();
    }

    /**
     * Performs the actual permission check based on the {@link HasPermission} annotation.
     *
     * @param context the annotation instance
     * @param subjectValue the subject for which permissions are checked (may be {@code null} when
     *                     {@code denyOnNullSubject} is {@code false})
     * @return {@code true} if the subject has the required permissions, {@code false} otherwise
     */
    private boolean doCheckPermission(final HasPermission context, final T subjectValue) {
        String of = resolveOf(context);
        log.debug("Checking permissions for subject [{}]", subjectValue);
        Set<String> permissions = permissionService.getPermissions(subjectValue);
        log.trace("Permissions found: {}", permissions);
        if (!of.isEmpty() && !permissions.contains(of)) {
            log.debug("Missing required permission [{}] for subject [{}]", of, subjectValue);
            return false;
        }
        if (context.allOf().length > 0) {
            Set<String> allOfSet = Set.of(context.allOf());
            if (!permissions.containsAll(allOfSet)) {
                log.debug("Subject [{}] is missing some required permissions from: {}", subjectValue, allOfSet);
                return false;
            }
        }
        if (context.anyOf().length > 0) {
            Set<String> anyOfSet = Set.of(context.anyOf());
            if (Collections.disjoint(permissions, anyOfSet)) {
                log.debug("Subject [{}] has none of the required permissions from: {}", subjectValue, anyOfSet);
                return false;
            }
        }
        log.debug("Permission check passed for subject [{}]", subjectValue);
        return true;
    }

    /**
     * Resolves the subject value using the provided SpEL expression.
     *
     * @param expression the SpEL expression to evaluate
     * @param joinPoint the join point representing the method execution
     * @return the resolved subject value, or {@code null} if the expression evaluation fails
     */
    @SuppressWarnings("unchecked")
    private T getSubject(String expression, JoinPoint joinPoint) {
        if (expression == null || expression.isEmpty()) {
            expression = "#" + defaultSubjectPropertyName;
            log.trace("Using default subject expression: {}", expression);
        }
        try {
            StandardEvaluationContext context = createEvaluationContext(joinPoint);
            Expression exp = expressionCache.computeIfAbsent(expression, expressionParser::parseExpression);
            Object value = exp.getValue(context);
            if (value == null) {
                log.warn("Subject expression [{}] evaluated to null", expression);
                return null;
            }
            return (T) value;
        } catch (ParseException e) {
            log.error("Failed to parse expression [{}]: {}", expression, e.getMessage());
            return null;
        } catch (EvaluationException e) {
            log.error("Failed to evaluate expression [{}]: {}", expression, e.getMessage());
            return null;
        } catch (ClassCastException e) {
            log.error("Expression [{}] result cannot be cast to expected type: {}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * Creates an evaluation context for SpEL expression evaluation.
     *
     * @param joinPoint the join point representing the method execution
     * @return the evaluation context populated with method parameters and metadata
     */
    private StandardEvaluationContext createEvaluationContext(JoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Signature sig = joinPoint.getSignature();
        if (sig instanceof MethodSignature) {
            MethodSignature signature = (MethodSignature) sig;
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames != null && args != null && paramNames.length == args.length) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            } else {
                log.warn("Cannot bind method parameters for {}", signature);
            }
            context.setVariable("method", signature.getMethod());
            context.setVariable("methodName", signature.getName());
            context.setVariable("returnType", signature.getReturnType());
        } else {
            log.warn("Cannot bind method parameters for non-method signature {}", sig);
        }
        Object target = joinPoint.getTarget();
        context.setVariable("target", target);
        context.setVariable("targetClass", target != null ? target.getClass() : null);
        return context;
    }
}
