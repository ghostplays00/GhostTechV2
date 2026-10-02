package org.ghostplays.ghosttech.api.block;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Context handed to a {@link CustomBlock} callback.
 *
 * <p>A single type is used for every interaction so modules write one handler shape rather than
 * four. The unused fields are simply {@code null} or default for a given action — for example
 * {@link #player()} is {@code null} when a piston pushes a block.
 */
public interface BlockContext {

    /** The block being placed, broken or interacted with. Never {@code null}. */
    Block block();

    /** The location of {@link #block()}. Never {@code null}. */
    Location location();

    /** The player responsible for the action, or {@code null} for machine-driven actions. */
    org.bukkit.entity.Player player();

    /** The block's content ID. Never {@code null}. */
    GhostId blockId();

    /**
     * The {@link org.bukkit.block.Block} placed against the action target.
     *
     * <p>For a right-click this is the clicked block; for a place event it is the block that was
     * clicked to place against. {@code null} when no supporting block exists.
     */
    Block against();
}