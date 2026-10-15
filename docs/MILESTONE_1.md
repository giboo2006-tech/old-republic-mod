# Milestone 1: First Playable Experience (design spec)

This is the working spec for the vertical slice. It turns section 17 of `DESIGN_BRIEF.md` into concrete, testable features. Numbers here are defaults; the live values are in `config/oldrepublic/balance.json` (written on first server start from `BalanceConfig`).

## Player journey

1. **Stirring.** A new player is Force-sensitive but nothing shows: no HUD, no powers. After they have spent 2 in-game days alive in the world, *or* the first time they sleep, the Awakening triggers.
2. **The Dream (Awakening).** On waking (or at the trigger moment if not sleeping), the screen fades to a short whispered dream: three subtitled lines and a low drone. Afterwards an uncontrolled Force burst ripples out from the player: nearby loose items and small mobs are nudged, grass and leaves shed particles, and the player is briefly slowed (shock). They receive the **Wanderer's Journal** with entry 1. The Force Energy meter and Resonance appear from this point on.
3. **Focus (meditation).** Journal entry 1 asks them to calm the disturbance. Crouching still with the journal in hand starts a meditation (focus ring fills over 8 s; moving, attacking or taking damage breaks it). Completing it teaches **Force Push (rank 1)**.
4. **Practice.** Push hostile creatures 5 times (only hits on hostile mobs count). Grants attunement XP and push mastery, and reveals entry 3: a resonance pulling them somewhere.
5. **The Calling.** While the journal is held, the player hears and sees the pull: a faint shimmer drifts in the direction of the nearest Crystal Cave and a resonance tone gets stronger the closer they face it. Server side, the direction comes from a structure search, cached per player.
6. **The Cave.** A crystal-poacher camp sits at the cave mouth (environmental story: a mercenary crew is cutting crystals out of a sacred cave). Poachers are the milestone's ranged enemy. Inside: dark tunnels, crystal formations that hum and glint, **unstable crystal clusters** that discharge when you linger next to them (hazard), and a hidden side chamber (rare loot, a hilt component).
7. **Trial of Reflection.** At the heart of the cave is a sealed chamber. The seal opens only for an awakened player who has learned Push. Kneeling at the Meditation Stone starts the trial: the chamber dims and a **Shadow Echo** of the player emerges. It mirrors the player's push and attacks. Defeating it ends the trial; the player may retry if they die or leave (the echo vanishes, nothing is lost).
8. **The Crystal.** On success, a **kyber crystal** forms on the stone, attuned to that player (only they can pick it up; it is bound to their UUID). Big resonance gain.
9. **Hilt.** Emitter, grip and pommel components come from poachers (the emitter is guaranteed by the 4th poacher kill through bad-luck protection), the hidden chamber chest, or crafting.
10. **Construction.** The **Lightsaber Workbench** (crafted) takes crystal + emitter + grip + pommel, lets the player choose blade colour (cosmetic), shows the resulting stats, and constructs their lightsaber. The journal's first chapter closes.

## Systems

### Progression (persistent player attachment, server-authoritative, synced to the owning client only)
- Force Attunement Level 1-100, XP curve `xp_to_next(L) = base * L^exponent + linear * L` (defaults 120, 1.55, 30).
- Midi-chlorian Resonance: shown as a count (starts 2,000 to 4,000, seeded per player). Gained only from milestones (awakening, first push, trial, first saber). Raises max Force Energy and regen slightly. Documented as a gameplay adaptation.
- Force Energy: max = 50 + 2 per level + resonance bonus. Regenerates 2/s after 1.5 s without spending.
- Skill points: 1 per level from level 2, plus milestone rewards.
- Ability Mastery: per ability XP, ranks at 50 / 150 / 400 uses-on-valid-targets. Mastery improves the ability slightly (radius, cost), never changes the branch.
- Anti-grind: kill XP only from hostile mobs; per-entity-type diminishing returns (each kill of the same type within the last 20 min of game time gives 15% less, floor 10%); no XP from mobs spawned by spawners that the player has killed more than 30 of; mastery only from hits on hostile living targets, 1 per target per cast.
- Death: everything is kept (copyOnDeath). Force Energy refills on respawn. Dimension change keeps everything.

### Force Push (the M1 ability)
- Key: `Use Force Power` (default R). Press-and-hold to charge (0.25 s minimum, 1 s full), release to fire. Charging shows gathered dust and plays the charge layer; releasing early below minimum cancels silently with no cost.
- Base: cone 70 degrees, range 6 blocks, knockback 1.1 to 1.8 by charge, cost 18 to 28 FE by charge, cooldown 3.5 s. Pushes mobs, players (only if PvP effects are enabled), item entities and projectiles. Line of sight is checked from the eyes to each target.
- **Branch choice (exclusive, unlocked at level 3 for 1 skill point; re-choosing costs 3 skill points):**
  - *Repulse Wave*: 130 degree cone, range 7, knockback x0.8, briefly slows targets (stagger). For crowds.
  - *Focused Blast*: 25 degree cone, range 12, knockback x1.8, extra damage if the target hits a wall. For a single dangerous foe.
  - *Deflecting Pulse*: 360 degree pulse, radius 4, knockback x0.6, reflects or destroys incoming projectiles in radius, cooldown x0.6. Defensive.
