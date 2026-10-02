package org.ghostplays.ghosttech.internal;

import java.io.File;
import org.ghostplays.ghosttech.api.GhostTechApi;
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
 * The plugin's {@link GhostTechApi} implementation.
 *
 * <p>A thin wiring class: it holds the concrete services and hands them to modules. Keeping the
 * wiring in one place means the API surface and the runtime stay in step.
 *
 * <p>This class is also the only thing modules ever see, which is deliberate: there is no accessor
 * for plugin internals, so a module cannot destabilise the host.
 */
public final class GhostTechApiImpl implements GhostTechApi {

    private final String apiVersion;
    private final File dataFolder;
    private final CustomBlockRegistry blocks;
    private final MultiBlockRegistry multiblocks;
    private final ContentRegistry content;
    private final GuiFactory gui;
    private final DialogService dialogs;
    private final PermissionService permissions;
    private final CommandRegistry commands;
    private final StorageService storage;
    private final DatabaseService database;
    private final ModuleMessaging messaging;
    private final ModuleRegistry modules;

    /**
     * @param apiVersion  the API generation this plugin implements
     * @param dataFolder  the plugin data folder
     * @param blocks      the custom block registry
     * @param multiblocks the multiblock registry
     * @param content     the content registry
     * @param gui         the GUI factory
     * @param dialogs     the dialog service
     * @param permissions the permission service
     * @param commands    the command registry
     * @param storage     the storage service
     * @param database    the database service
     * @param messaging   the module message bus
     * @param modules     the module registry
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public GhostTechApiImpl(String apiVersion, File dataFolder, CustomBlockRegistry blocks,
            MultiBlockRegistry multiblocks, ContentRegistry content, GuiFactory gui,
            DialogService dialogs, PermissionService permissions, CommandRegistry commands,
            StorageService storage, DatabaseService database, ModuleMessaging messaging,
            ModuleRegistry modules) {
        this.apiVersion = apiVersion;
        this.dataFolder = dataFolder;
        this.blocks = blocks;
        this.multiblocks = multiblocks;
        this.content = content;
        this.gui = gui;
        this.dialogs = dialogs;
        this.permissions = permissions;
        this.commands = commands;
        this.storage = storage;
        this.database = database;
        this.messaging = messaging;
        this.modules = modules;
    }

    @Override
    public String apiVersion() {
        return apiVersion;
    }

    @Override
    public CustomBlockRegistry blocks() {
        return blocks;
    }

    @Override
    public MultiBlockRegistry multiblocks() {
        return multiblocks;
    }

    @Override
    public ContentRegistry content() {
        return content;
    }

    @Override
    public GuiFactory gui() {
        return gui;
    }

    @Override
    public DialogService dialogs() {
        return dialogs;
    }

    @Override
    public PermissionService permissions() {
        return permissions;
    }

    @Override
    public CommandRegistry commands() {
        return commands;
    }

    @Override
    public StorageService storage() {
        return storage;
    }

    @Override
    public DatabaseService database() {
        return database;
    }

    @Override
    public ModuleMessaging messaging() {
        return messaging;
    }

    @Override
    public ModuleRegistry modules() {
        return modules;
    }

    @Override
    public File dataFolder() {
        return dataFolder;
    }
}