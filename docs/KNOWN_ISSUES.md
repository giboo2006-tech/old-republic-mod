# Known issues

Honest list of what is incomplete, rough or unverified. "Verified" in this project means one of: compiled on CI, passed a server game test, or seen in a client game test screenshot. Nothing here has been play-tested by a person yet.

## Fixed in code, pending CI
These come from the automated playthrough. The fixes are written but have not yet been compiled or run on CI; the lead confirms them there.

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
- **Fixed in code, pending CI:** the guaranteed poacher emitter used to be spent when it dropped (`got_emitter` set on drop), so a drop that landed out of reach (for example down the camp's central shaft) was never replaced and the saber could not be built from poacher loot; this was the likely cause of the playthrough STUCK at "14 emitter". `PoacherLoot.reconsiderEmitterFlag` now clears the flag on the next poacher kill when the killer carries neither an emitter nor a lightsaber, and the protected emitter drops at the killer's feet with an unlimited lifetime. `EnemyGameTests.lostEmitterCanDropAgain` covers it (pending CI). The playthrough still logs poacher deaths and emitter drops.
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

Everything below was written from the code; none of it has been seen in play. Automated test status for all of Milestone 2: **pending CI**.

## Not yet verified
- No Milestone 2 test result is recorded yet. The server game tests (`IdentityGameTests`, `StoryGameTests`, `SkillGameTests`, the extended `ForceGameTests` and `SaberGameTests`, `EnemyGameTests`) and the client screenshot test (`IdentityBuildsClientTest`, screenshots `oldrepublic_m2_10` to `59`) exist but have not been confirmed on CI.
- **The PlaythroughTest has not been extended to Chapter Two** (spec 16.3). No automated run plays the Two Voices dream, the Crossing, the lessons, Smoke on the Ridge or a trial with real inputs; the Sith and Independent runs do not exist either. Chapter Two is only covered by rule tests, site tests and command-driven screenshots.
- The manual checks of spec 16.4 are all open: feel of combo, heavy and lunge timing; Lightning readability at all four presets; audio loops (`force.jump.gather`, `force.jump.glide`, `force.heal.loop`, `force.lightning.loop`, `saber.throw_loop`, `story.two_voices`) stopping on death, logout and dimension change; two players at separate Crossings; a dedicated server with `allow-flight=false` and Soaring Leap.
- Contextual alignment detection (defended, harmed, killed innocents, cruelty) is not driven by any test; only the cap arithmetic is.
- Some `API-CHECK` comments describe runtime behaviour that a compile does not confirm:
  - `ForceJump.tickGlide`: `ServerPlayer#getLastClientInput().jump()` is the held Jump key; if not, Soaring Leap never glides.
  - `DialogueScreen`: `Screen#removed()` runs whenever the screen is replaced or closed (it ends the server session).
  - `SettlerModel`, `MentorModel`: `ModelPart` positions, rotations and `visible` are reset by `setupAnim`; if not, kneeling and role-specific robe pieces could stick.
  - `SithAcolyteRenderer`, `PoacherCaptainRenderer`: `LivingEntityRenderer#scale` runs after the model flip (kneel pose offsets); `PistolGlowLayer` assumes the 1.21 `ItemInHandLayer` arm transform.
  - `ThrownSaber`, `SaberTechniques`: bosses are recognised through the `c:bosses` convention tag.
  - `A/items/lightsaber.json` (written by `gen_textures_saber.py`) wraps the M1 definition in `minecraft:condition` on `minecraft:has_component` with a `minecraft:empty` branch; if 26.2 names these differently, the lightsaber item model fails to load.
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
