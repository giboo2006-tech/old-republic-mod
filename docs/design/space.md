# Space design, Milestone 5: orbits, ship combat, encounters and the Silent Spindle (W2 space)

Owner: W2 (space). Covers spec `docs/MILESTONE_5.md` section 5 (the orbits and how they look, the gunner seat's weapon,
weapons and heat, missiles, shields and hull damage, the four fighters and the generator, encounters and co-op, the
Silent Spindle, the bounty fight, the space objectives' runtime and the combat HUD), the orbit rain rule of 3.5, the
Spindle Capacitor's effect (11.2), the combat HUD and space sky rows of 13.2, the W2 rows of 15 (budgets, the
`hull_debris` particle, nine effect events, the space sounds), the `space` balance section (16.4), the W2 ids and
payloads of 18, and the W2 tests of 19.1 and 19.2. The ship itself, its flight, the helm, landings, jumps and the
disable rescue are W1's; W4's mission service drives the objectives, pays the bounty and owns the blockade's story
(`ChapterFiveService`); W3 owns Ghessar's surface sky. The orbit dimensions and their type were written by the lead in
Phase 0; W2 filled the biome, the feature and everything that happens in them.

Status legend for APIs: **[V]** verified against the 26.2 dumps, the probe digests or code that compiled before,
**[U]** unverified, marked `// API-CHECK` in the code and listed in the W2 report for the lead's probe.

## 0. Implementation status

Everything in section 5 is built, including the four cut-order items in W2's scope (2 the gunner's turret, 3 the
Silent Spindle, 7 the bounty fight, 8 concussion missiles), except one thing: **Ghessar's twin sun is cut in orbit as
on the surface**. Spec 5.1 says the orbit's goes with cut 1, and W3 cut the surface's (`docs/design/planets_m5.md`), so
`orbit_ghessar` shows the planet below and the nebula like every other orbit. Differences from the spec:

- **Battles never cross** (`ShipBolt.lands`): enemy and allied fire lands only on the ships of its own encounter (its
  owner and the co-op crews that joined it); fire that belongs to no encounter (a fighter from a spawn egg) lands only
  on armed ships. One player's fight never hurts a passer-by's ship or another player's fighters.
- **The gunship adopts its generator into its encounter**, so the generator's destruction plays its comm line and its
  kill counts for the encounter like any other.
- **A run's patrol shadows the ship, neutral** (5.9): once the ship is under way it arrives ahead of it and follows; its
  fighters fill the scan meter, and only a player's weapon turns them hostile (which ends the scan for this attempt).
- **The log terminal and the vault are vanilla blocks** at their anchors (an observer at `SPINDLE_LOG`, a chest at
  `SPINDLE_VAULT`) whose use `SilentSpindle.onUse` takes over through `UseBlockCallback`; 18.1 names no new block. The
  vault's roll goes straight to the player; the chest itself stays empty.
- **Nothing drops in space**: kill credits and vault items that do not fit the player's pockets go into their ship's
  hold (`SpaceRewards.give`, `ShipHolds`).
- **The patrol bonus** (`patrolBonusXp`, 300) goes to the owner and to every co-op pilot who destroyed one of its
  fighters.
- **Extra balance keys** beyond 16.4's table: `gunship.turretSpread` (3°), `gunship.patrolRadius` (40),
  `clearGroupSize` ([3, 6], the spec's "3–6 fighters"), `spindleDroidRange` (64, the spec's "within 64 blocks"), and
  the fighters' `burst`, `missileInterval` and `missileDamage` fields (the spec's "in bursts" and "a missile every 200
  ticks for 15"). `pvpShipDamage` exists but is reserved and never read: 5.4 rules out PvP in space whatever it says.
- **Test hooks** (public, each documented as for game tests): `SpaceEncounters.startBlockadeAt`, `startPatrolAt`,
  `joinCoop`, `snapshot`, `lastEnd`, `lastEndTime`; `SpaceMissions.stepForTest`, `movePointForTest`,
  `scanPatrolForTest`, `lastResult`; `ShipCombat.disabled`; `ShipBolt.strikeOf`; `SilentSpindle.recordIn`,
  `placeNow`, `openVault(p, pos, day)`; `OrbitFloor.catchPlayer`; `client.space.SpaceCrew.status()`. An encounter
  started by a test never ends as Peaceful (another test may have set the difficulty).
