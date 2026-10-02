package org.ghostplays.ghosttech.api.block;

import java.util.Collection;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.api.util.GhostTextureId;

/**
 * A custom block definition.
 *
 * <p>Custom blocks are backed by a real vanilla {@link Material}, so they work on any Paper 26.x
 * server without a resource pack or a client mod. The plugin tracks which world positions hold a
 * custom block in SQLite, and tags the item form with the content ID so the association survives
 * being moved around, dropped and re-picked-up.
 *
 * <p>Implementations are registered during module load:
 * <pre>{@code
 * context.api().blocks().register(new SimpleBlock(
 *         GhostId.parse("example:legendary_forge"),
 *         Material.ANVIL,
 *         "Legendary Forge"));
 * }</pre>
 */
public interface CustomBlock {

    /** The namespaced ID, e.g. {@code module1:legendary_forge}. */
    GhostId id();

    /** The vanilla material this block is built on. Must be a placeable block material. */
    Material material();

    /** Display name shown when a player looks at or holds the block item. */
    net.kyori.adventure.text.Component displayName();

    /** Optional lore lines for the item form. */
    List<net.kyori.adventure.text.Component> lore();

    /**
     * The permission required to place and break this block.
     *
     * <p>Conventionally {@code ghosttech.block.<namespace>.<path>}. When {@code null} the block is
     * available to everyone.
     */
    String permission();

    /**
     * Texture reference for a future resource pack, or {@code null} to use the vanilla material
     * texture.
     */
    GhostTextureId texture();

    /**
     * What the block drops when broken. Return an empty list for nothing.
     *
     * <p>Default: drops the block's own item, i.e. the vanilla material.
     */
    Collection<ItemStack> drops(BlockContext context);

    /** Called after the block has been placed and tracked. */
    default void onPlace(BlockContext context) {
    }

    /**
     * Called before the block is removed. Returning {@code false} cancels the break.
     *
     * <p>This is where protection checks belong.
     */
    default boolean onBreak(BlockContext context) {
        return true;
    }

    /** Called on right-click, after the permission check passed. */
    default void onInteract(BlockContext context) {
    }

    /** Called when the block is destroyed by anything other than a player, e.g. an explosion. */
    default void onDestroyed(BlockContext context) {
    }

    /**
     * {@code true} if the block needs support underneath, like a torch or a flower.
     *
     * <p>Default {@code false}. When {@code true} the plugin removes the block itself if the
     * supporting block is broken.
     */
    default boolean requiresSupport() {
        return false;
    }

    /** {@code true} when the block can be broken in survival mode. Default {@code true}. */
    default boolean breakableInSurvival() {
        return true;
    }

    /** {@code true} when a piston can push or pull this block. Default {@code false}. */
    default boolean pistonMovable() {
        return false;
    }
}