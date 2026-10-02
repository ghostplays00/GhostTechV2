package org.ghostplays.ghosttech.api.multiblock;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Context handed to {@link MultiBlock} callbacks.
 *
 * <p>The {@code key} field tells you which logical part of the shape was involved, so one class can
 * handle every part of a machine.
 */
public interface MultiBlockContext {

    /** The multiblock's content ID. Never {@code null}. */
    GhostId multiBlockId();

    /** The anchor position of the formed structure. Never {@code null}. */
    Location origin();

    /**
     * The part that triggered this callback.
     *
     * <p>For {@code onForm} this is {@code null}, since no specific part was involved. For
     * interaction it is the clicked block.
     */
    Block block();

    /**
     * The pattern key of {@link #block()}, e.g. {@code "output"}.
     *
     * <p>{@code null} on {@code onForm}, and {@code null} when the block was not part of the
     * pattern.
     */
    String key();

    /** The player responsible, or {@code null} for environmental changes. */
    Player player();
}