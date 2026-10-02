package org.ghostplays.ghosttech.api.command;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;

/**
 * Registry of commands and subcommands, including tab completion.
 *
 * <p>Commands are registered under a parent path. The root path {@code ""} holds top-level
 * commands; {@code "block"} holds everything under {@code /ghosttech block ...}.
 *
 * <p>Tab completion is handled centrally, so a module only implements {@link GhostCommand#suggest}
 * and gets consistent, permission-filtered completion for free.
 */
public interface CommandRegistry {

    /**
     * Registers a command under a parent path.
     *
     * @param parent  parent path, {@code ""} for top level, e.g. {@code "block"}
     * @param command the command
     * @return {@code true} if registered, {@code false} if the name was taken in that path
     */
    boolean register(String parent, GhostCommand command);

    /** Registers a top-level command. */
    default boolean register(GhostCommand command) {
        return register("", command);
    }

    /**
     * Removes a command and its whole subpath.
     *
     * <p>Called by the plugin when a module is unloaded.
     *
     * @return {@code true} if something was removed
     */
    boolean unregister(String parent, String name);

    /** Every command at a parent path. */
    Collection<GhostCommand> commands(String parent);

    /** Looks up a command by name under a parent path. */
    Optional<GhostCommand> command(String parent, String name);

    /** Registers the root command alias, e.g. making {@code /gt} work alongside {@code /ghosttech}. */
    void registerAlias(String alias);

    /** Aliases currently pointing at the root command. */
    List<String> aliases();

    /**
     * Computes completions for a partially typed command.
     *
     * <p>Used by the plugin's {@code TabCompleter}. Results already respect permissions and are
     * filtered against {@code args}.
     *
     * @param sender the sender completing
     * @param args   the arguments typed so far, the last one possibly partial
     * @return matching completions, never {@code null}
     */
    List<String> complete(CommandSender sender, String[] args);

    /**
     * Whether the sender may see a command at all.
     *
     * <p>Used to hide commands from tab completion when the player cannot run them.
     */
    boolean visible(CommandSender sender, GhostCommand command);
}