package org.ghostplays.ghosttech.internal.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.block.BlockContext;
import org.ghostplays.ghosttech.api.util.GhostId;

/** Immutable {@link BlockContext} handed to custom block callbacks. */
public final class BlockContextImpl implements BlockContext {

    private final Block block;
    private final Player player;
    private final GhostId blockId;
    private final Block against;

    /**
     * @param block   the block involved, never {@code null}
     * @param player  the responsible player, or {@code null} for machine-driven actions
     * @param blockId the block's content ID
     * @param against the supporting or clicked block, may be {@code null}
     */
    public BlockContextImpl(Block block, Player player, GhostId blockId, Block against) {
        this.block = block;
        this.player = player;
        this.blockId = blockId;
        this.against = against;
    }

    @Override
    public Block block() {
        return block;
    }

    @Override
    public org.bukkit.Location location() {
        return block.getLocation();
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public GhostId blockId() {
        return blockId;
    }

    @Override
    public Block against() {
        return against;
    }
}