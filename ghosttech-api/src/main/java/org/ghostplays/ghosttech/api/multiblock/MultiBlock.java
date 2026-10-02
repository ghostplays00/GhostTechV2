package org.ghostplays.ghosttech.api.multiblock;

import java.util.List;
import org.bukkit.Location;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * A multiblock definition: a pattern plus the behaviour that runs when it forms or is used.
 *
 * <p>Registered by modules during load:
 * <pre>{@code
 * context.api().multiblocks().register(new SimpleMultiBlock(
 *         GhostId.parse("example:rubyforge"),
 *         MultiBlockPattern.exact(shape),
 *         List.of("input", "output")));
 * }</pre>
 */
public interface MultiBlock {

    /** The namespaced ID, e.g. {@code module2:rubyforge}. */
    GhostId id();

    /** Display name used in command feedback and GUI listings. */
    net.kyori.adventure.text.Component displayName();

    /** The shape this structure is recognised by. */
    MultiBlockPattern pattern();

    /** Permission required to form and use the structure. {@code null} means everyone. */
    String permission();

    /** Texture reference for a future resource pack, may be {@code null}. */
    org.ghostplays.ghosttech.api.util.GhostTextureId texture();

    /**
     * Called when the shape is detected in the world.
     *
     * <p>Only fire once per instance: the plugin keeps a record of formed instances and will not
     * call this again for the same structure unless it is broken and reformed.
     */
    void onForm(MultiBlockContext context);

    /** Called when a player right-clicks one of the structure's keyed parts. */
    default void onInteract(MultiBlockContext context) {
    }

    /** Called when any block of the structure is broken. */
    default void onBreak(MultiBlockContext context) {
    }

    /** {@code true} to let the structure stay formed after a break. Default {@code false}. */
    default boolean persistent() {
        return false;
    }

    /**
     * Interaction keys this structure understands, matching the keys used in its pattern.
     *
     * <p>Only keys listed here fire {@link #onInteract}, which lets a player click the "output"
     * side of a machine without the frame triggering it.
     */
    List<String> interactionKeys();
}