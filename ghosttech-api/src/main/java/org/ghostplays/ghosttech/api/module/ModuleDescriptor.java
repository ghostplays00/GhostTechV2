package org.ghostplays.ghosttech.api.module;

import java.util.List;
import java.util.Objects;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Immutable description of a module, parsed from the {@code module.yml} at the root of a module jar.
 *
 * <p>YAML shape:
 * <pre>{@code
 * id: example            # namespace for every ID this module registers
 * name: Example Module
 * version: 1.0.0
 * main: com.example.ExampleModule
 * api-version: 1.0
 * authors: [ Ghostplays00 ]
 * description: Demonstrates blocks, multiblocks, GUI and module messaging
 * depend: [ ]            # hard dependencies, module is skipped if missing
 * soft-depend: [ ]       # loaded first if present, never blocks loading
 * }</pre>
 */
public record ModuleDescriptor(
        GhostId id,
        String name,
        String version,
        String main,
        String apiVersion,
        List<String> authors,
        String description,
        List<GhostId> depend,
        List<GhostId> softDepend
) {

    /** The API version this module was compiled against. */
    public static final String SUPPORTED_API_VERSION = "1.0";

    public ModuleDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(main, "main");

        name = name == null || name.isBlank() ? id.namespace() : name.trim();
        version = version == null || version.isBlank() ? "0.0.0" : version.trim();
        apiVersion = apiVersion == null || apiVersion.isBlank() ? SUPPORTED_API_VERSION : apiVersion.trim();
        authors = authors == null ? List.of() : List.copyOf(authors);
        description = description == null ? "" : description.trim();
        depend = depend == null ? List.of() : List.copyOf(depend);
        softDepend = softDepend == null ? List.of() : List.copyOf(softDepend);

        if (main.isBlank()) {
            throw new IllegalArgumentException("Module main class must not be blank for " + id);
        }
    }

    /**
     * {@code true} when this module targets an API generation the plugin still supports.
     *
     * <p>Comparison is on the major component only, so an API of {@code 1.4} is accepted by a
     * plugin advertising {@code 1.0}. A module requiring {@code 2.0} is rejected.
     */
    public boolean compatibleWith(String pluginApiVersion) {
        return major(apiVersion) <= major(pluginApiVersion);
    }

    private static int major(String version) {
        int dot = version.indexOf('.');
        String head = dot < 0 ? version : version.substring(0, dot);
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException exception) {
            // Unparseable versions are treated as incompatible rather than silently accepted.
            return Integer.MAX_VALUE;
        }
    }
}