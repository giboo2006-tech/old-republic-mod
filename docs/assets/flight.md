# Flight and ships assets, Milestone 5: the interceptor, the weapon parts on both ships, the atmosphere streak and the flight sounds

Everything below is original art and sound made by our own scripts. Nothing is copied from, traced from or made to
imitate a ship, prop or sound from a Star Wars game or film. The interceptor is a generic wedge-hulled two-seater of our
own design (a narrow hull, a short nose, a bubble canopy, swept delta wings, twin engine pods and a raked fin), and the
weapon parts are plain boxes with original detailing.

Textures are **placeholders** from `python3 tools/assets/gen_textures_ship.py` (PIL, deterministic: re-running it leaves
every file byte-identical; it runs from anywhere). This is the Milestone 4 ship generator, extended. The same run still
writes the M4 courier, beacon and terminal art (`docs/assets/travel.md`), and it refuses overlapping or out-of-range boxes
in either ship's layout. The sounds are **designed stand-ins** from `tools/assets/sounds/flight.py`, rendered by
`python3 tools/assets/gen_sounds.py --m5 --feature flight`. Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (the lead's files) do not change.

The design is in `docs/design/flight.md`. The Milestone 4 ship assets are in `docs/assets/travel.md`. The orbits' and
space combat's assets are W2's (`docs/assets/space.md`). Paths are under `src/main/resources/assets/oldrepublic/`.

Some things are drawn in code and have no texture of their own:

- the helm HUD (`HelmHud`: speed and throttle line, boost meter, hull bar, altitude or orbit, one line of guidance);
- the atmosphere overlay (`AtmosphereOverlay`: screen-space streaks and a heat shimmer, then black going up or cloud
  coming down);
- the ship screen's Milestone 5 rows (`ShipScreen`).

All three use the shared UI kit's helpers and sprites (`docs/assets/ui.md`).

Palette: the M4 courier's palette, with its three paints per owner (**Watch green** 82,120,90, **Imperial grey**
96,102,112, **Vasch's rust-red** 146,72,50). The courier's palette is off-white durasteel faded to bone, grey seams, a
darker belly and smoked blue-black glass. The weapon parts are gunmetal with a stripe of the ship's paint. The shield
emitter is brass with a blue core. The interceptor's engines glow a cooler violet-blue than the courier's blue-white.

## Textures

### The interceptor (entity, 256 × 128, `InterceptorModel` box layout)

| Path (`textures/entity/ship/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `interceptor_watch.png`, `interceptor_imperial.png`, `interceptor_vasch.png` | The hull in each paint, with the weapon parts' block | placeholder generated | Off-white panels and seams over a narrow wedge hull. The paint shows as: a flash along the hull's sides, a broad stripe down the spine, a chevron sweeping forward along each cheek of the nose, the outer third of the wings (with a dark leading edge), and the whole fin. There is a dark belly, intake grilles ahead of the engine pods, and smoked canopy glass with one frame hoop. The gunmetal engine pods are heat-stained toward the nozzles. Lean and quick, not menacing: it is the player's ship. Keep the layout, or change `InterceptorModel` and `INTERCEPTOR_PARTS` in the script together. |
| `interceptor_lights.png` | Running lights, an emissive layer (`eyes`) | placeholder generated | Only the lit spots are opaque: the amber wing-tip lights, the white tail beacon on the fin, the cyan nose sensor, a faint cockpit glow, the shield emitter's blue core and the turret's amber sensor. Everything else is transparent. |
| `interceptor_lights_damaged.png` | The same with the fin beacon dead | placeholder generated | As above with the beacon dark. It is used while the ship is `DISABLED`. While a disabled ship tumbles at 0 hull, the renderer flickers the lights (steady with flashing off). |
| `interceptor_engines.png` | Nozzle glow, an emissive layer | placeholder generated | A white core in each nozzle's rear face, fading through violet-blue to the rim, with a faint glow on the nozzles' sides. The renderer tints it by engine power. |
| `interceptor_scorch.png` | Soot and blast marks, translucent | placeholder generated | Soft dark blotches on the hull's sides and spine, the nose, the wing tops, the engine pods and the fin. Like the courier's, it fades in below hull 70 and is full at 0. |

**Model brief** (`InterceptorModel`: model units are an eighth of a block, and the renderer scales the model by 2):

- The model spans about 5.5 × 2.5 × 6.4 blocks, with its struts' feet at y 0.
- The hull: a 14 × 9 × 34 body (model units) with a 10 × 7 × 10 nose and a small sensor tip.
- The bubble canopy is hinged at its rear edge. Its front lifts open (0.6 rad) on the ground.
- The swept delta wings run back to the tips with a slight droop.
- Twin engine pods run along the tail. The nozzles' rear faces carry the engine glow.
- A raked dorsal fin.
- Three landing struts (one under the nose, two under the pods). They fold in flight.

The final model must keep:

- the struts' feet at y 0;
- the nozzles where the effects spawn exhaust (`Interceptor.NOZZLES`, ship space ±0.85 right, 0.9 up, −2.8 forward);
- the weapons at the class's points, which W2's space combat fires from: cannons ±1.65, 0.7, 1.4; the turret at 0, 1.75,
  −1.3; the rack at 0, 0.375, 2.1.

The hull code (for example `AB-123`) is drawn in text on both sides of the hull, ahead of the wings. It is not in the
texture.

### The weapon parts (both ships)

`ShipWeaponParts` draws the Milestone 5 parts on both models, each only with its upgrade:

- twin cannons (a pod and a barrel each);
- the gunner turret (a ring, a head with a sight slit, twin guns; the ring turns and the guns lift where the gunner
  looks);
- the missile rack (two tube mouths and hazard edges);
- the shield emitter (a brass disc with a blue core).

Their boxes share one block of each ship's texture, 132 × 12 pixels: at (210, 112) on the courier's 512 × 256 sheets, and
at (0, 64) on the interceptor's. `weapon_boxes` in the script mirrors `ShipWeaponParts#define`. The THRUSTERS upgrade has
no part on the model (cut 2 in the design doc).

| Path (`textures/entity/ship/`) | What changed | Status | Brief for the final artist |
|---|---|---|---|
| `courier_shuttle_{watch,imperial,vasch}.png` | Regenerated with the weapon parts' block | placeholder generated | The rest of the sheet is unchanged from M4. The parts: gunmetal plates with the paint as a stripe along the cannon pods, and the turret head banded in the paint. They should look bolted on by a yard, not built in. |
| `courier_shuttle_lights.png`, `courier_shuttle_lights_damaged.png` | Regenerated with the emitter's core and the turret's sensor lit | placeholder generated | As in M4, plus the two lit spots of the parts. |

`courier_shuttle_engines.png` and `courier_shuttle_scorch.png` were regenerated byte-identical.

### Particles

| Path | What it is | Status | Brief |
|---|---|---|---|
| `textures/particle/atmosphere_streak_{0..2}.png` | Streak frames, 8 × 8, white (tinted per spawn) | placeholder generated | A thin vertical streak of hot air, brightest at its middle, in three lengths. `FlightFx` tints it white-hot (warm) when the ship comes down and blue-white when it goes up. It is translucent, full-bright, lives 6 to 10 ticks and fades out. The streaks rush past the hull against the ship's way for 15 ticks after each `atmosphere` event. |

## JSON

- `particles/atmosphere_streak.json`: the three frames. The type is `TravelParticles.ATMOSPHERE_STREAK`, and `FlightFx`
  registers its provider.
- No new item, block, model or equipment JSON: the interceptor's model is built in code (`InterceptorModel.LAYER`) and it
  has no item.
- Lang: `src/main/lang-parts/travel.json` (+97 keys: the Helm key, the HUD, the refusals, the landing reasons, the
  screen's M5 rows, the shipwright's messages, the upgrades, the interceptor). Final.

## Sounds

All are synthesised with the `gen_sounds.py` helpers (oscillators, filtered noise, thumps, clicks, metal rings and a
simple reverb). Nothing is sampled. They are mono OGG at 44.1 kHz.

- **The loops are seamless.** Each turbine partial is its own oscillator with a whole number of cycles per loop, and
  every filter, modulation and reverb is circular.
- **Volumes** (events_m5.json): the two engines 0.8, the alarm 0.6, the rest 1.0. The client's mod sound volume applies
  on top of that.
- **The engine loops** follow the ship flown by hand. Their pitch runs from 0.75 at a hover to 1.35 at full speed (+0.1
  while boosting), and their loudness rises with it (`FlightFx.engineLoop`). The M4 idle hum takes over on the ground.

| Event | File (under `sounds/`) | Length | Final sound (as rendered) | Brief for the final sound designer |
|---|---|---|---|---|
| `ship.flight.engine` | `ship/flight_engine.ogg` | 3.00 s, loop | A full roar of thrust, a turbine whine, a sub-bass hum and a slow flutter of the exhaust. It is fuller and rougher than the M4 idle. | The courier flown by hand. It must stay pleasant across the pitch range and over long flights. |
| `ship.interceptor.engine` | `ship/interceptor_engine.ogg` | 2.00 s, loop | Two bright turbines a few hertz apart, so they beat, over a thin, raspy roar. It is higher and leaner than the courier's. | A small, fast two-seater: eager and light, never a scream. Same pitch rule as the courier. |
| `ship.flight.boost` | `ship/flight_boost.ogg` | 2.36 s | A pressure thump, then a brighter, longer plume swelling over a rising whine and the crackle of the exhaust. | The kick of the boost. It sits on top of the engine loop and does not replace it. |
| `ship.flight.thruster` | `ship/flight_thruster.ogg` | 0.60 s | A valve's click and a short, bright jet of gas. | Played on each strafe, climb or descent press, with its pitch varied a little. Short and soft, so repeats never tire. |
| `ship.flight.atmosphere` | `ship/flight_atmosphere.ogg` | 4.23 s | A deep roar of air swelling against the hull, buffeting, a rising whistle of wind and the hull creaking in the heat, then it eases off. | Breaking the atmosphere, under the overlay (pitch 1.0 going up, 0.9 coming down). Big but brief, and unlike a hyperspace jump. |
| `ship.flight.impact` | `ship/flight_impact.ogg` | 1.52 s | A heavy crunch, a gritty scrape falling in pitch and a ringing panel. | The hull strikes the ground and scrapes along it. It must sound costly without being an explosion. |
| `ship.flight.land_warn` | `ship/flight_land_warn.ogg` | 1.43 s | Two soft falling chimes from the cockpit, with a short low buzz under the second. | The landing is refused (only the riders hear it). A gentle "no", not an alarm. |
| `ship.flight.helm` | `ship/flight_helm.ogg` | 3.00 s | The struts fold with two hydraulic clunks, the ramp seals with a thud and a hiss, and the engines flare from a murmur to a strong whine. | Taking the helm: the moment the player becomes the pilot. It is also played lower (pitch 0.8) when the autopilot takes the ship. |
| `ship.flight.edge` | `ship/flight_edge.ogg` | 2.21 s | The nav computer's three-note chirp, then a long side burst of the thrusters as the ship swings round. | Turned back at the orbit's edge. Informative, not punishing. |
| `ship.refit` | `ship/refit.ogg` | 4.85 s | A crane's motor whirs and its gears click, a heavy plate clanks into place, welding crackles, and a cloud of steam hisses out. | The shipwright's refit under the steam, to the interceptor or back. The crane is heard but not seen (cut 1 in the design doc). |
| `ship.upgrade_fit` | `ship/upgrade_fit.ogg` | 1.80 s | A ratchet's quick clicks, a firm clunk as it seats, then a short rising chirp as it powers up. | An upgrade fitted or removed at the yard. |
| `ship.flight.alarm` | `ship/flight_alarm.ogg` | 2.00 s, loop | A two-tone klaxon through a small cockpit speaker: 620 Hz, then 470 Hz, a 0.3 s tone each half second with soft edges. | The disabled ship's alarm. Only the pilot hears it, from the tumble until the rescue arrives. Urgent but not harsh, and no clicks at the loop point. |
| `ship.flight.rescue` | `ship/flight_rescue.ogg` | 4.00 s | The rescue tug's engines swell in from afar, the tow clamps lock on with a heavy clank, and the comm chirps twice. | Relief: help has come. |

Reused from M4:

- `ship.damaged` as the ship is disabled (with the pilot's jolt);
- the M4 engine events around the helm: `ship.spool` and `ship.ramp` at the Spool, and `ship.climb` at the rise;
- for a landing by hand: `ship.descent`, then `ship.land` and `ship.ramp` at touchdown;
- the idle hum on the ground;
- the M4 jump sounds when the autopilot takes over.

## VFX

All counts go through `Vfx` (the particle preset), shakes through the shake setting, and blinks, flickers and pulses
through the flashing setting. Nothing raises dust in an orbit, and nothing forms a shape on the ground. The table per
event is in the design doc, section 12.

- **The helm:** the struts fold, the ramp shuts, the engines flare from every nozzle.
- **Flight:** an engine trail from each nozzle (2 puffs a tick at a hover, up to 8 at full speed, 12 for the boost's first
  10 ticks). Short white jets from the hull on the sides whose thrusters fire. The boost adds a longer, brighter plume
  and a pressure puff, and shakes the riders.
- **A crash:** sparks and the struck block's dust at the struck point.
- **Breaking the atmosphere:** the overlay (streaks and a heat shimmer, then black going up or cloud coming down; a plain
  fade with the hyperspace overlay option off), and `atmosphere_streak` particles rushing past the hull.
- **The refit:** a puff of steam that hides the body swap, then welding sparks for 40 ticks. A fit or a removal gives
  sparks at the fitting point.
- **The disabled ship:** sparks, smoke and flickering lights (steady with flashing off) as it tumbles. The alarm stops
  when the rescue tug arrives.
