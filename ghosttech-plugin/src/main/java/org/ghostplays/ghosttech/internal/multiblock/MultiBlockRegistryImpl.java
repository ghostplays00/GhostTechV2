package org.ghostplays.ghosttech.internal.multiblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.ghostplays.ghosttech.api.content.ContentType;
import org.ghostplays.ghosttech.api.multiblock.MultiBlock;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockContext;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockPattern;
import org.ghostplays.ghosttech.api.multiblock.MultiBlockRegistry;
import org.ghostplays.ghosttech.api.util.GhostId;
import org.ghostplays.ghosttech.internal.content.ContentRegistryImpl;
import org.ghostplays.ghosttech.internal.database.Schema;
import org.ghostplays.ghosttech.internal.database.SqliteDatabaseService;

/**
 * {@link MultiBlockRegistry} with SQLite-backed instance tracking.
 *
 * <h2>How a multiblock forms</h2>
 * When a block is placed, every registered pattern is tested against the candidate origins that the
 * new block could anchor. A matching pattern is recorded as an instance, together with a row per
 * part so that a later click on any block of the structure can be routed to the owning module
 * without re-matching.
 *
 * <h2>Anchor choice</h2>
 * The anchor is the lowest, then most negative X, then most negative Z cell of the pattern. Fixing
 * the anchor rule means the same structure always resolves to the same origin, so instances survive
 * a restart and are re-found correctly.
 */
public final class MultiBlockRegistryImpl implements MultiBlockRegistry {

    private final Map<GhostId, MultiBlock> definitions = new ConcurrentHashMap<>();

    /** Anchor key to multiblock ID. */
    private final Map<String, GhostId> instances = new ConcurrentHashMap<>();

    /** Part position key to pattern key, for interaction routing. */
    private final Map<String, String> parts = new ConcurrentHashMap<>();

    /** Part position key to multiblock ID. */
    private final Map<String, GhostId> partOwners = new ConcurrentHashMap<>();

    /** Part position key to its instance anchor, so a click needs no re-matching. */
    private final Map<String, Location> partAnchors = new ConcurrentHashMap<>();

    private final SqliteDatabaseService database;
    private final ContentRegistryImpl content;
    private final Logger logger;

    /**
     * @param database the instance store
     * @param content  the shared content registry
     * @param logger   logger for load warnings
     */
    public MultiBlockRegistryImpl(SqliteDatabaseService database, ContentRegistryImpl content,
            Logger logger) {
        this.database = database;
        this.content = content;
        this.logger = logger;
    }

    @Override
    public void register(MultiBlock multiBlock) {
        if (definitions.putIfAbsent(multiBlock.id(), multiBlock) != null) {
            throw new IllegalArgumentException(
                    "MultiBlock " + multiBlock.id() + " is already registered");
        }
        content.register(ContentType.MULTIBLOCK, multiBlock.id(), multiBlock, multiBlock.texture());
    }

