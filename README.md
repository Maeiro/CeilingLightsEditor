# Ceiling Lights Editor (Viewpoint)

Mod ID: `CeilingLightsEditor`

Experimental client integration for **Ceiling Lights (Viewpoint) 2.0.0**, **Project Viewpoint 0.1.7**, **ZombieBuddy 2.3.0+**, and **Project Zomboid 42.22.2**.

CeilingLights places decorative fixtures across rooms, but the renderer normally puts a room light at the centre of each room-light rectangle. This mod captures the positions actually submitted by CeilingLights and registers virtual light sources at those fixtures. Each light sits just below its model's lowest point to avoid placing the source inside its housing. No CeilingLights meshes or textures are included in this package.

## Installation

1. Enable **Ceiling Lights Editor (Viewpoint)**, **Ceiling Lights (Viewpoint)**, **Viewpoint**, and **ZombieBuddy** in Mods.
2. Exit and restart Project Zomboid completely. Lua reload does not load Java patches.
3. Approve the mod's Java patches when ZombieBuddy requests approval.
4. For the manual editor, open **Viewpoint > Advanced > Addons**, activate **Ceiling Light Editor Cursor**, and approve its **camera** and **Lua** permissions. This separate helper releases the mouse for the editor.
5. Enter a world and use a Viewpoint 3D camera in an electrically lit room. Aim at a fixture and press **F7**.

The lighting core uses ZombieBuddy directly; the cursor helper is activated separately in Viewpoint's Addons list. No additional shaderpack is required. The currently selected look, including Default, Somber, and Bath & Shower Effects variants, remains in use.

User-reported in-game checks confirmed the lighting replacement and manual editor, including the current-room list and distance display. Broader multiplayer and performance checks remain pending.

The addon changes Viewpoint's internal source registry, not `IsoCell.roomLights`, map objects, saves, electrical rules, or server state. Only clients that want these visuals need it. Server restrictions on client mods still apply. Multiplayer visual verification is pending.

## Manual editor

**F7** opens and closes the editor in a Viewpoint 3D camera. Aim at a fixture before opening to select it, or choose one from the current-room list. It shows only loaded fixtures in the room recognized by the game, including hidden fixtures. Horizontal distance is shown in squares, ordered nearest first. **Refresh room** reloads the list for the room you are currently in and recalculates distances; it keeps the selected fixture when it is still in that room. Outside a room the list is empty.

- Move a fixture west/east, north/south, or up/down using 0.05, 0.25, 0.5, or 1-square steps. **Snap to grid** aligns its position to the chosen step.
- Rotate in 15-degree increments, choose another CeilingLights model, and adjust per-light intensity and reach.
- **Duplicate** creates a new light at the same position; move the copy using the position controls.
- **Add model at aimed ceiling** places the selected model at the horizontal position aimed at when the editor opened. Close F7, aim again, and reopen to choose another point. If no hit exists, the player's position is used. New lights start at the model's normal ceiling height; adjust height manually.
- **Hide/Show** controls an automatic fixture. **Restore automatic position** removes its override. Added fixtures have a **Delete** action.
- Model geometry is rebuilt in the destination chunk, and its light follows the same placement. Updates are asynchronous; while the panel is open, lighting refreshes periodically to allow the preview to settle.

Changes save automatically after each successful action in `viewpoint-ceiling-lights.properties` inside the current game's save directory. SP worlds and local MP save directories have separate layouts. A failed file write preserves the last live configuration. The preceding file is retained as `.bak`.

**Export layout** writes `Zomboid/Lua/ViewpointCeilingLightSources-export.properties`. Give that file to a friend using the same map coordinates; they can put it at the same path and choose **Import layout**. Imports merge by stable identifier, so importing the same file twice does not duplicate lights. Unknown formats and invalid entries reject the complete import.

Manual lights remain client-only and use the existing room's power state. Movement stays within the same loaded room and floor, at most 32 squares from the original position. To add light in another room, go there and add a fixture to its own lighting circuit. Existing source budgets still apply. Up to 512 edited/added records are stored per world.