- **Lead request applied (W4's factions):** `SpaceMissions.start` salts a bounty's seed with the fence's ciphers spent
  at its board (`FactionStanding.ciphers`), so a cipher rerolls the day's ace and point (7.1); with no cipher the seed
  is unchanged. `missionOf(Entity)`, `start`, `cancel`, `blockadeRunning` and `startBlockade` keep their signatures.

## 1. The orbits (5.1)

Five data dimensions, `orbit_home`, `orbit_selvaren`, `orbit_varkhast`, `orbit_ghessar` and `orbit_tyrvann`, share the
dimension type `oldrepublic:orbit` (Phase 0: black sky and fog, the overworld's stars at full brightness, the sun fixed
at `sun_angle` 325 and the moon at `moon_angle` 78, no time animation through the empty `#oldrepublic:orbit` timeline
tag, `has_ceiling` so no weather, no beds or anchors, `music.space`) and the void generator `orbit_void`. W2's data:
the fixed biome `orbit_field` (black sky and fog attributes, no precipitation or spawns, the `ambient.space` loop, the
asteroid feature in step 0) and the feature `oldrepublic:asteroid` with its placed feature (one try per two chunks,
y 80 to 280).

**Asteroids** (`AsteroidFeature`): a lumpy cluster 3 to 12 blocks across, a main body and a few smaller lumps, each a
noisy ellipsoid; it writes only within 12 blocks of its origin and draws every random number from the feature
context, so a seed fills the same fields every time. The feature keeps a share of the tries so each orbit gets one
cluster per `asteroidDensity` chunks (home 3, Selvaren 4, Varkhast 2: the Ash Ring, Ghessar 3, Tyrvann 3). Palettes:
home and Selvaren stone, andesite, tuff, basalt and deepslate; Varkhast blackstone, basalt, deepslate, tuff and
andesite;
Ghessar stone, andesite, sandstone and white calcite standing in for salt; Tyrvann basalt, smooth (scorched) basalt,
deepslate and tuff. Nothing is placed within 40 blocks of the arrival column at (0, 0) or within 56 blocks of the
Spindle's centre. Ships collide with rock (W1, 4.4); ship weapons never break blocks.

**What the pilot sees** (`client.space.OrbitSky`, `SkyMaps`), all off with `ClientConfig.spaceSky`:

- **The planet below**: a sphere impostor drawn camera-relative in `LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES` [V]
  with the unfogged colour-only `debugQuads` type, between the render distance and 0.9 of the frame's far plane, so
  terrain, ships and the station cover it by depth and it covers the vanilla sun, moon and stars below the horizon.
  A polar grid of directions round the nadir (16 to 32 rings and 40 to 88 segments by preset) meets the sphere; the
  point met gives the colour from the planet's equirectangular map (scrolled 30° across the orbit's 2,048 blocks and
  spinning slowly), the sunlight from the fixed sun (a dim night side), and haze thickening toward the limb, with a
  thin band of air past the rim. The height above the atmosphere line (y 64) sets its size: at y 100 the rim is 15°
  below the horizon; at the line it fills the view below the horizon. A missing map falls back to two colour bands.
- **The five worlds**: the home world blue and green with cloud swirls; Selvaren green under mist with silver lakes;
  Varkhast rust and ash with glowing fissures; Ghessar amber dunes with white salt pans and dark canyons; Tyrvann dark
  green under a storm whose flashes flicker across the disc (one every 25 ticks on average at Standard, 50 Reduced, 18
  High, 12 Cinematic; a slow, dim swell instead with `flashingEffects` or `stormFlashes` off).
- **No twin sun**: Ghessar's is cut with cut 1 (see 0); its orbit has only the vanilla sun and moon.
- **The nebula**: a faint violet and blue band with dark lanes across the stars, climbing from the north-east horizon
  over the zenith's south side, at half alpha at most.

**No rain** (3.5): every client tick in an orbit sets the client level's rain and thunder to 0, whatever the
overworld's weather packets say (they reach every dimension). The client steps check it after `/weather rain`.

**On foot** (`OrbitFloor`): a player below `orbitCatchY` (-56) in an orbit is caught: put back aboard their ship (their
own, else the one whose crew they are) when it is in that orbit with a free seat, otherwise set down beside it at its
last landing spot by the 4.10 cross-level move under a chunk ticket, drifting with slow falling while the chunk loads;
a player with no ship goes to the home world's spawn.

## 2. Weapons, heat and missiles (5.2, 5.3)

Clients send only their triggers: `ShipWeaponInput` reads the attack and use keys every client tick while the local
player pilots (W1's `Piloting.isPiloting`) or mans the gunner seat (`SpaceCrew.isGunner`: W1's seat rule
`ShipBody#gunner`, the first rider who is not the owner, on a flying ship with cannons), and sends `ship_fire(weapon,
pressed)` on each change; a screen opening, the role or the level changing releases a held trigger. Vanilla's clicks are
cancelled for the pilot by W1's mixin and for the gunner by `mixin/space/MinecraftGunnerMixin`. `ShipWeapons` fires
while a trigger is held, from the ship's server position and rotation, and ignores anyone not in the role. Heat, the
alternating muzzle and the lock are per ship (by owner), in memory.

