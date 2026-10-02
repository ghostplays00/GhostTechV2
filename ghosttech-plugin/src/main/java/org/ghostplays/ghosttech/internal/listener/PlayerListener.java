package org.ghostplays.ghosttech.internal.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.ghostplays.ghosttech.internal.permission.PermissionServiceImpl;

/**
 * Loads and releases per-player permission state.
 *
 * <p>Grants and group memberships live in SQLite, so they are read once when a player joins rather
 * than queried per permission check. On quit the in-memory cache is dropped, which also keeps the
 * cache from growing for a long-running server.
 */
public final class PlayerListener implements Listener {

    private final PermissionServiceImpl permissions;

    /**
     * @param permissions the permission service whose cache is warmed and cleared
     */
    public PlayerListener(PermissionServiceImpl permissions) {
        this.permissions = permissions;
    }

    /**
     * Loads the joining player's grants and groups.
     *
     * <p>The load runs off the main thread; failures are logged by the database layer and leave the
     * player with whatever the default permission plugin provides.
     *
     * @param event the join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        permissions.load(player.getUniqueId()).exceptionally(throwable -> {
            // A failed cache warm-up must never block or cancel the join.
            pluginLogger().warning("Could not load permission data for " + player.getName());
            return null;
        });
    }

    /**
     * The logger used for join failures.
     *
     * @return the plugin's logger, obtained from the server rather than the player
     */
    private java.util.logging.Logger pluginLogger() {
        return org.bukkit.Bukkit.getLogger();
    }

    /**
     * Releases the quitting player's cached permission state.
     *
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        permissions.forget(event.getPlayer().getUniqueId());
    }
}