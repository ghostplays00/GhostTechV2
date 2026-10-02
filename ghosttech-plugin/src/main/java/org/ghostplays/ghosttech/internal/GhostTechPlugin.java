package org.ghostplays.ghosttech.internal;

import dev.triumphteam.gui.TriumphGui;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.ghostplays.ghosttech.api.permission.DefaultValue;
import org.ghostplays.ghosttech.internal.block.CustomBlockRegistryImpl;
import org.ghostplays.ghosttech.internal.command.BuiltinCommands;
import org.ghostplays.ghosttech.internal.command.CommandRegistryImpl;
import org.ghostplays.ghosttech.internal.command.RootCommand;
import org.ghostplays.ghosttech.internal.content.ContentRegistryImpl;
import org.ghostplays.ghosttech.internal.database.Schema;
import org.ghostplays.ghosttech.internal.database.SqliteDatabaseService;
import org.ghostplays.ghosttech.internal.dialog.PaperDialogService;
import org.ghostplays.ghosttech.internal.gui.TriumphGuiFactory;
import org.ghostplays.ghosttech.internal.listener.BlockListener;
import org.ghostplays.ghosttech.internal.listener.PlayerListener;
import org.ghostplays.ghosttech.internal.module.ModuleManager;
import org.ghostplays.ghosttech.internal.module.ModuleMessagingImpl;
import org.ghostplays.ghosttech.internal.module.ModuleRegistryImpl;
import org.ghostplays.ghosttech.internal.multiblock.MultiBlockRegistryImpl;
import org.ghostplays.ghosttech.internal.permission.PermissionServiceImpl;
import org.ghostplays.ghosttech.internal.storage.SqliteStorageService;
import org.ghostplays.ghosttech.internal.util.Messages;

/**
 * GhostTech V2 main plugin class.
 *
 * <h2>Startup order</h2>
 * <ol>
 *   <li>Configuration is read.</li>
 *   <li>The SQLite database opens and its schema is created.</li>
 *   <li>Registries are created and the API is assembled.</li>
 *   <li>Listeners and commands are registered.</li>
 *   <li>Module jars load, which is when modules register their content.</li>
 *   <li>Tracked blocks and multiblock instances are restored into the caches.</li>
 * </ol>
 *
 * <p>The order matters: modules register content during load, so world state can only be restored
 * afterwards. Restoring first would drop the state of any block whose module has not loaded yet.
 *
 * <h2>Shutdown</h2>
 * Modules are disabled first so they can persist their state, then the database is flushed and
 * closed, so queued writes are not lost.
 */
public final class GhostTechPlugin extends JavaPlugin {

    /** The API generation this plugin implements. */
    public static final String API_VERSION = "1.0";

    private SqliteDatabaseService database;
    private ContentRegistryImpl content;
    private CustomBlockRegistryImpl blockRegistry;
    private MultiBlockRegistryImpl multiBlockRegistry;
    private PermissionServiceImpl permissionService;
    private SqliteStorageService storageService;
    private ModuleRegistryImpl moduleRegistry;
    private ModuleMessagingImpl messaging;
    private ModuleManager moduleManager;
    private CommandRegistryImpl commandRegistry;
    private GhostTechApiImpl api;
    private Component prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = Messages.parse(getConfig().getString("prefix", "&8[&6GhostTech&8] &r"));

