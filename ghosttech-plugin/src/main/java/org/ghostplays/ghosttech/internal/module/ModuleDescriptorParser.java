package org.ghostplays.ghosttech.internal.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.ghostplays.ghosttech.api.module.ModuleDescriptor;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Parses a module's {@code module.yml}.
 *
 * <p>Validation happens here, at load time, so a malformed descriptor is reported with the offending
 * module's file name rather than surfacing as a confusing error later.
 */
public final class ModuleDescriptorParser {

    /** The descriptor file name inside a module jar. */
    public static final String DESCRIPTOR_FILE = "module.yml";

    private ModuleDescriptorParser() {
    }

    /**
     * Reads and validates the descriptor from a module jar.
     *
     * @param jar the module jar
     * @return the parsed descriptor
     * @throws IOException              if the jar cannot be read
     * @throws IllegalArgumentException if the descriptor is missing or malformed
     */
    public static ModuleDescriptor parse(File jar) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
            java.util.zip.ZipEntry entry = zip.getEntry(DESCRIPTOR_FILE);
            if (entry == null) {
                throw new IllegalArgumentException(
                        jar.getName() + " has no " + DESCRIPTOR_FILE);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return parse(in, jar.getName());
            }
        }
    }

    /**
     * Reads and validates a descriptor from a stream.
     *
     * @param in     the YAML stream
     * @param source a name used in error messages, e.g. the jar file name
     * @return the parsed descriptor
     * @throws IllegalArgumentException if the descriptor is malformed
     */
    public static ModuleDescriptor parse(InputStream in, String source) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));

        String id = yaml.getString("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(source + ": module.yml is missing 'id'");
        }
        GhostId moduleId;
        try {
            moduleId = GhostId.parse(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    source + ": invalid module id '" + id + "' - " + exception.getMessage());
        }

        // The namespace must be a single segment, since it is used as an ID prefix and as a
        // directory name under plugins/GhostTech/modules.
        if (moduleId.path().contains("/")) {
            throw new IllegalArgumentException(
                    source + ": module id must not contain '/', got '" + id + "'");
        }

        String main = yaml.getString("main");
        if (main == null || main.isBlank()) {
            throw new IllegalArgumentException(source + ": module.yml is missing 'main'");
        }

        return new ModuleDescriptor(
                moduleId,
                yaml.getString("name"),
                yaml.getString("version"),
                main.trim(),
                yaml.getString("api-version"),
                yaml.getStringList("authors"),
                yaml.getString("description"),
                parseIdList(yaml.getStringList("depend"), source, "depend"),
                parseIdList(yaml.getStringList("soft-depend"), source, "soft-depend"));
    }

    /**
     * Parses a list of ID strings, ignoring malformed entries with a warning.
     *
     * @param raw    the raw values
     * @param source a name used in error messages
     * @param field  the field name, used in the warning
     * @return the parsed IDs
     */
    private static List<GhostId> parseIdList(List<String> raw, String source, String field) {
        List<GhostId> ids = new ArrayList<>();
        if (raw == null) {
            return ids;
        }
        for (String value : raw) {
            GhostId parsed = GhostId.parseOrNull(value);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        source + ": invalid id '" + value + "' in " + field);
            }
            ids.add(parsed);
        }
        return ids;
    }
}