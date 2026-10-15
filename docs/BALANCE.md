# Balance configuration

All gameplay numbers that can be tuned without a rebuild live in one server-side file:

```
config/oldrepublic/balance.json
```

It is read and written by `com.oldrepublic.config.BalanceConfig`. Clients never read it; the server is authoritative.

## How the file works

- **One section per feature.** Each section is a plain Java class with public fields and default values. The six sections are `progression`, `force_push`, `quest`, `saber`, `enemy` and `world`.
- **Defaults are filled in.** When the server first reads a section, any missing section or field is filled from the code defaults and the file is written back. So the file always lists every value that exists, but a section only appears once the server has needed it (normally as soon as a player joins and plays). Delete a key (or the whole file) to get the default back.
- **Your values win.** Values you set are merged over the defaults. Unknown keys are kept in the file but ignored. If a section cannot be parsed (for example a string where a number belongs), the server logs an error and uses the defaults for that whole section.
- **Reload.** There is no reload command. The file is re-read on every server start (`ServerLifecycleEvents.SERVER_STARTING`, and once at mod init). In singleplayer, leaving and reopening the world counts as a server start. After a reload, values are cached per section until the next start, so edits made while the server runs do nothing until it restarts.
- **Values baked into things.** A few values are copied into the world when something is created:
  - Saber attribute modifiers (damage, attack speed, reach) are stored on the item. They are rewritten from the live balance whenever the saber is ignited, retracted or built, so an edit reaches an existing saber the next time it is toggled. Sabers in the creative tab are built with the code defaults.
  - Poacher and Shadow Echo attributes are applied when the entity is created or loaded.
  - A blaster bolt's damage and lifetime are fixed when it is fired.
  - The Force Push numbers are sent to each client on join, so the Force screen shows the server's values.

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
newVelocity = oldVelocity * 0.3 + direction * s + (0, lift, 0)
```

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
| `emitterGuaranteeKill` | 4 | kills | The poacher kill (per player) on which an emitter drop is guaranteed if none has dropped yet. |
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

## Known mismatches

Places where the code defaults or behaviour differ from the design docs. The code is what runs.

- Anti-grind rule "no XP from mobs spawned by spawners after 30 kills" from `MILESTONE_1.md` is not implemented.
- `docs/design/enemy.md` lists `aimSpreadDegrees` 2.5; the code key is `aimInaccuracy` with default 5 (vanilla inaccuracy units, not degrees).
- `docs/design/world.md` section 6 gives the chamber as 8 horizontal / 5 vertical; the code defaults are `chamberRadius` 9.5 and `chamberHeight` 6 (the same doc's balance list agrees with the code).
- `MILESTONE_1.md` gives max energy as "50 + 2 per level"; the code adds 2 per level **above 1**.
