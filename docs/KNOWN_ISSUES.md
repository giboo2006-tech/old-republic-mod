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
- Hand transforms of the 3D saber model were tuned from client test screenshots only; they may still need adjusting by hand.
- The swing trail follows an approximate arc, not the exact animated hand.
- The blade extension animates in 7 per-tick steps without partial-tick smoothing.
- Explosions hitting a guarding player from the front are reduced like melee hits.
- With the saber in the off hand, `G` toggles it but guarding uses whichever hand the use action comes from.

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
  - `SithAcolyteRenderer`, `PoacherCaptainRenderer`: `LivingEntityRenderer#scale` runs after the model flip (kneel pose offsets); `PistolGlowLayer` assumes the 1.21 `ItemInHandLayer` arm transform.
  - `ThrownSaber`, `SaberTechniques`: bosses are recognised through the `c:bosses` convention tag.
  - `Settlers`: the loot calls for `settler_thanks` / `captive_payment` (`reloadableRegistries().getLootTable`, `LootParams` with the chest set).
  - The others (`Vec3.CODEC`, `UUIDUtil.STREAM_CODEC`, attribute modifier methods, `Entity#is(TagKey)`, `DamageSources#generic`, `TamableAnimal#isTame`, `CommandSourceStack#getTextName`) are signature checks that a green compile settles.

## Identity, dialogue and settlers
- **Spawn protection radius comes from the balance file, not `server.properties`.** 26.2 exposes no getter for the server's spawn-protection radius, so story sites avoid a square of `story.spawnProtectionRadius` (default 16) plus half the footprint around world spawn. A server with a larger protection radius must raise this value by hand.
- Frontier Settlers only exist as Smoke on the Ridge captives or through `/oldrepublic identity settler free`. There are no natural settlers or homesteads in M2, so the Frontier tier effects (thanks bundle, settlers fleeing a distrusted player) are only seen with those.
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
- Swing trails are third person only and follow an approximated blade line; NPC trails use the entity's main arm.
- After a crash while a saber is thrown, the `saber_thrown` component is cleared 60 ticks after the stack is next ticked in an inventory; until then the saber cannot be used.

## Chapter Two
- Meeting and encounter sites are only searched in loaded chunks. In rough terrain the fallbacks can put the meeting point 24 to 40 blocks away, or 12 blocks in front of the player.
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
- **Placeholders.** Every site template (Jedi Ruin, Sith Tomb, Wreck Field, both outposts, the three Echo Shrines) is a generated placeholder, with `site_anchors` sidecars. See `docs/ASSET_MANIFEST.md`.
- **Command placement.** `/oldrepublic site place <structure>` places at the chunk corner, in any biome, and registers the start. A shrine placed this way has no owner, so its guard is shared.
- **Loaded chunks only.** Runtime outposts and shrines go only into loaded chunks; a failed search retries later.
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
- **Hessa Dray twice did not appear.** In two CI runs (`db74f96` and `f49864c`) the bot found no R2 chief at the post, though the mission had marked the chief done; Hessa, spared in Chapter Two, should have stood there. The camp was on its ring both times, so this is not the camp bug below, and the cause was not found. `Cameos.tickChief` now logs a warning when she does not spawn, and the mission hands the record over without her. She has appeared in the four runs since.
- **Forward-fallback mission camps (fixed).** When neither the mission rings nor their fallback found a spot (often with a small view distance), the forward fallback stamped the camp with no site checks. In one playthrough (`ac2e508`) the R2 cache landed inside the Republic outpost's box, and the camp lookup then answered with the outpost, which has no crate, so the record could only come from Hessa. `34c4ecd` had the same symptom with the cache at the same spot. The fallback now tries rings of 16 to 40 blocks with every site check, and stamps the forward spot only when it is clear of other sites and builds; otherwise the mission runs around the bare spot, with its people still spawned there. The lookup answers only the mission's own camp (`1e8eabb`, server test `missionCampLookupIgnoresAnOverlappingOutpost`).
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
- **Reliquary rings and small view distances.** The Reliquary's rings (96, 128 and 144 blocks) need loaded chunks. With a small view distance they are not loaded, so placement falls back to the 48 and 80 rings, and when those fail it tries again later as the player moves. On a server with a view distance below about 9 the Reliquary will often sit on the fallback rings.
- **Phase 2 length.** In the last six runs the bot won phase 2 three times (in 1,529, 2,374 and 2,822 ticks) and ran out of time three times, with Vasch at 143, 152 and 171 of the 135 needed (the budget is 2,400 ticks, and since `ac2e508` up to 3,600 after progress). It loses time walking to floor spots under the gallery and on Pulls that miss. Whether the phase is too long for a person is a question for hand play.
- **Away timer.** A participant's away timer runs only outside the whole Reliquary, so a participant at the antechamber bed stays listed.
- **Runtime API-CHECKs:** `Entity#teleportTo` on an `ItemEntity` (the reset moves items to the antechamber) is assumed to keep their owner and pickup delay; `LivingEntityRenderer#scale` is assumed to run after the model flip in `OrrenVaschRenderer` and `RuinSentinelRenderer`.

