package org.ghostplays.ghosttech.api;

import org.ghostplays.ghosttech.api.block.CustomBlockRegistry;
import org.ghostplays.ghosttech.api.command.CommandRegistry;
import org.ghostplays.ghosttech.api.content.ContentRegistry;
import org.ghostplays.ghosttech.api.database.DatabaseService;
import org.ghostplays.ghosttech.api.dialog.DialogService;
import org.ghostplays.ghosttech.api.gui.GuiFactory;
import org.ghostplays.ghosttech.api.module.ModuleMessaging;
import org.ghostplays.ghosttech.api.module.ModuleRegistry;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockRegistry;
import org.ghostplays.ghosttech.api.permission.PermissionService;
import org.ghostplays.ghosttech.api.storage.StorageService;

/**
 * The plugin's public API, handed to every module through {@link ModuleContext#api()}.
 *
 * <p>This is the whole surface a module can reach. Everything the plugin can do, a module can do,
 * because every service here is fully implemented rather than a stub. There is deliberately no
 * "plugin instance" escape hatch: a module using only this interface cannot reach plugin internals
 * and cannot break the plugin by accident.
 *
 * <p>Threading: every accessor must be used from the main server thread, except
 * {@link #database()}, whose methods return futures and are safe to call from anywhere.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class ExampleModule implements GhostTechModule {
 *
 *     private ModuleContext context;
 *
 *     @Override
 *     public void onLoad(ModuleContext context) {
 *         this.context = context;
 *
 *         // Custom block: module1:legendary_forge
 *         context.api().blocks().register(new LegendaryForge());
 *
 *         // Multiblock: module1:rubyforge
 *         context.api().multiblocks().register(new RubyForge());
 *
 *         // Command with autocomplete
 *         context.api().commands().register(new ForgeCommand());
 *
 *         // Permission
 *         context.api().permissions().register(
 *                 "ghosttech.block.example.legendary_forge",
 *                 Component.text("Place and break legendary forges"));
 *
 *         // Wait for another module to finish loading.
 *         context.api().messaging().subscribe("example:ready", context.descriptor().id(),
 *                 payload -> payload);
 *     }
 * }
 * }</pre>
 */
public interface GhostTechApi {

    /** The API generation this plugin implements, e.g. {@code 1.0}. */
    String apiVersion();

    /** Custom block definitions and world position tracking. */
    CustomBlockRegistry blocks();

    /** Multiblock definitions and formed instance tracking. */
    MultiBlockRegistry multiblocks();

    /** The general content registry, for IDs that are not blocks or multiblocks. */
    ContentRegistry content();

    /** Chest, paginated and storage GUI construction. */
    GuiFactory gui();

    /** Native server dialogs. */
    DialogService dialogs();

    /** Permission registration, checking, groups and grants. */
    PermissionService permissions();

    /** Command registration and tab completion. */
    CommandRegistry commands();

    /** Persistent inventory storage. */
    StorageService storage();

    /** The shared SQLite database. */
    DatabaseService database();

    /** Module-to-module messaging. */
    ModuleMessaging messaging();

    /** The loaded module registry. */
    ModuleRegistry modules();

    /**
     * The plugin's data folder.
     *
     * <p>Modules should normally use {@link ModuleContext#dataFolder()} instead, so their files
     * stay separate. Use this for plugin-wide shared data.
     */
    java.io.File dataFolder();
}