| | Cannons (pilot, attack) | Turret (gunner, attack) | Concussion missiles (pilot, use) |
|---|---|---|---|
| Needs | `CANNONS` | `CANNONS` and a gunner (`ShipBody#gunner`, W1) | `MISSILES` and `concussion_missile` in the hold |
| From | the ship class's two muzzles in turn (`ShipBody#cannons`, W1's points, where the model draws them) | the turret's pivot (`ShipBody#turret`) plus 1.2 blocks along the aim | the rack (`ShipBody#rack`) |
| Aim | the ship's nose | the gunner's server-side look, clamped from 10° below the horizon to straight up (`ShipWeapons.turretAim`) | homing on the locked target |
| Rate, damage | every 5 ticks, 6 | every 7 ticks, 5 | one per 20 ticks, 30 and 10 splash within 3 blocks |
| Heat | +7 a shot; cools 20 a second after 10 ticks without firing; at 100 locked for 60 ticks, then cooled to 40 (`SpaceRules.Heat`) | its own heat, the same rules | none |

- **Bolts** (`ShipBolt`, an `AbstractHurtingProjectile` [V]): straight at `boltSpeed` 4 plus the ship's own velocity
  (measured on the server from the ship's movement, since the pilot's client moves it), for `boltRange` 96, the whole
  move swept every tick so a fast target is never skipped. A bolt never hits the ship or fighter that fired it, nor
  anyone riding it, for its whole life; it never hurts players, other mobs or blocks (a block stops it with sparks).
  Its velocity is synced exactly, so a bolt flies at its speed on every client. The gunship's box is as wide as its
  length, so its hull is tested on its own turned box (`BlockadeGunship.hullHit`) before the sweep.
- **Teams**: the players' bolts and the allied fighters' hit ship targets (`#oldrepublic:ship_targets`) of the other
  side; the hostile fighters' and the gunship's hit players' ships (through `ShipCombat.hitShip`) and allied fighters;
  all inside their own encounter (0). Colours: a Sith owner's bolts red, a Jedi's green-white, an Independent's yellow
  (red before a path); the Hymn's and the gunship's violet, the kajidic's orange, the clan's amber.
- **The damage** is `oldrepublic:ship_weapon` caused by the firing ship or fighter, never a player, so the M1 kill
  reward never pays twice; `SpaceRewards` pays the shooter recorded on the bolt.
- **The lock** (`ShipWeapons.lockable`, `SpaceRules.lockHeld`): holding use picks the ship target nearest the nose
  within 20° (measured to its box's centre) and 80 blocks; held in the cone for 30 ticks it is locked (`missile_lock`
  0 with the progress, then 1); leaving the cone or dying loses it (2). Releasing fires one missile from the hold at a
  held lock; a release before the lock fires nothing.
- **Missiles** (`ShipMissile`): 1.6 blocks a tick, turning at most 6° a tick, falling apart after 100 ticks; a hit on
  a target of its team, and a player's missile also hurts the other ship targets within 3 blocks for 10. A raider's
  missile at a player's ship keeps that pilot's incoming warning (`missile_lock` 3, then 4) on until it ends, whatever
  ends it.

## 3. Shields, hull and the disabled ship (5.4, 11.2)

`ShipCombat` keeps each flying ship's shield in memory: 40 points with `SHIELD` (60 on an interceptor), +20 with the
Spindle Capacitor (`PART_SPINDLE_CAPACITOR`, which needs `SHIELD`). A hit drains the shield first; what it does not
absorb comes off the hull through `Ships.update(owner, r -> r.withHull(...))`, whole points, fractions carried to the
next hit. The shield recharges `shieldRecharge` (4) points a second, ×1.5 with the capacitor, after `shieldDelay` (60)
ticks without a hit; `ship_shield` plays when it falls (0), starts to recharge (1) and is full again (2). Only a ship at
the helm (`FLYING`) takes hits; a ship starts every flight with a full shield. `underFire` (hit within 200 ticks) is
W1's `UNDER_FIRE` jump refusal. At 0 hull the ship is disabled through W1's `Ships.disable` (the tumble, then the free
rescue home) and every encounter it was in ends (`DISABLED`; the blockade resets). The body's M4 invulnerability stays:
nothing else hurts it, and its riders take no damage while it flies.

## 4. The starfighters, the gunship and the buoy (5.5, 5.9)

