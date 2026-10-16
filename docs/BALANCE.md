# Balance configuration

All gameplay numbers that can be tuned without a rebuild live in one server-side file:

```
config/oldrepublic/balance.json
```

It is read and written by `com.oldrepublic.config.BalanceConfig`. Clients never read it; the server is authoritative.

## How the file works

- **One section per feature.** Each section is a plain Java class with public fields and default values. Milestone 1 has six sections: `progression`, `force_push`, `quest`, `saber`, `enemy` and `world`. Milestone 2 adds ten: `identity`, `story`, `skills`, `force_pull`, `force_jump`, `force_heal`, `force_lightning`, `saber_throw`, `sith_acolyte` and `poacher_captain` (and extends `saber`).
- **Defaults are filled in.** When the server first reads a section, any missing section or field is filled from the code defaults and the file is written back. So the file always lists every value that exists, but a section only appears once the server has needed it (normally as soon as a player joins and plays). Delete a key (or the whole file) to get the default back.
- **Your values win.** Values you set are merged over the defaults. Unknown keys are kept in the file but ignored. If a section cannot be parsed (for example a string where a number belongs), the server logs an error and uses the defaults for that whole section.
- **Reload.** There is no reload command. The file is re-read on every server start (`ServerLifecycleEvents.SERVER_STARTING`, and once at mod init). In singleplayer, leaving and reopening the world counts as a server start. After a reload, values are cached per section until the next start, so edits made while the server runs do nothing until it restarts.
- **Values baked into things.** A few values are copied into the world when something is created:
  - Saber attribute modifiers (damage, attack speed, reach) are stored on the item. They are rewritten from the live balance whenever the saber is ignited, retracted or built, so an edit reaches an existing saber the next time it is toggled. Sabers in the creative tab are built with the code defaults.
  - Poacher and Shadow Echo attributes are applied when the entity is created or loaded.
  - A blaster bolt's damage and lifetime are fixed when it is fired.
  - The Force Push numbers are sent to each client on join, so the Force screen shows the server's values. Since Milestone 2 every power's numbers go out in the `ability_info` payload on join and after `/reload`.
  - Acolyte and Captain attributes, and settler health, are applied when the entity is created or loaded, like the poacher's.

Units: 20 ticks = 1 second. Distances are in blocks. "Impulse" values are added to an entity's velocity in blocks per tick.

---

## `progression`

Class `com.oldrepublic.progression.ProgressionBalance`, used by `Progression`.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `maxLevel` | 100 | level | Force Attunement level cap. At the cap XP stays 0. |
| `curveBase` | 120 | XP | `curveBase` in the XP curve below. |
| `curveExponent` | 1.55 | - | Exponent in the XP curve. |
| `curveLinear` | 30 | XP | Linear term in the XP curve. |
| `skillPointsPerLevel` | 1 | points | Skill points gained per level up. |
| `resonanceStartMin` | 2000 | Resonance | Lowest starting Midi-chlorian Resonance. |
| `resonanceStartMax` | 4000 | Resonance | Upper bound (exclusive) of the starting Resonance. |
| `baseMaxEnergy` | 50 | Force Energy | Max energy at level 1. |
| `maxEnergyPerLevel` | 2 | FE per level | Extra max energy per level above 1. |
| `maxEnergyPerThousandResonance` | 6 | FE | Extra max energy per 1000 Resonance gained above the starting value. |
| `energyRegenPerSecond` | 2 | FE/s | Base energy regeneration. |
| `energyRegenPerThousandResonance` | 0.25 | FE/s | Extra regen per 1000 Resonance gained above the starting value. |
| `energyRegenDelayTicks` | 30 | ticks | Time after spending energy before regen starts (1.5 s). |
| `awakeningTicks` | 48000 | ticks | Time alive before the Awakening triggers by itself (2 in-game days). It still waits for a safe moment (see `quest.awakeningSafeTicks`). |
| `killXpPerHealth` | 1.5 | XP per HP | Kill XP = victim max health x this, before the cap and fatigue. |
| `killXpCap` | 80 | XP | Upper limit of the base kill XP. |
| `fatigueFactor` | 0.85 | multiplier | Each point of fatigue for a mob type multiplies kill XP by this. |
| `fatigueFloor` | 0.10 | multiplier | Kill XP never drops below this fraction. |
| `fatigueDecayPerMinute` | 0.75 | fatigue points | Fatigue points removed per minute (applied in one-second steps). |

### Formulas

**XP to the next level** (`Progression.xpToNext`), for level `L` below `maxLevel`:

```
xp_to_next(L) = max(1, round(curveBase * L^curveExponent + curveLinear * L))
```

With the defaults: L1 150, L2 411, L3 749, L5 1,604, L10 4,558, L20 13,067, L50 53,092, L99 151,706. Reaching level 100 takes about 6.0 million XP in total.

**Starting Resonance** is seeded from the player's UUID, so it is stable per character:
`resonanceStartMin + floorMod(uuidMostBits XOR uuidLeastBits, resonanceStartMax - resonanceStartMin)` (2000 to 3999 by default).

**Max Force Energy** (recomputed on every change):

```
gained    = max(0, resonance - startingResonance)
maxEnergy = baseMaxEnergy + maxEnergyPerLevel * (level - 1) + gained / 1000 * maxEnergyPerThousandResonance
```

**Energy regen** (only once awakened, only while below max): after `energyRegenDelayTicks` without spending, each tick adds
`(energyRegenPerSecond + gained / 1000 * energyRegenPerThousandResonance) / 20`. Energy refills completely on respawn. Creative players spend nothing.

All four Resonance milestones of Milestone 1 add 1,500 Resonance (`quest.awakeningResonance` 250 + `force_push.firstPushResonance` 250 + `quest.trialResonance` 600 + `quest.firstSaberResonance` 400), which is +9 max energy and +0.375 FE/s with the defaults.

**Kill XP** (`Progression.onDeath`). Only for kills by an awakened player of a mob that is a vanilla `Enemy` (hostile). Fatigue is tracked per entity type:

```
fatigue    = current fatigue for this mob type (starts at 0)
multiplier = max(fatigueFloor, fatigueFactor ^ fatigue)
xp         = max(1, round(min(killXpCap, victimMaxHealth * killXpPerHealth) * multiplier))
fatigue   += 1
```

Every second, each type's fatigue drops by `fatigueDecayPerMinute / 60` and is removed at 0. Example: a zombie (20 HP) gives 30 XP, then 26, 22, 18... if killed in quick succession; the floor is 3 XP.

**Ability mastery** (not configurable): rank 1/2/3 at 50/150/400 mastery points (`Progression.masteryRank`). Force Push earns 1 point per hostile living target hit per cast.

---

## `force_push`

Class `com.oldrepublic.force.PushBalance`, used by `ForcePush`, `PushShape`, `PushEffects`, `PushTargeting`, `PushCharges` and `BranchChoice`. Angles are **half-angles** in degrees (a 70 degree cone has a half-angle of 35).