    @Override
    public Optional<MultiBlock> multiBlock(GhostId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Collection<MultiBlock> multiBlocks() {
        List<MultiBlock> all = new ArrayList<>(definitions.values());
        all.sort((left, right) -> left.id().compareTo(right.id()));
        return List.copyOf(all);
    }

    @Override
    public Optional<MultiBlock> formedAt(Location origin) {
        if (origin.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(instances.get(originKey(origin)))
                .flatMap(this::multiBlock);
    }

    @Override
    public Optional<MultiBlock> instanceAt(Block block) {
        return Optional.ofNullable(partOwners.get(blockKey(block.getLocation())))
                .flatMap(this::multiBlock);
    }

    @Override
    public Optional<String> keyAt(Block block) {
        return Optional.ofNullable(parts.get(blockKey(block.getLocation())));
    }
    @Override
    public boolean checkForm(Location candidate) {
        if (candidate.getWorld() == null) {
            return false;
        }
        boolean formed = false;
        for (MultiBlock multiBlock : definitions.values()) {
            MultiBlockPattern pattern = multiBlock.pattern();
            // The anchor is derived from the pattern, so the same structure always resolves to the
            // same origin, which is what lets instances survive a restart.
            Location anchor = MultiBlockPattern.anchor(
                    new ArrayList<>(pattern.cells().values()), candidate);
            if (!matches(pattern, anchor)) {
                continue;
            }
            record(multiBlock, anchor, pattern);
            formed = true;
        }
        return formed;
    }

    /**
     * Tests a pattern against an origin.
     *
     * <p>Loose patterns only check their listed cells. Exact patterns additionally require the
     * bounding box to be free of unexpected blocks, so a shape does not form inside unrelated
     * material.
     *
     * @param pattern the shape
     * @param anchor  the candidate origin
     * @return {@code true} when the structure matches
     */
    private boolean matches(MultiBlockPattern pattern, Location anchor) {
        World world = anchor.getWorld();
        for (MultiBlockPattern.Cell3 cell : pattern.cells().values()) {
            Block block = world.getBlockAt(anchor.getBlockX() + cell.x(),
                    anchor.getBlockY() + cell.y(), anchor.getBlockZ() + cell.z());
            if (!cell.cell().matches(contextFor(block))) {
                return false;
            }
        }
        return pattern.loose() || exactBoundsAreClear(pattern, anchor);
    }

    /**
     * For an exact pattern, every position in the bounding box must be declared by the pattern.
     *
     * @param pattern the shape
     * @param anchor  the origin
     * @return {@code true} when nothing unexpected occupies the bounding box
     */
    private boolean exactBoundsAreClear(MultiBlockPattern pattern, Location anchor) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (MultiBlockPattern.Cell3 cell : pattern.cells().values()) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        World world = anchor.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (pattern.cells().containsKey(x + "," + y + "," + z)) {
                        continue;
                    }
                    Block block = world.getBlockAt(
                            anchor.getBlockX() + x, anchor.getBlockY() + y, anchor.getBlockZ() + z);
                    if (!block.getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Builds the match context for a position, including any custom block tracked there.
     *
     * @param block the block
     * @return the context
     */
    private MultiBlockPattern.Cell.CellContext contextFor(Block block) {
        return new MultiBlockPattern.Cell.CellContext(block.getType(), customBlockLookup.apply(block));
    }

    /**
     * Supplies the custom block ID at a position to the pattern matcher.
     *
     * <p>Set by the plugin so the multiblock registry does not depend on the block registry.
     *
     * @param supplier returns the tracked ID at a block, or {@code null}
     */
    public void setCustomBlockLookup(java.util.function.Function<Block, GhostId> supplier) {
        this.customBlockLookup = supplier;
    }
    /**
     * Records a formed instance and its parts, then notifies the module.
     *
     * @param multiBlock the definition
     * @param anchor     the origin
     * @param pattern    the shape, used to record part keys
     */
    private void record(MultiBlock multiBlock, Location anchor, MultiBlockPattern pattern) {
        GhostId id = multiBlock.id();
        instances.put(originKey(anchor), id);

        database.execute(
                "INSERT OR REPLACE INTO " + Schema.TABLE_MULTIBLOCKS
                        + " (world, x, y, z, multiblock_id, formed_at) VALUES (?, ?, ?, ?, ?, ?)",
                Schema.positionArgs(anchor)[0],
                anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ(),
                id.toString(), System.currentTimeMillis());

        for (MultiBlockPattern.Cell3 cell : pattern.cells().values()) {
            Location partLocation = anchor.clone().add(cell.x(), cell.y(), cell.z());
            String partKey = blockKey(partLocation);
            parts.put(partKey, cell.key());
            partOwners.put(partKey, id);
            // The anchor is stored alongside each part so a click can be routed without re-matching.
            partAnchors.put(partKey, anchor.clone());
            database.execute(
                    "INSERT OR REPLACE INTO " + Schema.TABLE_MULTIBLOCK_PARTS
                            + " (world, x, y, z, multiblock_id, part_key)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    Schema.positionArgs(partLocation)[0],
                    partLocation.getBlockX(), partLocation.getBlockY(), partLocation.getBlockZ(),
                    id.toString(), cell.key());
        }

        // A module may throw here; that must not corrupt the bookkeeping above.
        try {
            multiBlock.onForm(new Context(id, anchor, null, null, null));
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE,
                    "Module " + id + " failed while forming a multiblock", exception);
        }
    }

    @Override
    public void removeInstance(Location origin) {
        if (origin.getWorld() == null) {
            return;
        }
        // The owning ID is read before the instance is dropped from the cache, since the part
        // cleanup below needs to know which pattern's offsets to use.
        GhostId ownerId = instances.remove(originKey(origin));

        // Remove every cached part whose recorded anchor is this origin. Comparing anchors directly
        // is reliable, because all anchors for a structure come from the same anchor rule.
        List<String> doomedParts = partAnchors.entrySet().stream()
                .filter(entry -> entry.getValue().equals(origin))
                .map(Map.Entry::getKey)
                .toList();
        for (String part : doomedParts) {
            partAnchors.remove(part);
            partOwners.remove(part);
            parts.remove(part);
        }

        // Part rows are stored without their anchor, so the rows are removed by walking the owning
        // pattern's cell offsets from this origin.
        if (ownerId != null) {
            for (MultiBlock multiBlock : definitions.values()) {
                if (!multiBlock.id().equals(ownerId)) {
                    continue;
                }
                for (MultiBlockPattern.Cell3 cell : multiBlock.pattern().cells().values()) {
                    database.execute("DELETE FROM " + Schema.TABLE_MULTIBLOCK_PARTS
                                    + " WHERE world=? AND x=? AND y=? AND z=?",
                            origin.getWorld().getName(),
                            origin.getBlockX() + cell.x(),
                            origin.getBlockY() + cell.y(),
                            origin.getBlockZ() + cell.z());
                }
                break;
            }
        }

        database.execute("DELETE FROM " + Schema.TABLE_MULTIBLOCKS + " WHERE "
                + Schema.positionClause(), Schema.positionArgs(origin));
    }

    /**
     * Dispatches an interaction on a formed structure.
     *
     * <p>Only fires when the clicked block's key is one the module declared, so clicking a frame
     * does not trigger the machine's output handler.
     *
     * @param block  the clicked block
     * @param player who clicked
     * @return {@code true} when a module handled it
     */
    public boolean interact(Block block, Player player) {
        Optional<MultiBlock> found = instanceAt(block);
        if (found.isEmpty()) {
            return false;
        }
        MultiBlock multiBlock = found.get();
        String partKey = parts.get(blockKey(block.getLocation()));
        if (partKey == null || !multiBlock.interactionKeys().contains(partKey)) {
            return false;
        }
        Location anchor = partAnchors.get(blockKey(block.getLocation()));
        try {
            multiBlock.onInteract(
                    new Context(multiBlock.id(), anchor, block, partKey, player));
            return true;
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE,
                    "Module " + multiBlock.id() + " failed handling a multiblock interaction",
                    exception);
            return false;
        }
    }

    /**
     * Notifies a module that a block of its structure was broken.
     *
     * @param block  the broken block
     * @param player who broke it, or {@code null} when environmental
     * @return {@code true} when a module was notified
     */
    public boolean notifyBreak(Block block, Player player) {
        Optional<MultiBlock> found = instanceAt(block);
        if (found.isEmpty()) {
            return false;
        }
        MultiBlock multiBlock = found.get();
        String partKey = parts.get(blockKey(block.getLocation()));
        Location anchor = partAnchors.get(blockKey(block.getLocation()));
        try {
            multiBlock.onBreak(new Context(multiBlock.id(), anchor, block, partKey, player));
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE,
                    "Module " + multiBlock.id() + " failed handling a multiblock break", exception);
            return false;
        }
        if (!multiBlock.persistent()) {
            removeInstance(anchor);
        }
        return true;
    }
    /**
     * Loads formed instances and their parts from the database.
     *
     * <p>Called on startup and after a reload so structures survive a restart.
     *
     * @return a future completing once the caches are warm
     */
    public CompletableFuture<Void> loadAll() {
        CompletableFuture<List<Object[]>> instanceRows = database.query(
                "SELECT world, x, y, z, multiblock_id FROM " + Schema.TABLE_MULTIBLOCKS,
                row -> new Object[]{
                        row.getString("world"),
                        row.getInt("x"),
                        row.getInt("y"),
                        row.getInt("z"),
                        row.getString("multiblock_id")
                });

        CompletableFuture<List<Object[]>> partRows = database.query(
                "SELECT world, x, y, z, multiblock_id, part_key FROM "
                        + Schema.TABLE_MULTIBLOCK_PARTS,
                row -> new Object[]{
                        row.getString("world"),
                        row.getInt("x"),
                        row.getInt("y"),
                        row.getInt("z"),
                        row.getString("multiblock_id"),
                        row.getString("part_key")
                });

        return CompletableFuture.allOf(instanceRows, partRows).thenAccept(ignored -> {
            instances.clear();
            partOwners.clear();
            parts.clear();
            partAnchors.clear();

            for (Object[] row : instanceRows.join()) {
                GhostId id = GhostId.parseOrNull((String) row[4]);
                World world = Bukkit.getWorld((String) row[0]);
                if (id == null || world == null) {
                    continue;
                }
                instances.put(originKey(world, (Integer) row[1], (Integer) row[2], (Integer) row[3]),
                        id);
            }
            for (Object[] row : partRows.join()) {
                GhostId id = GhostId.parseOrNull((String) row[4]);
                World world = Bukkit.getWorld((String) row[0]);
                if (id == null || world == null) {
                    continue;
                }
                Location partLocation = new Location(world, (Integer) row[1], (Integer) row[2],
                        (Integer) row[3]);
                String partKey = blockKey(partLocation);
                parts.put(partKey, (String) row[5]);
                partOwners.put(partKey, id);
                partAnchors.put(partKey, anchorFor(id, partLocation));
            }
            logger.info("Loaded " + instances.size() + " formed multiblocks");
        });
    }

