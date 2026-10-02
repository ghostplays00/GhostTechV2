package org.ghostplays.ghosttech.internal.permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.ghostplays.ghosttech.api.permission.DefaultValue;
import org.ghostplays.ghosttech.api.permission.PermissionDefinition;
import org.ghostplays.ghosttech.api.permission.PermissionGroup;
import org.ghostplays.ghosttech.api.permission.PermissionService;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.database.Schema;
import org.ghostplays.ghosttech.internal.database.SqliteDatabaseService;

/**
 * Permission service backed by SQLite plus a Bukkit fallback.
 *
 * <p>Two systems cooperate here. Registered nodes are attached to Bukkit's permission system, so a
 * permission plugin such as LuckPerms sees them and players can be granted them through it. Direct
 * grants and group assignments live in SQLite, which keeps the plugin useful on servers with no
 * permission plugin at all.
 *
 * <p>Wildcards are resolved before falling through to Bukkit: {@code ghosttech.block.*} and
 * {@code ghosttech.*} are checked alongside the exact node, so one admin grant of
 * {@code ghosttech.*} covers every node the plugin defines.
 */
public final class PermissionServiceImpl implements PermissionService {

    private final SqliteDatabaseService database;
    private final Logger logger;
    private final org.bukkit.plugin.PluginManager pluginManager;

    /** Registered node metadata, keyed by node. */
    private final Map<String, PermissionDefinition> definitions = new ConcurrentHashMap<>();

    /** Group definitions, keyed by lowercase group name. */
    private final Map<String, PermissionGroup> groups = new ConcurrentHashMap<>();

    /** Direct grants per player, mirrored in the database. */
    private final Map<UUID, Set<String>> grants = new ConcurrentHashMap<>();

    /** Group membership per player, mirrored in the database. */
    private final Map<UUID, Set<String>> memberships = new ConcurrentHashMap<>();