| Key | Default | Unit | What it does |
|---|---|---|---|
| `minChargeTicks` | 5 | ticks | Shortest hold that fires (0.25 s). Releasing earlier cancels with no cost. |
| `fullChargeTicks` | 20 | ticks | Hold time for full charge (1 s). |
| `maxHoldTicks` | 100 | ticks | Holding longer cancels the push with no cost (5 s). |
| `baseHalfAngle` | 35 | degrees | Cone half-angle of the base push (70 degree cone). |
| `baseRange` | 6 | blocks | Range of the base push. |
| `knockbackMin` | 1.1 | blocks/tick | Impulse at zero charge. |
| `knockbackMax` | 1.8 | blocks/tick | Impulse at full charge. |
| `costMin` | 18 | FE | Energy cost at zero charge. |
| `costMax` | 28 | FE | Energy cost at full charge. |
| `cooldownTicks` | 70 | ticks | Cooldown after a cast (3.5 s). |
| `distanceFalloff` | 0.35 | fraction | Fraction of the impulse lost at the edge of the range. |
| `lift` | 0.18 | blocks/tick | Upward lift added to living targets. |
| `liftPerCharge` | 0.12 | blocks/tick | Extra lift at full charge. |
| `knockbackResistanceWeight` | 0.6 | fraction | How strongly the knockback resistance attribute reduces the push (0 ignores it). |
| `repulseHalfAngle` | 65 | degrees | Repulse Wave half-angle (130 degree cone). |
| `repulseRange` | 7 | blocks | Repulse Wave range. |
| `repulseKnockback` | 0.8 | multiplier | Repulse Wave impulse multiplier. |
| `repulseStaggerTicks` | 30 | ticks | Slowness (stagger) applied by Repulse Wave. |
| `repulseStaggerAmplifier` | 2 | effect amplifier | Slowness amplifier (2 = Slowness III). |
| `focusedHalfAngle` | 12.5 | degrees | Focused Blast half-angle (25 degree cone). |
| `focusedRange` | 12 | blocks | Focused Blast range. |
| `focusedKnockback` | 1.8 | multiplier | Focused Blast impulse multiplier. |
| `slamDamageBase` | 4 | HP | Wall-slam damage at zero charge. |
| `slamDamagePerCharge` | 4 | HP | Extra slam damage at full charge. |
| `slamMinSpeed` | 0.35 | blocks/tick | Minimum horizontal speed when hitting a wall for a slam. |
| `slamWindowTicks` | 12 | ticks | How long after the push a wall hit still counts. |
| `deflectRadius` | 4 | blocks | Deflecting Pulse radius (360 degrees). |
| `deflectKnockback` | 0.6 | multiplier | Deflecting Pulse impulse multiplier. |
| `deflectCooldownMult` | 0.6 | multiplier | Deflecting Pulse cooldown multiplier. |
| `resistantKnockbackMult` | 0.4 | multiplier | Impulse and lift multiplier for `#oldrepublic:push_resistant` entities (bosses, Shadow Echo). |
| `staggerDrWindowTicks` | 200 | ticks | Diminishing-returns window for Repulse stagger on push-resistant targets. |
| `masteryRangePerRank` | 0.04 | fraction | Range bonus per mastery rank (+4% per rank). |
| `masteryCostPerRank` | 0.05 | fraction | Cost reduction per mastery rank (-5% per rank). |
| `itemKnockbackMult` | 1.0 | multiplier | Impulse multiplier for dropped items. |
| `projectilePushSpeed` | 1.4 | blocks/tick | Minimum speed of a pushed or reflected projectile. |
| `pvpEffects` | false | boolean | Push affects other players only when this is true **and** the server allows PvP (never creative or allied players). |
| `branchUnlockLevel` | 3 | level | Level needed to choose a branch. |
| `branchFirstCost` | 1 | skill points | Cost of the first branch choice. |
| `branchRechooseCost` | 3 | skill points | Cost of switching to another branch. |
| `firstPushResonance` | 250 | Resonance | One-time Resonance milestone for the first successful push. |

### Formulas

**Charge** from hold time `h` in ticks (`PushShape.chargeFromHold`):

```
h < minChargeTicks  -> cancelled, no cost
c = clamp((h - minChargeTicks) / (fullChargeTicks - minChargeTicks), 0, 1)
```

**Shape of a cast** (`PushShape.of`), with mastery rank `r` (0 to 3) and charge `c`:

| Value | Base | Repulse Wave | Focused Blast | Deflecting Pulse |
|---|---|---|---|---|
| half-angle | `baseHalfAngle` | `repulseHalfAngle` | `focusedHalfAngle` | 180 (sphere) |
| range | `baseRange` | `repulseRange` | `focusedRange` | `deflectRadius` |
| impulse K | `lerp(c, knockbackMin, knockbackMax)` | K x `repulseKnockback` | K x `focusedKnockback` | K x `deflectKnockback` |
| cooldown | `cooldownTicks` | same | same | `round(cooldownTicks x deflectCooldownMult)` |

Then for every branch:

```
range    = range * (1 + masteryRangePerRank * r)
cost     = lerp(c, costMin, costMax) * max(0, 1 - masteryCostPerRank * r)
cooldown = max(1, cooldown)
```

With the defaults: base 70 degrees / 6 blocks / 1.1 to 1.8 / 18 to 28 FE / 70 ticks; Repulse 130 degrees / 7 / 0.88 to 1.44; Focused 25 degrees / 12 / 1.98 to 3.24; Pulse 360 degrees / 4 / 0.66 to 1.08 / 42 ticks. At mastery rank 3 range is x1.12 and cost x0.85.

A charge can only start when the player has at least the zero-charge cost in energy. The cost of the actual charge is spent on release.

**Targeting.** A target counts when its centre, eyes or feet are within `range + half its width` of the caster's eyes, inside the cone (points closer than 0.6 blocks always count) and in line of sight. Living entities, dropped items and projectiles are affected; blocks never are.

**Impulse per living target** (`PushEffects.pushLiving`), with `d` the distance to the target:

```
s = K * (1 - distanceFalloff * clamp(d / range, 0, 1))
if push-resistant: s *= resistantKnockbackMult
s *= 1 - knockbackResistanceWeight * clamp(knockbackResistanceAttribute, 0, 1)
lift = (lift + liftPerCharge * c) * (push-resistant ? resistantKnockbackMult : 1)
newVelocity = oldVelocity * 0.3 + direction' * s + (0, lift, 0)
```

`direction'` is the push direction with any downward component removed, so a push never drives a living target into the ground.

The direction is mostly along the aim (60% aim, 40% towards the target) for Base and Repulse, exactly along the aim for Focused Blast, and outward from the caster (never downward) for Deflecting Pulse.

