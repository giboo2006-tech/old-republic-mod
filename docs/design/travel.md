# Ships and travel feature design: the courier shuttle, repairs, flight, pads, beacon (Milestone 4)

Owner: W3 (ships and travel). Packages `com.oldrepublic.travel` (common, with `travel.network`),
`com.oldrepublic.client.travel` (client) and `com.oldrepublic.mixin.travel` (common mixin, config
`oldrepublic-travel.mixins.json`). Spec: `docs/MILESTONE_4.md` sections 4.1 to 4.11, 11.2 and 11.3 (flight HUD,
hyperspace overlay, toasts, the ship screen and the destination map), 12 (saved state, multiplayer, edge cases), 13
(`ship_engine`, `ship_jump`, `hyperspace`, `ship_repair`, `ship_damage`, `landing_beacon`; the `engine_exhaust` and
`spark_shower` particles; the ship and beacon sounds), 14.3 (`ship` and `travel` balance), 16 (ids, payloads, lang)
and 17 (`TravelGameTests`, screenshots 30 to 39). Assets are in `docs/assets/travel.md`.

The lead's contract files are untouched: `ShipState`, `Destination`, `ShipRecord`, `TravelData`, `TravelAttachments`,
`TravelLocks`, and the body of `TravelService.moveAcross`, which is the only code that moves anything between levels.

Nothing was cut: upgrades beyond seats and hold (cut item 7) and the destination map (cut item 8) are both in. The map
is the Nav tab's **Chart** view rather than a separate screen (section 9).

