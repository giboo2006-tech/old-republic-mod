# Force feature design: Force Push (Milestone 1) and the Force powers (Milestone 2)

*Since October 2026 the client effects of these powers are drawn by the energy renderer (`client/force/energy/`) or the kinetic kit (`client/force/kinetic/`), as `VFX_SFX_SPEC.md` ("After Milestone 5") describes; the particle types this file names for their effects are unused. The server side, the events, the poses and the sounds are as written here.*

Owner: force worker. Status: **implemented (Phase 2)**, waiting for the first CI compile. Probe results: `api/force.txt`. Section 10 lists where the implementation differs from the Phase 1 plan.

Legend for the API tables: **V** = verified against the 26.2 probe logs or the Fabric API 26.2 sources (where it was seen is noted), **U** = unverified (a probe is queued; the code will carry `// API-CHECK` until confirmed).

## 1. Scope

Force Push end to end, as specified in `docs/MILESTONE_1.md` ("Force Push") and `docs/VFX_SFX_SPEC.md` ("Force Push"):

- Hold `Use Force Power` (R) to charge (0.25 s minimum, 1 s full), release to fire; C2S intents only, the server measures the hold time.
- Server cone targeting with eye-to-target line of sight, branches (Repulse Wave / Focused Blast / Deflecting Pulse), push resistance, stagger with diminishing returns, wall slam, projectile push/reflection, mastery, quest hook.
- Branch choice payload and the "Force" screen (K).
- Effect events and all client VFX/SFX, third-person arm animation for every player, first-person hand offset, caster camera kick, energy-fail feedback.

## 2. Classes

### Common / server (`src/main/java/com/oldrepublic/force/`)

| Class | Role |
|---|---|
| `ForceFeature` | `init()`: registers payload types, receivers, particles, events, the `/oldrepublic push` test subcommand. |
| `ForcePush` | Public API and the cast itself. `ABILITY_ID = "force_push"`. `grant(ServerPlayer)` (adds the ability, commits, chat feedback + `oldrepublic:force_push_learned` effect to the player). `startCharge`, `release`, `cancel` (called from payload handlers / events). `cast(player, charge)` does validation, cost, targeting, effects, mastery, quest hook. |
| `PushBranch` | Enum `NONE("")`, `REPULSE_WAVE("repulse_wave")`, `FOCUSED_BLAST("focused_blast")`, `DEFLECTING_PULSE("deflecting_pulse")`; `id()`, `byId(String)`, `ordinal` used as effect variant. Matches the `ForceData.pushBranch` comment. |
| `PushShape` | Record computed per cast from balance + branch + mastery rank + charge: `halfAngleDeg`, `range`, `knockback`, `cost`, `cooldownTicks`, `spherical`. One place for the numbers so the server, the screen sync and tests agree. |
| `PushBalance` | `BalanceConfig.section("force_push", ...)`. All tunables (see section 5). |
| `PushCharges` | Server-side per-player charge state (`Map<UUID, Charge>` with start game tick). Not persisted on purpose: a charge never survives death, disconnect, dimension change or restart. Ticked from `ServerTickEvents.END_SERVER_TICK` to cancel on item use, spectator, death, over-hold timeout. |
| `PushTargeting` | Finds targets: entities in an AABB around the eyes, filtered by cone/sphere, range and line of sight; classifies living / item / projectile. |
| `PushEffects` | Applies impulses, stagger (with diminishing returns for `#oldrepublic:push_resistant`), projectile redirect/reflection, and tracks pushed targets for the Focused Blast wall slam (short server-side list ticked each server tick). |
| `BranchChoice` | Server validation and application of a branch choice (level >= 3, has push, 1 skill point first time, 3 to re-choose, cannot pick the current one). |
| `ForceTags` | `PUSH_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, oldrepublic:push_resistant)`. |
| `ForceParticles` | Registers `SimpleParticleType`s: `air_ripple`, `push_streak`, `push_impact`, `gather_dust`, `fail_mote`. `AIR_RIPPLE` is public so the quest worker can reuse it for the Awakening burst. |
| `ForceEffects` | Effect id constants: `force_push_charge`, `force_push`, `force_push_impact`, `force_push_slam`, `force_push_learned`. |
| `network/PushInputPayload` | C2S `record PushInputPayload(Action action)` with `Action { START, RELEASE, CANCEL }` (one varint). |
| `network/ChooseBranchPayload` | C2S `record ChooseBranchPayload(String branchId)` (`ByteBufCodecs.STRING_UTF8`, bounded). |
| `network/PushInfoPayload` | S2C on every join: the exact numbers per branch (half-angle, range, knockback multiplier, cooldown multiplier, cost min/max, stagger ticks, slam damage) so the Force screen tooltips show the server's live balance values, never client guesses. |

### Client (`src/client/java/com/oldrepublic/client/force/`)

| Class | Role |
|---|---|
| `ForceClient` | `init()`: key mappings, tick handler, effect handlers, particle providers, payload receiver, disconnect cleanup. |
| `ForceKeys` | `USE_POWER` (R, `key.oldrepublic.use_force_power`) and `FORCE_SCREEN` (K, `key.oldrepublic.force_screen`) in category `oldrepublic:force`. |
| `PushInput` | Local hold/release state machine. Sends START on press (after client-side pre-checks from the synced attachment, so an obviously invalid press shows a hint instead of a packet), RELEASE on release, CANCEL when a screen opens, the player starts using an item, dies, or changes level. Predicts the local charge visuals immediately. |
| `PushAnimations` | Per-entity client animation state: `chargeStartTick`, `releaseTick`, `branch`. Feeds the render state, the arm pose, the first-person offset and the remote charge visuals. Cleared on level change/disconnect. |
| `PushFx` | Builds all particles and sounds from effect events (charge tick, release, impact, slam, aftermath, energy fail). Every spawn goes through `Vfx.inRange` and `Vfx.count`. |
| `ChargeSoundInstance` | `AbstractTickableSoundInstance` following the charging entity; volume and pitch rise with charge; `stop()` when the entity is no longer charging, removed, dead, or on a different level. |
| `ForceScreen` | The Force screen (level, XP, Resonance, skill points, energy, Push mastery rank, three branch buttons with exact numbers in tooltips). |
| `ClientPushInfo` | Holds the last `PushInfoPayload`. |
| `particle/AirRippleParticle`, `PushStreakParticle`, `PushImpactParticle`, `GatherDustParticle`, `FailMoteParticle` | `SingleQuadParticle` subclasses + providers. |

### Mixins (`com.oldrepublic.mixin.force`, config `oldrepublic-force.mixins.json`, all `"client"`)

| Mixin | Target | What |
|---|---|---|
| `AvatarRendererMixin` | `AvatarRenderer#extractRenderState(Avatar, AvatarRenderState, float)` at `RETURN` | Writes `PushPose` (draw 0..1, thrust 0..1, arm) into the render state with `FabricRenderState#setData(RenderStateDataKey, ...)`. |
| `HumanoidModelMixin` | `HumanoidModel#setupAnim(HumanoidRenderState)` at `TAIL` | If the state carries a `PushPose`, rotates the push arm (draw back: arm pulled back and bent toward the shoulder; thrust: arm straight forward, palm open). Works for any humanoid whose renderer sets the key (the Shadow Echo can reuse it: world worker sets the same key). PlayerModel copies arm to sleeve after super, so sleeves follow (to confirm with `body:PlayerModel#setupAnim`). |
| `ItemInHandRendererMixin` | `ItemInHandRenderer#submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` `HEAD` push+translate / `RETURN` pop | Pulls both first-person hands back while charging and jabs them forward on release (26.2 has no per-arm public hook; the per-arm method is private and was not probed). |
| `CameraMixin` | `Camera#alignWithEntity(float)` (private) at `TAIL` | `setRotation(yRot() + CameraShake.yaw(pt), xRot() + CameraShake.pitch(pt))` when non-zero. `update` calls `alignWithEntity` and then builds the view matrix and frustum, so the shake reaches both. Applies the shared `CameraShake` for every feature. |

No common mixins are needed.

## 3. Payloads and effect events

### C2S
- `oldrepublic:push_input` `PushInputPayload(Action)`. Server handler runs on the server thread (Fabric `PlayPayloadHandler`, context `player()`).
- `oldrepublic:choose_branch` `ChooseBranchPayload(String)`.

### S2C
- `oldrepublic:push_info` `PushInfoPayload` (numbers for the screen).
- Foundation `EffectEventPayload` for everything visual:

