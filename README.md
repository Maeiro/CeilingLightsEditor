# Viewpoint Ceiling Light Sources

Experimental client integration for **Ceiling Lights (Viewpoint) 2.0.0**, **Project Viewpoint 0.1.7**, **ZombieBuddy 2.3.0+**, and **Project Zomboid 42.22.2**.

CeilingLights places decorative fixtures across rooms, but the renderer normally puts a room light at the centre of each room-light rectangle. This mod captures the positions actually submitted by CeilingLights and registers virtual light sources at those fixtures. Each light sits just below its model's lowest point to avoid placing the source inside its housing. No CeilingLights meshes or textures are included in this package.

## Installation

1. Enable **Viewpoint Ceiling Light Sources**, **Ceiling Lights (Viewpoint)**, **Viewpoint**, and **ZombieBuddy** in Mods.
2. Exit and restart Project Zomboid completely. Lua reload does not load Java patches.
3. Approve the mod's Java patches when ZombieBuddy requests approval.
4. Enter a world and use a Viewpoint 3D camera in an electrically lit room.

This package uses ZombieBuddy directly. It is not an entry that must be activated in Viewpoint's managed Addons list. It requires no additional shaderpack and keeps the currently selected look, including Default, Somber, and Bath & Shower Effects variants.

An initial user-reported in-game test confirmed that the lighting replacement works well. Broader visual, multiplayer, and performance checks remain pending.

The addon changes Viewpoint's internal source registry, not `IsoCell.roomLights`, map objects, saves, electrical rules, or server state. Only clients that want these visuals need it. Server restrictions on client mods still apply. Multiplayer visual verification is pending.

## Local settings

On its first startup, the mod creates `Zomboid/Lua/ViewpointCeilingLightSources.properties`:

```properties
enabled=true
intensity=1.0
reach=6
diagnostics=false
```

- `enabled`: turn the replacement on or off.
- `intensity`: 0.25–2.0, default 1.0; multiplies the original room-light colour/strength.
- `reach`: 3–12 squares, default 6.
- `diagnostics`: aggregated statistics every ten seconds, off by default.

The file is checked every two seconds. Changes take effect on the next native lighting update; they can be delayed while paused. Invalid values keep the previous valid configuration and produce one error per changed file. There is no Mod Options page in this initial build.

Look in `Zomboid/console.txt` for `[CeilingLightSources]`. Startup reports compatibility and the settings path. The first completed replacement reports `first room replaced; fixtures=...`. Diagnostics report captured squares, cached rooms, virtual sources, and rooms replaced during the preceding registry update.

## Behaviour and limits

- Positions come from successful CeilingLights model placements, including fractional tile coordinates, model selection, stairs exclusions, and ceiling-height adjustments.
- A room is replaced only after all its loaded room-light rectangles have been meshed and at least one fixture was captured. Incomplete/unloaded rooms retain their original light.
- Virtual lights are prepared through Viewpoint's existing light worker. While their calculations are pending, they are excluded from direct rendering and the original room light remains active. Empty results do not suppress the original source.
- The room switch powers the replacement lights. Inactive rooms publish no virtual sources. No other lamps, torches, headlights, or daylight sources are replaced.
- At most 64 extra active sources are prepared per native update. A room that would exceed this safety limit retains its original lighting. Viewpoint still selects the lights and shadows it draws using its own distance and shaderpack limits; this mod does not raise those budgets.
- Rooms exceeding 4,096 room-light rectangle tiles retain the original path. Captured-square storage is bounded and unloaded entries are pruned.
- This is an internal integration. A SHA-256 compatibility check covers the renderer/placement classes it depends on. Changed classes disable the integration with an explicit log instead of guessing their layout.

The native ambient grid and direct-source handover are asynchronous. The direct central source and replacement sources are selected together at a native update, but a short ambient-light transition can remain while the worker rebuilds its grid. Native tile-level occlusion is retained; precise shadows, glass, open doors, multistorey rooms, and inclined roofs still require in-game visual checks.

## Build and checks

Run `tools/Build.ps1` with JDK 25; it compiles with `--release 17`. Parameters allow overriding the game directory and dependency JARs. The package contains only this mod's Java classes and small metadata resources.

`tools/Test.ps1` builds the mod, exercises its state machine against the actual installed Viewpoint registry with isolated game fixtures, and uses the installed ZombieBuddy transformer plus ByteBuddy to verify that all seven patches can be woven into their actual target methods. Test fixtures and checking tools are excluded from the shipped JAR.

Automated checks cover partial capture, native-work readiness, empty results, stable identities, frames without a registry update, switch state, model removal, unload fallback, configuration validation, and world cleanup. These checks do not establish visual alignment, multiplayer correctness, or GPU cost.

## In-game acceptance

Use the same large room with the addon enabled and disabled. Check that the bright points move from the room centre to the physical fixtures, then test power switches, generators, doors, walls, upper floors, stairs, and return visits after leaving the area. Repeat in multiplayer. Compare the Viewpoint Performance page and frame times with the same shader settings. These in-game comparisons remain pending; the game was not restarted or controlled during installation.