    /**
     * Derives an instance's anchor from one of its parts.
     *
     * <p>The anchor rule is fixed, so testing candidate origins derived from the part position
     * recovers the origin without storing it a second time.
     *
     * @param id           the multiblock ID
     * @param partLocation a known part position
     * @return the origin, or the part location when the definition is unknown
     */
    private Location anchorFor(GhostId id, Location partLocation) {
        Optional<MultiBlock> found = multiBlock(id);
        if (found.isEmpty()) {
            return partLocation;
        }
        MultiBlockPattern pattern = found.get().pattern();
        List<MultiBlockPattern.Cell3> cells = new ArrayList<>(pattern.cells().values());
        for (MultiBlockPattern.Cell3 cell : cells) {
            Location candidate = partLocation.clone().subtract(cell.x(), cell.y(), cell.z());
            Location anchor = MultiBlockPattern.anchor(cells, candidate);
            if (anchor.equals(candidate)) {
                return anchor;
            }
        }
        return partLocation;
    }

    @Override
    public Collection<Location> instances() {
        List<Location> origins = new ArrayList<>();
        for (String key : instances.keySet()) {
            String[] parts = key.split(":");
            if (parts.length < 4) {
                continue;
            }
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }
            origins.add(new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        }
        return List.copyOf(origins);
    }