        if (!openDatabase()) {
            getLogger().severe("Could not open the database; disabling GhostTech to avoid"
                    + " corrupting data.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        buildServices();
        registerListeners();
        registerCommands();
        loadModules();
        warmCaches();

        getLogger().info("GhostTech V2 enabled: " + blockRegistry.blocks().size()
                + " custom blocks, " + multiBlockRegistry.multiBlocks().size() + " multiblocks, "
                + moduleRegistry.size() + " modules");
    }

    @Override
    public void onDisable() {
        // Modules go first: they may write to the database while shutting down.
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (database != null) {
            // Flushes queued writes before closing, so nothing is lost on a clean stop.
            database.shutdown();
        }
        getLogger().info("GhostTech V2 disabled.");
    }
    /**
     * Opens the database and creates the schema.
     *
     * @return {@code true} when the database is usable
     */
    private boolean openDatabase() {
        try {
            File file = new File(getDataFolder(), "ghosttech.db");
            database = new SqliteDatabaseService(file, getLogger());
            // Awaited so the first query cannot race table creation.
            Schema.create(database).join();
            return true;
        } catch (SQLException | RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not initialise the SQLite database", exception);
            return false;
        }
    }

    /**
     * Creates every service and assembles the API handed to modules.
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private void buildServices() {
        content = new ContentRegistryImpl();
        blockRegistry = new CustomBlockRegistryImpl(this, database, content, getLogger());
        multiBlockRegistry = new MultiBlockRegistryImpl(database, content, getLogger());

        // Patterns must be able to ask whether a position holds a custom block, which is what lets a
        // machine be framed with machine parts instead of plain blocks.
        multiBlockRegistry.setCustomBlockLookup(
                block -> blockRegistry.blockIdAt(block.getLocation()).orElse(null));

        permissionService = new PermissionServiceImpl(
                database, getServer().getPluginManager(), getLogger());
        storageService = new SqliteStorageService(database);

        moduleRegistry = new ModuleRegistryImpl();
        messaging = new ModuleMessagingImpl(getLogger());
        commandRegistry = new CommandRegistryImpl(permissionService, prefix);

        api = new GhostTechApiImpl(
                API_VERSION,
                getDataFolder(),
                blockRegistry,
                multiBlockRegistry,
                content,
                new TriumphGuiFactory(storageService),
                new PaperDialogService(),
                permissionService,
                commandRegistry,
                storageService,
                database,
                messaging,
                moduleRegistry);

        moduleManager = new ModuleManager(this, api, moduleRegistry, messaging, getLogger());

        registerPluginPermissions();
        loadConfiguredGroups();

        // Triumph needs to know which plugin owns its listeners before any GUI is created.
        TriumphGui.init(this);
    }

    /**
     * Registers the plugin's own permission nodes.
     *
     * <p>{@code ghosttech.*} is registered so one grant covers every plugin permission, which is what
     * most server owners actually want.
     */
    private void registerPluginPermissions() {
        permissionService.register("ghosttech.command",
                Component.text("Use any GhostTech command"), DefaultValue.OP);
        permissionService.register("ghosttech.command.reload",
                Component.text("Reload GhostTech and its modules"), DefaultValue.OP);
        permissionService.register("ghosttech.command.block",
                Component.text("List and give custom blocks"), DefaultValue.OP);
        permissionService.register("ghosttech.command.multiblock",
                Component.text("Inspect multiblocks"), DefaultValue.OP);
        permissionService.register("ghosttech.command.permissions",
                Component.text("List permission nodes and groups"), DefaultValue.OP);
        permissionService.register("ghosttech.module.admin",
                Component.text("Administer loaded modules"), DefaultValue.OP);
        permissionService.register("ghosttech.*",
                Component.text("Every GhostTech permission"), DefaultValue.OP);
    }

    /**
     * Defines the permission groups declared in {@code config.yml}.
     *
     * <p>Groups are defined before modules load, so a module can reference one during its own load.
     */
    private void loadConfiguredGroups() {
        var section = getConfig().getConfigurationSection("permission-groups");
        if (section == null) {
            return;
        }
        permissionService.clearGroups();
        for (String name : section.getKeys(false)) {
            var group = section.getConfigurationSection(name);
            if (group == null) {
                continue;
            }
            permissionService.defineGroup(name, group.getStringList("nodes"),
                    group.getStringList("inherit"));
        }
    }

    /** Registers the Bukkit listeners. */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new BlockListener(blockRegistry, multiBlockRegistry, permissionService), this);
        getServer().getPluginManager().registerEvents(
                new PlayerListener(permissionService), this);
    }

    /** Registers the root command and the built-in commands. */
    private void registerCommands() {
        new RootCommand(this, commandRegistry, prefix).register(List.of("gt", "ghosttechv2"));
        new BuiltinCommands(this).registerAll(commandRegistry);
    }

    /** Loads every module jar. */
    private void loadModules() {
        try {
            moduleManager.loadAll();
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Module loading failed; the plugin continues without modules", exception);
        }
    }
    /**
     * Restores tracked blocks and formed multiblocks from the database.
     *
     * <p>Runs after modules load, because a stored row is only meaningful once its owning module has
     * registered the matching definition.
     */
    private void warmCaches() {
        try {
            blockRegistry.loadAll().join();
            multiBlockRegistry.loadAll().join();
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not restore world state from the database", exception);
        }
    }

    /**
     * Reloads configuration, modules and world state.
     *
     * <p>Called by {@code /ghosttech reload}. Modules are disabled first so their classloaders are
     * not leaked when they are loaded again.
     */
    public void reloadEverything() {
        reloadConfig();
        prefix = Messages.parse(getConfig().getString("prefix", "&8[&6GhostTech&8] &r"));
        loadConfiguredGroups();

        moduleManager.disableAll();
        loadModules();
        warmCaches();
    }

    /** The assembled API, also handed to every module. */
    public GhostTechApiImpl api() {
        return api;
    }

    /** The custom block registry. */
    public CustomBlockRegistryImpl blockRegistry() {
        return blockRegistry;
    }

    /** The multiblock registry. */
    public MultiBlockRegistryImpl multiBlockRegistry() {
        return multiBlockRegistry;
    }

    /** The permission service. */
    public PermissionServiceImpl permissionService() {
        return permissionService;
    }

    /** The command registry. */
    public CommandRegistryImpl commandRegistry() {
        return commandRegistry;
    }

    /** The module manager. */
    public ModuleManager moduleManager() {
        return moduleManager;
    }

    /** The database, for diagnostics. */
    public SqliteDatabaseService database() {
        return database;
    }
}