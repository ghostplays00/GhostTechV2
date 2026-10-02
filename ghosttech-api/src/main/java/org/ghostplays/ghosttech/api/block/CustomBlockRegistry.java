package org.ghostplays.ghosttech.api.block;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Registry of custom blocks plus the operations the plugin performs on their behalf.
 *
 * <p>Modules register definitions here; the plugin exposes this interface so blocks behave
 * identically no matter which module defined them.
 */
public interface CustomBlockRegistry {

    /**
     * Registers a custom block.
     *
     * @throws IllegalArgumentException if the ID is taken or the material cannot be placed
     */
    void register(CustomBlock block);

    /** Looks up a definition by ID. */
    Optional<CustomBlock> block(GhostId id);

    /** Every registered definition. */
    Collection<CustomBlock> blocks();

    /** {@code true} when a custom block is tracked at this position. */
    boolean isCustomBlock(Location location);

    /** The content ID tracked at this position, empty when the position is not a custom block. */
    Optional<GhostId> blockIdAt(Location location);

    /** The tracked block at this position, or {@code null}. */
    default CustomBlock customBlockAt(Location location) {
        return blockIdAt(location).flatMap(this::block).orElse(null);
    }

    /** Convenience for the common case of a position identified by its {@link Block}. */
    default Optional<GhostId> blockIdAt(Block block) {
        return blockIdAt(block.getLocation());
    }

    /**
     * Builds the item that places this block.
     *
     * <p>The returned stack carries the content ID in its persistent data, which is how the plugin
     * recognises the block being placed.
     */
    ItemStack createItem(CustomBlock block);

    /**
     * Extracts the custom block ID from an item, if it is a custom block item.
     *
     * @return the ID, or empty when the stack is a plain vanilla item
     */
    Optional<GhostId> itemId(ItemStack item);

    /** Registers a placed block at a position in the plugin's position store. */
    void track(Location location, GhostId id);

    /** Removes the tracked custom block at a position. */
    void untrack(Location location);

    /** Finds the first custom block ID whose material matches, used for interaction fallback. */
    Optional<CustomBlock> byMaterial(Material material);
}