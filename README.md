# Create: Loconautics — decompiled source (0.39) + fixes

Unofficial source tree of **[Create: Loconautics](https://www.curseforge.com/minecraft/mc-mods/loconautics) 0.39** for NeoForge 1.21.1. Loconautics turns Create trains into physical Sable sub-levels riding on the tracks.

The original mod is made by **Lycoris, MDLOP, Basilforrealss, TugKanTheMan and Hatchet**. This tree was recovered from the 0.39 release jar with [Vineflower](https://github.com/Vineflower/vineflower) 1.12.0, with permission from the mod's team. A handful of bug and security fixes are applied on top. This is not an official repository: for the real mod, support and updates, go to the original project.

## What's changed compared to 0.39

| Where | Change |
|-------|--------|
| `network/packets/variantpicker/BlockVariantApplyPacket` | **Exploit fix.** The server only checked that both blocks were *some* known variant, so a forged packet could turn a stack of one block into a stack of any other (e.g. postboxes → engines). Switching is now limited to the same variant family. |
| `trainphysics/spawn/SableTrainSpawner#relocate` | **Exploit fix.** Holding the train relocator was only checked client-side, so a forged packet could pull any train to the sender. The server now requires the relocator in hand. Optional `relocation.requireOwnership` server config limits it to the owner or operators. |
| `trainphysics/rail/RailSeams` + `LoconauticsClient` | **Memory leak.** `CLIENT_LEVELS` was never cleared, keeping unloaded client worlds alive after leaving. It is now cleared on level unload and on disconnect. |
| ~250 logger calls | Tagged debug traces (`[maneuver]`, `[boilerfix]`, `[issue274]`, …) moved from INFO to DEBUG. Some fired every second per boiler. Output of the explicit `/dbgloco … trace` commands stays at INFO. |
| `foundation/debug/StallWatchdog` | No longer starts a permanent background thread polling 10×/s unless portal tracing is enabled. |
| `mixin/BlockEntityTickContextMixin` | The per-block-entity tick hook now skips everything that isn't a Create `SmartBlockEntity`. |
| `network/RejectedPacketLog` | Rejected (likely forged) packets are logged at most once per player every 10 s, so a cheating client can't flood the server log. |
| `compat/heat/PropulsionEngineHeat`, `SmallEngineBlockEntity` | **Crash fix.** The small combustion engine no longer crashes the game (placing it, its ponder, loading a world with it) when Create Propulsion isn't installed. |
| `content/hatch/HullsteelSocketBridge` | The one method Vineflower couldn't decompile, rebuilt by hand from the bytecode. |

Version string: `0.39+fixes.1`. The mod id is still `loconautics`, so the built jar replaces the original.

## Building

Requires JDK 21.

```sh
./gradlew fetchLibs   # downloads the mod jars Loconautics compiles against into ./libs
./gradlew build       # -> build/libs/loconautics-0.39+fixes.1.jar
```

`fetchLibs` downloads these versions from Modrinth and unpacks their nested JarJar jars. Every one of the 4689 references the original bytecode makes into these mods resolves against them:

| Mod | Version |
|-----|---------|
| Create (+ Flywheel, Ponder/Catnip, Registrate) | 6.0.10 |
| Sable (+ Rapier, sable-companion, Veil) | **2.0.6** |
| Simulated, Aeronautics (from `create-aeronautics-bundled`) | 1.3.2 |
| CC: Tweaked | 1.121.0 |
| Create Big Cannons | 5.11.7 |
| Steam 'n' Rails | 0.3.0-beta.2 |
| Create Diesel Generators | 1.3.15 |
| Create Propulsion: Simulated | 1.1.5 |

> **Note:** Loconautics 0.39 needs **Sable 2.x** at runtime. It uses the Sable 2 physics-constraint and sub-level ticket APIs.

### Status

The tree was produced by a decompiler: all 957 files parse cleanly, but a full type-checked compile against Minecraft hasn't been confirmed yet. If `build` reports errors, please open an issue with the log.

## License

The **code** is MIT-licensed, same as the original. See [LICENSE](LICENSE).

The **assets** (models, textures, sounds) are **All Rights Reserved** by the original authors and are included with their permission. They are not covered by the MIT license.

---

### По-русски

Неофициальные исходники Create: Loconautics 0.39, восстановленные декомпилятором с разрешения команды мода, с исправлениями эксплойтов, утечки памяти и спама в логах (подробности — в таблице выше). Сборка: `./gradlew fetchLibs`, затем `./gradlew build`. В игре нужна Sable 2.x.
