package org.ghostplays.ghosttech.internal.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.dialog.DialogService;

/**
 * {@link DialogService} using Paper's server-side dialogs.
 *
 * <p>Dialogs render natively in the client, so a confirmation cannot be spoofed with a renamed item
 * and the player's inventory is never opened.
 */
public final class PaperDialogService implements DialogService {

    /** Click options shared by every generated button. */
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    @Override
    public void notice(Player player, Component title, Component body, Consumer<Player> onClose) {
        ActionButton dismiss = ActionButton.builder(Component.text("OK"))
                .action(DialogAction.customClick(
                        (response, audience) -> notifyClose(onClose, audience), CLICK_OPTIONS))
                .build();

        player.showDialog(build("notice", title, body, DialogType.notice(dismiss), List.of()));
    }

    @Override
    public void confirmation(Player player, Component title, Component body,
            Component confirmLabel, Component cancelLabel, BiConfirmCallback onConfirm) {
        ActionButton confirm = ActionButton.builder(confirmLabel)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player target) {
                        onConfirm.accept(target, true);
                    }
                }, CLICK_OPTIONS))
                .build();

        ActionButton cancel = ActionButton.builder(cancelLabel)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player target) {
                        onConfirm.accept(target, false);
                    }
                }, CLICK_OPTIONS))
                .build();

        player.showDialog(build("confirm", title, body,
                DialogType.confirmation(confirm, cancel), List.of()));
    }
    @Override
    public void options(Player player, Component title, Component body,
            List<Component> labels, BiSelectCallback onSelect) {
        List<ActionButton> buttons = new ArrayList<>();
        for (int index = 0; index < labels.size(); index++) {
            // Captured per iteration so each button reports its own index.
            int optionIndex = index;
            buttons.add(ActionButton.builder(labels.get(index))
                    .action(DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player target) {
                            onSelect.accept(target, optionIndex);
                        }
                    }, CLICK_OPTIONS))
                    .build());
        }
        if (buttons.isEmpty()) {
            // A multi-action dialog needs at least one button, so fall back to a plain notice.
            notice(player, title, body, null);
            return;
        }
        player.showDialog(build("options", title, body,
                DialogType.multiAction(buttons, null, buttons.size()), List.of()));
    }

    @Override
    public void textInput(Player player, Component title, Component body,
            java.util.function.BiConsumer<Player, String> onInput) {
        DialogInput input = DialogInput.text("input", Component.text("Your answer"))
                .width(200)
                .build();

        ActionButton submit = ActionButton.builder(Component.text("Submit"))
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player target) {
                        onInput.accept(target, response.getText("input"));
                    }
                }, CLICK_OPTIONS))
                .build();

        player.showDialog(build("text", title, body,
                DialogType.multiAction(List.of(submit), null, 1), List.of(input)));
    }

    @Override
    public void numberInput(Player player, Component title, Component body, int min, int max,
            java.util.function.BiConsumer<Player, Integer> onInput) {
        DialogInput input = DialogInput.numberRange("amount", Component.text("Amount"), min, max)
                .width(200)
                .initial((float) min)
                .step(1f)
                .build();

        ActionButton submit = ActionButton.builder(Component.text("Submit"))
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player target) {
                        Float value = response.getFloat("amount");
                        onInput.accept(target, value == null ? null : value.intValue());
                    }
                }, CLICK_OPTIONS))
                .build();

        player.showDialog(build("number", title, body,
                DialogType.multiAction(List.of(submit), null, 1), List.of(input)));
    }

    /**
     * Builds a dialog.
     *
     * <p>Since Paper 1.21.8 a dialog no longer needs a registry key, so no key is assigned here.
     * Each call produces a fresh instance, which means a dialog can never be accidentally reused
     * after a reload.
     *
     * @param kind   short name used in debug output
     * @param title  dialog title
     * @param body   message shown under the title
     * @param type   the dialog type deciding which buttons are rendered
     * @param inputs input fields, may be empty
     * @return the dialog
     */
    private Dialog build(String kind, Component title, Component body, DialogType type,
            List<? extends DialogInput> inputs) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .body(List.of(DialogBody.plainMessage(body)))
                .inputs(new ArrayList<DialogInput>(inputs))
                .build();

        // Dialog.create takes the consumer and returns the finished dialog, so there is no
        // build() call on the entry builder here.
        return Dialog.create(registry -> registry.empty()
                .base(base)
                .type(type));
    }

    /**
     * Runs a close callback for the audience, when it is a player.
     *
     * @param onClose  the callback, may be {@code null}
     * @param audience who interacted with the dialog
     */
    private static void notifyClose(Consumer<Player> onClose, Audience audience) {
        if (onClose != null && audience instanceof Player player) {
            onClose.accept(player);
        }
    }
}