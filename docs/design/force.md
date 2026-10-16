# Force feature design: Force Push (Milestone 1) and the Force powers (Milestone 2)

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
