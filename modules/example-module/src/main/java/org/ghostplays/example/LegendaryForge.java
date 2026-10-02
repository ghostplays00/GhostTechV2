package org.ghostplays.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.block.BlockContext;
import org.ghostplays.ghosttech.api.block.CustomBlock;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.api.util.GhostTextureId;

import java.util.Collection;
import java.util.List;

/**
 * A custom block defined by a module, not by the plugin.
 *
 * <p>Everything the plugin does for custom blocks, this class opts into simply by implementing
 * {@link CustomBlock} and registering it. That is the whole point of the modular design.
 */
public final class LegendaryForge implements CustomBlock {

    /** This block's namespaced ID. The namespace must match the module ID. */
    public static final GhostId ID = GhostId.parse("example:legendary_forge");

    private static final String PERMISSION = "ghosttech.block.example.legendary_forge";

    @Override
    public GhostId id() {
        return ID;
    }

    @Override
    public Material material() {
        // A vanilla material is all that is needed, so the block works on any server with no
        // resource pack and no client mod.
        return Material.ANVIL;
    }

    @Override
    public Component displayName() {
        return Component.text("Legendary Forge", NamedTextColor.GOLD);
    }

    @Override
    public List<Component> lore() {
        return List.of(
                Component.text("Smelts anything you bring it.", NamedTextColor.GRAY),
                Component.text("Module: example", NamedTextColor.DARK_GRAY));
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public GhostTextureId texture() {
        // Declared now so a future resource pack needs no code change. Until such a pack exists the
        // vanilla anvil texture is used.
        return new GhostTextureId("example", "block/legendary_forge");
    }

    @Override
    public Collection<ItemStack> drops(BlockContext context) {
        // The block drops itself, so it can be picked back up and re-placed.
        return List.of(new ItemStack(Material.ANVIL));
    }

    @Override
    public void onPlace(BlockContext context) {
        // Confirmation for the placer, so placement never feels silent.
        if (context.player() != null) {
            context.player().sendMessage(Component.text(
                    "Legendary Forge placed.", NamedTextColor.GOLD));
        }
    }

    @Override
    public boolean onBreak(BlockContext context) {
        // Returning true allows the break. Returning false would cancel it, which is where a
        // protection or a quest requirement would go.
        return true;
    }

    @Override
    public void onInteract(BlockContext context) {
        if (context.player() == null) {
            return;
        }
        context.player().sendMessage(Component.text(
                "The Legendary Forge hums quietly.", NamedTextColor.YELLOW));
    }

    @Override
    public boolean pistonMovable() {
        // Anvils cannot be pushed, and a custom block should behave like its material unless the
        // module says otherwise.
        return false;
    }
}