The lighting integration and editor have successful user-reported in-game checks, supplemented by automated storage, geometry, cursor, room filtering and Lua lifecycle checks.

## Local settings

On its first startup, the mod creates `Zomboid/Lua/ViewpointCeilingLightSources.properties`:

```properties
enabled=true
intensity=1.0
reach=6
diagnostics=false
```

- `enabled`: turn the replacement on or off.
- `intensity`: 0.25-2.0, default 1.0; multiplies the original room-light colour/strength.
- `reach`: 3-12 squares, default 6.
- `diagnostics`: aggregated statistics every ten seconds, off by default.

The file is checked every two seconds. Changes take effect on the next native lighting update; they can be delayed while paused. Invalid values keep the previous valid configuration and produce one error per changed file. There is no Mod Options page in this initial build.

Look in `Zomboid/console.txt` for `[CeilingLightSources]`. Startup reports compatibility and the settings path. The first completed replacement reports `first room replaced; fixtures=...`. Diagnostics report captured squares, cached rooms, virtual sources, and rooms replaced during the preceding registry update.

## Behaviour and limits

- Positions come from successful CeilingLights model placements, including fractional tile coordinates, model selection, stairs exclusions, and ceiling-height adjustments.
- A room is replaced only after all its loaded room-light rectangles have been meshed and the required fixture geometry is ready. Explicitly hiding every known fixture suppresses its rendered room sources. Incomplete/unloaded rooms retain their original light.
- Virtual lights are prepared through Viewpoint's existing light worker. While their calculations are pending, they are excluded from direct rendering and the original room light remains active. Empty results do not suppress the original source.
- The room switch powers the replacement lights. Inactive rooms publish no virtual sources. No other lamps, torches, headlights, or daylight sources are replaced.
- At most 64 extra active sources are prepared per native update. A room that would exceed this safety limit retains its original lighting. Viewpoint still selects the lights and shadows it draws using its own distance and shaderpack limits; this mod does not raise those budgets.
- Rooms exceeding 4,096 room-light rectangle tiles retain the original path. Captured-square storage is bounded and unloaded entries are pruned.
- This is an internal integration. A SHA-256 compatibility check covers the renderer/placement classes it depends on. Changed classes disable the integration with an explicit log instead of guessing their layout.

The native ambient grid and direct-source handover are asynchronous. The direct central source and replacement sources are selected together at a native update, but a short ambient-light transition can remain while the worker rebuilds its grid. Native tile-level occlusion is retained; precise shadows, glass, open doors, multistorey rooms, and inclined roofs still require in-game visual checks.

## Build and checks

Run `tools/Build.ps1` with JDK 25; it compiles with `--release 17`. Parameters allow overriding the game directory and dependency JARs. The package contains only this mod's Java classes and small metadata resources.

`tools/Test.ps1` builds the mod, exercises its state machine against the actual installed Viewpoint registry with isolated game fixtures, and uses the installed ZombieBuddy transformer plus ByteBuddy to verify that all eight patches can be woven into their actual target methods. Test fixtures and checking tools are excluded from the shipped JARs. The cursor helper is compiled separately against the API and never bundles Viewpoint classes.

Automated checks cover partial capture, native-work readiness, empty results, stable identities, frames without a registry update, switch state, model removal, unload fallback, configuration validation, and world cleanup. These checks do not establish visual alignment, multiplayer correctness, or GPU cost.

## In-game acceptance

Use the same large room with the addon enabled and disabled. Check that the bright points move from the room centre to the physical fixtures, then test power switches, generators, doors, walls, upper floors, stairs, and return visits after leaving the area. Repeat in multiplayer. Compare the Viewpoint Performance page and frame times with the same shader settings. These in-game comparisons remain pending; the game was not restarted or controlled during installation.

## Upgrading from the previous Mod ID

The former Mod ID was `ViewpointCeilingLightSources`. Enable `CeilingLightsEditor` in the Mods selection for your save after updating. Existing lighting layouts, client settings and exported files keep their original filenames and do not need to be recreated.
