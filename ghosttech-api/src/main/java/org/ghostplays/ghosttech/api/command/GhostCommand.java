package org.ghostplays.ghosttech.api.command;

import java.util.List;

/**
 * A command or subcommand.
 *
 * <p>Commands are registered by modules during load and appear automatically in tab completion and
 * help output. Both {@link #execute(CommandContext)} and {@link #suggest(CommandContext)} are
 * required to be complete: a command with no suggestions will feel broken to players, since tab
 * completion is how they discover arguments.
 *
 * <p>Implementations should be stateless, or hold only immutable state, because a single instance
 * serves every player.
 */
public interface GhostCommand {

    /** The primary name, without any leading slash. */
    String name();

    /** Alternative names, may be empty. */
    default List<String> aliases() {
        return List.of();
    }

    /** One-line description shown in help. */
    net.kyori.adventure.text.Component description();

    /** Usage string shown on error, e.g. {@code /ghosttech block give <id> [amount]}. */
    default String usage() {
        return "/" + name();
    }

    /**
     * Permission needed to run this command.
     *
     * @return the node, or {@code null} when everyone may run it
     */
    default String permission() {
        return null;
    }

    /** {@code true} when only players may run this command. Console gets a clear error instead. */
    default boolean playerOnly() {
        return false;
    }

    /**
     * Runs the command.
     *
     * @param context the invocation context
     * @return {@code false} to have the framework print usage, {@code true} when handled
     */
    boolean execute(CommandContext context);

    /**
     * Provides tab completions for the argument currently being typed.
     *
     * <p>Returning an empty list means "no suggestions". The framework filters results by the
     * current input, so return the full candidate list rather than pre-filtering it.
     *
     * @param context the invocation context, {@link CommandContext#arguments()} holds the partial
     *                input as the last element
     */
    List<String> suggest(CommandContext context);
}