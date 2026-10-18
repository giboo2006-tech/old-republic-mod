# Ships and travel assets: the courier shuttle, ship items, landing beacon, pad terminal (Milestone 4)

Everything below is original art and sound made by our own scripts. Nothing is copied from, traced from or made to
imitate a ship, prop or sound from a Star Wars game or film. The courier shuttle is a generic blunt-nosed courier of our
own design. The textures are **placeholders** from `tools/assets/gen_textures_ship.py` (PIL, deterministic, seeded per
texture, runs from anywhere). The same script writes the item, block and particle JSON, and those are final unless
noted. The templates come from `tools/assets/gen_structures_ship_m4.py` (deterministic, byte-identical on every run).
The sounds are designed stand-ins from `tools/assets/sounds/ship.py`, rendered by
`python3 tools/assets/gen_sounds.py --m4 --feature ship`. Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (the lead's files) do not change. The design is in `docs/design/travel.md`.

The hyperspace overlay, the flight HUD, the toasts, the ship screen and the star chart's points and lanes are drawn in
code (`client/travel`), with the shared UI kit's sprites (`Ui.CARD`, `Ui.SOCKET`, `Ui.SLOT`; see `docs/assets/ui.md`).

Palette: off-white durasteel faded to bone, grey seams, a darker belly, smoked blue-black glass, canvas and timber for
the hulk's tarp and jacks. Paint per owner: **Watch green** (the *Patient Star*), **Imperial grey** (Sorn's written-off
courier), **Vasch's rust-red** (the Independent's spare courier). Lights are a warm white, a red and green pair on the
wing tips, and an amber fin beacon. The engines glow blue-white.

Paths are under `src/main/resources/assets/oldrepublic/`.

## Textures

### The courier shuttle (entity, 512x256, `CourierShuttleModel` box layout)

