package org.ghostplays.ghosttech.internal.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.ghostplays.ghosttech.api.GhostTechApi;
import org.ghostplays.ghosttech.api.module.ModuleContext;
import org.ghostplays.ghosttech.api.module.ModuleDescriptor;

/**
 * {@link ModuleContext} for one loaded module.
 *
 * <p>The logger name is the module ID, so console output is attributable without any extra
 * configuration from module authors.
 */
public final class ModuleContextImpl implements ModuleContext {

    private final ModuleDescriptor descriptor;
    private final GhostTechApi api;
    private final File dataFolder;
    private final Logger logger;
    private final ModuleClassLoader classLoader;

    /**
     * @param descriptor  the module's descriptor
     * @param api         the plugin API
     * @param dataFolder  the module's private directory
     * @param logger      the plugin logger
     * @param classLoader the module classloader, used to read bundled resources
     */
    public ModuleContextImpl(ModuleDescriptor descriptor, GhostTechApi api, File dataFolder,
            Logger logger, ModuleClassLoader classLoader) {
        this.descriptor = descriptor;
        this.api = api;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.classLoader = classLoader;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public GhostTechApi api() {
        return api;
    }

    @Override
    public File dataFolder() {
        // Created lazily so a module that never writes anything leaves no empty directory.
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("Could not create module data folder: " + dataFolder);
        }
        return dataFolder;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public boolean saveResource(String resourcePath) {
        File target = new File(dataFolder, resourcePath);
        if (target.exists()) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create directory for resource: " + target);
            return false;
        }
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.warning("Resource not found in module jar: " + resourcePath);
                return false;
            }
            try (OutputStream out = Files.newOutputStream(target.toPath())) {
                in.transferTo(out);
            }
            return true;
        } catch (IOException exception) {
            logger.log(java.util.logging.Level.WARNING,
                    "Could not save resource " + resourcePath, exception);
            return false;
        }
    }

    @Override
    public InputStream getResource(String resourcePath) {
        return classLoader.getResourceAsStream(resourcePath);
    }

    /**
     * Loads a YAML config from the module's data folder, extracting it from the jar when absent.
     *
     * <p>This is the shape almost every module needs on first run, so it is provided here rather than
     * repeated in each module.
     *
     * @param fileName the config file name, e.g. {@code config.yml}
     * @return the loaded configuration
     */
    public YamlConfiguration config(String fileName) {
        saveResource(fileName);
        File file = new File(dataFolder, fileName);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Annotate defaults into the file so users can see every available option.
        if (yaml.getKeys(false).isEmpty()) {
            yaml.options().copyDefaults(true);
            yaml.options().header("Configuration for the " + descriptor.name() + " module.");
            try {
                yaml.save(file);
            } catch (IOException exception) {
                logger.log(java.util.logging.Level.WARNING,
                        "Could not write default config for " + fileName, exception);
            }
        }
        return yaml;
    }
}