    /**
     * @param database      the plugin database
     * @param pluginManager used to register nodes with Bukkit
     * @param logger        logger for warnings about undefined groups
     */
    public PermissionServiceImpl(SqliteDatabaseService database,
            org.bukkit.plugin.PluginManager pluginManager, Logger logger) {
        this.database = database;
        this.pluginManager = pluginManager;
        this.logger = logger;
    }
    @Override
    public void register(String node, Component description, DefaultValue defaultValue) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Permission node must not be blank");
        }
        PermissionDefault bukkitDefault = switch (defaultValue) {
            case OP -> PermissionDefault.OP;
            case TRUE -> PermissionDefault.TRUE;
            case FALSE -> PermissionDefault.FALSE;
        };

        // Registering with Bukkit is what makes the node visible to permission plugins and lets
        // player.hasPermission(node) work with no extra wiring. Bukkit's own Permission takes a
        // plain string description, so the component is flattened for it.
        if (pluginManager.getPermission(node) == null) {
            String plainDescription = description == null ? "" : org.ghostplays.ghosttech.internal.util
                    .Messages.plain(description);
            pluginManager.addPermission(new Permission(node, plainDescription, bukkitDefault));
        }
        definitions.put(node, new PermissionDefinition(node, description, defaultValue, null));
    }

    /** Registers a node owned by a module, so listings can be grouped by module. */
    public void register(String node, Component description, DefaultValue defaultValue, GhostId owner) {
        register(node, description, defaultValue);
        definitions.put(node, new PermissionDefinition(node, description, defaultValue, owner));
    }

    @Override
    public Optional<Component> description(String node) {
        PermissionDefinition definition = definitions.get(node);
        return definition == null ? Optional.empty() : Optional.ofNullable(definition.description());
    }

    @Override
    public Collection<PermissionDefinition> definitions() {
        List<PermissionDefinition> all = new ArrayList<>(definitions.values());
        all.sort((left, right) -> left.node().compareTo(right.node()));
        return List.copyOf(all);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String node) {
        if (node == null || node.isBlank()) {
            return true;
        }
        // A wildcard grant covers every node beneath it, which saves one node per block.
        if (matchesWildcard(sender, node)) {
            return true;
        }
        return sender.hasPermission(node);
    }

    /**
     * Checks the node and every wildcard parent of it.
     *
     * <p>For {@code ghosttech.block.example.forge} this tries {@code ghosttech.block.example.*},
     * {@code ghosttech.block.*}, {@code ghosttech.*} and finally {@code *}.
     */
    private boolean matchesWildcard(CommandSender sender, String node) {
        String separator = ".";
        int index = node.indexOf(separator);
        while (index > 0) {
            if (sender.hasPermission(node.substring(0, index) + ".*")) {
                return true;
            }
            index = node.indexOf(separator, index + 1);
        }
        return sender.hasPermission("*");
    }

    @Override
    public boolean hasAll(CommandSender sender, String... nodes) {
        if (nodes == null || nodes.length == 0) {
            return true;
        }
        for (String node : nodes) {
            if (!hasPermission(sender, node)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasAny(CommandSender sender, String... nodes) {
        if (nodes == null || nodes.length == 0) {
            return true;
        }
        for (String node : nodes) {
            if (hasPermission(sender, node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void grant(Player player, String node) {
        Set<String> nodes = grants.computeIfAbsent(
                player.getUniqueId(), key -> ConcurrentHashMap.newKeySet());
        nodes.add(node);
        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_PERMISSION_GRANTS
                        + " (player_uuid, node, granted_at) VALUES (?, ?, ?)",
                player.getUniqueId().toString(), node, System.currentTimeMillis());
    }

    @Override
    public void revoke(Player player, String node) {
        Set<String> nodes = grants.get(player.getUniqueId());
        if (nodes != null) {
            nodes.remove(node);
        }
        database.execute(
                "DELETE FROM " + Schema.TABLE_PERMISSION_GRANTS + " WHERE player_uuid=? AND node=?",
                player.getUniqueId().toString(), node);
    }

    @Override
    public void clear(Player player) {
        grants.remove(player.getUniqueId());
        database.execute("DELETE FROM " + Schema.TABLE_PERMISSION_GRANTS + " WHERE player_uuid=?",
                player.getUniqueId().toString());
    }

    @Override
    public Collection<String> grants(UUID playerId) {
        Set<String> nodes = grants.get(playerId);
        return nodes == null ? Set.of() : Set.copyOf(nodes);
    }
    @Override
    public void defineGroup(String group, Collection<String> nodes, Collection<String> parents) {
        groups.put(group.toLowerCase(Locale.ROOT), new PermissionGroup(group, nodes, parents));
    }

    @Override
    public boolean removeGroup(String group) {
        return groups.remove(group.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public Collection<PermissionGroup> groups() {
        return List.copyOf(groups.values());
    }

    @Override
    public void addToGroup(Player player, String group) {
        if (!groups.containsKey(group.toLowerCase(Locale.ROOT))) {
            logger.warning("Assigning player to undefined permission group: " + group);
        }
        Set<String> assigned = memberships.computeIfAbsent(
                player.getUniqueId(), key -> ConcurrentHashMap.newKeySet());
        assigned.add(group.toLowerCase(Locale.ROOT));
        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_PERMISSION_GROUPS
                        + " (player_uuid, group_name, assigned_at) VALUES (?, ?, ?)",
                player.getUniqueId().toString(), group.toLowerCase(Locale.ROOT),
                System.currentTimeMillis());
    }

    @Override
    public void removeFromGroup(Player player, String group) {
        Set<String> assigned = memberships.get(player.getUniqueId());
        if (assigned != null) {
            assigned.remove(group.toLowerCase(Locale.ROOT));
        }
        database.execute(
                "DELETE FROM " + Schema.TABLE_PERMISSION_GROUPS
                        + " WHERE player_uuid=? AND group_name=?",
                player.getUniqueId().toString(), group.toLowerCase(Locale.ROOT));
    }

    @Override
    public Collection<String> groupsOf(UUID playerId) {
        Set<String> assigned = memberships.get(playerId);
        return assigned == null ? Set.of() : Set.copyOf(assigned);
    }

    @Override
    public void forget(UUID playerId) {
        grants.remove(playerId);
        memberships.remove(playerId);
    }

    /**
     * Loads a player's grants and groups into the cache.
     *
     * <p>Called on join. Both reads are issued together so the player is ready as soon as the
     * future completes.
     *
     * @param playerId the player to load
     * @return a future completing once the cache is populated
     */
    public CompletableFuture<Void> load(UUID playerId) {
        String uuid = playerId.toString();
        CompletableFuture<List<String>> grantRows = database.query(
                "SELECT node FROM " + Schema.TABLE_PERMISSION_GRANTS + " WHERE player_uuid=?",
                row -> row.getString("node"), uuid);
        CompletableFuture<List<String>> groupRows = database.query(
                "SELECT group_name FROM " + Schema.TABLE_PERMISSION_GROUPS + " WHERE player_uuid=?",
                row -> row.getString("group_name"), uuid);

        return CompletableFuture.allOf(grantRows, groupRows).thenAccept(ignored -> {
            grants.put(playerId, ConcurrentHashMap.newKeySet());
            grants.get(playerId).addAll(grantRows.join());

            memberships.put(playerId, ConcurrentHashMap.newKeySet());
            memberships.get(playerId).addAll(groupRows.join());
        });
    }

    /**
     * All nodes a player effectively holds: direct grants plus every group they belong to.
     *
     * <p>Group inheritance is resolved transitively.
     *
     * @param playerId the player to resolve for
     * @return the resolved node set
     */
    public Set<String> effectiveNodes(UUID playerId) {
        Set<String> resolved = new LinkedHashSet<>(grants(playerId));
        Collection<PermissionGroup> allGroups = groups();
        for (String groupName : groupsOf(playerId)) {
            for (PermissionGroup group : allGroups) {
                if (group.name().equalsIgnoreCase(groupName)) {
                    resolved.addAll(group.resolve(allGroups));
                    break;
                }
            }
        }
        return resolved;
    }

    /** Drops every cached group definition, used before a config reload re-defines them. */
    public void clearGroups() {
        groups.clear();
    }
}