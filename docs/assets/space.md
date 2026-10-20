# Space assets, Milestone 5: orbits, starfighters, the gunship and the Silent Spindle (W2 space)

Every asset here is an **original placeholder** made by a script in this repository; no Star Wars game or film asset is
used or imitated, nothing is traced or sampled, and no ship design from any film or game is copied: the fighters, the
gunship and the station are this mod's own. Textures and item JSON: `tools/assets/gen_textures_space.py` (PIL and
numpy, seeded, deterministic: a run rewrites byte-identical files). The station template and its sidecar:
`tools/assets/gen_structures_space.py` (deterministic NBT). Sounds: `tools/assets/sounds/space.py`, rendered by
`python3 tools/assets/gen_sounds.py --m5 --feature space` (numpy synthesis: oscillators, filtered noise, sweeps,
partials, thumps, convolution reverb; every loop built seamless). The space art rule holds throughout: **no dust in
orbit** (sparks, plating fragments, engine light, smoke that thins fast and bent space only), and, as everywhere in the
mod, no glowing runes, sigils, halos, rings or ground circles. Design and use: `docs/design/space.md`.

## Textures (under `src/main/resources/assets/oldrepublic/textures/`)

The entity textures follow their models' box UV layouts (vanilla cube unwrapping: top and bottom across the top row,
left, rear, right and front below), mirrored in the generator's box tables, which refuse overlaps. A replacement must
keep each texture's size and layout, or change the model's `texOffs` with it. Each `_glow` texture has the same layout,
transparent except what glows (drawn full-bright, additive).

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `environment/space/planet_home.png` | The home world from orbit, 512x256 equirectangular map (longitude across, latitude down), seamless across the date line; sampled per vertex by the planet impostor | placeholder generated | A temperate living world: deep blue oceans, continents of forest green, steppe and tan desert bands, grey mountains, white ice caps and swirling white weather fronts. Must read as Earth-like but not Earth (no recognisable coastlines). Detail survives being sampled at about 64x32 points. |
| `environment/space/planet_selvaren.png` | Selvaren, same format | placeholder generated | A forest world under mist: rich greens in many tones, silver lakes catching the light, pale cloud banks drifting low. Softer and more veiled than the home world. |
| `environment/space/planet_varkhast.png` | Varkhast, same format | placeholder generated | An industrial ash world: rust plains, black basalt, thin glowing orange fissures, grey ash hazing everything. Hostile and scorched. |
| `environment/space/planet_ghessar.png` | Ghessar, same format | placeholder generated | A wasteland: amber dune seas with long ripple bands, white salt pans, dark red canyon chains, a little dust in the air. Bright, dry, harsh. |
| `environment/space/planet_tyrvann.png` | Tyrvann, same format | placeholder generated | A storm jungle seen through its weather: dark greens and black mire under heavy grey storm bands that cover most of the disc; the ground shows through the gaps. (The lightning is drawn by code.) |
| `environment/space/nebula.png` | The nebula band across the stars, 512x64, colour with alpha, wrapping along x | placeholder generated | A faint band of violet and blue gas with darker dust lanes, alpha fading to nothing at both long edges. Subtle: it sits behind the stars and must never compete with the planet. |
| `entity/space/hymn_interceptor.png`, `_glow.png` | Hymn interceptor, 128x64 (`StarfighterModel`); glow: the engine's mouth, the nose tip and the blades' leading edges | placeholder generated | Ulvaine's blade-winged fighter: black lacquer with a soft sheen, thin violet trim lines and dashes along the hull, the blade wings edged in violet; the engine burns violet-white and the blades' edges glow faintly, brightest at the tips (their "singing" edges). Elegant, cold, dangerous. Not any franchise fighter. |
| `entity/space/kajidic_raider.png`, `_glow.png` | Kajidic raider, 128x64; glow: the twin engines' mouths | placeholder generated | A cartel heavy fighter: gunmetal panels with ochre bands, welded patches of other metals with rivets, rust and soot streaks, hazard stripes on the rack, an amber canopy; a hot orange engine glow. Squat, patched, much-repaired. |
| `entity/space/hask_fighter.png`, `_glow.png` | Hask fighter, 128x64; glow: the engine's mouth | placeholder generated | Clan Hask's fighter in clan colours: clan iron (76, 82, 92) plates, an ember-red (194, 72, 40) stripe and bone (214, 204, 180) markings; a warm engine glow. Hand-built, proud, worn. Colours shared with W8's clan armour. |
| `entity/space/blockade_gunship.png` | Blockade gunship, 256x256 (`GunshipModel`: the hull in two halves, keel, spine, engines, bridge, four turrets, the bay doors, the generator) | placeholder generated | A slab-sided warship 24 blocks long: dark plated hull with big panels, a violet band high on each side, vent grilles, light housings; the two halves' inner faces (seen only after it breaks) charred and torn with a few red-hot edges. Heavy and oppressive, Ulvaine's colours. |
| `entity/space/blockade_gunship_glow.png` | Its glow layer | placeholder generated | The engines' mouths, the running lights, the prow's sensor slit and the bridge windows: violet-white and cold. |
| `entity/space/blockade_gunship_core.png` | The shield generator's core and emitter (pulsed by the renderer; steady with flashing off) | placeholder generated | A caged core of violet light inside an emitter on the spine; the light is inside the housing, never a ring or halo around it. |
| `entity/space/ship_missile.png` | Concussion missile, 32x32 (`ShipMissileRenderer.MissileModel`) | placeholder generated | A dark finned body with a pale nose cone and a yellow warning band; reads at 2 blocks long in flight. |
| `entity/space/nav_buoy.png` | Nav buoy, 64x32 (`NavBuoyRenderer.BuoyModel`: base, mast, vanes, collar, lamp) | placeholder generated | A small drifting marker: a squat metal base with radial ribs, a mast with two sensor vanes and a collar, utilitarian and slightly battered. |
| `entity/space/nav_buoy_lamp.png` | The lamp's glass, white (tinted amber, green once reached) | placeholder generated | The lamp's lens alone in white so code can tint it; a faint inner structure (a filament or facets). |
| `particle/hull_debris_0..3.png` | `oldrepublic:hull_debris`, 8x8, light grey (tinted by the particle: hot, then dark metal) | placeholder generated | Four jagged plating fragments of different shapes, a darker edge and a seam or rivet on each; must read as metal shards tumbling, never as rock or dust. |
| `item/concussion_missile.png` | Concussion missile item, 16x16 | placeholder generated | A missile lying corner to corner: white nose, yellow band, grey body, dark fins. |
| `item/sealed_cargo.png` | Sealed cargo item, 16x16 | placeholder generated | A small sealed crate: blue-grey plating, a sealing band and a lit amber lock. Contraband, not treasure. |
| `item/hymn_interceptor_spawn_egg.png`, `kajidic_raider_spawn_egg.png`, `hask_fighter_spawn_egg.png` | Spawn eggs (testing aids), 16x16 | placeholder generated | Egg shapes in each fighter's colours: black lacquer with violet spots; gunmetal with ochre; clan iron with ember. |

