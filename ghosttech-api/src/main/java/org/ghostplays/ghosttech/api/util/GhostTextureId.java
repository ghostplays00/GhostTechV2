package org.ghostplays.ghosttech.api.util;

import java.util.Locale;

/**
 * Identifier for a texture that a content entry may reference.
 *
 * <p>Custom blocks and items work today without any custom textures (they use vanilla materials).
 * The moment you ship a resource pack, every content entry can carry a {@code GhostTextureId}
 * pointing at the sprite/model/JSON file that should be used instead. The IDs are declared now so
 * that content definitions do not have to be redesigned later.
 *
 * <p>Format: {@code namespace:path}, for example {@code module1:blocks/legendary_forge}.
 */
public record GhostTextureId(String namespace, String path) implements Comparable<GhostTextureId> {

    private static final GhostTextureId DEFAULT = new GhostTextureId("minecraft", "block/stone");

    public GhostTextureId {
        namespace = namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
        path = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Texture id must be namespace:path");
        }
    }

    /** Parses {@code namespace:path}, defaulting the namespace to {@code ghosttech}. */
    public static GhostTextureId parse(String raw) {
        GhostId id = GhostId.parse(raw);
        return new GhostTextureId(id.namespace(), id.path());
    }

    /** The texture used when no custom texture is configured. */
    public static GhostTextureId vanillaDefault() {
        return DEFAULT;
    }

    /**
     * The resource-pack location this texture resolves to, e.g.
     * {@code assets/module1/textures/block/legendary_forge.png}.
     */
    public String assetPath(String category) {
        return "assets/" + namespace + "/textures/" + category + "/" + path + ".png";
    }

    @Override
    public int compareTo(GhostTextureId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}