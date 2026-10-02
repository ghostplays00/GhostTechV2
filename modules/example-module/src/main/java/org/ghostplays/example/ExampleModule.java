package org.ghostplays.example;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.GhostTechApi;
import org.ghostplays.ghosttech.api.command.CommandContext;
import org.ghostplays.ghosttech.api.command.GhostCommand;
import org.ghostplays.ghosttech.api.content.ContentType;
import org.ghostplays.ghosttech.api.gui.GhostGui;
import org.ghostplays.ghosttech.api.module.GhostTechModule;
import org.ghostplays.ghosttech.api.module.ModuleContext;
import org.ghostplays.ghosttech.api.module.ModuleDescriptor;
import org.ghostplays.ghosttech.api.permission.DefaultValue;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Reference module demonstrating everything the plugin API offers.
 *
 * <p>Deliberately complete rather than minimal: it registers a custom block, a multiblock, a command
 * with tab completion, a paginated GUI, permission nodes, and it both subscribes to and publishes on
 * the module message bus.
 *
 * <h2>Load and enable</h2>
 * All registration happens in {@link #onLoad(ModuleContext)}. That is the correct place: the plugin
 * reads module content after every module has loaded, so anything registered in {@code onEnable}
 * would be invisible to a module querying it during its own load.
 */
public final class ExampleModule implements GhostTechModule {

    private ModuleContext context;
    private GhostTechApi api;

    @Override
    public void onLoad(ModuleContext context) {
        this.context = context;
        this.api = context.api();
        ModuleDescriptor descriptor = context.descriptor();

        // 1. Custom block, registered under this module's own namespace.
        api.blocks().register(new LegendaryForge());

        // 2. Multiblock.
        api.multiblocks().register(new RubyForge());

        // 3. Permission nodes, so /ghosttech perms lists them with descriptions.
        api.permissions().register(
                "ghosttech.block.example.legendary_forge",
                Component.text("Place, break and use the Legendary Forge"),
                DefaultValue.OP);
        api.permissions().register(
                "ghosttech.multiblock.example.rubyforge",
                Component.text("Form and use the Ruby Forge multiblock"),
                DefaultValue.OP);
        api.permissions().register(
                "ghosttech.command.example.forge",
                Component.text("Use the /forge command"),
                DefaultValue.OP);

        // 4. Command with tab completion.
        api.commands().register(new ForgeCommand());

        // 5. Subscribe to other modules. Modules never reference each other's classes directly; they
        //    communicate over channels, which keeps every module independently removable.
        api.messaging().subscribe("example:request-craft", descriptor.id(), payload ->
                // Returning a value makes this subscriber answer requests as well as receive them.
                "example module acknowledged: " + payload);

        // 6. Announce readiness on the shared channel, for any module that cares to listen.
        api.messaging().publish("example:ready", descriptor.id(), descriptor.id());

        context.logger().info(
                "Registered content for namespace '" + descriptor.id().namespace() + "'");
    }

    @Override
    public void onEnable() {
        context.logger().info("Example module enabled.");
    }

    @Override
    public void onDisable() {
        // Subscriptions are dropped by the plugin automatically; anything else needs releasing here.
        context.logger().info("Example module disabled.");
    }

    /**
     * Opens a paginated GUI listing the module's blocks and multiblocks.
     *
     * @param player the viewer
     */
    public void openContentMenu(Player player) {
        GhostGui.Paginated gui = api.gui().paginated(6, 45,
                Component.text("Example Content", NamedTextColor.GOLD));

        List<ItemStack> items = new java.util.ArrayList<>();

        ItemStack forge = new ItemStack(Material.ANVIL);
        forge.editMeta(meta -> meta.displayName(
                Component.text(LegendaryForge.ID.toString(), NamedTextColor.GOLD)
                        .append(Component.text(" (custom block)", NamedTextColor.GRAY))));
        items.add(forge);

        ItemStack ruby = new ItemStack(Material.FURNACE);
        ruby.editMeta(meta -> meta.displayName(
                Component.text(RubyForge.ID.toString(), NamedTextColor.RED)
                        .append(Component.text(" (multiblock)", NamedTextColor.GRAY))));
        items.add(ruby);

        // setContent replaces the page contents, which is what a menu wants.
        gui.setContent(items);

        // A dialog is the native alternative to a confirm menu, and cannot be spoofed with a renamed
        // item the way a chest menu can.
        gui.addItem(new ItemStack(Material.PAPER), event -> {
            if (event.getWhoClicked() instanceof Player viewer) {
                api.dialogs().confirmation(viewer,
                        Component.text("Forge information", NamedTextColor.GOLD),
                        Component.text("The Legendary Forge smelts anything.\n"
                                + "The Ruby Forge forms from a 3x3 frame around a furnace."),
                        (confirmedPlayer, confirmed) -> confirmedPlayer.sendMessage(
                                Component.text(confirmed
                                                ? "Use /forge info <id> for details."
                                                : "Cancelled.",
                                        NamedTextColor.GRAY)));
            }
        });

        gui.open(player);
    }
    /**
     * A module-provided command, demonstrating tab completion over custom content IDs.
     *
     * <p>The plugin's own commands go through this same interface, so a module command behaves exactly
     * like a built-in one in help output and completion.
     */
    private final class ForgeCommand implements GhostCommand {

        @Override
        public String name() {
            return "forge";
        }

        @Override
        public List<String> aliases() {
            return List.of("forges");
        }

        @Override
        public Component description() {
            return Component.text("Example module commands.");
        }

        @Override
        public String usage() {
            return "/forge <list|menu|info> [id]";
        }

        @Override
        public String permission() {
            return "ghosttech.command.example.forge";
        }

        @Override
        public boolean execute(CommandContext commandContext) {
            String action = commandContext.argument(0) == null ? "list"
                    : commandContext.argument(0).toLowerCase(java.util.Locale.ROOT);

            switch (action) {
                case "list" -> {
                    commandContext.reply(Component.text("Example content:", NamedTextColor.GOLD));
                    for (GhostId id : api.content().ids(ContentType.BLOCK)) {
                        commandContext.reply(Component.text("  " + id, NamedTextColor.YELLOW));
                    }
                    for (GhostId id : api.content().ids(ContentType.MULTIBLOCK)) {
                        commandContext.reply(Component.text("  " + id, NamedTextColor.RED));
                    }
                    return true;
                }
                case "menu" -> {
                    Player player = commandContext.player().orElse(null);
                    if (player == null) {
                        commandContext.error(
                                Component.text("The menu can only be opened by a player."));
                        return true;
                    }
                    openContentMenu(player);
                    return true;
                }
                case "info" -> {
                    String rawId = commandContext.argument(1);
                    if (rawId == null) {
                        return false;
                    }
                    GhostId id = GhostId.parseOrNull(rawId);
                    if (id == null) {
                        commandContext.error(Component.text("Invalid ID: " + rawId));
                        return true;
                    }
                    var block = api.blocks().block(id);
                    if (block.isPresent()) {
                        commandContext.reply(Component.text(id + " uses material "
                                + block.get().material().name(), NamedTextColor.GRAY));
                        return true;
                    }
                    var multiBlock = api.multiblocks().multiBlock(id);
                    if (multiBlock.isPresent()) {
                        commandContext.reply(Component.text(id + " is a "
                                + multiBlock.get().pattern().kind() + " multiblock with "
                                + multiBlock.get().pattern().cells().size() + " cells",
                                NamedTextColor.GRAY));
                        return true;
                    }
                    commandContext.error(Component.text("No such content: " + id));
                    return true;
                }
                default -> {
                    commandContext.error(Component.text("Unknown action: " + action));
                    return true;
                }
            }
        }

        @Override
        public List<String> suggest(CommandContext commandContext) {
            List<String> args = commandContext.arguments();
            if (args.size() <= 1) {
                return List.of("list", "menu", "info");
            }
            // Offering real content IDs is what makes the command discoverable by typing.
            if (args.size() == 2 && args.get(0).equalsIgnoreCase("info")) {
                List<String> ids = new java.util.ArrayList<>();
                for (GhostId id : api.content().ids(ContentType.BLOCK)) {
                    ids.add(id.toString());
                }
                for (GhostId id : api.content().ids(ContentType.MULTIBLOCK)) {
                    ids.add(id.toString());
                }
                return ids;
            }
            return List.of();
        }
    }
}