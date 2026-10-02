package org.ghostplays.ghosttech.api.content;

import java.util.Collection;
import java.util.Optional;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.api.util.GhostTextureId;

/**
 * Central, typed lookup for everything registered by the plugin and its modules.
 *
 * <p>IDs are namespaced per module, so registrations never collide across modules. Duplicate
 * registration of the same {@code (type, id)} pair is rejected with an exception naming the owning
 * module, which catches copy-paste mistakes at load time rather than at first use.
 *
 * <p>All methods must be called from the main server thread.
 */
public interface ContentRegistry {

    /**
     * Registers a content entry.
     *
     * @param type      the content kind
     * @param id        the namespaced ID, whose namespace should match the owning module
     * @param value     the content object
     * @param textureId optional texture reference for a future resource pack, may be {@code null}
     * @param <T>       the content type
     * @throws IllegalArgumentException if {@code (type, id)} is already registered
     */
    <T> void register(ContentType type, GhostId id, T value, GhostTextureId textureId);

    /**
     * Registers content with no associated texture.
     *
     * @see #register(ContentType, GhostId, Object, GhostTextureId)
     */
    <T> void register(ContentType type, GhostId id, T value);

    /** Looks up registered content. */
    <T> Optional<T> find(ContentType type, GhostId id);

    /** Looks up content, failing loudly if it is missing. Intended for internal plugin use. */
    <T> T require(ContentType type, GhostId id);

    /** {@code true} when content is registered under this type and ID. */
    boolean contains(ContentType type, GhostId id);

    /** Every registered ID of a type, sorted. */
    Collection<GhostId> ids(ContentType type);

    /**
     * The texture attached to a content entry, empty when none was configured.
     *
     * <p>Always returns empty today, since no custom textures are shipped yet. It exists so content
     * definitions can already carry texture references.
     */
    Optional<GhostTextureId> texture(ContentType type, GhostId id);

    /**
     * Removes a registration.
     *
     * <p>Called by the plugin when a module is unloaded so its content stops resolving immediately.
     *
     * @return {@code true} if something was removed
     */
    boolean unregister(ContentType type, GhostId id);

    /** Drops every registration whose ID namespace belongs to {@code namespace}. */
    int unregisterNamespace(String namespace);
}