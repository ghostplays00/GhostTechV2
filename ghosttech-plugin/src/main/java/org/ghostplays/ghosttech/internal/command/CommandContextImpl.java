package org.ghostplays.ghosttech.internal.command;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.command.CommandContext;

/**
 * {@link CommandContext} implementation handed to commands during execution and tab completion.
 *
 * <p>The argument list holds the partial input as its last element while completing, which is how a
 * command knows whether the player is still typing a subcommand or has moved on to its arguments.
 */
public final class CommandContextImpl implements CommandContext {

    private final CommandSender sender;
    private final String label;
    private final List<String> arguments;
    private final net.kyori.adventure.text.Component prefix;

    /**
     * @param sender    who is running the command
     * @param label     the label the player typed
     * @param arguments arguments after the label; may include a partial final entry
     * @param prefix    plugin message prefix
     */
    public CommandContextImpl(CommandSender sender, String label, List<String> arguments, Component prefix) {
        this.sender = sender;
        this.label = label;
        this.arguments = arguments == null ? List.of() : List.copyOf(arguments);
        this.prefix = prefix;
    }

    @Override
    public CommandSender sender() {
        return sender;
    }

    @Override
    public Optional<Player> player() {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public List<String> arguments() {
        return arguments;
    }

    @Override
    public String argument(int index) {
        return index >= 0 && index < arguments.size() ? arguments.get(index) : null;
    }

    @Override
    public int integer(int index, int defaultValue) {
        String raw = argument(index);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    @Override
    public boolean missing(int required) {
        return arguments.size() < required;
    }

    @Override
    public String join(int from) {
        if (from >= arguments.size()) {
            return "";
        }
        return String.join(" ", arguments.subList(from, arguments.size()));
    }

    @Override
    public void reply(Component message) {
        sender.sendMessage(prefix.append(message));
    }

    @Override
    public void replyRaw(Component message) {
        sender.sendMessage(message);
    }

    @Override
    public void error(Component message) {
        sender.sendMessage(prefix.append(Component.text("[Error] ", NamedTextColor.RED))
                .append(message.colorIfAbsent(NamedTextColor.RED)));
    }

    @Override
    public void success(Component message) {
        sender.sendMessage(prefix.append(Component.text("[OK] ", NamedTextColor.GREEN))
                .append(message.colorIfAbsent(NamedTextColor.GREEN)));
    }
}