| Path (`textures/entity/ship/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `courier_shuttle_watch.png`, `courier_shuttle_imperial.png`, `courier_shuttle_vasch.png` | The hull in each paint, plus the tarp, timber jacks and upgrade parts | placeholder generated | Panel lines, rivets and service hatches over off-white plate; the paint as a faded band along the spine, the wings and the nacelle cowls, chipped at the edges; dark smoked canopy glass with a soft highlight; a darker belly with the ramp's outline. The hulk's tarp is patched canvas with rope ties, and the jacks are rough timber. Keep the layout or update `CourierShuttleModel` and `PARTS` in the script together. |
| `courier_shuttle_lights.png` | Running lights, an emissive layer (`eyes`) | placeholder generated | Only the lit spots are opaque: the wing-tip pair, the amber fin beacon, the nose lens, the canopy's faint glow, the ramp edge. Everything else is transparent. |
| `courier_shuttle_lights_damaged.png` | The same with the fin beacon dead | placeholder generated | As above with the beacon dark. It is used while the ship is `DISABLED`. |
| `courier_shuttle_engines.png` | Nozzle glow, an emissive layer | placeholder generated | Blue-white cores in the nozzle mouths, fading to the rim. The renderer tints it by engine power. |
| `courier_shuttle_scorch.png` | Soot and blast marks, translucent | placeholder generated | Streaks behind the nozzles, scorch around hatches and seams, and a few blast scars on the flanks. It fades in below hull 70 and is full at 0. |

**Model brief** (`CourierShuttleModel`, about 9 x 4 x 11 blocks, the renderer scales the model by 2):

- The body: a blunt nose with a raised canopy, a dorsal spine and a swept tail fin, and swept-down wings.
- Two engine nacelles at the tail, three landing struts, and a tail ramp hinged under the hull.
- On a hulk: a tarp over the spine and two timber jacks under the wings. They are pulled back when it stands up.
- Upgrades show: a belly cargo pod (cargo bay), spine tanks (fuel tank) and nose plating (reinforced plating).

The final model must keep the ramp at the tail, keep the struts' feet at y 0, and keep the nozzles where `TravelFx`
spawns exhaust (ship space ±2.6 right, 1.9 up, −5.6 forward).

The hull code (for example `AB-123`) is drawn in text on both sides of the nose. It is not in the texture.

### GUI and particles

| Path | What it is | Status | Brief |
|---|---|---|---|
| `textures/gui/destination_map.png` | The star chart backdrop, 256x128 | placeholder generated | Deep blue-black space with a faint dust band and sparse stars. No points, lanes or labels: the screen draws those. It must stay dark enough for dim text to read on it. |
| `textures/particle/engine_exhaust_{0..3}.png` | Exhaust puff frames, 8x8 | placeholder generated | A soft round puff, blue-white at birth and greyer with each frame. Tinted per spawn. |
| `textures/particle/spark_shower_{0,1}.png` | Spark frames, 8x8 | placeholder generated | A bright orange-white streak and a dimmer ember. |

### Items (16x16 flat icons)

| Path (`textures/item/`) | Brief |
|---|---|
| `power_core.png` | A squat drum with brass caps and a blue core window. |
| `hyperdrive_motivator.png` | A coil-wrapped cylinder lying diagonally, with a violet glow at its heart. |
| `nav_computer.png`, `nav_computer_loaded.png` | A handheld console. The screen is dark when blank. Loaded, it is lit with a green star plot. |
| `hyperfuel_cell.png` | A capped glass cell of glowing amber fuel. |
| `passenger_seat.png` | A padded crash couch with a harness. |
| `cargo_bay_extension.png` | A cargo pod with striped ends. |
| `reinforced_plating.png` | A thick bolted armour plate. |
| `fuel_tank_extension.png` | A pair of banded ochre tanks. |

All are **placeholders**. `items/nav_computer.json` switches to the loaded icon with
`minecraft:condition` / `minecraft:has_component` on `oldrepublic:nav_data`.

### Blocks (16x16 faces)

| Path (`textures/block/`) | Brief |
|---|---|
| `landing_beacon_base.png`, `landing_beacon_post.png`, `landing_beacon_post_front.png` | A squat steel post on a dark footed base. The front face carries an arrow pointing toward the pad. |
| `landing_beacon_lamp.png` (+ `.mcmeta`) | The lamp, 4 frames, `frametime` 15, interpolated: a **slow pulse**, never a strobe. |
| `pad_terminal_base.png`, `pad_terminal_body.png`, `pad_terminal_screen.png` | A console on a pedestal with a tilted screen showing a bay grid in green. |

**Flashing note.** A block texture animation cannot read the client's flashing setting, so the beacon lamp's
animation is a gentle interpolated pulse that is safe with flashing off. The bright blinks of set, refuse and recall
are the `landing_beacon` effect's particles, which do respect the setting (one steady glow instead).

Block models: `models/block/landing_beacon.json` (base, post, lamp; `facing` rotates it so the panel faces the pad)
and `models/block/pad_terminal.json` (pedestal, body, and the screen tilted back 22.5 degrees).

## JSON

- `items/<id>.json` and `models/item/<id>.json` for the eight ship items. `items/landing_beacon.json` and
  `items/pad_terminal.json` use the block models.
- `blockstates/landing_beacon.json` and `blockstates/pad_terminal.json` (by `facing`).
- `particles/engine_exhaust.json`, `particles/spark_shower.json`.
- `data/oldrepublic/loot_table/blocks/landing_beacon.json`: drops itself and survives explosions. The pad terminal has
  no loot table: it is unbreakable in survival.
- `data/oldrepublic/dialogue/pad_terminal.json`: no ship, hulk, recall / tow / restore menu, and the restore
  confirmation.

## Templates

| Template | Size | Anchors | What it is |
|---|---|---|---|
| `structure/ship/cradle.nbt` | 17 x 7 x 21, 1 buried | `HEART` (8, 1, 10), the hulk's feet | A packed-earth yard: lamp posts at both ends, a canvas lean-to over a crafting table, a chipped anvil and a grindstone, tool crates and barrels by the nose, timber cribbing by the wings. The ship's footprint (3..13, 1..5, 3..17) is kept clear and checked. The entrance and the ship's tail face local north. |
| `structure/ship/beacon_pad.nbt` | 11 x 3 x 11, 1 buried | `HEART` (5, 1, 5), where the ship lands | Only a ground layer: stone and andesite flags with gravel joints and a coarse-dirt margin. All natural ground (`#story_ground`, none in `#built_blocks`). Nothing above the ground is written, so it never cuts a tree. |

No floor circles, sigils or glowing runes in either template.

## Sounds

All are placeholders, synthesised with numpy (`tools/assets/sounds/ship.py`). Loops are seamless: every filter and
oscillator is circular.

| Event | File | Length | Intent |
|---|---|---|---|
| `ship.idle` | `ship/idle` | 3.0 s loop | A low engine hum, a soft wavering turbine whine and a rumble of air. |
| `ship.spool` | `ship/spool` | 2.8 s | The engines spool up: a whine climbing in pitch, a swelling rumble, latches clicking. |
| `ship.climb` | `ship/climb` | 4.1 s | A roar of thrust swelling and holding, with a slowly rising tone, then easing away. |
| `ship.descent` | `ship/descent` | 4.5 s | A repulsor whine falling in pitch, the rumble settling with it. |
| `ship.land` | `ship/land` | 2.3 s | Touchdown: a heavy thump, the struts' creak, a long pneumatic hiss. |
| `ship.ramp` | `ship/ramp` | 2.0 s | A hydraulic motor under load, then a clunk as the ramp seats. |
| `ship.jump_out` | `ship/jump_out` | 2.4 s | A pressure thump, then a tearing streak climbing to nothing. |
| `ship.jump_in` | `ship/jump_in` | 2.3 s | A streak falling out of the high register into a heavy thump. |
| `ship.hyperspace` | `ship/hyperspace` | 4.0 s loop | A deep drone beating slowly, an airy rush, a slow swell. |
| `ship.fit_part` | `ship/fit_part` | 1.2 s | A heavy clank, a latch click and a short rising hum (pitched per part). |
| `ship.weld` | `ship/weld` | 1.1 s | Arc crackle and hiss over a buzz, then the metal ticking as it cools. |
| `ship.power_up` | `ship/power_up` | 3.4 s | Relays clack, a tone rises through the systems and settles into the idle. |
| `ship.damaged` | `ship/damaged` | 2.0 s loop | A damaged engine: sparks, an irregular sputter, a failing buzz. |
| `beacon.set` | `beacon/set` | 0.8 s | A latch click and two rising beeps. |
| `beacon.refuse` | `beacon/refuse` | 0.7 s | Two low buzzing tones falling. |
| `beacon.recall` | `beacon/recall` | 2.9 s | Three ascending beeps, then a distant engine swell. |

## VFX (phase by phase)

All counts go through `Vfx` (the particle preset), shakes through the shake setting, and blinks and pulses through the
flashing setting. Nothing forms a shape on the ground: dust is blown outward in uneven puffs in random directions.

- **Idle** (`READY` on the ground): a lazy puff of exhaust now and then, the idle hum, lights blinking (steady with
  flashing off).
- **Spool**: the exhaust rate rises; dust gusts from under the ship; the struts fold and the ramp closes; the spool
  sound.
- **Climb**: full exhaust from both nozzles, the climb roar, a small shake for riders, the third-person camera.
- **Jump**: exhaust pulled into a streak ahead of the ship, the jump-out thump, a shake. Then the overlay.
- **Hyperspace** (riders): star streaks stretching into a blue-white tunnel with a slow pulse (no pulse with flashing
  off; fewer streaks on Reduced), a dark vignette, the HUD fading, the hyperspace hum. It is also drawn over the level
  loading screen.
- **Descent**: the arrival streak and thump, the descent whine, the struts down at 6 blocks, dust as the ground nears.
- **Settle**: the land thump and hiss, the ramp lowering, the exhaust dying away, the arrival banner.
- **Repairs**: sparks and a clank as each part seats; arc sparks and smoke for welds; at the stand-up, steam from the
  jacks, sparks, the power-up and the lights coming on.
- **Wear**: below `ship.damageFxHull`, one nacelle sparks and trails thin smoke. Below the jump minimum, the hull seam
  smokes, the damaged crackle loops, the nozzle glow stutters and the fin beacon is dead. Scorch marks deepen as the
  hull falls.
- **Beacon**: set (three green blinks and rising beeps), refuse (two red blinks and low tones), recall (five amber
  blinks and the recall chime). With flashing off, each is one steady glow.
