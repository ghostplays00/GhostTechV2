# GhostTech V2

A modular Minecraft **26.x** plugin (Paper) for custom blocks, multiblocks, custom GUIs, native
dialogs, permissions and tab-completed commands — where the actual features live in **separate module
jars** the plugin loads at runtime.

**Status: alpha.** The architecture and all core systems are implemented and compiling. See
[Status](#status) for what is done and what is not.

---

## Table of contents

- [Why modular](#why-modular)
- [Features](#features)
- [Requirements](#requirements)
- [Building](#building)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Writing your first module](#writing-your-first-module)
- [Architecture at a glance](#architecture-at-a-glance)
- [Design decisions](#design-decisions)
- [Status](#status)

---

## Why modular

The plugin is a **platform**. Your content is a **module**. That means:

- A module jar holds its own logic. The plugin holds the shared machinery.
- Modules are isolated: two modules cannot reference each other's classes, so they stay independently
  removable and independently loadable.
- Content IDs are namespaced per module, so `module1:legendarysword` and `module2:rubysword` can
  never collide.
- A broken module is reported and skipped. The server and every other module keep running.

Everything the plugin can do, a module can do. There is no "pro" feature living only in the plugin.

---

## Features

### Custom blocks
Backed by real vanilla materials, so they work on any Paper 26.x server with **no resource pack and
no client mod**. Each block defines its material, name, lore, permission, drops, and behaviour for
place, break, interact and destruction.

### Multiblocks
Structures built from ordinary blocks. Patterns are declared as cells with offsets and logical keys,
and come in two strictness levels:

- **exact** — every listed cell must match and the bounding box must be otherwise clear
- **loose** — only listed cells are checked, so structures can form around existing terrain

Keys make interaction precise: right-clicking a machine's `input` block fires your handler while the
frame stays inert.

### Custom GUI + storage
GUI construction is provided through **Triumph**, wrapped behind a plugin interface so modules never
import the library directly. Three shapes are available:

| Type | Use |
| --- | --- |
| `chest` | fixed-size menus |
| `paginated` | automatic paging with navigation controls |
| `storage` | persistent inventories backed by SQLite |

Storage inventories survive restarts and serialise with Bukkit's own streams, so items from other
plugins round-trip faithfully.

### Native dialogs
Built on Paper's server-side dialog API: notices, confirmations, option lists, and text or number
inputs. They render natively in the client, so a confirmation cannot be spoofed with a renamed item
and the player's inventory is never opened.

### Permissions
Node registration with descriptions, wildcard resolution (`ghosttech.block.*`, `ghosttech.*`), custom
groups with transitive inheritance, and direct grants stored in SQLite. Works standalone or alongside
LuckPerms — registered nodes are attached to Bukkit, so permission plugins see them.

### Commands with auto-complete
A single command framework used by both the plugin and modules. Completion resolves the subcommand
tree, respects permissions so players never see commands they cannot run, and lets each command
supply its own argument candidates — including live content IDs.

### SQLite
WAL mode, a dedicated database thread, and parameter binding everywhere so module code cannot inject
SQL. Table names are validated as identifiers, since those cannot be parameterised.

### Textures (prepared)
Every content entry can already declare a `GhostTextureId`. Nothing renders it yet, because no
textures ship — but the ID plumbing exists, so a future resource pack needs no redesign.

---

## Requirements

| | |
| --- | --- |
| Server | Paper 26.x |
| Java | 25 |
| Gradle | 9.7.0 (wrapper included) |

Libraries are resolved by the server at startup — no manual downloads:

- `dev.triumphteam:triumph-gui:3.1.13`
- `org.xerial:sqlite-jdbc:3.53.4.0`

---

## Building

```powershell
# Everything
.\gradlew.bat build

# Just the plugin jar
.\gradlew.bat :ghosttech-plugin:build

# A dev server with Paper 26.3
.\gradlew.bat :ghosttech-plugin:runServer
```

Output:

```
ghosttech-plugin/build/libs/ghosttech-plugin-1.0-alpha.jar    <- deploy this
ghosttech-api/build/libs/ghosttech-api-1.0-alpha.jar           <- compile against this
modules/example-module/build/libs/example-module-1.0-alpha.jar <- a module
```

> **Note:** Gradle writes to native stderr, which some PowerShell wrappers treat as a fatal error.
> Redirect to a log when scripting:
> ```powershell
> .\gradlew.bat build --console=plain --no-daemon > build-log.txt 2>&1
> ```

---

## Installation

1. Drop `ghosttech-plugin-1.0-alpha.jar` into `plugins/`.
2. Start the server once to generate `plugins/GhostTechV2/`.
3. Drop module jars into `plugins/GhostTechV2/modules/`.
4. Restart, or run `/ghosttech reload`.

```
plugins/
└── GhostTechV2/
    ├── config.yml
    ├── ghosttech.db
    └── modules/
        ├── example-module-1.0-alpha.jar
        └── your-module-1.0.0.jar
```

On startup the console reports what loaded:

```
[GhostTechV2] Enabled module example v1.0.0
[GhostTechV2] Loaded 12 tracked custom blocks
[GhostTechV2] GhostTech V2 enabled: 3 custom blocks, 2 multiblocks, 1 modules
```

---
## Commands

Root: `/ghosttech`, aliased `/gt` and `/ghosttechv2`.

| Command | Permission | Description |
| --- | --- | --- |
| `/ghosttech help` | `ghosttech.command` | List available commands |
| `/ghosttech version` | `ghosttech.command` | Versions and content counts |
| `/ghosttech block list` | `ghosttech.command.block` | List custom blocks |
| `/ghosttech block give <id> [amount]` | `ghosttech.command.block` | Give yourself a block |
| `/ghosttech multiblock list` | `ghosttech.command.multiblock` | List multiblocks |
| `/ghosttech multiblock instances` | `ghosttech.command.multiblock` | Count formed structures |
| `/ghosttech permissions nodes` | `ghosttech.command.permissions` | List registered nodes |
| `/ghosttech permissions groups` | `ghosttech.command.permissions` | List groups |
| `/ghosttech permissions check <node>` | `ghosttech.command.permissions` | Test a node |
| `/ghosttech reload` | `ghosttech.command.reload` | Reload config and modules |
| `/forge list\|menu\|info <id>` | `ghosttech.command.example.forge` | Example module |

Tab completion works at every level and hides commands you cannot run:

```
/ghosttech block <TAB>              ->  list  give
/ghosttech block give <TAB>         ->  example:legendary_forge
/ghosttech permissions check <TAB>  ->  ghosttech.command, ghosttech.*, ...
/forge info <TAB>                   ->  example:legendary_forge  example:rubyforge
```

---

## Permissions

### Plugin-owned

| Node | Default | Grants |
| --- | --- | --- |
| `ghosttech.*` | op | everything below |
| `ghosttech.command` | op | use `/ghosttech` |
| `ghosttech.command.reload` | op | `/ghosttech reload` |
| `ghosttech.command.block` | op | list and give blocks |
| `ghosttech.command.multiblock` | op | inspect multiblocks |
| `ghosttech.command.permissions` | op | inspect permissions |
| `ghosttech.module.admin` | op | administer modules |

### Module-registered

Modules register their own nodes during load, so `/ghosttech permissions nodes` lists everything,
including the example module's:

```
ghosttech.block.example.legendary_forge      op
ghosttech.multiblock.example.rubyforge       op
ghosttech.command.example.forge              op
```

### Wildcards

`ghosttech.*` covers every plugin node and `ghosttech.block.*` covers every block node, so adding a
feature does not require editing an existing group.

---

## Configuration

`plugins/GhostTechV2/config.yml`:

```yaml
prefix: "&8[&6GhostTech&8] &r"

permission-groups:
  default:
    nodes:
      - ghosttech.command
  builder:
    inherit:
      - default
    nodes:
      - ghosttech.command.block
---

## Writing your first module

### 1. Build script

```kotlin
plugins { java }

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly(files("../ghosttech-api/build/libs/ghosttech-api-1.0-alpha.jar"))
}
```

Compile against the API with `compileOnly` — the plugin jar already contains those classes at runtime.

### 2. Descriptor: `module.yml` at the jar root

```yaml
id: mymodule                    # becomes the namespace for all your content IDs
name: My Module
version: 1.0.0
main: com.example.MyModule
api-version: "1.0"
authors: [ YourName ]
description: Does something useful

depend: [ ]                     # hard: skipped entirely if missing
soft-depend: [ ]                # soft: loaded first if present
```

### 3. Entry point

```java
package com.example;

import org.ghostplays.ghosttech.api.GhostTechApi;
import org.ghostplays.ghosttech.api.module.GhostTechModule;
import org.ghostplays.ghosttech.api.module.ModuleContext;

public final class MyModule implements GhostTechModule {

    private GhostTechApi api;

    @Override
    public void onLoad(ModuleContext context) {
        api = context.api();

        api.blocks().register(new MyBlock());
        api.multiblocks().register(new MyMachine());
        api.commands().register(new MyCommand());
        api.permissions().register(
                "ghosttech.block.mymodule.myblock",
                net.kyori.adventure.text.Component.text("Place My Blocks"),
                org.ghostplays.ghosttech.api.permission.DefaultValue.OP);
    }

    @Override
    public void onEnable() { /* start behaviour */ }

    @Override
    public void onDisable() { /* release resources */ }
}
```

**Register in `onLoad`, not `onEnable`.** The plugin reads module content after all modules have
loaded, so anything registered in `onEnable` is invisible to modules querying it during their load.

### 4. A custom block

```java
public final class MyBlock implements CustomBlock {

    public static final GhostId ID = GhostId.parse("mymodule:myblock");

    @Override public GhostId id() { return ID; }
    @Override public Material material() { return Material.BEACON; }
    @Override public Component displayName() { return Component.text("My Block"); }
    @Override public List<Component> lore() { return List.of(); }
    @Override public String permission() { return "ghosttech.block.mymodule.myblock"; }
    @Override public GhostTextureId texture() { return null; }

    @Override
    public Collection<ItemStack> drops(BlockContext context) {
        return List.of(new ItemStack(Material.BEACON));
    }

    @Override
    public boolean onBreak(BlockContext context) {
        return true;   // false cancels the break: put protection here
    }
}
```

### 5. A GUI

```java
GhostGui menu = api.gui().chest(3, Component.text("My Menu"), gui -> {
    gui.fillEmpty(filler);
    gui.setItem(11, new ItemStack(Material.DIAMOND), event -> {
        Player player = (Player) event.getWhoClicked();
        api.dialogs().confirmation(player,
                Component.text("Are you sure?"),
                Component.text("This cannot be undone."),
                (p, confirmed) -> p.sendMessage(
                        Component.text(confirmed ? "Done" : "Cancelled")));
    });
---

## Architecture at a glance

```
+----------------------------------------------------------+
|  ghosttech-plugin-1.0-alpha.jar                          |
|                                                          |
|  internal/          <- all the logic                      |
|    blocks | multiblocks | modules | commands               |
|    gui (Triumph) | dialogs | permissions | storage        |
|    database (SQLite) | listeners                          |
|                                                          |
|  api/              <- bundled, shared with modules        |
+----------------------------------------------------------+
     ^ compiles against                ^ loads at runtime
     |                                 |
+------------------------+   +----------------------------+
| ghosttech-api          |   | plugins/GhostTechV2/modules |
| (compileOnly)          |   |   my-module.jar             |
+------------------------+   |   other-module.jar          |
                             |   example-module.jar        |
                             +----------------------------+
```

Each module gets its own `URLClassLoader`: child-first for its own classes, parent-first for the
API, Bukkit and Adventure. That isolates modules from each other while sharing API identity with the
plugin — if a module loaded its own copy of `CustomBlock`, every `instanceof` check would fail.

See [STRUCTURE.md](STRUCTURE.md) for the full package map, data flows and threading rules.

---

## Design decisions

**Custom blocks are tracked in SQLite, not in the world.** Vanilla blocks such as stone and dirt are
not tile entities, so they have no `PersistentDataContainer` and nowhere to store an ID. The plugin
keeps its own position table. Item forms *do* use persistent data, since items always support it and
it travels with the item when dropped, stored or given to a player.

**Namespaced IDs per module.** `module1:legendarysword` and `module2:rubysword` cannot collide, and
ownership resolves from the namespace alone. IDs are validated on construction, so they are safe in
commands and config files without escaping.

**Every database call is async.** SQLite writes must not block the main thread, and `.join()` on the
main thread would deadlock the single database thread. The API returns `CompletableFuture`
throughout; the one synchronous convenience, `StorageService.count()`, returns 0 and points at
`countAsync()`.

**Libraries are declared in `plugin.yml`, not shaded.** Paper resolves Maven coordinates at startup,
so the plugin jar stays small and dependency bumps need no shadow configuration or relocation rules.

**Modules are loaded in dependency order with failure isolation.** Hard dependencies load first; a
module missing one is skipped, and a cycle is reported rather than hanging the load. A module that
throws is marked `FAILED` and everything depending on it is skipped, so the server keeps running.

**Tab completion is central.** One registry resolves the command tree, filters by permission, and
delegates argument candidates to each command. A module command behaves exactly like a built-in one,
because it *is* the same code path.

---

## Status

### Implemented and compiling

- Gradle multi-module build with wrapper, Java 25 toolchain
- `ghosttech-api` contract: blocks, multiblocks, content, commands, GUI, dialogs, permissions,
  storage, database, module system
- Module loader: `module.yml` parsing, dependency-ordered loading, classloader isolation, message bus
- SQLite: WAL, dedicated thread, schema, typed binding, table-name validation
- Custom blocks: position store, item PDC, place/break/interact/destroy, permissions, drops
- Multiblocks: exact and loose patterns, keyed cells, instance tracking, click routing
- GUI: chest, paginated and persistent storage via Triumph
- Dialogs: notice, confirmation, options, text and number input via the Paper dialog API
- Permissions: registration, wildcards, groups with inheritance, SQLite grants
- Commands: nested tree, permission-aware tab completion, built-in commands
- Example module: block, multiblock, command, GUI, dialog, messaging

### Known gaps

- **`api-version` is set to `1.21`** in `plugin.yml` rather than `26.3`, so the plugin loads across
  26.x builds. Worth confirming against your target server.
- **`multiblock.search-radius`** is in `config.yml` but not yet consulted; pattern checks currently
  test only the candidate anchor rather than a radius of surrounding origins.
- **`storage.default-rows`** is likewise read from config but not yet wired into the GUI factory.
- **`log-startup-summary`** is declared but not yet read.
- **No automated tests.** The build is verified by compiling and by inspecting jar contents, but
  there is no test suite yet.
- **`/ghosttech module <list|reload>`** commands are not implemented; use `/ghosttech reload`.
- **Multiblock shapes are tested on placement only.** A structure whose anchor block is placed last
  may not be detected until the next block placement nearby.
- **No permission-plugin bridge.** Registered nodes work with LuckPerms, but there is no direct
  LuckPerms API integration.

---

## Building on this

Suggested order for the next pieces of work:

1. Wire the unread `config.yml` keys (`search-radius`, `default-rows`, `log-startup-summary`).
2. Add `/ghosttech module list|reload|info` for module administration.
3. Add multiblock re-validation on load, so a structure completed while a chunk unloaded is detected.
4. Add JUnit tests for `GhostId`, `ModuleDescriptorParser`, dependency ordering and pattern matching,
   which are pure logic and need no server.

## License

All rights reserved. Ghostplays00.
    gui.lock();     // prevents item theft
});
menu.open(player);
```

### 6. Persistent storage

```java
GhostGui.Storage backpack = api.gui().storage(6, Component.text("Backpack"));
backpack.openStored(player, GhostId.parse("mymodule:backpack_" + player.getUniqueId()));
```

Contents load on open and are written back on close. The storage API is async, so never `join()` on
the main thread.

### 7. Talk to other modules

```java
// Fire and forget
api.messaging().publish("mymodule:item-crafted", itemStack, descriptor().id());

// Ask whoever is listening and take the first reply
Object answer = api.messaging().request("mymodule:find-owner", blockId, descriptor().id());
```

Modules cannot reference each other directly, so channels are the only path. A module that throws in
a handler is logged and skipped; delivery continues to the others.

### 8. Build and deploy

```powershell
.\gradlew.bat :modules:my-module:build
Copy-Item modules\my-module\build\libs\my-module-1.0.0.jar ..\..\..\plugins\GhostTechV2\modules\
```

---
      - ghosttech.command.multiblock
  admin:
    nodes:
      - ghosttech.*

storage:
  default-rows: 6

multiblock:
  search-radius: 8
```

Messages accept **MiniMessage** markup, so `<yellow>` and `<bold>` work as well as `&` codes. Groups
support `inherit`, resolved transitively.