| Effect id | Sent with | pos / dir / entityId / intensity / variant |
|---|---|---|
| `oldrepublic:force_push_charge` | `EffectEvents.toTracking(caster)` | caster eye / look / caster / 0 / `0` start, `1` stop (cancel or refused) |
| `oldrepublic:force_push` (release) | `toTracking(caster)` | caster eye / look / caster / charge 0..1 / `branch | rangeQuarterBlocks << 2 | halfAngleDeg << 10` so clients draw the server's real cone |
| `oldrepublic:force_push_impact` | `EffectEvents.around(level, targetPos)` | point on the target facing the caster / push dir / target / impulse strength / hit index in this cast (client plays the impact sound only for index < 3) |
| `oldrepublic:force_push_slam` | `around` | contact point / wall normal / target / damage / 0 |
| `oldrepublic:force_push_learned` | `toPlayer` | player / – / player / 1 / 0 (soft ripple on the hand) |
| `oldrepublic:energy_fail` (foundation, client handler owned here) | sent by `Progression.trySpendEnergy` | eye / look / player | – |

The caster's own client predicts its charge visuals; its echo of `force_push_charge` start is ignored for the local player, but a `stop` echo cancels the local prediction (server refused or cancelled).

## 4. Server flow

**START** (payload): ignore if already charging. Check: alive, not spectator, `ForceData.isAwakened()`, `hasAbility("force_push")`, `!Progression.isOnCooldown(player, "force_push")`, not using an item. Energy pre-check: if `!creative && energy < minimum cost` call `Progression.trySpendEnergy(player, cost)` (fails and plays the foundation fail feedback) and stop. Otherwise store `Charge(startTick = server tick count)` and send `force_push_charge` start to trackers. On a refused START send the overlay reason to the player (cooldown / not learned) and a `stop` event to the player so a predicted charge ends.

**Server tick** (`PushCharges.tick`): for each charge: cancel (stop event, no cost) if the player is dead, removed, spectator, using an item, or held longer than `maxHoldTicks` (default 100 = 5 s; avoids an endless loop sound).

**RELEASE** (payload): remove the charge; `held = now - startTick`. If `held < minChargeTicks` (5): silent cancel, no cost (stop event). `charge = clamp((held - min)/(full - min), 0, 1)`. Re-validate (alive, awakened, ability, cooldown). Build `PushShape`. `Progression.trySpendEnergy(player, cost)`; on false stop (fail feedback already sent). Set cooldown `round(baseCooldown * branchCooldownMult)` ticks. Run targeting, apply effects, send events, mastery, quest hook.

**CANCEL** (payload) / **death** (`ServerLivingEntityEvents.AFTER_DEATH` for players) / **disconnect** (`ServerPlayConnectionEvents.DISCONNECT`) / **dimension change** (`ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`): remove the charge, send stop (where the player is still connected).

**Targeting** (`PushTargeting`): `level.getEntities(player, eyeAABB.inflate(range), predicate)`; for each candidate the aim point is the closest point of its bounding box to the eye ray, fallback centre. Keep if distance <= range and (spherical or `dot(look, toTarget.normalize()) >= cos(halfAngle)`). Line of sight: `level.clip(new ClipContext(eye, aimPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))` must be a MISS or end at/after the target; a second ray to the target's eye height is tried before rejecting so a mob behind a slab is still hit. Classes:
- `LivingEntity` (not the caster, alive, not spectator, not invulnerable-creative players). Players only when `balance.pvpEffects && serverLevel.isPvpAllowed()` and not allied (`isAlliedTo`).
- `ItemEntity` (and other loose entities such as XP orbs via a small allowlist: item entities only in M1).
- `Projectile`.

**Effects** (`PushEffects`):
- Living: `falloff = 1 - 0.35 * dist/range`; `strength = shape.knockback * falloff * (resistant ? 0.4 : 1) * (1 - 0.6 * knockbackResistanceAttr)`; velocity add `dir * strength + up(0.18 + 0.12 * charge)` (Deflecting Pulse uses the radial direction); `entity.setDeltaMovement(...)`, `entity.hurtMarked = true` (forces the velocity packet, needed for players), `resetFallDistance` not applied (falls you cause are fair). Hostile mobs get `setLastHurtByMob(player)` so they react. Repulse Wave: `MobEffects.SLOWNESS` (amplifier 2, `staggerTicks` 30) with diminishing returns for push-resistant targets (per-entity UUID record: each stagger within 10 s halves duration; third within the window is ignored).
- Focused Blast: target registered in the slam tracker for 12 ticks with the push direction and caster; each server tick, if `horizontalCollision` and the speed before collision was > `slamMinSpeed`, deal `slamDamageBase + slamDamagePerCharge * charge` via `damageSources().playerAttack(player)` (kill credit, XP via foundation), send `force_push_slam`, and drop it from the tracker.
- Items: impulse `dir * strength * itemFactor`.
- Projectiles: Deflecting Pulse: `DeflectableProjectile` → `redirect(velocity toward its shooter's eyes (or reversed) at the same speed, player, false)`; other projectiles → `Projectile#deflect(ProjectileDeflection.REVERSE, player, owner ref, true)` (U) or set velocity outward + `setOwner(player)`. Other branches: projectiles in the cone are shoved along the push direction (deflectables via `redirect`, others `setDeltaMovement` + `hurtMarked` + `setOwner(player)`).
- Never touches blocks.

**After the cast**: `hostileHits` = distinct living targets that are `Enemy`; `Progression.addMastery(player, "force_push", hostileHits)` (1 per target per cast, hostile living only, per spec anti-grind); `QuestHooks.onAbilityUsed(player, "force_push", hostileHits)`. First successful push: `Progression.grantResonanceMilestone(player, "first_push", balance.firstPushResonance)` (one-shot flag; see "Coordination").

**Mastery scaling**: rank r (0-3 from `Progression.masteryRank`): range and radius `x (1 + 0.04 r)`, cost `x (1 - 0.05 r)`. Never changes the branch.

**Branch choice** (`BranchChoice.apply`): reject unless awakened, has push, `level >= branchUnlockLevel (3)`, id valid and not `NONE`, differs from current; cost = current empty ? 1 : 3 skill points; spend, set `pushBranch`, commit, message `message.oldrepublic.force.branch_chosen`. Failures send a specific overlay message.

**`ForcePush.grant(ServerPlayer)`**: idempotent; adds `force_push`, commits, chat message with the bound key name hint, `force_push_learned` effect to the player. Used by the quest worker after meditation.

**Test command**: `/oldrepublic push grant|branch <id>|reset_cooldown [player]` (operator) via `OldRepublicCommands.addSubcommand`.

## 5. Balance (`balance.json` section `force_push`)