    @Override
    public int instanceCount() {
        return instances.size();
    }
        /**
     * Removes every multiblock a module registered, used when a module is unloaded.
     *
     * @param namespace the module namespace
     * @return how many definitions were removed
     */
    public int unregisterNamespace(String namespace) {
        List<GhostId> owned = definitions.keySet().stream()
                .filter(id -> id.namespace().equals(namespace))
                .toList();
        for (GhostId id : owned) {
            definitions.remove(id);
            content.unregister(ContentType.MULTIBLOCK, id);
        }
        return owned.size();
    }

    /** Supplies the custom block ID lookup used while matching patterns. */
    private java.util.function.Function<Block, GhostId> customBlockLookup = block -> null;

    /**
     * Builds the cache key for an origin.
     *
     * @param location the origin
     * @return the key
     */
    private static String originKey(Location location) {
        return originKey(location.getWorld(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    private static String originKey(World world, int x, int y, int z) {
        return world.getName() + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Builds the cache key for a part position.
     *
     * @param location the position
     * @return the key
     */
    private static String blockKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX()
                + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    /** Immutable {@link MultiBlockContext} handed to modules. */
    private record Context(GhostId multiBlockId, Location origin, Block block, String key,
                           Player player) implements MultiBlockContext {
    }

    /** Renders a definition's display name as plain text, for console output. */
    public String describePlain(MultiBlock multiBlock) {
        Component name = multiBlock.displayName();
        return name == null ? multiBlock.id().toString()
                : org.ghostplays.ghosttech.internal.util.Messages.plain(name);
    }
}
