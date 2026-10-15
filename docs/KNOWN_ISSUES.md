# Known issues

Honest list of what is incomplete, rough or unverified. "Verified" in this project means one of: compiled on CI, passed a server game test, or seen in a client game test screenshot. Nothing here has been play-tested by a person yet.

## Not yet verified in game
- Nothing has been hand-played. The automated client test drives the awakening, Force Push, the Force and journal screens, the workbench, saber ignition and guard, a poacher fight, the cave and the trial, and a dedicated-server join, but it cannot judge feel, balance or audio (the CI machine has no sound device).
- Natural cave generation and `/oldrepublic locatecave` are not exercised by the tests; the test places the cave's heart piece with `/place template`.
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
- Hand transforms of the 3D saber model are first guesses and need tuning in game.
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
- The camp can overhang on steep slopes (no terrain adaptation).
- An open resonant seal is open for everyone while it is open.
- Slim player skins show a one-pixel seam on the Shadow Echo's arms (it uses the wide model).
- Crystal pickup is detected by polling once per second.
- If the server crashes in the 1.5 s between the trial win and the crystal forming, after the world saved but before player data saved, a second crystal could form.

## Assets
- Every texture, model, particle sprite, structure and sound is a generated placeholder. See `docs/ASSET_MANIFEST.md` for the list and artist briefs.

## Project
- No license file yet; waiting for the owner's choice (All Rights Reserved or MIT).
