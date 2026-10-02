package org.ghostplays.ghosttech.internal.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.ghostplays.ghosttech.api.command.CommandContext;
import org.ghostplays.ghosttech.api.command.CommandRegistry;
import org.ghostplays.ghosttech.api.command.GhostCommand;
import org.ghostplays.ghosttech.api.permission.PermissionService;

/**
 * Command registry with permission-aware tab completion.
 *
 * <h2>How completion resolves</h2>
 * Completion walks the typed arguments one at a time, descending into subcommands as long as each
 * fully typed word matches a registered command name or alias. Once it reaches a command that has
 * no children, it delegates to that command's own {@link GhostCommand#suggest}.
 *
 * <p>Commands the sender cannot run are filtered out, so a player never sees a command in their
 * completion list that would then refuse to run.
 */
public final class CommandRegistryImpl implements CommandRegistry {

    /** The root path, holding top-level commands. */
    public static final String ROOT = "";

    private final Map<String, Map<String, GhostCommand>> commands = new ConcurrentHashMap<>();
    private final List<String> aliases = new ArrayList<>();
    private final PermissionService permissions;
    private final Component prefix;

    /**
     * @param permissions used to decide whether a command is visible to the sender
     * @param prefix     message prefix handed to command contexts
     */
    public CommandRegistryImpl(PermissionService permissions, Component prefix) {
        this.permissions = permissions;
        this.prefix = prefix;
        commands.put(ROOT, new ConcurrentHashMap<>());
    }

    @Override
    public boolean register(String parent, GhostCommand command) {
        String path = normalise(parent);
        Map<String, GhostCommand> siblings = commands.computeIfAbsent(path, key -> new ConcurrentHashMap<>());
        if (siblings.containsKey(command.name().toLowerCase(Locale.ROOT))) {
            return false;
        }
        siblings.put(command.name().toLowerCase(Locale.ROOT), command);
        // Aliases are registered as extra names pointing at the same instance, so completion and
        // dispatch treat them identically.
        for (String alias : command.aliases()) {
            if (alias != null && !alias.isBlank()) {
                siblings.putIfAbsent(alias.toLowerCase(Locale.ROOT), command);
            }
        }
        return true;
    }

    @Override
    public boolean unregister(String parent, String name) {
        Map<String, GhostCommand> siblings = commands.get(normalise(parent));
        if (siblings == null) {
            return false;
        }
        GhostCommand removed = siblings.remove(name.toLowerCase(Locale.ROOT));
        // Remove the aliases of anything registered under this name too.
        siblings.values().removeIf(command -> command.name().equalsIgnoreCase(name));
        commands.keySet().removeIf(child ->
                child.equalsIgnoreCase(normalise(parent) + "/" + name));
        return removed != null;
    }

    @Override
    public Collection<GhostCommand> commands(String parent) {
        Map<String, GhostCommand> siblings = commands.get(normalise(parent));
        if (siblings == null) {
            return List.of();
        }
        // De-duplicate by primary name, since aliases share an instance with their primary command.
        Set<GhostCommand> unique = new LinkedHashSet<>(siblings.values());
        List<GhostCommand> ordered = new ArrayList<>(unique);
        ordered.sort((left, right) -> left.name().compareTo(right.name()));
        return List.copyOf(ordered);
    }

    @Override
    public Optional<GhostCommand> command(String parent, String name) {
        Map<String, GhostCommand> siblings = commands.get(normalise(parent));
        if (siblings == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(siblings.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void registerAlias(String alias) {
        if (alias != null && !alias.isBlank() && !aliases.contains(alias.toLowerCase(Locale.ROOT))) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public List<String> aliases() {
        return List.copyOf(aliases);
    }

    @Override
    public boolean visible(CommandSender sender, GhostCommand command) {
        if (command == null) {
            return false;
        }
        String node = command.permission();
        return node == null || node.isBlank() || permissions.hasPermission(sender, node);
    }
    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        // The final element is what the player is currently typing, possibly empty.
        String current = args[args.length - 1];
        String path = ROOT;
        GhostCommand resolved = null;

        // Descend through fully typed subcommand names. The last argument is never treated as a
        // complete name, because it may still be mid-typing.
        for (int index = 0; index < args.length - 1; index++) {
            Optional<GhostCommand> next = command(path, args[index]);
            if (next.isEmpty()) {
                // Not a subcommand name: the rest belongs to the command resolved so far.
                break;
            }
            resolved = next.get();
            path = path.isEmpty() ? resolved.name() : path + "/" + resolved.name();
        }

        List<String> candidates = new ArrayList<>();

        if (resolved == null) {
            // Still choosing a subcommand: offer only the names the sender may actually run.
            for (GhostCommand command : commands(path)) {
                if (visible(sender, command)) {
                    candidates.add(command.name());
                }
            }
        } else {
            if (!visible(sender, resolved)) {
                return List.of();
            }
            // Build the argument list the command sees, matching its own indexing.
            List<String> commandArgs = new ArrayList<>();
            int consumed = countPathWords(args, path);
            for (int index = consumed; index < args.length; index++) {
                commandArgs.add(args[index]);
            }
            if (commandArgs.isEmpty()) {
                commandArgs.add("");
            }
            CommandContext context =
                    new CommandContextImpl(sender, label(path), commandArgs, prefix);
            try {
                candidates.addAll(resolved.suggest(context));
            } catch (RuntimeException exception) {
                // A faulty suggest implementation must not break completion for every command.
                return List.of();
            }
        }

        String lowerCurrent = current.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null
                    && candidate.toLowerCase(Locale.ROOT).startsWith(lowerCurrent)) {
                filtered.add(candidate);
            }
        }
        filtered.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(filtered);
    }

    /**
     * How many leading arguments were consumed by walking the subcommand path.
     *
     * @param args the raw arguments, where index 0 is the command label
     * @param path the resolved subcommand path
     * @return the number of arguments belonging to the path
     */
    private int countPathWords(String[] args, String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        int depth = path.split("/").length;
        // args[0] is the command label itself, which a context never sees.
        return Math.min(depth, Math.max(0, args.length - 1));
    }

    /**
     * Builds a context for executing a command.
     *
     * @param sender who ran it
     * @param label  the label typed
     * @param path   the resolved subcommand path, {@link #ROOT} for top level
     * @param args   arguments after the label
     * @return a ready context
     */
    public CommandContext contextFor(CommandSender sender, String label, String path, List<String> args) {
        return new CommandContextImpl(sender, label, args, prefix);
    }

    private static String normalise(String parent) {
        return parent == null ? ROOT : parent.trim().toLowerCase(Locale.ROOT);
    }

    private static String label(String path) {
        return path.isEmpty() ? "ghosttech" : path.substring(path.lastIndexOf('/') + 1);
    }

    /** Every registered path, used by diagnostics and help output. */
    public Set<String> paths() {
        return Set.copyOf(commands.keySet());
    }
}