| Field | Default | Spec |
|---|---|---|
| `minChargeTicks` / `fullChargeTicks` / `maxHoldTicks` | 5 / 20 / 100 | 0.25 s, 1 s; hold cap |
| `baseHalfAngle` | 35 | 70 degree cone |
| `baseRange` | 6 | |
| `knockbackMin` / `knockbackMax` | 1.1 / 1.8 | |
| `costMin` / `costMax` | 18 / 28 | |
| `cooldownTicks` | 70 | 3.5 s |
| `repulseHalfAngle` / `repulseRange` / `repulseKnockback` / `repulseStaggerTicks` / `repulseStaggerAmplifier` | 65 / 7 / 0.8 / 30 / 2 | |
| `focusedHalfAngle` / `focusedRange` / `focusedKnockback` / `slamDamageBase` / `slamDamagePerCharge` / `slamMinSpeed` / `slamWindowTicks` | 12.5 / 12 / 1.8 / 4 / 4 / 0.35 / 12 | |
| `deflectRadius` / `deflectKnockback` / `deflectCooldownMult` | 4 / 0.6 / 0.6 | |
| `resistantKnockbackMult` / `staggerDrWindowTicks` | 0.4 / 200 | |
| `masteryRangePerRank` / `masteryCostPerRank` | 0.04 / 0.05 | |
| `itemKnockbackMult` / `projectilePushSpeed` | 1.0 / 1.4 | |
| `pvpEffects` | false | "players only if PvP effects are enabled" (also requires the server's PvP setting) |
| `branchUnlockLevel` / `branchFirstCost` / `branchRechooseCost` | 3 / 1 / 3 | |
| `firstPushResonance` | 250 | milestone |

## 6. Client rendering approach

**Input** (`ClientTickEvents.END_CLIENT_TICK`): `wasDown`/`isDown` edge detection on `USE_POWER`. Release while `client.screen != null` (vanilla releases all keys when a screen opens) is sent as CANCEL, never as a fire. `FORCE_SCREEN` `consumeClick()` opens `ForceScreen` when awakened, else `player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"))`.

**Charge visuals** (each client tick for every charging entity in `PushAnimations`, culled by `Vfx.inRange`): hand position from `yBodyRot`, eye height and arm side. 1-3 `gather_dust` motes per tick spawned 1.5-3 blocks away near the ground, coloured from the block underfoot (`BlockState#getMapColor(level,pos).col`) and flying toward the hand; occasional vanilla `ParticleTypes.BLOCK` crumbs of the ground block lifted slightly; 1 small low-alpha `air_ripple` at the palm every 3 ticks, growing with charge. `ChargeSoundInstance` for the loop.

**Release**: arm thrust (animation state), `FORCE_PUSH_RELEASE` via `ClientLevel#playLocalSound(x,y,z, event, SoundSource.PLAYERS, vol, 1 + rand(±0.06), false)`; 3-5 `air_ripple` shells spawned along the cone axis with velocity 0.9 blocks/tick (18 blocks/s), each shell a ring of 6-10 ripple quads at radius `distance * tan(halfAngle)` for cones or a full sphere sample for Deflecting Pulse; `push_streak` particles along the cone edges moving outward (Focused Blast: tighter, faster, brighter streaks; Repulse: wide and low, biased toward the ground). Ground dust kicked along the cone floor. Caster: `CameraShake.add(0.12 + 0.12 * charge)`.

**Impact**: `push_impact` burst on the target side facing the caster, vanilla `ParticleTypes.BLOCK` dust of the block under the target, `FORCE_PUSH_IMPACT` only when variant (hit index) < 3. **Slam**: extra `BLOCK` debris from the wall block at the contact point, `push_impact` larger, `FORCE_PUSH_SLAM`. **Aftermath**: low-gravity `gather_dust` motes that settle over 20-30 ticks; for leaves/grass blocks sampled inside the cone (`BlockTags.LEAVES`, short grass, ferns) a few `BLOCK` particles of that state.

**Energy fail** (`oldrepublic:energy_fail`): 2-3 tiny `air_ripple` flickers at the hand that fade within 4 ticks (steady glow variant when `Vfx.flashing()` is off: no rapid alpha toggling), 2 grey `fail_mote` falling, `FORCE_FAIL` at the hand.

**Particles**: `SingleQuadParticle` subclasses, translucent layer, providers registered with `ParticleProviderRegistry.getInstance().register(type, FabricSpriteSet -> provider)`; sprites from `assets/oldrepublic/particles/<id>.json` listing `oldrepublic:<id>_<n>` textures in `textures/particle/`. `push_streak` overrides `extract(...)` to build a camera-facing quad rotated so its long axis follows the velocity (`extractRotatedQuad(state, quaternion, x, y, z, partialTick)` with our own quaternion). Colours: colourless/pale, alpha 0.12-0.35 for ripples; no glow, no rings on the ground.

**Arm animation**: render state key `PushPose(float draw, float thrust, boolean leftArm)`, populated in `AvatarRendererMixin` from `PushAnimations` (partial-tick interpolated). Push arm = the empty hand (off hand if the main hand holds something, else main arm). In `HumanoidModelMixin` after vanilla setup: draw → `arm.xRot = lerp(xRot, -0.35f, d)`, `arm.yRot = ±0.45f * d`, `arm.zRot` slight; thrust → `arm.xRot = -π/2 + head.xRot` (palm forward along the look), `yRot = head.yRot`, eased out over 6 ticks.

**First-person**: `ItemInHandRendererMixin` translates the push-arm PoseStack by `(0, -0.08 d, 0.12 d)` while charging and `(0, 0.02 t, -0.25 t)` on thrust, undone at RETURN.

**Camera**: `CameraMixin` (see table).

## 7. API verification

| API | Use | Status |
|---|---|---|
| `KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, `isDown()`, `consumeClick()` | keys | V (client.txt) |
| `KeyMappingHelper.registerKeyMapping(KeyMapping)` | keys | V (fabricapi.txt) |
| `InputConstants.Type.KEYSYM`, `GLFW.GLFW_KEY_R/K` | default keys | U (InputConstants probe; GLFW is LWJGL) |
| `PayloadTypeRegistry.serverboundPlay()/clientboundPlay().register(type, codec)` | payloads | V (Fabric source) |
| `ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.player())` | C2S | V (Fabric source; `Context#player()` U) |
| `ClientPlayNetworking.send(payload)`, `registerGlobalReceiver` | C2S/S2C | V (Fabric source) |
| `ServerPlayConnectionEvents.DISCONNECT`, `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`, `ServerLivingEntityEvents.AFTER_DEATH`, `ServerPlayerEvents.JOIN`, `ServerTickEvents.END_SERVER_TICK` | lifecycle | V (Fabric source, foundation usage) |
| `ClientTickEvents.END_CLIENT_TICK`, `ClientPlayConnectionEvents.DISCONNECT` | client lifecycle | V (fabricapi.txt) |
| `Entity#getEyePosition()`, `getLookAngle()`, `isSpectator()`, `isUsingItem()`, `stopUsingItem()`, `hurtMarked`, `horizontalCollision`, `push(Vec3)`, `onGround()`, `getBlockStateOn()`, `getOnPos()` | targeting/effects | V (core.txt) |
| `Entity#setDeltaMovement(Vec3)`, `getDeltaMovement()`, `getBoundingBox()`, `isRemoved()`, `level()` | effects | U (Entity dump truncated) |
| `Level#getEntities(Entity, AABB, Predicate)` | targeting | V (core.txt) |
| `BlockGetter#clip(ClipContext)`, `ClipContext(Vec3, Vec3, Block, Fluid, Entity)` | LOS | ctor V, `clip` U |
| `LivingEntity#addEffect(MobEffectInstance, Entity)`, `setLastHurtByMob`, `yBodyRot`, `getMainArm()`, `getItemHeldByArm(HumanoidArm)` | stagger, arm | V (core.txt) |
| `MobEffects.SLOWNESS` (Holder), `MobEffectInstance(Holder, int, int)` | stagger | U |
| `DamageSources#playerAttack(Player)`, `Entity#damageSources()`, `hurtServer(ServerLevel, DamageSource, float)` | slam | V (core.txt) |
| `ServerLevel#isPvpAllowed()` | PvP gate | V (core.txt) |
| `Projectile#setOwner(Entity)`, `getOwner()`, `deflect(ProjectileDeflection, Entity, EntityReference, boolean)` | projectiles | V (core.txt); `ProjectileDeflection.REVERSE`, `EntityReference.of` U |
| `AbstractHurtingProjectile` acceleration field | fireballs | U |
| `TagKey.create(Registries.ENTITY_TYPE, id)`, `EntityType#is(TagKey)` | push_resistant | TagKey V, `EntityType#is` U |
| Tag file path `data/oldrepublic/tags/entity_type/push_resistant.json` | tag | U (resfind probe confirms singular folder) |
| `Progression.*`, `EffectEvents.*`, `QuestHooks.onAbilityUsed`, `BalanceConfig.section`, `OldRepublicCommands.addSubcommand` | foundation | V (source) |
| `FabricParticleTypes.simple()`, `Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type)` | particles | V (Fabric source + testmod) |
| `ParticleProviderRegistry.getInstance().register(type, PendingParticleProvider)` with `FabricSpriteSet#get(RandomSource)` | providers | V (Fabric testmod) |
| `ParticleProvider#createParticle(T, ClientLevel, x,y,z, dx,dy,dz, RandomSource)` | providers | V (client.txt) |
| `SingleQuadParticle(ClientLevel, x,y,z, dx,dy,dz, TextureAtlasSprite)`, `getLayer()`, fields `quadSize/rCol/gCol/bCol/alpha/roll`, `setSpriteFromAge(SpriteSet)`, `extract`, `extractRotatedQuad(state, Quaternionf, x, y, z, partial)` | particles | V (client.txt) |
| `SingleQuadParticle.Layer` constants (`TRANSLUCENT`?) | particles | U |
| `Particle` fields `xd/yd/zd/age/lifetime/gravity/friction/hasPhysics`, `getLightCoords(float)` | particles | V (client.txt) |
| `ParticleEngine#createParticle(ParticleOptions, x,y,z, dx,dy,dz)` returning `Particle`, `Minecraft#particleEngine` | tinted dust | field V, method U |
| `ClientLevel#addParticle(...)`, `playLocalSound(x,y,z, SoundEvent, SoundSource, float, float, boolean)`, `getEntity(int)` | fx | V (client.txt) |
| `ParticleTypes.BLOCK`, `BlockParticleOption(ParticleType, BlockState)` | debris | V (core.txt) |
| `BlockState#getMapColor(BlockGetter, BlockPos).col`, `BlockTags.LEAVES` | colours | U |
| `AbstractTickableSoundInstance(SoundEvent, SoundSource, RandomSource)`, `stop()`, fields `x/y/z/volume/pitch/looping/delay/attenuation` | charge loop | V (client.txt) |
| `Minecraft#getSoundManager().play(SoundInstance)`, `SoundManager#stop(SoundInstance)` | sound | V (client.txt) |
| `SoundInstance.Attenuation.LINEAR` | sound | U |
| `Screen(Component)`, `init()`, `extractRenderState(GuiGraphicsExtractor, int, int, float)`, `addRenderableWidget`, `tick()`, `rebuildWidgets()`, `isPauseScreen()` | screen | V (client.txt) |
| `Button.builder(...).bounds(...).tooltip(Tooltip).build()` | screen | V (client.txt) |
| `Tooltip.create(Component)`, `AbstractWidget#active` | screen | U |
| `GuiGraphicsExtractor#fill/text/centeredText/textWithWordWrap` | screen | V (client.txt) |
| `Minecraft#setScreen(Screen)` | screen | U (Minecraft dump truncated; `setScreenAndShow` seen) |
| `Player#sendOverlayMessage(Component)` | hints | V (client.txt/core.txt) |
| `AvatarRenderer#extractRenderState(Avatar, AvatarRenderState, float)` mixin target | arm pose | V (Fabric rendering testmod `AvatarRendererMixin`) |
| `FabricRenderState#setData/getData`, `RenderStateDataKey.create(Supplier<String>)` | arm pose | V (fabricapi.txt) |
| `HumanoidModel#setupAnim(HumanoidRenderState)`, fields `rightArm/leftArm/head` | arm pose | V (client.txt) |
| `ModelPart` fields `xRot/yRot/zRot` | arm pose | U |
| `ArmedEntityRenderState#mainArm` | arm side | U |
| `PlayerModel#setupAnim` copies arms to sleeves after super | arm pose | U |
| `ItemInHandRenderer#renderArmWithItem` signature (PoseStack param) | first person | U |
| `Camera#update(DeltaTracker)`, `setRotation(float, float)` (protected), `yRot()`, `xRot()`, `isDetached()` | camera | V (client.txt); behaviour inside `update` U |