API status: everything is checked against the 26.2 digests and the Fabric 26.2 sources; the five `// API-CHECK:`
points listed in section 14 were probed by `tools/api-probe/m4_w3.txt` (results there).

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Entity | `courier_shuttle` (7 × 4 × 7 box, model about 9 × 4 × 11) | `ShipEntities`, `CourierShuttle` |
| Blocks | `landing_beacon` (with block entity), `pad_terminal` | `TravelBlocks` |
| Items (the lead's `M4Items`) | `power_core`, `hyperdrive_motivator`, `nav_computer`, `hyperfuel_cell`, `passenger_seat`, `cargo_bay_extension`, `reinforced_plating`, `fuel_tank_extension`: W3 gives them their models, icons and uses | `ShipActions`, `NavData` |
| Data component | `oldrepublic:nav_data` (list of planet ids) | `NavData` |
| Global storage | `oldrepublic:ships` (attachment on the overworld, every `ShipRecord`) | `ShipRegistry` |
| Player attachments | `oldrepublic:travel` (the lead's `TravelData`), `oldrepublic:travel_visits` (worlds landed on, a bit per planet) | `TravelAttachments`, `TravelVisits` |
| Chunk ticket type | `oldrepublic:ship_pad` | `TravelTickets` |
| Menu type | `oldrepublic:ship` | `ShipMenu` |
| Payloads | `ship_action` (C2S), `ship_status`, `flight_phase` (S2C) | `travel.network` |
| Particles | `engine_exhaust`, `spark_shower` | `TravelParticles` |
| Effect events | `ship_engine`, `ship_jump`, `hyperspace`, `ship_repair`, `ship_damage`, `landing_beacon` | `TravelEffects` |
| Sounds | `ship.{idle,spool,climb,descent,land,ramp,jump_out,jump_in,hyperspace,fit_part,weld,power_up,damaged}`, `beacon.{set,refuse,recall}` | `tools/assets/sounds/ship.py` |
| Templates | `ship/cradle` (`SHIP_CRADLE`), `ship/beacon_pad` (stamped, no `SiteKind`) | `Ships`, `LandingBeaconBlock` |
| Dialogue | `pad_terminal`; conditions `ship:state`, `ship:aboard`; actions `ship:recall_free`, `ship:tow`, `ship:recall_terminal`, `ship:restore`, `nav:load` | `TravelDialogue` |
| Balance | sections `ship` and `travel` | `ShipBalance` |
| Commands | `/oldrepublic ship give|hulk|repair|fuel|hull|travel|recall|restore|unlock|info` | `ShipCommands` |
| Lang | `src/main/lang-parts/travel.json` | |

Append-only: `TravelService.Refusal` (stored by ordinal in `ship_status`; `HERE` and `UNAVAILABLE` were appended
after the Phase 0 values), `ShipRules.Recall`, the effect variants below, the socket indices and the upgrade bits.

## 2. Classes

Common (`src/main/java/com/oldrepublic/travel/`):

- `ShipRules`: every rule as a pure function on detached records (capacities, the launch checks, the jump's cost,
  the Settle state, sockets, plates, fuel, upgrades, crew, bays, recalls, restart recovery, flight curves, hull codes,
  the wear level a body shows). The game tests drive these directly.
- `ShipRegistry`: the global `oldrepublic:ships` attachment; `of`, `byBody`, `put`, `snapshot`, `commit`.
- `Ships`: bodies. `placeHulk` (cradle and hulk, 4.2), `give`/`giveAt` (command and test path), `update` (read,
  change, store, re-show, tell the story), `shuttle`/`body`, `restore` (4.9), `tickBody` (stale copies discard
  themselves, the body is kept on its pad, non-crew riders are put off, the damage effect on a hull drop),
  `placeBesidePad`, `safeFall`.
- `CourierShuttle`: the body entity (section 3).
- `Flights`: the flight sequence (section 5), recalls and tows, restart recovery, riders leaving mid-flight.
- `TravelService`: `canLaunch`, `launch`, `launchByCommand`, `recall` (both overloads), `isUnlocked`, `unlock`,
  `pad`, and the lead's frozen `moveAcross`.
- `Pads`: where ships land (section 6). `TravelTickets`: the `ship_pad` tickets. `CombatLock`: the launch's combat
  lock.
- `ShipActions`: everything a player does to a ship: boarding, welding, fuelling, the four sockets, upgrades, crew,
  and the `ship_status` view.
- `ShipMenu`: the hold (section 9). `ShipCommands`, `ShipEvents`, `ShipCamera`, `NavData`, `TravelVisits`,
  `TravelDialogue`, `TravelEffects`, `TravelParticles`, `TravelNetwork`, `ShipBalance`, `TravelFeature` (wiring).
- `LandingBeaconBlock`, `LandingBeaconBlockEntity`, `PadTerminalBlock`, `TravelBlocks`.
- `mixin/travel/PlayerShipRideMixin`: `Player#wantsToStopRiding` returns false while the ship locks its riders, on
  both sides, so the client never predicts a dismount the server refuses.

Client (`src/client/java/com/oldrepublic/client/travel/`): `CourierShuttleModel`, `CourierShuttleRenderState`,
`CourierShuttleRenderer`, `TravelFx` (effect handlers, particles, running effects), `ShipLoop` (loops),
`HyperspaceOverlay`, `FlightHud`, `ShipToasts`, `ShipCameraClient`, `ShipScreen`, `ShipClientState`, and
`TravelClient` (wiring, the receivers, the nav computer's tooltip).

## 3. The record and the body (4.1, 4.9)

The truth is the owner's `ShipRecord` in `ShipRegistry`; a `CourierShuttle` only shows it. Every change goes through
`Ships.update` (or `ShipRegistry.put` from a snapshot in tests), which re-shows the body, plays `ship_damage` when the
hull dropped (a body's own tick catches a drop written with a plain `put`) and calls `QuestHooks.onShipStateChanged`
when the state changed.

- **Never twice.** Every new body gets a new UUID, written to the record before the body is added. A body whose UUID
  is not its record's current `body` discards itself on its first check (every 10 ticks), so a copy in an old chunk
  save, a duplicated entity or a body left behind by a restore never shows a second ship or a second hold.
- **Never lost.** The body takes no damage at all and `/kill` removes only the body; `Ships.restore` places a new
  one from the record at its last pad, waiting under a chunk ticket when that chunk is not loaded (it never writes
  into an unloaded chunk). The beacon, the pad terminal and the `restore` command call it.
- **Synced data**: state, hull, wear level (`ShipRules.wearLevel`: sound, below `ship.damageFxHull`, below the jump
  minimum; decided by the server's balance), phase, struts, hull code (packed into an int), upgrade mask (with the
  paint) and owner.
- **Nobody steers.** `getControllingPassenger()` is null, so no client sends vehicle movement and the vanilla
  floating kicks skip riders. Seats: the pilot plus `ShipRules.seats` passengers; only the owner and crew may board.
- **Hull codes**: the first two letters of the owner's name and a number from 100 to 999 seeded by their UUID
  (`AB-123`), on both sides of the nose.
- **Paint** (two bits of the upgrade mask, fixed when the hulk is placed): Watch green for a Jedi (the *Patient
  Star*), Imperial grey for a Sith, Vasch's rust for an Independent.
- **Socket progress** of the hulk lives in the upgrade mask too (core, motivator and nav bits, four bits of plates),
  above the four upgrade bits. `ShipRecord` has no field for it, and adding one would change the lead's codec.

## 4. Hulk, repairs, fuel, wear, upgrades (4.2 to 4.4, 4.7)

- **The hulk** is placed by `Ships.placeHulk(owner, near)` (called by W2's `ChapterFourRepairs`): the `ship/cradle`
  template on the M3 rings within `ship.cradleRange` of `near`, the hulk body at its `HEART` anchor, nose to the south
  of the template, and `ship.starterCells` cells in the hold. Null means "no spot yet; retry later".
- **Sockets** on the Repairs tab: plating (12 `durasteel_plate`, any number per click), power core, hyperdrive
  motivator, nav computer (a *loaded* one: `NavData.isLoaded`; a blank one says so). A nav computer is crafted blank
  and loaded by using it on a mentor (the path's world: Selvaren for a Jedi, Varkhast for a Sith) or, for an
  Independent, on the wreck at Courier's Fall (both worlds, from its nav log; any block of a recorded
  `COURIERS_FALL` piece). The listeners run in their own phase ahead of Fabric's default, so the mentor's dialogue
  does not take the click. Dialogues can do the same with `nav:load` (optionally with planet ids). The tooltip lists
  the worlds on it. Each click takes the parts from
  the inventory, plays the socket's fit effect, saves at once and calls `QuestHooks.onShipPartFitted(owner, socket)`.
  When all four are in, the state becomes `GROUNDED` (or `READY` with fuel in the tank): the tarp is pulled back,
  the jacks retract and the running lights come on (`ship_repair` variant 5 and the `power_up` sound), and the owner
  gets the "Ship repaired" toast.
- **Fuel**: a cell used on the ship, or the Status tab's Add fuel button. A tank of `tankCells` (6 with the fuel tank
  extension). An empty tank is `GROUNDED`.
- **Wear**: each jump takes `cellsPerJump` and `wearPerJump` (3 with reinforced plating). Below `jumpMinHull` the ship
  is `DISABLED` and refuses to jump (`HULL`). The nav console warns when the jump will leave the hull below the
  minimum. The wear level shows on the body: sparks and smoke from a nacelle below `damageFxHull`, seam smoke, a
  crackling loop, a stuttering nozzle and one dead running light below the minimum, and scorch marks that deepen as
  the hull falls.
- **Welding**: a `durasteel_plate` used on a repaired ship on the ground adds `plateRepair` hull, or
  `plateRepairSkilled` for an Armourcraft or Armstech player at discipline level 2 (W5's `Crafting.level`). There is a
  `plateRepairCooldown` between welds. A `DISABLED` ship becomes `READY` or `GROUNDED` again once over the minimum.
- **Upgrades** (Status tab, owner only, on the ground, not on a hulk): `passenger_seat` (+1 seat), `cargo_bay_extension`
  (hold 27 to 36), `reinforced_plating` (wear 5 to 3), `fuel_tank_extension` (tank 4 to 6). Each fits once and comes
  out as its item. A removal is refused while it would strand something: a crew member over the seat limit, items in
  the extra hold slots, or fuel over the small tank.

## 5. The flight (4.6)

`TravelService.launch(owner, destination)` checks `ShipRules.launchRefusal` in this order: an owner with a ship, the
config switch, no flight running, an unlocked destination, the hull (`DISABLED`, or below the minimum), fuel, `READY`,
then the combat lock. The Nav tab shows the same reason on each row. `HERE` (the ship stands at that destination) and
`UNAVAILABLE` (its world is missing, or the camp is not placed) come from `TravelService.canLaunch`. The combat lock
(`CombatLock`) refuses while a hostile within `combatLockRange` has hurt the owner or a crew member in the last
`combatLockTicks`. Hits between crew do not count.

`Flights` then runs one in-memory `Active` per owner, a tick at a time:

| Phase | Server | Riders |
|---|---|---|
| Spool (`spoolTicks`) | `LAUNCHING`; the record gets `flight(from, to, 0)`; a `ship_pad` ticket on the destination pad (radius `chunkTicketRadius`); `PlanetHubs.ensure` starts for a planet's first visit | `flight_phase` 0; engine spool, dust gusts, ramp up, struts in |
| Climb (`climbTicks`) | rises `climbHeight` blocks on an ease-in, turning to the nose's heading | phase 1; exhaust, rumble, shake (Vfx-scaled) |
| Jump (`jumpTicks`) | fuel and wear taken; the record's position moves to the destination pad; `moveAcross(ship, level, pad + 40, yaw)`; `IN_TRANSIT` | phase 2; `ship_jump` out (a thump, stars stretching) |
| Hyperspace (`hyperspaceTicks`) | waits for the destination chunks to load and tick (up to `hyperspaceMaxWait` more; else it lands above the pad's column as found) | phase 3; the `hyperspace` effect starts the overlay and its hum |
| Descent (`descentTicks`) | `LANDING`; the pad is re-checked (`Pads.landing`: a blocked pad shifts to the nearest clear spot); an ease-out to the pad, struts down at 6 blocks | phase 4; the overlay ends, `ship_jump` in, the descent whine, dust |
| Settle (`settleTicks`) | `ShipRules.settleState` (`DISABLED` wins over `GROUNDED`); riders dismount beside the ramp (each spot tried on the first ground found from above, never on a beacon or pad terminal and never below that ground); `QuestHooks.onPlanetArrived(rider, planet, first)` for a planet; the ticket lingers `ticketLingerTicks` | phase 5, then −1; ramp, hiss, the arrival banner |

- Riders are locked in from the Spool to the end of the Settle (the mixin, plus `CourierShuttle.locksRiders`). They
  take no damage except `/kill` (`ShipEvents.allowDamage`) and their Force powers are locked (`TravelLocks`, the
  lead's). Only riders travel: a crew member left on the pad stays behind, and the nav console warns "Crew aboard:
  1 of 2".
- The sequence cannot be cancelled. Failsafes: an exception in a flight's tick settles it where it is (logged); a
  destination world that disappears lands it back at its origin; a jump that does not happen lands back at the
  origin.
- **Camera**: `ShipCamera` gives each rider a transient `CAMERA_DISTANCE` modifier (`oldrepublic:ship_camera`,
  `cameraDistance`), removed on dismount and by a once-a-second sweep. `ShipCameraClient` switches a first-person
  rider to third person for the Climb and Descent when `ClientConfig.shipCamera` is on, and restores the camera at the
  Settle. A camera the player changes mid-flight is left alone.
- **Packet order across the jump**: `flight_phase(JUMP)`, `ship_jump` out, then `moveAcross` (the client respawns
  into the new level behind a `LevelLoadingScreen`), then `flight_phase(HYPERSPACE)` and `hyperspace` (intensity 0).
  The overlay starts on whichever comes first and is also drawn over the loading screen. Its hum starts on the
  `hyperspace` effect, after the respawn, so the level change does not stop it. At the Descent, `hyperspace`
  (intensity 1) ends the overlay once the client's level is ready.

## 6. Destinations and pads (4.5)

- `home`: the owner's landing beacon pad if set, else the ship cradle (the hulk's `HEART`), else the world spawn
  (12.2). The ship faces away from the beacon post.
- `greenwatch`, `proving`: the hub's four bays (`PlanetHubs.padBays`). A landing ship takes the first bay with no other
  ship within a few blocks; with all four taken it lands on the overflow rings (`overflowRings`, `MeetingSites.find`
  rules). A claim lapses after `bayLapseTicks` with no owner or crew near the ship (in memory; after a restart every
  ship counts as attended again).
- `hunting_camp`: a clearing `campPadOffset` blocks from the camp (W2's `PlanetSites.find`), toward the hub.
- Unlocks live in `TravelData.unlocked` (W2 calls `TravelService.unlock`; the owner gets the "Destination unlocked"
  toast). `home` is always unlocked.

## 7. Landing beacon, recall, tow, restore (4.8, 4.9)

- Placing a `landing_beacon` checks the four sides of the post (the placer's side first) for an `beaconArea` square
  with `MeetingSites` rules: the home world, natural dry ground, slope at most `beaconMaxSlope`, nothing built, open
  sky. A refusal says which rule failed (`ship.oldrepublic.beacon.<reason>`), plays the refuse blink and gives the
  beacon back. A beacon that is set binds to its owner and pad, sets `TravelData.homePad`, and lays `ship/beacon_pad`
  over the area: the ground fitted level (`RuntimeSites.adaptTerrain`), then a ground layer of stone and andesite
  flags with gravel joints and a coarse-dirt margin. Every block is in `#story_ground` and none is in
  `#built_blocks`, so the same area passes the check again. The pad is not laid when `beaconArea` is configured below
  11, since only the checked area is known to be free.
- One per player: a new beacon moves the home pad, and the old one pops as an item: at once if its chunk is loaded,
  otherwise when its own block entity next checks (`LandingBeaconBlockEntity`, every few seconds). Only the owner
  can break it; explosions and pistons cannot.
- **Recall** (`TravelService.recall`, rule `ShipRules.recall`): the beacon used with a cell, the pad terminal, the hub
  host (`ship:recall_free`, once a day) and the spacer (`ship:tow`). Refused when there is no ship, it is a hulk, it
  is flying, someone is aboard, it is already at that pad, or there is no cell. A `DISABLED` ship is **towed** instead,
  free, once a day: it arrives at the jump minimum. A paid recall costs the cell and the normal wear. A loaded body
  takes off with the usual Spool and Climb. An unloaded one vanishes and appears over the pad for the Descent.
- **Restore** (`ship:restore`, the beacon without a cell, `/oldrepublic ship restore`): a new body from the record at
  its last pad (or home, when that world is gone) when the body is missing. With "only if missing", nothing changes
  when the body turns up once its chunk loads.

## 8. Saved state, multiplayer, edge cases (12)

- `ShipRegistry` is server-only and never synced. `TravelData` is the lead's (persistent, kept on death, owner-synced).
  `TravelVisits` (`oldrepublic:travel_visits`, an `Integer` mask, persistent, kept on death, not synced) gives
  `onPlanetArrived` its `first` flag. **Note for 12.1:** this attachment and the socket and paint bits in
  `ShipRecord.upgrades` are additions to the saved-state table.
- **Restart mid-flight** (`Flights.recover` at `SERVER_STARTED`): a record still holding a `flight` settles before the
  jump at its origin with nothing taken, and after it at the destination (the jump already paid). Its state is then
  set as at the Settle, and `Ships.restore(only if missing)` puts a body on that pad once its chunk loads (the body
  caught in the air moves there itself, or discards itself if a new one was placed first).
- **Disconnect aboard** (`ServerPlayerEvents.LEAVE`): the rider is put off before the save and set down beside the
  pad of the world they are in, with slow falling if the ship was in the air. **Join**: a rider is never logged in
  aboard. Spec 4.9 names `DISCONNECT`; `LEAVE` is the Fabric event that runs while the player is still in the
  world, before the save.
- **Death aboard**: vanilla dismounts; the rider respawns as 12.2 says. The camera modifier is removed on respawn.
- **Crew**: up to `ShipRules.crewLimit`, added from players within `crewAddRange`. Joining another crew leaves the old
  one. Removing a member puts them off the ship at once. Only the owner fits, fuels, launches or changes the crew. A
  crew member sees the Status tab, read-only.
- **No duplication**: the hold is the record's (section 9); the body's UUID rule; tickets and flights live in memory
  and are cleared on server stop.

## 9. The ship screen and the destination map (4.10, 11.3)

`ShipMenu` (272 × 204, the workbench's durasteel style) shows the hold as 36 slots plus the player's inventory, on the
Hold tab only. Slots past `ShipRules.holdSize` are locked behind a shutter. The hold's truth is the record: every
change is written back at once, and a change made elsewhere (credits paid from the hold, 7.7) is read back every
tick. A crew member's menu holds an empty, locked hold.

`ShipScreen` tabs:

- **Status**: state, hull, fuel, seats, hold, crew, and the four upgrades with Fit and Remove.
- **Repairs**: the four sockets while a hulk. Afterwards, the hull and how to weld it.
- **Nav**: a row per destination (name, pad, and the refusal on a dimmed row), the warnings (board first, hull, crew
  aboard) and Launch. The **Chart** toggle swaps the rows for the destination map: a dark star chart
  (`textures/gui/destination_map.png`) with the frontier's star and the two worlds as soft lit points on faint dotted
  lanes. Locked points are dim; hovering shows the description, the pad and the reason. Choosing a point is the same
  as choosing a row.
- **Hold** and **Crew** (members with aboard/ashore/away, nearby players with Add, Remove).

Every value comes from `ship_status`; every button sends `ship_action`, which the server checks again (the sender
must have this menu open). Notices (`NOTICE_REPAIRED`, `NOTICE_UNLOCKED`) go to `ShipToasts` alone, so they never
overwrite an open screen's view.

## 10. HUD, overlay, toasts, banner (11.2)

- **Flight HUD** (`FlightHud`): a cockpit strip at the top during the take-off and landing: destination, phase,
  altitude to the pad, hull bar, fuel pips and a phase line. It slides in at the Spool and fades under the overlay.
- **Hyperspace overlay** (`HyperspaceOverlay`): streaks stretching into a blue-white tunnel with a slow pulse
  (flashing off: no pulse; Reduced preset: fewer streaks), a vignette, and the HUD fading. It is drawn over the level
  loading screen too. It ends after the Hyperspace phase or when the level is ready, whichever is later, and always
  ends (off the ship for 100 ticks, a 600-tick safety, disconnect). With `ClientConfig.hyperspaceOverlay` off, a plain
  dark fade covers the same moments.
- **Toasts** (`ShipToasts`): "Ship repaired", "Destination unlocked", in the third toast row at the top right (under
  the set-bonus and crafting rows).
- **Banner**: the destination's name at the Settle (`Banners.show`). W2's planet arrival banner queues behind it.

## 11. Effect events (13.3)

| Event | Target | Variant / intensity | Look and sound |
|---|---|---|---|
| `ship_engine` | tracking, the ship | 0 idle, 1 spool, 2 climb, 3 descent, 4 settle | nacelle exhaust by phase, dust gusts, the spool, climb, descent and land sounds, ramp |
| `ship_jump` | tracking, the ship | 0 out, 1 in | exhaust pulled into a streak ahead of the ship (out) or trailing behind it (in), `jump_out` / `jump_in`, a shake for riders |
| `hyperspace` | the rider | intensity 0 starts, 1 ends | the overlay and the `ship.hyperspace` loop |
| `ship_repair` | around | 0 to 3 a socket, 4 a weld, 5 stand up, 6 a cell fuelled, 7 an upgrade | sparks (`spark_shower`), smoke, steam from the jacks at the stand-up, an exhaust puff at the filler; `fit_part` / `weld` / `power_up` / `ramp` |
| `ship_damage` | tracking, the ship | intensity 1 − hull/100 | a burst of sparks and smoke at a nacelle, the `damaged` crackle, a shake for riders |
| `landing_beacon` | around | 0 set, 1 refuse, 2 recall | lamp blinks (one steady glow with flashing off), `beacon.*` sounds |

Loops (`ShipLoop`): idle (`ship.idle`) while the engines run, the crackle (`ship.damaged`) at `WEAR_FAILING`, and the
rider's hyperspace hum. They fade in and out and stop at once when the ship is gone, the level changes or the client
disconnects. No effect draws a shape on the ground; dust is blown outward in uneven puffs.

## 12. Balance (14.3)

`ship`: `tankCells` 4, `tankCellsUpgraded` 6, `cellsPerJump` 1, `wearPerJump` 5, `wearReinforced` 3, `jumpMinHull` 20,
`damageFxHull` 40, `sabotageHull` 10, `hullPlates` 12, `plateRepair` 5, `plateRepairSkilled` 8, `plateRepairCooldown`
10, `spacerRepairPerPoint` 6, `starterCells` 2, `cradleRange` [32, 64], `crewAddRange` 16, `seats` 2, `seatsUpgraded`
3, `hold` 27, `holdUpgraded` 36, `cameraDistance` 8.

`travel`: `enabled`, `combatLock`, `combatLockRange` 16, `combatLockTicks` 100, the six phase lengths (40 / 60 / 10 /
80 / 80 / 20), `climbHeight` 40, `hyperspaceMaxWait` 200, `padBays` 4, `bayLapseTicks` 24000, `overflowRings`
[40, 48, 56] (Milestone 6: [64, 72, 80]), `campPadOffset` 48, `chunkTicketRadius` 3, `ticketLingerTicks` 200,
`beaconArea` 11, `beaconMaxSlope` 2, `freeRecallPerDay` 1, `freeTowPerDay` 1.

`sabotageHull` and `spacerRepairPerPoint` are for W2's sabotage and spacer (section 15).

## 13. Commands (testing, 4.11)

`/oldrepublic ship give <player>` (a repaired, fuelled ship beside them; hold, crew and upgrades kept), `hulk`, `repair
[amount]`, `fuel [amount]`, `hull <value>`, `travel <destination> [instant]` (the player aboard), `recall` (free, to
this planet's hub or home), `restore`, `unlock <destination|all>`, `info`.

## 14. Tests and API status

`TravelGameTests` (17.1, template `oldrepublic_test:arena_24x7x24`): each spec test checks its pure rule in
`ShipRules` and then the live path in the arena: instant command flights to a real beacon pad (every phase one tick),
recalls and tows with the body unloaded, real item use on the beacon, the socket and crew buttons
(`ShipActions.fitSocket`, `addCrew`, `removeCrew` are public for this), the damage events' invokers, and the
`Flights.recover` sweep for the restart rules. Riders are `M3TestPlayers.listed`; each test removes only its own
owners' records when it ends, so parallel tests keep theirs.

`m4/TravelClientSteps` (17.2) builds a grass stage 60 blocks above the player and runs the whole loop there: the hulk
command (30 `hulk_tarp`), two sockets fitted and the Repairs tab (31 `hulk_sockets`), repair and fuel by command, a
beacon placed with a real use (37 `landing_beacon`), boarding and the screen with real uses (36 `screen_<tab>`, 38
`destination_map`), Launch from the Nav tab and the flight home with real ticks (32 `takeoff_dust`, 33 `climb`, 34
`hyperspace`, 35 `landing`), and the landed ship at 15 hull (39 `ship_damage`). It asserts the states, the flight
camera, the dismount and one jump's cost, and puts back the stage, the ship, the cradle's site record and the player's
travel data. The `// API-CHECK:` points, probed by `tools/api-probe/m4_w3.txt` (run 39097913804):

1. `TravelTickets`: `TicketType` is registered in `BuiltInRegistries.TICKET_TYPE` with the `(timeout, flags)`
   constructor and the `FLAG_*` constants. Confirmed (`TicketType#register`).
2. `ShipCamera`: players carry `Attributes.CAMERA_DISTANCE` (null-safe either way). Confirmed
   (`LivingEntity#createLivingAttributes` adds it).
3. `CourierShuttle`: a plain entity drives its own `InterpolationHandler` from `tick` (as boats do). Confirmed
   (`AbstractBoat#tick` calls `interpolation.interpolate()` itself).
4. `CourierShuttleRenderer`: the parameter order of `SubmitNodeCollector#submitText`. Open: the probe confirms
   the signature `(PoseStack, float, float, FormattedCharSequence, boolean, Font.DisplayMode, int, int, int, int)`,
   not what each of the four ints means.
5. `ShipScreen`: `extractLabels` coordinates are relative to `leftPos`/`topPos`. Confirmed (vanilla's
   `extractLabels` draws at `titleLabelX/Y` and `inventoryLabelX/Y`).

## 15. Requests outside W3's files

These are in the W3 report. (W1's hub templates already place `pad_terminal` at `PAD_TERMINAL`, and W2's
dialogues already use `ship:recall_free`, `ship:tow` and `ship:state`.)

- register `TravelGameTests`;
- the `#minecraft:mineable/pickaxe` tag entry for `landing_beacon`, and `#oldrepublic:built_blocks` entries for
  `landing_beacon` and `pad_terminal`;
- the 12.1 table notes above;
- the Refusal values `HERE` and `UNAVAILABLE` appended to the 15.1 contract listing;
- W2's sabotage reads `ShipBalance.get().sabotageHull` and goes through `Ships.update`.
