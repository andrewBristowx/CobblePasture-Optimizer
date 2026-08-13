# Changelog

## 0.1.0-alpha.5

- Fixes the fixed Pasture TextDisplay being rotated 180 degrees away from the readable side.
- Keeps the display fixed in world space, transparent, and without text shadow.
- Existing alpha.4 displays are corrected automatically on their next scheduled refresh; Pastures do not need to be replaced.

## 0.1.0-alpha.4

- Changes the floating Pasture display to a fixed world-space sign by default instead of rotating to follow the player's camera.
- Aligns the fixed display with the Pasture block's horizontal facing direction.
- Removes the dark background behind the text by default.
- Removes the TextDisplay shadow by default.
- Adds `facePlayer`, `showBackground`, and `textShadow` options to `config/cobblepastureoptimizer.json` so those effects can be re-enabled later if desired.
- Leaves the alpha.3 Pokemon grouping, hopper status, Cobbreeding status and cleanup behavior unchanged.

## 0.1.0-alpha.3

- Adds one lightweight vanilla `TextDisplay` hologram per non-empty Pasture instead of bringing physical Pokemon entities back.
- Groups stored Pokemon by species and shows their counts above the Pasture.
- Optional level ranges can be enabled in `config/cobblepastureoptimizer.json`.
- Shows whether a hopper is directly connected below the Pasture.
- When Cobbreeding 2.2.2 is installed, shows egg count and breeding state/progress without making Cobbreeding a hard dependency.
- Hologram range, height, update interval, line count and visible fields are configurable.
- Hologram text is only rewritten when its contents change and refreshes are staggered by Pasture position.
- Adds slow cleanup for orphaned holograms after their Pasture is broken.

## 0.1.0-alpha.2

- Sends virtual Pasture Loot drops directly into a hopper placed immediately below the Pasture.
- Preserves Cobblemon `ItemDropEntry` quantity and components by capturing only the ItemEntities created by the current drop call and inserting them before they can tick or move.
- Falls back to a normal world drop when there is no hopper or when the hopper cannot accept the complete stack.
- Verified compatibility design with Cobbreeding 2.2.2: breeding reads the stored tethered Pokemon rather than requiring physical PokemonEntity instances.
- Cobbreeding eggs remain in its Pasture inventory and can be extracted by vanilla hoppers when `allowHoppersToPullFromPastureBlock` is enabled (default in Cobbreeding 2.2.2).

## 0.1.0-alpha.1

- Initial experimental Fabric implementation.
- Virtualizes physically spawned Cobblemon pasture Pokemon while preserving pasture tethering.
- Replays Cobblemon Pasture Loot 1.0.5 drop checks from stored Pokemon data.
- Uses Pasture Loot's already-loaded probability, blacklist, and legacy quantity configuration.
- Moves virtual drop spawn position to the Pasture block.