- **Items:** velocity += direction x `K x falloff x itemKnockbackMult` + (0, 0.2, 0).
- **Projectiles:** sent along the push direction at `max(current speed, projectilePushSpeed)`. Deflecting Pulse sends blaster bolts back at their shooter and reverses other projectiles.
- **Repulse stagger:** Slowness (`repulseStaggerAmplifier`) for `repulseStaggerTicks`. On push-resistant targets, within `staggerDrWindowTicks` the 1st stagger is full, the 2nd half as long, the 3rd and later are ignored.
- **Focused Blast slam:** damage `slamDamageBase + slamDamagePerCharge x c` (4 to 8 HP by default) when the target hits a wall within `slamWindowTicks` while its horizontal speed is at least `slamMinSpeed`.
- **Mastery:** +1 point per hostile living target hit per cast.

**Branch choice** (`BranchChoice`): needs the Awakening, Force Push learned and level >= `branchUnlockLevel`. Costs `branchFirstCost` the first time and `branchRechooseCost` to switch.

---

## `quest`

Class `com.oldrepublic.quest.QuestBalance`, used by `Awakening`, `Meditation`, `Calling` and `QuestService`.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `dreamTicks` | 140 | ticks | Length of the Awakening dream before the Force burst (7 s). Taking damage ends it at once. |
| `burstRadius` | 5.0 | blocks | Radius of the uncontrolled burst that nudges items and small creatures. |
| `burstSlownessTicks` | 60 | ticks | Slowness II on the player after the burst (shock). |
| `burstMaxEntityWidth` | 0.9 | blocks | Creatures wider than this are too heavy for the burst to move. Players are never moved. |
| `awakeningSafeTicks` | 100 | ticks | The timed Awakening waits until the player has not been hurt by a mob for this long (and is on the ground, not in lava, not asleep). |
| `awakeningMinSleepTicks` | 100 | ticks | Sleeping at least this long and waking triggers the Awakening. |
| `meditationTicks` | 160 | ticks | Stillness needed to complete a meditation (8 s). |
| `meditationStartDelayTicks` | 10 | ticks | Crouching still on the ground with the journal in hand this long starts a meditation. |
| `meditationMaxDrift` | 0.15 | blocks | Maximum horizontal drift from the start point before the meditation breaks. |
| `meditationFirstXp` | 60 | XP | XP for the first meditation (which also teaches Force Push). |
| `meditationDailyXp` | 40 | XP | XP for a training meditation; at most one per game day (24000 ticks). |
| `practiceHits` | 5 | hits | Hostile targets that must be hit with Force Push in the Practice stage. |
| `practiceXp` | 150 | XP | XP for finishing Practice. |
| `callingIntervalTicks` | 50 | ticks | How often the server sends the Calling direction while the journal is held in the Calling stage. |
| `callingFarDistance` | 512 | blocks | Distance at which the Calling is faintest: `closeness = 1 - min(1, distance / callingFarDistance)`. |
| `trialXp` | 400 | XP | XP for completing the Trial of Reflection. |
| `completeXp` | 500 | XP | XP for building the first lightsaber (chapter complete). |
| `completeSkillPoints` | 2 | skill points | Skill points for completing the chapter. |
| `awakeningResonance` | 250 | Resonance | One-time milestone at the Awakening. |
| `trialResonance` | 600 | Resonance | One-time milestone for the trial. |
| `firstSaberResonance` | 400 | Resonance | One-time milestone for the first lightsaber. |

Burst push (fixed in code): items get `(0.25, 0.25 up)` and small creatures `(0.6, 0.2 up)` outward, scaled by `1 - 0.6 x distance / burstRadius`.

---

## `saber`

