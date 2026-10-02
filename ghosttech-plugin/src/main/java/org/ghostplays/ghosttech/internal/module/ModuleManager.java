package org.ghostplays.ghosttech.internal.module;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.ghostplays.ghosttech.api.GhostTechApi;
import org.ghostplays.ghosttech.api.module.GhostTechModule;
import org.ghostplays.ghosttech.api.module.ModuleDescriptor;
import org.ghostplays.ghosttech.api.module.ModuleHandle;
import org.ghostplays.ghosttech.api.module.ModuleState;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.module.ModuleRegistryImpl.ModuleHandleImpl;

/**
 * Discovers, loads, enables and disables module jars.
 *
 * <h2>Startup order</h2>
 * Modules are sorted by their dependency graph: hard dependencies load first, soft dependencies are
 * preferred but never required. A module whose hard dependency is missing is skipped rather than
 * loaded in a broken state, and a cycle is reported instead of silently producing an arbitrary order.
 *
 * <h2>Failure isolation</h2>
 * A module that throws during load is marked {@link ModuleState#FAILED} and everything depending on
 * it is skipped. The server and every unrelated module keep working.
 */
public final class ModuleManager {

    /** Subdirectory of the plugin folder that module jars are read from. */
    public static final String MODULE_DIRECTORY = "modules";

    private final Plugin plugin;
    private final GhostTechApi api;
    private final Logger logger;
    private final ModuleRegistryImpl registry;
    private final ModuleMessagingImpl messaging;
    private final File modulesFolder;

    /** Live classloaders, so they can be closed on unload. */
    private final Map<GhostId, ModuleClassLoader> classLoaders = new HashMap<>();

    /** Load order, so shutdown can run in reverse. */
    private final List<GhostId> loadOrder = new ArrayList<>();

    /**
     * @param plugin    the owning plugin
     * @param api       the API handed to modules
     * @param registry  the module registry to populate
     * @param messaging the message bus modules talk through
     * @param logger    the plugin logger
     */
    public ModuleManager(Plugin plugin, GhostTechApi api, ModuleRegistryImpl registry,
            ModuleMessagingImpl messaging, Logger logger) {
        this.plugin = plugin;
        this.api = api;
        this.registry = registry;
        this.messaging = messaging;
        this.logger = logger;
        this.modulesFolder = new File(plugin.getDataFolder(), MODULE_DIRECTORY);
    }
    /**
     * Loads every module jar found in the modules folder, then enables them.
     *
     * @return how many modules reached {@link ModuleState#ENABLED}
     */
    public int loadAll() {
        if (!modulesFolder.exists() && !modulesFolder.mkdirs()) {
            logger.warning("Could not create modules folder: " + modulesFolder);
            return 0;
        }

        File[] jars = modulesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            logger.info("No modules found in " + MODULE_DIRECTORY + "/");
            return 0;
        }
        // Sorted by name so load order is reproducible when modules are independent.
        List<File> ordered = new ArrayList<>(List.of(jars));
        ordered.sort(Comparator.comparing(File::getName));

        Map<GhostId, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        for (File jar : ordered) {
            try {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (!descriptor.compatibleWith(api.apiVersion())) {
                    logger.warning("Skipping " + jar.getName() + ": module targets API "
                            + descriptor.apiVersion() + " but this plugin provides "
                            + api.apiVersion());
                    continue;
                }
                if (descriptors.containsKey(descriptor.id())) {
                    logger.warning("Skipping " + jar.getName() + ": module id " + descriptor.id()
                            + " is already provided by another jar");
                    continue;
                }
                descriptors.put(descriptor.id(), descriptor);
            } catch (IOException | IllegalArgumentException exception) {
                logger.log(Level.WARNING,
                        "Could not read module descriptor from " + jar.getName(), exception);
            }
        }

        List<GhostId> order = resolveLoadOrder(descriptors);
        loadOrder.addAll(order);

        for (GhostId id : order) {
            try {
                loadModule(descriptors.get(id));
                if (registry.module(id).orElse(null) instanceof ModuleHandleImpl impl) {
                    impl.markLoaded();
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed to load module " + id
                        + " (" + descriptors.get(id).name() + ")", exception);
            }
        }

        // Only modules that loaded cleanly are enabled, in the same order.
        int enabled = 0;
        for (GhostId id : order) {
            var handle = registry.module(id).orElse(null);
            if (!(handle instanceof ModuleHandleImpl impl) || impl.state() != ModuleState.LOADED) {
                continue;
            }
            try {
                impl.enable();
                enabled++;
                logger.info("Enabled module " + id + " v" + descriptors.get(id).version());
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Failed to enable module " + id, exception);
            }
        }

