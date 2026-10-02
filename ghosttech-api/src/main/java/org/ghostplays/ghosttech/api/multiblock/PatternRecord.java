package org.ghostplays.ghosttech.api.multiblock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable {@link MultiBlockPattern} implementation.
 *
 * <p>Cells are held in a {@link LinkedHashMap} keyed by {@code "x,y,z"} so iteration order follows
 * the declaration order, which keeps pattern matching and debug output deterministic.
 *
 * @param cells the shape, keyed by offset string
 * @param kind  {@code "exact"} or {@code "loose"}
 */
public record PatternRecord(Map<String, Cell3> cells, String kind) implements MultiBlockPattern {

    public PatternRecord {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("A multiblock pattern must contain at least one cell");
        }
        cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
        kind = kind == null || kind.isBlank() ? "exact" : kind;
    }

    /**
     * Builds a pattern from cells, keying them by their {@code "x,y,z"} offset.
     *
     * @param kind {@code "exact"} or {@code "loose"}
     * @param cells the shape cells
     * @return an immutable pattern
     */
    public static PatternRecord of(String kind, List<Cell3> cells) {
        if (cells == null || cells.isEmpty()) {
            throw new IllegalArgumentException("A multiblock pattern must contain at least one cell");
        }
        Map<String, Cell3> byOffset = new LinkedHashMap<>();
        for (Cell3 cell : cells) {
            byOffset.put(offsetKey(cell.x(), cell.y(), cell.z()), cell);
        }
        return new PatternRecord(byOffset, kind);
    }

    /** The canonical map key for an offset. */
    public static String offsetKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}