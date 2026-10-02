package org.ghostplays.ghosttech.internal.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Message helpers.
 *
 * <p>All player-facing text goes through here so colours and prefixes are consistent. Messages are
 * authored in MiniMessage, which lets config files contain formatting without any legacy colour
 * codes.
 */
public final class Messages {

    /** Parses MiniMessage markup into a component. */
    public static Component parse(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage == null ? "" : miniMessage);
    }

    /** Serialises a component back to MiniMessage markup, e.g. for config output. */
    public static String toMiniMessage(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    /** Strips all formatting, for console output and log lines. */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Strips all formatting from MiniMessage markup. */
    public static String plain(String miniMessage) {
        return plain(parse(miniMessage));
    }

    /**
     * Applies a colour code by replacing {@code &} codes in a legacy string.
     *
     * <p>Only used for reading legacy config values; new content should use MiniMessage.
     */
    public static Component legacy(String text) {
        if (text == null) {
            return Component.empty();
        }
        return parse(text.replace('&', '§')
                .replace("§§", "&"));
    }

    /** Builds a list of components from MiniMessage strings, skipping nulls. */
    public static List<Component> parseAll(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(Messages::parse)
                .toList();
    }

    private Messages() {
    }
}