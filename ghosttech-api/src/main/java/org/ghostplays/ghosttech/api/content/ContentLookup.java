package org.ghostplays.ghosttech.api.content;

/**
 * Read access to the module content registry, for use inside content type definitions.
 *
 * <p>Separated from {@link ContentRegistry} so that module code holding a reference cannot mutate
 * the registry.
 */
public interface ContentLookup {

    /** Looks up registered content. */
    <T> java.util.Optional<T> find(ContentType type, org.ghostplays.ghosttech.api.util.GhostId id);

    /** {@code true} when content is registered under this type and ID. */
    boolean contains(ContentType type, org.ghostplays.ghosttech.api.util.GhostId id);

    /** Every registered ID of a type. */
    java.util.Collection<org.ghostplays.ghosttech.api.util.GhostId> ids(ContentType type);
}