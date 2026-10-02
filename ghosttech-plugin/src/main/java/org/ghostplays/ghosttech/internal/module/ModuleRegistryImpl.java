package org.ghostplays.ghosttech.internal.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.ghostplays.ghosttech.api.module.GhostTechModule;
import org.ghostplays.ghosttech.api.module.ModuleDescriptor;
import org.ghostplays.ghosttech.api.module.ModuleHandle;
import org.ghostplays.ghosttech.api.module.ModuleRegistry;
import org.ghostplays.ghosttech.api.module.ModuleState;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * In-memory {@link ModuleRegistry}.
 *
 * <p>Insertion order is preserved so modules load in a predictable order, which matters when one
 * module queries another's content during load.
 */
public final class ModuleRegistryImpl implements ModuleRegistry {

    private final Map<GhostId, ModuleHandleImpl> handles = new ConcurrentHashMap<>();

    /** Registers a module handle. */
    public void add(ModuleHandleImpl handle) {
        handles.put(handle.descriptor().id(), handle);
    }

    /** Removes a module handle. */
    public ModuleHandleImpl remove(GhostId id) {
        return handles.remove(id);
    }

    @Override
    public Collection<ModuleHandle> modules() {
        return List.copyOf(handles.values());
    }

    @Override
    public Optional<ModuleHandle> module(GhostId id) {
        return Optional.ofNullable(handles.get(id));
    }

    @Override
    public boolean isEnabled(GhostId id) {
        return Optional.ofNullable(handles.get(id))
                .map(handle -> handle.state().active())
                .orElse(false);
    }

    @Override
    public Optional<ModuleHandle> ownerOf(GhostId contentId) {
        // Content IDs are namespaced by module, so the namespace identifies the owner directly.
        GhostId owner = new GhostId(contentId.namespace(), contentId.path());
        return Optional.ofNullable(handles.get(owner));
    }

    /** Number of registered modules. */
    public int size() {
        return handles.size();
    }

    /** A module instance plus its lifecycle state. */
    public static final class ModuleHandleImpl implements ModuleHandle {

        private final ModuleDescriptor descriptor;
        private final GhostTechModule instance;
        private ModuleState state = ModuleState.DISCOVERED;

        /**
         * @param descriptor the parsed descriptor
         * @param instance  the loaded module
         */
        public ModuleHandleImpl(ModuleDescriptor descriptor, GhostTechModule instance) {
            this.descriptor = descriptor;
            this.instance = instance;
        }

        @Override
        public ModuleDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ModuleState state() {
            return state;
        }

        @Override
        public GhostTechModule instance() {
            return instance;
        }

        @Override
        public boolean enable() {
            if (state != ModuleState.LOADED) {
                return false;
            }
            try {
                instance.onEnable();
                state = ModuleState.ENABLED;
                return true;
            } catch (Exception exception) {
                // The caller decides how to report this; the state records that it happened.
                state = ModuleState.FAILED;
                throw new ModuleLoadException(
                        "Module " + descriptor.id() + " failed to enable", exception);
            }
        }

        @Override
        public boolean disable() {
            if (state != ModuleState.ENABLED) {
                state = ModuleState.DISABLED;
                return false;
            }
            try {
                instance.onDisable();
            } catch (Exception exception) {
                throw new ModuleLoadException(
                        "Module " + descriptor.id() + " failed to disable", exception);
            } finally {
                state = ModuleState.DISABLED;
            }
            return true;
        }

        /** Marks the module as loaded, ready to be enabled. */
        public void markLoaded() {
            state = ModuleState.LOADED;
        }

        /** Marks the module as failed. */
        public void markFailed() {
            state = ModuleState.FAILED;
        }

        @Override
        public String toString() {
            return descriptor.id() + " [" + state + "]";
        }
    }
}