`Starfighter` is a gravity-free `Mob` flown the Phantom way: it steers its velocity toward a point (its own fixed look
control, since vanilla's resets the pitch) and loops through three phases: **circle** its target, **run** at it, and
**break** away close in. It fires only while its nose is within `fighterAimCone` (10°) of where the target will be (the
lead from the bolt's speed), its bolts spread `fighterSpread` (2°). Health is a synced recharging shield (5 a second
after 60 ticks without a hit) and the hull (the mob's health). Only `ship_weapon` damage hurts it, with no hurt
cooldown, so two cannons and a turret all land. It is never saved; its encounter decides when it vanishes, and a
destroyed fighter is gone at once (the `ship_destroyed` burst takes its place). Its side is synced: **hostile** fighters
attack the encounter's armed player ships and allied fighters; **allied** fighters attack hostile ship targets;
**neutral** fighters (a run's scanning patrol, the Peaceful pass) shadow their ship and turn hostile when a player's
weapon hits them.

| Fighter | Look | Speed | Shield / hull | Guns | Flight (circle radius, height, break distance) |
|---|---|---|---|---|---|
| `hymn_interceptor` | slim black hull, needle nose, ringed engine, swept blade wings with tall tips, violet trim; a singing engine | 1.6 | 10 / 20 | 4 a bolt, bursts of 3, 3 a second | 44, 22 above, 24: dives from high above |
| `kajidic_raider` | squat, boxy, patched gunmetal with ochre bands, wingtip gun pods, twin engines, a belly rack | 1.2 | 20 / 40 | 5 a bolt, bursts of 2, 2 a second; a missile every 200 ticks for 15 after a 30-tick lock the pilot hears | 34, level, 18: strafes from the side |
| `hask_fighter` | lean clan-iron hull, forward-swept wings with down-turned tips, ember and bone markings | 1.4 | 15 / 30 | 5 a bolt, bursts of 2, 2.5 a second | 40, 10 above, 22: trios holding formation on a leader |
| `blockade_gunship` + `gunship_generator` | a slab-sided dark hull 24 blocks long, violet trim, four twin turrets, a belly bay, the generator on the spine | 0.3 | generator 80; gunship 400 | four turrets, 3 a bolt, 2 a second each within 64 blocks, 3° spread, leading the target | a slow orbit (radius 40) round its post |

A bounty's **ace** has its shield and hull doubled and fires twice as fast (`aceMultiplier`), and wears its name within
96 blocks. **The gunship** takes no damage while its generator stands (a separate small entity it places on its spine
every tick); it launches two interceptors from its bay every 600 ticks (the doors open 30 ticks before, the first
launch 240 ticks after it appears) while the generator stands and fewer than six are alive. Its death: three blasts
along the spine at ticks 1, 14 and 27, the end blast at 40 (the hull breaks in two and drifts apart), gone at 80. With
`setPassive` (the Peaceful pass) it neither fires nor launches.

**Kills** (`SpaceRewards`): XP to the shooter (interceptor 60, raider 80, Hask fighter 70, gunship 600) with the M1
anti-grind fatigue, and credits straight to the shooter (raider 15, the others 10), then the story's `onShipKill` and
the encounter's progress. The **nav buoy** (`nav_buoy`) is a run's destination: a slowly turning marker that pings every
two seconds (`nav_buoy` 0) and turns green when reached (1); it cannot be hurt and is never saved. Every ship is in the
`MISC` category, so Peaceful never removes the scripted pass.

## 5. Encounters (5.6)

`SpaceEncounters` holds one in-memory encounter per owner (`Encounter`: its type, ships, joined players and comm
lines). A restart, the owner leaving the orbit, logging out or being disabled ends it and its ships vanish; the end
reasons are `CLEARED`, `OUTRUN`, `LEFT`, `LOGOUT`, `DISABLED`, `BROKEN`, `PASSED`, `MISSION`, `REPLACED`, `PEACEFUL`
(the world turned Peaceful) and `RESET`. Every point W2 places in an orbit lies within 1,920 blocks of (0, 0).

**Patrols.** While an owner pilots an armed ship in an orbit, every `patrolCheckTicks` (2,400) of such flight a patrol
finds them with `patrolChance` (25%), never within `patrolGapTicks` (1,200) of their last encounter and never on
Peaceful. It arrives `patrolDistance` (120) blocks away, hails and attacks; it ends when every fighter is destroyed
(the bonus), when the ship is `patrolLeaveRange` (200) from all of them, or when the ship leaves.

| Orbit | Patrol (`SpaceRules.patrolKind`, `patrolSize`) |
|---|---|
| Home, Selvaren, Varkhast | 2–4 kajidic raiders |
| Ghessar | 2–4 kajidic raiders; 3–4 for a player at Hostile kajidic standing |
| Tyrvann | Hymn interceptors until the owner's blockade is broken; afterwards Hask fighters if the clan is Hostile, else raiders |

**The blockade** (6.5): `SpaceEncounters.startBlockade(owner)` from W4's story when the owner's ship arrives in
Tyrvann's orbit during `BLOCKADE`. The gunship and its generator 150 blocks ahead; wave 1 (3 interceptors), then wave
2 (4) when wave 1 is down; the gunship's launches throughout. If `ChapterFive.clanStands(owner)`, three Hask fighters
in its escort break formation at the start and fight on the player's side. Destroying the gunship breaks it
(`QuestHooks.onBlockadeBroken`; its pilots flee for 160 ticks and the encounter closes); a disabled ship resets it. On
Peaceful, or with flight switched off (`flight.enabled`), it is the **scripted pass**: the interceptors hail and turn
away after 60 ticks, the gunship holds fire, and the way opens after `peacefulPassTicks` (200).

**Co-op.** Other players' armed ships within `coopRange` (128) join the encounter: its fighters attack them too, their
kills count for it and pay them; only the owner's chapter, contract or bounty moves.

## 6. The Silent Spindle (5.7, 11.4)

One shared site per server at `spindlePos` (420, 150, -380) in `orbit_home`: placed when a player first comes within
`spindlePlaceRange` (400). The chunks of its box are loaded (generated where needed), the template `station/spindle`
is stamped with `RuntimeSites.stamp` (no terrain adaptation) and recorded in `WorldSites` as `DERELICT_STATION`.

The template (64 × 40 × 48, nothing buried; `tools/assets/gen_structures_space.py`): an octagonal spine 58 blocks long
with decks one level high, split by bulkheads into the reactor room (a dead core in an iron cage, half its bulbs lit),
the hydroponics bay gone to black vines (with an open floor hatch over the void), the ring junction with the vault
behind bars, the central hall with the airlock corridor south to the landing deck, and the command deck (consoles, the
log terminal under the viewport, the listening dish on the roof). Outside: the listening ring on four spokes, and the
landing deck on the south side (23 × 21, clear to the sky; a courier or two interceptors land there by hand). Anchors:
`SPINDLE_DECK`, `SPINDLE_POST_1`–`4`, `SPINDLE_LOG`, `SPINDLE_VAULT`.

- **Droids**: once per in-game day, when a player comes within `spindleDroidRange` (64), a `security_droids` droid
  stands up at each post that has none, kept to its post (`SpawnerGroups.spawn`). The day is a global attachment.
- **The log terminal** opens W4's dialogue `spindle_log` through `Dialogues.open`.
- **The vault**: once per player per in-game day (`SpaceData.vaultDay`) a roll on `chests/spindle_vault` (2–3 alloy
  plates, a power core, a masterwork fragment and a 25% second) and the Spindle Capacitor's schematic roll
  (`LegendaryDrops.roll`, 10%, guaranteed on the 8th opening without it); a second use that day says it is empty.

## 7. Space objectives and bounties (5.8, 5.9)

W4's mission service starts each through `SpaceMissions.start` and W2 reports the end through
`QuestHooks.onSpaceObjectiveDone(p, missionId, success)`. The objectives in progress are saved (`SpaceData`, the
`oldrepublic:space` attachment, persistent and kept on death); their patrols and groups are encounters, so leaving the
orbit, being disabled or a restart takes those away and they come again on the next approach.

- **`space_run`**: a `sealed_cargo` goes into the hold at the start. In the run's orbit the buoy's point is chosen
  `buoyDistance` (600–1,000) blocks from where the ship first flies there and is shown on the HUD from anywhere in that
  orbit; the buoy is placed when the ship comes near. Once the ship is under way the patrol (Clan Hask's for
  `c5_cargo`, pirates otherwise) shadows it: one of its fighters within `scanRange` (48) with a clear line for
  `scanTicks` (60) fills the meter, which drains while none does (`space_scan`). A full meter fails the run; firing on
  the patrol turns it hostile and ends the scan; destroying or outrunning it ends it for this attempt. Reaching the
  buoy with the crate completes the run. Each Hask fighter destroyed on `c5_cargo` costs clan standing (W4's rule).
- **`space_clear`**: a marked point in one of the owner's unlocked orbits, seeded by the day (and the board's ciphers,
  0), shown on the HUD from anywhere there. Near it the group waits: 3–6 fighters, or a bounty's named ace and two
  wingmen. Destroying them all completes it, and a bounty rolls `gameplay/space_bounty` (2 sunstone or 2 stormglass
  shards; 5% a hymnstone shard) for the owner; W4 pays the rest. On Peaceful no fighters come, and reaching the point
  completes it.

## 8. The combat HUD (5.10, 13.2)

`client.space.CombatHud`, shown while the local player pilots or guns, beside W1's flight HUD; every number comes from
the server's `ship_combat_status` (every other tick, stale after a second so a lost packet never leaves it up):

- a panel on the left: the shield bar (blue), the heat bar of the seat's guns (the cannons for the pilot, the turret for
  the gunner) turning red with an overheat lock, and the missile count with the lock's state;
- corner brackets on ship targets within 160 blocks (red hostile, green allied, white neutral), sized to the target
  (the gunship to its hull); over the one nearest the crosshair its name and distance, under it its shield and hull;
- that target's lead pip (a diamond where to aim, from the bolts' speed and the guns' own mount on the ship) and the
  boresight of the seat's guns, so laying the boresight on the pip is a hit;
- the lock ring closing around the locked target (amber while locking, red and labelled once locked), with W2's tones;
- a red edge pulse and a warning line while a missile locks on or flies at the ship (a steady glow with flashing off);
- the scan meter under W1's flight strip (amber, red near full);
- the run's buoy or the bounty's group: a marker with its distance, or an arrow at the screen's edge;
- the encounter's comm lines as subtitles in the M4 bark style (`space_comm`): the speaker's name in their colour, then
  the line, low in the centre; hidden with the barks (`showBarks`).

## 9. Effects, phase by phase (15)

Every effect goes through `client.fx.Vfx` (counts scale with the preset; flashes turn steady with flashing effects off)
and through `network.EffectEvents` in multiplayer. Space has no air and no ground: no dust; smoke that thins fast
(12–17 ticks) and never rises; fragments that keep flying. Sounds carry (the films' convention): a loudness above 1
widens a sound's reach.

| Effect | Phase | Look | Sound |
|---|---|---|---|
| `ship_weapon` 0 cannons, 1 turret | each shot | a short warm-white muzzle glow riding with the ship (3 ticks), 2 muzzle sparks; the bolt is the renderer's streak (a white-hot core in two layers of glow in its colour, one tick of flight long, never trailing particles) | `space.cannon.fire` (two variants), `space.turret.fire` |
| `ship_weapon` 2 missile launch | launch | 3 puffs of smoke at the rack | `space.missile.launch` |
| | flight | the dark finned body rolling as it homes, a blue-white motor flame (steady with flashing off), 1 smoke puff every 2 ticks behind it | none |
| `ship_weapon` 3 overheat | 20 ticks | 6 thin smoke puffs from the barrels, following the ship | `space.cannon.overheat` |
| `ship_weapon` 4 fighter lasers, 6 gunship turret | each shot | a muzzle glow tinted by who flies it (Hymn violet, kajidic orange, clan amber; the gunship's larger and violet), 2 sparks | `space.fighter.fire` (the gunship's pitched down) |
| `ship_weapon` 5 fighter missile | launch | 3 smoke puffs | `space.missile.launch`, pitched down |
| `ship_hit` 0 shield | impact | one ripple of bent space (`air_ripple`, 1.4 to 4.5 blocks by damage, growing 2.6 times over 10 ticks) spreading over the struck side, 3 sparks; the pilot's camera jolts lightly | `space.bolt.hit_shield` |
| `ship_hit` 1 hull | impact | 8 sparks and 2 `hull_debris` thrown out of the hull with its drift; the pilot's camera shakes with the damage | `space.bolt.hit_hull` |
| `ship_hit` 2 block | impact | 3 sparks only | `blaster.impact` (existing) |
| `ship_shield` 0 down | the fall | a last ripple over the whole hull that tears into 8 sparks | `space.shield.down` |
| `ship_shield` 1 recharge | 20 ticks | one faint shimmer (a single ripple) crawling from stern to bow | `space.shield.recharge` |
| `ship_shield` 2 full | | one faint ripple | none |
| `missile_lock` 0 locking | while held | the HUD's ring closes, amber | `space.missile.lock_tone` loop, its pitch rising with the progress |
| `missile_lock` 1 locked | | the ring turns red and labelled | `space.missile.locked`; the tone holds, higher |
| `missile_lock` 2 lost | | the ring goes | the tone stops |
| `missile_lock` 3, 4 incoming | start to end | the HUD's red edge pulse and warning line (steady with flashing off) | `space.missile.warning` loop, start to end |
| `ship_destroyed` 0 fighter, 1 generator | the blast | one flash (a steady, dimmer glow with flashing off), 24 `hull_debris` tumbling outward, glowing hot then burning out to dark metal, 12 sparks; a camera jolt within 32 blocks | `space.fighter.explode`; `space.gunship.generator_down` |
| `ship_destroyed` 2 gunship blast | ticks 1, 14, 27 | a fighter-sized burst along the spine; its engines sputter | `space.fighter.explode`, pitched down |
| `ship_destroyed` 3 gunship end | tick 40 | a large flash and 60 `hull_debris`; the hull breaks in two and the halves drift apart until tick 80 | `space.gunship.explode` |
| `gunship` 0 bay opens | 30 ticks | the belly doors part (the renderer), a breath of venting exhaust | `space.gunship.launch`, low |
| `gunship` 1 launch | | two interceptors drop out of the bay through 10 puffs of exhaust | `space.gunship.launch` |
| `space_comm` | a line | a subtitle in the M4 bark style, the speaker's name in their colour (fades in over 4 ticks, out over 14) | `space.comm` (a radio crackle) |
| `space_scan` 0 scanning, 1 broken, 2 complete | while scanned | the HUD's scan meter | `space.scan` loop while scanning |
| `nav_buoy` 0 blink | every 2 s | the lamp blinks amber with the ping (steady amber with flashing off) | `space.buoy` |
| `nav_buoy` 1 reached | | the lamp turns a steady green, one green glow | `space.buoy`, higher |
| engines (client) | while near | the fighters' exhausts and the gunship's engines and running lights glow (a flicker, steady with flashing off) | `space.fighter.engine`, `space.hymn_interceptor.engine`, `space.gunship.hum`: entity-bound loops for the nearest six fighters within 56 blocks and the gunship within 96 |

Budgets at Standard (15.1) are met as listed. Loops stop (15.4): the lock tone when the lock is lost or fired; the
warning when the last incoming missile hits, misses or expires; the scan when it breaks or completes or the run ends;
the engines and the hum when their entity is removed or out of earshot; all of them on a dimension change and on
disconnect. Each cockpit tone also has a watchdog on the seat's status, so a lost event never leaves it playing.

## 10. Sounds: the final sound of each event

All 24 events (26 files with the two variants of the cannons and the fighters' guns) are placeholders made by
`tools/assets/sounds/space.py`; the briefs for the final versions are in `docs/assets/space.md`. In short: the cannons
a heavy, punchy bolt with a metallic spring and a recoil thump; the turret a lighter double zap; the fighters' guns
thinner, heard across the dark; the overheat a coolant hiss and falling beeps; the lock a steady train of seeker beeps,
the lock confirmed by a rising chirp; the warning two fast alternating tones; the missile a clunk, a crack and a
roaring rush; a shield hit a crackling fizz over a soft whump; a hull hit a clang, sparks and a dull thud; the shield
falling away through discharges, and coming back as a rising, brightening whine; the fighters' engines a rushing roar
under a twin whine, the Hymn interceptor's a singing, vowel-shaped wail; explosions in the films' manner; the gunship a
low beating drone; the comm a click, static and squelch; the scan two rising chirps a second; the buoy a clear bell.
`ambient.space` is the void heard from the cockpit; `music.space` is "Orbit", slow pads in D minor with distant bells.

## 11. Balance (`space` section, 16.4)

`space.SpaceBalance`, server side; the rules take an instance, so tests pass `new SpaceBalance()`. Defaults as in
16.4: `patrols` true, `patrolCheckTicks` 2400, `patrolChance` 0.25, `patrolGapTicks` 1200, `patrolSize` [2, 4],
`hostilePatrolSize` [3, 4], `patrolDistance` 120, `patrolLeaveRange` 200, `patrolBonusXp` 300, `pvpShipDamage` false
(reserved), `cannonInterval` 5, `cannonDamage` 6, `cannonHeat` 7, `boltSpeed` 4, `boltRange` 96, `turretInterval` 7,
`turretDamage` 5, `turretHeat` 7, `heatCoolPerSecond` 20, `heatCoolDelay` 10, `overheatLockTicks` 60, `overheatCoolTo`
40, `missileInterval` 20, `missileDamage` 30, `missileSplash` 10, `missileSplashRadius` 3, `missileSpeed` 1.6,
`missileTurn` 6, `missileLife` 100, `lockCone` 20, `lockRange` 80, `lockTicks` 30, `courierShield` 40,
`interceptorShield` 60, `shieldRecharge` 4, `shieldDelay` 60, `underFireTicks` 200, `capacitorShield` 20,
`capacitorRechargeMult` 1.5, `fighterAimCone` 10, `fighterSpread` 2, `fighterShieldRecharge` 5, `fighterShieldDelay`
60, `hymnInterceptor` {1.6, 10, 20, 4, 3 a second, bursts of 3}, `raider` {1.2, 20, 40, 5, 2, bursts of 2, missile every
200 for 15}, `haskFighter` {1.4, 15, 30, 5, 2.5, bursts of 2}, `gunship` {0.3, 400, 80, 3, 2 a second, 64, spread 3,
600, 2, 6, patrol radius 40}, `killXp` {60, 80, 70, 600}, `killCredits` {10, 15, 10, 10}, `blockadeDistance` 150,
`blockadeWaves` [3, 4], `clanFighters` 3, `peacefulPassTicks` 200, `coopRange` 128, `scanRange` 48, `scanTicks` 60,
`buoyDistance` [600, 1000], `aceMultiplier` 2, `aceWingmen` 2, `clearGroupSize` [3, 6], `spindlePos` [420, 150, -380],
`spindlePlaceRange` 400, `spindleDroidRange` 64, `vaultSchematicChance` 0.1, `vaultSchematicPity` 8, `asteroidDensity`
{3, 4, 2, 3, 3}, `orbitCatchY` -56.

## 12. Ids, payloads, data and commands (18)

- **Entities** (`SpaceEntities`, none saved): `hymn_interceptor`, `kajidic_raider`, `hask_fighter`,
  `blockade_gunship`, `gunship_generator`, `ship_bolt`, `ship_missile`, `nav_buoy`; spawn eggs for the three fighters
  (testing aids; a fighter from an egg sets itself up hostile). Items `concussion_missile` and `sealed_cargo` are the
  lead's (`M5Items`); W2 writes their assets and lang.
- **Data**: damage type `ship_weapon` (no exhaustion, never scaled), tag `#oldrepublic:ship_targets` (the five ship
  targets, each `required: false`), loot `gameplay/space_bounty` and `chests/spindle_vault`, template
  `station/spindle` with its sidecar, the `orbit_field` biome and the `asteroid` feature, particle `hull_debris`.
- **Payloads** (`SpaceNetwork`): `ship_fire(weapon, pressed)` C2S (weapon 0 the attack button, 1 the use button);
  `ship_combat_status(role, shield, shieldMax, cannonHeat, cannonLocked, turretHeat, turretLocked, missiles,
  lockTarget, lockProgress, incoming, scan, boltSpeed)` S2C; `space_encounter(kind, point, comm keys, ace)` S2C. The
  server trusts nothing but the trigger's intent; the client reads no `BalanceConfig` (the bolt speed comes in the
  status).
- **Lang**: `src/main/lang-parts/space.json` (79 keys: entities, items, the five orbits and the biome, three death
  messages, 16 HUD strings, 30 comm lines, 8 ace names, the Spindle's terminal and vault lines).
- **Commands** (`/oldrepublic space ...`, operators, every shortcut logged): `status <player>`, `blockade
  start|win|reset <player>`, `patrol <player>`, `end <player>`, `hit <player> <damage>`, `spindle place`, `spindle
  visit <player>`, `spindle restock`, `vault reset <player>`, `reset`.

## 13. Tests

**Server game tests** (`gametest/SpaceGameTests`, 28, in 19.1's order; the lead registers the class in the gametest
`fabric.mod.json`): `boltSweepHitsFastTarget`, `boltNeverHitsItsShipOrRiders`, `shipWeaponsHurtOnlyShipTargets`,
`heatLocksAt100AndCoolsTo40`, `missileLockConeRangeAndTicks`, `missilesComeFromTheHold`, `shieldFirstThenHull`,
`shieldRechargeAfterDelay`, `hullZeroCallsDisable`, `playersShipsNeverDamaged`, `patrolsOnlyForArmedShips`,
`patrolGapRespected`, `encounterEndsOnLeaveJumpOrDisable`, `blockadeWavesAndGeneratorShield`,
`blockadeClanFightersSideWithPlayer`, `blockadeResetsOnDisable`, `peacefulBlockadeIsAPass`, `scanMeterFillsAndDrains`,
`spaceRunCompletesAtTheBuoy`, `spaceClearCompletesWhenGroupDies`, `bountyAceHasDoubleShieldHullAndTwoWingmen`,
`killXpAndCreditsToShooter`, `capacitorShieldAndRecharge`, `spindlePlacedOnceAndVaultOncePerDay`,
`fallBelowOrbitFloorReturnsToShip`, `spindleTemplateHasSidecarAnchorsInsideSize`, `coopKillsCountForOwner`,
`fighterFlightAndRuns` (a fixture in the overworld sky). Each takes at most 400 ticks; randomness is seeded.

**Client steps** (`gametest/m5/SpaceClientSteps`, screenshots `oldrepublic_m5_<nnn>_<what>`, 110–124): each orbit with
its planet below at Standard (110 home, 111 Selvaren, 112 Varkhast, 113 Ghessar, 114 Tyrvann, each looking west 5°
down at the moon low over the rim, which confirms the sky's orientation); Tyrvann at Reduced, High and Cinematic
(115–117); the home orbit's rain check (the client's rain stays 0 after `/weather rain`);
an asteroid field in the Ash Ring (118, found by a heightmap search of the loaded chunks); the combat HUD with
brackets, lead pip, heat and a full lock, with the real helm key and held attack and use (119); a shield hit from two
raider bolts (120); a fighter exploding (121); the blockade gunship with its generator (122); the Spindle's landing deck
with a ship on it (123) and its vault (124). The player's ship, registry, travel data, position, camera, preset and
sky switch are restored afterwards; the orbit chunks visited stay generated, and the Spindle stays placed with its
droids.

**By hand** (19.4): fly an armed ship in `orbit_home` until a patrol comes (or `/oldrepublic space patrol @p`); fire the
cannons until they overheat; lock and fire a missile; let the shield fall; with a second player in the gunner seat, fire
the turret all round; run the blockade with and without the clan (`/oldrepublic space blockade start @p`); fly to
(420, 150, -380) in `orbit_home`, land on the Spindle's deck, use the log terminal and the vault twice; with
`flashingEffects` off, check that no explosion, buoy or storm flashes.

## 14. API status

Verified [V]: `AbstractHurtingProjectile` and its sweep, a gravity-free `Mob` steered by its move control, the
`LevelRenderEvents` hook and `debugQuads`, `EntityRenderer#shouldShowName(T, double)`, `Camera#forwardVector`,
`upVector` and `leftVector` (their signatures), `UseBlockCallback`, attachments, `RuntimeSites.stamp`, the entity and
item registration helpers, `Vec3.directionFromRotation`, `Entity#getYRot(float)` and `getXRot(float)`. Unverified
[U], marked `// API-CHECK`: that `AbstractHurtingProjectile#createParticleTrail` skips a null trail particle
(`ShipBolt`, `ShipMissile`; vanilla's wind charges return null), that the camera's vectors are the view's unit axes
in world space, and that `Camera#getFov` is the frame's vertical field of view in degrees (`CombatHud`, which projects
the brackets and the pip with it, so speed and boost widening the view keep them on their targets; a value outside 1
to 179 falls back to the options' setting).
