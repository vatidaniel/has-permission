# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`has-permission` is a lightweight, annotation-based permission control library for Spring applications, published to Maven Central under `io.github.vatisteve`. It is a **library, not an application** — there is no `main`. As of 0.2.0 it is a **multi-module** Maven project:

```
pom.xml                       # parent aggregator (has-permission-parent, packaging=pom)
core/                         # artifactId: has-permission  — the aspect, annotation, SPI, exception
spring-boot-starter/          # artifactId: has-permission-spring-boot-starter — auto-configuration
```

- **core** targets Java 11. Its Spring/AspectJ/Lombok/SLF4J deps are all `provided` scope (the host app supplies them at runtime).
- **spring-boot-starter** targets Spring Boot 2.7.18 (which aligns with core's Spring 5.3.x and Java 11).

## Build / JDK

**Build with JDK 11**, not the machine default. On this machine `mvn` (sdkman) defaults to **JDK 25**, under which Lombok/AspectJ in this project misbehave. Point `JAVA_HOME` at JDK 11 first, e.g. (PowerShell):

```powershell
$env:JAVA_HOME = "D:\tools\sdkman\bashonly\candidates\java\11.0.29-tem"
& D:\tools\sdkman\bashonly\candidates\maven\current\bin\mvn.cmd <goals>
```

## Commands

```bash
mvn clean install                    # build + test both modules
mvn test                             # test everything
mvn -pl core test                    # test only the core module
mvn -pl spring-boot-starter test     # test only the starter (core must be installed, or add -am)
mvn -pl core test -Dtest=HasPermissionAuthorizerTest#testClassLevelAdvice   # single method

mvn clean deploy -P release -DperformRelease=true   # GPG sign + deploy both modules to OSSRH
```

The `release` profile (GPG, javadoc-jar, sources-jar, nexus-staging) lives in the parent and only activates with `-DperformRelease=true`; normal builds skip it.

## Dependency-management gotcha

The **parent intentionally declares no `<dependencyManagement>`**. Maven gives parent-inherited dependencyManagement precedence over a child module's *imported* BOM, so pinning versions in the parent leaks into the starter and conflicts with the Spring Boot BOM (this caused a logback `classic`/`core` version split → `NoSuchMethodError`). Instead: **core pins its own versions** via the `<properties>` in the parent; **the starter derives everything from the Spring Boot BOM** it imports in its own `<dependencyManagement>`. Keep it that way.

## Architecture

Runtime flow (core):

1. **`@HasPermission`** (`core/.../HasPermission.java`) — annotation for methods or types. Attributes: `subject` (SpEL identifying the actor), and constraints `of`/`value` (single; `value` is an alias for `of`, `of` wins), `allOf` (all required), `anyOf` (at least one). All-empty = no-op.

2. **`HasPermissionAuthorizer<T extends Serializable>`** (`core/.../HasPermissionAuthorizer.java`) — the `@Aspect`, and the only bean a consumer must register (the starter does this automatically). Two `@Before` advices: `checkPermissionOnMethod` (`@annotation`) and `checkPermissionOnClass` (`@within`). Resolves the subject by evaluating the `subject` SpEL against a context populated with the method's named parameters plus `#method`, `#methodName`, `#returnType`, `#target`, `#targetClass`; empty `subject` falls back to `#<defaultSubjectPropertyName>` (constructor arg, e.g. `"userId"`). The resolved subject goes to the `PermissionService`, whose returned set is matched against the constraints.

3. **`PermissionService<T extends Serializable>`** (`core/.../PermissionService.java`) — the SPI the consumer implements: `Set<String> getPermissions(T subject)`. The library never knows where permissions come from.

4. **`PermissionDeniedException`** — unchecked; thrown on denial. Has `(message)` and `(message, cause)` constructors.

5. **Starter** (`spring-boot-starter/.../autoconfigure/`) — `HasPermissionAutoConfiguration` (`@AutoConfiguration`, conditional on a `PermissionService` bean, backs off on a user-defined authorizer) + `HasPermissionProperties` (`has-permission.*`), registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Key behaviors to preserve when editing

- **Empty annotation is a true no-op**: `checkPermission` returns *before* resolving/denying on a null subject and before calling the `PermissionService` when no constraint is declared (`hasNoConstraints`). Don't reintroduce a service call for the empty case.
- **Null subject + constraints → policy-driven**: denied when `denyOnNullSubject` is `true` (default, fail-closed); otherwise the `null` subject is passed to the service. Exposed via the 3-arg constructor and the `has-permission.deny-on-null-subject` property.
- **`getSubject` is fail-closed**: SpEL `ParseException`/`EvaluationException`/`ClassCastException` are logged and return `null` (→ deny when constrained). Parsed expressions are cached in `expressionCache` (`ConcurrentHashMap`) — keep parsing cache-friendly.
- **`of` vs `value`**: explicit `of` wins; `value` only used when `of` is empty (`resolveOf`).
- **Constraint combination is AND**: every present constraint must pass.
- **`-parameters` matters**: the compiler plugin sets `<parameters>true</parameters>` so `#paramName` subject expressions resolve via real parameter names. The real-proxy integration test (`HasPermissionIntegrationTest`) is what actually exercises this end to end.

## Tests

- `core`: `HasPermissionAuthorizerTest` (JUnit 5 + Mockito; mocks `JoinPoint`/`MethodSignature`/`HasPermission`) covers the branch logic; `HasPermissionIntegrationTest` spins a real `AnnotationConfigApplicationContext` with `@EnableAspectJAutoProxy` to prove interception + parameter-name resolution (the path mocked unit tests can't cover). `spring-context` is a test-scope dep for this.
- `spring-boot-starter`: `HasPermissionAutoConfigurationTest` uses `ApplicationContextRunner` to verify the conditional bean wiring, property binding, and back-off.
- Mockito runs in strict-stubs mode: don't stub annotation attributes a given code path won't read (e.g. `allOf`/`anyOf` aren't read when `of` is non-empty because `hasNoConstraints` short-circuits).
