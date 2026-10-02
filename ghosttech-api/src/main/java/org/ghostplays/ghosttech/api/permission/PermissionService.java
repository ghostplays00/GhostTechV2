package org.ghostplays.ghosttech.api.permission;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Permission management.
 *
 * <p>The plugin does not replace your permission plugin. Instead it defines how permissions are
 * <em>registered</em> (so they can be documented, listed and granted consistently) and how they are
 * checked, including the plugin's own defaults.
 *
 * <p>Three things happen here:
 * <ul>
 *   <li>Modules register their nodes during load, so {@code /ghosttech permissions} can list
 *       everything a module needs.</li>
 *   <li>Custom groups can be defined and assigned, with groups inheriting from each other.</li>
 *   <li>Wildcard resolution is provided, so {@code ghosttech.block.*} grants every block node.</li>
 * </ul>
 *
 * <p>Grant and revoke write to SQLite and apply immediately. When a permission plugin such as
 * LuckPerms is present, delegate to it via {@link #hasPermission(CommandSender, String)}; the
 * default implementation consults Bukkit, so it works with any setup.
 */
public interface PermissionService {

    /**
     * Registers a permission node with a description.
     *
     * @param node         the node, e.g. {@code ghosttech.block.example.legendary_forge}
     * @param description  shown in {@code /ghosttech permissions}
     * @param defaultValue the default granted to operators, e.g. {@code OP}
     */
    void register(String node, Component description, DefaultValue defaultValue);

    /** Registers a node that operators have by default. */
    default void register(String node, Component description) {
        register(node, description, DefaultValue.OP);
    }

    /** Looks up a registered node's description. */
    Optional<Component> description(String node);

    /** Every registered node with its description, sorted. */
    Collection<PermissionDefinition> definitions();

    /**
     * Checks a permission for a sender, honouring wildcards.
     *
     * <p>Resolution order: exact node, then the sender's groups, then any wildcard parent such as
     * {@code ghosttech.block.*} or {@code ghosttech.*}.
     *
     * @return {@code true} when granted
     */
    boolean hasPermission(CommandSender sender, String node);

    /**
     * Checks several nodes, requiring all of them.
     *
     * <p>Used for actions that need more than one node, e.g. forming a multiblock and using it.
     */
    boolean hasAll(CommandSender sender, String... nodes);

    /** Checks several nodes, requiring at least one. */
    boolean hasAny(CommandSender sender, String... nodes);

    /**
     * Grants a node directly to a player, persisted in SQLite.
     *
     * <p>This is GhostTech's own grant system. It works standalone and is not required when a
     * permission plugin manages everything.
     */
    void grant(Player player, String node);

    /** Revokes a directly granted node. */
    void revoke(Player player, String node);

    /** Revokes every direct grant for a player. */
    void clear(Player player);

    /** Direct grants currently stored for a player. */
    Collection<String> grants(UUID playerId);

    /**
     * Defines a group with a set of nodes.
     *
     * @param group  the group name
     * @param nodes  nodes granted directly by this group
     * @param parents groups whose nodes this group also inherits
     */
    void defineGroup(String group, Collection<String> nodes, Collection<String> parents);

    /** Removes a group definition. */
    boolean removeGroup(String group);

    /** Every defined group. */
    Collection<PermissionGroup> groups();

    /** Assigns a player to a group, persisted in SQLite. */
    void addToGroup(Player player, String group);

    /** Removes a player from a group. */
    void removeFromGroup(Player player, String group);

    /** Groups a player belongs to. */
    Collection<String> groupsOf(UUID playerId);

    /** Removes all GhostTech group and grant data for a player, called on quit. */
    void forget(UUID playerId);
}