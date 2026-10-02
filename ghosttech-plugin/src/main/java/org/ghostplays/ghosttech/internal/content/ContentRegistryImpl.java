package org.ghostplays.ghosttech.internal.content;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.ghostplays.ghosttech.api.content.ContentRegistry;
import org.ghostplays.ghosttech.api.content.ContentType;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.api.util.GhostTextureId;

/**
 * In-memory content registry backed by concurrent maps.
 *
 * <p>Registration happens on the main thread during module load, but {@link #find} is also called
 * from listeners that may run while a module is unloading. Concurrent maps keep those interactions
 * safe without forcing every caller onto a specific thread.
 */
public final class ContentRegistryImpl implements ContentRegistry {

    private record Entry(Object value, GhostTextureId textureId) {
    }

    private final Map<ContentType, Map<GhostId, Entry>> byType = new EnumMap<>(ContentType.class);

    public ContentRegistryImpl() {
        for (ContentType type : ContentType.values()) {
            byType.put(type, new ConcurrentHashMap<>());
        }
    }

    @Override
    public <T> void register(ContentType type, GhostId id, T value, GhostTextureId textureId) {
        Map<GhostId, Entry> entries = byType.get(type);
        Entry existing = entries.putIfAbsent(id, new Entry(value, textureId));
        if (existing != null) {
            // Failing loudly here catches copy-paste mistakes at load time rather than when a
            // player first walks into the content.
            throw new IllegalArgumentException(
                    "Content " + type + " " + id + " is already registered");
        }
    }

    @Override
    public <T> void register(ContentType type, GhostId id, T value) {
        register(type, id, value, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(ContentType type, GhostId id) {
        Entry entry = byType.get(type).get(id);
        // The registry stores values untyped by design, so the caller asserting the expected type
        // is what makes this cast sound. Callers use it through typed registries such as
        // CustomBlockRegistry, which check before delegating here.
        return entry == null ? Optional.empty() : Optional.of((T) entry.value());
    }

    @Override
    public <T> T require(ContentType type, GhostId id) {
        Optional<T> found = find(type, id);
        if (found.isEmpty()) {
            throw new IllegalStateException("Content " + type + " " + id + " is not registered");
        }
        return found.get();
    }

    @Override
    public boolean contains(ContentType type, GhostId id) {
        return byType.get(type).containsKey(id);
    }

    @Override
    public Collection<GhostId> ids(ContentType type) {
        List<GhostId> ids = new java.util.ArrayList<>(byType.get(type).keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    @Override
    public Optional<GhostTextureId> texture(ContentType type, GhostId id) {
        Entry entry = byType.get(type).get(id);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.textureId());
    }

    @Override
    public boolean unregister(ContentType type, GhostId id) {
        return byType.get(type).remove(id) != null;
    }

    @Override
    public int unregisterNamespace(String namespace) {
        int removed = 0;
        for (ContentType type : ContentType.values()) {
            for (GhostId id : byType.get(type).keySet()) {
                if (id.namespace().equals(namespace)) {
                    removed += byType.get(type).remove(id) != null ? 1 : 0;
                }
            }
        }
        return removed;
    }
}