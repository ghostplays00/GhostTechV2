package org.ghostplays.ghosttech.api.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Context passed to a {@link GhostCommand} when it runs.
 *
 * <p>Argument access is bounds-checked and never throws, so commands do not need defensive length
 * checks before every read.
 */
public interface CommandContext {

    /** Who ran the command. */
    CommandSender sender();

    /** The player, when the sender is a player. Empty for the console. */
    java.util.Optional<Player> player();

    /**
     * The command label the player typed, e.g. {@code ghosttech}.
     *
     * <p>Use this when building help output so aliases stay consistent.
     */
    String label();

    /** The raw arguments after the command label, never {@code null}. */
    List<String> arguments();

    /**
     * The argument at {@code index}, or {@code null} when out of range.
     *
     * @param index zero-based
     */
    String argument(int index);

    /** The argument at {@code index} parsed as an integer, or {@code defaultValue} if invalid. */
    int integer(int index, int defaultValue);

    /** {@code true} when fewer arguments were given than this command needs. */
    boolean missing(int required);

    /**
     * Joins arguments from {@code from} to the end with spaces.
     *
     * <p>Useful for multi-word names like a display name.
     *
     * @param from first argument index, inclusive
     */
    String join(int from);

    /** Sends a message to the sender, prefixed with the plugin tag. */
    void reply(net.kyori.adventure.text.Component message);

    /** Sends a raw message to the sender without the prefix. */
    void replyRaw(net.kyori.adventure.text.Component message);

    /** Sends an error message in the standard error style. */
    void error(net.kyori.adventure.text.Component message);

    /** Sends a success message in the standard success style. */
    void success(net.kyori.adventure.text.Component message);
}