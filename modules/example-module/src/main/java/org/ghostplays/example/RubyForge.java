package org.ghostplays.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.multiblock.MultiBlock;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockContext;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockPattern;
import org.ghostplays.ghosttech.api.multiblock.PatternRecord;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.api.util.GhostTextureId;

import java.util.List;

/**
 * A multiblock defined by a module.
 *
 * <h2>The shape</h2>
 * A 3x3 hollow frame of polished blocks around a central furnace, with the corners marked as
 * {@code corner} and the edges as {@code frame}. Keys matter because they tell the plugin which
 * clicks should reach the module: right-clicking the furnace fires {@code onInteract}, while the
 * frame is inert.
 */
public final class RubyForge implements MultiBlock {

    /** This multiblock's namespaced ID. */
    public static final GhostId ID = GhostId.parse("example:rubyforge");

    private static final String PERMISSION = "ghosttech.multiblock.example.rubyforge";

    /** Pattern keys this structure reacts to. */
    private static final String KEY_CORNER = "corner";
    private static final String KEY_FRAME = "frame";
    private static final String KEY_CORE = "core";

    private static final MultiBlockPattern PATTERN = buildPattern();

    /**
     * Builds the 3x3 frame shape.
     *
     * <p>The pattern is loose so the structure can form around existing terrain without the bounding
     * box needing to be empty.
     *
     * @return the pattern
     */
    private static MultiBlockPattern buildPattern() {
        List<MultiBlockPattern.Cell3> cells = List.of(
                new MultiBlockPattern.Cell3(0, 0, 0,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_CORNER),
                new MultiBlockPattern.Cell3(1, 0, 0,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_FRAME),
                new MultiBlockPattern.Cell3(2, 0, 0,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_CORNER),

                new MultiBlockPattern.Cell3(0, 0, 1,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_FRAME),
                // The middle of the frame is empty, so the centre cell is the machine core.
                new MultiBlockPattern.Cell3(1, 0, 1,
                        new MultiBlockPattern.Cell.Exact(Material.FURNACE), KEY_CORE),
                new MultiBlockPattern.Cell3(2, 0, 1,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_FRAME),

                new MultiBlockPattern.Cell3(0, 0, 2,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_CORNER),
                new MultiBlockPattern.Cell3(1, 0, 2,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_FRAME),
                new MultiBlockPattern.Cell3(2, 0, 2,
                        new MultiBlockPattern.Cell.Exact(Material.POLISHED_DEEPSLATE), KEY_CORNER));

        return PatternRecord.of("loose", cells);
    }

    @Override
    public GhostId id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.text("Ruby Forge", NamedTextColor.RED);
    }

    @Override
    public MultiBlockPattern pattern() {
        return PATTERN;
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public GhostTextureId texture() {
        return new GhostTextureId("example", "block/rubyforge");
    }

    @Override
    public List<String> interactionKeys() {
        return List.of(KEY_CORE);
    }

    @Override
    public void onForm(MultiBlockContext context) {
        // Fired once per instance, when the shape first appears. A module would typically announce
        // the machine is now active.
        if (context.player() != null) {
            context.player().sendMessage(Component.text(
                    "The Ruby Forge activates.", NamedTextColor.RED));
        }
    }

    @Override
    public void onInteract(MultiBlockContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        // Only the core key fires interaction, so clicking the frame does nothing.
        player.sendMessage(Component.text(
                "The Ruby Forge hums. It is ready for work.", NamedTextColor.GRAY));
    }

    @Override
    public void onBreak(MultiBlockContext context) {
        // Called when any part is broken. The plugin removes the instance automatically unless the
        // multiblock declares itself persistent.
    }

    @Override
    public boolean persistent() {
        // The structure stops existing once any part is broken.
        return false;
    }
}