- Bosses and trial enemies have push resistance (knockback x0.4) and stagger diminishing returns.
- Counterplay: limited range, cooldown, telegraphed charge visible to others, line of sight.
- Terrain: Push never breaks blocks in M1.

### Lightsaber
- Components stored as item data: crystal colour, emitter, grip, pommel, owner name. Colour has no stat effect.
- Ignite/retract with `Toggle Lightsaber` (default G) or by using it while sneaking. Retracted: hilt does 1 damage.
- Ignited: base 7 damage, 1.6 attack speed, modified by components (emitter: Guardian +0.25 reach/-0.1 speed, Duelist +0.15 speed/-0.5 damage; grip: Wrapped +20% guard stamina, Ridged +0.1 speed; pommel: Weighted +1 damage/-0.15 speed, Balanced +10% parry window).
- **Blocking and deflection:** hold use with an ignited saber to guard. Guard stamina (100, synced) drains on blocked hits; at 0 the guard breaks (stagger 1 s, loud break sound). The first 6 ticks of a guard are the **parry window**: a blaster bolt is returned straight to its shooter at full speed and melee attackers are staggered. Outside the window bolts deflect in a random forward arc and melee damage is reduced by 70%. Guard drains more for heavier hits.

### Crystal Poacher (ranged enemy)
- Mercenary with a blaster rifle. Keeps 8 to 14 blocks from its target, strafes, backs away if the player closes in, and seeks a spot with line of sight.
- **Attack tell:** 0.6 s before each shot the rifle muzzle glows and a rising whine plays. Shots come in bursts of 2 to 3 with a recovery window after each burst.
- Blaster bolt: fast glowing projectile, 5 damage, deflectable, not affected by gravity, despawns after 3 s.
- Drops: hilt components with bad-luck protection, credits-placeholder loot (iron nuggets).

### Shadow Echo (trial enemy)
- Dark mirror of the player. Melee pressure, telegraphed lunge, and a mirrored push (also telegraphed). Push resistance x0.4. Only exists during a trial; vanishes if the player leaves the chamber or dies.

### VFX and SFX (defined per feature; see `docs/VFX_SFX_SPEC.md`)
Quality presets Reduced / Standard / High / Cinematic scale particle counts and distances; screen shake and flashing have separate toggles. Effects are triggered by compact effect-event packets and built client-side.

### Commands (operator, for testing)
`/oldrepublic progress <player>` shows state. `/oldrepublic awaken`, `/oldrepublic setlevel <n>`, `/oldrepublic grantskill`, `/oldrepublic quest reset`, `/oldrepublic locatecave`.

## Shared identifiers (contract between features)

All in the `oldrepublic` namespace. Features look each other's content up by these ids (or through the shared classes listed) instead of importing each other's classes.

| Kind | Id | Owner |
|---|---|---|
| Item | `wanderers_journal` | quest |
| Item | `kyber_crystal` (carries `oldrepublic:crystal_attunement`) | saber |
| Item | `emitter_guardian`, `emitter_duelist`, `grip_wrapped`, `grip_ridged`, `pommel_weighted`, `pommel_balanced` | saber |
| Item | `lightsaber` | saber |
| Block + item | `lightsaber_workbench` | saber |
| Item | `blaster_rifle` (poacher weapon, rendered in its hands) | enemy |
| Block + item | `kyber_cluster`, `crystal_veined_stone`, `unstable_crystal_cluster`, `resonant_seal`, `meditation_stone` | world |
| Entity | `crystal_poacher`, `blaster_bolt` | enemy |
| Entity | `shadow_echo` | world (trial) |
| Structure | `crystal_cave` (tag `#oldrepublic:crystal_caves`) | world |
| Entity type tag | `#oldrepublic:push_resistant` (bosses, trial enemies) | force (defines), world/enemy (add members) |
| Loot table | `oldrepublic:entities/crystal_poacher`, `oldrepublic:chests/crystal_cave_hidden` | enemy, world |

Shared Java contracts: `progression.Progression` (XP, energy, mastery, cooldowns), `quest.QuestHooks` (events into the quest), `network.EffectEvents` + `client.fx.ClientEffects` (effect events), `combat.CombatHooks` + `combat.DeflectableProjectile` (guards and deflection), `registry.ModComponents.CRYSTAL_ATTUNEMENT`, `world.CaveLocator`, `registry.ModRegistry` (registration + creative tab), `config.BalanceConfig` (balance sections), `client.fx.Vfx` / `CameraShake` / `client.config.ClientConfig` (quality settings).
