package org.ghostplays.ghosttech.internal.listener;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.block.BlockContext;
import org.ghostplays.ghosttech.api.block.CustomBlock;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.block.BlockContextImpl;
import org.ghostplays.ghosttech.internal.block.CustomBlockRegistryImpl;
import org.ghostplays.ghosttech.internal.multiblock.MultiBlockRegistryImpl;
import org.ghostplays.ghosttech.internal.permission.PermissionServiceImpl;

/**
 * Handles placement, breaking and interaction of custom blocks and multiblocks.
 *
 * <h2>Why custom blocks need no custom item</h2>
 * A custom block item is an ordinary item carrying its content ID in persistent data. This listener
 * reads that ID on placement and records the position, which is the only extra state a vanilla block
 * needs in order to behave as a custom block.
 *
 * <h2>Priorities</h2>
 * Handlers run at {@link EventPriority#NORMAL}, after protection plugins at {@code LOW}. Cancelling
 * here therefore respects an earlier plugin's decision rather than overriding it.
 */
public final class BlockListener implements Listener {

    private final CustomBlockRegistryImpl blocks;
    private final MultiBlockRegistryImpl multiblocks;
    private final PermissionServiceImpl permissions;

    /**
     * @param blocks      the custom block registry
     * @param multiblocks the multiblock registry
     * @param permissions used to check placement and break permissions
     */
    public BlockListener(CustomBlockRegistryImpl blocks, MultiBlockRegistryImpl multiblocks,
            PermissionServiceImpl permissions) {
        this.blocks = blocks;
        this.multiblocks = multiblocks;
        this.permissions = permissions;
    }
    /**
     * Tracks a custom block placed from a custom block item.
     *
     * <p>After placement every multiblock pattern is re-checked around the new position, since one
     * block can be the piece that completes a structure.
     *
     * @param event the placement event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Optional<GhostId> id = blocks.itemId(event.getItemInHand());
        if (id.isEmpty()) {
            return;
        }
        Optional<CustomBlock> definition = blocks.block(id.get());
        if (definition.isEmpty()) {
            // The item outlived the module that created it, e.g. after that module was removed.
            deny(player, "This block type is no longer available and was not placed.");
            event.setCancelled(true);
            return;
        }

        CustomBlock block = definition.get();
        if (!can(player, block.permission())) {
            deny(player, "You do not have permission to place this block.");
            event.setCancelled(true);
            return;
        }

        Block placed = event.getBlockPlaced();
        blocks.track(placed.getLocation(), block.id());
        blocks.setPlacer(placed.getLocation(), player);

        block.onPlace(new BlockContextImpl(placed, player, block.id(), event.getBlockAgainst()));
        multiblocks.checkForm(placed.getLocation());
    }

    /**
     * Removes tracking when a custom block is broken, and spawns its drops.
     *
     * @param event the break event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<GhostId> id = blocks.blockIdAt(block.getLocation());
        if (id.isEmpty()) {
            return;
        }
        Optional<CustomBlock> definition = blocks.block(id.get());
        if (definition.isEmpty()) {
            // The owning module is gone, so the stale tracking row is cleaned up.
            blocks.untrack(block.getLocation());
            return;
        }

        CustomBlock customBlock = definition.get();
        Player player = event.getPlayer();
        BlockContext context = new BlockContextImpl(block, player, customBlock.id(), null);

        if (!can(player, customBlock.permission())) {
            deny(player, "You do not have permission to break this block.");
            event.setCancelled(true);
            return;
        }
        if (!customBlock.breakableInSurvival() && player.getGameMode() == GameMode.SURVIVAL) {
            deny(player, "This block cannot be broken in survival mode.");
            event.setCancelled(true);
            return;
        }
        if (!customBlock.onBreak(context)) {
            event.setCancelled(true);
            return;
        }

        // The world drops nothing on its own, because the block is a vanilla material, so the
        // definition decides exactly what appears.
        for (ItemStack drop : customBlock.drops(context)) {
            if (drop != null && !drop.getType().isAir()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }

        // Multiblocks are notified before tracking is dropped, so the module still knows the shape.
        multiblocks.notifyBreak(block, player);
        blocks.untrack(block.getLocation());
    }
    /**
     * Routes a right-click to a multiblock part, or failing that to a custom block.
     *
     * <p>Multiblocks are checked first so a machine's input side wins over the block it is built from.
     *
     * @param event the interaction event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        if (multiblocks.interact(block, player)) {
            event.setCancelled(true);
            return;
        }

        Optional<CustomBlock> customBlock = blocks.blockIdAt(block.getLocation())
                .flatMap(blocks::block);
        if (customBlock.isEmpty()) {
            return;
        }
        if (!can(player, customBlock.get().permission())) {
            deny(player, "You do not have permission to use this block.");
            event.setCancelled(true);
            return;
        }
        customBlock.get().onInteract(
                new BlockContextImpl(block, player, customBlock.get().id(), block));
        // Cancelled so the block does not also run its vanilla behaviour, e.g. opening a chest.
        event.setCancelled(true);
    }

    /**
     * Reports loss of a custom block to its module when it burns away.
     *
     * @param event the burn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        reportDestroyed(event.getBlock());
    }

    /**
     * Reports loss of custom blocks listed in an explosion.
     *
     * <p>Runs at {@code MONITOR} because the server has already decided the outcome; this only
     * notifies the owning module.
     *
     * @param event the explosion event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            reportDestroyed(block);
        }
    }

    /**
     * Notifies a module that a tracked block was destroyed, then clears its tracking row.
     *
     * @param block the destroyed block
     */
    private void reportDestroyed(Block block) {
        blocks.blockIdAt(block.getLocation())
                .flatMap(blocks::block)
                .ifPresent(customBlock -> {
                    customBlock.onDestroyed(
                            new BlockContextImpl(block, null, customBlock.id(), null));
                    blocks.untrack(block.getLocation());
                });
    }

    /**
     * Checks a permission node, treating a {@code null} node as allowed.
     *
     * @param player the acting player
     * @param node   the node, may be {@code null}
     * @return {@code true} when allowed
     */
    private boolean can(Player player, String node) {
        return node == null || node.isBlank() || permissions.hasPermission(player, node);
    }

    /**
     * Sends the standard denial message.
     *
     * @param player  the acting player
     * @param message the reason
     */
    private static void deny(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}