## 8. Assets (detailed list in `docs/assets/force.md`)

Generated by `tools/assets/gen_textures_force.py` (PIL, deterministic seed): `textures/particle/air_ripple_0..3.png` (32x32 soft pale refraction rim, very low alpha centre), `push_streak_0..2.png` (16x16 horizontal tapered streak), `push_impact_0..3.png` (32x32 radial dust/pressure puff), `gather_dust_0..2.png` (8x8 greyscale motes, tinted at runtime), `fail_mote_0..1.png` (8x8 dull grey). Particle JSONs in `particles/<id>.json`. All placeholders, original.

## 9. Coordination / changes needed outside this feature

- **Quest worker**: call `ForcePush.grant(player)` after meditation. Do not grant the `first_push` resonance milestone (Force grants it on the first successful push, flag `resonance:first_push`), or tell me to drop it and you do it.
- **Quest worker**: may reuse `ForceParticles.AIR_RIPPLE` for the Awakening burst (common class, `SimpleParticleType`).
- **World/enemy workers**: add `oldrepublic:shadow_echo` (and any bosses) to `data/oldrepublic/tags/entity_type/push_resistant.json`; I will ship the tag with `shadow_echo` already listed as `{"id": "oldrepublic:shadow_echo", "required": false}` so it never fails to load. For the Shadow Echo's mirrored push arm, the world worker can set `PushPose.KEY` on its render state (client API I expose: `com.oldrepublic.client.force.PushPose`).
- **Lead**: lang keys `key.oldrepublic.use_force_power` and `key.oldrepublic.force_screen` live in my `force.json` part. If the saber worker also registers `KeyMapping.Category` `oldrepublic:force`, only one of us may call `Category.register` (it probably rejects duplicates); I propose the force feature owns the registration and exposes `ForceKeys.CATEGORY` for the saber worker.
- **Foundation**: `CameraShake.yaw/pitch` return a new random value per call; with the camera mixin calling them once per frame the shake is per-frame jitter. Acceptable; noted.

## 10. Implementation notes (Phase 2)

- Screens open through `Minecraft#gui.setScreen(Screen)` and the current screen is `gui.screen()` (26.2 moved them to `Gui`).
- Disconnect handling uses `ServerPlayerEvents.LEAVE` (gives the player directly).
- Player lookups by UUID iterate `server.getPlayerList().getPlayers()` (the only verified accessor).
- Particles spawn through `ParticleEngine#createParticle`, which returns the particle so it can be tinted and sized after creation (`ForceParticle` fluent setters). Other features using `level.addParticle(ForceParticles.AIR_RIPPLE, ...)` get sensible defaults (faint shell, 10 ticks).
- `PushStreakParticle` overrides `extract` and builds its own camera-facing, velocity-aligned quaternion for `extractRotatedQuad`.
- `PlayerModel#setupAnim` calls `HumanoidModel#setupAnim` and the sleeves are children of the arms, so one mixin on `HumanoidModel` animates players (and any humanoid whose renderer sets `PushPose.KEY`).
- The client keeps its own visual charge (20 ticks = full); the server's measured charge decides the actual push.
- Tests: `src/gametest/java/com/oldrepublic/gametest/ForceGameTests.java` (shapes, mastery, charge thresholds, cone geometry, release packing, branch choice rules).

---

# Milestone 2: Push, Pull, Jump, Heal and Lightning on the ability framework

Spec: `docs/MILESTONE_2.md` sections 8 and 13 (balance) and 14.2 (W4). Sections 1 to 10 above still describe Push; what changed for Push is in 11.1.

## 11. Overview and classes

### 11.1 Push migration
- `PushAbility` (a `ForceAbility`, `HOLD_RELEASE`) delegates to `ForcePush.onInput(player, AbilityAction)`. `PushInputPayload` and the client `PushInput` are deleted; the slot keys are the skills feature's, and prediction lives in `ForceAbilityHooks` (11.4).
- The branch comes from the active `push_*` skill node (`ForcePush.branch`), falling back to the `ForceData.pushBranch` mirror (still set by `/oldrepublic push branch`).
- Costs go through `ForcePowers.costMultiplier` (`AbilityCosts` × `ForceReactions`), cooldowns through `AbilityCosts.cooldownMultiplier`, mastery through `AbilityCosts.masteryAllowed`.
- Hits call `ForceReactions.onPushHit` (push clash / guard ×0.5 and drain 20, implemented by the saber/enemy feature) and `QuestHooks.onAbilityHit`. Focused Blast wall slams deal `oldrepublic:force_impact`.
- Repulse stagger uses the shared diminishing returns in `ControlEffects`.

### 11.2 Common / server (`com.oldrepublic.force`)
| Class | Role |
|---|---|
| `ForceFeature` | Registers the five abilities, the server tick, cleanup (leave, death, dimension change, server stop), `AFTER_DAMAGE` (heal and lightning interruption, PvP combat record), the no-melee rule while healing, and the commands. |
| `ForcePowers` | Shared rules: knows / hasNode (skill node or the `ForceData.abilities` test fallback), start checks, cost / cooldown / mastery multipliers, the PvP rule `canAffect`, resistance, knockback weighting. |
| `ForceReactions` | Hook interface other features implement: guards (`isGuarding`, `drainGuard`, `restoreGuard`), push clash (`onPushHit`), wind-up interruption, area damage multiplier (captives), cost multiplier (Ataru jump). Results combine (OR, product, broadcast). |
| (handlers) | The saber feature's `SaberForceReactions` (guards with the form's arc, drain/restore, push on guard, NPC wind-ups, Ataru jump cost) and the enemy feature's `EnemyForceReactions` (push clash) implement `ForceReactions`; they must also honour `ForceStatus.isGuardSuppressed`. |
| `ForceStatus` | `isDisarmed(entity)` (poachers must not fire) and `isGuardSuppressed(entity)` (no guard), timed by game time. |
| `ControlEffects` | Stagger (Slowness + wind-up interrupt) with push-resistant diminishing returns (2nd half, 3rd+ none within 200 ticks). |
| `ForceDamage` | Damage type keys `force_lightning`, `force_impact`. Both are in W1's tag `#oldrepublic:force_harm`. |
| `pull/ForcePull`, `PullTargeting`, `PullShape`, `PullBalance` | Force Pull. |
| `jump/ForceJump`, `JumpShape`, `JumpBalance` | Force Jump. |
| `heal/ForceHeal`, `HealShape`, `HealBalance` | Force Heal. |
| `lightning/ForceLightning`, `LightningShape`, `LightningBalance` | Force Lightning. |
| `mixin/force/LivingEntityForceMixin` (common) | `hurtServer` HEAD: Reeling Pull's +30% next saber hit. `causeFallDamage` HEAD: Force Jump fall allowance. |

