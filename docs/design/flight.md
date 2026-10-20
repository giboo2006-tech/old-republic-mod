# Free flight and ships, Milestone 5: the helm, the flight model, landing anywhere, sky and orbit, the disabled ship, the M5 upgrades and the interceptor

Owner: W1 (flight and ships). Spec: `docs/MILESTONE_5.md` section 4 (all of it), 5.4's disabled ship (the lock, the
tumble, the rescue, their keys and sounds; W2 owns the shield and when a hit lands), 11.2's Hymn-Tuned Drive, 13.1 (the
Helm key), 13.2 (the flight HUD and the ship screen's changes), 14 (what flight saves and syncs), W1's rows of 15, the
`flight` section of 16.4, W1's payloads of 18.2 and the W1 tests of 19.1 and 19.2. The Milestone 4 ship and travel
design this builds on is `docs/design/travel.md`; the assets are in `docs/assets/flight.md`. The orbits, ship weapons
and space combat are W2's (`docs/design/space.md`); the shipwright's dialogue and stock are W4's.

API status: every vanilla and Fabric call is checked against the 26.2 dumps, the probe digests or code that already
compiled. There is no `// API-CHECK` (section 16).

## 0. Implementation status

**Recipe A.** The spike of 4.1 passed on CI (`FlightClientTest`, commit a76b12b): the pilot's client simulates the ship
and the server checks its moves. Fallback B was not needed and was not built (section 17).

Built: everything in section 4 (the helm, controls and the flight model, crashes, landing anywhere and in hubs, the
autopilot both ways, the sky and orbit bands, the orbit's edge and jumps from orbit, leaving mid-flight and restarts,
the camera, HUD and engine sounds), the M5 upgrades and the refit as dialogue actions (4.8, 4.9, cut order 9 is in: the
interceptor), `Ships.disable` with the tumble and the free rescue (5.4), the Hymn-Tuned Drive (11.2), the ship screen's
changes (13.2), the sounds (`tools/assets/sounds/flight.py`) and the tests.

Cut or reduced:

1. **No crane at the refit.** 15.3 lists "the shipwright's crane" for `ship_refit`; the refit shows a puff of steam that
   hides the body swap and 40 ticks of welding sparks, with the crane only in the sound.
2. **The THRUSTERS upgrade has no part on the model.** Cannons, the gunner turret, the missile rack and the shield
   emitter are drawn (`ShipWeaponParts`); thrusters show only as their extra speed and the boost's length.
3. **Not run on the game-test servers:** the sky and orbit bands, the edge, jumps from orbit and the hub glide need
   planets, orbits or a hub, which the game-test server lacks; there their rules are tested as pure functions. The orbit
   trip of 19.2 (`m5/FlightOrbitTrip`: climb into `orbit_home`, dive back, climb again, jump to `orbit_ghessar` through
   Nav with its confirm, dive and land at Saltgate by hand) flies the crossings, the jump and the hub glide live in
   `FlightClientTest` after the spike. It is written against W2's orbits and W3's Saltgate but has not run yet; the
   orbit's edge stays covered by the server tests only.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Entity | `oldrepublic:interceptor` (5 × 2.5, `ShipBody` subclass `Interceptor`) | `travel/ShipEntities`, `Interceptor`; client `InterceptorModel`, `InterceptorRenderer` |
| Key | `key.oldrepublic.helm`, default `Y`, category of the Force keys | `client/travel/FlightClient.HELM` |
| Command | `/oldrepublic ship helm <player>` (unlocks the helm; 4.2) | `travel/ShipCommands` |
| `ship_action` buttons (C2S) | `TAKE_HELM` 8, `LAND` 9, `RETURN_TO_LANDING` 10, `HAND_TO_AUTOPILOT` 11 (arg: destination), `JUMP_ORBIT` 12 (arg: destination), `OPEN_SCREEN` 13 | `travel/network/ShipActionPayload`, handled in `ShipActions.handle` |
| Payloads | `flight_boost` (C2S, Sprint held), `flight_status` (S2C, the helm's stage, boost, profile, landing check, cooldown, edge and ceiling), `flight_motion` (S2C, the server moved the piloted ship: velocity, yaw, turn the look) | `travel/network/Flight{Boost,Status,Motion}Payload`, registered in `FlightFeature` |
| Effect events | `ship_flight` (0 helm, 1 boost, 2 thruster puff, 3 impact, 4 to the autopilot, 5 turned back at the edge, 6 landing refused), `atmosphere` (0 up, 1 down; intensity 0 start, 1 end), `ship_refit` (0 to the interceptor, 1 to the courier, 2 upgrade fitted), `ship_disabled` (0 the tumble, 1 the rescue arrives) | `travel/TravelEffects`; client `FlightFx` |
| Particle | `oldrepublic:atmosphere_streak` (and the M4 `engine_exhaust`, `spark_shower`) | `TravelParticles`; `FlightFx` registers its provider |
| Dialogue | condition `ship:weapons`; actions `ship:fit <id>`, `ship:remove <id>`, `ship:refit interceptor|courier` | `travel/Shipwright` |
| Sounds (13) | `ship.flight.{engine,boost,thruster,atmosphere,impact,land_warn,helm,edge,alarm,rescue}`, `ship.interceptor.engine`, `ship.refit`, `ship.upgrade_fit` | Phase 0 `ModSounds`; recipes `tools/assets/sounds/flight.py` |
| Balance | section `flight` (section 13) | `travel/FlightBalance` |
| Lang | `travel.json` +97 keys (the helm, the HUD, refusals, landing reasons, the screen's M5 rows, the shipwright's messages, upgrades, the interceptor) | `src/main/lang-parts/travel.json` |
| Ship screen | Status: class line, hull out of the class's most, Take the helm / At the helm, Return to last landing, the shipwright's list; Nav in the air: "Land at …" and "Jump to … orbit" with a confirm | `client/travel/ShipScreen` |

## 2. Classes

New, server: `FlightBalance` (section `flight`), `FlightModel` (the flight step as pure functions, shared by the client
and the tests), `FlightRules` (every rule of this milestone as pure functions), `Helm` (the helm run on the server),
`Landings` (the landing check read from the world), `Shipwright` (the dialogue conditions and actions), `Interceptor`,
the three payloads.

New, client: `Piloting` (the local player at the controls: flying, gunner seat, the ship), `FlightClient` (the Helm key,
the inventory key at the helm, `flight_status` and `flight_motion`, the steering read at the end of `rideTick`),
`FlightFx` (W1's effect events, the running flight effects, the engine loop), `HelmHud`, `AtmosphereOverlay`,
`InterceptorModel`, `InterceptorRenderer`, `ShipWeaponParts`; mixins `LocalPlayerPilotMixin` (the pilot's input reaches
the ship after `rideTick`) and `MinecraftPilotMixin` (attack and use clicks at the helm do nothing).

Changed: `ShipBody` (the shared body out of `CourierShuttle`: synced `DATA_PILOTED` last, the client simulation, the
server's move checks, crashes, `canUsePortal`, `onBelowWorld`, seats and the weapon points), `CourierShuttle` (the
courier's numbers only), `Ships` (busy, `disable`, the refit's body swap in `tickBody`, `restore`, the hull clamp),
`Flights` (from the air, the take-over in the Descent, the rescue, jumps between orbits, recovery of a FLYING record),
`ShipActions` (the new buttons; `handle` is public as the tests' seam), `TravelService` (the blockade rule, below),
`ShipEvents` (riders leaving), `Pads` (the last landing), `ShipRules` (`PHASE_HELM`, `maxHull`, paint), `ShipCommands`,
`TravelEffects`, `TravelNetwork`, `TravelParticles`, `TravelDialogue`, `ShipStatusPayload`, `mixin/travel/PlayerShipRideMixin`;
client `CourierShuttleRenderer` (and its state and model: weapon parts, flicker lights), `TravelFx`, `ShipLoop`,
`FlightHud`, `HyperspaceOverlay`, `ShipScreen`, `ShipCameraClient`.

**Request applied (W4, spec 6.9):** `TravelService.start` sets the blockade arrival only with free flight on
(`FlightBalance.get().enabled && FlightRules.blockadeArrival(...) && orbit exists`), so with flight off the run lands at
the holdfast and W4's `TyrvannArc` plays the scripted pass.

## 3. The helm (4.2)

The owner in the pilot seat of a repaired ship at rest presses Helm (or the Status tab's button). The refusals, in
order (`FlightRules.helmRefusal`): not the owner, flight switched off (`flight.enabled`), the helm not unlocked
(`TravelData.helm`, by the Chapter Five step or the command), busy (a run or a flight on the record, `FLYING` or
`LAUNCHING`), the state (the hulk), the combat lock. `GROUNDED` and `DISABLED` ships may fly (sublight flight needs no
fuel and has no hull minimum). Then the **room check**: the hull's box over the rise (`helmRiseBlocks` up) must be clear,
at the press and again after the Spool (`NO_ROOM`).

Stages (`Helm.Stage`): **Spool** (`helmSpoolTicks` 40: the M4 Spool, the ramp shuts and the struts fold at 20),
**Rise** (`helmRiseTicks` 20, `helmRiseBlocks` 4 straight up under server control), then the **hand-over**: the record
goes `FLYING`, the body's piloted flag is set after `flight_status` carried the flight numbers, the camera goes out
behind the ship, `QuestHooks.onHelmTaken` fires, and crashes count from 10 ticks later. **Piloted** (flown by hand),
**Hover** (held by the server: flight off, the blockade's arrival), **Glide** (a hub landing's run to its bay),
**Land**, **Settle**, **Tumble**. The record's position stays the last landing spot until a landing by hand moves it.

## 4. Controls and the flight model (4.3, 4.4)

The pilot's client flies the ship (`ShipBody.tick` simulates it for the controlling passenger); vanilla sends the moves
and the server replays them as `MoverType.PLAYER` moves. The controls are the player's `Input` and look, read at the end
of `rideTick`: Forward thrust, Back brake (then a slow reverse), Left and Right strafe, Jump climb, Sneak descend (the
pilot never dismounts at the helm), the look turns the ship at its turn rate and pitches the nose within `pitchLimit`
(inverted with the client option `invertShipPitch`), Sprint asks the server for the boost, the inventory key opens the
ship screen, and attack and use do nothing.

`FlightModel` keeps the speed in three channels (along the heading, the strafe, world up). A channel with no input
decays (`decaySky` 0.97, `decayOrbit` 0.99 a tick), so the ship coasts to a hover; released Jump or Sneak stop the climb
at the brake rate, so the height holds. No velocity exceeds the profile's cap (`maxSpeedCap` 2.0). The numbers
(`FlightRules.profile`) per class and place: the courier 1.0 in a sky and 1.4 in an orbit, thrust 0.04, brake 0.08,
climb 0.4, turn 6°; the interceptor 1.2 / 1.45, 0.07, 0.12, 0.5, 9°. THRUSTERS add `thrustersSpeedPct` 10 % top speed
and `thrustersBoostPct` 25 % boost time. The boost (`FlightRules.Boost`, kept by the server): ×`boostMultiplier` 1.25
for `boostTicks` 60, then `boostRechargeTicks` 160 to refill; thruster puffs when a strafe, climb or descent key goes
down.

**The server's checks** (`ShipBody.checkPilot`, `checkImpact`): a move longer than the cap × 1.1 is put back (the
client counts these corrections; the spike allows two in its run). A move that loses speed against blocks counts as a
crash: the speed lost along the blocked axes over `impactFloor` 0.6 costs `impactHull` 2 per `impactStep` 0.25 begun
(0.61 → 2, 0.86 → 4, 1.6 → 8, 2.0 → 12), at most once in 10 ticks, with `ship_flight` 3 (sparks and the struck block's
dust) and `ship.flight.impact`. At 0 hull a crash disables the ship (section 9). The server never moves a piloted ship
itself; when it must (an orbit crossing, the edge) it tells the pilot's client with `flight_motion`.

## 5. Landing (4.6)

Every 5 ticks the server checks the spot below (`Landings.check`, the rules in `FlightRules.landing`), and the HUD shows
the result. The reasons, in order: `TOO_HIGH` (no ground within `landHeight` 6 under the footprint), `TOO_FAST` (over
`landMaxSpeed` 0.25 a tick), `HUB` (inside a hub's footprint: allowed), `NO_GROUND` (water or lava), `NOT_LEVEL` (a
column not solid, or more than `landLevelTolerance` 1 between the highest and lowest), `NO_ROOM`, `SITE` (another
player's site), `OK`. The footprint is the class's pad box (`Pads.isClear`).

The Helm key in the air lands: refused, the overlay says why with `ship_flight` 6 and `ship.flight.land_warn`;
allowed, the server takes the ship straight down onto the spot (the floor's x and z centre, on top of the highest
block) over clamp(4 ticks per block, 20, 80) ticks, struts down for the last 6 blocks; in a hub it first glides to the
first free bay within `hubBayRange` 32. At touchdown the record's position becomes the spot (`FlightRules.landedAt`, the
M4 Settle's state rule), the spot becomes the owner's last landing (`TravelData.lastPad`), `Pads.landed` runs and every
rider on a planet gets `onPlanetArrived`. After the Settle (`travel.settleTicks`) the riders step off beside the ramp
and the helm is released. A field landing keeps its spot across a restart: the body is restored there.

## 6. The autopilot both ways (4.5)

In the air the Nav tab's "Land at …" hands the ship to the M4 autopilot from its Jump (`Flights.launchFromAir`). A
destination on the planet below is a **hop** (no cell, no wear; home from the overworld is a hop); another system is a
jump (a cell and the wear, `FlightRules.afterAirJump`). The refusals are the M4 launch's plus the helm's
(`FlightRules.airRefusal`): travel off, not at the helm, locked, blockaded, no fuel for a jump, under fire (W2), the
combat lock. Helm during the autopilot's Descent takes the ship back at once (`Flights.abandon`, no Spool): `FLYING`,
with the destination's pad (which the record names from the Descent on) as its last landing spot.

The blockade (6.5): a run to the holdfast during `BLOCKADE` ends in Tyrvann's orbit, held (`HOVER`), when free flight is
on (`FlightRules.blockadeArrival`); with flight off W4's scripted pass plays at the holdfast (section 2).

## 7. Sky and orbit (4.7)

Needs W2's orbits (`Planets.orbitLevel`); a planet without one has no ceiling. In a planet's sky above `orbitCeiling`
300 the ship crosses into its orbit at `orbitArrivalY` 100, x and z divided by `orbitScale` 8; in an orbit below
`atmosphereLine` 64 it crosses down into the sky at `skyArrivalY` 290, x and z times 8; not within
`transitionCooldown` 60 ticks of the last crossing. Arrivals in an orbit are pulled inside the edge's warning band
(`FlightRules.clampInside`, radius `orbitRadius − edgeWarning` = 1,920). The body crosses with its riders through
`TravelService.moveAcross` under a ticket held from the line; the pilot keeps the helm, and `flight_motion` gives the new
body the old one's velocity and heading. Every rider gets `atmosphere` at the line and at the end: the overlay (streaks
and heat shimmer, then black going up or cloud going down; `AtmosphereOverlay`, about 30 ticks, drawn over the level
loading screen and never longer than 200 ticks) and `ship.flight.atmosphere`. `QuestHooks.onOrbitEntered` fires.

**The edge:** within `edgeWarning` 128 of `orbitRadius` 2,048 the HUD warns (a slow pulse, steady with flashing off);
past it the server turns the ship back: 16 blocks inside along its own radius, heading for the centre, the pilot's look
turned with it (`flight_motion`), `ship_flight` 5 and `ship.flight.edge`.

**Jumps from orbit** (Nav, "Jump to … orbit", with a confirm): only from an orbit, to another system's orbit; the M4
Jump and Hyperspace phases (a cell, the wear), then the helm again in the other orbit over the pad divided by 8 at
`jumpArrivalY` 160, inside the band. The body and riders stay together.

**Clear arrivals:** every place the server puts a flown ship (a crossing either way, the edge's turn back, a jump into
an orbit) goes through `Helm.clearArrival`. When the ship's hitbox at the computed place meets a block (an asteroid),
`FlightRules.clearArrival` looks for the nearest place where it fits (`Ships.hullFits`: loaded, no block, nothing
solid): a block at a time straight up, at most `CLEAR_UP` 16 blocks, then sideways at the same height on rings 2
blocks apart out to `CLEAR_SIDE` 16 blocks, 16 bearings each. It stays inside the arrivals' band in an orbit (the turn
back: 16 blocks inside the edge) and at most `orbitCeiling − 1` in a sky, so the ship does not cross straight back up.
When nothing there fits, or the arrival's chunk is not loaded yet, the ship goes to the computed place; the first is
logged.

## 8. Leaving, restarts and the return to the last landing (4.10)

- A rider (not the pilot) leaving the server mid-flight is put down beside the last landing; the pilot keeps flying.
- The pilot leaving the seat, the server or dying: before the rise nothing has happened (released); in the air the
  ship makes the rescue run home with everyone aboard (section 9, but at no cost and with no hull change).
- A restart with a record `FLYING` and no run: `Flights.recover` (every 200 ticks) settles it at its last landing.
- The Status tab's "Return to last landing": paid by the free daily recall when unused today (`recallDay`), else a cell
  from the tank, else one carried; refused when not at the helm, with nothing to pay, under fire or in combat.
- A ship never uses a portal (`canUsePortal` false) and never falls out of the world (`onBelowWorld`: home with its
  riders).
- No body reference is kept across a tick or a level change; only `TravelService.moveAcross` moves a ship between
  levels, under a ticket.

## 9. The disabled ship and the rescue (5.4)

`Ships.disable(owner)` (W2's fire at 0 hull, or a crash): only at the helm in the air (not in the Spool, the rise or the
Settle). The controls lock (`HELM_TUMBLE`): for `tumbleTicks` 40 the ship rolls and sinks 0.05 a tick with sparks,
smoke and flickering lights (steady with flashing off), `ship_disabled` 0 and the pilot's `ship.flight.alarm` loop. Then
the free rescue (`Flights.rescue`): the Jump, then home to the last landing through Hyperspace (80) and the Descent
(80) with everyone aboard, `ship_disabled` 1 and `ship.flight.rescue` on arrival; the record arrives with at least
`rescueHull` 20 (`FlightRules.rescued`), `READY` or `GROUNDED`. Nothing is lost.

A disable ends its owner's space encounter or blockade (`SpaceEncounters.onDisabled`, W2's): W2's `ShipCombat` tells
the encounters after its own `Ships.disable`, and `Helm.impact` does for a crash at 0 hull, only when `Helm.disable`
really disables the ship (never twice for one disable: a tumbling ship flies no moves).

## 10. Upgrades, the Hymn-Tuned Drive and the interceptor (4.8, 4.9, 11.2)

The shipwright's services (`Shipwright`, called from W4's dialogue) need the ship landed at his yard: in his world,
within 64 blocks of him.

- `ship:fit cannons|shield|thrusters|missiles`: once each, at `cannonsPrice` 800, `shieldPrice` 1,000,
  `thrustersPrice` 900, `missilesPrice` 1,200 (credits from the pockets and the ship's hold). `ship:fit
  spindle_capacitor|hymn_drive` fits W7's legendary part from the pack for `legendaryFitCredits` 500.
  `ship_refit` 2 and `ship.upgrade_fit`.
- `ship:remove <id>`: out, for nothing, with no refund (and the part is not returned).
- `ship:weapons`: whether the ship has cannons (W4's dialogue branches on it).
- **The Hymn-Tuned Drive:** no hull wear on jumps (`ShipRules.wear`) and Hyperspace × `hymnDriveHyperspaceMult` 0.5.
- **The refit** (`ship:refit interceptor`): `refitCredits` 2,500 and `refitAlloy` 6 alloy plates, nobody aboard and at
  most `interceptorHold` 9 stacks in the hold (the shipwright says how many to unload); back to the courier
  (`ship:refit courier`) for `refitBackCredits` 500. The record keeps its hull code, fuel, upgrades, paint and (trimmed
  to one) crew; to the interceptor the hull is cut to `interceptorHull` 80 (`ShipRules.maxHull`), back it is kept.
  `Ships.tickBody` swaps the body for one of the new class at the same spot within 10 ticks, under `ship_refit`'s steam.
- **The interceptor:** 5 × 2.5, one pilot seat and the gunner's seat (`GUNNER_SEAT`, the first rider who is not the
  owner, on a flying ship with cannons: `ShipBody#gunner`, used by W2), a 9-stack hold, 80 hull, faster and nimbler
  (section 4). Its weapons hang at its own points (`cannons()`, `turret()`, `rack()`), where `ShipWeaponParts` draws them
  and W2 fires from.

## 11. Camera, HUD, the ship screen and sounds (4.11, 13.1, 13.2)

- **Camera:** at the rise a first-person pilot goes to the camera behind the ship (`shipCamera` option), and back when
  the helm is released, unless they chose another camera meanwhile.
- **HUD** (`HelmHud`, at the top of the screen where the M4 flight HUD sits): the speed with a throttle line, the boost
  meter, the hull bar out of the class's most, the altitude or "Orbit: <planet>", the helm's stage while the server
  flies; one line under it: the edge warning, then "Y: Land" (or why not, once low), then the orbit cue near the
  ceiling. W2's shield, combat panel and scan meter sit beside and under it.
- **The ship screen:** the inventory key at the helm opens it. Status: the class, hull out of its most, "Take the helm"
  / "At the helm" (greyed with the reason), "Return to last landing" with its cost, the shipwright's fitted list. Nav in
  the air: "Land at …" or "Jump to … orbit" (a toggle), each with a second click to confirm. Test seams for the lead's
  playthrough: `selectDestination`, `setJumpMode`; the Nav rows keep a 22-pixel pitch from six rows.
- **Sounds:** the engine loop of the courier or the interceptor follows the ship in flight with its pitch rising with
  speed (`ShipLoop`), the M4 idle takes over on the ground; the alarm loop is the pilot's alone and stops when the
  rescue completes; the rest are one-shots on their events (section 12).

## 12. Effects (15)

| Event | Looks | Sound |
|---|---|---|
| `ship_flight` 0 helm | the struts fold, the ramp shuts, the engines flare | `ship.flight.helm` |
| `ship_flight` 1 boost | a longer, brighter plume and a pressure puff for 10 ticks; the riders' shake (settings) | `ship.flight.boost` |
| `ship_flight` 2 thrusters | short white jets from the hull on the pressed sides | `ship.flight.thruster` |
| `ship_flight` 3 impact | sparks and the struck block's dust at the struck point | `ship.flight.impact` |
| `ship_flight` 4 autopilot | none (the M4 Jump follows) | `ship.flight.helm`, lower |
| `ship_flight` 5 edge | none (the turn itself, the HUD's warning) | `ship.flight.edge` |
| `ship_flight` 6 landing refused | none (the HUD line and the overlay message) | `ship.flight.land_warn`, the riders only |
| `atmosphere` | `AtmosphereOverlay` and `atmosphere_streak` around the hull | `ship.flight.atmosphere` |
| `ship_refit` 0, 1 / 2 | a puff of steam over the swap, then welding sparks for 40 ticks / sparks at the fitting point (a fit or a removal) | `ship.refit` / `ship.upgrade_fit` |
| `ship_disabled` 0 / 1 | sparks, smoke and lights that flicker (steady with flashing off) for the tumble, the pilot's jolt / the alarm stops | M4 `ship.damaged` and `ship.flight.alarm` (loop, the pilot) / `ship.flight.rescue` |
| running | the engine trail (2 puffs a tick at a hover to 8 at full speed, 12 for the boost's first 10 ticks), the engine loop | `ship.flight.engine`, `ship.interceptor.engine` |

Every count goes through `Vfx`; nothing raises dust in an orbit; no effect draws a shape on the ground; every running
effect stops with its condition, its ship, the level change and a disconnect. The atmosphere overlay follows the
client's `hyperspaceOverlay` option (off: a plain fade to black or white over the same moments).

## 13. Balance (`flight`, 16.4)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | free flight; off, the helm is refused and the autopilot ends the blockade run at the holdfast |
| `serverSteering`, `serverSteeringSpeedPct` | false, 67 | reserved for fallback B, which was not built (section 17) |
| `maxSpeedCap` | 2.0 | the hard cap on a ship's speed; the server puts back a move over 1.1 × this |
| `pitchLimit`, `reverseSpeed`, `strafePct` | 60, 0.2, 40 | the nose's pitch, the reverse's top speed, strafe as a share |
| `courier{Sky,Orbit,Thrust,Brake,Climb,Turn}` | 1.0, 1.4, 0.04, 0.08, 0.4, 6 | the courier's numbers |
| `interceptor{Sky,Orbit,Thrust,Brake,Climb,Turn}` | 1.2, 1.45, 0.07, 0.12, 0.5, 9 | the interceptor's numbers |
| `decaySky`, `decayOrbit` | 0.97, 0.99 | the share of speed kept each tick with no input |
| `boostMultiplier`, `boostTicks`, `boostRechargeTicks` | 1.25, 60, 160 | the boost |
| `thrustersSpeedPct`, `thrustersBoostPct` | 10, 25 | the THRUSTERS upgrade |
| `impactFloor`, `impactStep`, `impactHull` | 0.6, 0.25, 2 | crashes |
| `helmSpoolTicks`, `helmRiseTicks`, `helmRiseBlocks` | 40, 20, 4 | taking the helm |
| `landHeight`, `landMaxSpeed`, `landLevelTolerance`, `hubBayRange` | 6, 0.25, 1, 32 | landing |
| `orbitCeiling`, `atmosphereLine`, `orbitScale` | 300, 64, 8 | the bands |
| `orbitArrivalY`, `skyArrivalY`, `jumpArrivalY`, `transitionCooldown` | 100, 290, 160, 60 | arrivals |
| `orbitRadius`, `edgeWarning` | 2048, 128 | the edge |
| `cannonsPrice`, `shieldPrice`, `thrustersPrice`, `missilesPrice` | 800, 1000, 900, 1200 | the M5 upgrades |
| `refitCredits`, `refitAlloy`, `refitBackCredits`, `interceptorHold`, `interceptorHull` | 2500, 6, 500, 9, 80 | the refit and the interceptor |
| `legendaryFitCredits`, `hymnDriveHyperspaceMult` | 500, 0.5 | the legendary parts |
| `rescueHull`, `tumbleTicks` | 20, 40 | the disabled ship |

## 14. Saved and synced (14)

Saved: nothing new of W1's own. The lead's records carry it: `ShipRecord` (`FLYING`, the class and upgrade bits, the
last landing as `pos`), `TravelData` (`helm`, `lastPad` with its dimension, `recallDay`). Runs live in memory and are
never saved; after a restart a `FLYING` record goes home (section 8). Synced: `ShipBody`'s `DATA_PILOTED` (added last,
after the M4 fields), the phase, the paint and the class from the record (`Ships.show`); the payloads of section 1.

## 15. Tests

- `FlightGameTests` (19.1, 31 tests: the spec's 30 names and `arrivalPicksAClearPlace`): the pure rules with
  `new FlightBalance()` / `new ShipBalance()`, and live runs in the M4 travel tests' arena (`TravelGameTests.ARENA`,
  opened to the sky, its `Scene` and helpers) with listed mock players: the helm and its refusals, the controller, no
  server moves, the speed cap and the move check, crashes into a wall, a field landing kept across a restart (codec
  round trip and `Ships.restore`), riders and the pilot leaving, restarts while flying, the rescue across levels with
  two riders, a jump that keeps the body and riders, an arrival's clear place around placed blocks, the refit's
  refusals and payment, upgrades and their removal, the interceptor's seats, the disable and the rescue at 20 hull,
  the autopilot from the air and the take-over in the Descent, the return to the last landing and its costs, and a
  pilot never kicked for flying. Intents go through `ShipActions.handle`; a pilot's flight is replayed with
  `ShipBody.move(MoverType.PLAYER, …)`; the Nether's bedrock roof stands in for another world.
- `FlightClientTest` (the lead's driver) with `m5/FlightSpike` (W1's steps and assertions): the spike of 4.1 on a
  dedicated server; screenshots 108 and 109.
- `FlightClientTest` then runs `m5/FlightOrbitTrip` (19.2) on the same server: after the spike's rescue, the Helm key,
  the climb toward the world's centre into `orbit_home`, a stop, the dive back, the climb again, the ship screen's Nav
  tab (mode button, Saltgate's row, Jump and its confirm, all mouse clicks) for the jump to `orbit_ghessar`, the dive
  into Ghessar's sky, and down over Saltgate's pad by hand to the landing check's `HUB` and the Helm key's glide onto a
  bay. Each crossing and the jump are matched to their rule's arrival; the helm, the status's top speed and orbit cue,
  the overlay's timing, the cell spent and the last landing are checked; every phase is time-boxed. Shortcuts, logged:
  the owner put beside the pad and boarded on the server, the Nav tab opened through `ShipScreen.openTab`, Saltgate's
  row through `selectDestination` only if its click did not enable Jump, any sandstorm stopped by command. Screenshots
  091 `orbit_liftoff`, 092 `atmosphere_line`, 093 `orbit`, 094 `saltgate_landing` (the lead's band).
- `M5ClientTest` with `m5/FlightClientSteps`: on a stage 60 blocks over the player, real Helm, Jump, Forward, Back and
  Sneak presses fly the player's ship; screenshots 100 the helm HUD, 101 the landing hint, 102 and 103 the atmosphere
  overlay up and down, 104 the refit to the interceptor, 105 and 106 its Imperial and Vasch paints, 107 a field landing,
  each with its server-side checks.

## 16. API status

No `// API-CHECK` is left. `ShipBody#canUsePortal(boolean)` overrides `public boolean canUsePortal(boolean)`, which is
declared on `net.minecraft.world.entity.Entity`: the `Entity` class dumps list it, not only its overrides on
`LivingEntity` and `ThrowableProjectile`.

## 17. Decisions and known issues

- **Fallback B is not built.** The spike passed on recipe A; `flight.serverSteering` only scales the flight numbers by
  `serverSteeringSpeedPct` and does not make the server steer.
- The inventory key at the helm is taken at the start of the client tick (vanilla reads its keys before `rideTick`).
- `atmosphere`'s intensity is its stage: 0 at the line, 1 at the end.
- The client option `hyperspaceOverlay` also governs the atmosphere overlay.
- The boost meter is the server's `FlightRules.Boost` record, sent in `flight_status`.
- Nav in the air needs two clicks (a confirm), and one toggle chooses between landing and a jump to an orbit.
- `ShipActions.handle` is public as the game tests' seam; `TravelGameTests`' arena, `Scene` and helpers are
  package-private for `FlightGameTests`.
- The client steps' atmosphere shots send the event the helm sends at the line (no orbits before W2's merge); the real
  crossing is `m5/FlightOrbitTrip`'s, in `FlightClientTest`.
- Clear arrivals (section 7) are checked only where the arrival's chunks are loaded: a crossing goes ahead after
  60 ticks and a jump after `hyperspaceMaxWait` whether or not they are, and then the computed place is used unchecked.
  A place with no room within 16 blocks up or sideways (a dense field) is used as computed too, with a warning.
- Risks on CI: `rescueKeepsRidersAcrossLevels` is the first test to move mock players across levels, and the Nether's
  roof chunks must generate within its budget.