## Assets (all areas)
- Every Milestone 3 texture, model, particle sprite, structure template and sound is a generated placeholder, except the Reliquary layout (final layout, placeholder blocks) and the singing crystal (final art for M3). All 121 new sound ids play synthesised placeholder audio. There is no voice acting: Vasch, the troopers and the officers have none. See `docs/ASSET_MANIFEST.md`, "Missing assets".

---

# Milestone 4 (Broader Content)

Everything below was written from the code, the design docs (`docs/design/{planets,chapter_four,vendor,travel,gear,crafting,saber,force_m4,foes}.md`) and the worker reports; none of it has been seen in play by a person. All six areas are merged: W1 worlds, W2 Chapter Four, vendors and economy, W3 ships and travel, W4 gear, W5 crafting and saber, and W6 Force Speed, Barrier and Choke, the foes and Kaast. Each heading names the owner area (spec 15.2).

## Not yet verified (lead)
- **Nothing is seen by a person yet.** The client screenshots (`oldrepublic_m4_10` to `76`) prove that a scene draws, not how it looks or feels. The manual checks of spec 17.4 are all open, including every effect at the four quality presets and audio loops stopping on death, logout and dimension change. CI has no sound device.
- **No playthrough covers Milestone 4.** The Chapter Four extension of `PlaythroughTest` (17.3) is not written, so the chapter has never run from the mark to the report in one world. `StoryM4GameTests` checks its rules one at a time, mostly by setting the stage directly.
- **Not driven by any test:** planet drops, the duskglass cut and budding growth; the Heartstone trial and the expedition puzzles (the Hollow's chime order, the rift bleed, the cache's sinking platforms); respawn at the hub medbay; fitting and removing ship upgrades; the fusion cutter's held use (the plating test calls `SealedPlatingBlock.cut` directly) and the repair kit; throwing a player grenade; the staff throw; the Heartstone and Sepulchre trials and the arc missions in the world; the `escort` and `spar` contracts; natural hollowhorn and ash hound spawns; a whole Kaast fight from the ring to the yield (the tests and client steps set his phase).
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
- **Placeholders.** Every planet template (waystones, greater tomb pieces, both hubs, the Sepulchre, the Heartstone, the expeditions, the Wayfarer cache, the hunting camp and the ambush rocks) is generated by `tools/assets/gen_structures_m4.py`, with `site_anchors` sidecars.
- **Weather on the planets (fixed, `7362217`).** The probes showed that every level that can have weather counts down the shared weather timers, so with both planets loaded the weather cycle ran about three times faster. A mixin (`PlanetWeatherCycleMixin`) now lets only the overworld advance them; the planets still ease their own rain toward the shared state, so their storms follow the home world's weather. Sleeping through rain on a planet clears it everywhere, as sleeping at home does. CI's storm shot (12) still shows the Varkhast storm with ashfall after the fix.
- **Ambience is a client emitter.** The biome beds and the Varkhast ashfall come from client emitters (`PlanetAmbience`), not from the biomes' ambience data, because 26.2 changed that data (`DECISIONS.md`). They follow the `planetAmbience` client option, and so do the hub beds.
- **Client-side balance.** `planets.ashfallDensity` and `riftBleedTicks` (the length of the rift whispers) are read on the client from its own file or the defaults, so a dedicated server's values do not reach players. The server still decides how long the rift crystal must be held.
- **Hub people.** The hub's people spawn from `hub_spawner` blocks through factories other areas register (`HubSpawners.register`). A role with no factory logs one warning and stays empty.
- **Hub search.** When no ring has a bare 63 by 63 square, the rings are searched again with trees allowed and the hub's box is cleared first; the stilt platform at (0, 0) is the last resort.
- **Expedition underground pieces** are stamped under the surface piece even when the sunken placement checks refuse.
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
- **Saberstaff display.** The staff's held display transforms (`staff_base.json`, `staff_guard_base.json`) need tuning in game.
- **Trainers.** W2's four trainers stand in the hubs and use these conditions and actions; `StoryM4GameTests.commissionChargesFeeAndStandardQuality` covers a commission through the bench.
- **Cut:** crystal tuning (spec section 20). `workbench.tune` stays registered and unused.