### 11.3 Server flows
- **Pull (TAP):** the press picks the target (crosshair ray, else nearest the axis within 8°, line of sight) or, with no living target, the loose items within 2.5 blocks of the ray end. No target: an overlay, nothing spent. A 3-tick reach is visible (`force_pull`), then the cost and cooldown are paid and the target gets `velocity()` with `hurtMarked`. For 20 ticks a dragged target within 2.5 blocks has its horizontal speed ×0.3 (`braked`), and the first such tick is the arrival (`force_pull_hit` ARRIVED; Reeling: stagger 20 and the empowered next saber hit). Disarming: `ForceStatus.disarm` 120 (DR), a `SaberDuelist` loses 40 stamina and cannot guard 40, a player's main-hand item gets a 40-tick cooldown and stops being used.
- **Jump (HOLD_RELEASE):** start needs ground (or 3 ticks of coyote time, tracked per player each tick). Release below 4 ticks of gather cancels at no cost; held past 60 cancels. The leap spends the energy, sets the cooldown and the velocity (`hurtMarked`). Fall protection: the first landing within 80 ticks ignores up to (apex + 4) blocks of fall (`adjustFall`). Landing: `force_jump_land` (surface group), Slowness II for 6 ticks unless Ataru, Crushing Descent shockwave (≥ 3 blocks fallen). Soaring Leap glide: the server reads the held Jump key from `getLastClientInput()`, charges 0.4 FE per tick (paid every 5 ticks), allows 30 ticks once per leap and sends `force_jump_glide` start/stop; the client caps its own fall speed (11.4).
- **Heal (CHANNEL):** refused while sprinting or using an item. Start cost (×1.25 Shared Light), movement ×0.5 (transient attribute modifier), heals every 5 ticks after 6 ticks of start-up for up to 60, paying 3 FE per heal. Ends on release, max, a hit ≥ 4 after armour, a stagger (Slowness ≥ III), sprinting, using an item or running out of energy. Cooldown 200, or 100 if interrupted (or nothing healed). Serene Ward on an end after ≥ 20 ticks. Shared Light heals other players not fought in the last 200 ticks, villagers, wandering traders, tamed animals and Frontier Settlers within 6 at 60%, reporting `HELPED_OTHER` once per ally per channel.
- **Lightning (CHANNEL):** 8-tick charge, then targets every 4 ticks and a damage tick every 5 (paying 0.75 × 5 FE per damage tick, ×1.2 Chain). Guarded targets take ×0.2, lose 3 stamina and are not shocked; others get Slowness I 10 (DR for resistant) and their wind-up interrupted. The caster burns 0.5 per damage tick after 60 arc ticks (`force_lightning` with no attacker). Interrupted by a hit ≥ 4. Cooldown 160, 200 after a full 80-tick channel, none if the arcs never started.

