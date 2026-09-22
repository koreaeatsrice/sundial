# Sundial

**Time dilation for Minecraft 1.7.10 (GTNH) — server-side only.**

Sundial makes days and nights longer or shorter without breaking anything else.
It replaces the vanilla "+1 tick per tick" daylight cycle with a smooth,
monotonic clock that behaves exactly like a recurring `/time add` — the sun and
moon move steadily, the server clock never jumps backwards, and **clients need
nothing installed**.

## Features

- **Smooth, monotonic clock** — a per-dimension fractional accumulator applies
  only whole ticks, forward-only. No drift, no snap-backs.
- **Per-tick time broadcast** — the server sends its clock to clients every tick
  instead of once per second, so even modless clients see a perfectly smooth
  day/night cycle (corrections ≤ 1 tick, invisible).
- **Per-dimension scales, persisted** — stored in the world save
  (`sundial.dat`). `/timescale set` applies the scale to **ALL dimensions**
  at once, any dimension that loads later inherits it, and the value is
  re-enforced on every server start — time dilation everywhere, not just
  the Overworld.
- **Quiet console** — one log line per `/timescale` command; no per-tick
  spam.
- **Sanitized input** — NaN / Infinity / huge scales are clamped; a poisoned
  value can never wrap the world clock negative.
- **Crash-safe** — the ASM-inserted tick call is fully guarded; a failure can
  never propagate into the vanilla server tick.
- **Any client version accepted** — `acceptableRemoteVersions = "*"`; clients
  with the old mod, another version, or no mod at all can join.

## Usage

```
/timescale            -> show the current dimension's scale
/timescale set <n>    -> set it for ALL dimensions (1.0 = vanilla; 0.3333 = 3x longer days; 0 = frozen)
```

Examples:
- `0.3333` — 3× slower: 1 in-game minute = 2.5 real seconds; a full day = 60 real minutes.
- `2.0` — 2× faster (10-minute days).
- `0` — freeze the clock.

## How it works

- A **coremod** (`sundial.asm.Plugin` → `sundial.asm.Transformer`) rewrites the
  daylight-cycle increment in `WorldServer.tick()` / `WorldClient.tick()`
  (obfuscated and deobfuscated names, verified against the real jars) to call
  `WorldHandler.tick(world)`.
- The transformer also patches the server's time-broadcast cadence
  (`tickCount % 20` → every tick) so client clocks stay in lockstep.
- `WorldHandler.tick` advances the world clock from the accumulator: scale 1.0
  behaves exactly like vanilla; otherwise whole ticks are added when the
  fraction completes — the same net effect as running `/time add` on a timer.

## Building

Requires JDK 25 (GTNH Gradle toolchain) and network access to the GTNH maven:

```bash
JAVA_HOME=/opt/jdk-25 ./gradlew clean build --no-daemon
# output: build/libs/sundial-<git-hash>.jar (reobfuscated release jar; version 1.1.1 is in its metadata)
```

The release jar carries the coremod manifest
(`FMLCorePlugin: sundial.asm.Plugin`, `FMLCorePluginContainsFMLMod: true`) and
`mcmod.info`.

## Deploying (server)

1. Stop the server.
2. Drop the jar into `mods/`.
3. Start; verify in `logs/fml-server-latest.log`:
   - `Patched time-update cadence to every tick (1 site(s))`
   - `Successfully patched WorldServer.tick()`
   - `Enabling mod Sundial`

## Layout

```
src/main/java/sundial/          mod + coremod code
  asm/                          FML coremod plugin + ASM transformer
src/main/resources/mcmod.info   mod metadata
gradle.properties               modId=sundial, coreModClass=asm.Plugin
```

## Credits

Johnathan Gallagher.
