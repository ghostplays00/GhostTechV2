package org.ghostplays.ghosttech.api.permission;

import net.kyori.adventure.text.Component;

/**
 * A registered permission node with its documentation.
 *
 * @param node         the permission node, e.g. {@code ghosttech.block.example.forge}
 * @param description  what it allows, shown in {@code /ghosttech permissions}
 * @param defaultValue whether operators get it by default
 * @param owner        the module that registered it, {@code null} for plugin-owned nodes
 */
public record PermissionDefinition(String node, Component description, DefaultValue defaultValue,
                                  org.ghostplays.ghosttech.api.util.GhostId owner) {

    /** A short one-line summary for command output. */
    public String describe() {
        String text = description == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(description);
        return node + (text.isBlank() ? "" : " - " + text);
    }
}