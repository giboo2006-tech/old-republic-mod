# Balance configuration

All gameplay numbers that can be tuned without a rebuild live in one server-side file:

```
config/oldrepublic/balance.json
```

It is read and written by `com.oldrepublic.config.BalanceConfig`. Clients never read it; the server is authoritative.

## How the file works

- **One section per feature.** Each section is a plain Java class with public fields and default values. Milestone 1 has six sections: `progression`, `force_push`, `quest`, `saber`, `enemy` and `world`. Milestone 2 adds ten: `identity`, `story`, `skills`, `force_pull`, `force_jump`, `force_heal`, `force_lightning`, `saber_throw`, `sith_acolyte` and `poacher_captain` (and extends `saber`). Milestone 3 adds eleven: `sites`, `chapter_three`, `missions`, `holocron`, `force_sense`, `force_lift`, `trooper`, `patrols`, `equipment`, `sentinel` and `collector` (and extends `progression`, `identity`, `skills` and `saber`). Milestone 4 adds seventeen: `planets`, `ship`, `travel`, `blasters`, `melee`, `armour_m4`, `mods`, `crafting`, `chapter_four`, `vendors`, `force_speed`, `force_barrier`, `force_choke`, `mandalorian`, `security_droid`, `creatures` and `kaast` (and extends `saber`).
- **Defaults are filled in.** When the server first reads a section, any missing section or field is filled from the code defaults and the file is written back. So the file always lists every value that exists, but a section only appears once the server has needed it (normally as soon as a player joins and plays). Delete a key (or the whole file) to get the default back.
- **Your values win.** Values you set are merged over the defaults. Unknown keys are kept in the file but ignored. If a section cannot be parsed (for example a string where a number belongs), the server logs an error and uses the defaults for that whole section.
- **Reload.** There is no reload command. The file is re-read on every server start (`ServerLifecycleEvents.SERVER_STARTING`, and once at mod init). In singleplayer, leaving and reopening the world counts as a server start. After a reload, values are cached per section until the next start, so edits made while the server runs do nothing until it restarts.
- **Values baked into things.** A few values are copied into the world when something is created:
  - Saber attribute modifiers (damage, attack speed, reach) are stored on the item. They are rewritten from the live balance whenever the saber is ignited, retracted or built, so an edit reaches an existing saber the next time it is toggled. Sabers in the creative tab are built with the code defaults.
  - Poacher and Shadow Echo attributes are applied when the entity is created or loaded.
  - A blaster bolt's damage and lifetime are fixed when it is fired.
  - The Force Push numbers are sent to each client on join, so the Force screen shows the server's values. Since Milestone 2 every power's numbers go out in the `ability_info` payload on join and after `/reload`.
  - Acolyte and Captain attributes, and settler health, are applied when the entity is created or loaded, like the poacher's.
  - Trooper and Ruin Sentinel attributes (health, armour, speed) are applied when the entity spawns, or on its first server tick after loading (`applyBalance`).
  - Era armour durability and enchantability (`equipment.trooperDurability`, `robeDurability`, `trooperEnchant`, `robeEnchant`) and the movement speed part of `trooperSpeedPct` are read from the code defaults when the items are registered at mod init. A changed file does not reach them; the other `equipment` keys are live.
  - The client never reads `balance.json`, so the Standing tab's rank 3 checklist, the Missions tab and the Holocrons tab's carry line show the default `chapter_three`, `missions` and `holocron` values. The server decides.
  - Milestone 4 registration defaults: blaster durability, `rifleSlowPct`, `repeaterSlowPct` and `bashDamage`; the vibroweapons' and electrostaff's damage, attack speed, reach and durability; the speed part of `armour_m4.mandoSpeedPct` and `spacerSpeedPct`; and `crafting.cutterDurability` and `repairKitUses`. They are read when the items are registered at mod init, so a changed file reaches them only after a full restart of the game or server.
  - The quality multipliers (`crafting.qualityWeaponDamage`, `qualityArmour`, `qualityDurability`) are handed to `crafting.Quality` on every server start. A crafted Fine or Masterwork item stores its scaled durability when it is crafted, so it keeps that value.
  - Some client drawing uses the code defaults: the blaster reticle's spread and heat bar, the discipline level thresholds on the Crafting tab and the bench screen, and the mod tooltips. The server decides every outcome.
  - `planets.ashfallDensity` and the rift crystal's hold (`riftBleedTicks`, for the whispers' length) are read on the client from the client's own file or the defaults, so a dedicated server's values do not reach players.

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
| `tier1Alignment` / `tier2Alignment` | 15 / 40 | alignment | Not used yet. Meant as the gates of the Jedi (≥ +n) and Sith (≤ -n) passive nodes; the skill tree files set the same gates with `alignment_min` and `alignment_max`. |
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
| `crossingXp` | 300 | XP for any confirmed choice. |
| `mentorLeash` / `mentorLookDistance` / `mentorAttackCooldownTicks` | 6 / 8 / 200 | Mentor behaviour. |
| `mentorSummonTicks` / `summonMinDistance` / `summonRings` / `summonFallback` | 1200 / 48 / [8, 12] / [4, 6] | "Ask them to come": arrival delay, offered beyond 48 blocks from the camp, new camp rings. |
| `sessionRadius` | 8 | Distance to the mentor for sessions and Atone / Embrace. |
| `lostMentorTicks` / `lostMentorRadius` | 100 / 24 | A missing mentor is respawned at the anchor. |
| `lessonXp` / `lessonTrust` / `lessonRep` / `lessonTwoTrust` | 300 / 15 / 40 / 30 | Lesson rewards; trust needed for lesson two. |
| `pullLessonTargets` / `sithPullKills` / `sithPullKillWindowTicks` | 5 / 3 / 60 | Lesson one goals. |
| `parryLessonCount` / `techniqueLessonCount` | 3 / 3 | Lesson two spar goals. |
| `masterYield` / `rivalLessonYield` / `rivalTrialYield` | 0.35 / 0.4 / 0.15 | Yield fractions passed to the spars (these are the ones in effect, see mismatches). |
| `encounterMinDist` / `encounterMaxDist` / `encounterSpawnDistance` | 96 / 160 / 48 | Raider camp placement and spawn-on-approach. |
| `poachers` / `extraPerPlayer` / `extraMax` / `extraPlayerRadius` | 4 / 1 / 3 / 32 | Crew size, scaled by other awakened players nearby. |
| `captiveCount` | 3 | Captive settlers. |
| `abandonDistance` / `cleanupDistance` | 128 / 128 | Unresolved captives count as left; leftover entities removed. |
| `independentDelayTicks` | 24000 | Independents' mission starts a day after the Crossing. |
| `reckoningXp` / `reckoningSp` / `reckoningSpIndependent` | 1000 / 1 / 3 | Mission rewards. |
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

# Milestone 3 sections

Eleven sections were added in Milestone 3: `sites`, `chapter_three`, `missions`, `holocron`, `force_sense`, `force_lift`, `trooper`, `patrols`, `equipment`, `sentinel` and `collector`. `progression`, `identity`, `skills` and `saber` gain keys. Defaults match `docs/MILESTONE_3.md` section 14 except where "Known mismatches" says otherwise, and every value below was checked against its Java class. Keys the spec does not list are marked "(added)".

**One value, one place.** Some features read keys that live in another feature's section:
- Troopers read `missions.tendTicks`, `escortDespawnTicks`, `interceptYield` and `borderYield` (`TrooperEntity`, `TrooperEscortGoal`). The `trooper` section has no copies of them.
- Force Heal's revive range is `missions.healTendRange`.
- The Echo Shrines and the Reliquary read their placement rings from `chapter_three` (`ChapterThreeService.balance()`).
- An owned shrine guard spawns at `story.encounterSpawnDistance` (48), and runtime sites keep `story.siteMinSeparation` (48) from other players' sites.
- A Seeker's veil reveal adds `force_sense.seekerBonusTicks` (80) to `sites.veilRevealTicks`.
- The `quiet_step` wake range is `skills.quietStepWake`, for sentinels too.
- Every FE refund from a kill or a parry (`hungering_dark`, `tomb_hunger`, the robe set bonuses, `watchers_insight`) goes through `ability.EnergyRefunds`, so `progression.killRefundCap` and `parryRefundCap` cap all of them together.

## `sites`

Class `com.oldrepublic.world.site.SiteBalance`, read through `SiteBalance.get()`. Puzzle blocks, vents, cairns, spawners, outposts and the structure locator (spec 3).

| Group | Keys (default) |
|---|---|
| Veiled stone | `veilRevealTicks` 160, `veilGroupMax` 27 (blocks per reveal) |
| Puzzle reset | `puzzleResetTicks` 1200, `puzzleResetRange` 24, `puzzleTickInterval` 200. The idle step runs every 200 ticks, so a reset lands 1,000 to 1,200 ticks after the last player leaves |
| Counterweights | `counterweightTicks` 600, `counterweightWarnTicks` 40 |
| Pull anchors and rubble | `anchorStrainTicks` 10, `anchorOpenTicks` 600; `rubbleTicks` 20 |
| Urns | `urnSenseRange` 12, `urnStepTicks` 20, `urnRetryTicks` 100; a wrong urn deals `urnWrongDamage` 2 within `urnWrongRadius` 3 and wakes sentinels within `sentinelWakeRadius` 16 |
| Tomb vents | `ventMinTicks` 60, `ventMaxTicks` 100, `ventTellTicks` 15, `ventActiveRange` 8; `ventDamage` 4, `ventSlownessTicks` 60 |
| Cairns and spawners | `cairnRevealTicks` 600; `spawnerRange` 32 (`site_spawner`) |
| Outposts | `outpostWorldgenRadius` 640, `outpostReuseRadius` 512; `outpostRings` [112, 128, 144], `outpostFallback` [80, 96], `outpostSeparation` 128; `maxRuntimeOutposts` 8, `runtimeOutposts` true |
| Garrisons and terminals | `garrisonRefillTicks` 12000, `garrisonRefillClearRange` 48, `garrisonLeash` 12; `terminalChecksBeforeRespawn` 2 |
| Structure locator | `locatorCacheTicks` 600, `locatorCacheMove` 48, `locatorCellsPerTick` 4, `locatorMaxRings` 4 |
| Presence | `presenceCheckTicks` 20 |

## `chapter_three`

Class `com.oldrepublic.quest.story.ChapterThreeBalance`, read through `ChapterThreeService.balance()`.

