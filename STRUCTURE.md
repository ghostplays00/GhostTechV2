# Project Structure

Reference for how GhostTech V2 is organised, why, and where to add new code.

---

## 1. Layout at a glance

```
GhostTech V2/
├── settings.gradle.kts              # Declares the three Gradle modules
├── build.gradle.kts                 # Shared config: Java 25, UTF-8, reproducible jars
├── gradle.properties                # Versions: Paper, Triumph, SQLite, API version
├── gradle/wrapper/                  # Gradle 9.7.0 (required by run-paper 3.1.0)
│
├── ghosttech-api/                   # -- THE CONTRACT --------------------------
│   └── src/main/java/org/ghostplays/ghosttech/api/
│       ├── GhostTechApi.java        # The one object a module receives
│       ├── util/
│       │   ├── GhostId.java         # namespaced "module:path" identifiers
│       │   └── GhostTextureId.java  # texture IDs for a future resource pack
│       ├── module/                  # entry point, context, descriptor, state,
│       │                            #   registry, messaging bus
│       ├── block/                   # CustomBlock + registry + context
│       ├── multiblock/              # MultiBlock + pattern + registry + context
│       ├── content/                 # ContentType, ContentRegistry, ContentLookup
│       ├── command/                 # GhostCommand, context, registry + completion
│       ├── gui/                     # GuiFactory, GhostGui (+ Paginated, Storage)
│       ├── dialog/                  # DialogService
│       ├── permission/              # service, definitions, groups, defaults
│       ├── storage/                 # StorageService
│       └── database/                # DatabaseService
│
├── ghosttech-plugin/                # -- THE IMPLEMENTATION -------------------
│   ├── build.gradle.kts             # bundles the API, runs the dev server
│   └── src/main/
│       ├── java/org/ghostplays/ghosttech/internal/
│       │   ├── GhostTechPlugin.java      # main class, startup/shutdown order
│       │   ├── GhostTechApiImpl.java     # wires services into the API
│       │   ├── block/                   # SQLite position store for custom blocks
│       │   ├── multiblock/              # pattern matching + instance tracking
│       │   ├── module/                  # manager, classloader, parser, registry, bus
│       │   ├── command/                 # command tree, completion, Bukkit bridge
│       │   ├── gui/                     # Triumph -> GhostGui adapters
│       │   ├── dialog/                  # Paper dialog API
│       │   ├── permission/              # SQLite grants + groups + wildcards
│       │   ├── storage/                 # SQLite inventories
│       │   ├── content/                 # typed registry
│       │   ├── database/                # SQLite, statement helpers, schema
│       │   ├── listener/                # block events, player join/quit
│       │   └── util/                    # MiniMessage, item serialisation
│       └── resources/
│           ├── plugin.yml               # commands, permissions, libraries
│           └── config.yml               # prefix, groups, settings
│
├── modules/
│   └── example-module/             # -- A REAL MODULE JAR ---------------------
│       ├── build.gradle.kts         # compileOnly the API only
│       └── src/main/
│           ├── java/org/ghostplays/example/
│           │   ├── ExampleModule.java    # registers everything
│           │   ├── LegendaryForge.java   # custom block  example:legendary_forge
│           │   └── RubyForge.java        # multiblock    example:rubyforge
│           └── resources/module.yml       # the descriptor
│
├── README.md                        # Overview, install, usage, module guide
---

## 2. Why three modules

| Module | Ships as | Purpose |
| --- | --- | --- |
| `ghosttech-api` | Separate jar | The contract. Module authors compile against this and nothing else. |
| `ghosttech-plugin` | The plugin jar | All runtime logic. Bundles the API classes so modules need no extra jar. |
| `modules/example-module` | A module jar | Working reference, and proof the API suffices on its own. |

The split means a module author's build declares exactly two dependencies:

```kotlin
dependencies {
    compileOnly(project(":ghosttech-api"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}
```

`compileOnly` for Paper is deliberate: the server provides it. The API is `compileOnly` too, because
the plugin jar already contains those classes at runtime.

### Verified packaging

```
ghosttech-plugin-1.0-alpha.jar     121 entries   api=56  bukkit=0  triumph=0
ghosttech-api-1.0-alpha.jar        61 entries   api=56  bukkit=0  triumph=0
example-module-1.0-alpha.jar       10 entries   api=0   bukkit=0  triumph=0
```

Paper and Triumph are never bundled. The server resolves them from `plugin.yml`:

```yaml
libraries:
  - dev.triumphteam:triumph-gui:3.1.13
  - org.xerial:sqlite-jdbc:3.53.4.0
```

Bumping a dependency is therefore a one-line change with no shadow configuration and no relocation
conflicts.

---

## 3. Package responsibilities

### `api` — contract only
Interfaces, records and enums. **No logic.** If a method here needs a behavioural body, the logic
belongs in `internal`.

Rule: an API method must be implementable by more than one implementation, or it does not belong in
the contract.

### `internal` — implementation
Everything the plugin actually runs. Modules never import these classes; the classloader would
refuse to share them anyway, which is enforcement rather than convention.

### Module isolation

`ModuleClassLoader` loads child-first for a module's own classes but **parent-first** for:

```
org.ghostplays.ghosttech.api.
org.bukkit.
net.kyori.
com.google.gson.
org.jetbrains.annotations.
```

Parent-first for the API is essential. If a module loaded its own copy of `CustomBlock`, the plugin's
`instanceof CustomBlock` check would fail and every registration would silently become a no-op.

The flip side is the point of the design: two modules cannot see each other's classes, so they are
forced to communicate through `ModuleMessaging`.

---

## 5. Storage design

### Why custom blocks use a SQL position store

A custom block is a real vanilla block, so the world cannot record which blocks are custom. Vanilla
blocks such as stone and dirt are not tile entities and therefore have **no**
`PersistentDataContainer`. There is nowhere in the world to stash an ID.

So the plugin keeps its own table:

```sql
CREATE TABLE ghosttech_blocks (
    world     TEXT    NOT NULL,
    x         INTEGER NOT NULL,
    y         INTEGER NOT NULL,
    z         INTEGER NOT NULL,
    block_id  TEXT    NOT NULL,
    placed_by TEXT,
    placed_at INTEGER NOT NULL,
    PRIMARY KEY (world, x, y, z)
);
```

The **item** does use persistent data, because items always support it:

```
ghosttech:custom_block_id = "example:legendary_forge"
```

That is what makes placement recognisable. Items travel; the world cannot.

### Cache versus database

The in-memory map is authoritative during gameplay; SQLite is the durable copy. Writes hit the cache
first and the database asynchronously, so interaction is instant while a restart still restores
state. Caches are warmed **after** modules load, since a stored row is meaningless until its module
has registered the matching definition.

### Tables

| Table | Holds |
| --- | --- |
| `ghosttech_blocks` | custom block positions |
| `ghosttech_multiblocks` | formed multiblock anchors |
| `ghosttech_multiblock_parts` | part position to pattern key, for click routing |
| `ghosttech_storage` | serialised inventories as Base64 blobs |
| `ghosttech_permission_grants` | direct player grants |
| `ghosttech_permission_groups` | player group membership |
| `ghosttech_modules` | module load state |

---

## 6. Content IDs

Every content entry is namespaced by its module, so IDs cannot collide:

```
example:legendary_forge      custom block
example:rubyforge           multiblock
othermod:rubysword          a different module's content, no clash
## 7. Data flow

### Custom block placement

```
Player places item
  └─ BlockListener.onPlace
       ├─ blocks.itemId(item)                  read ID from item PDC
       ├─ permissions.hasPermission(player, node)
       ├─ blocks.track(location, id)            cache + INSERT into ghosttech_blocks
       ├─ block.onPlace(context)               module callback
       └─ multiblocks.checkForm(location)      maybe a structure just completed
```

### Multiblock formation and routing

```
Block placed
  └─ checkForm(candidate)
       └─ per pattern:
            ├─ anchor = anchor(cells, candidate)   min-Y, then min-X, then min-Z
            ├─ matches?                            exact also requires a clear bbox
            ├─ record(...)                          INSERT multiblocks + parts
            └─ onForm(context)

Later clicks route from cache, with no re-matching:
Player right-clicks
  └─ BlockListener.onInteract
       ├─ multiblocks.interact(block, player)   key must be in interactionKeys()
       └─ else: custom block onInteract
```

### Commands

```
/ghosttech block give <id>
  └─ RootCommand.onCommand
       ├─ walk args through CommandRegistryImpl    "block" resolves
       ├─ permission check                          hidden from completion too
       └─ BlockCommand.execute(context)
            └─ suggest(context) supplies IDs        same path for module commands
```

---

## 8. Module lifecycle

```
discover jar in plugins/GhostTech/modules/*.jar
  └─ parse module.yml, reject API mismatch and duplicate IDs
## 9. Where to add things

| I want to... | Do this |
| --- | --- |
| Add a block, multiblock, item, GUI or dialog | Write a module. No plugin change needed. |
| Add a command | Implement `GhostCommand`, call `api.commands().register(...)`. |
| Add a permission node | `api.permissions().register(node, description, DefaultValue.OP)`. |
| Change how the plugin behaves | Edit `internal/`. Nothing in `api/` should move. |
| Add a platform feature | Extend `GhostTechApi`, then implement in `GhostTechApiImpl`. Both are compile-checked. |
| Change a dependency version | `gradle.properties` **and** the matching `plugin.yml` `libraries:` entry. |
| Retarget another Minecraft 26.x build | `paperApiVersion` in `gradle.properties`; `minecraftVersion` for the dev server. |

### Adding an API method

Two files always change together, and the compiler enforces it:

1. Declare the method on the interface in `ghosttech-api`.
2. Implement it in the `ghosttech-plugin` service class.

Modules can then call it, because `ghosttech-api` is on their compile classpath.

---

## 10. Conventions

- **Java 25**, `--release 25`, UTF-8, enforced in the root build file.
- **Records for data**, classes for behaviour.
- **MiniMessage** for player-facing text, never legacy `&` codes in new content.
- **`GhostId`** for anything namespaced. Never a bare string ID.
- **`Optional`** for absence, `CompletableFuture` for anything touching the database.
- **Javadoc explains *why*.** The code says what; comments should record what was considered and
  rejected, which is the part a reader cannot recover from the code.
- Gradle writes to native stderr, which the shell wrapper here treats as fatal, so redirect:

```powershell
.\gradlew.bat build --console=plain --no-daemon > build-log.txt 2>&1
```
  └─ topological sort: depend first, then soft-depend
     ├─ missing hard dependency -> module skipped, with a warning
     └─ cycle -> reported, then broken rather than hung on
  └─ per module:
       ModuleClassLoader (child-first, API parent-first)
       ├─ instantiate main class
       ├─ onLoad(context)     <- ALL registration happens here
       ├─ publish "ghosttech:module-loaded"
       └─ mark LOADED
  └─ second pass: onEnable() for everything that reached LOADED
  └─ publish "ghosttech:modules-ready" with the ordered list
```

**Registration belongs in `onLoad`, not `onEnable`.** The plugin reads module content after every
module has loaded, so content registered in `onEnable` is invisible to modules querying it during
their own load.

Shutdown reverses the order, unsubscribing before disabling so a module cannot receive a message
while being torn down.

---
```

`GhostId` lower-cases and validates on construction, rejecting anything outside `[a-z0-9_.-]` for
the namespace and `[a-z0-9_/.-]` for the path. That makes IDs safe in commands, config files and
resource-pack paths without further escaping.

The namespace also routes ownership: `modules.ownerOf(contentId)` resolves the owning module by
namespace alone, with no lookup table.

### Texture IDs

`GhostTextureId` is a parallel type for texture references, declared now so a future resource pack
requires no redesign:

```java
@Override
public GhostTextureId texture() {
    return new GhostTextureId("example", "block/legendary_forge");
}
```

`assetPath("block")` yields `assets/example/textures/block/legendary_forge.png`. Today the registry
stores and returns these; nothing renders them yet, because no textures ship.

---
---

## 4. Threading model

| Work | Thread | Why |
| --- | --- | --- |
| Commands, listeners, GUI, dialogs | main | Bukkit API requires it |
| Module `onLoad` / `onEnable` / `onDisable` | main | they register into Bukkit-backed registries |
| All SQL | `GhostTech-Database` | blocking main stalls the server |
| `request` / `onClose` handlers | main | they touch inventories |

Two consequences worth internalising:

1. **Never call `.join()` on the main thread.** Every database method returns a `CompletableFuture`
   for this reason. `StorageService.load`, `.owner`, `.delete` and `.storagesInNamespace` are all
   async, and `count()` returns 0 with `countAsync()` provided for when a real number is needed.

2. **Module message handlers run synchronously on the publisher's thread.** A handler doing I/O
   stalls the server. Publish, then schedule.
└── STRUCTURE.md                     # this file
```