# Known issues

Honest list of what is incomplete, rough or unverified. "Verified" in this project means one of: compiled on CI, passed a server game test, or seen in a client game test screenshot. Nothing here has been play-tested by a person yet.

## Fixed and confirmed on CI
These came from the automated playthrough. The fixes compiled and passed on CI, including the full Milestone 1 playthrough.

- **Hidden chamber wall did not break.** The block itself is fine: the lower veined block is the same block as the one above it (no other state, no block entity, same hardness and tool), and nothing in the generator or the weathering processor changes it. The likely cause is the bot: it aimed once and then held attack while standing under the passage's ceiling cluster, whose discharge (every ~4.7 s while you linger) knocks the player back and up, which moves the crosshair off the block or out of reach and restarts or stops the mining. The bot now walks into reach before mining, re-aims every tick, steps back in when knocked away, and logs how many ticks the crosshair was on the block and the damage taken while mining.
- **Guard against poacher bolts.** No bug was found in the arc maths; the bolts that got through most likely came from poachers outside the 100 degree front arc, because the bot only faced one poacher. Two robustness fixes in `SaberGuard`: the arc is now judged from the bolt's direction of travel (vanilla moves a projectile onto its hit point before handling the hit), and a deflected or parried bolt now leaves from the blade in front of the chest instead of from wherever it touched the player. New server game tests check that bolts from up to 47 degrees off the facing (level, from above and from below) are deflected with no damage and the right stamina cost, that side and rear bolts are not, and that a fresh guard parries straight back. The playthrough now turns to face whichever poacher shows its shot tell and logs the direction of every hit that gets through.
- **Camps on slopes.** The camp now uses its own pool element (`oldrepublic:camp_pool_element`), which after placing the template fills every foundation column down to solid ground (up to 24 blocks) and removes terrain left above the cleared yard (up to 24 blocks), during world generation and per chunk, so it is deterministic. Where the ground just outside is two or more blocks lower, the yard's outer two rings now step down one block each, so the camp is not ringed by a ledge on its downhill side (CI run 79f61d8 showed the bot stuck below such a 2-block edge). Camp poachers spawn on the camp floor (template position) and now also stay within 4 blocks of their spawn height, so they no longer climb the hillside around the camp. Poachers aim at the head when the chest line to a player below a ledge is blocked. Server game tests cover the support fill and the edge steps. The playthrough's "foundation columns over air" count no longer counts the shaft column, which is open on purpose (that was the remaining 1/81).
- **Bot could not place the workbench (CI run 79f61d8).** Tall grass in the line of sight caught the right-click, so the workbench was placed in the plant's spot instead of on the ground the bot checked, and the second attempt had no workbench left. The bot now breaks plants in the way before placing and accepts the block wherever it actually landed.
- **Bot could not cut down poachers with the saber.** This was the bot: it walked (poachers back off faster than that) and only swung inside 3.6 blocks. It now sprints to close in, aims at the middle of the body, swings whenever the attack is charged and the crosshair is on the target, and takes a logged teleport when the target stays out of reach for 10 s across a height difference or while the bot walks into something.