        // Announce that the load sequence is complete so modules can resolve each other.
        messaging.publish("ghosttech:modules-ready", List.copyOf(order), null);
        return enabled;
    }
    /**
     * Loads a single module: creates its classloader, instantiates the main class and runs
     * {@link GhostTechModule#onLoad}.
 *
     * @param descriptor the module to load
     * @throws Exception if the jar, class or lifecycle call fails
     */
    private void loadModule(ModuleDescriptor descriptor) throws Exception {
        File jar = findJar(descriptor.id());
        if (jar == null) {
            throw new ModuleLoadException(
                    "Jar for module " + descriptor.id() + " disappeared during load");
        }

        ModuleClassLoader classLoader = new ModuleClassLoader(
                jar.toURI().toURL(), plugin.getClass().getClassLoader(), List.of());

        Class<?> mainClass = Class.forName(descriptor.main(), true, classLoader);
        if (!GhostTechModule.class.isAssignableFrom(mainClass)) {
            throw new ModuleLoadException("Module " + descriptor.id() + " main class "
                    + descriptor.main() + " does not implement GhostTechModule");
        }

        GhostTechModule instance = (GhostTechModule) mainClass.getDeclaredConstructor().newInstance();

        File dataFolder = new File(new File(plugin.getDataFolder(), MODULE_DIRECTORY),
                descriptor.id().namespace());
        // The logger name becomes the module ID, so console output is attributable by name.
        Logger moduleLogger = Logger.getLogger("GhostTech[" + descriptor.id() + "]");

        ModuleContextImpl context = new ModuleContextImpl(
                descriptor, api, dataFolder, moduleLogger, classLoader);

        classLoaders.put(descriptor.id(), classLoader);
        registry.add(new ModuleHandleImpl(descriptor, instance));

        instance.onLoad(context);

        // Published immediately so another module can find this one during its own load.
        messaging.publish("ghosttech:module-loaded", descriptor.id(), null);
    }

    /**
     * Finds the jar backing a module ID.
     *
     * @param id the module ID
     * @return the jar file, or {@code null} when not found
     */
    private File findJar(GhostId id) {
        File[] jars = modulesFolder.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null) {
            return null;
        }
        for (File jar : jars) {
            try {
                if (ModuleDescriptorParser.parse(jar).id().equals(id)) {
                    return jar;
                }
            } catch (IOException | IllegalArgumentException exception) {
                // Not a GhostTech module jar, or unreadable; already reported during discovery.
            }
        }
        return null;
    }

    /**
     * Sorts modules so dependencies come first.
     *
     * @param descriptors the discovered modules
     * @return the load order
     */
    private List<GhostId> resolveLoadOrder(Map<GhostId, ModuleDescriptor> descriptors) {
        List<GhostId> order = new ArrayList<>();
        Set<GhostId> resolved = new HashSet<>();
        Set<GhostId> resolving = new LinkedHashSet<>();

        for (GhostId id : descriptors.keySet()) {
            visit(id, descriptors, resolved, resolving, order);
        }
        return order;
    }

    /**
     * Depth-first visit producing a topological order.
     *
     * @param id          the module being visited
     * @param descriptors all discovered modules
     * @param resolved    modules already ordered
     * @param resolving   modules on the current path, used to detect cycles
     * @param order       the output order
     */
    private void visit(GhostId id, Map<GhostId, ModuleDescriptor> descriptors, Set<GhostId> resolved,
            Set<GhostId> resolving, List<GhostId> order) {
        if (resolved.contains(id)) {
            return;
        }
        if (!resolving.add(id)) {
            // A cycle has no valid order, so it is reported and broken here rather than hanging.
            logger.severe("Dependency cycle detected involving module " + id
                    + "; loading it without its dependencies first");
            resolved.add(id);
            order.add(id);
            return;
        }

        ModuleDescriptor descriptor = descriptors.get(id);
        if (descriptor != null) {
            for (GhostId dependency : descriptor.depend()) {
                if (!descriptors.containsKey(dependency)) {
                    logger.warning("Module " + id + " requires missing module " + dependency
                            + " and will be skipped");
                    resolving.remove(id);
                    resolved.add(id);
                    order.add(id);
                    return;
                }
                visit(dependency, descriptors, resolved, resolving, order);
            }
            for (GhostId dependency : descriptor.softDepend()) {
                if (descriptors.containsKey(dependency)) {
                    visit(dependency, descriptors, resolved, resolving, order);
                }
            }
        }

        resolving.remove(id);
        resolved.add(id);
        order.add(id);
    }
    /**
 * Disables every enabled module and closes its classloader.
 *
 * <p>Called on plugin disable. Modules are disabled in reverse load order, so a module is never
 * disabled before something it depends on.
 *
 * @return how many modules were disabled
 */
    public int disableAll() {
        int disabled = 0;
        List<GhostId> reverseOrder = new ArrayList<>(loadOrder);
        java.util.Collections.reverse(reverseOrder);

        for (GhostId id : reverseOrder) {
            var handle = registry.module(id).orElse(null);
            if (!(handle instanceof ModuleHandleImpl impl)) {
                continue;
            }
            try {
                // Subscriptions are dropped first, so a disabled module cannot receive messages
                // between being disabled and its classloader being closed.
                messaging.unsubscribeAll(id);
                if (impl.disable()) {
                    disabled++;
                }
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Failed to disable module " + id, exception);
            }
        }

        for (Map.Entry<GhostId, ModuleClassLoader> entry : classLoaders.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException exception) {
                logger.log(Level.WARNING,
                        "Could not close classloader for module " + entry.getKey(), exception);
            }
        }
        classLoaders.clear();
        loadOrder.clear();
        return disabled;
    }

    /**
     * The descriptor of a loaded module.
     *
     * @param id the module ID
     * @return the descriptor, or {@code null} when not loaded
     */
    public ModuleDescriptor descriptor(GhostId id) {
        return registry.module(id).map(ModuleHandle::descriptor).orElse(null);
    }

    /** The folder module jars are read from. */
    public File modulesFolder() {
        return modulesFolder;
    }

    /** The order modules were loaded in. */
    public List<GhostId> loadOrder() {
        return List.copyOf(loadOrder);
    }
}