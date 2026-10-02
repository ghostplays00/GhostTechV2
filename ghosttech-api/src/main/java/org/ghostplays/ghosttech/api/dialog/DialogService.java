package org.ghostplays.ghosttech.api.dialog;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Server-side dialogs, built on the Paper dialog API.
 *
 * <p>Dialogs are the modern replacement for chest-menu confirmations. They render natively in the
 * client, so no items are consumed from the player's inventory and nothing can be stolen by an
 * inventory swap.
 *
 * <p>Callback handlers are invoked on the main server thread. They must return quickly; move any
 * slow work to a task.
 */
public interface DialogService {

    /**
     * Shows a message with a single dismiss button.
     *
     * @param player   the viewer
     * @param title    the dialog title
     * @param body     the message body
     * @param onClose  optional callback run after the dialog closes
     */
    void notice(Player player, Component title, Component body, Consumer<Player> onClose);

    /** Shows a notice with no close callback. */
    default void notice(Player player, Component title, Component body) {
        notice(player, title, body, null);
    }

    /**
     * Asks a yes/no question.
     *
     * @param onConfirm receives the player and {@code true} for confirm, {@code false} for cancel
     */
    void confirmation(Player player, Component title, Component body,
            Component confirmLabel, Component cancelLabel, BiConfirmCallback onConfirm);

    /** Shows a confirmation with default labels. */
    default void confirmation(Player player, Component title, Component body,
            BiConfirmCallback onConfirm) {
        confirmation(player, title, body,
                Component.text("Confirm"), Component.text("Cancel"), onConfirm);
    }

    /**
     * Offers several actions in a list.
     *
     * @param labels   button labels, in order
     * @param onSelect receives the player and the index of the chosen button
     */
    void options(Player player, Component title, Component body,
            java.util.List<Component> labels, BiSelectCallback onSelect);

    /**
     * Prompts for free text.
     *
     * @param onInput receives the player and the entered text; empty when the player cancelled
     */
    void textInput(Player player, Component title, Component body, java.util.function.BiConsumer<Player, String> onInput);

    /**
     * Prompts for a number within a range.
     *
     * @param min      lowest accepted value, inclusive
     * @param max      highest accepted value, inclusive
     * @param onInput  receives the player and the value; {@code null} when cancelled or invalid
     */
    void numberInput(Player player, Component title, Component body, int min, int max,
            java.util.function.BiConsumer<Player, Integer> onInput);

    /** Confirmation result callback. */
    @FunctionalInterface
    interface BiConfirmCallback {

        /**
         * @param player the viewer
         * @param confirmed {@code true} for confirm, {@code false} for cancel
         */
        void accept(Player player, boolean confirmed);
    }

    /** Multi-option result callback. */
    @FunctionalInterface
    interface BiSelectCallback {

        /**
         * @param player the viewer
         * @param index  the zero-based index of the chosen option
         */
        void accept(Player player, int index);
    }
}