### 11.4 Client (`com.oldrepublic.client.force`)
| Class | Role |
|---|---|
| `ForceAbilityHooks` | `ClientAbilityHook` per power: precheck (awake, learned, cooldown) and the own prediction (Push charge, Pull reach, Jump gather, Heal channel, Lightning charge). A prediction is confirmed by the server's own effect event or dropped after 15 ticks, and at once on the own `energy_fail` event. |
| `PowerAnimations`, `PowerPose` | One pose per entity for Pull / Jump gather / launch / glide / Heal / Lightning, with blend in and out, watchdog ends, predicted state, and the attached loop sound. `PowerPose.KEY` on the render state (any humanoid renderer can set it); jump gather sets `isCrouching`. |
| `PowerLoopSound` | Looped power sound that stops when its pose ends or the entity dies, is removed or leaves the level. |
| `PullFx`, `JumpFx`, `HealFx`, `LightningFx` | Effect handlers and per-tick visuals. `JumpFx` also applies the Soaring Leap glide to the own player (fall speed capped at 0.15 while Jump is held past the apex, at most 30 ticks, once, stopped by the server's glide stop event). |
| `ForceFxUtil` | Shared helpers (hand / chest positions, ground lookup, dust colour, spawn, sound). |
| particles | `LightningSegmentParticle` (full-bright quad rolled along a stored segment direction), `LightningSparkParticle`, `WardGlintParticle`; `pull_streak` reuses `PushStreakParticle`, `jump_trail` reuses `DustMoteParticle`. `ForceParticle#fullBright()` added. |

## 12. Effect events (all clientbound through `EffectEvents`)
| Id | Sent | Fields |
|---|---|---|
| `force_pull` | tracking caster | entity caster, pos hand, dir look, variant `PullShape` ordinal |
| `force_pull_hit` | around | variant 0 LAUNCHED (entity target, dir to caster, intensity speed), 1 ARRIVED (entity target, pos caster feet), 2 ITEMS (entity caster, pos item point, dir to hand, intensity count) |
| `force_pull_disarm` | around | entity target, pos weapon hand |
| `force_jump_gather` | tracking | variant 0 start / 1 stop |
| `force_jump_launch` | tracking | pos take-off, dir velocity, intensity charge, variant `JumpShape` ordinal |
| `force_jump_land` | around | intensity blocks fallen, variant surface (0 soft, 1 stone, 2 sand, 3 snow; `ForceEffects.surface`) |
| `force_jump_slam` | around | pos centre, intensity damage |
| `force_jump_glide` | tracking | variant 0 start / 1 stop (new id, not in the spec list) |
| `force_heal` | tracking | variant 0 start, 1 end, 2 interrupted; intensity `HealShape` ordinal |
| `force_heal_ally` | around | entity ally, variant `packCaster(caster, 0 start / 1 stop)` |
| `force_heal_ward` | tracking | intensity duration in ticks |
| `force_lightning` | tracking | low 2 bits phase 0 charge / 1 arcs / 2 stop (spec numbers), shape ordinal above; intensity mastery rank |
| `force_lightning_target` | around | entity target, pos chest, variant `packCaster(caster, slot)`, intensity 1 on / 0 off; only on changes |
| `force_lightning_hit` | tracking | pos primary contact or struck block, intensity hits (0 = block), variant bit mask of guarded slots |

About 4 lightning packets per second per caster (one hit every 5 ticks, target changes at most every 4).

## 13. VFX and SFX treatment
- **Pull:** contracting ripple at the palm and short streaks along the reach; streaks peeling off the target toward the caster, dust and block bits kicked from under a dragged target; a dust puff at the caster's feet on arrival; warm metal sparks on a disarm; dust trails for items. `force.pull.cast / drag / catch / disarm`.
- **Jump:** crouched gather with ground dust drawn to the feet and a faint low shimmer every 6 ticks (`force.jump.gather` loop); launch burst scaled by charge; trail motes for 10 ticks (none on Reduced); landing dust and block chips by surface and fall; Crushing Descent ground-level ripple shell, radial debris, caster shake 0.25; glide arms spread with `force.jump.glide` loop.
- **Heal:** warm gold-white `force_mote` particles (the quest feature's particle, recoloured) drawn in at the start, streaming into the chest and rising along the body, a very faint chest ripple per heal tick, upward drift at the end, scatter-and-fall on interrupt, thin ally streams, ward glints every 4 ticks on the body outline. No halo, ring or column.
- **Lightning:** charge sparks and 2 to 3 tiny arcs at the hand; arcs rebuilt every 2 ticks as 8 to 14 jittered segments (Reduced 1 arc no forks, Standard/High 2, Cinematic 3; 1 + rank forks of 0.5 to 1.5 blocks), segments live 2 to 3 ticks, alpha 0.7 to 1.0 per rebuild or a steady 0.85 with flashing off; chain arcs from the primary to the others; contact sparks per damage tick, impact sizzle at most 2 per 10 ticks, `saber.lightning_block` (max 1 per 10 ticks) on guarded slots; stop removes every live segment at once and leaves 2 to 3 dark wisps for 10 ticks; caster shake 0.03 while arcing. In first person the arcs leave from just below the view.
- Watchdogs: Heal 76 ticks; Lightning arcs hidden after 6 ticks without a damage-tick refresh (they return with the next one) and the channel forgotten after 40; Jump gather 70; glide 50; predictions 15. Level change and disconnect clear everything.

## 14. Balance (`balance.json` sections `force_pull`, `force_jump`, `force_heal`, `force_lightning`)
All spec 13 keys, with the spec's defaults (see the four `*Balance` classes). Extra keys: `force_heal.staggerAmplifier` (2: Slowness III and above counts as a stagger). Push-resistant displacement for Pull uses `force_push.resistantKnockbackMult` (0.4) and the stagger DR window `force_push.staggerDrWindowTicks`.

## 15. Commands
- `/oldrepublic push grant [player] | branch <id|none> | cooldown | fire` (unchanged).
- `/oldrepublic force grant <power|upgrade node|all> [player]`: powers go into `ForceData.abilities` and their skill node; upgrades are granted as skill nodes (or into the ability set when the trees do not know them).
- `/oldrepublic force use <power> start|release|cancel`: drives a power without the slot keys (tests, screenshots).
- `/oldrepublic force cooldowns`: clears the five cooldowns.

## 16. Tests
- `ForceGameTests` (extended): Pull speed formula, guard ×0.5, brake, aim assist / cone / line of sight, Disarming Pull sets `ForceStatus.isDisarmed`; Jump velocity by charge and Soaring, gather thresholds, cost, fall allowance; Crushing Descent damage; Heal amounts, interruption and timings; Shared Light excludes players in combat (recorded by `onDamaged`); Lightning damage (guard, rank, chain, Consuming ramp and cap, resistant), guard drain, burn, cooldowns; Chain target selection; alignment opposition for side powers; PvP flags; effect variant packing. The M1 `branchChoiceRules` test is removed with `BranchChoice`.
- `m2/ForceClientSteps.run(context, server)`: screenshots `oldrepublic_m2_40_pull_drag`, `41_jump_gather`, `42_jump_launch`, `43_jump_landing`, `44_heal_channel`, `45_lightning_charge`, `46_lightning_arcs`, `47_lightning_chain`, `48_lightning_after_stop`.

## 17. API notes (Milestone 2)
Verified in the probes or Fabric sources: `damageSources().source(key[, entity, entity])`, `ServerLivingEntityEvents.AFTER_DAMAGE`, `AttackEntityCallback`, `LivingEntity#causeFallDamage(double, float, DamageSource)`, `ServerPlayer#getLastClientInput()`, `ServerLevel#isPvpAllowed`, `Entity#isAlliedTo(Entity)`, `Entity#pick`, `AABB#getXsize/getCenter`, `Particle#setLifetime/remove`, `SingleQuadParticle#setColor`, `HumanoidRenderState.isCrouching`, `Options.keyJump`, `ClientLevel#getEntity(int)`, `FakePlayer.get(level, profile)`.
Open (marked `API-CHECK`, probes in `tools/api-probe/force_m2.txt`): `Input#jump()`, `AttributeInstance#addTransientModifier/removeModifier(Identifier)`, `TamableAnimal` location and `isTame()`, `Entity#is(TagKey)` / `BlockState#is(TagKey)`, the `@ModifyVariable` argument capture in the common mixin (same pattern as the saber mixin).

## 18. Known limitations
- The Soaring Leap fall-speed cap is applied by the client (vanilla player movement is client-simulated); a modified client could glide longer, but the server still charges energy per held tick and ends it.
- The lightning arc watchdog hides arcs after 6 ticks without a refresh, while refreshes come every 5 ticks: on a jittery connection the arcs may blink for a tick; the channel survives.
- No dynamic light from lightning (vanilla cannot).
- The spec's body lean in the Lightning pose is not done: humanoid arms are not children of the body, so tilting the body would detach them.

---

# Milestone 3: Force Sense, Telekinetic Lift, the dispatch order and Heal's revive

Spec: `docs/MILESTONE_3.md` sections 10.1 (Sense), 10.2 (Lift), 10.4 (granted nodes), 10.5 (keys), 10.6 (dispatch order), 11.2 (HUD), 13 (VFX and SFX), 14 (balance), 16 (payloads and events) and 5.4 (Heal raises downed troopers). Owner: W3 (Part A). Sections 1 to 18 still hold; this part only adds.

## 19. Classes

### 19.1 Common / server
| Class | Role |
|---|---|
| `sense/ForceSense` | The `force_sense` ability (`TAP`, never in a slot). `cast(player, locator, sink, now)`: start checks, spend, cooldown (×0.7 with `archivists_road`), collect, reveal, gesture, "watched" notice (PvP option), holocron direction from the locator's cache, follow-up searches, hidden count, mastery, `QuestHooks.onForceSense`. `tick(server)` sends the best answer when the follow-up window closes; `forget` / `clear`. |
| `sense/SenseTargets` | Provider registry (contract). `Point(kind, pos, entityId, attitude)`, kinds `BLOCK, CACHE, ANCHOR, URN, CREATURE` (append only). `collect(caster, blockRange, creatureRange, max)` asks every provider (a provider that throws is logged and skipped), sorts nearest to the eye first and keeps `min(maxPoints, 32)`. |
| `sense/SenseCreatures` | Sense's own provider: mobs within the creature range, by entity lookup (never a block scan), tinted by `Attitudes`; other players only with `pvpEffects`. Tamed animals owned by the caster count as allied. |
| `sense/SenseLocator`, `SenseSink` | Seams: the holocron locator (`cached(p, kind)`, `find(p, kind, radius, exclude, callback)`, backed by `StructureLocator`) and the output (reveal payload, gesture event, holocron pulse, follow-up pulse). The game uses `HolocronService` and the network; the tests use fakes. |
| `sense/SenseRules` | Pure rules: cost with mastery (never below 0), creature range with mastery, cooldown with `archivists_road` (at least 1), follow-up window `followUpDue`, closeness `1 - d / radius`, flat direction, Danger cone test `inDangerCone`. |
| `sense/SenseBalance` | `force_sense` section (section 24). |
| `lift/ForceLift` | The `force_lift` ability (`CHANNEL`). The press chooses a target in the 10.6 order and pays the start cost; the channel pays `costPerSecond` every 10 ticks, holds for 60 ticks (Suspend 100) and ends in a slam or a Hurl throw. A hit of 6 or more on the caster drops the target without a slam. Every end path lets the target go. |
| `lift/LiftedTargets` | Creatures held now: transient gravity modifier `oldrepublic:lift_gravity` (gravity 0), the rise to the chosen height over 10 ticks, paused AI through `MobLiftMixin`. Nothing is saved; an unload or a server stop leaves nothing behind. |
| `lift/LiftRules`, `LiftShape`, `LiftBalance` | Pure rules (cost with mastery and `archivists_road`, range with mastery, hold by shape, height per target), the shapes `NONE, SUSPEND, HURL` (node ids `lift_suspend`, `lift_hurl`) and the `force_lift` section. |
| `ForceDispatch` | The 10.6 order shared by Push, Pull and Lift: a `ForceReactiveEntity` on the aim, then a living target, then a reactive block (`ForceBlockInteractions`), then (Push and Pull) loose items. `Finders` is injectable so the game test can put a stand-in on the aim. |
| `heal/HealRevive` | On every Force Heal heal tick, each downed trooper within 4 blocks of the caster is stood up through `Troopers.tend(caster, trooper, true)` (through the `Downed` seam). |
| `network/SenseRevealPayload` | S2C `sense_reveal`, caster only: up to 32 points (kind, x, y, z, entity id, attitude) and the reveal length. Reading refuses more than 32. |
| `mixin/force/MobLiftMixin` (common) | Pauses a held creature's AI (`Mob#serverAiStep` cancelled while `LiftedTargets.isLifted`). |

The ability runtime routes the Sense key (`AbilityRuntime.SENSE_SLOT`, 8, beyond the slot range) to `force_sense` on a press only; a release is ignored and an active charge or channel is not cancelled (Sense refuses instead).

### 19.2 Client (`com.oldrepublic.client.force`, `client.hud`)
| Class | Role |
|---|---|
| `SenseFx` | Caster reveal (inhale, streams, clusters, reveal ticks), the gesture for everyone, holocron pulses. |
| `SenseReveal` | The current reveal (points, start, length) for the effects and the HUD. `ForceClient` clears it, with `SenseFx`, on a level change, the local player's death or respawn, and a disconnect (spec 12.2). |
| `DangerSense` | Danger Sense on the client: an effect-event listener that prickles for attack tells behind the player. |
| `LiftFx` | Lift start, held column, flail, slam, hurl; the held-target watchdog. |
| `hud/DangerEdgeHud` | The dust tick at the screen edge toward the tell (HUD element `danger_edge`). |
| `particle/SenseMoteParticle`, `SenseStreamParticle`, `LiftShimmerParticle` | The three new particle types (registered in `ForceParticleProviders`). |
| `PowerAnimations` (`SENSE`, `LIFT`, `flail`), `PowerPose` (`FLAIL` key), `mixin/HumanoidModelMixin` | Poses: Sense two fingers to the temple, head tilted; Lift free arm raised, palm up, trembling; a lifted humanoid flails its arms and legs. |
| `mixin/force/LivingEntityRendererLiftMixin` (client) | Copies the flail amount onto every living entity's render state. |
| `ForceAbilityHooks` | Sense precheck (awake, learned, cooldown, energy); Lift prediction (pose and loop at once, dropped after 130 ticks or on the server's end). |
| `ForceKeys`, `AbilityInput` | `key.oldrepublic.force_sense`, default **H**: a press sends the Sense input (slot 8); the cost preview uses Sense's flat mastery cost and Archivist's Road on Lift. |
| `HealFx` | Mending Breath: dull grey-green wisps drawn out of a cleansed body. |

## 20. Server flows

- **Sense (TAP, own key).** Refused (with the Milestone 1 fail feedback) while not awakened, unlearned, cooling down, short of energy, refused by alignment, locked by another feature (`AbilityLocks`), or while another power charges or channels (a Lift channel included). It costs `15 - masteryRank` FE (never below 0) and has a 240-tick cooldown (168 with `archivists_road`). Collect: every provider within `blockRange` 16 (blocks) and `creatureRange` 24 + 4 per rank (creatures). Points are sorted nearest first and cut to 32. The caster gets `sense_reveal` (160 ticks, Seeker +80) and everyone tracking gets the gesture (`force_sense` variant 0). With `pvpEffects` on, revealed players get the overlay `message.oldrepublic.force.sense_watched`. The holocron direction is read at once from `SenseLocator.cached`: the nearest cached site, over every kind in `HolocronService.senseSearchKinds(player)`, that is within 256 blocks (512 with Seeker) and not exhausted for the player. When there is one, the caster gets variant 1 (flat direction, `senseKind`, closeness). A fresh `find` then runs for each kind. The follow-up pulse goes out once every search has answered within `holocron.senseFollowUpTicks` (40) of the cast, or when that window closes with the best answer so far. It is the quest feature's `calling` event, variant 4, sent to the caster with the direction toward the nearest fresh site. Nothing is sent when that is the site the cast already showed. Late answers and a dimension change drop the follow-up. A cast that revealed at least one hidden point earns a mastery point: a block, anchor or urn point, a cache point that is an echo cairn (found by its block id; a Seeker's Sight glint on a pedestal or chest was never hidden and does not count), or a loaded creature out of line of sight. `QuestHooks.onForceSense(player, points)` is called.
- **Lift (CHANNEL).** The press chooses what it reaches (10.6), and nothing is spent when nothing answers. The order:
  1. a reactive entity on the aim ray (`onForceLift` every tick);
  2. a living target (crosshair ray, else nearest within the 8° cone, line of sight, height at most 2.4). Its height comes from `ForceReactions.liftHeight`: 2.5, 1.2 when push resistant, a boss's own cap, and 0 refuses. Its hold comes from `ControlEffects.diminished(target, holdTicks, HOLD)`, and 0 refuses. The lift interrupts the target's wind-up;
  3. a reactive block on the aim (`ForceBlockInteractions` act `LIFT`, dispatched every tick; W1's rubble counts its own 20 ticks).

  The start costs 20 FE (×(1 − 0.05 × rank), ×0.9 with `archivists_road`, then the shared multipliers), and 8 FE per second is paid in 10-tick steps. When the energy runs out, the channel ends as if released.
  - **Release or hold end:** a slam. The target is driven down (−1.0 speed) and takes 6 `force_impact` damage plus a 15-tick stagger (a boss 10) on landing, or after 30 ticks at the latest.
  - **Hurl:** the target is thrown 10 blocks along the flat aim and takes 6 damage and a 20-tick stagger when it hits a wall or the ground (30 ticks at the latest). The first creature it meets on the way takes the same blow. Hurl never throws a player.
  - **Interruptions:** a hit of 6 or more on the caster drops the target without a slam. A lost target (dead, unloaded, out of range, freed) ends the channel with no slam. Death, disconnect, dimension change, a slot change or a stop cancel the channel.
  - **Other effects:** holding a hostile for 20 ticks earns the mastery point. With Suspend, the caster's allies deal +20% to the held creature: the caster, players the caster has not fought within `force_heal.combatExcludeTicks` (the Shared Light rule), and creatures `Attitudes` reports as allied. A player in a fight with the caster gets no bonus. Players are held only when both `force_lift.pvpEffects` and server PvP are on, through `Holds` (source `force_lift`).
  - **Cooldown:** 280 ticks, counted from the release.
- **Dispatch (10.6).**
  - **Push:** the cone goes first. A reactive entity in it takes `onForcePush`, and `true` skips its knockback. Then a reactive block on the aim gets `PUSH`.
  - **Pull:** a reactive entity on the aim, then a living target, then a reactive block (`PULL`), then the loose items around the ray end. Energy is spent only when a step answered.
  - **Lightning:** after the 8-tick charge, it feeds a reactive entity on the aim every arc tick (`onForceLightning(caster, perTick)`). While one takes the arcs, no living target is struck.
  - **Thrown saber:** it touches non-living `ForceReactiveEntity`s on its path (`onSaberThrow(caster, damage)`).
- **Heal revive.** On each heal tick of a channel, `HealRevive.revive(caster)` stands up every downed trooper within 4 blocks (`Troopers.tend(caster, trooper, true)`). A second call finds none. The 4 is `HealRevive.RANGE`, the default of W2's `missions.healTendRange`: W2's `MissionBalance` merges after W3 (15.3), so the lead switches the call to `MissionFeature.balance().healTendRange` at W2's merge.
- **Mending Breath (`mending_breath`).** On every heal tick, Slowness, Weakness and Poison are removed from the caster and from every Shared Light ally that is healed. Each cleansed entity sends `force_heal` variant `HEAL_CLEANSED` (3).
- **Watcher's Insight (`watchers_insight`).** Force Heal costs 10% less (a `ForceModifier`). It is dormant at alignment below +15, like every Jedi node.

## 21. Payloads and effect events (Milestone 3)
| Id | Sent | Fields |
|---|---|---|
| `sense_reveal` (payload) | caster | points (kind, pos, entity id or −1, attitude), reveal ticks |
| `force_sense` | variant 0 (gesture): tracking caster; variant 1 (holocron): caster | 0: entity caster, pos eye, intensity points. 1: dir unit horizontal, entity `HolocronKind` ordinal, intensity closeness 0..1 |
| `force_lift` | tracking caster | variant `packCaster(caster, phase)`, phase 0 start (intensity hold seconds), 1 end, 2 slam (pos feet, intensity damage), 3 hurl (dir throw, intensity distance); entity target (−1 for a block), pos target or block centre |
| `force_lift_target` | tracking target | entity target, pos feet, intensity hold seconds (0 = let go) |
| `force_heal` variant 3 | around | `HEAL_CLEANSED`: entity = the cleansed entity |
| `calling` variant 4 (`QuestEffects.CALLING_HOLOCRON`, the quest feature's event) | caster | Sense's follow-up: pos eye, dir unit horizontal, entity `HolocronKind` ordinal, intensity closeness; drawn and voiced by the quest client's `CallingFx` |

A reveal is one packet per cast. Lift sends one start, one target, one end and one slam or hurl per channel.

## 22. VFX and SFX treatment (phase by phase)

Shared rules: only motes, dust and bent air; no outline, glow, ring, ground circle or column of light. Counts go through `Vfx.count` (Reduced about a third, Cinematic more) and distant casters are culled with `Vfx.inRange`. Sounds are the placeholder recipes in `tools/assets/sounds/force.py` (briefs in `docs/assets/force.md`).

### 22.1 Force Sense
| Phase | Ticks | Caster sees and hears | Others see and hear |
|---|---|---|---|
| Cast | 0 | `force.sense.cast` (0.8): an inhale through the teeth, then a soft resonant swell. Pose `SENSE` for 10 ticks (two fingers of the free hand to the temple, head tilted; first person the hand rises toward the view). The mod's ambience dips 30% for 20 ticks: the world feature's site and cave beds dip themselves on this gesture event when its entity id is the local player (`SiteClient.senseDip`). | `force.sense.cast` (0.55), the same pose, a faint breath of 5 pale dust motes leaving the head. |
| Inhale | 0 to 15 | Every 4 ticks, 2 `sense_mote`s rise around the waist and drift outward (8 in all). | – |
| Streams | from 6 | Nearest point first, one `sense_stream` speck per tick per point (up to 4 per point, 24 per cast), from just in front of the chest to the point, life 8 to 24 ticks by distance. A large reveal (over 16 points) staggers its starts two per tick. | – |
| Clusters | arrival to the end of the reveal | Every 5 ticks, a wave of 2 to 5 `sense_mote`s, spread round-robin over the points that have arrived. They drift slowly at chest height, about 30 ticks each, and the last wave fades with the reveal. A creature's cluster follows it and ends if it dies. Tints: hostile ember orange, wary amber, neutral grey-white, allied pale blue, blocks pale gold. | – |
| Reveal ticks | on each arrival | Each hidden point (any block point, or a creature out of line of sight) gets `force.sense.reveal`, a soft pebble tick, played up to 6 blocks away in its direction. At most 6 per cast, at least 3 ticks apart, pitch rising slightly. | – |
| Holocron | from 20 | Three pulses 10 ticks apart. Each pulse sends 4 kind-tinted motes along the direction (Jedi warm gold, Sith rust, Wayfarer brass), brighter when closer. The first pulse plays `story.calling_holocron` quietly, louder when closer. No pulse when the caster stands on the site's column. | – |
| Follow-up | within 40 ticks | The quest feature's `calling` pulse (variant 4) toward the fresh site, drawn by `CallingFx` with `story.calling_holocron`. Direction only, never a position. | – |
| End | 160 (240 with Seeker) | The clusters stop. Level change and disconnect clear everything. | – |

Budget at Standard: at most 60 live Sense particles per cast (8 inhale, 24 stream specks, 30 cluster motes). The 12 pulse motes come after the inhale has faded.

**Danger Sense** (`sense_danger`, client only). When an enemy attack tell (the frozen `DangerTellTable`: NPC saber wind-up, captain heavy, acolyte shove, trooper charge, grenade tell and fuse, tomb vent, sentinel slam and lance, the boss attacks) starts within 12 blocks and outside the 100° front cone, the player hears `force.sense.danger` (a dry prickle, cut off sharply) 3 blocks from the head in the tell's direction. `DangerEdgeHud` shows 8 pale dust specks at that edge of the view (the bottom edge when the tell is straight behind), drifting outward and fading within 18 ticks. At most one prickle per 20 ticks. Range, cone and repeat come from the `force_sense` info entry.

### 22.2 Telekinetic Lift
| Phase | Ticks | What it looks and sounds like |
|---|---|---|
| Start | 0 | Pose `LIFT`: the free arm raised toward the target, palm up, a small uneven tremble (first person: the hand lifts and trembles). `force.lift.start`: pressure gathering, a lurch, loose grit. A puff of 3 bent-air ripples at the palm. The `force.lift.loop` starts on the pose and stops with it. |
| Rise | 0 to 10 | The target rises to its height (2.5, or 1.2 when resistant) and turns slowly (2.5° per tick) while held; a humanoid starts to flail its arms and legs (full swing after 6 ticks). No throat or chest constriction. |
| Held | to the end of the hold | Every 2 ticks a `lift_shimmer` wisp at a random height of the column between the ground and the target's feet. They are staggered, never a disc, and at most 3 blocks tall without ground. Every 5 ticks a dark pebble drops from the feet, and every 3 ticks pale dust in the ground's colour sifts down. Every 6 ticks a bent-air ripple rises off the caster's palm. About 20 live particles per target. `force.lift.loop` plays all along: a strained low hum of held weight with a trickle of grit. |
| Slam | release or hold end | `force.lift.slam`: a heavy thud, a crunch of debris, a dust breath. 10 irregular dust puffs of varied reach and height (gaps, never a ring) and 8 debris particles of the landing block. A 0.1 camera shake within 5 blocks. |
| Hurl | release or hold end, Hurl | `force.lift.hurl`: displaced air rushing past, tumbling grit. 8 faint `push_streak`s along the throw and a bent-air ripple. |
| End | release, interruption, loss | The pose and the loop end on the `force_lift` end event. If that event is lost, a watchdog ends them at the hold plus 30 ticks (the own prediction at 130). The held column ends on `force_lift_target` 0 or at its own watchdog. Death, removal, a level change and a disconnect clear everything. |

A block target (W1's rubble) gets the pose and loop from the caster's events. The block's own crumble and slide are W1's.

### 22.3 Heal (Mending Breath)
On `HEAL_CLEANSED`, dull grey-green wisps leave the cleansed body, drift outward and thin out as they rise. No sound of its own; the Heal channel sounds carry it.

## 23. Keys, HUD and screen
- **H** (`key.oldrepublic.force_sense`, category `oldrepublic:force`) casts Sense. Its clicks are drained every tick, and the press is skipped while a screen is open or the player is held (`Holds.isHeld`). Sense never takes a slot: `SkillRules.slotless`, `SkillService.nodeAdded` skips it, and `assignSlot` refuses it with the overlay `message.oldrepublic.skill.slot.slotless`.
- **Sense pip** (`ForceHud`): a small eye (`skill/sense_pip`) right of the sockets, with the key's name under it. It is dim while Sense cools down and relit from the bottom by a sweep over the eye's rows 3 to 13. A brief glint shows when it is ready, and an amber pulsing tint when energy is below Sense's cost. It fades in when Sense is learned.
- **Force screen:** the Exploration tab appears once Sense is known. Sense's node shows its key as a button instead of a slot: no drag and no number keys.
- **Cost preview:** the HUD and the Force screen multiply costs by the player's `cost_modifiers` entry. That entry carries `ForceModifiers.cost × ForceReactions.costMultiplier` per ability and is re-sent when it changes, checked every 20 ticks.

## 24. Balance (`balance.json` sections `force_sense`, `force_lift`)
- `force_sense`: `cost` 15, `cooldownTicks` 240, `durationTicks` 160, `creatureRange` 24, `blockRange` 16, `maxPoints` 32, `holocronRadius` 256, `seekerRadius` 512, `seekerBonusTicks` 80, `glintRange` 24, `dangerRange` 12, `dangerConeDegrees` 100, `dangerRepeatTicks` 20, `masteryCostPerRank` 1, `masteryRangePerRank` 4, `pvpEffects` false.
- `force_lift`: `startCost` 20, `costPerSecond` 8, `cooldownTicks` 280, `maxHoldTicks` 60, `suspendHoldTicks` 100, `range` 14, `coneDegrees` 8, `maxTargetHeight` 2.4, `height` 2.5, `resistantHeight` 1.2, `riseTicks` 10, `slamDamage` 6, `slamStaggerTicks` 15, `hurlDistance` 10, `hurlDamage` 6, `hurlStaggerTicks` 20, `suspendAllyBonus` 0.2, `interruptDamage` 6, `bossHeight` 1.2, `bossStaggerTicks` 10, `rubbleTicks` 20, `masteryCostPerRank` 0.05, `masteryRangePerRank` 1, `pvpEffects` false.
- The node numbers (`archivists_road` and the rest) are in the `skills` section; see `docs/design/skill.md`.

## 25. Commands
- `/oldrepublic force grant force_sense|force_lift|sense_danger|sense_seeker|lift_suspend|lift_hurl|all [player]` (the grantable list now holds the seven powers and their upgrades).
- `/oldrepublic force use force_sense start` and `/oldrepublic force use force_lift start|release|cancel` drive the powers without keys.
- `/oldrepublic force cooldowns` clears all seven cooldowns.

## 26. Tests (Milestone 3)
`ForceGameTests` (arena `oldrepublic_test:arena_24x7x24`, listed mock players, test hooks registered once and inert for anything unmarked):
- `senseCostCooldownCapSlot8`
- `senseProvidersCalledNoBlockScan`
- `senseColdCastSendsRevealWithoutDirection`
- `senseFollowUpPulseWithin40Ticks`
- `liftHeightDurationResistantHeight`
- `liftHeightZeroRefuses`
- `liftHoldUsesHoldKind`
- `dispatchOrderPushPullLift`
- `lightningAndThrowReachReactiveEntities`
- `healRevivesDownedThroughTend`
- `grantedNodeEffects`

Energy is checked on a survival `FakePlayer` because mock players are creative. A test-local `ReactiveItemStandIn` (an `ItemEntity` that implements `ForceReactiveEntity`) and a lodestone handler stand in for W1's and W2's reactive things, and fake `SenseLocator` and `SenseSink` stand in for the holocron service and the network. `SkillGameTests.explorationTreeAndSlotlessSense` checks the tree, its visibility and the slotless rule.

Client steps (`m2/ForceClientSteps.runM3`) take these screenshots:
- `oldrepublic_m3_30_sense_reveal`
- `31_danger_sense_edge`
- `32_lift_zombie`
- `33_lift_rubble` (with a logged stand-in when W1's rubble is absent)
- `34_exploration_tree_tab`

## 27. API notes (Milestone 3)
Checked against the 26.2 dumps or the Fabric sources: `Mob#serverAiStep` (protected, `MobLiftMixin`), `Attributes.GRAVITY` / `FALL_DAMAGE_MULTIPLIER` / `MAX_HEALTH` / `MOVEMENT_SPEED`, `Player#causeFoodExhaustion(float)` (`PlayerExhaustionMixin`), `LivingEntity#removeEffect(Holder)`, `LivingEntity#hasLineOfSight(Entity)`, `ServerEntityEvents.ENTITY_UNLOAD`, `ServerLifecycleEvents.SERVER_STOPPING`, `ConventionalEntityTypeTags.BOSSES`, `LivingEntityRenderer#extractRenderState(LivingEntity, LivingEntityRenderState, float)`, `InputConstants.KEY_H`, `AABB#clip`. `AttributeInstance#addOrUpdateTransientModifier` and `removeModifier(Identifier)` are not in the dumps, but code that already built in CI uses them (`Holds`, `SaberForms`, `ForceHeal`). `AttributeInstance#addPermanentModifier(AttributeModifier)` (Hardy's saved health in `SkillAttributes`) is called by vanilla `Mob#finalizeSpawn` (bytecode in `api/enemy.txt`). Still open (marked `API-CHECK`): `TamableAnimal#isOwnedBy(LivingEntity)` in `SenseCreatures`, and the Milestone 2 checks listed in section 17.

## 28. Known limitations (Milestone 3)
- A held creature keeps its position by gravity 0 and a paused AI. A creature pushed by another source (an explosion, water) drifts while held, and the channel ends if it leaves the range.
- Lifted players (PvP option only) are held through `Holds`. Their client simulates their own movement, so on a high-latency connection a held player may jitter for a tick.
- The Sense ambience dip is the world feature's (W1 `SiteClient.senseDip`, on the caster's own gesture event); until W1 merges, only the reveal ticks stand out.
- Danger Sense only reacts to tells the server sends as effect events (`DangerTellTable`). Vanilla mobs have no tells, so a skeleton drawing a bow does not prickle.
- The Sense pip shows the Sense key's current binding name, cut to the socket's width; an unbound key shows the shortened vanilla "not bound" text.
- The test stand-ins (`ReactiveItemStandIn`, the lodestone handler) prove the 10.6 order with Part A's code only; W1's rubble and W2's reactive entities are checked by their own tests.
