package org.ghostplays.ghosttech.internal.command;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.ghostplays.ghosttech.api.command.CommandContext;
import org.ghostplays.ghosttech.api.command.GhostCommand;

/**
 * Bridges Bukkit's command system to the {@link org.ghostplays.ghosttech.api.command.CommandRegistry}.
 *
 * <h2>Dispatch</h2>
 * Each typed word is resolved against the registry, descending into subcommands while words keep
 * matching. The first word that does not match a subcommand becomes an argument of the deepest
 * command matched so far.
 *
 * <h2>Tab completion</h2>
 * Completion is delegated to the registry, which filters by permission and lets each command supply
 * its own candidates.
 */
public final class RootCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final CommandRegistryImpl registry;
    private final Component prefix;

    /**
     * @param plugin   the owning plugin
     * @param registry the command registry
     * @param prefix   the message prefix
     */
    public RootCommand(Plugin plugin, CommandRegistryImpl registry, Component prefix) {
        this.plugin = plugin;
        this.registry = registry;
        this.prefix = prefix;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments: show help so the command is never a dead end.
        if (args == null || args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String path = "";
        GhostCommand resolved = null;
        int consumed = 0;

        for (int index = 0; index < args.length; index++) {
            var next = registry.command(path, args[index]);
            if (next.isEmpty()) {
                break;
            }
            resolved = next.get();
            path = path.isEmpty() ? resolved.name() : path + "/" + resolved.name();
            consumed = index + 1;
        }

        if (resolved == null) {
            sendError(sender, "Unknown command: " + args[0]);
            return true;
        }
        if (!registry.visible(sender, resolved)) {
            sendError(sender, "You do not have permission to use this command.");
            return true;
        }
        if (resolved.playerOnly() && !(sender instanceof Player)) {
            sendError(sender, "This command can only be used by a player.");
            return true;
        }

        List<String> remaining = new ArrayList<>();
        for (int index = consumed; index < args.length; index++) {
            remaining.add(args[index]);
        }
        CommandContext context = registry.contextFor(sender, label, path, remaining);
        try {
            if (!resolved.execute(context)) {
                context.reply(Component.text("Usage: " + resolved.usage(), NamedTextColor.YELLOW));
            }
        } catch (RuntimeException exception) {
            // A module's bug must not surface as a raw stack trace to a player.
            sendError(sender, "That command failed. Check the server console for details.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Command /" + label + " " + String.join(" ", args) + " failed", exception);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
            String[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        try {
            return registry.complete(sender, args);
        } catch (RuntimeException exception) {
            // Completion failures are never worth interrupting a player's typing for.
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Tab completion failed for /" + alias, exception);
            return List.of();
        }
    }
    /**
     * Binds this executor and completer to the command declared in {@code plugin.yml}.
     *
     * <p>Aliases declared in the plugin descriptor are registered with the registry so they appear
     * in completion too.
     *
     * @param declaredAliases alias names from {@code plugin.yml}
     */
    public void register(List<String> declaredAliases) {
        PluginCommand pluginCommand =
                ((org.bukkit.plugin.java.JavaPlugin) plugin).getCommand("ghosttech");
        if (pluginCommand == null) {
            plugin.getLogger().severe(
                    "Command 'ghosttech' is missing from plugin.yml; commands will not work");
            return;
        }
        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
        for (String alias : declaredAliases) {
            registry.registerAlias(alias);
        }
    }

    /**
     * Sends the top-level help, listing only commands the sender may run.
     *
     * @param sender who asked
     * @param label  the label they typed
     */
    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(
                prefix.append(Component.text("GhostTech V2 commands", NamedTextColor.GOLD)));
        for (GhostCommand command : registry.commands(CommandRegistryImpl.ROOT)) {
            if (!registry.visible(sender, command)) {
                continue;
            }
            sender.sendMessage(Component.text("  /" + label + " " + command.name() + " - ",
                    NamedTextColor.YELLOW)
                    .append(command.description() == null
                            ? Component.empty()
                            : command.description().colorIfAbsent(NamedTextColor.GRAY)));
        }
    }

    /**
     * Sends a prefixed error message.
     *
     * @param sender  who to tell
     * @param message the reason
     */
    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(prefix.append(Component.text(message, NamedTextColor.RED)));
    }

    /** The message prefix used by all plugin output. */
    public Component prefix() {
        return prefix;
    }
}