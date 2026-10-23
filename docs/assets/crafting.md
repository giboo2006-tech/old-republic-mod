# Crafting assets: the Engineering Bench, materials, consumables, tools and grenades (Milestone 4)

Everything below is original art and sound made by our own scripts. Nothing is copied from, traced from or made to
imitate a Star Wars game or film prop. The textures are **placeholders** from `tools/assets/gen_textures_crafting.py`
(PIL, deterministic, seeded per texture, runs from anywhere). The same script writes the JSON files, and those are final
unless noted. The sounds are designed stand-ins from `tools/assets/sounds/crafting.py`, rendered by
`python3 tools/assets/gen_sounds.py --m4 --feature crafting`. Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (the lead's files) do not change.

The saberstaff, training saber, staff grip and lenses are drawn by the saber generator; see `docs/assets/saber.md`,
"Milestone 4 additions". The bench screen has no background texture: `EngineeringBenchScreen` draws its panels in code
with the shared UI kit sprites (`Ui.SLOT`, `Ui.SOCKET`; see `docs/assets/ui.md`).

The palette is a worn workshop that matches the lightsaber workbench: brushed and dark steel, iron, brass pulls, olive
and canvas for field kit, a teal medical accent, a pale blue kolto, a red combat stim, a violet focus stim, a hot orange
cutter tip, blueprint blue, and a green scanner trace.

Paths are under `src/main/resources/assets/oldrepublic/`.

## Textures

### The Engineering Bench (block, 16x16 faces)

| Path (`textures/block/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `engineering_bench_top.png` | The work plate | placeholder generated | A heavy, scarred steel plate with a darker rim, a grid of bolt holes and a scorched patch where the welding arm works. It should read as a surface people hammer on, not a table. |
| `engineering_bench_side.png` | Sides and back of the plate and cabinet | placeholder generated | Dark steel panel with an iron frame and one seam across the middle. Plain, so the front and top carry the detail. |
| `engineering_bench_front.png` | The drawer cabinet's front and the plate's front edge | placeholder generated | Two drawers with brass pulls and a yellow-and-black warning stripe along the top edge. The stripe must stay generic (no logo or lettering). |
| `engineering_bench_metal.png` | Legs, brace, the welding arm, the vice, undersides | placeholder generated | Dark iron with a faint diagonal brush. It tiles on small boxes, so keep it low-contrast. |
| `engineering_bench_arc.png` | The welding emitter's tip (emissive, light 12) | placeholder generated | A white-hot centre fading to pale blue at the edge. It is drawn full-bright on a tiny box, so only the centre 4x4 really shows. |

The block model (`models/block/engineering_bench.json`) has a full-width plate on top (y 12-16), a drawer cabinet at the
front left, two legs and a brace on the open right side, and a welding arm on a post at the back left. The arm's
emitter tip glows (`light_emission` 12) over the plate, and a small vice sits on the plate. `blockstates/engineering_bench.json`
rotates the model by `facing` (north is the model; east, south and west turn it 90, 180 and 270 degrees). The item
(`items/engineering_bench.json`) uses the block model. **Final model brief:** keep the silhouette (plate, cabinet, open
leg side, arm over the plate) within one block, and keep the arm's tip as the only emissive part. The model's parent is
`minecraft:block/block`, so it uses the vanilla block display transforms.

### Items (16x16 flat icons, `minecraft:item/generated`)

| Path (`textures/item/`) | Item | Status | Brief for the final artist |
|---|---|---|---|
| `alloy_plate.png` | `alloy_plate`, the refined crafting material | placeholder generated | Two rolled plates stacked at an angle, blue-grey with rivets at the corners. A step up from `durasteel_plate`: cleaner, cooler, more finished. |
| `masterwork_fragment.png` | `masterwork_fragment`, the rare salvage return | placeholder generated | A shard of gilded alloy with a soft violet glow at its core. It should look valuable but small, never like a crystal or a holocron. |
| `schematic.png` | `schematic`, the item that teaches a schematic | placeholder generated | A rolled blueprint sheet: blue paper, white technical lines, a brass clip. One icon for every schematic (the tooltip names which). |
| `medpac.png` | `medpac` | placeholder generated | A field medpac: a tan canvas pouch with a teal panel and a white plus. Use a plus, not a red cross (the red cross is a protected emblem). |
| `kolto_injector.png` | `kolto_injector` | placeholder generated | A pneumatic injector on the diagonal: a steel body, a window of pale blue liquid, a plunger and a short needle. |
| `combat_stim.png` | `combat_stim` | placeholder generated | The same injector with red liquid. The three injectors must be told apart by colour at 16 px. |
| `focus_stim.png` | `focus_stim` | placeholder generated | The same injector with violet liquid. |
| `fusion_cutter.png` | `fusion_cutter` | placeholder generated | A handheld cutting tool: a rubber grip, a boxy body and a glowing orange tip. Industrial, not a weapon. |
| `scanner.png` | `scanner` | placeholder generated | A handheld scanner: a dark body, a small screen with a green trace, and a stub antenna. |
| `repair_kit.png` | `repair_kit` | placeholder generated | A small tool case with a brass latch and a wrench strapped to its lid. |
| `frag_grenade.png` | `frag_grenade` (new in M4) | placeholder generated | A round grenade with an orange band, a cap and a pin ring. It must read as a fragmentation grenade next to the stun grenade, mainly by its colour. |
| `stun_grenade.png` | `stun_grenade` | placeholder generated | The same body with a pale blue band and small spark marks. |

Each item has `items/<id>.json` (a plain `minecraft:model`) and `models/item/<id>.json` (`item/generated` with
`layer0`), written by the generator.

## Sounds (`tools/assets/sounds/crafting.py`)

All are synthesised with numpy, mono and deterministic. The stream flag, volumes and categories are Phase 0's
`sounds.json` (`bench.weld` plays at volume 0.7). Every sound is a **placeholder**.

| Event | File (`sounds/`) | Placeholder | Brief for the final sound designer |
|---|---|---|---|
| `bench.mod_fit` | `bench/mod_fit` | A sliding scrape, a firm double click, a short metal ring (0.45 s plus the tail) | A mod seating into a weapon or armour piece: precise and mechanical, with the satisfaction of a part that fits. Short, since players fit several in a row. |
| `bench.mod_remove` | `bench/mod_remove` | A lower latch click, then the part sliding free | The reverse of `mod_fit`: a release, a little looser and lower. It must not sound like something breaking. |
| `bench.weld` | `bench/weld` | A seamless 2 s loop: arc crackle band-passed around 3.8 kHz, a hiss, a 120 Hz mains buzz, a slow flutter | The craft's working loop, which plays for the length of a craft (2 to 6 s). An arc welder at a distance: crackle and hiss over a low buzz. It must loop cleanly at any length, sit under dialogue and not tire after many crafts. The code starts and stops it, and a watchdog stops it if the craft is interrupted. |
| `bench.done` | `bench/done` | The arc cuts out with a hiss, two clicks, then a two-note ready chime (A5, E6) | The item is ready: a clean stop and a small "done" cue, like a tool powering down plus a soft notification. |
| `bench.masterwork` | `bench/masterwork` | A bright rising triad of bells (E5, G#5, B5, E6) over a soft shimmer, 2.2 s | Rare and rewarding (a few crafts in a hundred at level 1, more at higher levels, or the pity guarantee). Warmer and grander than `bench.done` but still short; it plays with a toast. Never a fanfare or brass. |
| `bench.salvage` | `bench/salvage` | A ratchet, then a clatter of plates and screws on the bench | Taking an item apart: a quick ratchet and parts settling on a metal surface. |
| `medpac.use` | `medpac/use` | A seal tearing, a cold spray, a soft C6 confirm tone | Applying a field dressing: tearing a sealed pack and a short cold spray. The confirm tone is optional; it must not sound like a UI beep. |
| `kolto.inject` | `kolto/inject` | A pneumatic hiss and a few rising liquid bubbles | A quick injection of a healing fluid: a pneumatic press with a liquid, bubbling quality that sets it apart from the stims. |
| `stim.inject` | `stim/inject` | A sharp pneumatic snap, a short hiss, two quick heartbeats | A combat stimulant: harder and faster than kolto, with a sense of the heart picking up. |
| `stim.crash` | `stim/crash` | A falling tone and three slowing, softer heartbeats, 1.6 s | The stim wearing off (the slow phase). It plays at the player only; it should read as fatigue, not damage. |
| `scanner.pulse` | `scanner/pulse` | A rising sweep (600 to 2400 Hz) that rings outward and echoes, 1.4 s | A sensor sweep spreading through the area: a rising tone and a wide tail. The tail sets the sense of range. |
| `scanner.ping` | `scanner/ping` | A short double beep (A6, C7) | A find is marked: short, clear, positional. It may play once per pulse, so it must not be shrill. |
| `grenade.throw` | `grenade/throw` | The pin's click and a short whoosh | The pin pulled and the grenade thrown. The vanilla bounce and explosion cover the rest of the frag grenade. |
| `grenade.stun` | `grenade/stun` | A sharp crack, a low thump, a spreading electric crackle and a faint 3.5 kHz ring, 1.4 s | A non-lethal electrical burst: a snap of discharge that spreads, with no fire or debris. Keep the high ring faint; it must not reproduce tinnitus at full volume. |

## Data (final)

| Path (under `src/main/resources/data/oldrepublic/`) | What it is |
|---|---|
| `schematic/*.json` (65) | The schematics: 16 Armstech, 25 Armourcraft, 7 Medtech, 8 Artifice and 9 common ones. Four of them are Masterwork-only trainer recipes (`*_masterwork`). By how they are learned: 49 `auto`, 12 `item` and 4 `trainer`. The format and the rules are in `docs/design/crafting.md`, section 4. They were generated once by a scratch script that is not in the repository, so edit the JSON files directly. |
| `recipe/engineering_bench.json` | A shaped vanilla recipe for the bench: `DDD / ICI / R` with durasteel plates, iron ingots, a crafting table and redstone |
| `loot_table/blocks/engineering_bench.json` | The bench drops itself if it survives the explosion. It needs a pickaxe once the lead adds it to `mineable/pickaxe`. |

Lang: `src/main/lang-parts/crafting.json` holds 154 keys: items, the block, disciplines, qualities, schematics, the bench
screen, the Crafting journal tab, refusals, toasts and the commands.
