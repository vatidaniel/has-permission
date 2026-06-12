package io.github.vatisteve;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Set;

/**
 * Service interface for retrieving permissions associated with a subject.
 *
 * @param <T> the type of the subject for which permissions are retrieved, must be {@link Serializable}
 * @author tinhnv - Jan 15, 2025
 */
public interface PermissionService<T extends Serializable> {

    /**
     * Retrieves the set of permissions associated with the specified subject.
     *
     * @param subject the subject for which permissions are retrieved, may be {@code null}
     * @return a non-null set of permissions associated with the subject
     */
    @NonNull Set<String> getPermissions(@Nullable T subject);
}
