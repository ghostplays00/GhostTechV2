package org.ghostplays.ghosttech.internal.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ghostplays.ghosttech.api.block.CustomBlock;
import org.ghostplays.ghosttech.api.command.CommandContext;
import org.ghostplays.ghosttech.api.command.GhostCommand;
import org.ghostplays.ghosttech.api.multiblock.MultiBlock;
import org.ghostplays.ghosttech.api.permission.PermissionDefinition;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.GhostTechPlugin;
import org.ghostplays.ghosttech.internal.permission.PermissionServiceImpl;
import org.ghostplays.ghosttech.internal.util.Messages;

/**
 * The commands the plugin itself provides.
 *
 * <p>Each is a {@link GhostCommand} like any module command, so they appear in help and tab
 * completion through exactly the same path a module's commands do. That doubles as proof the
 * command API works for third-party modules.
 */
public final class BuiltinCommands {

    private final GhostTechPlugin plugin;

    /**
     * @param plugin the owning plugin, used to reach the services
     */
    public BuiltinCommands(GhostTechPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers every built-in command.
     *
     * @param registry the registry to register into
     */
    public void registerAll(CommandRegistryImpl registry) {
        registry.register(new HelpCommand());
        registry.register(new VersionCommand());
        registry.register(new BlockCommand());
        registry.register(new MultiBlockCommand());
        registry.register(new PermissionsCommand());
        registry.register(new ReloadCommand());
    }

    /** Lists the available commands. */
    private final class HelpCommand implements GhostCommand {

        @Override
        public String name() {
            return "help";
        }

        @Override
        public List<String> aliases() {
            return List.of("?");
        }

        @Override
        public Component description() {
            return Component.text("Show this list.");
        }

        @Override
        public String usage() {
            return "/ghosttech help";
        }

        @Override
        public boolean execute(CommandContext context) {
            context.reply(Component.text("Commands", NamedTextColor.GOLD));
            for (var command : plugin.commandRegistry().commands(CommandRegistryImpl.ROOT)) {
                if (!plugin.commandRegistry().visible(context.sender(), command)) {
                    continue;
                }
                context.reply(Component.text("  " + command.name() + " - ", NamedTextColor.YELLOW)
                        .append(command.description() == null
                                ? Component.empty()
                                : command.description().colorIfAbsent(NamedTextColor.GRAY)));
            }
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            return List.of();
        }
    }

    /** Reports the plugin and API versions. */
    private final class VersionCommand implements GhostCommand {

        @Override
        public String name() {
            return "version";
        }

        @Override
        public List<String> aliases() {
            return List.of("ver");
        }

        @Override
        public Component description() {
            return Component.text("Show the plugin and module versions.");
        }

        @Override
        public boolean execute(CommandContext context) {
            context.reply(Component.text("GhostTech V2 " + plugin.getPluginMeta().getVersion()
                    + " (API " + plugin.api().apiVersion() + ")", NamedTextColor.GOLD));
            int enabled = 0;
            for (var handle : plugin.api().modules().modules()) {
                if (handle.state().active()) {
                    enabled++;
                }
            }
            context.reply(Component.text("Modules: " + enabled + " enabled, "
                    + plugin.blockRegistry().blocks().size() + " custom blocks, "
                    + plugin.multiBlockRegistry().multiBlocks().size() + " multiblocks",
                    NamedTextColor.GRAY));
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            return List.of();
        }
    }
    /** Gives custom blocks and lists them. */
    private final class BlockCommand implements GhostCommand {

        @Override
        public String name() {
            return "block";
        }

        @Override
        public List<String> aliases() {
            return List.of("blocks");
        }

        @Override
        public Component description() {
            return Component.text("List and give custom blocks.");
        }

        @Override
        public String usage() {
            return "/ghosttech block <list|give> [id] [amount]";
        }

        @Override
        public String permission() {
            return "ghosttech.command.block";
        }

        @Override
        public boolean playerOnly() {
            return false;
        }

        @Override
        public boolean execute(CommandContext context) {
            String action = context.argument(0) == null ? "list" : context.argument(0).toLowerCase(Locale.ROOT);

            if (action.equals("list")) {
                var blocks = plugin.blockRegistry().blocks();
                if (blocks.isEmpty()) {
                    context.reply(Component.text("No custom blocks are registered.", NamedTextColor.GRAY));
                    return true;
                }
                context.reply(Component.text("Custom blocks (" + blocks.size() + ")",
                        NamedTextColor.GOLD));
                for (CustomBlock block : blocks) {
                    context.reply(Component.text("  " + block.id(), NamedTextColor.YELLOW)
                            .append(Component.text(" -> " + block.material().name(),
                                    NamedTextColor.GRAY)));
                }
                return true;
            }

            if (action.equals("give")) {
                String rawId = context.argument(1);
                if (rawId == null) {
                    return false;
                }
                GhostId id = GhostId.parseOrNull(rawId);
                if (id == null) {
                    context.error(Component.text("Invalid block ID: " + rawId));
                    return true;
                }
                var definition = plugin.blockRegistry().block(id);
                if (definition.isEmpty()) {
                    context.error(Component.text("No such custom block: " + id));
                    return true;
                }
                Player player = context.player().orElse(null);
                if (player == null) {
                    context.error(Component.text("This subcommand needs a player to give items to."));
                    return true;
                }
                int amount = Math.max(1, Math.min(64, context.integer(2, 1)));
                ItemStack item = plugin.blockRegistry().createItem(definition.get());
                item.setAmount(amount);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    // The inventory was full, so the remainder is dropped rather than deleted.
                    leftover.values().forEach(stack ->
                            player.getWorld().dropItemNaturally(player.getLocation(), stack));
                    context.reply(Component.text("Inventory was full; the remainder was dropped.",
                            NamedTextColor.YELLOW));
                }
                context.success(Component.text("Gave " + amount + "x " + id));
                return true;
            }

            context.error(Component.text("Unknown subcommand: " + action));
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            List<String> args = context.arguments();
            // While the action itself is being typed, offer the subcommands.
            if (args.size() <= 1) {
                return List.of("list", "give");
            }
            // Otherwise offer block IDs, which is what makes this command discoverable.
            if (args.size() == 2) {
                List<String> ids = new ArrayList<>();
                for (CustomBlock block : plugin.blockRegistry().blocks()) {
                    ids.add(block.id().toString());
                }
                return ids;
            }
            return List.of();
        }
    }
    /** Lists multiblocks and formed instances. */
    private final class MultiBlockCommand implements GhostCommand {