## Chapter Four, vendors and economy (W2)
- **Discovery XP on the planets.** W2 pays the first landing on each planet and the first visit to its hub (`chapter_four.planetXp` and `hubXp`). Nothing pays discovery XP for waystones, other planet sites or greater tombs: the M3 `Discovery` pays only for M3 site kinds, and the `planets` discovery keys are read by no code. Spec 14.1 counts about 1,200 XP of planet discovery in the chapter's budget, so a player levels a little slower than planned. See `BALANCE.md`, "Known mismatches".
- **Credits pickup sound.** `credits.pickup` plays when a vendor pays the player and at the chit and bundle exchange. Chits picked up from the ground keep the vanilla item pickup sound.
- **Vendor stock errors** are logged when a stock is first opened, not when the data loads, because in 26.2 a reload runs before item components are bound and stacks are built at first use.
- **The arrival banners.** On a first landing, W3's destination banner and W2's planet banner (`lesson_complete` 9) both show, one after the other.
- **The Chapter Four sting's subtitle.** `story.chapter_four` plays only with the "Chapter Four complete" banner (`lesson_complete` 8, sent after Kaast's ending), but its subtitle reads "Chapter Four begins". The subtitle is in the lead's `sounds.json` lang part and `events_m4.json`.
- **Hub people.** Hub people are persistent, leashed to their spawners and cannot be hurt, except a spar partner (only by its partner, never lethal) and an escort (mobs can hurt it). An escort is not saved: after a restart it is spawned again beside its owner. Zarvos's echo is not saved either.
- **Lost chapter items** (the motivator, the relic, the beacon) are re-issued at most once per in-game day, by the route that gave them.
- **Peaceful.** No hunter spawns, the clues arrive by journal on the same triggers, and entering the camp's ring goes straight to Kaast's yield.
- **Old saves.** The Milestone 3 back pay (100 per milestone, at most 300) is settled once, on the first slow tick after Milestone 4 loads; a Chapter Three save at COMPLETE enters MARKED on the player's first slow tick.
- **Open API check (W2):** whether `GuiGraphicsExtractor#enableScissor` transforms its rectangle by the pose (the journal's scaled feed clips with it); a query is in `tools/api-probe/m4_followup.txt`. The m4_w2 probe and the compile confirm the stock payload's item codec, the skins' `Optional` resource lookup, `awardRecipesByKey` and the 13-field story codec.

## Force powers and foes (W6)
- **Force Speed's sprint jump** is lengthened on the client with a fixed 20%, because the client moves its own player and never reads the server's `balance.json`; `force_speed.sprintJumpPct` is not read.
- **The wrist flamer's cone** is 60 degrees across: `mandalorian.flamerConeDegrees` (30) is read as a half-angle, while `docs/design/foes.md` describes a 30-degree cone. Kaast's flamer uses the same key.
- **Hunters and troopers.** Hunters, their droids and Kaast are their own side (`Allegiance.HUNTERS`). Troopers do not attack them on sight; they fight them only when attacked or defending someone. Poachers and hunters are no longer allies.
- **Credit drops from hunters (fixed at the merge).** `CreditDrops` now asks `MandalorianHunter#mayDropLoot` and skips anything a hub spawned, so bounties and Kaast's called hunters cannot be farmed for chits.
- **The Kaast fight is not saved.** A server stop clears every session (the gate reopens before the save). After a restart with the duel already yielded, Kaast comes back knelt. A camp placed with `kaast.placeCamp` false needs `/oldrepublic kaast camp <player>`.
- **Kaast's music** crossfades three loops and holds vanilla music off; it stops on the yield, a victory, a reset, leaving the ring, death, a level change, disconnect or the client toggle, with a 200-tick watchdog. Not checked by a test (CI has no audio).
- **The hunter's jet hop under a roof.** The landing spot comes from the `MOTION_BLOCKING_NO_LEAVES` heightmap, so under a roof within 6 blocks (a house, a low cave ceiling) the spot is the roof top; the hunter jets into the ceiling and drops when the hop time runs out.
- **The net** ends or refuses Force Speed, Barrier and jet pack bursts while it holds a player; only Push is allowed, and Push breaks it.
- **Open API checks (W6):** the m4_w6 probe and the compile confirm the rest (negative food exhaustion, the hurt-volume mixin target, `setBaseValue`, `Level#getHeight`, the flame's source with no position, the targeting selector, the meat's food builder, the rocket's rotation convention, and that a mob's home is saved). Still open on the client: `RenderTypes.lightning()` drawing quads in `POSITION_COLOR` (the droid's eye and plate glow) and `LivingEntityRenderer#scale` running after the model flip. Queries for both are in `tools/api-probe/m4_followup.txt`.

## Assets (all areas)
- Every Milestone 4 texture, model, particle sprite, structure template, sound and music loop is a generated placeholder, apart from the data files the asset docs mark final. All 139 new sound events (140 files), including Kaast's three music loops, play synthesised placeholder audio; `workbench.tune` is still the generic Phase 0 placeholder. The hub people's skins, the portraits and the story items are placeholders too, and there is no voice acting: the hunters' hurt, death and bark sounds are wordless by design. `textures/gui/container/lightsaber_workbench.png` is still generated but the screen no longer draws it. See `docs/ASSET_MANIFEST.md`, "Milestone 4 additions".
