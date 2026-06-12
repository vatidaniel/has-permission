package io.github.vatisteve;

/**
 * Exception thrown when a permission check fails.
 * This exception indicates that the subject does not have the required permissions to perform the requested operation.
 *
 * @author tinhnv - Mar 23, 2025
 */
public class PermissionDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link PermissionDeniedException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public PermissionDeniedException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@link PermissionDeniedException} with the specified detail message and cause.
     *
     * @param message the detail message explaining the reason for the exception
     * @param cause the underlying cause of the permission failure
     */
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