## Not yet verified in game
- Nothing has been hand-played. The automated client test drives the awakening, Force Push, the Force and journal screens, the workbench, saber ignition and guard, a poacher fight, the cave and the trial, and a dedicated-server join, but it cannot judge feel, balance or audio (the CI machine has no sound device).
- Natural cave generation is exercised by the playthrough test (`PlaythroughTest`), which finds naturally generated caves on a fixed seed, but it still teleports between distant points and down the camp shaft.
- Multiplayer with two real players (seeing each other's effects, owner-only crystal pickup) is untested; only one client on a dedicated server is tested.

## Force Push
- The first-person push offset moves both hands, not only the pushing hand.
- In third person the camera shake is applied after the camera position is computed, so the camera is very slightly misaligned while shaking.
- Wall-slam detection on pushed players relies on server-side collision, which is unreliable for players (their client controls movement). With PvP effects on, slams on players may not trigger.
- The client's charge visual assumes 20 ticks to full charge; changing `fullChargeTicks` in the balance file desyncs the visual.
- Release feedback (thrust, sound, camera kick) waits for the server's release event, so it arrives one round trip after letting go in multiplayer.
- The air-ripple sprite still reads a little like a ring when viewed head-on; final art should push it toward heat haze.

## Progression
- The anti-grind rule "no XP from spawner mobs after 30 kills" is not implemented yet; kill-XP fatigue per mob type is.
- `/oldrepublic setlevel` is limited to 1 to 100 and ignores `progression.maxLevel`; mastery rank thresholds (50/150/400) are not in the balance file.
- Some design docs disagree with the code on small numbers; `docs/BALANCE.md` lists them under "Known mismatches" and the code values are the ones in effect.

## Quest, journal and HUD
- Relogging during the Awakening dream replays the dream (no progress is lost).
- "(new)" markers in the journal only cover entries unlocked in the current session.
- The journal recipe has no recipe-book advancement, so it does not appear in the recipe book automatically.
- The Practice objective in the journal shows a running count rather than "n / 5" (the goal is server-side balance); the chat message shows the goal.

## Lightsaber
- The held pose (the first-person rest, cuts, guard and staff twirl; the third-person ready stance and the two-handed staff) was tuned from CI screenshots only and has not been hand-played; it may still need adjusting by hand.
- A lit blade glows but does not light the blocks around it (there is no dynamic light). The glow is soft additive layers drawn around the beam, not a bloom shader.
- Explosions hitting a guarding player from the front are reduced like melee hits.
- With the saber in the off hand, the Toggle Lightsaber key (`U`) toggles it but guarding uses whichever hand the use action comes from.

## Crystal Poacher
- **Fixed (confirmed on CI):** the guaranteed poacher emitter used to be spent when it dropped (`got_emitter` set on drop), so a drop that landed out of reach (for example down the camp's central shaft) was never replaced and the saber could not be built from poacher loot; this was the likely cause of the playthrough STUCK at "14 emitter". `PoacherLoot.reconsiderEmitterFlag` now clears the flag on the next poacher kill when the killer carries neither an emitter nor a lightsaber, and the protected emitter drops at the killer's feet with an unlimited lifetime. `EnemyGameTests.lostEmitterCanDropAgain` covers it and passes on CI. The playthrough still logs poacher deaths and emitter drops.
- The natural-spawn biome modifier is always registered; the spawn rule blocks spawns outside crystal caves unless `naturalSpawns` is on, and the cave itself disables monster spawns, so in practice poachers come from the camp template.
- Bolts slow down in water (vanilla projectile water inertia).
- Rifle pose and muzzle-glow position may need tuning in game.
- Balance changes reach already-loaded poachers only after they reload.

## Crystal cave and trial
- On slopes the camp's support columns and the cut above its yard are straight vertical stone and terrain faces, and the edge steps only lower the yard by up to two blocks (a deeper drop keeps a ledge); they look man-made on very steep ground. Support stops 24 blocks down and the clearing stops 24 blocks up, so a camp at a cliff edge or under a tall cliff can still hang or be overhung. Water next to a cleared column is not settled until a block update reaches it.
- Trees placed by later world generation steps still grow on the camp yard (seen in the CI screenshots).
- The edge steps read the world-gen height just outside the camp from the neighbouring chunk; if that chunk was changed by another structure first, the step height can differ.
- Camps in chunks generated before the terrain fix keep their old foundations.
- The hidden chamber's wall can only be mined from inside the reach of the passage's ceiling cluster, so mining it means taking a discharge or two unless you are quick (intended hazard, but harsh).
- An open resonant seal is open for everyone while it is open.
- Slim player skins show a one-pixel seam on the Shadow Echo's arms (it uses the wide model).
- Crystal pickup is detected by polling once per second.
- If the server crashes in the 1.5 s between the trial win and the crystal forming, after the world saved but before player data saved, a second crystal could form.

---

# Milestone 2 (Identity and Builds)

Everything below was written from the code; none of it has been seen in play by a person. Milestone 2 passes on CI: the build, every server and client game test, and PlaythroughTest 41/41 on the Jedi path.

## Not yet verified
- The server game tests (`IdentityGameTests`, `StoryGameTests`, `SkillGameTests`, the extended `ForceGameTests` and `SaberGameTests`, `EnemyGameTests`) and the client screenshot test (`IdentityBuildsClientTest`, screenshots `oldrepublic_m2_10` to `59`) pass on CI. A screenshot test proves a screen opens and draws; it does not judge how it looks.
- **The PlaythroughTest Chapter Two run covers the Jedi path only** (spec 16.3). It passes on CI (41/41): every dialogue choice, lesson, the spar, the Captain's yield, the captives and the Trial are done with real inputs. Logged shortcuts remain for travel (walks that stop short at tents or ledges teleport to the talking spot), node selection on the Force screen, and levelling (see the next item). The Sith and Independent runs do not exist.
- **The Trial can be reached under-levelled (kept by design).** A straight Jedi run reaches the debrief at level 5 while `story.trialMinLevel` is 6, so the player gains a level before asking for the Trial. Gabriel chose to keep this pacing.
- The manual checks of spec 16.4 are all open: feel of combo, heavy and lunge timing; Lightning readability at all four presets; audio loops (`force.jump.gather`, `force.jump.glide`, `force.heal.loop`, `force.lightning.loop`, `saber.throw_loop`, `story.two_voices`) stopping on death, logout and dimension change; two players at separate Crossings; a dedicated server with `allow-flight=false` and Soaring Leap.
- Contextual alignment detection (defended, harmed, killed innocents, cruelty) is not driven by any test; only the cap arithmetic is.
- Some `API-CHECK` comments describe runtime behaviour that a compile does not confirm:
  - `ForceJump.tickGlide`: `ServerPlayer#getLastClientInput().jump()` is the held Jump key; if not, Soaring Leap never glides.
  - `DialogueScreen`: `Screen#removed()` runs whenever the screen is replaced or closed (it ends the server session).
  - `SettlerModel`, `MentorModel`: `ModelPart` positions, rotations and `visible` are reset by `setupAnim`; if not, kneeling and role-specific robe pieces could stick.
  - `SithAcolyteRenderer`, `PoacherCaptainRenderer`: `LivingEntityRenderer#scale` runs after the model flip (kneel pose offsets); `PistolGlowLayer` assumes the 1.21 `ItemInHandLayer` arm transform (probed in Milestone 5, `tools/api-probe/m5_w8.txt`: unchanged in 26.2).
  - `ThrownSaber`, `SaberTechniques`: bosses are recognised through the `c:bosses` convention tag.
  - `Settlers`: the loot calls for `settler_thanks` / `captive_payment` (`reloadableRegistries().getLootTable`, `LootParams` with the chest set).
  - The others (`Vec3.CODEC`, `UUIDUtil.STREAM_CODEC`, attribute modifier methods, `Entity#is(TagKey)`, `DamageSources#generic`, `TamableAnimal#isTame`, `CommandSourceStack#getTextName`) are signature checks that a green compile settles.

## Identity, dialogue and settlers
- **Spawn protection radius comes from the balance file, not `server.properties`.** 26.2 exposes no getter for the server's spawn-protection radius, so story sites avoid a square of `story.spawnProtectionRadius` (default 16) plus half the footprint around world spawn. A server with a larger protection radius must raise this value by hand.
- Frontier Settlers only exist as Smoke on the Ridge captives or through `/oldrepublic identity settler free`. There are no natural settlers or homesteads in M2 (Milestone 6 added homesteads to the home world's wilds, still with nobody living in them), so the Frontier tier effects (thanks bundle, settlers fleeing a distrusted player) are only seen with those.
- If a server changes `identity.lightThreshold` or `leaningThreshold`, the client still draws the default band edges.
- Dialogue stances are fewer and smaller than the spec asks for (see `docs/BALANCE.md`, Known mismatches).
- Alignment and reputation toasts stay hidden until the player reaches the Crossing (by design); values are still recorded before that.

## Skills and the Force screen
- Stat passives and dormancy are recomputed on every change and once a second, so an alignment change can take up to a second to switch a passive on or off.
- Skill trees are data files; a broken datapack override is logged and skipped at load. `/oldrepublic skills validate` reports problems.
- Milestone 1 saves are migrated on join (Push granted, chosen branch owned as 1 spent point, Push in slot 1). The migration is tested only by a server test.

## Force powers
- The Soaring Leap fall-speed cap is applied by the jumper's own client (player movement is client-side). A modified client could glide longer; the server still charges energy per held tick and ends the glide.
- Whether Force Jump and the glide trip the vanilla flying kick on a server with `allow-flight=false` is unchecked (spec API-CHECK).
- The Lightning arc watchdog hides arcs after 6 ticks without a refresh while refreshes come every 5 ticks, so on a jittery connection the arcs may blink for a tick; the channel itself continues.
- No dynamic light from Lightning (vanilla cannot do it).
- The Lightning pose has no body lean: humanoid arms are not children of the body, so tilting it would detach them.

## Saber combat
- Spars are in memory only: a server stop, logout or death ends one with no result.
- Saber stamina is still not saved (refills on login), as in Milestone 1.
- After a crash while a saber is thrown, the `saber_thrown` component is cleared 60 ticks after the stack is next ticked in an inventory; until then the saber cannot be used.

## Chapter Two
- Meeting and encounter sites are only searched in loaded chunks. In rough terrain the fallbacks can put the meeting point 24 to 40 blocks away, or 12 blocks in front of the player. (Milestone 6: the Crossing and the raider stronghold are now placed far out first, 350 to 600 blocks from the player and 450 to 750 from the mentor's camp; this search runs only when far placement fails.)
- Camps and the raider camp are placed into the existing world (terrain filled below and natural clutter cleared above the footprint) and their blocks stay after the chapter; `story.placeStructures=false` turns block placement off.
- If the Captain fails to spawn she counts as fled. If Brenn cannot be found, the Sith trial passes without the duel and the Sith daily session uses the Overseer's parry drill (the spec's invisible training remote is not built).
- The smoke Calling (variant 3) has no sound; the camp's campfire smoke is the guide.
- Crew deaths while the owner is offline are recovered by an absence check when the owner returns within 32 blocks of the camp.
- Mentors do not follow the player to other dimensions; "Ask them to come" works in the overworld only, and Chapter Two waits while the player is elsewhere.
- The Ledger's four pages are fixed text; it is a hook for Milestone 3.

## Assets
- Every texture, model, particle sprite, structure and sound, in Milestone 1 and Milestone 2, is a generated placeholder. See `docs/ASSET_MANIFEST.md` for the list and artist briefs. Settler, acolyte and Captain vocal sounds are synthesised grunts; there is no voice acting.

---

# Milestone 3 (Exploration)

Everything below was written from the code and the worker reports; none of it has been seen in play by a person. Milestone 3 is built, and at `8add85b` every automated test passes on CI (286 server game tests and every client game test). `PlaythroughTest` plays through Chapter Three on the Jedi path, with the results below. Each heading names the owner area (spec 15.2): W1 world, W2 story, missions and identity, W3 Force, skills and holocrons, W4 troopers and armour, W5 boss, constructs and saber, and the lead.

## Not yet verified (lead)
- **Screenshots prove a scene draws, not how it looks.** A review of the 49 Milestone 3 screenshots from `9f9cccd` found the burial chamber, the Listening Hall and the Reliquary too dark to read (night vision added for the pictures in `cf7471d`), the rubble Lift step aiming before its teleport landed so it used the stand-in (fixed in `cf7471d`), the Republic outpost framed behind a hill, and the Archivist and yield pictures missing Vasch (their cameras stood on the hall's lower floor, with the dais edge in the way). Since `2fb2f42` the boss cameras only stand where they have a clear sight line to their subject, and the overview cameras rise over a hill that hides their site; CI run `2fb2f42` shows Vasch in both pictures and the outpost from above. The veiled stone does change after Sense (its revealed texture is speckled), but the placeholder difference is subtle at screenshot size; final art should make it read at a glance.
- **Chapter Three playthrough: the Jedi run, with shortcuts.** `PlaythroughTest` plays steps 35 to 55 with real inputs after Chapter Two. At `8add85b`, 61 of 63 step results pass (20 of 22 in Chapter Three), with 55 logged shortcuts in the whole run. The Chapter Three shortcuts it still takes, all logged: a Watchers' Rest placed by command when none lies within the first-echo radius (the seed survey is pending), teleports when a long surface walk stops at a drop (to the outpost, the mission sites and the Reliquary), the Reliquary placed by command when no spot was found near the player (see W5), XP and level top-ups for the Reliquary and rank 3, the second Jedi holocron and the three own-side contracts for rank 3 given by command (the pilgrim's niche gives the Wayfarer's Compass, not a Jedi holocron), keep-alive effects at low health in the Collector fight, a floor spot or the saber when a gallery crystal stays out of sight, and moving the fight on when the bot does not win a phase in time. The Sith run is not written; the Independent run is cut 7.
- **Not written:** `ExplorationPersistenceTest` (save and reload of every M3 attachment and saved data) and the M3 dedicated-server steps in `ClientSmokeTest`. Server game tests do run on a dedicated server, and the codecs have round-trip tests.
- **Seed survey.** The worldgen seed survey is still pending, so the client steps place every site with `/oldrepublic site place` on rings at least 320 blocks out instead of finding natural ones. Natural placement of ruins, tombs, wrecks and outposts is not seen by a test.
- The manual checks of spec 17.4 are all open, including two players at one Reliquary, a co-op helper's stance, audio loops stopping on death, logout and dimension change, and every effect at the four quality presets. CI has no sound device.

## Tests and infrastructure (lead)
- **`GameTestHelper#relativePos` turns positions a half turn.** In this Minecraft version, `relativePos` rotates an unrotated test's positions by 180°. The M3 tests that need local positions (the urn fixture cells, for example) compute them by subtracting the test origin instead (`7b38f8e`).
- **Client game test runs sometimes fail before starting** when Maven Central answers 429 (Too Many Requests) while Gradle resolves dependencies. This is infrastructure, not a test failure; rerun the job.
- **Mock players are always creative.** `Mob#setTarget` and `getTarget` drop them, so the squad tests check the squad's intent toward a mock player instead of its target (`1c55343`).
- **Lift screenshot shortcut.** If the real Lift cast does not take hold in step 32, the step lifts the zombie through `ForceLift.liftTarget` and logs the shortcut.
- **Stand-ins in the Force tests.** The dispatch and Sense provider tests in `ForceGameTests` use stand-in blocks, entities and providers, not the real puzzle blocks. The grenade's Push and Pull are tested by calling `onForcePush` and `onForcePull` directly, not through a cast.
- **The playthrough world varies from run to run.** The seed is fixed, but where the bot ends each step depends on its path and timing, so the mentor camp, the Reliquary and the mobs about differ, and a step can pass in one run and fail in the next. In the last six runs (`f49864c` to `8add85b`) Chapter Two's first lesson failed once, the Listening Hall and pilgrim's niche pedestals three times between them (zombies nearby, or the bot left out of reach after fighting them), R1's tending twice (a trooper lying behind a camp wall), R2 twice, the first echo once (fighting the diggers took the bot 25 blocks from the courtyard), the Reliquary descent once and phase 2 three times. Every Chapter Three step passed in at least one of those runs. Only a stuck run fails the job.
- **The client tests run at render distance 5** (the Fabric client game test default), so only about 80 blocks around the player are loaded. That is why the playthrough places the Reliquary by command, and why its mission camps fell back to the forward spot (see W2).
- No test covers a respec of Exploration nodes.

## World and sites (W1)
- **Placeholders.** Every site template (Jedi Ruin, Sith Tomb, Wreck Field, both outposts, the three Echo Shrines) is a generated placeholder, with `site_anchors` sidecars. See `docs/ASSET_MANIFEST.md`. (Milestone 6: all rebuilt; see Milestone 6 below.)
- **Command placement.** `/oldrepublic site place <structure>` places at the chunk corner, in any biome, and registers the start. A shrine placed this way has no owner, so its guard is shared.
- **Loaded chunks only.** Runtime outposts and shrines go only into loaded chunks; a failed search retries later. (Milestone 6: far placement now puts them 400 to 700 blocks from the player and loads the chunks it needs; this search is the fallback.)
- **Overworld only.** Outposts, shrines and every Chapter Three site are placed only in the overworld. `/oldrepublic outpost ensure` in another dimension replies "not in the Overworld".
- **Cairn expiry is lazy.** Leaving LISTENING marks the owner's cairns as expired in `WorldSites` (`EchoCairns.expire`, wired in `9f9cccd`). Each cairn removes itself the next time it ticks, so a cairn in a chunk that stays unloaded keeps its block until that chunk loads.
- **Puzzle reset granularity.** A puzzle resets 1,000 to 1,200 idle ticks after the last player leaves, because the idle step runs every 200 ticks (see `DECISIONS.md`).
- **Courtyard stair headroom (fixed).** On the Watchers' Rest courtyard stair, the block over the step down from the top tread was solid, so a player walking down hit their head and stopped. The generator now clears it (`e4440e6`); the playthrough found it.
- **Mast lamp.** The outpost mast lamp's flare is client particles, and there is none with flashing off.
- **Runtime API-CHECKs** that a compile does not settle: the jigsaw start height of the M3 structures, banner patterns stored in template NBT, and chests saved without `LootTableSeed`.

## Chapter Three, missions and identity (W2)
- **The client gets no server balance.** The Standing tab's rank 3 checklist and the Missions tab (rank 3 contract counts, full-pay count, duties per day) show the default thresholds. On a server with tuned `chapter_three` or `missions` sections the ticks can disagree with the server's `rank3:ready`, which decides.
- **Holocron return naming (spec 4.6).** The return options run `holocron:return` directly, so the dialogue names the holocron after the return ("return.done"), not before the player confirms.
- **Story choice shifts.** A major alignment shift is refused once its source id is a recorded choice. The story missions and the Collector's endings used to record the choice first, so their shifts did nothing; `7fc0d9c` applies the shift first. No test drove the missing shift before the fix.
- **`echo_cache` emitter.** The `echo_cache` loot table's own emitter entry is never given. The rematch emitter comes only from `collector.emitterChance` and the pity counter (about 1 clear in 4, the 4th at the latest).
- **Hessa Dray did not appear (fixed).** In three CI runs (`db74f96`, `f49864c` and `fc12ff8`) the bot found no R2 chief at the post, though the mission had marked the chief done. The cause was the mission's safety recount: Hessa, spared in Chapter Two, is not spawned until the owner comes within 12 blocks of her post, so a player who stayed near the site for two recounts first had her counted missing and marked done, and the record came from the crate instead. The recount now skips a chief who waits at her post (`Cameos.chiefWaitsAtPost`).
- **Forward-fallback mission camps (fixed).** When neither the mission rings nor their fallback found a spot (often with a small view distance), the forward fallback stamped the camp with no site checks. In one playthrough (`ac2e508`) the R2 cache landed inside the Republic outpost's box, and the camp lookup then answered with the outpost, which has no crate, so the record could only come from Hessa. `34c4ecd` had the same symptom with the cache at the same spot. The fallback now tries rings of 16 to 40 blocks with every site check, and stamps the forward spot only when it is clear of other sites and builds; otherwise the mission runs around the bare spot, with its people still spawned there. The lookup answers only the mission's own camp (`1e8eabb`, server test `missionCampLookupIgnoresAnOverlappingOutpost`). The bare spot itself could still lie in the outpost the mission was taken at: R1's did in several runs, and at `c5e476a` R2's stood 12 blocks from the officer, so step 44 found no camp and no crate. The fallback now looks further along the same line, up to 48 blocks, for a spot where the camp fits or, failing that, a bare spot that no other site touches, and the playthrough bot walks to a camp-less site for the chief and the record (`dd1f066`).
- The Sith lesson, Courier's Fall, and the first-visit discovery XP and toast are not driven by a test. The Jedi lesson is (`PlaythroughTest` steps 36 and 37).

## Force, skills and holocrons (W3)
- **Lifted creatures.** A lifted creature hangs at gravity 0 with its AI paused. An explosion or water can still push it, and the channel ends if it leaves the range.
- **Lifted players** (PvP option only) may jitter for a tick on a high-latency connection.
- **Danger Sense** reacts only to the effect-event tells in `DangerTellTable`. Vanilla mobs have no tells, so it never warns of them.
- **Holocrons tab.** Its carry line shows the default `holocron` values, because the client cannot read the server's `balance.json`.
- **Restore day.** The holocron restore is once per 24,000 ticks of game time (the project's day index), which sleep does not skip (see `DECISIONS.md`).
- **Busy pedestal.** A player idling in a holocron's dialogue keeps that pedestal busy for others for up to 2,400 ticks (`MAX_MOMENT_TICKS`).
- **Runtime API-CHECKs:** `GatekeeperFigure` passes a null sprite and a null crumbling overlay to `submitModel`; `RisingHolocron` passes a null `ItemOwner` to `ItemModelResolver#updateForTopItem`; `SenseCreatures` relies on `TamableAnimal#isOwnedBy`.

## Troopers and armour (W4)
- **The uniform disguise (9.5) is cut.** `ArmourSets.disguise` always returns NONE, because `Allegiances.of(player)` reads it and a live disguise would make every trooper bolt pass through the wearer (`canHitEntity` skips friendly targets).
- **Registration defaults.** `equipment.trooperDurability`, `robeDurability`, `trooperEnchant`, `robeEnchant` and the speed attribute of `trooperSpeedPct` are read from the code defaults at item registration, so file changes to them do not apply.
- **Squads by proximity.** Squad membership is found by proximity (48 blocks). Scattered members act as separate squads until they regroup.
- **Reinforcements in view.** When no spot out of sight is found in 16 tries, the rifleman arrives at the first spot tried with standing room, which a player might see. When none has room, it does not come.
- **Robe gate.** The robe gate in tooltips and the set toast uses the default `setAlignment` (15), not the server's value.
- **Refund cap per tick.** Two parries in one game tick share one per-parry refund cap (`progression.parryRefundCap`), because `EnergyRefunds` keys a parry by its tick.
- **Bound sentinels.** Troopers attack idle bound sentinels on sight only through `Troopers.registerBoundCheck`. `ConstructFeature` now registers it (with `registerDormancyCheck`), so this is resolved in code; no test puts a squad next to a bound sentinel.
- The runtime look of the trooper muzzle glow (`TrooperGlowLayer`, the 1.21 arm transform) and the renderer `scale` order (`TrooperRenderer`) are unverified until screenshots 40 to 49 pass.

## Boss, constructs and saber (W5)
- **NPC deflection cone.** "Within 70°" is read as 70° either side of facing (a 140° arc), the same way the code reads the 15° spread and the emitter's 12°. The lead accepted this reading (`DECISIONS.md`); set `saber.npcDeflectConeDegrees` and `collector.deflectConeDegrees` to 35 for a 70° full arc.
- **Reliquary build resume.** An unfinished build is found from its `WorldSites` records: a surface record with no hall record under it. The hall is then rebuilt whole. If the surface record itself is lost, nothing resumes.
- **Dormant sentinels and area powers.** A dormant, unbound sentinel is NEUTRAL to players, so area powers skip it. A direct hit, coming close, or `wakeNear` still wakes it.
- **Peaceful.** Vasch talks for 60 ticks, then kneels; there is no fight.
- **Layout and look deviations:** the vault is 9×4×3, inside the dais; the sunken floor is 15×14; the antechamber sits at +5; the cloak streams out behind him, not forward; `hoard_open`'s direction points from the plinth to the vault seal; the echo's form event is sent around the dais, because nobody tracks an entity on the tick it spawns; on the Sith Spare walk-out his blade shows as a steady glow on the dais and is not left as an item.
- **Pull on a gallery crystal from below (fixed).** The server checked only the line to the crystal's centre, so from the floor under a gallery the pylon block's edge hid it and Pull did nothing, though the crystal's tip was in sight. Pull now also takes a clear line to the tip (`PylonFocus.TIP_INSET`, `b380712`); `BossGameTests.pullReachesTheCrystalTipOverThePylonEdge` covers it.
- **Reliquary rings and small view distances.** The Reliquary's rings (96, 128 and 144 blocks) need loaded chunks. With a small view distance they are not loaded, so placement falls back to the 48 and 80 rings, and when those fail it tries again later as the player moves. On a server with a view distance below about 9 the Reliquary will often sit on the fallback rings. (Milestone 6: far placement now puts the Reliquary 600 to 900 blocks from the player and loads the chunks it needs; these rings are the fallback.)
- **Phase 2 length.** In the last six runs the bot won phase 2 three times (in 1,529, 2,374 and 2,822 ticks) and ran out of time three times, with Vasch at 143, 152 and 171 of the 135 needed (the budget is 2,400 ticks, and since `ac2e508` up to 3,600 after progress). It loses time walking to floor spots under the gallery and on Pulls that miss. Whether the phase is too long for a person is a question for hand play.
- **Away timer.** A participant's away timer runs only outside the whole Reliquary, so a participant at the antechamber bed stays listed.
- **Runtime API-CHECKs:** `Entity#teleportTo` on an `ItemEntity` (the reset moves items to the antechamber) is assumed to keep their owner and pickup delay. `LivingEntityRenderer#scale` running after the model flip (`OrrenVaschRenderer`, `RuinSentinelRenderer`) is now confirmed by the m4_followup probe.

## Assets (all areas)
- Every Milestone 3 texture, model, particle sprite, structure template and sound is a generated placeholder, except the Reliquary layout (final layout, placeholder blocks) and the singing crystal (final art for M3). All 121 new sound ids play synthesised placeholder audio. There is no voice acting: Vasch, the troopers and the officers have none. See `docs/ASSET_MANIFEST.md`, "Missing assets".

---

# Milestone 4 (Broader Content)

Everything below was written from the code, the design docs (`docs/design/{planets,chapter_four,vendor,travel,gear,crafting,saber,force_m4,foes}.md`) and the worker reports; none of it has been seen in play by a person. All six areas are merged: W1 worlds, W2 Chapter Four, vendors and economy, W3 ships and travel, W4 gear, W5 crafting and saber, and W6 Force Speed, Barrier and Choke, the foes and Kaast. Each heading names the owner area (spec 15.2).

## Not yet verified (lead)
- **Nothing is seen by a person yet.** The client screenshots (`oldrepublic_m4_10` to `76`) prove that a scene draws, not how it looks or feels. The manual checks of spec 17.4 are all open, including every effect at the four quality presets and audio loops stopping on death, logout and dimension change. CI has no sound device.
- **The playthrough covers Chapter Four on the Jedi path only.** `PlaythroughTest` steps 56 to 80 play the chapter from the mark to the report in one world, on Selvaren. Where a real input does not take, the step falls back (a command or a direct call) and logs a `SHORTCUT` line. The Sith arc (the Proving, the Sepulchre, the Duskglass Rift) and the Independent road (Tessaly's Road, Both Doors) are not played; `StoryM4GameTests` checks their rules one at a time, mostly by setting the stage directly.
- **Chapter Four playthrough results.** At `ab1372a`, 24 of the 25 Chapter Four steps pass, and 79 of the 80 steps in the whole run; the run logs 103 shortcuts, 39 of them in Chapter Four. At `c5e476a` all 25 Chapter Four steps pass, and 79 of 80 in the whole run (step 44, the R2 mission, is under Chapter Three above); that run logs 95 shortcuts, 42 of them in Chapter Four. At `c01b1ac` all 80 steps pass, with 101 shortcuts, 45 of them in Chapter Four; the bot won Kaast's phases 2 and 3 in play (7 parries in phase 3). The Chapter Four shortcuts still taken:
  - teleports where a walk stops short (cliffs, drops, the hub's walls) and to far places (the waystones, the ring points, the ship);
  - a teleport down into the Singing Hollow's grotto, because the bot does not climb ladders;
  - the materials for the ship parts and the weld's plates, given with `/give`;
  - a Force slot freed by a direct call at the hub, so Barrier has a slot to be granted into (with every slot full the Heartstone waits for one);
  - the second ambush, started by a call: it comes two days after the first, and `Clocks.day` counts the home world's game time, which `/time` does not move;
  - a fled ambush's one-day wait, cleared after the bot reaches open ground;
  - keep-alive effects at low health in the fights;
  - Kaast's phase 1, moved on with `/oldrepublic kaast phase 2` after a 2,400-tick timeout. The bot rarely lands a hit there; at `ab1372a` it won phases 2 and 3 in play.
- **Flaky Chapter Four steps.** The playthrough world varies from run to run (see Milestone 3), and three steps have each failed once:
  - step 73, at `ab1372a`: a hit, from the second ambush's last flames or a straggler, closed Kaast's challenge before the bot answered it. The step now waits for the fight to settle and, if the challenge closes, replays it from the marker beacon as a player would. It passed at `6b54612`;
  - step 68, at `6b54612`: in each round one Push was cast with the crosshair on the Hollow's counterweight, but it did not sink, so the puzzle restarted and the step ended it by a call. A Push acts on the first block on the server's eye ray at the release, so the bot now waits until it stands still, and pushes a second time, aiming a little lower, if the first does not sink the stone. It logs what the server's ray meets;
  - step 78, at `6b54612`: a hit closed Kaast's yield a second after it opened, before the bot chose Spare. All three phases had timed out that run, and the cause was not logged; fire still burning on the player or a leaving hunter is the likely source. The step now waits for the fight to settle and talks to the knelt Kaast again, as a player would. At `7c31aa8` steps 68 and 73 passed but 78 failed again: vanilla night mobs at the camp kept hitting the player, and a plank of the camp took the right-click meant for the knelt Kaast. The step now also removes vanilla hostiles that keep the player in combat, and reopens the yield with `/oldrepublic c4 yield` when a right-click does not (both logged as shortcuts). It passed at `c5e476a`, where the first right-click opened the yield.
  - at `dac8c32`, in a world where Kaast's camp is cleared beside its ring point (as at `7c31aa8`), the run passed 78 of 80. Step 59's second hyperfuel batch did not start after a Craft click. At step 73 a straggler's bolt killed the player just after the challenge came, and the retry skipped the unanswered challenge. At step 74 the camp was placed around the player, who was shot dead while the bot went back to the ship, and the bot then waited for chunks on the death screen for 7 minutes. In all three Kaast phases the bot stood stuck in the gate at the ring's edge, so each phase timed out, as at `7c31aa8`. The bot now clicks Craft once more, replays an unanswered challenge from the marker beacon after a death, steps out of a camp placed around it, stops waiting for chunks while dead, and teleports into the ring when its walk stops in the gate (each logged as a shortcut).
  - at `d7c66a1` the gate fix held: in the same camp layout the bot won Kaast's phases 2 and 3 in play, with 14 parries. Step 61 failed there: something stood between the bot and Saelen on all three tries with the nav computer. The bot now steps to a spot where its crosshair reaches the one it uses an item on.
- **Other flaky playthrough steps.** Step 09 once could not reach a poacher standing inside the camp piece, and step 55 once found the knighting dialogue closed. Both passed in the other recent runs. At `d7c66a1` a hit closed the captives' dialogue a second after it opened (step 30; steps 31 and 34 failed because of it). When a dialogue closes before the bot's first reply, the bot now waits for the fight to settle and talks again.
- **Not driven by any test:** planet drops, the duskglass cut and budding growth; the rift bleed and the cache's sinking platforms; respawn at the hub medbay; fitting and removing ship upgrades; the fusion cutter's held use (the plating test calls `SealedPlatingBlock.cut` directly) and the repair kit; throwing a player grenade; the staff throw; the Sepulchre trial and the Sith and Independent arc missions in the world; the `escort` and `spar` contracts; natural hollowhorn and ash hound spawns. The playthrough drives the Heartstone trial, the Singing Hollow's puzzle and the Kaast fight on the Jedi path, but a bot is no judge of how they play, and it moves Kaast's first phase on by command.
- **Seed survey.** The client steps find each biome with `findClosestBiome3d` (within 2,400 blocks of its hub) and place the waystone and greater tomb they photograph with `/oldrepublic site place`. Natural placement of waystones and greater tombs is not seen by a test.

## Tests and infrastructure (lead)
- **Planets do not exist on the game-test server.** `PlanetGameTests` checks rules, data and templates; the planets themselves are only reached by the client steps, which move the player with `TravelService.moveAcross`.
- **The expedition wait.** `PlanetClientSteps` waits at most 1,200 ticks for `PlanetSites.ensure` to place an expedition and fails the step if none is placed by then. A slow server (many force-loaded chunks) can use up that time. When no ring candidate is bare, a clearing search around the ring point now places the site (`cd6039a`); a site with no spot at all logs why once per owner, and the step reports the same when it times out.
- **Mock players in the M4 server tests.** Mock players are always creative (see Milestone 3). Ship item use checks `hasInfiniteMaterials`, as vanilla does, so a survival test player uses up its parts (`b78a996`). Where a real hit cannot land on a mock player, `CraftingGameTests` invokes the Fabric event the hit would fire, and attribute and cooldown ticking are checked by direct calls. The flurry test depends on the mock player's attack path.
- **Real input and its fallbacks.** The Weapon Action key (`N`) is pressed with real input in screenshots 46 and 49; screenshot 57 toggles the staff's second blade by a direct call (`SaberStaffs.toggleSingle`). `TravelClientTest` boards the ship with a server-side `startRiding`, and walks to the Greenwatch's keeper with real key presses; that walk passes if it gets closer, even when a wall stops it short, and fails only when it makes no progress. In `TravelClientSteps`, a `WARN` line in the log means a real use did not take and the step used its fallback (using the ship from the pilot seat, or from beside the hulk past the cradle props).
- **Stages above the player.** The travel and crafting client steps build their stage above the player (the travel stage 60 blocks up, clearing terrain 60 to 72 blocks above the start) and put everything back afterwards.
- **Parallel ship tests.** `TravelGameTests` edit the shared ship registry through a snapshot and a commit. Each test removes only its own owners' records, but a snapshot committed after another test's write could wipe that write.
- **Dedicated server.** `TravelClientTest` connects to a dedicated server for one round trip, the jet pack chain (`GearClientSteps.jetChain`), one blaster shot and 80 ticks of Force Barrier, and asserts the player stays connected. Barrier is taught and pressed on the server's side. No other M4 client step runs on a dedicated server.
- **Story and foe client steps.** `StoryClientSteps` sets the Chapter Four stage directly for shot 21 (WORLD_ARC) and spawns the person in shots 22 to 24 three blocks ahead of the player, so uneven terrain can hide them. `FoesClientSteps` sets Kaast's phase with `/oldrepublic kaast phase` and ends the fight with `kaast reset`; each shot waits for the server to confirm the scene. Screenshots 69 `kaast_phase3` and 69 `kaast_yield` share a number.
- **Persistence.** `PersistenceM4Test` reopens a save with a ship record, travel data, crafting data, modded gear and Chapter Four at DUEL with Kaast yielded and the camp on Varkhast (the stage is written directly, as `/oldrepublic c4 stage` would). It does not check planet site data, waystone cleansing or the vendor day counters.
- **The hull code's text arguments (W3, checked).** The m4_w3 probe dumps `TextFeatureRenderer$Submit` as (pose, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor), and `submitText` copies its arguments into it in that order, so `CourierShuttleRenderer` passes light, colour, no background and no outline as intended.

## Worlds (W1)
- **Placeholders.** Every planet template (waystones, greater tomb pieces, both hubs, the Sepulchre, the Heartstone, the expeditions, the Wayfarer cache, the hunting camp and the ambush rocks) is generated by `tools/assets/gen_structures_m4.py`, with `site_anchors` sidecars. (Milestone 6: all rebuilt by the scripts in `tools/assets/structures/`; `gen_structures_m4.py` no longer writes them.)
- **Weather on the planets (fixed, `7362217`).** The probes showed that every level that can have weather counts down the shared weather timers, so with both planets loaded the weather cycle ran about three times faster. A mixin (`PlanetWeatherCycleMixin`) now lets only the overworld advance them; the planets still ease their own rain toward the shared state, so their storms follow the home world's weather. Sleeping through rain on a planet clears it everywhere, as sleeping at home does. CI's storm shot (12) still shows the Varkhast storm with ashfall after the fix.
- **Ambience is a client emitter.** The biome beds and the Varkhast ashfall come from client emitters (`PlanetAmbience`), not from the biomes' ambience data, because 26.2 changed that data (`DECISIONS.md`). They follow the `planetAmbience` client option, and so do the hub beds.
- **Client-side balance.** `planets.ashfallDensity` and `riftBleedTicks` (the length of the rift whispers) are read on the client from its own file or the defaults, so a dedicated server's values do not reach players. The server still decides how long the rift crystal must be held.
- **Hub people.** The hub's people spawn from `hub_spawner` blocks through factories other areas register (`HubSpawners.register`). A role with no factory logs one warning and stays empty.
- **Hub search.** When no ring has a bare 63 by 63 square, the rings are searched again with trees allowed and the hub's box is cleared first; the stilt platform at (0, 0) is the last resort. (Milestone 6: the square is as wide as the hub, up to 95 blocks, and before the stilts a hub is fitted to the ring stand with the least spread of ground.)
- **Expedition underground pieces** are stamped under the surface piece even when the sunken placement checks refuse. The chunks they reach are loaded first (fixed): the playthrough (`fc12ff8`) found a Singing Hollow placed just after the player arrived, before those chunks had loaded, with no grotto under it.
- **Sealed plating** opens at most 32 blocks of a connected sheet (`SealedPlatingBlock.MAX_SHEET`).
- **Cut:** Selvaren's second moon (cut 1).

## Ships and travel (W3)
- **First hub placement.** A ship flying to a hub that is not placed yet aims for a provisional pad at (0, sea level, 0) and finds the height at landing. If the server stops during that first placement, the ship lands at the provisional height.
- **Offline crew.** Removing a crew member updates the ship record at once, but the member's own `crewOf` (in their travel data) is cleared only if they are online, so a member removed while offline keeps a stale link. The Crew tab knows offline members' names only from players seen since the server started, so after a restart an offline member shows without a name.
- **Home fallback.** Home is the landing beacon's pad, else the ship cradle, else the world spawn.
- **A beacon broken by something other than a player** is noticed by the beacon's own check every 100 ticks or when home is next asked for (`Pads.home`), which then falls back to the cradle.
- **Beacon lamp.** The lamp's block texture pulses slowly whatever the flashing setting, because a block texture animation cannot read it. The set, refuse and recall blinks follow the setting.
- **Hull colours.** The ship screen colours the hull red below 20 and amber below 40 with fixed numbers; they do not follow `ship.jumpMinHull` or `damageFxHull` if those are changed.
- **Recall.** A recalled ship's body hovers over the destination pad during the 80-tick hyperspace phase.
- **Jump wear** plays a small `ship_damage` burst.
- **Cuts used:** none. Cut 7 (ship upgrades) and cut 8 (the destination map's chart view) were not needed.

## Gear (W4)
- **Melee resistance and Force damage.** `EquipmentModifiers` keeps the M3 rule that damage whose direct entity is a living attacker counts as melee, so melee resistance (traits, the weave mod, Warden's Resolve) also reduces Force damage whose source names the caster as its direct entity. The vibro guard itself only blocks real melee damage types.
- **Guard drain.** The vibro and electrostaff guards drain stamina on hits that land inside the target's invulnerability frames, because the drain is worked out before vanilla discards the hit.
- **Damage clamp.** A raised guard together with heavy resistances can reach the shared damage clamp (0.25); the clamp is what stops them.
- **Long guns and the off hand.** The rifle and the repeater hide the off-hand item in third person only. In first person the off-hand item still shows and can still be used.
- **Guard bars.** The vibro guard bar and the saber guard bar share a spot under the crosshair. They never show together for long.
- **Electrostaff charge sound.** `electrostaff.charge` is a 2.45-second rise, not a loop; the arcs at the emitters show the loaded state after it ends.
- **Client-side balance.** The reticle's spread, the heat gauge and the mod tooltips use the default balance numbers, because the client never reads the server's `balance.json`.
- **Light on Your Feet** stops soul sand and honey from slowing the wearer. Spec 6.2 names ash too, but 26.2 has no ash block.
- **Droids.** The `#oldrepublic:droids` entity tag lists W6's security droid, seeker droid and Rattle. The electrostaff's extra shock damage (and W5's longer stun) also take any entity whose id contains `droid`, as a fallback for droids the tag does not list.
- **Set bonuses on the Force powers.** Warden's Resolve (Force Barrier cost), Wrath Unbound (Force Choke and Lightning cost and damage), Hunter's Tenacity (Force Speed cost) and Light on Your Feet (Force Speed duration) now reach W6's powers, through `ForcePowers.costMultiplier`, the attacker side of `EquipmentModifiers` and `SetBonuses.forceSpeedDurationMultiplier`. No test casts a power while wearing a set.
- **Charged-shot latch.** The rifle's "loaded" click reuses `workbench.slot` (see `AUDIO_BRIEF.md`).
- Client key timing and the jet pack chain's timing depend on the client harness and are not checked beyond the screenshots and `jetChain`.

## Crafting and saber (W5)
- **Player frag grenade (fixed).** It first used the trooper grenade's numbers; it now reads `crafting.playerGrenadeDamage` and `playerGrenadeRadius` (7 and 3, `c72cdda`).
- **Stun grenade on droids.** The longer droid stagger (`crafting.stunDroidTicks`) finds droids through the `#oldrepublic:droids` tag, with the entity id fallback above.
- **Client-side balance.** The bench screen and the Crafting tab show the default discipline level thresholds, not the server's `crafting.levelXp`.
- **Registration defaults.** `crafting.cutterDurability` and `repairKitUses` are read when the items are registered, so a changed value needs a full restart.
- **Untranslated errors.** Some `/oldrepublic crafting` command errors are literal English, not lang keys.
- **Saberstaff display.** The staff's held display transforms (`staff_base.json`, `staff_guard_base.json`) need tuning in game. (Milestone 7: `staff_guard_base.json` is gone; the guard uses `staff_base.json`.)
- **Trainers.** W2's four trainers stand in the hubs and use these conditions and actions; `StoryM4GameTests.commissionChargesFeeAndStandardQuality` covers a commission through the bench.
- **Cut:** crystal tuning (spec section 20). `workbench.tune` stays registered and unused.

## Chapter Four, vendors and economy (W2)
- **Discovery XP on the planets.** W2 pays the first landing on each planet and the first visit to its hub (`chapter_four.planetXp` and `hubXp`), and since the merge the M3 `Discovery` pays the first waystone (`planets.siteDiscoveryXp`) and the first greater tomb (`greaterTombDiscoveryXp`), once per site kind. Other planet sites pay no discovery XP. In all that is about 1,050 XP against the 1,200 spec 14.1 counts. See `BALANCE.md`, "Known mismatches".
- **Credits pickup sound.** `credits.pickup` plays when a vendor pays the player and at the chit and bundle exchange. Chits picked up from the ground keep the vanilla item pickup sound.
- **Vendor stock errors** are logged when a stock is first opened, not when the data loads, because in 26.2 a reload runs before item components are bound and stacks are built at first use.
- **The arrival banners.** On a first landing, W3's destination banner and W2's planet banner (`lesson_complete` 9) both show, one after the other.
- **The Chapter Four sting's subtitle.** `story.chapter_four` plays only with the "Chapter Four complete" banner (`lesson_complete` 8, sent after Kaast's ending), but its subtitle reads "Chapter Four begins". The subtitle is in the lead's `sounds.json` lang part and `events_m4.json`.
- **Hub people.** Hub people are persistent, leashed to their spawners and cannot be hurt, except a spar partner (only by its partner, never lethal) and an escort (mobs can hurt it). An escort is not saved: after a restart it is spawned again beside its owner. Zarvos's echo is not saved either.
- **Lost chapter items** (the motivator, the relic, the beacon) are re-issued at most once per in-game day, by the route that gave them.
- **Peaceful.** No hunter spawns, the clues arrive by journal on the same triggers, and entering the camp's ring goes straight to Kaast's yield.
- **Old saves.** The Milestone 3 back pay (100 per milestone, at most 300) is settled once, on the first slow tick after Milestone 4 loads; a Chapter Three save at COMPLETE enters MARKED on the player's first slow tick.
- **API checks (W2), settled:** the m4_followup probe shows `GuiGraphicsExtractor#enableScissor` transforms its rectangle by the pose, which the journal's scaled feed relies on. The m4_w2 probe and the compile confirm the stock payload's item codec, the skins' `Optional` resource lookup, `awardRecipesByKey` and the 13-field story codec.

## Force powers and foes (W6)
- **Force Speed's sprint jump** is lengthened on the client with a fixed 20%, because the client moves its own player and never reads the server's `balance.json`; `force_speed.sprintJumpPct` is not read.
- **The wrist flamer's cone** is 60 degrees across: `mandalorian.flamerConeDegrees` (30) is read as a half-angle, while `docs/design/foes.md` describes a 30-degree cone. Kaast's flamer uses the same key.
- **Hunters and troopers.** Hunters, their droids and Kaast are their own side (`Allegiance.HUNTERS`). Troopers do not attack them on sight; they fight them only when attacked or defending someone. Poachers and hunters are no longer allies.
- **Credit drops from hunters (fixed at the merge).** `CreditDrops` now asks `MandalorianHunter#mayDropLoot` and skips anything a hub spawned, so bounties and Kaast's called hunters cannot be farmed for chits.
- **`/oldrepublic c4 yield` during a fight (fixed).** The command opened the yield choice but left the fight running, so the mines, hunters and Rattle kept hurting the player and the damage closed the choice. It now yields the running fight as his health threshold does.
- **The Kaast fight is not saved.** A server stop clears every session (the gate reopens before the save). After a restart with the duel already yielded, Kaast comes back knelt. A camp placed with `kaast.placeCamp` false needs `/oldrepublic kaast camp <player>`.
- **Kaast's music** crossfades three loops and holds vanilla music off; it stops on the yield, a victory, a reset, leaving the ring, death, a level change, disconnect or the client toggle, with a 200-tick watchdog. Not checked by a test (CI has no audio).
- **The hunter's jet hop under a roof.** The landing spot comes from the `MOTION_BLOCKING_NO_LEAVES` heightmap, so under a roof within 6 blocks (a house, a low cave ceiling) the spot is the roof top; the hunter jets into the ceiling and drops when the hop time runs out.
- **The net** ends or refuses Force Speed, Barrier and jet pack bursts while it holds a player; only Push is allowed, and Push breaks it.
- **Open API checks (W6):** the m4_w6 probe and the compile confirm the rest (negative food exhaustion, the hurt-volume mixin target, `setBaseValue`, `Level#getHeight`, the flame's source with no position, the targeting selector, the meat's food builder, the rocket's rotation convention, and that a mob's home is saved). The m4_followup probe settles the two client ones: `LightningBoltRenderer#quad` writes `POSITION_COLOR` quads for `RenderTypes.lightning()` (the droid's eye and plate glow), and `LivingEntityRenderer#submit` calls `scale` after the model flip.

## Assets (all areas)
- Every Milestone 4 texture, model, particle sprite, structure template, sound and music loop is a generated placeholder, apart from the data files the asset docs mark final. All 139 new sound events (140 files), including Kaast's three music loops, play synthesised placeholder audio; `workbench.tune` is still the generic Phase 0 placeholder. The hub people's skins, the portraits and the story items are placeholders too, and there is no voice acting: the hunters' hurt, death and bark sounds are wordless by design. `textures/gui/container/lightsaber_workbench.png` is still generated but the screen no longer draws it. See `docs/ASSET_MANIFEST.md`, "Milestone 4 additions".

---

# Milestone 5 (Advanced Features)

Everything below was written from the code, the design docs (`docs/design/{flight,space,planets_m5,chapter_five,turning,powers_m5,sabers_m5,foes_m5}.md`) and the worker reports; none of it has been seen in play by a person. All eight areas are merged.

## Not yet verified (lead)
- **Nothing is seen by a person yet.** The client screenshots (`oldrepublic_m5_090` to `oldrepublic_m5_199`) prove that a scene draws, not how it looks or feels. The manual checks of spec 19.4 are all open. CI has no sound device, so no M5 sound has been heard.
- **Chapter Five playthrough results.** `PlaythroughTest` steps 81 to 103 play the chapter on the Jedi path, by the kajidic way, in the world of Chapters One to Four, with the flights by hand. At `720e8a9` 21 of the 23 Chapter Five steps pass: step 84 counted a cargo run that had completed as a miss, and step 91's crosshair met the basalt in front of the verse stone (both fixed in `2f03466`). The bot won all three of Ulvaine's phases in play there, and every step before Chapter Five passed (steps 19 and 35 on their second try); the run logs 148 shortcuts, 46 of them in Chapter Five. At `2f03466` all 23 Chapter Five steps pass, four of them with a shortcut (84, the cargo's scans; 89, the blockade; 97 and 98, Ulvaine's timeouts), while seven earlier steps fail (below); the run logs 156 shortcuts, 54 of them in Chapter Five. At `c7dab39` all 23 pass again, and only the blockade (89) needed its command: the cargo was delivered by hand on the second run, and the bot won all three of Ulvaine's phases in play, with 25 parries. 101 of the run's 103 steps pass (26 and 61 failed, below), and it logs 160 shortcuts, 51 of them in Chapter Five.
- **Earlier steps that failed in the Milestone 5 runs.** The playthrough world varies from run to run (see Milestone 3). At `2f03466` no Reliquary spot passed around the player, nor near the mentor camp (the walk back stopped 39 blocks short), so steps 45, 46 and 50 to 52 failed and Chapter Three was finished by command; in the eleven other runs it was placed. In the same run the first counterweight in the Singing Hollow's chime order did not sink to a Push in either round (step 68, as at `6b54612` in Milestone 4), so the puzzle was ended by a call; no crystal reached the pack, and step 80's count failed with it. At `720e8a9` a bed refused the bot once ("the bed is too far away", step 19) and another would not place (step 35); both passed on the second try. At `c7dab39` two steps at the mentor camp failed. Lesson two's spar (step 26) stopped after the first parry, and the log does not say why; the step has failed in four of the fourteen runs with logs, twice that way and twice with no parry landing in twelve strikes, and the lesson is then finished by command. In step 61 the walk back to the camp stopped 98 blocks short, and after the teleport the crosshair did not find Saelen in three tries, so the nav computer was loaded by a call.
- **The bot does not win the blockade.** In each of the twelve runs whose logs reach it, the pilot held its distance from the gunship's shield generator and fired whenever its nose was on it, but it does not dodge: it broke off after about 200 to 230 ticks with the hull between 18 and 24 (from about 70), the generator still at 10 to 32 of its 80 and no fighter down. Step 89 then ends the blockade with `/oldrepublic space blockade win` (PASS WITH SHORTCUT). Whether a player at level 15 with the free cannons can break it is a manual check.
- **Ulvaine in the playthrough.** Across the twelve runs whose logs reach her, the bot won phase 1 in play every time, phase 2 ten times and phase 3 seven times. Four phase 3s and two phase 2s ran out their 2,400 ticks and were moved on by command (PASS WITH SHORTCUT), and in one early run (`545bd35`) the bot died twice in phase 3. At `720e8a9` and `c7dab39` it won all three in play; at `2f03466` it spent all of phase 2 on the chanters and never fought her. A bot is no judge of how the fight plays; that is a manual check.
- **The bot is often scanned on the cargo run.** The pilot cruises straight for the buoy: it neither outruns the patrol that shadows it nor fires on it, the two ways out of a scan (spec 5.9). In six of the nine runs that flew it, a run was completed by hand (on the first try in two); in the other three the patrol's scan failed both tries, and step 84 ended the job with `/oldrepublic c5 cargo success` (PASS WITH SHORTCUT). Whether the scan is fair to a player is a manual check.

## Tests and infrastructure (lead)
- **Shortcuts in the powers client steps.** `m5/ForceClientSteps` raises the player to level 20 and rank 4, teaches the nine powers and fills the eight slots directly, and spawns the foes with their AI off so they hold still (Fear's victims get it back). Each shortcut is logged, and everything is put back afterwards.
- **The powers tests' tag parts run in full.** The `force_resistant`, `fearless`, `strong_willed` and `ship_targets` parts, which skipped until W8, W4 and W2 were merged, now run on their real members.
- **The second player in screenshot 173** is a mock player on an embedded connection. It leaves a playerdata file in the test world.
- **Shortcuts in the turning and saber client steps.** `m5/TurningClientSteps` and `m5/SaberClientSteps` set the path, rank, level, items and crafting state directly. Each shortcut is logged, and everything is put back afterwards. The turning steps spawn the mentors with their AI off so they hold still for the pictures. The Courier's Fall pedestal for shot 161 is left in the M5 client world, as the M3 steps do.
- **Real mentors during the turning steps.** While a step's mentor is registered, a loaded real mentor of the same role could be discarded as a duplicate. A fresh world has none.
- **Screenshot 161 (`wayfarer`)** showed the fall's hull, not the Compass figure: the right-click hit the hull, and the step opened the ceremony on the server (its logged fallback). The step now picks a spot that sees the pedestal; the next CI run shows whether the click lands.
- **Shortcuts in the Chapter Five playthrough** (`PlaythroughTest` steps 81 to 103, spec 19.3) are logged: the bed and the night for the call (the 24,000-tick wait is not waited out), teleports beyond 128 blocks and where a walk stops short, a teleport down to the Heart's door (the Hymnal's halls are not walked) and its opening by a call when three uses of the key on its plating do not open it (in five of the twelve runs, each time after the fight before the door had moved the bot away from it, with a teleport to a guard it could not reach), durasteel plates to weld the hull before a flight, credits for the debtor when the run is short, keep-alive effects at low health and the removal of hostiles that come near the player, the XP top-up to level 17, and the rank 4 requirements (`TurningClientSteps.readyForRankFour`). The cargo run falls back on `/oldrepublic c5 cargo success` after a second scan failure, the blockade on `/oldrepublic space blockade win` when the fight ends unwon (after 2,400 ticks, or sooner when the pilot breaks off with the hull below 25: a disabled ship loses the battle), and each Ulvaine phase on `/oldrepublic ulvaine phase` (or `yield`) after 2,400 ticks: those count as PASS WITH SHORTCUT, the Milestone 3 rule. The pilot flies by hand and falls back on the autopilot (logged) when a leg fails. A talk that closes by itself part-way (any hit closes a dialogue) is picked up once where it now stands, after the hostiles near the player are removed (logged). From Chapter Four on, a step makes room in a full pack before it gives or crafts anything: the night's junk and other vanilla odds and ends are thrown away, and the mod's spare materials go into the ender chest (logged). When the knighting (step 55) failed earlier in the run, step 102 sets rank 3 with `/oldrepublic identity rank` (logged) so the ceremony can still be played.
- **What the playthrough itself needed in Chapter Five.** The run's saturation effect went with the bot's first death (Kaast's blaster killed it on a walk at `8394ace`), so later the bot starved, and each hunger pang broke a verse channel or closed a talk (steps 92 and 103 at `8394ace`); since `c7dab39` it is given again after each death (in that run the bot died once, to Kaast's blaster on a walk, and no later step failed for hunger). By Chapter Four the pack could be full (no free slot by Chapter Five at `a82cf2e`), so the bench's ingredients, the rewards and the Hollow's crystal fell on the ground (steps 56, 59 to 63, 68 and 80 failed for it at `a82cf2e`, and steps 68 and 80 at `8394ace`); since `c7dab39` each Chapter Four step makes room first, as the Chapter Five steps do (in that run none of those steps failed for want of room, and the Hollow's crystal reached the pack). A verse stone sits among basalt, and the crosshair aimed at its centre could land on the basalt in front of it (steps 92 and 93 at `a82cf2e`, 91 at `720e8a9`); since `2f03466` the bot aims at the part of the stone its eye can see, and does not use a stand again where the crosshair missed. The cargo run reaches the buoy at 12 blocks while the pilot cruised for 8, so a run that had completed was counted as a miss (step 84 at `a82cf2e` and `720e8a9`); since `2f03466` it counts. The CI summary keeps only Chapter Five's notes, so since `2f03466` it also prints the last notes of each earlier step that failed (`WHY` lines).
- **The Sith run of spec 19.3 is not played:** one story path per world, and the playthrough's world is on the Jedi path (`DECISIONS.md`). Nor are the Fall, Return and solo raid runs, the last item of the cut order (`DECISIONS.md`).
- **`returnEscortLandsPrisonersAtGreenwatch`** runs its "landed" branch only when the test server has no Selvaren hub data; otherwise it checks the "stay aboard" branch.

## Fixed during the Chapter Five playthrough (lead)
- **Story people were removed the moment they spawned (fixed in `b16c9e1`).** Fabric's entity load event fires for a fresh spawn as well as a load from disk, and the story and mission load rules discard any loaded entity that carries a leftover tag. Five spawns put that tag on before the spawn, so each was gone at once: Chapter Five's story people (the debtor, Dask, Clan Hask's champion), the Chapter Four seeker droid and the Heartstone trial's beasts, Brenn's Chapter Three cameo and the settler mission cameo. Each now tags the entity once it is in the level. `StoryM5GameTests.storyPersonStaysOnceSpawned` and the seeker test in `StoryM4GameTests` cover it.
- **The Heartstone and the Sepulchre were easier than designed until `b16c9e1`.** Their beasts (the Heartstone's six corrupted hollowhorns, the Sepulchre's ash hounds) were removed as they spawned, so every earlier run fought only the sentinels, the Milestone 4 result of all 80 steps included. With the beasts in, the bot died twice in the Heartstone at `b16c9e1`; the run now raises its keep-alive there. Whether a player at that level can hold the cleansing is a manual check.
- **Missiles outlived a won space fight (fixed in `f90bacd`).** A missile homes for its whole life, so one fired a moment before the blockade broke could still hit the winner's ship on the way down. When a blockade breaks, and whenever an encounter ends, its ships' missiles still in the air now go out without a blast. `blockadeWavesAndGeneratorShield` checks it. The missiles were not what sent the ship to Saltgate after the win, though: the next item was.
- **A space battle forgot the ships it could not see, and they fought on alone (fixed in `5b5ca2e`).** The level finds a ship by its id only while the ship's chunk is loaded, and a battle dropped any ship it could not find at its next check. The blockade's gunship is placed 150 blocks ahead, often before its chunk has loaded, so in three of the seven runs whose logs reach the blockade (`09f2902`, `69bc2d0`, `f90bacd`) it was dropped at once, and its shield generator never joined the battle either (7 ships counted, not 8). A dropped ship belongs to no battle, so it attacks any armed ship and nothing removes it: after the blockade was won, that gunship went on firing, the ship was disabled on the way down to the holdfast, and step 90's landing became a rescue to Saltgate. A battle now keeps a ship until it is gone (destroyed, discarded, or unloaded with its chunk), flies it again once it is seen, and removes it when the battle ends, seen or not. The bolts its ships (the destroyed ones too) still have in the air now go out with it as well: in `9107065` the hull still fell from 10 to 2 in the 40 ticks after the win, most likely to bolts already on their way (inferred from the damage, two interceptor bolts). `SpaceGameTests.blockadeKeepsShipsOutOfSight` and `blockadeWavesAndGeneratorShield` cover it.

## Flight (W1)
- **The orbit's edge is covered only by server tests** (`orbitEdgeTurnsBack`, `orbitArrivalClampedInsideTheEdge`); no client test flies to it.
- **Arrivals look for a clear place only where the chunk is loaded.** Arrivals in an orbit or a sky look for a clear place, up to 16 blocks straight up and then 16 sideways, if the arrival's chunk is already loaded. A crossing goes ahead after 60 ticks, and a jump after `hyperspaceMaxWait`, loaded or not. When the chunk isn't loaded (logged), or when nothing within reach fits, as in a dense asteroid field (logged as a warning), the ship arrives at the computed place.
- **There is no crane at the refit** (steam and sparks only), and THRUSTERS has no model part.
- **Nobody has flown it.** The feel, the camera, the HUD and the mix are unchecked.
- **`M5ClientTest`'s flight step fires the story's helm and home-arrival hooks** for the test player.

## Space (W2)
- **Sky angle sign is only checked visually** by shots 110–114. At `485ab32` the moon is in all five, so the sign stands. If it ever goes missing there, the sign is reversed: use `sun_angle` 35 and `moon_angle` 282 in `dimension_type/orbit.json`, and set `OrbitSky.SUN_ANGLE` to 35.
- **Disable without a helm run:** a FLYING record with no helm run (test fixtures, or a crash before W1's recovery) is neither tumbled nor rescued by `Ships.disable`. `ShipCombat` still marks it disabled and ends its encounters.
- **Gunship model:** its turrets do not visibly turn toward their targets, though bolts leave from the turret points. The broken halves' red-hot edges are plain texture, not glow.
- **Comm lines** sit 12 px above the M4 barks, so if both show at once they stack tightly. On a short screen a long line
  starts below the crosshair instead of stacking up over it, and then runs over the barks' row and the action bar.
- **Spindle observer:** using it with a block in hand shows a client-side ghost placement for a moment; the server cancels it.
- **Fighters lost with unloading chunks:** a fighter lost because the owner flew past view distance leaves its wave without counting as a kill, and the next wave comes anyway. A patrol lost this way ends as OUTRUN. A lost gunship regroups the blockade ahead of the owner.
- **Co-op leavers:** a co-op pilot who stops piloting leaves the encounter.
- **Client steps leave the world changed:** the Spindle and its droids stay placed in the test world, and visited orbit chunks stay generated.
- **Shot 122 is assisted:** it keeps the hull topped up so the gunship's fire doesn't disable the ship and trigger `onBlockadeFailed`.
- **Rain check timing:** it reads at the test's sync point after 110 ticks, once the overworld's rain fade has stopped sending packets.

## Force powers (W6)
- **Not drawn:** Drain's greying of the target's skin (it would need a tint on every renderer) and Mind Trick's slow blink (vanilla models have no eyelids; the calm pose carries it). Neither is on the cut-order list.
- **Rage's embers** follow an approximate blade line, not the rendered item's transform.
- **Rage after a relog:** other clients see a relogged player's rage only from that player's next cast. The player's own client is right throughout.

## Paths and ranks (W5)
- **A bladeless mentor may show a saber** for up to 10 ticks after a chunk load: `Mentors.keepBladeless` strips the rebuilt saber on the next slow tick. A saved bladeless state on `MentorEntity` would fix it.
- **A prisoner killed by `/kill` or the void respawns**, at the cell or beside the player during the escort. There is no fail state for it.
- **The Wayfarer's fold-back** after 90 ticks would also fold another player's Compass figure at the same pedestal at that moment.

## Sabers, crafting and gear (W7)
- **The client uses the default balance in two places.** With a changed `saber.offhandSpeed`, the off-hand clock (`SaberDualClient`) may swing early; the server refuses the strike (`NOT_READY`) and the swing hits nothing. The Tune tab's prices and its lit button also use the defaults; the server's check and its refusal line are what count, as with the M4 workbench preview.
- **Legendary numbers on a dedicated server's clients.** The legendary hilt numbers and the quality multipliers reach clients only through statics set at server start (`CraftingBalance.applyQuality`), so clients of a dedicated server with a changed `crafting` section show the default numbers in tooltips. M4's `Quality.configure` has the same limit.
- **Main hand only:** finishers, heavy attacks, lunges and Saber Throw.
- **A saberstaff's second blade** has no retraction sound of its own.
- **The stances are drawn in third person only.**

## Worlds (W3)
- **The client steps' timing** (hub generation, and the Hymnal's 225 force-loaded chunks) has been seen on the CI machine only.
- **The seed survey has no Milestone 5 positions.** The client steps find the biomes with `findClosestBiome3d`, and they place the verse stone and the Hymnal themselves.
- **Natural placement** of the four worldgen structures is not seen by any test.
- **The Hymnal's ladder shaft** (where the ridge is thin) is only exercised by the `gapFor` rule test.
- **Open Hymnal doors are kept in memory.** After a restart they reseal when a player comes near. This is by design.
- **Ridge strikes can start fires** on Normal and Hard (vanilla behaviour); the rain puts them out.
- **The Hymnal's Heart is very dark.** Without night vision, the playthrough's view of Ulvaine's fight is near-black (its shots 166 to 175): she shows mostly by her blade. The client test's view of the Heart (screenshot 138) uses night vision. Whether the fight reads at a normal brightness setting is a manual check; the Heart may need more light.
- **Tyrvann's mire is near-black at noon** in screenshot 131 at `485ab32`: the storm that never ends darkens the sky, and only the rain streaks show. It may read better at a player's brightness setting than on CI; a person should judge it before final art.

## Chapter Five and the factions (W4)
- **Prices in dialogue text are fixed in lang:** the debtor's 200, the bribe of 150, the clan's 1,000, the cipher's 50 and the shard's 400. If the balance changes, the server charges the configured value but the text is wrong.
- **The "with the %s" lines** (Milestones 2 to 4) read "with the Clan Hask".
- **The client journal reads the balance defaults** (the duel level and the clan's price). The server's values are not sent.
- **Blockade failures:** the per-battle "already counted" mark is kept in memory only. A restart while the ship is DISABLED in orbit could count one failure again.
- **`verse_vision.ogg` is 7.2 s**, with its tail, against a 6 s vision. It is stopped early only on death, a level change or a disconnect.
- **The hologram always runs its full 240 ticks**, even if Dask's fate is chosen sooner.
- **Days use the shared overworld time**, so `/time set` moves the daily and weekly resets.
- **The client steps' Spare runs W8's real `resolve`.** Whatever W8 records outside the data the steps put back stays in the Milestone 5 client world. The story steps run last.

## Foes and bosses (W8)
- **A raider who fled alive gets no wipe line;** only the dead are told.

## Assets (all areas)
- **Force powers (W6):** all 37 new PNGs (9 particle sprites, 6 effect icons and 22 skill icons) are generated placeholders, and the 14 new sounds are synthesised placeholders. See `docs/ASSET_MANIFEST.md`, "Missing assets (Milestone 5)".
- **Paths and ranks (W5):** the `watch_holocron` texture (eight frames) is a generated placeholder, and the 3 sounds are synthesised placeholders.
- **Sabers, crafting and gear (W7):** the 16 item icons and the legendary hilt swatch, the 4 robe textures and the 4 plain humanoid layers (25 PNGs) are generated placeholders, and the 14 sounds are synthesised stand-ins.
- **Worlds (W3):** the 12 block and item textures and the 7 particle frames (19 PNGs) and the 11 structure templates are generated placeholders, and the 19 sounds are synthesised placeholders.
- **Chapter Five and the factions (W4):** the 15 skins, Vessba, the 13 portraits, the 2 faction icons and the writ (32 PNGs) are generated placeholders, and the 2 sounds are synthesised placeholders.
- **Foes and bosses (W8):** the 21 entity textures, the 7 particle frames and the 5 egg icons (33 PNGs) are generated placeholders, and the 63 sounds and the 2 music loops are synthesised placeholders.
- **Flight and ships (W1):** the 7 interceptor textures, the 5 regenerated courier textures and the 3 streak frames (15 PNGs) are generated placeholders, and the 13 sounds are synthesised placeholders.
- **Space (W2):** all 27 textures and the station template are generated placeholders, and the 26 sound files are synthesised placeholders.

---

# After Milestone 5: the owner's fixes (October 2026)

The lightsaber and Force effect work on Gabriel's first list (2026-10-21; `DECISIONS.md`, "After Milestone 5"). It was tuned from CI screenshots only (`VisualsClientTest`: stills by day and by night, in first and third person). None of it has been played, watched in motion or heard by a person.

## Not yet verified (lead)
- **The held saber was tuned from stills.** How the cuts, the hold after each one, the ease back to rest, the ready stance and the staff twirl feel at full speed is a hand-play check, as is whether a cut through the crosshair hides a close target for too long.
- **The new saber sounds have not been heard in game.** CI has no sound device. The hum's rise with the blade's speed, the swings pitched to the hum and the clash and deflect variants are untested in play.
- **There is no bloom.** The blade, its trail, the sparks and the Force light are drawn layers added to the frame, so by day they read paler than at night, and none of them lights the blocks around it (vanilla has no dynamic light).
- **The old saber sprites are unused.** The `saber_spark`, `contact_flash`, `blade_glint` and `lunge_streak` particle types stay registered (the `/particle` command still shows them) and their textures stay in the jar, but nothing spawns them.
- **The Force effects were tuned from stills too.** How they move at full speed (Lightning's crackle, Drain's writhing strands, the dust of a Speed run, a pulled creature's skid) is a hand-play check, as is their cost on a slow machine: up to 1,400 drawn shapes and larger particle counts than the Milestone 2 to 5 budgets, all still thinned by the presets.

## Force effects (F1, F2)
- **The light is weaker by day.** It is added to the frame, so on a bright sky or sand it reads paler than at night. Danger Sense's prickle is clear at night and subtle by day, where its grit carries it.
- **Bent air is drawn, not refracted.** With no distortion shader, a front of bent air is pale and dark lines and streaks; it does not warp the picture behind it.
- **Sense does not see through walls.** Its threads are depth-tested, so a sensed point behind a wall shows as a thread ending at the wall, with no outline through it (the art direction rules out glow outlines).
- **Effects on a body are placed from its box.** Stasis's frost and the grit Choke draws round the neck follow the creature's bounding box, not its animated limbs, so on a creature that moves a lot frost can sit a little off its arms.
- **The quietest pictures** are Choke's drop and Speed seen from the front (Speed widens the view, so the runner is small). From behind, the caster's own body hides part of Push's charge; a pose would show it better.
- **The look pictures have no caster in the side views.** The free camera never draws the local player, so the Lift and Choke side shots show only the target. This is a limit of the test, not of the game.
- **Slowness written into a summon's data does not slow the creature.** A husk summoned with it kept its full speed (0.23; 0.023 once the effect was given by command), so an idle one could stroll off, and in one of ten kinetic look runs a husk walked out of Lift's aim before the cast. The kinetic look now gives the effect by command. `ClientSmokeTest`, the Milestone 2 and 3 Force client steps (`m2/ForceClientSteps`) and two `PlaythroughTest` helpers still write it into the data and are left as they are; a creature there can stroll off the same way.
- **Twelve old Force particle types are unused** (`VFX_SFX_SPEC.md`, "New particle types (October 2026, 9)"). They stay registered, so the `/particle` command still shows them.

## Assets (October 2026)
- **The 39 new particle frames are generated placeholders** (27 `kinetic_*` and 12 `energy_*`, from `tools/assets/gen_textures_force_kinetic.py` and `gen_textures_force_energy.py`), as are the regenerated `held_dust` frames. The light is drawn in code and has no texture.
- **The saber sounds are synthesised.** They are original, built to the films' character, and `AUDIO_BRIEF.md` lists each file a sound designer would replace. No Force power sound changed.

---

# Milestone 6 (the structures overhaul)

Everything below was written from the code, the structure checker and the worker reports; none of it has been seen in play by a person. Milestone 6 rebuilt every structure template of Milestones 1 to 5 except the Silent Spindle, added 31 worldgen structures to the home world and the four planets, places quest, mission and planet sites hundreds of blocks away (far placement), and gave the build kit final textures. Each heading names the worker area (`docs/MILESTONE_6.md` 8.1).

## Not yet verified (lead)
- **Nobody has walked a structure.** Every template was built and checked offline: `tools/check_m6_structures.py` passes all 155 templates, and the workers walked their routes, lit their rooms and aimed their cameras with their own tools. The structure tour photographs each structure in game; a picture shows that a structure stands on its ground, not that its rooms can be walked, its puzzles solved or its fights fought. The `visuals` workflow ran all eight groups without a failure: seven at `9fb3d88` and `landmarks` at `209b48f`.
- **CI.** The client game test job failed on every run from `1405e0b` to `eb32136` and has passed on every run since `7ba0e5f`, and the server game tests failed from `1405e0b` to `c5047cd`. At `209b48f` every job passes: the checks (with the strict structure check), all 667 server game tests and the client game tests.
- **Far placement is tested on made-up ground.** `FarSitesGameTests` run the chooser on synthetic height fields and `StoryFarGameTests` check the story rules; no server test generates far chunks. The tour places its runtime sites through far placement in the seeded world, but no test plays a quest out to a far site.
- **Old saves.** A site placed before Milestone 6 stays as it was built. Only sites placed from now on use the new templates and distances.

## Tests and infrastructure (lead)
- **The tour runs in groups.** `StructureTourClientTest` is too long for every build, so it runs in the `visuals` workflow with the look `tour` (every group) or `tour_<group>`: `landmarks`, `story`, `missions`, `wilds`, `selvaren`, `varkhast`, `ghessar` or `tyrvann`. The before pictures were taken at `0bcfbb8`.
- **Every tour shot timed out (fixed in `eb32136`).** The tour asked for a render distance of 12, but the setting never reached the server, which kept sending five chunks, so every shot waited out its timeout. The client now saves its options at a render distance of 8, which sends them to the server, waits for the round view, and takes a late shot anyway with a warning.
- **Tour sites in open water (fixed in `6d260e8` and `9fb3d88`).** The tour's slots run through an ocean in the test world, so most runtime sites stood on fill in open water. They are now placed through far placement on dry ground within 256 blocks of the slot, clear of villages, and each is framed from its own origin. The cameras no longer look through reeds and tall grass, rise above trees, and take the view that sees most of the structure.
- **The structure check is strict.** Since `209b48f` CI's checks job fails on any problem `tools/check_m6_structures.py` finds; before, it ran with `--report` and only printed them.

## Fixed during integration (lead)
- **A bed broken by the encampment stamp (fixed in `1bd09fe`).** The stamp put a red bed over the Crossing's grey one head first, and the grey foot's shape update broke the new head; `StoryFarGameTests.encampmentStampKeepsWhatPlayersPut` failed on it. The overlay now goes in without shape updates.
- **The first flight to a planet landed away from its hub (fixed in `1bd09fe`).** A Greenwatch fitted to rough ground can take longer to place than the jump waits, and the ship landed on the rings around (0, 0), 118 blocks from the hub. The flight now waits up to 1,200 more ticks for a hub still being placed and takes its pad once it stands.
- **Hubs and far sites waited for chunks their ticket never loaded (fixed in `7ba0e5f`).** Both waited for the ring of chunks one beyond their ticket's radius, which a ticket of that radius does not make full, so nothing was placed until a player came near. Their tickets are now one chunk wider.

## Far placement (J1, J2)
- **On flat land a site stands near the inner edge of its band.** On level ground many spots score the same, and a tie goes to the innermost ring, so a mission site on flat land is usually about 320 to 355 blocks from the board (its band is 300 to 600).
- **A short load while a far site is built.** Far placement loads the area around a site, generating it if it is new, before it places the site: up to 17 by 17 chunks with the defaults (`far.maxTicketRadius` 7, plus one), kept for 1,200 ticks after placing. A hub takes 15 by 15 chunks. A slow server may stall for a moment when a quest or a contract asks for a site.
- **Some fallbacks wait for the next call.** When far placement finds no ground for an outpost, the near search runs the next time an outpost is asked for, not at once. A far-placed planet site's surface goes in by itself, but what follows it (the expeditions' underground piece, the Hymnal's halls, a verse stone's set-up and the site's record) waits for the site's next check.
- **The near search finds less.** It runs only when far placement fails, but it now asks for each site's full size and clearance (a 75-block square for the raider stronghold, 73 for Courier's Fall, 61 for an outpost), so in rough country it fails more often and the forward fallback in front of the player is used more. The Crossing's near search is still the Milestone 2 meeting search: it places no Crossing, and after the choice the mentor's travel camp goes on the meeting point instead of the encampment.
- **The world remembers 512 far requests.** Past that the oldest finished one is forgotten, so on a very busy server a site placed long ago may be placed again if something asks for it under the same key.

## Story sites, Echo Shrines and the ship yard (QS, QS2, J2)
- **The mentor's camp appears at 64 blocks.** The encampment is stamped over the Crossing once the player is 64 blocks away (`story.encampmentStampDistance`), still in view, so it pops in. `/oldrepublic story camp <player>` builds it at once. An encampment built that way, or stamped directly when its far stamp fails, writes no site record of its own; the Crossing's record covers the same ground.
- **What the stamp keeps.** It leaves the player's things alone: every chest and other container, any block entity the Crossing did not place (a bed, a sign) and built blocks such as planks, cobblestone, glass and doors. Dirt, stone and logs a player put there count as ground and can be replaced. Where a kept cell takes the place of half of the camp's bed or door, the other half stands alone.
- **The Independent's summons comes late.** An Independent's Chapter Three summons arrives only once Courier's Fall is placed, because it names the Fall's distance and direction.
- **Captain Dray's stronghold.** The edges of the west tower are dim, though nothing spawns there.
- **The Crossing's rest-house is dark at night** on purpose.
- **Courier's Fall's hull** can be cut away for salvage with the Fusion Cutter.
- **Echo Shrines.** A player who builds a way down can get in through the fallen roof bay and skip the veiled door.
- **The landing pad's corners** are lit by invisible light blocks over pale corner plates; no lamp shows.

## Mission sites (QM)
- **The dug-in sites read best at eye level.** The trenches of the skirmish line and the Imperial dig of the survey post look flat from above.
- **The ambush rocks' trip wire and the pressure-plate mines are decoration;** they click and do nothing.

## Overworld landmarks (O1)
- **Not walked in game.** Watchers' Rest, the Sith tomb, the wreck field and the crystal cave were checked offline, and the tour (`tour_landmarks` at `209b48f`) placed and photographed both starts of each; nobody has walked them.
- **Watchers' Rest's cache chest** is easiest to open from beside the opened seal.
- **The long-dead wreck** still gives the odd conduit spark.
- **Sand can fall into the Sith tomb's rubble room.**
- **In rare layouts an outpost may touch a Sith tomb's dig camp.** The outposts' structure set keeps 6 chunks clear of the ruins' starts (`worldgen/structure_set/outposts.json`), but a Sith tomb now reaches about 94 blocks from its start chunk. Raising the count to 8 would fix it; it was left at 6 so the seeded test worlds keep their outposts where they are.
- **Kyber clusters are much more common** in the crystal cave. They are decoration only and drop only with Silk Touch.

## Outposts and the wilds (O2, O3)
- **Outposts on hills.** An outpost is 45 blocks wide and wants fairly even ground, so in hilly country few spots pass. On steep ground its fill shows as an earth cliff or a raised strip on one side.
- **Grass on desert ramps.** In deserts and badlands the ramps and fill around the home world's structures are topped with grass and dirt: the home world's ground palette has no sand rows.
- **Trees and bamboo** from later world generation can stand inside the outposts and the wilds.
- **The war scar's lightning rods draw lightning** in storms.
- **Homesteads have no settlers.** They generate, lived-in or raided, but nobody lives in them (see Milestone 2, "Identity, dialogue and settlers").
- **No monsters spawn inside any of the wilds,** by design.
- **The wilds are worldgen only.** `/oldrepublic site place` does not know them; vanilla `/place structure oldrepublic:wilds/<name>` places one.
- **Smuggler's Cove** on a sand beach stands 1 to 3 blocks above the sea with its jetty over sand; on a cliff the jetty is a hoist pier.
- **Stilt hamlets** stand on a mud bank 1 to 2 blocks high.
- **Poachers' digs.** The dig's fill shows as a spoil bank, and its three scavengers come only once.
- **The treaty stone** cuts a terrace into a steep meadow, and its footing shows on the downhill side; where it starts by a pond it stands on footings.
- **The sunken freighter** in deep water lies fully under water, and its sand bed is held up by stone up to 6 blocks deep.

## Hubs (H, H2)
- **Hubs on rough ground.** In the tour's worlds (`9fb3d88`) no ring stand could take a hub as it was: the Greenwatch, the Proving and Saltgate were fitted to the ground (the fill under them, ramps, apron and stepped skirt), and the Holdfast, which no stand could take even fitted, stood on the raised platform on stilts, where the skirt shows in full. The pictures show how they stand; nobody has walked them.
- **A few eave cells** in Saltgate and the Holdfast are only at light 6 to 7.
- **The Proving reads dark and of one tone** in the overhead previews; it needs judging at eye level.

## Selvaren (P1)
- **The libraries' chiseled bookshelves show books but hold none,** and the barrels are empty.
- **Tarn causeways carry their own water:** in a natural lake their rim shows low; on dry land they bring their own pond.
- **The Wayfarer cache needs a flat 27-block square.** If far placement fails, the near search asks for slope 3 and can take a while.
- **Waystone courts can stand on tall footings** on steep ground.
- **Waystones no longer generate within about 256 blocks of the Greenwatch** (their set keeps clear of the hub), so the Greenwatch arc's walk to them is longer.

## Varkhast (P3)
- **The Imperial dig's cut face** shows on steep slopes.
- **The ash village has one house style.** In its bell variant some ground floors can be reached only by digging down.
- **Doorways choked with ash** are on purpose.
- **The hound den's floor is lumpy.**
- **The Imperial dig's log** on the office lectern is a blank book.
- **The detention post's cell doors** (levers on the beams opening iron doors) are not checked in game.

## Ghessar (P2)
- **Dune walls.** World generation clears about 4 blocks above the ground, so a much higher dune can stand as a sand wall at a structure's edge.
- **The mesa lookout's lift pad** can sit on the feathered rim.
- **The freighter graveyard's crane hatch** is climbed by a trapdoor over a ladder.

## Tyrvann (P4)
- **Invisible light blocks.** Tyrvann's new structures still hold a lattice of level 0 light blocks, laid to keep the planet's trees out before the trees got their survival check (`60630ff`). They give no light and show only to a player holding a light block.
- **Silenced cities are found in the mire only.**

## Dungeons and bosses (D)
- **The light check treats tinted glass as clear,** so a room lit only through tinted glass may be darker in game than the check found.

## Assets (Milestone 6)
- **The build kit's 64 blocks have final textures** (`tools/assets/gen_textures_buildkit.py`). The light panels glow on every face, their housing included.
- **The structures replace the generated placeholder templates** named in the earlier sections. They are made by scripts and checked offline; whether they are final is for the review of the tour's pictures and for hand play. See `docs/ASSET_MANIFEST.md`, "Milestone 6: the structures overhaul".

---

# Milestone 7 (the lightsaber and weapons overhaul)

Everything below was written from the code, the tests and the worker reports; none of it has been seen in play by a person. Milestone 7 gave every hilt part a material, added the boss sets, the Forge, the ores and the meteor craters, rebuilt every hilt as a 3D model, and added four weapons, four mods and 3D models in the hand for every weapon. Each heading names the worker area (`docs/MILESTONE_7.md` 13.1).

## Not yet verified (lead)
- **Nothing has been played.** The workers compiled no Java; CI does. From `3863bf6` on, the checks job and the build job (the compile and every server game test, the 69 new ones among them) pass, and the client game-test job passes at `3863bf6` and at `e816c6b` (the playthrough fails it only when stuck). At `e816c6b` the playthrough logged 108 passes and 5 fails and was never stuck: a walk ended in a fall-damage death, step 46 did not walk down into the Reliquary, step 56's first try could not place the bed, so no seeker came after the sleep (two fails; the second try passed), and at step 71 the first ambush did not come by itself. The fails are in earlier chapters (the walk between steps 43 and 44), and the lead takes them for the world varying from run to run (Milestone 3), without comparing them with earlier runs or checking whether the fall was into one of the new craters. The `visuals` runs at `3863bf6` (`hilts`, `weapons`, `ores` and the tour's `wilds` group) and at `cf9e65f` (`weapons` again) passed, and the lead reviewed their pictures (below).
- **Old saves.** A saber or part made before Milestone 7 is steel and keeps its numbers. A player who beat Kaast, Ulvaine or the Long Hymn before Milestone 7 gets that set's emitter at the boss's next occasion. Every player, old or new, starts knowing only the four crafting-table designs.

## Seen in the screenshots (lead)
The `visuals` pictures settled what only the game could show:
- **The 3D icons in the workbench projector and the Forge tab** draw under their turned and scaled poses (the API-CHECKs at `LightsaberWorkbenchScreen.java:1148` and `ForgeTab.java:553`): the projector's hilt stands upright, and the Forge's 3× icon is a little soft but clear.
- **The thrown saber** flies as its own hilt, blade lit (the API-CHECKs at `ThrownSaberFx.java:165` and `:178`).
- **The weapons in the hand** sit as intended in first and third person, the force pike upright and clear of the forearm. The electrostaff's third-person hold was changed after the first pictures ("Fixed during integration").
- **In no picture:** the poacher's muzzle glow (API-CHECK at `MuzzleGlowLayer.java:36`). If the game leaves the poacher's held stack empty, the glow takes the rifle's muzzle point, as before.

## Tests and infrastructure (lead)
- **API-CHECK comments are left in the code,** as in earlier milestones. The compile-only ones (`OreFeature.java:23` and `:29`, which the probe confirmed, `OreLook.java:191`, `HiltLook.java:474` and `:524`), those under passing tests (`OresGameTests`, `BlasterM7GameTests.java:148`, `ForgeGameTests.java:417`) and the four the pictures settled (above) can go. `SaberM7Feature.java:20` assumes Fabric's `AFTER_DAMAGE` skips a killing blow and `AFTER_DEATH` fires instead; no test kills with a duskglass saber, but if both fired the hit would still count once.
- **Not covered by a test:** an elite's kill (the drop rules are tested, the kill path is not), an NPC's saber, a sunstone hit actually setting a foe alight (tested as a chance), and a crater in real terrain: `OresGameTests.craterPlacesOnFlatGround` places one in the superflat test world (at chunk 2048, 2048), where the bowl is cut off at y -64, and that world's single biome is the only one whose deep veins are checked.
- **`HiltLook` keeps Vasch's old hilt** (the Wrapped grip) so its before and after pictures compare; in game he holds the Collector set.

## Fixed during integration (lead)
- **A retune dropped the hilt's materials (fixed in `56c34f2`).** `SaberTunings.retunedSaber` rebuilt the saber without them (HF's request).
- **The projector's hilt was turned the wrong way (fixed in `46f46b0`).** The icon draws the emitter top right and a positive turn is clockwise on screen, so the +45° turn laid the hilt flat; it is now −45°, upright, centred at y 77 for a saber and 61 for a staff (HC's request).
- **The boss drops test never saw the echo cache's emitter (fixed in `48a4bbd`).** It rolled the table with consecutive seeds, whose first draws are almost the same; the seeds are now mixed.
- **The vibro-axe's "drained, not broken" knock sounded like a break (fixed in `3863bf6`).** It plays the vibroblade's hit (0.8, pitch 0.75) instead of the Sunder (SD's request).
- **Rerunning an old generator would undo the new weapon models (fixed in `d516a97` and `3863bf6`).** `gen_textures_enemy.py` and `gen_textures_equipment.py` now skip those files (`ASSET_MANIFEST.md`, Milestone 7).
- **A stand-in's electrostaff ran through its body in third person (fixed in `cf9e65f`).** It copied the saberstaff's crosswise hold, which needs the two-handed stance only a lit saberstaff gets. It now stands upright at the side, held by its middle, as the force pike stands.
- **Most first-person weapon pictures showed an empty hand (fixed in `cf9e65f`).** The game raises a newly drawn item as its attack recharges, and a slow weapon (a blaster's bash, the vibro-axe) was still below the screen; `WeaponLook` now waits for it.
- **The Chapter Five report's subtitle read "Chapter Five begins" (fixed with these notes).** Phase 0 (`a52a05e`) regenerated `lang-parts/sounds.json` from `tools/assets/sounds/events_m5.json`, which still had Milestone 5's first wording; both now say "Chapter Five complete", as decided in Milestone 5 (`DECISIONS.md`).
- **`check_m7_ids.py` runs strictly in CI (since these notes),** as the spec asks once integration is done (12.2). It finds 0 gaps.

## Hilt stats, materials and sets (HS)
- **Tooltips show the default numbers.** Clients never read `balance.json`, so a part's or saber's tooltip, the workbench's stat bars and the Forge's stat change use the code defaults (`BALANCE.md`, "Values baked into things").
- **A thrown saber's hit gets no trait but uses up a Curator's Eye mark.**
- **A foe the Long Hymn's pulse kills** gets the Hymn's death message, not the saber's.
- **Fake players get no on-hit effects** (duskglass, sunstone, Contrition, the Long Hymn's count): the effects need the attacker in the level.

## The Forge (HF)
- **The screen counts only the credits you carry.** The server also takes credits from the hold of your ship within 32 blocks, but the Forge tab counts only the inventory, so a button can stay dim when the server would accept. The Tune tab does the same.
- **With three tabs** the workbench's header shows the shorter title "Saber Workbench".

## Hilt client and designs (HC, HE, HG, HP)
- **A lit blade hides the emitter's mouth;** the crown rims, the Cantor's fins, the Watchman's teeth and the Choir's pipes stay in sight.
- **Thin parts may shimmer at a distance:** the Cantor's fins (0.18 wide), the Choir's pipes (0.22) and the Knurled grip's checker.
- **At 16 pixels** the steel grips differ only in pattern and outline, the Balanced pommel is the least distinct and the Watchman's wear barely shows. Clan iron lowers the contrast of the Ridged, Banded and Fluted grips, and clan iron and duskglass make the Balanced and Ringed pommels murky.
- **The Sunforged grip's rings** glow on one bar each, so their diagonal faces are dark at night.
- **The Ringed pommel's D-ring** hangs in the plane through the axis, facing the thumb side, so from the sides (±z) it is seen edge-on.
- **The loose staff grip's icon lies diagonal.** HC's suggested change to stand it upright (`build.py`, `part_frame`) was not applied.

## Ores and craters (OR)
- **Starfall ore in stone can generate between y -8 and 0,** where the deep veins reach plain stone.
- **Clan iron can replace plain basalt in Tyrvann's structure walls.**
- **Trees can grow** on the old crater and on the fresh crater's ejecta.
- **Craters in cold biomes** are covered by snow and ice, and the old crater's pond looks like an oasis in a desert.
- **The deep crater is not kept to hills:** a pool cannot choose its start by biome.
- **Not seen in game:** craters on slopes, the fresh crater's smoke rising through the meteorite, the old crater's pond holding its water and the deep crater's ladder (`OreLook` 04 to 09).

## Boss drops (BD)
- **A set grip or pommel that is lost never drops again:** the record says it was given.
- **Elite drops fall at the corpse** and are not kept for the player who earned them.
- **The Reliquary grip keeps dropping** after the first, like the Collector's emitter.

## Blasters (WB)
- **One scatter blast shows several hit markers and puffs** where its pellets land together.
- **The pistol is small in third person:** its model is scaled to keep its muzzle where the Captain's glow is drawn.

## Sounds (SD)
- **Nobody has heard the 25 new sounds in game;** CI has no audio. The Penitent's and Vanguard's swings (pitch 0.65 and 0.72) may drag, and can be raised to about 0.75 in `SaberAudio.emitterPitch`. The Long Hymn's pulse rings for 3 seconds, which may tire on every fifth hit. The Choir's hum with a singing crystal is untested.

## Assets (Milestone 7)
- **The hilts and weapons are first versions made by scripts,** checked in previews and in the looks' pictures. Whether they are final is for hand play; see `docs/ASSET_MANIFEST.md`, "Milestone 7: the lightsaber and weapons overhaul". If the 3D inventory icons read worse than the flat ones, `build.py --flat-icons` brings the flat ones back.
