package org.ghostplays.ghosttech.api.multiblock;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Registry of multiblock definitions and formed instances.
 *
 * <p>The plugin detects shapes as blocks are placed and broken, keeps formed instances in SQLite so
 * they survive a restart, and routes interactions to the owning module.
 */
public interface MultiBlockRegistry {

    /**
     * Registers a multiblock definition.
     *
     * @throws IllegalArgumentException if the ID is already taken
     */
    void register(MultiBlock multiBlock);

    /** Looks up a definition by ID. */
    Optional<MultiBlock> multiBlock(GhostId id);

    /** Every registered definition. */
    Collection<MultiBlock> multiBlocks();

    /**
     * The multiblock formed at an anchor position, if any.
     *
     * @return the definition, or empty when nothing is formed here
     */
    Optional<MultiBlock> formedAt(Location origin);

    /**
     * The multiblock instance a block belongs to.
     *
     * <p>Checks the instance table directly, so this works for any block of a formed structure
     * without re-running pattern matching.
     */
    Optional<MultiBlock> instanceAt(Block block);

    /**
     * The pattern key of {@code block} within the multiblock instance it belongs to.
     *
     * <p>This is how the plugin knows which logical part was clicked.
     */
    Optional<String> keyAt(Block block);

    /**
     * Re-checks the structure at an origin and fires {@link MultiBlock#onForm} if it matches.
     *
     * <p>Called automatically on block placement. Exposed so commands can force a check.
     *
     * @return {@code true} when a structure formed
     */
    boolean checkForm(Location origin);

    /** Records that a structure at an origin stopped existing. */
    void removeInstance(Location origin);

    /** Every formed instance origin, used on startup to re-fire {@code onForm}. */
    Collection<Location> instances();

    /** Total number of formed instances currently tracked. */
    int instanceCount();
}