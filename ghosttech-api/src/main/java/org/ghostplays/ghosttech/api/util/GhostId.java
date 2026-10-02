package org.ghostplays.ghosttech.api.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A namespaced identifier in the form {@code namespace:path}.
 *
 * <p>Every piece of content registered by the plugin or by a module uses this format, which keeps
 * IDs globally unique without any central registry. A module named {@code module1} registering a
 * sword gets the ID {@code module1:legendarysword}; a second module registering its own sword gets
 * {@code module2:rubysword}. The two can never collide.
 *
 * <p>The namespace must match {@code [a-z0-9_.-]+} and the path {@code [a-z0-9_/.-]+}, so IDs are
 * safe to use in commands, config files and resource-pack paths.
 */
public record GhostId(String namespace, String path) implements Comparable<GhostId> {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9_/.-]+");

    public static final String DEFAULT_NAMESPACE = "ghosttech";

    public GhostId {
        namespace = normalise(namespace);
        path = normalise(path);
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("GhostId namespace must not be empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("GhostId path must not be empty");
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid GhostId namespace: " + namespace);
        }
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid GhostId path: " + path);
        }
    }

    /**
     * Parses {@code namespace:path}. A value without a namespace falls back to
     * {@link #DEFAULT_NAMESPACE}, so {@code "minecraft:stone"} and {@code "stone"} both work.
     *
     * @param raw the raw identifier, may be {@code null}
     * @return the parsed id
     * @throws IllegalArgumentException if the raw value is malformed
     */
    public static GhostId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("GhostId must not be null or blank");
        }
        String trimmed = raw.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return new GhostId(DEFAULT_NAMESPACE, trimmed);
        }
        String namespace = trimmed.substring(0, separator);
        String path = trimmed.substring(separator + 1);
        if (path.contains(":")) {
            throw new IllegalArgumentException("GhostId must contain exactly one ':' -> " + raw);
        }
        return new GhostId(namespace, path);
    }

    /** Same as {@link #parse(String)} but returns {@code null} instead of throwing. */
    public static GhostId parseOrNull(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Lower-cases and trims, so user input is forgiving. */
    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** The final path segment, e.g. {@code legendarysword} for {@code module1:legendarysword}. */
    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    @Override
    public int compareTo(GhostId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}