package org.ghostplays.ghosttech.api.multiblock;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * A structural shape a multiblock can be built from.
 *
 * <p>Two pattern kinds are supported:
 * <ul>
 *   <li>{@link #exact()} — the structure must match exactly, every cell included.</li>
 *   <li>{@link #loose()} — only the listed cells must match; the origin and any unlisted cells are
 *       free. Useful for shapes with walkable interiors or decorative surroundings.</li>
 * </ul>
 *
 * <p>Patterns are matched at a candidate origin, which is the lowest, most negative-X cell by
 * default so the structure has a stable anchor.
 */
public interface MultiBlockPattern {

    /** What a pattern cell accepts. */
    sealed interface Cell {

        /** A cell that must be exactly this material. */
        record Exact(Material material) implements Cell {

            /** {@code true} when the position holds exactly the required material. */
            @Override
            public boolean matches(CellContext context) {
                return context.material() == material;
            }
        }

        /**
         * A cell that accepts any of the listed materials.
         *
         * @param materials alternatives, e.g. any log type for a frame
         */
        record AnyOf(List<Material> materials) implements Cell {
            public AnyOf {
                materials = materials == null ? List.of() : List.copyOf(materials);
            }

            /** Convenience factory taking varargs. */
            static AnyOf of(Material... materials) {
                return new AnyOf(List.of(materials));
            }

            /** {@code true} when the position holds one of the accepted materials. */
            @Override
            public boolean matches(CellContext context) {
                return materials.contains(context.material());
            }
        }

        /**
         * A cell that accepts any block registered as a custom block, whatever its material.
         *
         * <p>Custom blocks are backed by vanilla materials, so this is what lets a multiblock frame
         * be built from machine parts rather than plain blocks.
         */
        record AnyCustomBlock() implements Cell {

            /** {@code true} when a custom block is tracked at the position. */
            @Override
            public boolean matches(CellContext context) {
                return context.customBlockId() != null;
            }
        }

        /**
         * What a position actually holds, supplied to {@link #matches(CellContext)}.
         *
         * @param material      the block's material
         * @param customBlockId the content ID when a custom block is tracked there, else {@code null}
         */
        record CellContext(Material material, org.ghostplays.ghosttech.api.util.GhostId customBlockId) {

            /** A position holding no custom block. */
            public static CellContext of(Material material) {
                return new CellContext(material, null);
            }
        }

        /** Evaluates this cell against an actual position. */
        boolean matches(CellContext context);
    }

    /**
     * One cell of the pattern.
     *
     * @param x    X offset from the origin
     * @param y    Y offset from the origin, negative downwards
     * @param z    Z offset from the origin
     * @param cell what this position must contain
     * @param key  optional marker key. Cells sharing a key are one logical part, so
     *             {@code "input"} cells can be located later for interaction.
     */
    record Cell3(int x, int y, int z, Cell cell, String key) {
    }

    /** The shape data, keyed by offset. */
    Map<String, Cell3> cells();

    /** Human readable pattern kind, for logging. */
    String kind();

    /**
     * {@code true} when positions not listed in {@link #cells()} are ignored.
     *
     * <p>Loose patterns may match on top of existing structures, which is what makes them
     * convenient for machines built into walls.
     */
    default boolean loose() {
        return "loose".equals(kind());
    }

    /** The keys present in the pattern, e.g. {@code [frame, input, output]}. */
    default java.util.Set<String> keys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Cell3 cell : cells().values()) {
            if (cell.key() != null && !cell.key().isBlank()) {
                keys.add(cell.key());
            }
        }
        return keys;
    }

    /** A strict pattern: every listed cell must match and nothing else may be present. */
    static MultiBlockPattern exact(Map<String, Cell3> cells) {
        return new PatternRecord(cells, "exact");
    }

    /** A lenient pattern: only listed cells are checked. */
    static MultiBlockPattern loose(Map<String, Cell3> cells) {
        return new PatternRecord(cells, "loose");
    }

    /**
     * Computes the anchor (origin) of a shape: the minimum Y, then minimum X, then minimum Z.
     *
     * <p>Gives every instance of a shape a deterministic anchor so reloads find the same origin.
     */
    static Location anchor(List<Cell3> cells, Location candidate) {
        int x = 0;
        int y = 0;
        int z = 0;
        boolean first = true;
        for (Cell3 cell : cells) {
            if (first) {
                x = cell.x();
                y = cell.y();
                z = cell.z();
                first = false;
            } else {
                x = Math.min(x, cell.x());
                y = Math.min(y, cell.y());
                z = Math.min(z, cell.z());
            }
        }
        return candidate.clone().add(x, y, z);
    }
}