| Group | Keys (default) |
|---|---|
| WAITING and the dream | `waitTicks` 24000, `minSleepTicks` 100; `dreamTicks` 120 (added; the M2 dream's length) |
| Leads and the level gate | `leadsNeeded` 4; `reliquaryMinLevel` 9, `recommendedLevel` 10 |
| Path openings | `lessonXp` 400, `cacheXp` 400, `lessonTrust` 10, `lessonRep` 40; cairns `cairnCount` 3, `cairnRadius` 24, `cairnSpacing` 8; `cairnSenseRange` 24 (added: the caster's own untouched cairns and caches within this range answer Sense) |
| Courier's Fall | `fallRings` [64, 80, 96], `fallFallback` [32, 48] from the player; Independent caches `cacheMin` 12 to `cacheMax` 24 blocks from the fall |
| First echo and shrines | `firstEchoRadius` 768; `shrineRings` [96, 128], `shrineFallback` [48, 80]; `extraShrinesPerKind` 1 (the old-world guarantee) |
| Reliquary | `reliquaryRings` [96, 128, 144], `reliquaryFallback` [48, 80] from the player. `ReliquarySites` caps any ring at 144 |
| Retries | `retryTicks` 1200, `forwardFallbackAfter` 3 |
| Vasch's beats | `vaschMessageLeads` 2, `ambushLeads` 3; `meetGiveBackLight` 1; the ambush `ambushMinSiteDistance` 64, `ambushRange` [24, 32], `ambushDespawnDistance` 128 |
| Report | `reportXp` 600, `reportTrust` 10, `reportRep` 50 (the Independent's army, or Frontier for "Send word"), `reportResonance` 300 |
| Rank 3 | `rank3MinLevel` 11, `rank3MinTrust` 80, `rank3MinRep` 500, `rank3Alignment` 15; `rank3Holocrons` 2, `rank3Contracts` 3; rewards `rank3Xp` 1000, `rank3Sp` 2, `rank3Resonance` 700 |
| Pathfinder | `pathfinderHolocrons` 3, `pathfinderContracts` 3, `pathfinderTier` 100 (Trusted) |

## `missions`

Class `com.oldrepublic.mission.MissionBalance`, read through `MissionFeature.balance()`. The per-choice rewards of the four story missions are in their mission JSON (`data/oldrepublic/mission/`), not here.

| Group | Keys (default) |
|---|---|
| Story missions | `storyXp` 1200, `storyCommendations` 2; `jediOrderShare` 0.5 (a Jedi's Republic gains from Republic work also pay this share as Jedi Order) |
| Board and contracts | `offersPerBoard` 2, `dutiesPerDay` 1, `usedOffersMax` 16; `fullPayPerDay` 3, then `overCapMultiplier` 0.25 (XP only); XP `contractXpBase` 250 + `contractXpPerLevel` 40 per level, capped at `contractXpCap` 900; `contractRep` 25, `contractCommendations` 1; `boardReach` 8 (added: offers can be accepted this far from the terminal) |
| Mentor duties | `dutyTrust` 5, `dutyRep` 25 |
| Mission sites | `siteRings` [96, 128, 144], `siteFallback` [48, 80] from the player; `siteSpawnDistance` 48, `siteSeparation` 48; `siteRetryTicks` 200 and `siteForwardFallbackAfter` 3 (both added: the wait between failed placements, and the count after which the site goes straight ahead of the player) |
| Objectives | `surveyRadius` 512, `surveyHeartRange` 8, `secureRadius` 384; `interrogatePullTicks` 12000, `interrogateRadius` 1024 (also the freed-captive cameo's Calling) |
| Yields | `interceptYield` 0.25, `borderYield` 0.20, `chiefYield` 0.25, `fenceYield` 0.25. The chief and fence fractions are a per-entity tag set at spawn |
| Downed troopers | `tendTicks` 60, `healTendRange` 4 (Force Heal's revive range), `downedBleedTicks` 2400, `escortDespawnTicks` 300 |
| Discovery | `discoveryStructureXp` 300 (ruin, tomb or wreck), `discoveryOutpostXp` 150 |
| Tracker and guidance | `trackerHintTicks` 20, `trackerNear` 160, `trackerHere` 48; `guidanceIntervalTicks` 40; `outpostGuidanceRadius` 1024 (added: the Calling points to an unvisited outpost of an army this close) |

## `identity` (Milestone 3 additions)

Same class as above, `com.oldrepublic.identity.IdentityBalance`. Kill reputation (spec 5.2) lives here.

| Key | Default | What it does |
|---|---|---|
| `republicSeedJedi` / `republicSeedSith` / `republicSeedIndependent` | 100 / -100 / 0 | One-shot Republic reputation at Chapter Three SUMMONS. |
| `killedSoldier` | -2 | Alignment for killing a soldier who was not hostile (`KILLED_SOLDIER`). |
| `neutralKillRep` / `hostileKillRep` | -40 / -10 | Army reputation for killing one of its soldiers who was not hostile / who was. A kill never gains reputation. |
| `killRepDailyCap` | 200 | Daily cap per army. It covers only the -10 losses; the -40 losses are never capped. |

## `holocron`

Class `com.oldrepublic.holocron.HolocronBalance`, read through `HolocronBalance.get()`.

| Group | Keys (default) |
|---|---|
| Attunement | `baseXp` 600, `resonance` 120, `relicLoreXpBonus` 0.25; `pedestalClearRange` 16 (no hostile within it); `riseTicks` 30, `gatekeeperDelayTicks` 30; `senseFollowUpTicks` 40 |
| Opposed side | `sealShift` +3, `jediListenShift` -4, `breakShift` -3, `sithListenShift` +4; `listenResonance` 80, `breakResonance` 80, `breakTrust` 10 |
| Carry and return | `carriedRegenPct` 4, `carriedMax` 3, `carryRefreshTicks` 20; `returnTrust` 10, `returnRep` 40, `returnCommendations` 1; `amendsRep` 40, `amendsShards` 2 |
| Progression | `spEvery` 2; `milestone3Resonance` 150, `milestone6Sp` 1, `milestone9Resonance` 250, `milestone12Resonance` 400 |
| Echoes and shards | `echoXp` 150, `echoShards` 1, `echoRing` 64 (pedestals remembered), `shardsPerCommendation` 2 |
| Restore | `restoreCooldownTicks` 24000 (once per day index, game time) |
| Archive | `archiveXp` 300; return `archiveReturnOwnRep` 150, `archiveReturnRepublicRep` 60, `archiveReturnTrust` 20, `archiveReturnLight` 5, `archiveReturnFrontierRep` 100, `archiveReturnIndependentLight` 3; keep `archiveKeepJediShift` -6, `archiveKeepIndependentShift` -2; unmake `archiveUnmakeResonance` 400, `archiveUnmakeLight` 3, `archiveUnmakeRepLoss` -50 |

## Force powers: `force_sense`, `force_lift`

Classes `com.oldrepublic.force.sense.SenseBalance` (read through `ForceSense.balance()`) and `force.lift.LiftBalance` (read through `ForceLift.balance()`). Costs and cooldowns then go through `AbilityCosts` and the M3 cost and regen modifiers, clamped by `progression.modifierCost*` and `modifierRegen*`.

| Section | Keys (default) |
|---|---|
| `force_sense` | `cost` 15 FE, `cooldownTicks` 240, `durationTicks` 160; `creatureRange` 24, `blockRange` 16, `maxPoints` 32; holocron direction `holocronRadius` 256, Seeker `seekerRadius` 512 and `seekerBonusTicks` 80, `glintRange` 24; Danger Sense `dangerRange` 12, `dangerConeDegrees` 100, `dangerRepeatTicks` 20; mastery `masteryCostPerRank` 1, `masteryRangePerRank` 4; `pvpEffects` false |
| `force_lift` | `startCost` 20 + `costPerSecond` 8 FE, `cooldownTicks` 280; `maxHoldTicks` 60, `suspendHoldTicks` 100; `range` 14, `coneDegrees` 8, `maxTargetHeight` 2.4; `height` 2.5, `resistantHeight` 1.2, `riseTicks` 10; slam `slamDamage` 6, `slamStaggerTicks` 15; Hurl `hurlDistance` 10, `hurlDamage` 6, `hurlStaggerTicks` 20; Suspend `suspendAllyBonus` 0.2; `interruptDamage` 6; the Collector `bossHeight` 1.2, `bossStaggerTicks` 10; `rubbleTicks` 20; mastery `masteryCostPerRank` 0.05, `masteryRangePerRank` 1; `pvpEffects` false |

## `skills` (Milestone 3 additions)

Same class as above, `com.oldrepublic.skill.SkillBalance`. Only the Exploration nodes and the holocron nodes W3 runs; the saber nodes are in `saber`. Two numbers in brackets are rank 1 and rank 2.

| Key | Default | Node |
|---|---|---|
| `trailwiseFall` / `trailwiseHunger` | [0.2, 0.4] / [0.15, 0.3] | `trailwise`: less fall damage and hunger. |
| `hardyHealth` | [2, 4] | `hardy`: extra max health. |
| `scavengerChance` | 0.35 | `scavenger`. |
| `quietStepWake` / `quietStepWarn` / `quietStepWaryTicks` | 4 / 10 / 200 | `quiet_step`: sentinel wake range, trooper warn range, Wary time. |
| `pathfinderSpeed` / `pathfinderFall` | 0.10 / 0.25 | `pathfinders_poise`. |
| `fallReductionCap` | 0.6 | All fall reductions together. |
| `watchersInsightHealCost` | 0.10 | `watchers_insight`: Heal cost cut. |
| `tombHungerFe` / `tombHungerWindowTicks` | 6 / 60 | `tomb_hunger`: FE for a kill within 60 ticks of a power hit (through `EnergyRefunds`). |
| `cracklingSlow` / `cracklingTicks` | 0.3 / 40 | `crackling_grasp`. |
| `archivistsHungerBonus` / `archivistsHungerThreshold` | 0.15 / 0.75 | `archivists_hunger`. |
| `archivistsRoadSenseCooldown` / `archivistsRoadLiftCost` | 0.3 / 0.1 | `archivists_road`. |

### Skill trees (Milestone 3 data)

`skill_tree/exploration.json` is the fifth tab (order 4). As shipped: `force_sense` granted; `trailwise` 1, 1 / L7, 10; `sense_danger`, `sense_seeker` 1 / L8 (one exclusive group); `relic_lore` 1 / L8; `hardy` 1, 2 / L9, 12; `scavenger` 1 / L9; `quiet_step` 2 / L11; `force_lift` granted (Independent); `lift_suspend`, `lift_hurl` 1 / L9 (one exclusive group); `archivists_road` and `pathfinders_poise` granted. The holocron and rank 3 nodes are granted only.

## `trooper`

Class `com.oldrepublic.enemy.trooper.TrooperBalance`, read through `TrooperBalance.get()`. Health, armour and speed are applied when a trooper spawns or on its first server tick after loading.

| Group | Keys (default) |
|---|---|
| Roles (health / armour) | `republicRifleman` 24 / 4, `republicMedic` 20 / 2, `republicSergeant` 30 / 6; `imperialRifleman` 24 / 5, `imperialGrenadier` 26 / 5, `imperialSergeant` 30 / 7 |
| Movement and range | `speed` 0.27, `chargeTicks` 12, `bandMin` 10, `bandMax` 18 |
| Republic rifle | `republicBoltDamage` 4, `burstSize` 3, `burstGap` 4, `burstCooldown` 50; `ventAfterBursts` 3, `ventTicks` 40 |
| Imperial rifle and pistol | `imperialBoltDamage` 7, `imperialCooldown` 34, `flankerRange` [12, 16]; `pistolDamage` 4, `pistolCooldown` 40 |
| Field Medic | `medicHeal` 8, `medicThreshold` 0.5, `medicChannelTicks` 20, `medicCooldown` 160, `interruptDamage` 4 |
| Sergeant Rally | `rallyTellTicks` 30, `rallyTicks` 200, `rallyCooldown` 600, `rallyFireRate` 0.25 |
| Frag grenade | `grenadeInterval` 200, `grenadeTellTicks` 20, `grenadeFuseTicks` 40; `grenadeDamage` 8, `grenadeRadius` 3, `grenadeKnockback` 0.5, `grenadeRange` [6, 14]; `grenadePushSpeed` 1.2 |
| Flare reinforcements | `reinforceTellTicks` 30, `reinforceDelayTicks` 200, `reinforceCount` 2, `reinforceDistance` [24, 32] |
| Against a saber | `deflectWindowTicks` 100, `deflectCount` 2, `holdFireTicks` 40, `repositionRange` [4, 8] |
| Drops | `durasteelChance` 0.2 |
| Attitude ranges | `hostileRange` 24, `warnRange` 16, `waryRange` 8, `waryTicks` 120, `warnRepeatTicks` 600 |
| Incidents | `allyDefendRange` 16, `provokedTicks` 1200, `engagedTicks` 1200, `incidentTicks` 1200; standoff `standoffRange` 16, `standoffDistance` 12, `standoffTicks` 200 |
| Barks | `barkRange` 24, `barkTrooperTicks` 120, `barkSquadTicks` 40 |
| Squads | `extraRiflemanLevel` 11, `moraleRetreatTicks` 200, `moraleRetreatDistance` 24; `coldPeace` true |
| Added: bolts | `boltSpeed` 1.8 and `imperialBoltSpeed` 1.35 blocks per tick, `boltLifetimeTicks` 60, `aimInaccuracy` 4 (vanilla units at Normal; x1.5 on Easy, x0.6 on Hard) |
| Added: squad behaviour | `followRange` 32; `rallyRadius` 12, `rallyMinAlive` 2; `medicRange` 16; `squadSpacing` [3, 5]; cover below `coverHealthFraction` 0.5 at `coverSpeed` 1.3, held `coverHoldTicks` [40, 60], `coverCooldownTicks` 200 |
| Added: social | `saluteRange` 8, `moveAlongRange` 4 (an Allied salute; the Neutral "move along" at a lit saber); `homeRange` 384 (a stood-up trooper walks to its army's terminal within this range) |
| Added: encounters | `encounterEndTicks` 600 (after this long with no fighting the encounter ends: flare arrivals not on patrol walk off, and the squad may flare again) |

## `patrols`

Class `com.oldrepublic.enemy.trooper.PatrolBalance`, read through `PatrolBalance.get()`.

| Key | Default | What it does |
|---|---|---|
| `enabled` | true | false turns natural patrols off. |
| `intervalTicks` / `chance` | 1200 / 0.15 | Each eligible player gets one roll this often, with this chance of a squad. |
| `minDistance` / `maxDistance` / `passDistance` / `routeLength` | 48 / 80 / 16 / 96 | A squad appears this far away, out of sight; its route passes within 16 blocks of the player, then goes on 96 beyond. |
| `perPlayerRange` / `perPlayerCap` / `globalCap` | 128 / 1 / 6 | At most one patrol within 128 of a player, and six per server. |
| `despawnDistance` / `despawnCheckTicks` | 128 / 100 | Every 100 ticks, a squad whose members are all farther than 128 from every player is removed. |
| `outpostBiasRange` / `siteClearance` | 384 / 64 | The squad belongs to the army whose outpost terminal is nearer, within 384 (else a random army). No patrol spawns or walks within 64 of a mission site or the Reliquary. |

## `equipment`

Class `com.oldrepublic.equipment.EquipmentBalance`, read through `EquipmentBalance.get()`. Percentages are whole numbers (5 = 5%).

| Group | Keys (default) |
|---|---|
| Trooper sets, per piece | `trooperCostPct` 5 (Force cost), `trooperRegenPct` -5 (regen), `trooperSpeedPct` -1.5 (movement speed; registration default only) |
| Republic trooper armour | `republicBlasterPct` -6 blaster damage taken per piece; set (Shoulder to Shoulder) `republicSetBlasterPct` -10, `republicSetBoltDrainPct` -25 guard drain from bolts |
| Sith trooper armour | `sithMeleePct` -4 melee damage taken per piece; set (Iron Discipline) `sithSetControlPct` -30 control durations, `sithSetKnockbackResist` 0.2 |
| Jedi robes | `jediRegenPct` 6, `jediCostPct` -2 per piece; set (Calm of the Watch) `jediSetParryFe` 5 per parry, `jediSetHealCost` -15 (Lift for Independents) |
| Sith robes | `sithMaxEnergy` 5, `sithCostPct` -2 per piece; set (Hunger of the Tomb) `sithSetKillFe` 8 for a kill within `sithSetWindowTicks` 160 of a power hit, `sithSetLightningCost` -15 (Lift for Independents) |
| Robe gate | `setAlignment` 15 |
| Registration defaults | `trooperDurability` 18, `robeDurability` 10, `trooperEnchant` 9, `robeEnchant` 15 (see "Values baked into things") |
| Unused | `disguiseBreakTicks` 2400, `disguiseBreakRange` 24 (the uniform disguise, 9.5, is cut) |

## `sentinel`

Class `com.oldrepublic.enemy.construct.SentinelBalance`, read through `SentinelBalance.get()`. Attributes are applied when a sentinel spawns or on its first server tick after loading.

| Group | Keys (default) |
|---|---|
| Stats | `watcherHealth` 48, `tombHealth` 60, `armor` 8; `watcherSpeed` 0.18, `tombSpeed` 0.16 |
| Waking | `wakeRange` 6 (`skills.quietStepWake` with `quiet_step`), `wakeTicks` 20; `dormantAlignment` 15 (Watchers sleep for players at ≥ +15, Tomb sentinels at ≤ -15) |
| Slam | `slamTellTicks` 20, `slamDamage` 10, `slamRadius` 2.5, `slamArcDegrees` 90, `slamKnockback` 0.6, `slamCooldown` 60; then the core is exposed for `exposedTicks` 40 at `exposedMultiplier` 1.5 |
| Lance | `lanceTellTicks` 24, `lanceDamage` 7, `lanceSpeed` 1.6, `lanceRange` [6, 20], `lanceCooldown` 80; `lanceGuardDrain` 35; returned by a parry for `lanceReturnDamage` 10 and `lanceReturnStaggerTicks` 20 |
| Damage taken | `pushMultiplier` 0.2, `lightningMultiplier` 1.5, `shockDurationMultiplier` 2, `blasterMultiplier` 0.5, `saberMultiplier` 0.85; `liftHeight` 1.2 (added) |
| Drops | `durasteelChance` 0.4, `threadChance` 0.2 |

## `collector`

Class `com.oldrepublic.boss.CollectorBalance`, read through `CollectorBalance.get()`. W5's fight reads the fight keys; `quest.story.CollectorRewards` reads the reward and ending keys.

| Group | Keys (default) |
|---|---|
| Stats and scaling | `maxHealth` 450, `perParticipant` 0.6, `scalingCap` 4, `armor` 10, `knockbackResistance` 0.8 |
| Phases | `phase2At` 0.60, `phase3At` 0.30, `yieldAt` 0.10, `transitionTicks` 60 |
| Phase 1 duelist | `p1LightDamage` 6, `p1HeavyDamage` 11, `p1Guard` 140, `p1ParryChance` 0.30, `p1ParryMinStamina` 0.70, `p1WindupTicks` 10, `p1RecoveryTicks` 16; `p1StringMin` 2, `p1StringMax` 3, `p1GapTicks` 7 (added) |
| Hurl | `hurlInterval` 180, `hurlTellTicks` 24, `hurlDamage` 8, `hurlSpeed` 1.1, `hurlGuardDrain` 30; `hurlRipStaggerTicks` 20; returned within `hurlReturnRange` 6 at `hurlReturnMultiplier` 1.5 with `hurlReturnStaggerTicks` 30 |
| Palm | `palmRange` 3, `palmDwellTicks` 80, `palmTellTicks` 16, `palmDamage` 4, `palmKnockback` 1.4, `palmClashStaggerTicks` 20 |
| Bolt deflection | `deflectConeDegrees` 70, `deflectSpreadDegrees` 15 |
| Ward and pylons | `wardHigh` 0.35, `wardLow` 0.65; Unsung `unsungTicks` 100 at `unsungMultiplier` 1.5; `pylonResonance` 30, `pylonScale` 0.25, `pylonPullRange` 12, `pylonLiftTicks` 20; resonance taken by `pylonSaber` 10, `pylonPush` 10, `pylonFocusedBlast` 15, `pylonThrow` 15, `pylonLightningPerSecond` 10, `pylonParriedLance` 15; `resingTicks` 800, `regrowTellTicks` 60, `resingPerPhase` 1 |
| Lash and sentinels | `lashInterval` 140, `lashTellTicks` 20, `lashDamage` 9, `lashSpeed` 0.8, `lashRange` 20, `lashKnockback` 0.6; `sentinels` 2, `sentinelsMax` 4; `leashRange` 8 |
| Phase 3 duelist | `p3LightDamage` 7, `p3HeavyDamage` 12, `p3Guard` 100, `p3ParryChance` 0.15, `p3WindupTicks` 8, `p3RecoveryTicks` 12; `p3StringMin` 3, `p3StringMax` 4, `p3GapTicks` 7 (added) |
| Lunge | `lungeTellTicks` 14, `lungeRange` 8, `lungeDamage` 10; `lungeCooldown` 100, `lungeInterruptStaggerTicks` 20 (added) |
| Surge | `surgeInterval` 300, `surgeTellTicks` 30, `surgeRadius` 8, `surgeDamage` 10, `surgeKnockback` 2.0; `surgePartialMultiplier` 0.5, `surgeAirborneMultiplier` 0.5, `surgeClashTicks` 8, `surgeClashShelter` 3; Spent `spentTicks` 60 at `spentMultiplier` 1.5, `spentLiftHeight` 1.2 (added) |
| Grip | `gripInterval` 400, `gripTellTicks` 20, `gripHoldTicks` 80, `gripDamage` 2, `gripBreakDamage` 8, `gripTakenMultiplier` 1.25, `gripSurgeGap` 60; `gripBreakStaggerTicks` 20 (added); `interruptHitDamage` 6 (one hit that breaks the Grip tell) |
| Damage taken | `lightningMultiplier` 0.6, `saberThrowMultiplier` 0.7; `controlMultiplier` 0.5 (added: control durations outside Spent) |
| Threat | `threatWindowTicks` 200, `threatSwitchTicks` 200 |
| Session | `resetTicks` 200, `sealCloseTicks` 40, `meetDialogueTicks` 400, `participantLeaveTicks` 100, `readyRange` 48, `daisStartRange` 6 |
| First clear | `ownerXp` 3000, `ownerSp` 2, `resonance` 600; other helpers `otherHelperXp` 1000, `otherHelperShards` 1 |
| Endings | Spare `spareLight` 10, `spareJediOrderRep` 60, `spareRepublicRep` 100, `spareJediTrust` 10, `spareSithTrust` -25; Execute `executeDark` -10, `executeSithRep` 80, `executeSithTrust` 10, `executeJediTrust` -25, `executeFrontierRep` 20; Bind `bindDark` -5, `bindSithRep` 100, `bindSithTrust` 10; Release `releaseLight` 5, `releaseFrontierRep` 50; bring in `bringInRep` 100, `bringInCommendations` 2 |
| Echo rematch | `echoFormTicks` 60, `echoDamageMultiplier` 1.15, `echoXp` 600, `echoDurasteel` 2, `echoShards` 1; `emitterChance` 0.25 with `emitterPity` 4 |
| Unchained | `unchainedHealth` 1.5, `unchainedDamage` 1.25, `unchainedPhase3At` 0.40, `unchainedXp` 900, `unchainedRolls` 2 |
| Placement | `placeReliquary` true |

## `progression` (Milestone 3 additions)

| Key | Default | What it does |
|---|---|---|
| `modifierCostMin` / `modifierCostMax` | 0.70 / 1.50 | Clamp of the combined `ForceModifiers` cost multiplier (armour, nodes, holocrons). |
| `modifierRegenMin` / `modifierRegenMax` | 0.50 / 1.50 | Clamp of the combined `ForceModifiers` regen multiplier. |
| `damageModifierMin` / `damageModifierMax` | 0.25 / 2.0 | Clamp of the combined `DamageModifiers` multiplier. |
| `killRefundCap` / `parryRefundCap` | 12 / 6 | Most Force Energy all refunds of one kill / one parry may restore together (`ability.EnergyRefunds`). |

## `saber` (Milestone 3 additions)

Same class as above, `com.oldrepublic.saber.SaberBalance`. Includes the saber nodes W5 runs.

| Group | Keys (default) |
|---|---|
| NPC bolt deflection | `npcDeflectConeDegrees` 70 (either side of facing), `npcDeflectSpreadDegrees` 15 |
| Heavy and non-deflectable projectiles | `heavyBoltGuardDrain` 35, `nonDeflectableGuardDrain` 30 |
| Parry window | `parryWindowCap` 11 ticks (after flat bonuses and the form multiplier) |
| Hilt parts | Archivist pommel `archivistParryTicks` 2, `archivistAttackSpeed` -0.05; Reliquary emitter `reliquaryDeflectDegrees` 12, `reliquaryParryBonus` 0.15 |
| Nodes | `watchersInsightFe` 3 (through `EnergyRefunds`); `archivistsWardTicks` 2; `knightsVigilRange` 6, `knightsVigilReduction` 0.1; `lordsDominionRange` 8, `lordsDominionReduction` 0.1, `lordsDominionTicks` 100 |

### Values outside `balance.json` (Milestone 3)

- Structure sets: ruins (Jedi Ruin, Sith Tomb, Wreck Field) spacing 32, separation 12, salt 734211, at least 6 chunks clear of the crystal caves; outposts spacing 40, separation 16, salt 734214 (`data/oldrepublic/worldgen/structure_set/`).
- Echo Shrine footprint 13, clearance 17, slope 3 (`world.EchoShrines`); runtime outpost footprint 23, clearance 27 (`world.outpost.OutpostPlacement`).
- Site ambience: beds at 0.4 to 0.6 volume, at most 40 particles per site, and a 30% dip for 20 ticks when Sense is cast (client).
- `MAX_MOMENT_TICKS` 2,400: the longest a holocron attunement keeps its pedestal busy (`holocron.HolocronAttunement`).
- Loot tables in `data/oldrepublic/loot_table/`: the site chests (`chests/jedi_ruin`, `sith_tomb`, `wreck_field`), the M3 block drops (`blocks/lumen_moss`, `funerary_urn`, `wreck_salvage` and the ruin, tomb and wreck stones), `entities/republic_trooper`, `sith_trooper`, `ruin_sentinel`, and `gameplay/echo_cache`, `frontier_contract`, `r1_supply`, `wayfarer_cache`.
- Story mission choices: alignment, reputation and trust per choice are in `data/oldrepublic/mission/*.json`.
- Client options in `client.json`: `robeModels`, `showBarks`, `bossMusic` (all default true).

---

# Milestone 4 sections

Milestone 4 adds these sections: `planets` (W1), `chapter_four` and `vendors` (W2), `ship` and `travel` (W3), `blasters`, `melee`, `armour_m4` and `mods` (W4), `crafting` (W5), and `force_speed`, `force_barrier`, `force_choke`, `mandalorian`, `security_droid`, `creatures` and `kaast` (W6); `saber` gains keys (W5). Defaults match `docs/MILESTONE_4.md` section 14.3 except where "Known mismatches" says otherwise, and every value below was checked against its Java class. Keys the spec does not list are marked "(added)". Spec 14.3 gives the three powers and the three foe sections as a rule ("every number of 9.1 to 9.3", "every number of 10.1 to 10.4", one key each) rather than a key list, so no key in those sections is marked. A key marked "not used yet" is in the class but nothing in the code reads it, so changing it does nothing. Percentages are whole numbers (5 = 5%); a negative "damage taken" percentage is a resistance.

**One value, one place.** Some features read keys that live in another feature's section:
- The vibro guard and the electrostaff guard use the M2 saber guard's stamina pool, drain, regeneration and arc (`saber.guardStamina`, `meleeDrainBase`, `meleeDrainPerDamage` and the others); `melee` has only the share each guard blocks.
- The quality multipliers (`crafting.qualityWeaponDamage`, `qualityArmour`, `qualityDurability`) are handed to `crafting.Quality` on every server start; blaster bolts, crafted weapons and armour, and M4 armour traits all read them from there.
- The lens values by quality are `crafting.lensHoned`, `lensSteady` and `lensResonant`; the `lens_mastery` bonus is `saber.lensMasteryPct`.
- The player's frag grenade reads `crafting.playerGrenadeDamage` and `playerGrenadeRadius` through `FragGrenade.throwAt`; trooper grenades keep `trooper.grenadeDamage` and `grenadeRadius`.
- Every control duration on a wearer (Iron Discipline, Hunter's Tenacity, `mod_stabiliser`, the `Extra` trait) is bounded by `armour_m4.controlMin`. Cost, regeneration and damage from armour and mods go through the M3 clamps `progression.modifierCost*`, `modifierRegen*` and `damageModifier*`.
- The skilled weld (`ship.plateRepairSkilled`) needs Armourcraft or Armstech at discipline level 2 (`crafting.levelXp`).
- Keys read by another area: `ship.sabotageHull` by W2's sabotage (`TheHunt`); `ship.spacerRepairPerPoint` by W3's `ship:repair_paid` dialogue action, which W2's spacer offers; `saber.staffWhirlStaggerTicks` and `lensMasteryPct` by the saber code, for W6's `staff_whirl` and `lens_mastery` nodes.
- Set bonuses on the Force powers: `armour_m4.spacerSetSpeedDurationPct` lengthens Force Speed (through `SetBonuses.forceSpeedDurationMultiplier`); `mandoSetSpeedCostPct`, `watchSetBarrierCostPct` and `sithWarSetCostPct` change the powers' costs through `ForcePowers.costMultiplier`, which every power uses; `sithWarSetDamagePct` applies to Choke's `force_choke` damage as well as Lightning.
- Kaast's rewards (`kaast.ownerXp`, `ownerSp`, `ownerCredits`, `resonance`, `helperXp`, `rematchXp`, `rematchSchematicPity`, `rematchCredits`) are read by W2's `KaastDuel`; `chapter_four` has no copy. The duel helper's 1,000 XP is `kaast.helperXp` only.
- The hunting camp's ring is set twice, both 20 blocks: `chapter_four.campRing` moves the owner's story from CHALLENGED to DUEL, and `kaast.arenaRadius` starts the fight and decides who is a participant. Change both together.
- Kaast's flamer and his burn-off of a Choke use the hunters' `mandalorian.flamer*` keys. His rocket uses `mandalorian.rocketSpeed`, `rocketRadius`, `rocketLifetimeTicks` and the band `rocketMinRange` to `rocketMaxRange`, with his own `kaast.rocketDamage`, `rocketInterval` and `rocketTellTicks`.
- Discovery XP on a planet: W2 pays the first landing and the first visit to the hub from `chapter_four.planetXp` and `hubXp`; the M3 `Discovery` pays `planets.siteDiscoveryXp` for the first waystone and `greaterTombDiscoveryXp` for the first greater tomb. `planetDiscoveryXp` and `hubDiscoveryXp` are not used (see "Known mismatches").
- Hunter rewards: credit chits from `vendors.creditDropChance` and `creditDrop`; a Mandalorian schematic piece from `chapter_four.hunterSchematicChance` and `hunterSchematicPity` (W2's `TheHunt`, the only roll of that piece on a hunter kill); the echo rematch's piece from `kaast.rematchSchematicPity`.

## `planets`

Class `com.oldrepublic.planet.PlanetBalance`, read through `PlanetBalance.get()`. Hubs, planet sites, waystones and the planets' hazards (spec 3).

| Key | Default | What it does |
|---|---|---|
| `respawnAtHub` | true | On a planet, a player with no bed there respawns at the hub's medbay. |
| `hubRings` / `hubFootprint` / `hubMaxSlope` / `hubTicketRadius` | [0, 16, 32] / 63 / 6 / 5 | Rings searched from (0, 0) for a hub; the side of the natural square it needs; the height difference allowed across it; the chunk radius of the ticket held while it is placed (11 by 11 chunks). |
| `heartstoneRing` | [80, 100, 120] | Rings of the Heartstone around the Greenwatch. |
| `siteEnsureRange` | 160 | A per-player planet site is placed once its owner is this close to its ring point. |
| `expeditionRings` / `cacheRings` / `campRings` | [300, 375, 450] / [150, 200, 250] / [400, 500, 600] | Rings from the hub of the crystal expeditions, the Wayfarer cache and the hunting camp. |
| `ambushRings` / `ambushClearance` (added) | [24, 28, 32] / 96 | Rings around the player of a hunter ambush's rocks; an ambush keeps this far from the hub and from every other site. |
| `siteRetryTicks` (added) | 40 | Placement attempts of one owner's site are at least this many ticks apart. |
| `waystoneCorruptedChance` | 0.25 | Share of worldgen waystones that are corrupted, decided by position. |
| `waystoneSenseRange` / `waystoneAmbienceRange` (added) | 8 / 16 | A Sense cast cleanses a corrupted waystone this close; its corrupt loop plays for players this close it is not cleansed for (its motes show within 8). |
| `ashfallDensity` | 1.0 | Scale of the Varkhast ashfall (1.0 is 40 flakes at Standard). Read on the client (see "Values baked into things"). |
| `duskglassDamage` | 2 | Contact damage of a duskglass cluster (`duskglass_cut`). |
| `sinkingPlatformTicks` / `platformResetTicks` (added) | 60 / 200 | A Wayfarer cache platform sinks this long after it is first stood on, and rises again after this long. |
| `planetDiscoveryXp` / `hubDiscoveryXp` / `siteDiscoveryXp` / `greaterTombDiscoveryXp` | 400 / 200 / 150 / 300 | `siteDiscoveryXp` is paid for the player's first waystone and `greaterTombDiscoveryXp` for their first greater tomb (the M3 `Discovery`, once per site kind). `planetDiscoveryXp` and `hubDiscoveryXp` are not used yet: W2 pays the planet and the hub from `chapter_four.planetXp` and `hubXp` (see "Known mismatches"). |
| `hollowHumTicks` (added) | 200 | Not used yet. Meant as how long a Singing Hollow cluster stays readable after Sense. |
| `riftBleedTicks` (added) | 100 | How long the rift crystal must be held while the whispers play. |
| `hubRestoreTicks` (added) | 24000 | A hub spawner restores its person at most once per this many ticks while that person is dead or gone. |

## `ship`

Class `com.oldrepublic.travel.ShipBalance`, read through `ShipBalance.get()`. Fuel, hull wear and repair, the hulk, seats, hold and the camera (spec 4.1 to 4.4, 4.7).

| Key | Default | What it does |
|---|---|---|
| `tankCells` / `tankCellsUpgraded` / `cellsPerJump` | 4 / 6 / 1 | Hyperfuel cells the tank holds, without and with the fuel tank extension; cells one jump burns. |
| `wearPerJump` / `wearReinforced` | 5 / 3 | Hull one jump wears off, without and with reinforced plating. |
| `jumpMinHull` | 20 | Below this hull the ship is DISABLED and will not jump; a tow brings a disabled ship back at this hull. |
| `damageFxHull` | 40 | Below this hull a nacelle sparks and trails smoke. |
| `sabotageHull` | 10 | The story's sabotage sets the hull to this, once (read by W2's sabotage). |
| `hullPlates` | 12 | Durasteel plates the hulk's plating socket takes. |
| `plateRepair` / `plateRepairSkilled` / `plateRepairCooldown` | 5 / 8 / 10 | Hull one welded plate adds; with Armourcraft or Armstech at discipline level 2; ticks between two welds by the same player. |
| `spacerRepairPerPoint` | 6 | Credits per hull point a spacer charges to repair to full (read by W2's spacer). |
| `starterCells` | 2 | Cells left in the hold when the hulk is placed. |
| `cradleRange` | [32, 64] | Distance of the cradle from the player the hulk is placed for. |
| `crewAddRange` | 16 | The owner adds crew from players this close. |
| `seats` / `seatsUpgraded` | 2 / 3 | Passenger seats besides the pilot, without and with the passenger seat. |
| `hold` / `holdUpgraded` | 27 / 36 | Hold slots, without and with the cargo bay extension. |
| `cameraDistance` | 8 | Blocks added to a rider's third-person camera distance while aboard. |

## `travel`

Class `com.oldrepublic.travel.ShipBalance.Travel`, read through `ShipBalance.travel()`. The switch, the combat lock, the flight's phases, pads, the beacon and the daily favours (spec 4.5, 4.6, 4.8).

| Key | Default | What it does |
|---|---|---|
| `enabled` | true | false: ships do not jump (repairs and the hulk still work); operators can still move a player with `/oldrepublic ship travel`. |
| `combatLock` / `combatLockRange` / `combatLockTicks` | true / 16 / 100 | Refuse a launch while a hostile within 16 blocks has hurt the owner or a crew member in the last 100 ticks. Hits between crew do not count. |
| `spoolTicks` / `climbTicks` / `jumpTicks` / `hyperspaceTicks` / `descentTicks` / `settleTicks` | 40 / 60 / 10 / 80 / 80 / 20 | Length of each phase of the flight. |
| `climbHeight` | 40 | Blocks the ship climbs before the jump. |
| `hyperspaceMaxWait` | 200 | Extra ticks the hyperspace phase waits for the destination chunks to load; after that the ship lands above the pad's column as found. |
| `padBays` / `bayLapseTicks` | 4 / 24000 | Bays of a hub pad; a bay claim lapses after this long with no owner or crew near the ship. |
| `overflowRings` | [40, 48, 56] | Rings from the hub centre where a ship lands when every bay is taken. |
| `campPadOffset` | 48 | The hunting camp's landing spot lies this far from the camp, toward the hub. |
| `chunkTicketRadius` / `ticketLingerTicks` | 3 / 200 | Chunk radius of the destination pad's ticket, and how long it stays after the Settle. |
| `beaconArea` / `beaconMaxSlope` | 11 / 2 | Side of the clear square the landing beacon needs, and its allowed slope. Set below 11, the pad's ground layer is not laid. |
| `freeRecallPerDay` / `freeTowPerDay` | 1 / 1 | Free recalls by a hub host and free tows per in-game day. |

## `blasters`

Class `com.oldrepublic.equipment.blaster.BlasterBalance`, read through `BlasterBalance.get()`. Arrays hold one value per weapon: pistol, rifle, repeater (spec 5.1). A short hand-edited array uses its last value for the missing weapons.

| Key | Default | What it does |
|---|---|---|
| `damage` | [4.0, 5.0, 2.5] | Damage per bolt, times the stack's quality multiplier when fired. Mods add their part on the attacker side, inside the damage clamp. |
| `rate` | [8, 10, 3] | Ticks between shots while the trigger is held. |
| `spinUp` | 12 | Repeater: ticks of barrel spin before the first bolt, and again after each vent or overheat. |
| `spinHoldTicks` (added) | 40 | Repeater: a spun barrel winds down after this many ticks without a shot. |
| `speed` / `life` | [2.2, 2.6, 2.4] / [40, 60, 50] | Bolt speed (blocks per tick) and life (ticks). |
| `hipSpread` | [2.0, 2.5, 4.0] | Hip-fire spread: the cone's half-angle in degrees. |
| `steadyMultiplier` / `moveMultiplier` / `airMultiplier` | 0.35 / 2 / 3 | Spread multipliers when sneaking, moving faster than walking (0.24 blocks per tick), and airborne. |
| `heatPerShot` | [15, 14, 7] | Heat each shot adds (0 to 100). |
| `cooling` / `coolDelay` | [2.5, 2.0, 1.5] / 16 | Heat lost per tick once 16 ticks have passed since the last shot. |
| `overheatLock` | [50, 60, 80] | Ticks an overheated blaster cannot fire; its heat bleeds to 0 over the lock. |
| `ventLock` / `ventMin` | [20, 25, 30] / 30 | Ticks a vented blaster cannot fire; a vent below 30 heat does nothing. |
| `perfectVentBand` / `perfectVentFreeShots` | [70, 90] / 3 | A vent with heat inside the band (inclusive) halves the lock, and the next 3 shots add no heat. |
| `chargedTicks` / `chargedDamage` / `chargedHeat` | 30 / 12 / 35 | Rifle: ticks Weapon Action is held to load the heavy bolt, its damage at Standard quality, and its heat. |
| `repeaterPierce` | 1 | Repeater: targets a bolt passes through before it stops (each pierce continues as a new bolt). |
| `suppressHits` / `suppressWindow` / `suppressTicks` | 4 / 40 / 30 | Repeater: 4 hits on one mob within 40 ticks slow it (Slowness II, diminishing) for 30 ticks. Players are never suppressed. |
| `bashStagger` | 6 | Ticks a blaster bash staggers its target. |
| `quickDrawTicks` | 4 | Pistol: the first shot after drawing it comes this many ticks sooner than the rate. |
| `durability` | [600, 800, 900] | Durability; one per shot and one per bash. Registration default only. |
| `rifleSlowPct` / `repeaterSlowPct` | 10 / 30 | Movement slow while firing, percent; the repeater also cannot sprint. Registration default only. |
| `bashDamage` | 2 | Damage of a bash (attacking with a blaster). Registration default only. |

**Weapon reference** (spec 14.2: level 12, no mods, Standard quality). W4 simulated the defaults with the 14.2 model: a perfect vent each time heat reaches the band, half the lock, three free shots, no cooling while firing, the next shot at the later of the rate and the lock, and the repeater's spin-up after each vent.

| Weapon | Burst DPS | Sustained, perfect vents | Sustained, plain vents | Spec 14.2 |
|---|---|---|---|---|
| Blaster pistol | 10.0 | 9.56 | 7.56 | 10.0 / about 9.7 |
| Blaster rifle | 10.0 | 9.65 | 7.58 | 10.0 / about 9.8 |
| Heavy repeater | 16.67 | 10.16 | 7.15 | 16.7 / about 10.3 |

The sustained figures are within 0.15 of the spec's, because the simulation counts whole ticks. A blaster stays behind an Unformed saber (11.2 DPS) over a fight, as intended.

## `melee`

Class `com.oldrepublic.equipment.melee.MeleeBalance`, read through `MeleeBalance.get()`. The vibroweapons and the electrostaff (spec 5.2, 5.3). Damage, attack speed, reach and durability are fixed at registration; the rest is live.

| Key | Default | What it does |
|---|---|---|
| `vibrobladeDamage` / `vibrobladeSpeed` / `vibrobladeDurability` | 6 / 1.8 / 500 | Vibroblade damage, attacks per second and durability; its reach is vanilla. Registration defaults only. |
| `flurryHits` / `flurryWindow` / `flurryBonus` | 3 / 30 / 3 | The 3rd hit of a chain, with at most 30 ticks between hits, deals +3 damage and leaves the attack cooldown charged. |
| `vibroswordDamage` / `vibroswordSpeed` / `vibroswordReach` / `vibroswordDurability` | 8.5 / 1.2 / 0.5 / 700 | Vibrosword damage, attacks per second, reach added and durability. Registration defaults only. |
| `cleaveTargets` / `cleaveRange` / `cleaveMultiplier` | 2 / 2 / 0.6 | A full vibrosword swing also hits up to 2 other enemies within 2 blocks in a 120° arc in front, at 60% of the swing's damage. |
| `cleaveMinStrength` (added) | 0.9 | Attack strength a swing needs to count as full. |
| `guardBladePct` / `guardSwordPct` / `guardStaffPct` | 50 / 65 / 60 | Share of front melee damage a raised guard blocks, percent. It never blocks a bolt and never parries. |
| `saberNotch` | 5 | Durability a vibroblade or vibrosword loses to a saber blow on its raised guard, or when a saber parries its blow. The electrostaff loses none. |
| `staffDamage` / `staffSpeed` / `staffReach` | 6.5 / 1.1 / 1.0 | Electrostaff damage, attacks per second and reach added. Registration defaults only. |
| `staffDurability` (added) | 600 | Electrostaff durability. Registration default only. |
| `staffChargeTicks` / `staffChargeWindow` | 40 / 100 | A Weapon Action press starts the charge; it loads after 40 ticks and is spent by a hit within 100, or lost. |
| `staffShockTicks` | 20 | Shock (Slowness I, diminishing, and a wind-up interrupt) on the struck target and the arc's target. |
| `staffArcRange` | 3 | The shock arcs to the nearest other enemy within this many blocks. |
| `staffArcDamage` (added) | 2 | Damage of the arc (indirect magic). |
| `droidShockMultiplier` | 1.5 | Droids take this much more from shocks: the staff's hit and arc, and a shock edge. |
| `staffSweepDamage` (added) | 1 | A full electrostaff swing on the ground, not sprinting, deals this to each enemy beside the struck one. |

## `armour_m4`

Class `com.oldrepublic.equipment.EquipmentM4Balance`, read through `EquipmentM4Balance.get()`. Per-piece traits and set bonuses of the four M4 sets (spec 6.2). Traits are multiplied per piece like M3, and a crafted piece's helpful traits grow with its quality (`qualityArmour`); drawbacks do not. Base defence, toughness, durability and enchantability are not in the file (see "Values outside `balance.json` (Milestone 4)").

| Key | Default | What it does |
|---|---|---|
| `watchCostPct` / `watchMeleePct` | -3 / -4 | Watch battle armour, per piece: Force cost and melee damage taken. |
| `watchSetParryResistPct` / `watchSetBarrierCostPct` / `watchSetTicks` | -15 / -20 / 80 | Warden's Resolve (the light gate, `equipment.setAlignment`): for 80 ticks after a parry, melee damage taken -15% and Force Barrier cost -20%. |
| `sithWarMaxEnergy` / `sithWarMeleePct` / `sithWarRegenPct` | 4 / -5 / -3 | Sith war armour, per piece: max Force Energy, melee damage taken, Force regeneration. |
| `sithWarSetHealthFraction` / `sithWarSetCostPct` / `sithWarSetDamagePct` | 0.5 / -20 / 10 | Wrath Unbound (the dark gate): below half health, Force Choke and Force Lightning cost -20% and deal +10%. |
| `mandoBlasterPct` / `mandoCostPct` | -6 / 5 | Mandalorian armour, per piece: blaster damage taken and Force cost. |
| `mandoSpeedPct` | -3 | Mandalorian armour, per piece: movement speed. Registration default only. |
| `mandoSetControlPct` / `mandoSetJetCharges` / `mandoSetSpeedCostPct` | -30 / 1 / 10 | Hunter's Tenacity (no gate): control durations on the wearer -30%, one more jet pack charge, Force Speed cost +10%. |
| `spacerSpeedPct` | 2 | Spacer's gear, per piece: movement speed. Registration default only. |
| `spacerRegenPct` / `spacerFallPct` | 4 / -10 | Spacer's gear, per piece: Force regeneration and fall damage taken. |
| `spacerSetSpeedDurationPct` / `spacerSetFallPct` | 30 / -50 | Light on Your Feet (no gate): Force Speed lasts +30% (read by `force_speed` through `SetBonuses.forceSpeedDurationMultiplier`), and fall damage taken a further -50%. |
| `spacerSetTerrainEfficiency` (added) | 1 | Light on Your Feet: movement efficiency added (0 to 1), so soul sand and honey do not slow the wearer. |
| `controlMin` (added) | 0.4 | The smallest multiplier all gear together may put on a control duration. |

## `mods`

Class `com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers`, read through `EquipmentM4Balance.mods()`. Arrays hold one value per mod quality: Standard, Fine, Masterwork (spec 6.3, 6.4). Worn armour mods are summed over the four armour slots.

| Key | Default | What it does |
|---|---|---|
| `platingArmour` / `platingBlasterPct` | 1 / [-2, -3, -4] | `mod_plating`: armour added per piece, and blaster damage taken. |
| `weaveMeleePct` | [-2, -3, -4] | `mod_weave`: melee damage taken. |
| `attunementCostPct` | [-2, -3, -4] | `mod_attunement`: Force cost. |
| `conduitRegenPct` | [3, 4.5, 6] | `mod_conduit`: Force regeneration. |
| `stabiliserControlPct` | [-8, -12, -16] | `mod_stabiliser`: control durations on the wearer (bounded by `armour_m4.controlMin`). |
| `focusedSpreadPct` | [-20, -30, -40] | `mod_focused_barrel`: blaster spread. |
| `heavyDamagePct` / `heavyRateTicks` | [6, 9, 12] / 1 | `mod_heavy_barrel`: bolt damage, and ticks added between shots. |
| `coolingHeatPct` | [-15, -22, -30] | `mod_cooling_cell`: heat per shot. |
| `overchargedDamagePct` / `overchargedHeatPct` | [4, 6, 8] / 10 | `mod_overcharged_cell`: bolt damage, and heat per shot. |
| `keenDamagePct` | [5, 7.5, 10] | `mod_keen_edge`: melee damage. |
| `shockChancePct` / `shockTicks` | [8, 12, 16] / 20 | `mod_shock_edge`: chance per hit to shock, and the shock's length in ticks. |
| `jetCharges` / `jetRefillTicks` | 2 / 100 | Jet pack charges; all refill after this many ticks in a row on the ground. |
| `jetUp` / `jetForward` | 0.85 / 0.3 | A burst's upward velocity and its velocity along the horizontal look (half the old horizontal speed is kept). |
| `jetFallPct` / `jetLandWindow` | -50 / 20 | Fall damage taken when a burst came this many ticks or less before landing. |
| `jetBurstGapTicks` (added) | 4 | Ticks between two bursts, so a held key cannot spend every charge at once. |

## `crafting`

Class `com.oldrepublic.crafting.CraftingBalance`, read through `CraftingBalance.get()`. Disciplines, quality, salvage, trainers, consumables, tools, grenades and lenses (spec 7.1 to 7.6, 5.7). Schematic inputs, outputs, craft times, levels and XP are data in `data/oldrepublic/schematic/*.json`, not here.

| Key | Default | What it does |
|---|---|---|
| `levelXp` | [0, 120, 360, 800, 1600] | Total discipline XP needed for levels 1 to 5. |
| `secondDisciplineLevel` / `secondDisciplinePlayerLevel` | 2 / 13 | The second discipline opens at this level in the first, or at this player level. |
| `lowLevelXpMultiplier` | 0.25 | Discipline XP multiplier for a schematic two or more levels below the crafter. |
| `firstCraftXpPerLevel` | 30 | Player XP for the first craft of each schematic, per schematic level. |
| `fineBase` / `finePerLevel` | 0.18 / 0.06 | Fine chance, plus this per discipline level above the schematic's. |
| `masterworkBase` / `masterworkPerLevel` | 0.03 / 0.03 | Masterwork chance, plus this per level above. One draw picks Masterwork, Fine or Standard. |
| `pityCrafts` | 10 | Quality crafts below Masterwork, per discipline, before the next one is Masterwork without a draw. |
| `qualityWeaponDamage` / `qualityArmour` / `qualityDurability` | [1.0, 1.06, 1.12] / [1.0, 1.15, 1.30] / [1.0, 1.25, 1.5] | Standard, Fine and Masterwork multipliers for weapon damage, armour (and helpful M4 traits) and durability. |
| `craftCancelRange` | 6 | Walking further than this from the bench cancels a craft, with no loss. |
| `salvageReturn` / `salvageXp` | 0.5 / 0.25 | Share of each input salvage returns (rounded down, at least one of the first input), and the share of the craft's discipline XP it gives. |
| `unlearnCost` | 500 | Credits to unlearn a discipline at a trainer. |
| `commissionPerLevel` | 50 | Credits a trainer's commission charges per schematic level, on top of the inputs. |
| `medpacUseTicks` / `medpacHeal` / `medpacTicks` / `medpacInterruptDamage` | 16 / 8 / 40 / 4 | Medpac: use time, health healed over 40 ticks, and the damage of one hit that stops the use. |
| `healCooldown` | 200 | Cooldown a medpac puts on the medpac and the kolto injector. |
| `koltoUseTicks` / `koltoHeal` / `koltoCooldown` | 10 / 6 / 300 | Kolto injector: use time, health healed at once (it also removes poison and wither), its own cooldown. |
| `stimTicks` / `combatStimPct` / `focusStimPct` / `crashTicks` | 1200 / 8 / 20 / 200 | Stims, one at a time: the combat stim's +8% damage or the focus stim's +20% Force regeneration lasts 1200 ticks, then a 200-tick crash (Weakness I, or -20% regeneration). |
| `scannerRange` / `scannerTicks` / `scannerCooldown` | 48 / 200 / 400 | Scanner: pulse range, how long the marks show, cooldown. |
| `playerGrenadeDamage` / `playerGrenadeRadius` | 7 / 3 | The player's frag grenade. |
| `stunRadius` / `stunTicks` / `stunDroidTicks` | 3 / 30 / 60 | Stun grenade: radius, stagger, and the longer stagger for droids. |
| `grenadeCooldown` | 60 | Cooldown shared by both grenades. |
| `cutterSealTicks` | 60 | Held use of the fusion cutter that cuts a sheet of sealed plating. |
| `cutterDurability` / `repairKitUses` | 250 / 3 | Fusion cutter durability and repair kit uses, read when the items are registered (see "Values baked into things"). |
| `repairKitPct` | 25 | Share of durability a repair kit restores to the item in the other hand, or else the most worn armour piece. |
| `lensHoned` / `lensSteady` / `lensResonant` | [6, 9, 12] / [1, 1, 2] / [5, 6.5, 8] | Lens effects by quality: saber damage +%, parry window + ticks (under the cap), Force power damage +%. |

## `saber` (Milestone 4 additions)

Same class as above, `com.oldrepublic.saber.SaberBalance`. The training saber, the saberstaff, and the numbers of W6's `staff_whirl` and `lens_mastery` nodes (spec 5.5 to 5.7).

| Key | Default | What it does |
|---|---|---|
| `trainingSaberDamage` | 2.5 | Ignited damage of a training saber. A training blow never kills a player: it leaves them at 1 health. |
| `staffDamagePct` / `staffSpeedBonus` / `staffReach` | -10 / 0.2 / 0.5 | Saberstaff with both blades lit, against a saber of the same parts: damage per hit (percent), attacks per second added, reach added. With one blade it has a normal saber's damage and keeps the speed and reach. |
| `staffGuardDrainPct` / `staffParryTicks` / `staffConeDegrees` | 20 / -1 / 15 | Both blades lit: every block drains 20% more guard stamina, the parry window is 1 tick shorter, and the guard and deflection arc is 15° wider. |
| `staffThrowTargets` | 3 | A thrown staff hits at most this many targets. |
| `staffThrowSpeedMult` / `staffThrowWidth` (added) | 0.8 / 0.6 | A thrown staff's speed and return speed multiplier, and its extra hit-box width in blocks. |
| `whirlRange` / `whirlPct` / `whirlWindup` / `whirlRecovery` | 2.5 / 75 / 14 / 20 | The whirl (a staff's heavy attack): after a 14-tick wind-up every enemy within 2.5 blocks takes 75% of the ignited damage, then 20 ticks of recovery. Any held staff whirls, on one blade or two. |
| `staffWhirlStaggerTicks` | 10 | With the `staff_whirl` node, each whirl target is staggered this long. |
| `lensMasteryPct` | 50 | With the `lens_mastery` node, lens effects are 50% stronger. |

## `chapter_four`

Class `com.oldrepublic.quest.story.ChapterFourBalance`, read through `ChapterFourService.balance()`. Chapter Four, "Stars to Steer By" (spec 8, 10.6, 12). Every reward is paid once, behind a stage entry, a one-time flag or a per-day counter. Kaast's own rewards are in `kaast`.

| Key | Default | What it does |
|---|---|---|
| `waitTicks` / `minSleepTicks` | 24000 / 100 | MARKED: the seeker droid is due a day after Chapter Three, or at once on waking from a sleep of at least 100 ticks. |
| `seekerDescend` / `seekerScanTicks` | 20 / 40 | The seeker comes down from 20 blocks above the player and scans for 40 ticks. |
| `seekerHostileRange` (added) | 24 | No hostile may be this close for the seeker scene to start (the safe moment). |
| `briefXp` / `partXp` | 300 / 300 | XP for the briefing, and for each ship part fitted (once per socket). |
| `salvageRange` | 8 | Force Sense within this many blocks of a Wreck Field's `HEART` anchor finds the motivator. |
| `offTheBooksXp` / `offTheBooksCredits` | 1200 / 100 | "A Part Off the Books", the smuggler's route to the motivator. |
| `arcMissionXp` / `arcPowerMissionXp` / `arcMissionCredits` | 1500 / 2000 / 150 | Each world arc mission; the mission that grants the arc's power pays the higher XP. |
| `expeditionXp` / `expeditionSp` | 2000 / 1 | Each expedition, once. |
| `arcRep` / `arcPowerRep` / `arcPowerTrust` | 40 / 60 / 10 | The giver's faction standing for an arc mission and for the power mission; the mentor's trust for the power mission. |
| `planetXp` / `hubXp` (added) | 400 / 200 | XP for the first landing on each planet, and for the first visit within 48 blocks of its hub. These take the place of `planets.planetDiscoveryXp` and `hubDiscoveryXp`. |
| `heartstoneTicks` / `heartstoneGrantTicks` / `heartstoneWaves` | 1800 / 900 / [0, 600, 1200] | The Heartstone's cleansing: its length, when Force Barrier is granted, and when each of the three waves starts (ticks from the start). |
| `heartstoneHollowhorns` / `heartstoneSentinels` (added) | 2 / 1 | Each wave's corrupted hollowhorns and Watcher sentinels. |
| `trialRing` (added) | 12 | Standing this close to the Heartstone or to Zarvos's echo counts as being in the trial. |
| `sepulchreGripTicks` | 40 | Zarvos's echo holds the player this long before Force Choke is granted. |
| `sabotageXp` / `ambushXp` | 300 / 800 | The hunt: the sabotage, and each ambush survived. |
| `ambushSize` / `ambushRange` | [3, 4] / [24, 32] | Hunters in the first and second ambush, and how far from the player they land. |
| `ambushMinDistance` / `ambushGapDays` / `ambushLeaveRange` / `ambushRetryDays` | 96 / 2 / 160 / 1 | The first ambush comes at least 96 blocks from home, the second 2 days later; fleeing beyond 160 blocks ends an ambush, which comes again after a day. |
| `duelLevel` | 14 | The level the challenge needs (with the arc done and three clues). |
| `recommendedLevel` (added) | 15 | The level shown on the CHALLENGED objective. |
| `beaconDistance` (added) | 6 | The marker beacon lands this far ahead of the player. |
| `campRing` (added) | 20 | The owner within this many blocks of the camp's anchor is in its ring, which moves the chapter to DUEL. See "One value, one place". |
| `stashAlloy` / `stashFuel` / `stashCredits` (added) | 2 / 4 / 200 | The camp's stash: alloy plates, hyperfuel cells, and the credits given when no Armstech schematic is lacking. |
| `reportXp` / `reportTrust` / `reportResonance` | 800 / 10 / 300 | The report that ends the chapter. |
| `spareLight` / `endDark` / `endJediTrust` / `endSithTrust` | 10 / -10 / -15 / 10 | The endings: sparing Kaast moves alignment +10; ending him moves it -10, Jedi trust -15 and Sith trust +10. |
| `hunterBounties` / `hunterChance` / `hunterCheckTicks` | true / 0.10 / 1200 | After the chapter, a bounty hunter comes for an off-world player at a 10% chance every 1,200 ticks. |
| `hunterSchematicChance` (added) / `hunterSchematicPity` | 0.08 / 10 | A hunter killed by a player gives a Mandalorian armour schematic piece at 8%, guaranteed by the 10th such kill. Only hunters whose loot counts roll (never one Kaast called, never anything a hub spawned). |
| `retryTicks` / `forwardFallbackAfter` / `forwardFallback` (added) | 1200 / 3 / 12 | A failed placement (the hulk, the camp, an ambush) retries every 1,200 ticks; after 3 failures the hulk is placed 12 blocks ahead of the mentor. |

## `vendors`

Class `com.oldrepublic.vendor.VendorBalance`, read through `VendorBalance.get()`. Credits, vendors, contract pay and the hub spar (spec 7.7, 7.8, 8.7, 8.8). Prices, tiers and daily limits are data (see "Values outside `balance.json` (Milestone 4)").

| Key | Default | What it does |
|---|---|---|
| `priceMultiplier` | 1.0 | Every vendor price is multiplied by this. |
| `honouredDiscount` | 0.9 | Price multiplier at Honoured with the vendor's faction. |
| `sellRate` | 0.2 | A vendor buys an item back at this share of its base value. |
| `holdPaymentRange` | 32 | Not used yet. Meant as the distance within which the ship's hold pays; the rule is the code constant `Credits.HOLD_RANGE` (32), as spec 14.3 itself notes. |
| `creditDropChance` / `creditDrop` | 0.5 / [1, 6] | A trooper, Mandalorian or security droid killed by a player in a fight that counts drops 1 to 6 chits at 50%. Natural patrols, hunters whose loot does not count and anything a hub spawned never drop. |
| `contractCreditsBase` / `contractCreditsPerLevel` | 25 / 5 | Planet and outpost contracts pay 25 + 5 x level credits, at full pay only. |
| `backPayPerMilestone` / `backPayMax` | 100 / 300 | The one-time Milestone 3 back pay: 100 per milestone held, at most 300. |
| `maxPurchase` (added) | 64 | The most of one line a single purchase may buy. |
| `reach` (added) | 8 | A purchase is accepted, and the vendor screen stays open, within this many blocks of the vendor. |
| `hubSparYield` / `hubSparDamage` (added) | 0.4 / 0.5 | The hub spar contract: the apprentice or acolyte yields at 40% health, and its blows are scaled by 0.5. |

## Force powers: `force_speed`, `force_barrier`, `force_choke`

Classes `com.oldrepublic.force.speed.SpeedBalance`, `force.barrier.BarrierBalance` and `force.choke.ChokeBalance`, read through `BalanceConfig.section` (spec 9.1 to 9.3, and the 9.5 node numbers). Force Energy costs go through `ForcePowers.costMultiplier`, like every power's, so armour, mods and the M3 modifiers apply.

### `force_speed`

| Key | Default | What it does |
|---|---|---|
| `cost` / `cooldownTicks` | 20 / 600 | Force Energy of a cast; the cooldown, counted from the end of the run. |
| `durationTicks` / `speedPct` / `stepHeight` | 160 / 40 / 0.5 | The run's length, its movement speed bonus and its extra step height. The length is multiplied by Light on Your Feet (`armour_m4.spacerSetSpeedDurationPct`). |
| `sprintJumpPct` | 20 | Not used yet. Meant as how much further a sprint jump carries; the client moves its own player and adds a fixed 20% (`SpeedFx`), because the client never reads the server's file. |
| `sprintHungerMult` | 0.5 | Sprinting hunger is multiplied by this during the run. |
| `burstTicks` / `burstSpeedPct` / `burstDistance` / `burstDashTicks` | 20 / 100 / 6 / 4 | Burst (`speed_burst`): the first 20 ticks run at +100%, and the cast dashes up to 6 blocks along the look over 4 ticks, stopping at the first wall. |
| `longRoadTicks` / `longRoadExtraCost` | 300 / 5 | The Long Road (`speed_long_road`): the run lasts 300 ticks and costs 5 more. |
| `wayfarerMissChance` / `wayfarerMaxDegrees` / `wayfarerSafeFall` | 0.25 / 1.5 / 8 | Wayfarer (`speed_wayfarer`, granted to Independents): a projectile about to hit the runner misses at 25%, turned aside by at most 1.5 degrees and never into a wall; falls under 8 blocks do no damage during the run. |
| `masteryBlocks` / `masteryCostPerRank` / `masteryTicksPerRank` | 200 / 2 / 20 | Mastery: a point per 200 blocks run; each rank makes a cast 2 cheaper and the run 20 ticks longer. |
| `pvpEffects` | false | Kept for the shared shape; Speed affects only its caster. |

### `force_barrier`

| Key | Default | What it does |
|---|---|---|
| `startCost` / `channelCost` / `maxTicks` / `cooldownTicks` | 15 / 6 / 120 / 400 | Force Energy to raise it and per second while it is held; the longest hold; the cooldown from the release. |
| `radius` / `shelterRadius` | 2 / 3.5 | The sphere's radius around the caster's chest; with Shelter (`barrier_shelter`), which also guards allies and harmless creatures inside it. |
| `boltCost` / `heavyBoltCost` | 3 / 10 | Force Energy each stopped bolt costs, and each stopped heavy bolt. |
| `returnDamageMult` | 0.6 | Return (`barrier_return`): stopped bolts fly back at their shooter with 60% of their damage. |
| `blastMult` | 0.3 | Share of a rocket's or grenade's burst at the surface that still reaches inside. |
| `meleePct` / `flamePct` | 50 / 50 | Melee and flame damage to the protected are cut by these percentages. |
| `breakDamage` / `breakStaggerTicks` | 10 / 20 | One hit of at least 10 (before the barrier) breaks it and staggers the caster 20 ticks. |
| `sprintMoveMult` | 0.77 | While it is held, sprinting is cut back to walking speed. |
| `pulseMinTicks` / `pulseRadius` / `pulseKnockback` / `pulseStaggerTicks` | 40 / 3 / 0.85 / 10 | Releasing after at least 40 ticks sends a pulse that throws enemies within 3 blocks back and staggers them 10 ticks. |
| `guardiansPatienceFe` / `guardiansPatienceTicks` | 2 / 40 | Guardian's Patience (`guardians_patience`): each saber parry within 40 ticks of the barrier ending restores 2 Force Energy. |
| `masteryProjectiles` / `masteryMeleeAbsorbed` | 5 / 30 | Mastery: a point per 5 projectiles stopped, or per 30 melee damage absorbed. |
| `masteryCostPerRank` / `masteryTicksPerRank` | 1 / 10 | Each rank: 1 less Force Energy per second and 10 ticks more of hold. |
| `pvpEffects` | false | Whether the pulse pushes other players (the server's PvP must be on too). Bolts are always stopped. |

### `force_choke`

| Key | Default | What it does |
|---|---|---|
| `startCost` / `channelCost` / `maxTicks` / `cooldownTicks` | 25 / 6 / 60 / 360 | Force Energy to start and per second while it is held; the longest hold; the cooldown from the release. |
| `range` / `coneDegrees` | 12 / 6 | Reach of the crosshair ray, and of the cone tried when the ray finds no one. The cone is a full angle (3 degrees either side). |
| `liftHeight` | 0.6 | How high a held creature is lifted. |
| `damage` / `damageInterval` | 2 / 10 | Damage every 10 ticks of the hold (the `force_choke` damage type, tagged `force_harm`). |
| `releaseStaggerTicks` | 15 | The stagger when the target drops. |
| `interruptDamage` | 6 | One hit of at least this much on the caster ends the channel. |
| `bossMaxTicks` | 20 | The longest hold on a `c:bosses` target, whatever its own rule allows. |
| `nonHostileShift` | -3 | Alignment shift for choking a creature that is not hostile, or has yielded; once per target per day. |
| `crushMinTicks` / `crushDamage` | 40 / 6 | Crush (`choke_crush`): a release after at least 40 ticks deals 6 more. |
| `dreadRadius` / `dreadTicks` | 6 / 40 | Dread (`choke_dread`): the target's allies within 6 blocks, bosses excepted, flee for 40 ticks. |
| `cruelFocusPct` / `cruelFocusWindow` | 20 / 40 | Cruel Focus (`cruel_focus`): Lightning on a target released from the caster's Choke within 40 ticks deals 20% more. |
| `masteryHoldTicks` | 20 | Mastery: a point per hostile target held at least this long. |
| `masteryCostPerRank` / `masteryRangePerRank` | 2 / 1 | Each rank: 2 cheaper to start and 1 block more range. |
| `pvpEffects` | false | Whether Choke may hold other players (the server's PvP must be on too), through the M3 held state. |

## `mandalorian`

Class `com.oldrepublic.enemy.mandalorian.HunterBalance`, read through `HunterBalance.get()`. The Mandalorian hunter (spec 10.1). Kaast uses its flamer and some rocket keys (see "One value, one place").

| Key | Default | What it does |
|---|---|---|
| `maxHealth` / `armor` / `movementSpeed` / `knockbackResistance` / `followRange` | 40 / 10 / 0.28 / 0.2 / 40 | Attributes. |
| `minRange` / `maxRange` | 8 / 18 | The rifle band: the hunter holds between these distances and strafes. |
| `rifleDamage` / `burst` / `burstCooldownTicks` / `burstGapTicks` / `rifleTellTicks` | 6 / 2 / 40 / 5 / 10 | The rifle: gold bolts in bursts of 2, 5 ticks apart, 40 ticks between bursts, each burst after a 10-tick aim tell. |
| `boltSpeed` / `boltLifetimeTicks` / `aimInaccuracy` | 2.0 / 50 / 2.5 | The rifle bolt's speed and lifetime, and the aim's spread (vanilla inaccuracy units). |
| `lead` / `speedLead` | 0.75 / 1.0 | Share of the target's motion over the bolt's flight added to the aim; a Force Speed runner gets the larger one. |
| `jetIntervalTicks` / `jetCloseRange` / `jetTellTicks` | 200 / 5 / 20 | The jet hop: every 200 ticks, or when a player closes within 5 blocks, after a 20-tick tell. |
| `jetMinDistance` / `jetMaxDistance` / `jetFlightTicks` / `jetArcHeight` | 6 / 10 / 16 / 3 | The hop lands 6 to 10 blocks away, takes 16 ticks and arcs 3 blocks high. |
| `jetStaggerTicks` / `jetMinGapTicks` | 40 / 60 | Pushed out of a hop, the hunter is staggered 40 ticks on landing; hops are never closer than 60 ticks, even when rushed. |
| `flamerRange` / `flamerTellTicks` / `flamerConeLength` / `flamerConeDegrees` | 4 / 14 / 3 / 30 | The wrist flamer: under 4 blocks, a 14-tick tell, then a cone 3 blocks long. The angle is read as a half-angle, so the cone is 60 degrees across (see "Known mismatches"). |
| `flamerTicks` / `flamerDamage` / `flamerDamageInterval` / `flamerFireSeconds` / `flamerCooldownTicks` | 30 / 2 / 5 / 3 / 120 | The flame lasts 30 ticks, deals 2 every 5 ticks and sets 3 seconds of fire; 120 ticks between flames. |
| `rocketIntervalTicks` / `rocketMinRange` / `rocketMaxRange` / `rocketTellTicks` | 300 / 8 / 20 / 24 | The wrist rocket: every 300 ticks at 8 to 20 blocks, after a 24-tick tell (the red targeting dot). |
| `rocketSpeed` / `rocketDamage` / `rocketRadius` / `rocketLifetimeTicks` / `rocketPushSpeed` | 0.55 / 7 / 2 / 100 / 0.9 | The rocket's speed, burst damage and radius, its lifetime, and its speed when a Push sends it back. |
| `yieldAt` | 0.2 | The last hunter of a story ambush yields at this share of its health. |
| `coverHealthFraction` / `coverSpeed` / `coverHoldMinTicks` / `coverHoldMaxTicks` / `coverCooldownTicks` | 0.4 / 1.3 / 40 / 70 / 240 | Cover, through the M1 `PoacherCoverGoal`: a hunter below 40% health that was hurt recently runs to cover at 1.3 speed, stays 40 to 70 ticks, and may do it again after 240 ticks. |
| `ownerLeash` / `ownerAbsentTicks` | 160 / 200 | An owned hunter whose owner is gone (offline, in another level, or beyond 160 blocks) for 200 ticks leaves. |
| `dropHeight` / `ambushSpread` | 14 / 4 | Ambush hunters drop in on jet packs from 14 blocks up and land 4 blocks apart. |

## `security_droid`

Class `com.oldrepublic.enemy.droid.DroidBalance`, read through `DroidBalance.get()`. The Mk IV security droid (spec 10.2): a hunter escort, or the Proving's Imperial patrol.

| Key | Default | What it does |
|---|---|---|
| `maxHealth` / `armor` / `movementSpeed` / `knockbackResistance` / `followRange` | 50 / 12 / 0.2 / 0.5 / 32 | Attributes. |
| `minRange` / `maxRange` | 6 / 16 | It holds between these distances. |
| `blasterMult` / `parriedBoltMult` | 0.5 / 1.5 | Blaster bolts deal 50%; its own bolts parried back at it deal 150%. |
| `shockMult` | 1.5 | Force Lightning deals 150%. The electrostaff and shock edges apply their own droid multiplier (`melee.droidShockMultiplier`). |
| `pushMult` | 0.5 | Force Push moves it half as far. |
| `spinTellTicks` / `volleyBolts` / `volleyTicks` / `volleyCooldownTicks` | 20 / 8 / 40 / 80 | Firing: a 20-tick spin-up tell, 8 bolts over 40 ticks, then 80 ticks of cooldown. |
| `boltDamage` / `boltSpeed` / `boltLifetimeTicks` / `aimInaccuracy` | 4 / 1.8 / 50 / 4 | Each bolt. |
| `shieldAt` / `shieldTicks` / `shieldDegrees` | 0.5 / 100 / 70 | Below half health, once: a frontal plate for 100 ticks that stops projectiles arriving within 70 degrees either side of its facing. |
| `deathTellTicks` / `deathBurstDamage` / `deathBurstRadius` | 20 / 2 / 1.5 | Death: 20 ticks of sparking, then a burst of 2 damage within 1.5 blocks. |
| `ownerLeash` / `ownerAbsentTicks` | 160 / 200 | An owned droid whose owner is gone this long powers down and leaves, as the hunters do. |

## `creatures`

Class `com.oldrepublic.enemy.creature.CreatureBalance`, read through `CreatureBalance.get()`. The hollowhorn and the ash hound and their natural spawns (spec 10.3, 10.4, 3.4).

| Key | Default | What it does |
|---|---|---|
| `hollowhornHealth` / `bullHealth` / `hollowhornSpeed` / `hollowhornKnockbackResistance` | 30 / 45 / 0.25 / 0.4 | Hollowhorn attributes; the herd bull has more health. |
| `angerTicks` / `calfGuardRange` / `herdRange` | 400 / 6 / 16 | Neutral until hurt, then hostile for 400 ticks. A player within 6 blocks of a calf angers the herd, and anger spreads 16 blocks through it. |
| `chargeTellTicks` / `chargeMinRange` / `chargeRange` / `chargeSpeed` | 20 / 4 / 14 / 0.6 | The charge: a 20-tick tell (a hoof scraping), from at least 4 blocks away, running at most 14 blocks at 0.6 blocks a tick. |
| `chargeDamage` / `chargeKnockback` / `chargeCooldownTicks` | 8 / 1.6 / 80 | The charge's hit and knockback, and the time between charges. |
| `stunTicks` / `stunDamageMult` | 50 / 1.5 | A charge into a solid block stuns it 50 ticks, and it takes 150% damage meanwhile. |
| `buttDamage` / `buttCooldownTicks` | 4 / 30 | Up close, a horn butt. |
| `herdMin` / `herdMax` / `hollowhornWeight` / `calfChance` / `calfScale` | 3 / 5 / 10 / 0.35 / 0.6 | Herds of 3 to 5 at spawn weight 10; after the first two, each member is a calf at 35%, drawn at 60% size. |
| `houndHealth` / `houndSpeed` / `biteDamage` / `biteCooldownTicks` | 18 / 0.35 / 3 / 20 | Ash hound attributes and bite. |
| `packMin` / `packMax` / `houndWeight` | 3 / 4 / 12 | Packs of 3 to 4 at spawn weight 12, at dusk and night (always in the ashfall). |
| `howlRange` / `flankRadius` | 24 / 5 | The howl calls every hound within 24 blocks; flankers circle at 5 blocks before lunging. |
| `lungeTellTicks` / `lungeRange` / `lungeDamage` / `lungeCooldownTicks` / `lungeStaggerTicks` | 10 / 6 / 5 / 50 / 30 | The lunge: a 10-tick crouch, up to 6 blocks, 5 damage, 50 ticks between lunges; a parried or pulsed lunge staggers the hound 30 ticks. |
| `houndHubExclusion` | 64 | No natural hound spawns within 64 blocks of the Proving. |

## `kaast`

Class `com.oldrepublic.boss.kaast.KaastBalance`, read through `KaastBalance.get()`. Torvin Kaast and the camp fight (spec 10.5, 10.6). W6 reads the fight keys; W2's `KaastDuel` reads the reward keys. Nothing kills him: his health never falls below the yield floor while the fight runs.

| Key | Default | What it does |
|---|---|---|
| `maxHealth` / `perParticipant` / `scalingCap` | 500 / 0.6 / 4 | Health 500 x (1 + 0.6 per extra participant), with at most 4 participants counted: 500, 800, 1,100, 1,400. Above vanilla's 1024 cap the attribute stays at 1024 and each hit is scaled by 1024 / the scaled health. |
| `minesPerParticipant` / `huntersPerParticipant` | 2 / 1 | Extra mines and extra called hunters per extra participant. |
| `armor` / `knockbackResistance` / `movementSpeed` (added) | 12 / 0.6 / 0.3 | Attributes. |
| `controlShorten` | 0.4 | Control effects on him last 40% less (his own rule, not the M3 diminishing returns, and not the Choke hold). |
| `liftFallBlocks` | 1 | Lift raises him at most 1 block, and only during a phase 1 fall stagger. |
| `rattleHealth` | 20 | Rattle's health. |
| `arenaRadius` (added) | 20 | The ring round the pit: the owner entering it starts the fight, and players inside it are participants. See "One value, one place". |
| `phase2At` / `phase3At` / `yieldAt` | 0.65 / 0.30 / 0.10 | Phase 2 below 65% health, phase 3 below 30%; at 10% he kneels and yields. |
| `transitionTicks` | 60 | A phase change takes 60 ticks, during which he takes no damage. |
| `hopTellTicks` / `hopInterval` / `hopFlightTicks` (added) | 30 / 160 / 24 | Phase 1: he hops between nests every 160 ticks after a 30-tick tell; a hop takes 24 ticks. |
| `rifleDamage` / `burst` / `burstCooldown` | 6 / 3 / 40 | Phase 1: rifle bursts of 3, 40 ticks apart. |
| `rocketInterval` / `rocketTellTicks` / `rocketDamage` | 300 / 24 / 7 | A wrist rocket every 300 ticks after a 24-tick tell. Its speed, radius and range are the hunters'. |
| `markBonus` | 0.25 | His bolts deal 25% more to a player Rattle is scanning. |
| `rattleScanTicks` / `rattleRestTicks` / `rattleScanRange` (added) | 80 / 40 / 24 | Rattle scans for 80 ticks, then rests for 40, within 24 blocks. |
| `nestFallStagger` / `parriedBoltStagger` (added) | 40 / 20 | Pushed off a nest, he falls and is staggered 40 ticks; a bolt parried back staggers him 20. |
| `mines` / `mineArmTicks` / `mineKneelTicks` | 6 / 40 / 30 | Phase 2: he sets 6 mines, kneeling 30 ticks for each; a mine blinks for 40 ticks before it is live. |
| `mineDamage` / `mineRadius` | 8 / 1.5 | A live mine goes off for anyone not on his side within 1.5 blocks, for 8 damage. |
| `kneelHeavyDamage` / `kneelStagger` (added) | 9 / 30 | A blow of at least 9 while he kneels interrupts the mine and staggers him 30 ticks. |
| `netInterval` / `netTellTicks` / `netRootTicks` / `netSaberHits` | 400 / 20 / 60 / 3 | A net every 400 ticks after a 20-tick tell roots a player for 60 ticks or until 3 saber hits; a Push breaks it at once. |
| `hunters` / `huntersAt` | 2 / 0.5 | At 50% health he calls 2 hunters. |
| `comboDamage` / `comboWindup` | [8, 8, 12] / 10 | Phase 3: three-blow axe combos; the third blow comes after a 10-tick wind-up. |
| `comboGapTicks` / `comboCooldown` (added) | 8 / 40 | Ticks between the first blows of a combo, and after a combo. |
| `guard` / `parryGuardDamage` | 120 / 40 | The axe's guard against sabers from the front; a parried third blow costs it 40. |
| `comboParryStagger` / `comboBreakStagger` (added) | 30 / 10 | A parried third blow staggers him 30 ticks; an earlier parried blow, 10. |
| `blockDrainPerDamage` / `blockDamageFraction` (added) | 3 / 0.15 | A saber blow the axe blocks drains 3 guard per point of damage and still deals 15% of it. |
| `guardBreakTicks` / `guardRegenTicks` (added) | 40 / 100 | A broken guard staggers him 40 ticks and comes back full after 100. |
| `chargeTell` | 14 | The shoulder charge's tell. |
| `chargeDamage` / `chargeSpeed` / `chargeRange` / `chargeCooldown` (added) | 8 / 0.7 / 10 / 160 | The shoulder charge's damage, speed, range and cooldown. |
| `slamTellTicks` / `slamDamage` / `slamRadius` | 40 / 12 / 3 | One jet slam: a 40-tick rise, then 12 damage within 3 blocks. |
| `slamHeight` / `slamAt` (added) | 10 / 0.2 | The slam rises 10 blocks, and comes once his health falls to 20%. |
| `chokeTicks` | 20 | Choke holds him for 20 ticks, once, in phase 3 only. |
| `chokeBurnStagger` (added) | 30 | When his one Choke ends he burns it off with the flamer, staggering the caster 30 ticks. |
| `resetTicks` / `participantLeaveTicks` | 200 / 100 | Nobody in the ring for 200 ticks resets the fight; a participant out of the ring for 100 ticks leaves the list. |
| `ownerXp` / `ownerSp` / `ownerCredits` / `resonance` | 4000 / 2 / 300 / 500 | The owner's first clear, paid once by W2. |
| `helperXp` | 1000 | A helper in another player's duel, once per day. |
| `rematchXp` / `rematchSchematicPity` / `rematchCredits` | 800 / 4 / 100 | The owner's daily rematch with Kaast's echo: 800 XP and 100 credits, and a Mandalorian schematic piece on a 1-in-4 roll, guaranteed on the 4th clear without one. |
| `placeCamp` | true | false: camps are placed only by `/oldrepublic kaast camp <player>`. |

### Values outside `balance.json` (Milestone 4)

- M4 armour base values (`equipment.EquipmentMaterials`, fixed at registration):

| Set | Defence (helmet / chest / legs / boots) | Toughness / knockback resistance | Durability multiplier / enchantability |
|---|---|---|---|
| Watch battle | 2 / 5 / 4 / 2 | 1 / 0 | 16 / 12 |
| Sith war | 3 / 6 / 5 / 2 | 1 / 0.05 | 17 / 10 |
| Mandalorian (and Kaast's two pieces) | 3 / 7 / 6 / 3 | 2 / 0.1 | 22 / 8 |
| Spacer's gear | 1 / 4 / 3 / 1 | 0 / 0 | 9 / 16 |

- Structure sets: waystones spacing 20, separation 8, salt 905117; greater tombs spacing 36, separation 14, salt 905123 (`data/oldrepublic/worldgen/structure_set/`).
- Planet constants in code: a sealed plating sheet opens at most 32 blocks (`SealedPlatingBlock.MAX_SHEET`); the Sepulchre sits at (15, -16, 11) from the Proving (`PlanetHubs.SEPULCHRE_OFFSET`); template sizes and buried layers are in the templates (`docs/design/planets.md` section 3).
- Gear constants in code: the electrostaff sweep's shove (0.4); the jet pack's empty click at most every 10 ticks; a guard break is the saber guard's (Slowness II for `saber.guardBreakTicks`).
- Schematics: 65 files in `data/oldrepublic/schematic/` (inputs, output, `craft_ticks`, `xp`, `level`, how each is learned).
- Stack sizes: grenades 4, medpacs 16, kolto injectors and stims 8, the scanner 1.
- Loot tables in `data/oldrepublic/loot_table/`: the planet block drops (`blocks/silverleaf`, `ashroot`, `aurelite_ore`, `deepslate_aurelite_ore`, `duskglass_cluster`, `budding_duskglass`), `chests/greater_tomb`, `chests/wayfarer_cache_m4`, `blocks/landing_beacon` and `blocks/engineering_bench`.
- Client options in `client.json`: `planetAmbience`, `shipCamera`, `hyperspaceOverlay`, `blasterReticle` (all default true).
- Vendor stocks: 9 files in `data/oldrepublic/vendor/` hold every price, faction tier, discipline level, daily limit and the `buys` list (for example the training saber 150, the hyperdrive motivator 400 at the requisition stocks and 480 at the spacer, a trainer's Masterwork schematic 1,200 at discipline level 5). `vendors.priceMultiplier` and `honouredDiscount` apply on top.
- Missions: the 7 Chapter Four story missions and the 12 planet contracts (6 per planet) are data in `data/oldrepublic/mission/`, with their objective, site and counts.
- Loot tables: `entities/{mandalorian_hunter,security_droid,seeker_droid,hollowhorn,ash_hound,torvin_kaast,kaast_droid,camp_mine,hunter_rocket,hunter_net}`, `chests/kaast_stash`, `gameplay/planet_contract` and `blocks/{contract_terminal,marker_beacon}`. A hunter drops a durasteel plate at 30% and, killed by a player, a jet pack at 5%; its Mandalorian schematic piece comes from `chapter_four`, not its loot table.
- Chapter Four constants in code: the hub's discovery XP is paid within 48 blocks of the hub (`ChapterFourRepairs.HUB_REACH`), and an Independent's briefing plays within 12 blocks of Courier's Fall (`FALL_REACH`). A ship's hold pays a vendor within 32 blocks (`Credits.HOLD_RANGE`).
- Force Speed's sprint jump carries 20% further on the client (`SpeedFx.SPRINT_JUMP_BONUS`, 0.2).
- Skill tree nodes (data): the 17 Milestone 4 nodes in `data/oldrepublic/skill_tree/{universal,exploration,jedi,sith,saber}.json` hold their SP cost, level and requirements (spec 9.5). `lift_learned_jedi` and `lift_learned_sith` cost 2 SP at level 13 and require `force_push`.

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
- `force_jump.glideMaxFallSpeed` (0.15) is not read: the client's glide uses a fixed 0.15 (`JumpFx.GLIDE_FALL_SPEED`). `identity.tier1Alignment` and `tier2Alignment` (15 and 40) are not read either: the skill tree files set the same gates with `alignment_min` and `alignment_max`.

### Milestone 3

- Spec 14 writes the Collector's duelist keys as "P1 `lightDamage` ..." and "P3 `lightDamage` ...". The file keys carry the phase: `p1LightDamage`, `p1HeavyDamage`, `p1Guard`, `p1ParryChance`, `p1ParryMinStamina`, `p1WindupTicks`, `p1RecoveryTicks` and the same `p3*` keys (no `p3ParryMinStamina`). The values match the spec.
- `sentinel.quietStepWakeRange` (spec 14) was removed. The `quiet_step` wake range is `skills.quietStepWake` (4), for sentinels as for everything else.
- `collector.spentLiftStaggerTicks` (spec 14) was removed. Lifting the Spent Collector staggers him for `force_lift.bossStaggerTicks` (10).
- W4's stand-in `trooper` copies of `tendTicks`, `escortDespawnTicks`, `borderYield` and `interceptYield` were removed before release; troopers read the `missions` keys. A value left under `trooper` in an old file is ignored.
- `saber.npcDeflectConeDegrees` (70) and `collector.deflectConeDegrees` (70) are read as degrees either side of facing (a 140° arc), the same way the 15° spread and the Reliquary emitter's 12° are read. Set them to 35 for a 70° full arc. See `DECISIONS.md`.
- `equipment.disguiseBreakTicks` and `disguiseBreakRange` are not read: the uniform disguise (9.5) is cut.
- `equipment.trooperDurability`, `robeDurability`, `trooperEnchant`, `robeEnchant` and the speed part of `trooperSpeedPct` are registration defaults (see "Values baked into things").
- Keys marked "(added)" above are not in spec 14: `chapter_three.dreamTicks` and `cairnSenseRange`; `missions.siteRetryTicks`, `siteForwardFallbackAfter`, `boardReach` and `outpostGuidanceRadius`; the W4 `trooper` keys from `boltSpeed` to `encounterEndTicks`; `sentinel.liftHeight`; the `collector` string, lunge, grip-break, Spent lift and control keys.
- The rematch emitter comes only from `collector.emitterChance` and `emitterPity`. The `echo_cache` loot table's own emitter entry is never given.

### Milestone 4

- Spec 6.2 says Light on Your Feet means "never slowed by ash or soul sand", and `docs/design/gear.md` says "soul sand and ash". There is no ash block in 26.2. As built, `armour_m4.spacerSetTerrainEfficiency` (1) adds movement efficiency, which stops soul sand and honey slowing the wearer.
- The registration defaults listed under "Values baked into things" (blaster durability, slows and bash; melee damage, speed, reach and durability; the Mandalorian and spacer speed traits; `crafting.cutterDurability` and `repairKitUses`) need a full restart to change.
- Weapon reference: W4's simulated sustained DPS is within 0.15 of spec 14.2 (pistol 9.56 against about 9.7, rifle 9.65 against about 9.8, repeater 10.16 against about 10.3). The burst figures match. No default was changed for it.
- **Discovery XP on the planets.** `planets.planetDiscoveryXp` and `hubDiscoveryXp` are not used yet: W2 pays the first landing on each planet and the first visit to its hub from its own keys, `chapter_four.planetXp` and `hubXp`, with the same numbers (400 and 200). Since the merge the M3 `Discovery` pays `siteDiscoveryXp` (150) for the first waystone and `greaterTombDiscoveryXp` (300) for the first greater tomb, once per site kind as M3 does, not once per site or per planet. With the other world's landing and hub (600) that is about 1,050 XP against spec 14.1's "about 1,200" for planet discovery.
- `planets.hollowHumTicks` (200) is not read.
- `force_speed.sprintJumpPct` (20) is not read: the client adds a fixed 20% to a sprint jump (see "Values outside `balance.json` (Milestone 4)").
- `vendors.holdPaymentRange` (32) is not read: `Credits.HOLD_RANGE` (32) is the rule, as spec 14.3 notes.
- `docs/design/foes.md` describes the wrist flamer as "a 30 degree, 3-block cone". The code reads `mandalorian.flamerConeDegrees` (30) as a half-angle, so the cone is 60 degrees across, and Kaast's flamer reads the same key. Set it to 15 for a 30-degree cone.
- `docs/design/force_m4.md` section 3 says Force Speed's length is multiplied by the "Hunter's set". It is the spacer's set (Light on Your Feet, `armour_m4.spacerSetSpeedDurationPct`); the Mandalorian set (Hunter's Tenacity) makes Speed cost 10% more (`mandoSetSpeedCostPct`).
- Spec 9.4 asked the Lift nodes `lift_learned_jedi` and `lift_learned_sith` to require `force_push:2`. A requirement names a node, and `force_push` has one rank, so they require `force_push`; the spec now says so.
- The camp ring is two keys with the same value: `chapter_four.campRing` and `kaast.arenaRadius` (both 20). See "One value, one place".
- Keys marked "(added)" above are not in spec 14.3: `planets.ambushRings`, `ambushClearance`, `siteRetryTicks`, `waystoneSenseRange`, `waystoneAmbienceRange`, `platformResetTicks`, `hollowHumTicks`, `riftBleedTicks` and `hubRestoreTicks`; `blasters.spinHoldTicks`; `melee.cleaveMinStrength`, `staffDurability`, `staffArcDamage` and `staffSweepDamage`; `armour_m4.spacerSetTerrainEfficiency` and `controlMin`; `mods.jetBurstGapTicks`; `saber.staffThrowSpeedMult` and `staffThrowWidth`; `chapter_four.seekerHostileRange`, `planetXp`, `hubXp`, `heartstoneHollowhorns`, `heartstoneSentinels`, `trialRing`, `recommendedLevel`, `beaconDistance`, `campRing`, `stashAlloy`, `stashFuel`, `stashCredits`, `hunterSchematicChance`, `retryTicks`, `forwardFallbackAfter` and `forwardFallback`; `vendors.maxPurchase`, `reach`, `hubSparYield` and `hubSparDamage`; and the `kaast` keys marked above (`movementSpeed`, `arenaRadius`, the hop flight, Rattle's scan, the stagger, kneel, combo, block, guard, charge and slam keys, and `chokeBurnStagger`).
