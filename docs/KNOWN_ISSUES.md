# Known issues

Honest list of what is incomplete, rough or unverified. "Verified" in this project means one of: compiled on CI, passed a server game test, or seen in a client game test screenshot. Nothing here has been play-tested by a person yet.

## Fixed in code, pending CI
These come from the automated playthrough. The fixes are written but have not yet been compiled or run on CI; the lead confirms them there.

- **Hidden chamber wall did not break.** The block itself is fine: the lower veined block is the same block as the one above it (no other state, no block entity, same hardness and tool), and nothing in the generator or the weathering processor changes it. The likely cause is the bot: it aimed once and then held attack while standing under the passage's ceiling cluster, whose discharge (every ~4.7 s while you linger) knocks the player back and up, which moves the crosshair off the block or out of reach and restarts or stops the mining. The bot now walks into reach before mining, re-aims every tick, steps back in when knocked away, and logs how many ticks the crosshair was on the block and the damage taken while mining.
- **Guard against poacher bolts.** No bug was found in the arc maths; the bolts that got through most likely came from poachers outside the 100 degree front arc, because the bot only faced one poacher. Two robustness fixes in `SaberGuard`: the arc is now judged from the bolt's direction of travel (vanilla moves a projectile onto its hit point before handling the hit), and a deflected or parried bolt now leaves from the blade in front of the chest instead of from wherever it touched the player. New server game tests check that bolts from up to 47 degrees off the facing (level, from above and from below) are deflected with no damage and the right stamina cost, that side and rear bolts are not, and that a fresh guard parries straight back. The playthrough now turns to face whichever poacher shows its shot tell and logs the direction of every hit that gets through.
- **Camps on slopes.** The camp now uses its own pool element (`oldrepublic:camp_pool_element`), which after placing the template fills every foundation column down to solid ground (up to 24 blocks) and removes terrain left above the cleared yard (up to 24 blocks), during world generation and per chunk, so it is deterministic. Camp poachers spawn on the camp floor (template position) and now also stay within 4 blocks of their spawn height, so they no longer climb the hillside around the camp. A server game test covers the support fill.
- **Bot could not cut down poachers with the saber.** This was the bot: it walked (poachers back off faster than that) and only swung inside 3.6 blocks. It now sprints to close in, aims at the middle of the body, swings whenever the attack is charged and the crosshair is on the target, and takes a logged teleport when the target stays out of reach on a ledge.

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
- The natural-spawn biome modifier is always registered; the spawn rule blocks spawns outside crystal caves unless `naturalSpawns` is on, and the cave itself disables monster spawns, so in practice poachers come from the camp template.
- Bolts slow down in water (vanilla projectile water inertia).
- Rifle pose and muzzle-glow position may need tuning in game.
- Balance changes reach already-loaded poachers only after they reload.

## Crystal cave and trial
- On slopes the camp's support columns and the cut above its yard are straight vertical stone and terrain faces; they look man-made on very steep ground. Support stops 24 blocks down and the clearing stops 24 blocks up, so a camp at a cliff edge or under a tall cliff can still hang or be overhung. Water next to a cleared column is not settled until a block update reaches it.
- Trees placed by later world generation steps can still grow on the camp yard.
- Camps in chunks generated before the terrain fix keep their old foundations.
- The hidden chamber's wall can only be mined from inside the reach of the passage's ceiling cluster, so mining it means taking a discharge or two unless you are quick (intended hazard, but harsh).
- An open resonant seal is open for everyone while it is open.
- Slim player skins show a one-pixel seam on the Shadow Echo's arms (it uses the wide model).
- Crystal pickup is detected by polling once per second.
- If the server crashes in the 1.5 s between the trial win and the crystal forming, after the world saved but before player data saved, a second crystal could form.

## Assets
- Every texture, model, particle sprite, structure and sound is a generated placeholder. See `docs/ASSET_MANIFEST.md` for the list and artist briefs.