Class `com.oldrepublic.saber.SaberBalance`, used by `SaberStats`, `SaberGuard` and `SaberToggle`.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `baseDamage` | 7.0 | HP | Ignited damage before components (includes the player's base 1). |
| `baseAttackSpeed` | 1.6 | attacks/s | Ignited attack speed before components. |
| `retractedDamage` | 1.0 | HP | Damage of a retracted hilt. |
| `guardianReach` | 0.25 | blocks | Guardian emitter: extra reach while ignited. |
| `guardianSpeed` | -0.1 | attacks/s | Guardian emitter: attack speed change. |
| `duelistSpeed` | 0.15 | attacks/s | Duelist emitter: attack speed change. |
| `duelistDamage` | -0.5 | HP | Duelist emitter: damage change. |
| `wrappedStaminaMultiplier` | 1.2 | multiplier | Wrapped grip: guard stamina multiplier. |
| `ridgedSpeed` | 0.1 | attacks/s | Ridged grip: attack speed change. |
| `weightedDamage` | 1.0 | HP | Weighted pommel: damage change. |
| `weightedSpeed` | -0.15 | attacks/s | Weighted pommel: attack speed change. |
| `balancedParryMultiplier` | 1.1 | multiplier | Balanced pommel: parry window multiplier. |
| `guardStamina` | 100 | stamina | Guard stamina before the grip bonus. |
| `guardRegenPerSecond` | 15 | stamina/s | Stamina regeneration. |
| `guardRegenDelayTicks` | 20 | ticks | Time without a drain before stamina regenerates. |
| `parryWindowTicks` | 6 | ticks | Parry window at the start of a guard, before the pommel bonus. |
| `guardArcDegrees` | 100 | degrees | Full width of the guarded arc in front of the player. |
| `meleeReduction` | 0.70 | fraction | Melee damage removed by a successful (non-parry) block. |
| `meleeDrainBase` | 6 | stamina | Flat stamina drain per blocked melee hit. |
| `meleeDrainPerDamage` | 3 | stamina per HP | Extra drain per point of incoming damage. |
| `boltDrain` | 10 | stamina | Drain per deflected blaster bolt. |
| `deflectArcDegrees` | 50 | degrees | Spread of a deflected bolt (see below). |
| `guardBreakTicks` | 20 | ticks | Guard unusable after it breaks; also the Slowness on the defender. |
| `staggerTicks` | 20 | ticks | Stagger on a parried melee attacker. |
| `parryKnockback` | 0.6 | blocks/tick | Push-back on a parried melee attacker. |

### Formulas

**Saber stats** (`SaberStats.of`). Blade colour never matters.

```
damage   = baseDamage + (Duelist ? duelistDamage : 0) + (Weighted ? weightedDamage : 0)
speed    = max(0.1, baseAttackSpeed + (Guardian ? guardianSpeed : 0) + (Duelist ? duelistSpeed : 0)
                    + (Ridged ? ridgedSpeed : 0) + (Weighted ? weightedSpeed : 0))
reach    = Guardian ? guardianReach : 0          (only while ignited)
stamina  = guardStamina * (Wrapped ? wrappedStaminaMultiplier : 1)
parry    = parryWindowTicks * (Balanced ? balancedParryMultiplier : 1)
```

With the defaults: Guardian/Wrapped/Weighted = 8 damage, 1.35 attacks/s, +0.25 reach, 120 stamina, 6-tick parry. Duelist/Ridged/Balanced = 6.5 damage, 1.85 attacks/s, 100 stamina, 6.6-tick parry. A retracted saber does `retractedDamage` at `speed`.

**Guarding** (hold use with an ignited saber). The attacker or bolt must be within `guardArcDegrees / 2` of the player's horizontal facing. A hit counts as a parry while `ticksUsingItem < parry` (ticks 0 to 5 with 6, 0 to 6 with 6.6). Damage that bypasses shields, and hits while the player is still invulnerable from a previous hit, are not guarded.

| Incoming | Parry | Block | Guard breaks |
|---|---|---|---|
| Melee | Hit cancelled. Attacker pushed back `parryKnockback` (+0.15 up), Slowness IV and Weakness II for `staggerTicks`; an attacking player's swing charge resets. | Drain `meleeDrainBase + meleeDrainPerDamage x damage`; damage taken `x (1 - meleeReduction)`. | Full damage goes through. |
| Blaster bolt | Sent back at the shooter at the same speed (min 0.5 blocks/tick). | Drain `boltDrain`; bolt deflected in a random forward direction: yaw within +-`deflectArcDegrees`, pitch within +-`deflectArcDegrees / 2` (clamped to +-80). | Bolt destroyed. |

**Guard break** (stamina reaches 0): the guard stops, the saber gets an item cooldown of `guardBreakTicks`, the defender gets Slowness II for `guardBreakTicks`, stamina is 0 and does not regenerate until the break ends.

**Stamina regen:** `guardRegenPerSecond / 20` per tick while not guarding, not broken and at least `guardRegenDelayTicks` after the last drain. Stamina is not saved; it refills on login.

---

## `enemy`

Class `com.oldrepublic.enemy.EnemyBalance`, used by `CrystalPoacher`, `PoacherCombatGoal`, `PoacherCoverGoal` and `PoacherLoot`.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `maxHealth` | 24 | HP | Poacher max health. |
| `armor` | 2 | armor points | Poacher armor. |
| `movementSpeed` | 0.27 | attribute | Movement speed attribute. |
| `followRange` | 32 | blocks | Follow (detection) range attribute. |
| `meleeDamage` | 3 | HP | Rifle-butt bash used when a target is right on top of the poacher. |
| `minRange` | 8 | blocks | Near edge of the preferred distance band; closer than this it backs off while strafing. |
| `maxRange` | 14 | blocks | Far edge of the band; farther than this it closes in. It still shoots up to `maxRange + 4` with line of sight. |
| `retreatRange` | 5 | blocks | Below this distance it sprints away instead of strafing. |
| `retreatSpeed` | 1.25 | speed multiplier | Navigation speed while retreating. |
| `chargeTicks` | 12 | ticks | Attack tell before every shot: muzzle glow and charge whine (0.6 s). |
| `burstGapTicks` | 4 | ticks | Pause between shots of one burst, after a bolt and before the next tell (minimum 2). |
| `burstMin` | 2 | shots | Fewest shots per burst. |
| `burstMax` | 3 | shots | Most shots per burst (random in between, inclusive). |
| `recoveryMinTicks` | 40 | ticks | Shortest pause after a burst. |
| `recoveryMaxTicks` | 60 | ticks | Longest pause after a burst (random in between). |
| `boltDamage` | 5 | HP | Blaster bolt damage. |
| `boltSpeed` | 1.8 | blocks/tick | Bolt speed (no gravity). |
| `boltLifetimeTicks` | 60 | ticks | Bolt despawns after this (3 s). |
| `aimInaccuracy` | 5 | vanilla inaccuracy units | Spread at Normal difficulty; x1.5 on Easy, x0.6 on Hard. The poacher leads moving targets by half the flight time. |
| `coverHealthFraction` | 0.4 | fraction | Below this fraction of max health, a poacher hurt in the last 40 ticks looks for cover. |
| `coverSpeed` | 1.3 | speed multiplier | Navigation speed when running to cover. |
| `coverHoldMinTicks` | 40 | ticks | Shortest time spent in cover. |
| `coverHoldMaxTicks` | 60 | ticks | Longest time spent in cover. |
| `coverCooldownTicks` | 200 | ticks | Time before the poacher may seek cover again. |
| `emitterGuaranteeKill` | 3 | kills | The poacher kill (per player) on which an emitter drop is guaranteed if none has dropped yet. |
| `emitterChance` | 0.12 | probability | Chance per kill, before the guarantee, that an emitter drops. Each player gets at most one emitter this way. |
| `naturalSpawns` | false | boolean | Rare natural spawns deep underground outside crystal caves (below sea level - 8 and 12+ blocks under the surface). Camp spawns are unaffected. |

---

## `world`

Class `com.oldrepublic.world.WorldBalance`, used by `CaveLocator`, `UnstableClusterBlockEntity`, `ResonantSealBlock`, `TrialManager`, `ShadowEchoEntity`, `EchoLungeGoal` and `EchoPushGoal`.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `caveSearchRadius` | 6 | placement cells | Search radius for the nearest crystal cave. Each cell is 28 chunks, so 6 reaches roughly 2,700 blocks. |
| `caveCacheMoveBlocks` | 48 | blocks | A cached search result is reused while the player stays within this distance of where it was made. |
| `caveCacheSeconds` | 30 | seconds | A cached search result is refreshed after this long. |
| `caveHeartRefineBlocks` | 192 | blocks | Once the cave start is this close, the target is refined to the heart (trial chamber). |
| `unstableRadius` | 2.5 | blocks | Danger radius of an unstable crystal cluster (both trigger and discharge). |
| `unstableLingerTicks` | 10 | ticks | Time a non-creative, non-spectator player must stay in the radius before the cluster starts charging (checked every 4 ticks). |
| `unstableChargeTicks` | 24 | ticks | Length of the tell before the discharge (1.2 s). |
| `unstableDamage` | 4 | HP | Discharge damage to every living entity in the radius (plus an outward knock of 0.45, 0.2 up). |
| `unstableCooldownTicks` | 60 | ticks | Rest time after a discharge. |
| `sealOpenTicks` | 200 | ticks | How long an opened resonant seal stays open (minimum 20). It waits while someone stands in the doorway. |
| `chamberRadius` | 9.5 | blocks | Horizontal distance from the meditation stone that counts as inside the trial chamber. Leaving fails the trial. |
| `chamberHeight` | 6 | blocks | Vertical distance from the stone that counts as inside the chamber. |
| `trialFormTicks` | 40 | ticks | The chamber dims and the echo gathers for this long before it attacks. |
| `trialMaxTicks` | 6000 | ticks | A trial lasting longer than this fails (5 min). |
| `crystalFormDelayTicks` | 30 | ticks | Delay between the echo's defeat and the crystal forming on the stone. |
| `echoHealth` | 32 | HP | Shadow Echo max health. |
| `echoDamage` | 4 | HP | Echo melee damage. |
| `echoSpeed` | 0.31 | attribute | Echo movement speed attribute. |
| `echoArmor` | 2 | armor points | Echo armor. |
| `echoLungeCooldownTicks` | 100 | ticks | Time between lunges. |
| `echoLungeWindupTicks` | 14 | ticks | Lunge tell (crouch) before it leaps. |
| `echoLungeDamage` | 5 | HP | Lunge hit damage. |
| `echoLungeSpeed` | 0.9 | blocks/tick | Lunge speed. |
| `echoPushCooldownTicks` | 160 | ticks | Time between mirrored pushes. |
| `echoPushWindupTicks` | 16 | ticks | Push tell before release. |
| `echoPushRange` | 6 | blocks | Reach of the mirrored push. |
| `echoPushConeDegrees` | 50 | degrees | Full angle of the push cone. |
| `echoPushStrength` | 1.2 | blocks/tick | Horizontal shove of the push (plus 0.35 up). |
| `echoPushDamage` | 2 | HP | Damage of the push. |

---

# Milestone 2 sections

Ten sections were added in Milestone 2. Defaults match `docs/MILESTONE_2.md` section 13 except where "Known mismatches" says otherwise. Skill tree costs and level gates are **not** in `balance.json`: they are data files (see "Skill trees" below).

## `identity`

Class `com.oldrepublic.identity.IdentityBalance`, read through `Identity.balance()`. Alignment runs from -100 (dark) to +100 (light); reputation from -1000 to +1000; trust from 0 to 100.

| Key | Default | Unit | What it does |
|---|---|---|---|
| `lightThreshold` / `leaningThreshold` | 50 / 15 | alignment | Band edges: Light / Dark at ±50, Leaning at ±15, Balanced in between. The client always draws the default edges. |
| `tier1Alignment` / `tier2Alignment` | 15 / 40 | alignment | Gates of the Jedi (≥ +n) and Sith (≤ -n) passive nodes. |
| `minorDailyCap` | 6 | alignment | Contextual shifts per direction per in-game day (`gameTime / 24000`). |
| `minorLimit` | 60 | alignment | Contextual shifts never push alignment past ±60. |
| `severeDailyCap` | 24 | alignment | Darkness per day from killing innocents (may pass ±60). |
| `helpedOther` / `defendedInnocent` / `cruelty` / `harmedInnocent` | +1 / +1 / -1 / -2 | alignment | Contextual act amounts (minor cap). |
| `killedVillager` / `killedSettler` | -4 / -8 | alignment | Killing a villager or wandering trader / a settler (severe cap). |
| `defendWindowTicks` | 100 | ticks | A hostile that hurt an innocent this recently counts as "defended" when a player kills it. |
| `harmRepeatTicks` | 200 | ticks | Harming the same innocent (or striking the same yielded enemy) counts once per this. |
| `repHostile` / `repDistrusted` / `repTrusted` / `repHonoured` | -500 / -100 / 100 / 500 | reputation | Tier edges. |
| `joinRep` / `rivalRep` | 150 / -100 | reputation | Own and opposing faction when a path is joined (one shot). |
| `settlerKilledRep` | -100 | reputation | Frontier, per settler killed. |
| `trustStart` | 20 | trust | Chosen mentor's trust on joining. |
| `trustAttackPenalty` | 5 | trust | Lost for attacking one's mentor (at most once per 200 ticks). |
| `yieldTimeoutTicks` / `yieldLeaveDistance` / `executeConfirmTicks` | 2400 / 32 / 60 | ticks / blocks / ticks | A yielded enemy flees after the timeout or when the decider is farther away; the second (executing) strike must land within the confirm window. |
| `settlerHealth` / `settlerSpeed` | 16 / 0.5 | HP / - | Frontier Settler health; `settlerSpeed` is halved into the movement speed attribute (0.25). |
| `captiveAreaDamageMult` | 0.25 | multiplier | Damage captives take from player Force area effects (`#oldrepublic:force_harm`). |
| `settlerFleeDistance` / `settlerFleeTicks` | 24 / 600 | blocks / ticks | A fleeing settler despawns after either. |
| `dialogueEndDistance` / `dialogueChoiceDistance` | 8 / 6 | blocks | A dialogue ends beyond the first; a choice is only accepted within the second. |
| `contextualAlignment` | true | boolean | Turns contextual (minor and severe) detection off; story choices still apply. |

## `story`

Class `com.oldrepublic.quest.story.StoryBalance`, read through `StoryService.balance()`. It also holds the alignment, reputation and trust amounts of every Chapter Two choice (spec 3.2, 4.2, 6.3), which the story applies.

| Key | Default | What it does |
|---|---|---|
| `crossingDelayTicks` / `crossingMinSleepTicks` / `crossingSafeTicks` / `dreamTicks` | 24000 / 100 / 100 / 120 | Two Voices: a day after Chapter One or a sleep of ≥ 100 ticks, at a safe moment (no mob damage for 100 ticks); the dream lasts 6 s. |
| `meetingRings` / `meetingFallback` / `siteAngles` | [64, 96, 128] / [24, 40] / 16 | Meeting point search rings and angles. |
| `meetingFootprint` / `meetingClearance` | 9 / 13 | Site footprint and the area that must hold no `#oldrepublic:built_blocks`. |
| `encounterFootprint` / `encounterClearance` / `encounterFallback` | 13 / 17 / [48, 80] | The same for Smoke on the Ridge. |
| `maxSlope` / `siteMinSeparation` / `fallbackForward` | 2 / 48 / 12 | Height variation allowed; distance from other players' sites; last-resort spot ahead of the player. |
| `callingFarDistance` | 192 | Journal pull strength = 1 - distance / this. |
| `arrivalDistance` / `mentorSpawnDistance` / `mentorFaceDistance` / `mentorSpacing` | 24 / 64 / 12 / 6 | The Crossing. |
| `departDistance` / `departTimeoutTicks` | 16 / 600 | The mentor not chosen is removed this far out or after this long. |
| `crossingXp` | 400 | XP for any confirmed choice. |
| `mentorLeash` / `mentorLookDistance` / `mentorAttackCooldownTicks` | 6 / 8 / 200 | Mentor behaviour. |
| `mentorSummonTicks` / `summonMinDistance` / `summonRings` / `summonFallback` | 1200 / 48 / [8, 12] / [4, 6] | "Ask them to come": arrival delay, offered beyond 48 blocks from the camp, new camp rings. |
| `sessionRadius` | 8 | Distance to the mentor for sessions and Atone / Embrace. |
| `lostMentorTicks` / `lostMentorRadius` | 100 / 24 | A missing mentor is respawned at the anchor. |
| `lessonXp` / `lessonTrust` / `lessonRep` / `lessonTwoTrust` | 400 / 15 / 40 / 30 | Lesson rewards; trust needed for lesson two. |
| `pullLessonTargets` / `sithPullKills` / `sithPullKillWindowTicks` | 5 / 3 / 60 | Lesson one goals. |
| `parryLessonCount` / `techniqueLessonCount` | 3 / 3 | Lesson two spar goals. |
| `masterYield` / `rivalLessonYield` / `rivalTrialYield` | 0.35 / 0.4 / 0.15 | Yield fractions passed to the spars (these are the ones in effect, see mismatches). |
| `encounterMinDist` / `encounterMaxDist` / `encounterSpawnDistance` | 96 / 160 / 48 | Raider camp placement and spawn-on-approach. |
| `poachers` / `extraPerPlayer` / `extraMax` / `extraPlayerRadius` | 4 / 1 / 3 / 32 | Crew size, scaled by other awakened players nearby. |
| `captiveCount` | 3 | Captive settlers. |
| `abandonDistance` / `cleanupDistance` | 128 / 128 | Unresolved captives count as left; leftover entities removed. |
| `independentDelayTicks` | 24000 | Independents' mission starts a day after the Crossing. |
| `reckoningXp` / `reckoningSp` / `reckoningSpIndependent` | 1400 / 1 / 3 | Mission rewards. Crossing + two lessons + Reckoning (2600 XP) carry a player who ends Chapter One at level 4 to the Trial's level 6 (2660 XP needed) with a few kills. |
| `reckoningRep` / `independentFrontierRep` / `reckoningTrust` | 80 / 100 / 10 | Mission reputation and trust. |
| `captain*Alignment`, `captives*Alignment`, `brenn*Alignment` | ±12 / +3; ±10 / -3; ±15 | One-time major shifts (spare/execute/fled; free/pay/leave; spare/kill). |
| `brennSpareTrust` / `brennKillTrust` | -20 / +15 | Overseer trust for Brenn's fate. |
| `captain*Rep`, `captives*Rep` | see spec 4.2 | Faction reputation per choice and path (e.g. `captivesFreedFrontierRep` 150, `captivesPaidFrontierRep` -150, `captivesLeftFrontierRep` -60). |
| `captain*Trust`, `captives*Trust` | see spec 6.3 | Mentor trust per choice and path. |
| `trialMinLevel` / `trialMinTrust` / `trialMinRep` | 6 / 60 / 100 | Rank trial requirements (plus alignment ≥ 0 Jedi, ≤ 0 Sith). |
| `trialXp` / `trialSp` / `trialRep` / `trialRetryTicks` | 800 / 2 / 150 / 600 | Trial rewards and retry wait. |
| `jediTrialDamageScale` / `sithTrialDamageScale` | 0.6 / 1.0 | Partner damage scale in the trial spars. |
| `resonanceCrossing` / `resonanceReckoning` / `resonanceFirstRank` | 200 / 300 / 500 | Resonance milestones (+1,000 in total: +6 max FE, +0.25 FE/s). |
| `sessionXp` / `sessionTrust` / `atoneTrust` / `atoneAlignment` | 120 / 5 / 3 / 4 | Daily session and Atone / Embrace. |
| `placeStructures` | true | false: camps and the raider camp place no blocks, only entities spawn. |
| `respectSpawnProtection` / `spawnProtectionRadius` | true / 16 | Story sites keep out of a square of this radius (plus half the footprint) around world spawn. The radius is **not** read from `server.properties`; see Known issues. |

## `skills`

Class `com.oldrepublic.skill.SkillBalance`, read through `SkillService.balance()`.

| Key | Default | What it does |
|---|---|---|
| `switchPenalty` | 2 SP | Extra cost to switch inside an exclusive group (old node not refunded). Push: first branch 1, switch 3, as in Milestone 1. |
| `respecLoss` / `respecCooldownTicks` / `respecAnchorRadius` | 0.10 / 72000 / 8 | Respec refund = spent - max(1, ⌈spent × 0.10⌉); once per 3 in-game days; within 8 blocks of the path's mentor or a meditation stone. |
| `combatLockTicks` | 60 | Group switches, slot changes and respecs are refused this long after dealing or taking damage. |
| `baseSlots` / `maxSlots` | 3 / 4 | Slot 4 opens at rank 2 or with the `first_rank` milestone (No Master). |
| `deepReservesPerRank` | 8 FE | `deep_reserves`. |
| `quickenedRegenPctPerRank` / `quickenedDelayPerRank` | 10 % / 5 ticks | `quickened_flow`. |
| `centeredParryTicksPerRank` | 1 tick | `centered_mind`. |
| `calmFocusDelay` / `calmFocusMaxEnergyPct` | 10 ticks / 10 % | `calm_focus`. |
| `guardiansResolveReduction` / `guardiansResolveRadius` | 0.10 / 6 | `guardians_resolve`. |
| `furyPerRank` / `furyHealthFraction` | 0.06 / 0.5 | `fury`: damage bonus per rank below half health. |
| `hungeringEnergy` / `hungeringCooldownTicks` | 6 FE / 40 | `hungering_dark`. |
| `dreadFleeTicks` | 40 | `dread_presence`. |
| `unboundCostMult` / `unboundCooldownMult` | 0.85 / 0.9 | `unbound` (Independent) on Universal powers. |
| `opposedCostMult` | 1.5 | Side power used in the opposed alignment band (also no mastery). |

Stat passives reach `Progression` as `ForceData.counters` (`mod_max_energy`, `mod_max_energy_pct`, `mod_regen_pct`, `mod_regen_delay`), applied after the Milestone 1 formulas: `maxEnergy = (M1 maxEnergy + mod_max_energy) × (1 + mod_max_energy_pct / 100)`; regen per second `× (1 + mod_regen_pct / 100)`; regen delay `- mod_regen_delay` (never below 0).

### Skill trees (data, not `balance.json`)

`src/main/resources/data/oldrepublic/skill_tree/{universal,saber,jedi,sith}.json` hold each node's `cost` and `min_level` per rank, requirements, path and alignment gates and exclusive groups. They are a data pack: a datapack can override them, `/reload` re-sends them to clients, and `/oldrepublic skills validate` checks them. As shipped:

| Tree | Node (cost / min level per rank) |
|---|---|
| Universal | `force_push` granted; `push_*` branches 1 SP / L3; `force_pull` 2 / L4; `pull_disarm`, `pull_reel` 1 / L6; `force_jump` 2 / L5; `jump_crushing_descent`, `jump_soaring_leap` 1 / L8; `deep_reserves` 1, 1, 2 / L2, 5, 9; `quickened_flow` 1, 2 / L4, 8; `unbound` granted (Independent) |
| Lightsaber | `saber_lunge` 1 / L4; `steady_guard` 1, 1, 1 / L3, 6, 9; `deflection_control` 1 / L5; `riposte` 2 / L6; `form_soresu`, `form_ataru` 2 / L5; `saber_throw` 2 / L7; `throw_ricochet`, `throw_rending_arc` 1 / L9 |
| Jedi | `force_heal` granted; `heal_shared_light`, `heal_serene_ward` 1 / L8; `centered_mind` 1, 1 / L3, 6 (alignment ≥ 15); `calm_focus` 2 / L6 (≥ 40); `guardians_resolve` 2 / L9 (≥ 40) |
| Sith | `force_lightning` granted; `lightning_chain`, `lightning_consuming` 1 / L8; `fury` 1, 1 / L3, 6 (≤ -15); `hungering_dark` 2 / L6 (≤ -40); `dread_presence` 2 / L9 (≤ -40) |

The Milestone 1 keys `force_push.branchUnlockLevel`, `branchFirstCost` and `branchRechooseCost` were removed; the tree file and `switchPenalty` replace them. Old values left in a `balance.json` are ignored.

## Force powers: `force_pull`, `force_jump`, `force_heal`, `force_lightning`

Classes `com.oldrepublic.force.pull.PullBalance`, `force.jump.JumpBalance`, `force.heal.HealBalance`, `force.lightning.LightningBalance`. All costs and cooldowns are then multiplied by `AbilityCosts` (`unbound`, opposed alignment). Every default equals the spec (section 8 and 13); the keys are listed here with their unit.

| Section | Keys (default) |
|---|---|
| `force_pull` | `cost` 16 FE, `cooldownTicks` 120, `windupTicks` 3; `range` 12, `aimAssistHalfAngle` 8°; speed `speedBase` 0.35 + `speedPerBlock` 0.11 × distance, capped at `speedMax` 1.6; `lift` 0.25 + `liftPerBlockAbove` 0.03; `brakeDistance` 2.5, `brakeFactor` 0.3, `brakeWindowTicks` 20; `itemRadius` 2.5; `guardingMult` 0.5; `knockbackResistanceWeight` 0.6; mastery `masteryRangePerRank` / `masteryCostPerRank` 0.05; Disarming `disarmKnockMult` 0.6, `disarmTicks` 120, `acolyteGuardDrain` 40, `acolyteNoGuardTicks` 40, `pvpItemCooldownTicks` 40; Reeling `reelKnockMult` 1.2, `reelStaggerTicks` 20, `reelBonus` 0.30, `reelWindowTicks` 30; `pvpEffects` false |
| `force_jump` | `minChargeTicks` 4, `fullChargeTicks` 16, `maxHoldTicks` 60, `coyoteTicks` 3; `costMin` 14 / `costMax` 24 FE by charge, `cooldownTicks` 100; `verticalMin` 0.75 / `verticalMax` 1.15, `horizontalMin` 0.45 / `horizontalMax` 0.9 blocks/tick; `fallProtectionExtra` 4 blocks, `protectionWindowTicks` 80, `landingSlowTicks` 6; mastery `masteryMinDistance` 4, `masteryHostileRadius` 16, `masteryCostPerRank` 0.05, `masteryHeightPerRank` 0.04; Crushing Descent `crushMinFall` 3, `crushRadius` 3, damage `crushDamageBase` 3 + `crushDamagePerBlock` 0.5 per block, max `crushDamageMax` 8, `crushKnock` 0.8, `crushStaggerTicks` 15, `crushResistantMult` 0.5; Soaring Leap `soarHorizontalMult` 1.35, `soarVerticalMult` 1.1, `glideMaxFallSpeed` 0.15, `glideMaxTicks` 30, `glideCostPerTick` 0.4 FE; `pvpEffects` false |
| `force_heal` | `startupTicks` 6, `intervalTicks` 5, `maxChannelTicks` 60; `startCost` 8 + `costPerHeal` 3 FE; `healPerTick` 1.0 HP, `masteryHealPerRank` 0.10; `cooldownTicks` 200, `interruptedCooldownMult` 0.5, `interruptDamage` 4; `staggerAmplifier` 2; `moveMult` 0.5; Shared Light `sharedRadius` 6, `sharedRate` 0.6, `sharedCostMult` 1.25, `combatExcludeTicks` 200; Serene Ward `wardMinChannelTicks` 20, `wardAbsorptionTicks` 200, `wardAbsorptionAmplifier` 0, `wardStamina` 30; mastery `masteryHpPerPoint` 4, `masteryHostileRadius` 16 |
| `force_lightning` | `chargeTicks` 8, `maxChannelTicks` 80, `retargetTicks` 4; `startCost` 6 + `costPerTick` 0.75 FE; `cooldownTicks` 160, `fullChannelCooldownTicks` 200; `range` 9, `halfAngle` 20°; `damage` 1.5 HP every `damageIntervalTicks` 5, `masteryDamagePerRank` 0.08; `shockTicks` 10; self-burn `burnAfterTicks` 60, `burnDamage` 0.5; `interruptDamage` 4; vs guard `guardDamageMult` 0.2, `guardDrainPerTick` 3; `resistantDamageMult` 0.6; Chain `chainTargets` 2, `chainRange` 4, `chainDamageMult` 0.6, `chainCostMult` 1.2; Consuming `consumeRampPerSecond` 0.15, `consumeRampMax` 0.6, `consumeDrain` 0.2; `masteryPerCastMax` 5; `pvpEffects` false |

## `saber` (Milestone 2 additions)

Same class as above, `com.oldrepublic.saber.SaberBalance`; the new keys follow the Milestone 1 ones. The M1 guard stamina is now the shared saber stamina for guarding, heavy attacks and lunges.

| Group | Keys (default) |
|---|---|
| Light combo | `comboWindowTicks` 16, `comboMinStrength` 0.9, `finisherDamageMult` 1.3, `finisherKnockback` 0.45, `finisherGuardDrainMult` 2 |
| Heavy attack (Sneak + Attack) | `heavyWindupTicks` 10, `heavyDamageMult` 1.75, `heavyArcDegrees` 90, `heavyReachBonus` 0.5, `heavyMaxTargets` 3, `heavyStaminaCost` 15, `heavyCooldownTicks` 24, `heavyGuardDrainMult` 3, `heavyStaggerTicks` 20, `heavyInterruptDamage` 4, `heavyMoveMult` 0.5 |
| Lunge (Sprint + Attack) | `lungeMinRange` 3.5, `lungeMaxRange` 7, `lungeAimAssist` 6°, `lungeSpeed` 1.1, `lungeMaxTicks` 8, `lungeHitDistance` 2.5, `lungeDamageMult` 1.2, `lungeStaminaCost` 20, `lungeCooldownTicks` 60 |
| Forms | `formSwitchTicks` 20, `formCombatLockTicks` 60 |
| Form III Soresu | `soresuParryMult` 1.5, `soresuGuardArc` 150, `soresuDrainMult` 0.7, `soresuRegenMult` 1.2, `soresuDeflectAim` 15°, `soresuDamageMult` 0.85, `soresuHeavyWindupExtra` 4 ticks; no lunge |
| Form IV Ataru | `ataruSpeed` +0.15 attacks/s, `ataruStaminaMult` 0.75, `ataruParryTicks` -2, `ataruLungeCost` 12, `ataruLungeRange` 9, `ataruLungeCooldown` 40, `ataruComboWindow` 20, `ataruAerialMult` 1.4, `ataruJumpCostMult` 0.75 (read by Force Jump), `ataruMinFall` 1.5 |
| Passives | `riposteWindowTicks` 20, `riposteDamageMult` 1.4, `deflectionControlDegrees` 20, `steadyGuardPerRank` 0.10 |
| NPC guard and parry | `npcGuardArc` 120 (full width), `npcBlockDamageMult` 0.15, drain `npcDrainBase` 6 + `npcDrainPerDamage` 3 × damage, `npcBrokenTicks` 40, `npcBrokenDamageMult` 1.25, `npcParryMinStamina` 0.7, `npcParryStaggerTicks` 15 |
| Force interplay | `lightningBlockSoundInterval` 10, `pushOnGuardKnockMult` 0.5, `pushOnGuardDrain` 20 |
| Spars | `sparArenaRadius` 16, `sparPlayerLoseFraction` 0.2 |

## `saber_throw`

Class `com.oldrepublic.saber.throwing.SaberThrowBalance`.

| Keys (default) |
|---|
| `cost` 20 FE, `cooldownTicks` 160 (from the catch); `speed` 1.3, `returnSpeed` 1.5, `range` 14, `maxTicks` 60; `damageMult` 0.8 of the ignited damage, `maxTargets` 3; guard block drain `blockDrainPlayer` 15 / `blockDrainNpc` 20; Ricochet `ricochetRange` 6, `ricochetBounces` 2, `ricochetFalloff` 0.2; Rending Arc `rendingSpeed` 1.0, `rendingRange` 12, `rendingDamageMult` 1.4, `rendingStaggerTicks` 15; `masteryRangePerRank` 0.05; `orphanClearTicks` 60 |

## `sith_acolyte`

Class `com.oldrepublic.enemy.AcolyteBalance`. Also feeds the duelist profiles in `saber.duel.DuelistProfiles` (`acolyte`, `rival`, `jedi_master`, `training`).

| Group | Keys (default) |
|---|---|
| Acolyte | `health` 40, `armor` 4, `speed` 0.30, `followRange` 24, `knockbackResistance` 0.3, `damage` 6 |
| Guard and parry | `guardStamina` 60, `guardRegen` 10/s, `guardRegenDelayTicks` 30, `parryChance` 0.35 |
| Attack strings | `stringMin` 2, `stringMax` 3, `firstWindupTicks` 10, `swingGapTicks` 7, `recoveryTicks` 30 |
| Force Shove | `shoveCooldownTicks` 200, `shoveWindupTicks` 16, `shoveRange` 6, `shoveConeDegrees` 50, `shoveStrength` 1.1, `shoveLift` 0.3, `shoveDamage` 2, `shoveClashStaggerTicks` 30 |
| Brenn (`rival`) | `rivalGuardStamina` 70, `rivalTrialYield` 0.15, `rivalLessonYield` 0.4 |
| Jedi master profile | `masterHealth` 60, `masterDamage` 5, `masterGuardStamina` 120, `masterParryChance` 0.5, `masterYield` 0.35, `masterSparDamageScale` 0.6, `masterWindupTicks` 12 (see mismatches: not all are read) |
| Training profile | `trainingStrikeInterval` 50, `trainingWindupTicks` 14, `trainingDamage` 1 |

## `poacher_captain`

Class `com.oldrepublic.enemy.CaptainBalance`.

| Group | Keys (default) |
|---|---|
| Stats | `health` 36, `armor` 5, `speed` 0.28, `knockbackResistance` 0.4, `followRange` 24 |
| Vibroblade | `swingDamage` 6, `swingTellTicks` 12; overhead heavy `heavyDamage` 9, `heavyWindupTicks` 16, `heavyCooldownTicks` 100, `heavyGuardDrainMult` 2, `parryStaggerTicks` 40 |
| Blaster pistol | `pistolMinRange` 4, `pistolMaxRange` 12, `pistolTellTicks` 12, `pistolDamage` 4, `pistolCooldownTicks` 40 |
| Yield | `yieldFraction` 0.25 |

### Values outside `balance.json` (Milestone 2)

- Mentor entities: 60 max health is hard-coded in `mentor.MentorEntity.createAttributes()`.
- Loot: `data/oldrepublic/loot_table/entities/sith_acolyte.json` (1-3 iron nuggets, 10% player-kill chance of a hilt part), `poacher_captain.json` (2-5 nuggets, 15%), `gameplay/settler_thanks.json`, `gameplay/captive_payment.json`.
- Dialogue stances (one-time alignment and trust from a reply) are in `data/oldrepublic/dialogue/*.json`.
- `ClientConfig.showMinorAlignmentToasts` (client, `client.json`, default true).

---

## Known mismatches

Places where the code defaults or behaviour differ from the design docs. The code is what runs.

- Anti-grind rule "no XP from mobs spawned by spawners after 30 kills" from `MILESTONE_1.md` is not implemented.
- `docs/design/enemy.md` lists `aimSpreadDegrees` 2.5; the code key is `aimInaccuracy` with default 5 (vanilla inaccuracy units, not degrees).
- `docs/design/world.md` section 6 gives the chamber as 8 horizontal / 5 vertical; the code defaults are `chamberRadius` 9.5 and `chamberHeight` 6 (the same doc's balance list agrees with the code).
- `MILESTONE_1.md` gives max energy as "50 + 2 per level"; the code adds 2 per level **above 1**.

### Milestone 2

- `sith_acolyte.masterHealth` (60) is not read: mentor health is hard-coded to 60 in `MentorEntity`. `sith_acolyte.masterSparDamageScale` (0.6) is not read either: the Jedi trial uses `story.jediTrialDamageScale`.
- The yield fractions exist twice. `story.masterYield`, `rivalLessonYield` and `rivalTrialYield` are what the lessons and trials pass to `Spar`; the `sith_acolyte` copies only set the duelist profiles (used by `/oldrepublic saber spar`) and Brenn's out-of-spar yield (`rivalTrialYield`). Change both to be safe.
- Dialogue stances: the spec (3.2) asks for 3 or 4 stance answers per path at ±3 to ±5 alignment. As built, each Crossing dialogue has one creed question at ±2 alignment and ±3 trust; the mentor dialogues have two trust-only questions (±3).
- The spec's `respec_day` is stored as `last_respec` (game time); the cooldown is the same 72,000 ticks.
- `story.spawnProtectionRadius` is an addition (the spec reads the server's spawn protection, which 26.2 does not expose).