        @Override
        public String name() {
            return "multiblock";
        }

        @Override
        public List<String> aliases() {
            return List.of("mb", "multiblocks");
        }

        @Override
        public Component description() {
            return Component.text("List multiblocks and formed instances.");
        }

        @Override
        public String usage() {
            return "/ghosttech multiblock <list|instances>";
        }

        @Override
        public String permission() {
            return "ghosttech.command.multiblock";
        }

        @Override
        public boolean execute(CommandContext context) {
            String action = context.argument(0) == null ? "list"
                    : context.argument(0).toLowerCase(Locale.ROOT);

            if (action.equals("list")) {
                var multiBlocks = plugin.multiBlockRegistry().multiBlocks();
                if (multiBlocks.isEmpty()) {
                    context.reply(Component.text("No multiblocks are registered.", NamedTextColor.GRAY));
                    return true;
                }
                context.reply(Component.text("Multiblocks (" + multiBlocks.size() + ")",
                        NamedTextColor.GOLD));
                for (MultiBlock multiBlock : multiBlocks) {
                    context.reply(Component.text("  " + multiBlock.id(), NamedTextColor.YELLOW)
                            .append(Component.text(" (" + multiBlock.pattern().kind() + ", "
                                    + multiBlock.pattern().cells().size() + " cells)",
                                    NamedTextColor.GRAY)));
                }
                return true;
            }

            if (action.equals("instances")) {
                context.reply(Component.text("Formed multiblock instances: "
                        + plugin.multiBlockRegistry().instanceCount(), NamedTextColor.GOLD));
                return true;
            }

            context.error(Component.text("Unknown subcommand: " + action));
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            return context.arguments().size() <= 1 ? List.of("list", "instances") : List.of();
        }
    }
    /** Lists permission nodes and groups. */
    private final class PermissionsCommand implements GhostCommand {

        @Override
        public String name() {
            return "permissions";
        }

        @Override
        public List<String> aliases() {
            return List.of("perms", "perm");
        }

        @Override
        public Component description() {
            return Component.text("List permission nodes and groups.");
        }

        @Override
        public String usage() {
            return "/ghosttech permissions <nodes|groups|check> [node]";
        }

        @Override
        public String permission() {
            return "ghosttech.command.permissions";
        }

        @Override
        public boolean execute(CommandContext context) {
            PermissionServiceImpl permissions = plugin.permissionService();
            String action = context.argument(0) == null ? "nodes"
                    : context.argument(0).toLowerCase(Locale.ROOT);

            if (action.equals("nodes")) {
                var definitions = permissions.definitions();
                context.reply(Component.text("Permission nodes (" + definitions.size() + ")",
                        NamedTextColor.GOLD));
                for (PermissionDefinition definition : definitions) {
                    context.reply(Component.text("  " + definition.node(), NamedTextColor.YELLOW)
                            .append(Component.text(" - " + Messages.plain(definition.description()),
                                    NamedTextColor.GRAY)));
                }
                return true;
            }

            if (action.equals("groups")) {
                var groups = permissions.groups();
                context.reply(Component.text("Permission groups (" + groups.size() + ")",
                        NamedTextColor.GOLD));
                for (var group : groups) {
                    context.reply(Component.text("  " + group.name(), NamedTextColor.YELLOW)
                            .append(Component.text(" - " + group.nodes().size() + " nodes",
                                    NamedTextColor.GRAY)));
                }
                return true;
            }

            if (action.equals("check")) {
                String node = context.argument(1);
                if (node == null) {
                    return false;
                }
                boolean allowed = permissions.hasPermission(context.sender(), node);
                context.reply(Component.text("You " + (allowed ? "have" : "do not have") + " "
                        + node, allowed ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }

            context.error(Component.text("Unknown subcommand: " + action));
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            if (context.arguments().size() > 1) {
                // Offer registered nodes so the check subcommand is usable without memorisation.
                List<String> nodes = new ArrayList<>();
                for (PermissionDefinition definition : plugin.permissionService().definitions()) {
                    nodes.add(definition.node());
                }
                return nodes;
            }
            return List.of("nodes", "groups", "check");
        }
    }

    /** Reloads configuration and modules. */
    private final class ReloadCommand implements GhostCommand {

        @Override
        public String name() {
            return "reload";
        }

        @Override
        public Component description() {
            return Component.text("Reload configuration and modules.");
        }

        @Override
        public String usage() {
            return "/ghosttech reload";
        }

        @Override
        public String permission() {
            return "ghosttech.command.reload";
        }

        @Override
        public boolean execute(CommandContext context) {
            context.reply(Component.text("Reloading GhostTech...", NamedTextColor.YELLOW));
            plugin.reloadEverything();
            context.success(Component.text("Reload complete."));
            return true;
        }

        @Override
        public List<String> suggest(CommandContext context) {
            return List.of();
        }
    }
}