## Models, items and particles

| Path | What it is | Status |
|---|---|---|
| `items/{concussion_missile,sealed_cargo,hymn_interceptor_spawn_egg,kajidic_raider_spawn_egg,hask_fighter_spawn_egg}.json` | item model definitions | final |
| `models/item/<same>.json` | `item/generated` with the icon | final |
| `particles/hull_debris.json` | the four sprites | final |
| `client/space/StarfighterModel` (code) | the three fighters' geometry: Hymn interceptor (needle nose, ringed engine, swept blade wings with tall tips, dorsal fin, gun rails), kajidic raider (boxy hull, stub wings, wingtip gun pods, twin engines, belly rack, welded patch), Hask fighter (lean hull, forward-swept wings with down-turned tips, tall fin, one engine, two root cannons); an eighth of a block per unit | placeholder generated |
| `client/space/GunshipModel` (code) | the gunship: blunt prow, keel, dorsal spine, stern engine bank, raised bridge, four twin-barrelled turrets, belly bay doors, the generator part; built in two halves for the break-up; a quarter of a block per unit | placeholder generated |
| `ShipMissileRenderer.MissileModel`, `NavBuoyRenderer.BuoyModel` (code) | the missile and the buoy | placeholder generated |

Brief for a final modeller: keep the boxes' bounds (the fighters fit their entity boxes; the gunship its 24 x 9 x 7 hull,
the turrets at `BlockadeGunship.TURRETS` and the generator at `GENERATOR_SEAT`, where the server fires and places them),
keep the gunship splittable into two halves at its middle, and keep the parts the renderers move (the bay doors, the
generator, the buoy's lamp). Silhouettes must stay readable at 100 blocks: the Hymn's blade wings, the raider's bulk,
the clan fighter's forward sweep.

## Structure template (under `src/main/resources/data/oldrepublic/`)

| Path | Size | Status | Brief |
|---|---|---|---|
| `structure/station/spindle.nbt` | 64x40x48, nothing buried; unset cells are structure void | placeholder generated, functional (anchors reachable; the deck clear for a courier) | The Silent Spindle, an old Republic listening station gone dark: an octagonal spine of Republic panels and wreck plating split by bulkheads into the reactor room (a dead core in an iron cage, half its bulbs lit, conduits overhead, warning paint), the hydroponics bay gone to black vines (with an open floor hatch over the void), the ring junction with the vault behind bars, the central hall with the airlock corridor south to the landing deck, and the command deck (consoles, the log terminal under the viewport, the listening dish on the roof); outside, the listening ring on four chain spokes and the landing deck (23 x 21, guide chevrons and a square, railings with gaps). Abandoned, cold, quiet, a little eerie; no floor circles, sigils or glowing runes. A builder must keep the size, the deck's clear area and the sidecar anchors. |
| `site_anchors/station/spindle.json` | `SPINDLE_DECK` (45, 17, 36), `SPINDLE_POST_1`–`4`, `SPINDLE_LOG` (58, 17, 16, an observer), `SPINDLE_VAULT` (29, 17, 11, a chest with no loot table) | final (positions follow the template) | |

## World data, loot and the rest (final unless noted)

- `worldgen/biome/orbit_field.json` (black sky and fog, no precipitation or spawns, `ambient.space`, the asteroid feature),
  `worldgen/configured_feature/asteroid.json`, `worldgen/placed_feature/asteroid.json` (tuning may change). The five
  orbit dimensions, the `orbit` dimension type, `orbit_void` and the `#oldrepublic:orbit` timeline tag are the lead's
  Phase 0 files.
- `loot_table/gameplay/space_bounty.json` (2 sunstone or 2 stormglass shards; a hymnstone shard 1 in 20) and
  `loot_table/chests/spindle_vault.json` (2–3 alloy plates, a power core, a masterwork fragment, a second 1 in 4);
  tuning may change.
- `damage_type/ship_weapon.json`, `tags/entity_type/ship_targets.json`.
- Lang: `src/main/lang-parts/space.json` (entities, items, orbits, death messages, the combat HUD, 30 comm lines in five
  voices, 8 ace names, the Spindle's lines). Comm lines are short radio chatter, original, no franchise catchphrases.

## Sounds (under `src/main/resources/assets/oldrepublic/sounds/`)

All **placeholder generated** by `tools/assets/sounds/space.py`; ids, subtitles, categories and loop and stream flags
are in `tools/assets/sounds/events_m5.json` (Phase 0), and the file names stay when final audio replaces them. Space is
not silent (the films' convention): weapons, hits and explosions are heard in orbit for readability. Loops must stay
seamless in any replacement; the ambience and the music are streamed.

| File | Id | Length | What it is now | Brief for the final sound designer |
|---|---|---|---|---|
| `space/cannon_fire1.ogg`, `cannon_fire2.ogg` | `space.cannon.fire` | 0.6 s each | A saw-and-sine tone diving fast, a metallic comb twang, a recoil thump, a muzzle crack | The ship's twin cannons, four shots a second for long bursts: heavy and punchy with a springy metallic tail, never shrill; two variants that alternate naturally. |
| `space/turret_fire.ogg` | `space.turret.fire` | 0.56 s | A lighter double zap | The dorsal turret: lighter and quicker than the cannons, clearly the same family. |
| `space/cannon_overheat.ogg` | `space.cannon.overheat` | 1.7 s | A coolant hiss, two falling warning beeps, hot metal ticking | The guns lock from heat: a vent of coolant and a short falling alert the pilot learns at once. |
| `space/fighter_fire1.ogg`, `fighter_fire2.ogg` | `space.fighter.fire` | 1.0 s each | A thinner laser with more room around it | Enemy fighters' guns heard across the dark: thinner and more distant than the player's, still readable as "incoming"; the gunship's turrets use it pitched down. |
| `space/missile_lock_tone.ogg` | `space.missile.lock_tone` | 2 s loop | A steady train of short seeker beeps | The seeker hunting: a beep train the client pitches up as the lock builds and holds high once locked. Seamless. |
| `space/missile_locked.ogg` | `space.missile.locked` | 0.5 s | A rising two-tone chirp ringing down | Lock confirmed: clear and satisfying, not shrill. |
| `space/missile_launch.ogg` | `space.missile.launch` | 1.9 s | A clunk, the motor's crack and a roaring rush tearing away | The rack lets go and the motor lights; the raiders' launches use it pitched down. |
| `space/missile_warning.ogg` | `space.missile.warning` | 1 s loop | Two tones alternating fast, each beep falling away | Something has locked on to you: urgent, cutting through combat, but bearable for the seconds it lasts. Seamless. |
| `space/bolt_hit_shield.ogg` | `space.bolt.hit_shield` | 1.0 s | A crackling fizz ringing high over a soft low whump | A bolt splashing on a deflector: energy against energy, no metal. |
| `space/bolt_hit_hull.ogg` | `space.bolt.hit_hull` | 1.0 s | A clang, a spray of sparks, a dull thud through the frame | A bolt into plating: metal hurt; the pilot feels it. |
| `space/shield_down.ogg` | `space.shield.down` | 2.3 s | The shield's whine falling away through crackling discharges, a dead clunk | The shield fails: alarming, final. |
| `space/shield_recharge.ogg` | `space.shield.recharge` | 2.3 s | A rising, brightening whine with a shimmer and a soft chime | The shield comes back: relief. |
| `space/fighter_engine.ogg` | `space.fighter.engine` | 3 s loop | A rushing roar under a hard twin whine that wavers | Raiders and clan fighters passing: a roar and a whine with Doppler-friendly brightness. Seamless. Not any film's fighter sound. |
| `space/hymn_interceptor_engine.ogg` | `space.hymn_interceptor.engine` | 4 s loop | A vowel-shaped wail with slow vibrato, a fainter voice a fifth above, over a thin roar | The Hymn's singing engines: eerie, almost a voice, unmistakably theirs. Seamless; no words. |
| `space/gunship_hum.ogg` | `space.gunship.hum` | 4 s loop | Two low beating saws, a throb, machinery whining deep inside | The gunship's presence: a huge, slow, oppressive drone heard 96 blocks off. Seamless. |
| `space/fighter_explode.ogg` | `space.fighter.explode` | 2.8 s | A bright burst closing to a rumble, a deep thump, debris crackling | A fighter blown apart in the films' manner: bright, then a rolling rumble; the gunship's spine blasts use it pitched down. |
| `space/gunship_generator_down.ogg` | `space.gunship.generator_down` | 3.4 s | A long power-down whine through heavy arcing, a thump, a last zap | The generator dies: the battle's turning point; the player must hear that the gunship is open now. |
| `space/gunship_explode.ogg` | `space.gunship.explode` | 5.7 s | A huge blast, secondary explosions rolling through the hull, a long rumble | The gunship's end: the biggest sound in space, a victory. |
| `space/gunship_launch.ogg` | `space.gunship.launch` | 2.5 s | Hydraulic clunks, venting hiss, two engines spooling up | The bay opens and fighters drop out (played low as the doors part, then full for the launch). |
| `space/comm.ogg` | `space.comm` | 0.45 s | A click, a burst of static, the squelch closing | The comm opening before each line. Short; it plays often. |
| `space/scan.ogg` | `space.scan` | 2 s loop | Two rising chirps a second with a faint warble | A patrol's scanner sweeping the ship: nagging, tense. Seamless. |
| `space/buoy.ogg` | `space.buoy` | 2.7 s | A clear bell tone ringing out | The nav buoy's ping every two seconds: a beacon in the dark; played higher when reached. |
| `ambient/space.ogg` | `ambient.space` | 16 s loop | A low hull hum, the air recyclers' hiss, the frame creaking now and then, far electronic chirps | The void heard from the cockpit: quiet, enclosed, a little lonely; mixed low as a bed. Seamless. |
| `music/space.ogg` | `music.space` | 32 s loop | "Orbit": slow pads through four chords in D minor, a sub drone, a sparse bell figure like distant beacons | The orbits' music: vast, calm, lonely, with a hint of danger; no beat. Original composition, no resemblance to any franchise theme. |
