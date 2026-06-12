# Spring Permission Control Library

A lightweight, annotation-based permission control system for Spring applications.

## Features

- Method and class-level permission checks
- Flexible permission evaluation with SpEL expressions
- Support for simple, all-of, and any-of permission checks
- Customizable subject extraction from method arguments
- AspectJ-based implementation for minimal intrusion

## Installation

### Spring Boot (recommended)

Add the starter — it auto-configures the aspect from any `PermissionService` bean you expose, so no
manual bean wiring or `@EnableAspectJAutoProxy` is required:

```xml
<dependency>
    <groupId>io.github.vatisteve</groupId>
    <artifactId>has-permission-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Plain Spring (no Boot)

Add the core library and wire the aspect yourself (see [Manual setup](#manual-setup-plain-spring)):

```xml
<dependency>
    <groupId>io.github.vatisteve</groupId>
    <artifactId>has-permission</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Setup

### Spring Boot quick start

1. Implement [`PermissionService`](#1-implement-permissionservice) and expose it as a bean.
2. That's it — the starter registers the `HasPermissionAuthorizer` aspect automatically. Annotate
   methods/classes with `@HasPermission` and (optionally) handle `PermissionDeniedException`
   ([step 3](#3-handle-permissiondeniedexception)).

Tune behaviour via `application.properties` / `application.yml`:

| Property                                  | Default  | Description                                                                                          |
|-------------------------------------------|----------|------------------------------------------------------------------------------------------------------|
| `has-permission.default-subject-property` | `userId` | Parameter name used as the subject when `@HasPermission` does not set an explicit `subject` SpEL.    |
| `has-permission.deny-on-null-subject`     | `true`   | When `true`, deny if a constrained check resolves to a `null` subject; when `false`, pass `null` on. |

The auto-configuration backs off if you define your own `HasPermissionAuthorizer` bean.

### Manual setup (plain Spring)

### 1. Implement PermissionService

Create a service that implements the `PermissionService` interface to provide permissions for your subjects:

```java
@Service
public class UserPermissionService implements PermissionService<String> {
    
    @Override
    public Set<String> getPermissions(@Nullable String userId) {
        // Implementation examples:
        // 1. Fetch from database
        // 2. Load from cache
        // 3. Read from security context
        
        if (userId == null) {
            return Collections.emptySet();
        }
        
        // Example implementation - replace with your actual logic
        Map<String, Set<String>> userPermissions = Map.of(
            "user1", Set.of("READ", "CREATE"),
            "admin1", Set.of("READ", "CREATE", "UPDATE", "DELETE", "ADMIN")
        );
        
        return userPermissions.getOrDefault(userId, Collections.emptySet());
    }
}
```

### 2. Register HasPermissionAuthorizer as a Spring Bean

```java
@Configuration
@EnableAspectJAutoProxy
public class PermissionConfig {
    
    @Bean
    public HasPermissionAuthorizer<String> hasPermissionAuthorizer(PermissionService<String> permissionService) {
        // The second parameter is the default property name for the subject
        // This will be used if no subject is specified in the annotation
        return new HasPermissionAuthorizer<>(permissionService, "userId");
    }
}
```

### 3. Handle PermissionDeniedException

You can handle the exception using a controller advice:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handlePermissionDeniedException(PermissionDeniedException ex) {
        ErrorResponse error = new ErrorResponse("Access Denied", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
}
```

Or using an event listener approach:

```java
@Component
public class SecurityEventHandler {
    
    @EventListener
    public void handlePermissionDeniedException(PermissionDeniedException ex) {
        // Custom handling logic
        log.warn("Permission denied: {}", ex.getMessage());
        
        // Optionally transform to another exception type or perform additional actions
    }
}
```

## Usage Examples

### Method-Level Annotation

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @GetMapping("/{id}")
    @HasPermission(of = "DOCUMENT_READ")
    public Document getDocument(@PathVariable String id, @RequestParam String userId) {
        // The permission check happens before this method executes
        // The userId parameter will be used as the subject (based on our configuration)
        return documentService.findById(id);
    }
    
    @PutMapping("/{id}")
    @HasPermission(allOf = {"DOCUMENT_READ", "DOCUMENT_WRITE"})
    public Document updateDocument(
            @PathVariable String id, 
            @RequestBody Document document,
            @RequestParam String userId) {
        // User must have both read and write permissions
        return documentService.update(id, document);
    }
    
    @DeleteMapping("/{id}")
    @HasPermission(anyOf = {"DOCUMENT_DELETE", "ADMIN"})
    public void deleteDocument(@PathVariable String id, @RequestParam String userId) {
        // User must have either DOCUMENT_DELETE or ADMIN permission
        documentService.delete(id);
    }
    
    @GetMapping("/search")
    @HasPermission(subject = "#searchParams.userId", of = "DOCUMENT_SEARCH")
    public List<Document> searchDocuments(@RequestBody SearchParams searchParams) {
        // Custom subject expression used to extract the userId from the SearchParams object
        return documentService.search(searchParams);
    }
}
```

### Class-Level Annotation

```java
@RestController
@RequestMapping("/api/admin")
@HasPermission(of = "ADMIN")
public class AdminController {
    
    // All methods in this class require the ADMIN permission
    
    @GetMapping("/users")
    public List<User> getAllUsers(@RequestParam String userId) {
        return userService.findAll();
    }
    
    @PostMapping("/system/restart")
    @HasPermission(allOf = {"ADMIN", "SYSTEM_RESTART"})
    public void restartSystem(@RequestParam String userId) {
        // This method requires both ADMIN (from class) and SYSTEM_RESTART permissions
        systemService.restart();
    }
}
```

## Advanced Usage

### Custom Subject Expressions

You can use Spring Expression Language (SpEL) to create complex subject extraction logic:

```java
@GetMapping("/organizations/{orgId}/reports")
@HasPermission(
    subject = "T(com.example.SecurityUtils).getCurrentOrgId(#orgId, #request)", 
    of = "REPORT_VIEW"
)
public List<Report> getReports(
        @PathVariable String orgId, 
        HttpServletRequest request) {
    // ...
}
```

### Combining Permission Types

You can combine different permission requirements:

```java
@PostMapping("/documents/{id}/publish")
@HasPermission(
    of = "DOCUMENT_EDIT",
    allOf = {"DOCUMENT_PUBLISH"},
    anyOf = {"EDITOR", "ADMIN"}
)
public Document publishDocument(@PathVariable String id, @RequestParam String userId) {
    // User must have:
    // 1. DOCUMENT_EDIT permission AND
    // 2. DOCUMENT_PUBLISH permission AND
    // 3. Either EDITOR or ADMIN permission
    return documentService.publish(id);
}
```

## Notes

- Make sure AspectJ is properly enabled in your Spring application (automatic with the Spring Boot starter).
- The subject extracted by the SpEL expression must match the generic type of your PermissionService implementation.
- For optimal performance, consider caching permission results in your PermissionService implementation. Parsed SpEL subject expressions are cached internally by the aspect.
- **Evaluation semantics:**
  - An `@HasPermission` with no constraints (`of`/`value`/`allOf`/`anyOf` all empty) is a no-op and always allows access — the `PermissionService` is not even consulted.
  - When constraints are present but the subject resolves to `null` (the SpEL expression yields `null` or fails to parse/evaluate), access is denied by default (fail-closed). Set `has-permission.deny-on-null-subject=false` (or pass `false` to the 3-arg `HasPermissionAuthorizer` constructor) to instead evaluate permissions with a `null` subject.
  - `of`, `allOf`, and `anyOf` are combined with AND semantics: every constraint that is present must pass.

## License

[MIT License](LICENSE)