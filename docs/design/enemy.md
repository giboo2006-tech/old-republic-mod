# Enemy feature design: Crystal Poacher, blaster bolt, blaster rifle

Owner: enemy worker. Packages `com.oldrepublic.enemy` (common) and `com.oldrepublic.client.enemy` (client).
Spec sources: `docs/MILESTONE_1.md` (Crystal Poacher, id table), `docs/VFX_SFX_SPEC.md` (Crystal Poacher and blaster).

API status legend: **[V]** verified against 26.2 javap logs or Fabric 26.2 source; **[U]** unverified, probe queued in
`tools/api-probe/enemy.txt` (code will carry `// API-CHECK:` until confirmed).

## 1. Content and ids

| Kind | Id | Class |
|---|---|---|
| Entity (monster) | `oldrepublic:crystal_poacher` | `enemy.CrystalPoacher` |
| Entity (projectile) | `oldrepublic:blaster_bolt` | `enemy.BlasterBolt` |
| Item | `oldrepublic:blaster_rifle` | plain `Item` (stacksTo 1), no use action in M1 |
| Item | `oldrepublic:crystal_poacher_spawn_egg` | `Item` with `Item.Properties#spawnEgg(type)` **[V]** |
| Particle types | `oldrepublic:muzzle_glow`, `oldrepublic:bolt_impact`, `oldrepublic:bolt_scorch` | `FabricParticleTypes.simple()` **[V]** |
| Effect events | `oldrepublic:blaster_fire`, `oldrepublic:bolt_impact` | sent with `EffectEvents.around` **[V]** |
| Loot table | `oldrepublic:entities/crystal_poacher` | `data/oldrepublic/loot_table/entities/crystal_poacher.json` |
| Balance section | `enemy` | `enemy.EnemyBalance` via `BalanceConfig.section` **[V]** |

Common classes (`src/main/java/com/oldrepublic/enemy/`):

- `EnemyFeature` - `init()`: registers entity types, attributes, spawn placement, items, particle types, natural spawn (gated).
- `EnemyEntities` - `EntityType` constants and registration.
- `EnemyItems` - `blaster_rifle`, spawn egg (through `ModRegistry.item`).
- `EnemyParticles` - particle types.
- `EnemyBalance` - tunables (section 7).
- `CrystalPoacher` - the mob.
- `PoacherCombatGoal` - range keeping, strafing, retreat, line-of-sight repositioning and the charge/burst/recovery shot cycle.
- `PoacherCoverGoal` - break line of sight when hurt.
- `BlasterBolt` - projectile, implements `combat.DeflectableProjectile`.
- `PoacherLoot` - kill counter, bad-luck protection, quest hook.

Client classes (`src/client/java/com/oldrepublic/client/enemy/`):

- `EnemyClient` - `init()`: renderers, model layer, particle providers, effect handlers, client tick (tells, flyby).
- `CrystalPoacherRenderer`, `PoacherRenderState`, `PoacherModel`, `MuzzleGlowLayer`.
- `BlasterBoltRenderer`, `BlasterBoltRenderState`.
- `EnemyParticlesClient` - `MuzzleGlowParticle`, `BoltImpactParticle` (spark), `BoltScorchParticle` providers.
- `EnemyEffectsClient` - effect event handlers, charge whine and flyby detection.

No mixins are planned; `oldrepublic-enemy.mixins.json` stays empty.

## 2. Registration

- Entity types **[V]**: `EntityType.Builder.of(CrystalPoacher::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(8).build(key)`,
  then `Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type)` (pattern from Fabric testmod `EntityDataAccessorTest`).
- Bolt **[V]**: `EntityType.Builder.<BlasterBolt>of(BlasterBolt::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(1).noSave()`,
  plus Fabric's `alwaysUpdateVelocity(true)` (interface-injected `FabricEntityType.Builder` default method **[V]**) so redirects show at once on clients.
  `noSave()` is fine because a bolt lives 3 s at most.
- Attributes **[V]**: `FabricDefaultAttributeRegistry.register(type, CrystalPoacher.createAttributes())` where
  `createAttributes()` = `Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 2).add(Attributes.ATTACK_DAMAGE, 3)`.
  Balance values are applied per entity on its first server tick with `getAttribute(...).setBaseValue(...)` **[U: LivingEntity#getAttribute / AttributeInstance#setBaseValue in full LivingEntity dump]**, because attribute suppliers are built before `BalanceConfig.reload()` runs.
- Spawn placement **[V]**: `SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CrystalPoacher::checkPoacherSpawnRules)`.
  The rule: `Monster.checkMonsterSpawnRules(...)` **[V]** and, for `EntitySpawnReason.NATURAL` only, `balance.naturalSpawns` true, y below sea level minus 8 and `!level.canSeeSky(pos)` **[U: canSeeSky on LevelAccessor]**.
- Natural spawns (optional, off by default): `BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type, 2, 1, 2)` **[V addSpawn signature; U BiomeSelectors.foundInOverworld]**. Registered unconditionally (biome modifications are applied before the balance file is read) and gated in the spawn predicate, so the config switch works without restarts of the data pack.
- Spawn egg **[V]**: `ModRegistry.item("crystal_poacher_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(EnemyEntities.CRYSTAL_POACHER))` (probe confirmed `SpawnEggItem(Properties)`). Client item definition copied from the vanilla `zombie_spawn_egg` layout (probe).
- Renderers **[V]**: `EntityRendererRegistry.register(type, CrystalPoacherRenderer::new)` (Fabric 26.2 `fabric-rendering-v1`).
- Model layer **[V]**: `ModelLayerRegistry.registerModelLayer(new ModelLayerLocation(id("crystal_poacher"), "main"), PoacherModel::createBodyLayer)` (Fabric 26.2 class is `ModelLayerRegistry`, not `EntityModelLayerRegistry`).
- Particles **[V]**: `Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, FabricParticleTypes.simple())`; client `ParticleProviderRegistry.getInstance().register(type, SpriteSet -> provider)` (Fabric testmod `ParticleGroupRegistryTests`).

## 3. Crystal Poacher

`CrystalPoacher extends Monster` **[V]**. It does not implement `RangedAttackMob` (the vanilla bow goals are not used; a custom goal owns the shot cycle).

### 3.1 Synced state

- `DATA_CHARGING` : `EntityDataAccessor<Boolean>` via `SynchedEntityData.defineId(CrystalPoacher.class, EntityDataSerializers.BOOLEAN)`, defined in `defineSynchedData(SynchedEntityData.Builder)` **[V]**. True during each 0.6 s tell.
- `DATA_AIMING` : Boolean, true while it has a target in the combat band (renderer raises the rifle). (Could use `Mob#setAggressive`/`isAggressive` **[V]** instead; that flag is synced by vanilla - decision in Phase 2 after checking that `isAggressive` reaches the client.)

### 3.2 Goals

`registerGoals()` **[V]**:

| Selector | Priority | Goal | API |
|---|---|---|---|
| goal | 0 | `FloatGoal(this)` | **[U]** ctor |
| goal | 1 | `PoacherCoverGoal` (MOVE, LOOK) | custom on `Goal` **[V]** |
| goal | 2 | `PoacherCombatGoal` (MOVE, LOOK) | custom on `Goal` **[V]** |
| goal | 5 | `WaterAvoidingRandomStrollGoal(this, 0.8)` | **[U]** ctor |
| goal | 6 | `LookAtPlayerGoal(this, Player.class, 10f)` | **[V]** |
| goal | 7 | `RandomLookAroundGoal(this)` | **[U]** ctor |
| target | 1 | `new HurtByTargetGoal(this).setAlertOthers(CrystalPoacher.class)` | **[V]** (anything that hurts it, and the camp reacts together) |
| target | 2 | `NearestAttackableTargetGoal<>(this, Player.class, true)` | **[V]** |

`Goal$Flag` values MOVE and LOOK **[U: enum names, probe Goal$Flag]**.

### 3.3 PoacherCombatGoal (how each behaviour is achieved)

Runs while `mob.getTarget()` **[V]** is alive. `requiresUpdateEveryTick()` true **[V]**. Every tick:

1. Look: `mob.getLookControl().setLookAt(target, 30, 30)` **[U: LookControl#setLookAt(Entity,float,float)]**; body yaw follows via vanilla body control.
2. Line of sight: `mob.getSensing().hasLineOfSight(target)` **[V]**; keep `seeTicks` (positive while seen, negative while not), like `RangedBowAttackGoal`.
3. Range keeping (distance d = `mob.distanceTo(target)`):
   - **Too far** (d > `maxRange` 14) or not seen: `mob.getNavigation().moveTo(target, 1.0)` **[V]**.
   - **In band** (8..14) and seen: `getNavigation().stop()` **[V]** then strafe sideways with `mob.getMoveControl().strafe(0, ±0.5f)` **[V]**. Strafe direction flips at random every 20-40 ticks and immediately when `horizontalCollision` is set (vanilla bow goal pattern; confirm in `body:RangedBowAttackGoal#tick`).
   - **Too close** (d < `minRange` 8): strafe backwards `strafe(-0.6f, side)` while still facing the target (keeps shooting).
   - **Player closes in** (d < `retreatRange` 5): leave strafing and retreat: `DefaultRandomPos.getPosAway(mob, 10, 5, target.position())` **[U]** then `navigation.moveTo(x, y, z, 1.25)` **[V]**. Re-evaluated every 10 ticks; no shooting during the sprint (the charge is cancelled). If no position is found and the target is within 2.2 blocks, a rifle-butt bash: `doHurtTarget(serverLevel, target)` **[V]** with a 30-tick cooldown and arm swing `swing(InteractionHand.MAIN_HAND)` **[U]**.
4. Regaining line of sight: after 20 ticks without sight, sample up to 8 points `LandRandomPos.getPos(mob, 10, 4)` **[U]** (or `getPosTowards(mob, 10, 4, target.position())` **[U]**). Keep the first whose eye point has a clear `level.clip(new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))` **[V ClipContext]** (result type MISS **[U: HitResult$Type]**) and whose distance to the target is within the band; `navigation.moveTo(...)`. Fallback: path toward the target. Repeated at most every 30 ticks.
5. Shot cycle (state machine, server only):
   - `READY` -> if seen and d <= maxRange + 2 and recovery is over: enter `CHARGING`, set `DATA_CHARGING` true, timer = `chargeTicks` (12 = 0.6 s).
   - `CHARGING` -> counts down while the target stays visible; if sight is lost for more than 6 ticks or the poacher starts retreating, cancel (flag false, short 10-tick recovery). At 0: fire one bolt (3.4), flag false; if shots left in the burst, go back to `CHARGING` (each shot has its own 0.6 s tell, as the spec requires); else `RECOVERY` for 40-60 ticks (random in balance range).
   - Burst size is 2 or 3 (random per burst).
   - Every state change that ends the goal (`stop()` **[V]**) clears `DATA_CHARGING`, so a tell never sticks.

### 3.4 Firing

- Muzzle point: eye position **[V getEyePosition]** + forward(0.6) + right(0.35 on main arm side) - up(0.25), computed from `yBodyRot`/`getXRot` **[U field/method names in full LivingEntity dump]**.
- Aim: at `target.getEyePosition()` minus 0.35 Y (chest), with a small lead `target.getDeltaMovement() * (d / boltSpeed) * 0.5`, plus spread `aimSpreadDegrees` (2.5) scaled by difficulty (`level.getDifficulty()` **[U]**).
- Spawn: `BlasterBolt bolt = new BlasterBolt(level, this)`, `bolt.shoot(dx, dy, dz, boltSpeed, inaccuracy)` **[V Projectile#shoot(double,double,double,float,float)]**, `level.addFreshEntity(bolt)` **[U, standard]**. (Alternative `Projectile.spawnProjectileUsingShoot(...)` **[V]**, which needs an ItemStack - the rifle stack - and triggers `applyOnProjectileSpawned`; decide after reading its body.)
- Sound: `level.playSound(null, x, y, z, ModSounds.BLASTER_FIRE, SoundSource.HOSTILE, 1.0f, 0.95-1.05)` **[V Level#playSound(Entity,double,double,double,SoundEvent,SoundSource,float,float)]**. The two variants come from `sounds.json`.
- Muzzle flash: `EffectEvents.around(level, BLASTER_FIRE, muzzle, dir, id, 1, 0)` **[V]**. Client: 1 bright `muzzle_glow` flash (bigger, 3 ticks) + 2-4 small sparks.

### 3.5 Cover when hurt (PoacherCoverGoal)

- `canUse()`: has target; health below `coverHealthFraction` (40%) of max **[V getHealth/getMaxHealth in LivingEntity, confirm in full dump]**; hurt in the last 40 ticks (`tickCount - getLastHurtByMobTimestamp() < 40` **[V]**); cover cooldown elapsed (200 ticks); a cover spot found.
- Finding cover: sample 12 points with `LandRandomPos.getPos(mob, 8, 4)` **[U]** (or `DefaultRandomPos.getPosAway(mob, 8, 4, target.position())` **[U]**); a point is cover when `level.clip(ClipContext(targetEye -> point + 1.5 Y, COLLIDER, NONE, mob))` hits a block (BLOCK result) before reaching it. Choose the nearest one with a path: `navigation.createPath(BlockPos, 0)` **[V]** not null, then `navigation.moveTo(path, 1.3)` **[V]**.
- While running for cover the charge is cancelled. In cover it holds 40-60 ticks (crouch-like stillness, looking toward the target), then the goal ends and `PoacherCombatGoal` resumes, naturally peeking out to regain sight (3.3 step 4). Not feasible spots: the goal simply does not start (the spec says "if feasible").

### 3.6 Other entity behaviour

- Equipment: `populateDefaultEquipmentSlots(RandomSource, DifficultyInstance)` **[V]** sets `EquipmentSlot.MAINHAND` to a `blaster_rifle` stack via `setItemSlot` **[U, standard]**; also set in `finalizeSpawn` **[V]** and on first tick if empty (structure NBT entities may skip finalize). `setDropChance(EquipmentSlot.MAINHAND, 0)` **[V]** so the rifle never drops (not usable in M1).
- Persistence: `finalizeSpawn(..., EntitySpawnReason reason, ...)` **[V]** calls `setPersistenceRequired()` **[V]** when the reason is `STRUCTURE` (and `SPAWN_ITEM_USE`/`COMMAND`, so test poachers stay). Because template entities may not get `finalizeSpawn` (probe `StructureTemplate#placeEntities`), the world worker is asked to include `PersistenceRequired:1b` in the camp NBT entities as well. Natural spawns despawn normally.
- Sounds: `getHurtSound(DamageSource)` -> `ModSounds.POACHER_HURT`, `getDeathSound()` -> `ModSounds.POACHER_DEATH` **[V Monster overrides]**. No ambient sound (no voice lines in M1).
- Not in `#oldrepublic:push_resistant`.
- Pickup loot off: `setCanPickUpLoot(false)` **[V]**.

## 4. Blaster bolt

`BlasterBolt extends ThrowableProjectile implements DeflectableProjectile` **[V both]**.

- Constructors: `(EntityType<? extends BlasterBolt>, Level)` for the type factory **[V ThrowableProjectile ctor]**; `(Level, LivingEntity shooter)` sets position at the muzzle and `setOwner(shooter)` **[V]**.
- Physics: `getDefaultGravity()` returns 0 **[V protected]**; `getAirDrag()` returns 1.0 **[V protected]**, so speed is constant. Water drag: if `ThrowableProjectile#tick` uses a separate water inertia, override or use `setNoGravity(true)` + custom tick **[U: body ThrowableProjectile#tick]**. Speed 1.8 blocks/tick (36 m/s): fast but the 0.6 s tell gives the player time to guard.
- Hit detection is the vanilla one (`ProjectileUtil.getHitResultOnMoveVector` inside `ThrowableProjectile#tick` -> `hitTargetOrDeflectSelf` -> `onHit` -> `onHitEntity`/`onHitBlock` **[V names, U flow]**).
- Lifetime: `tick()` calls super, then on the server discards after `lifetimeTicks` (60) **[V discard is standard; U name in full Entity dump]**. No impact effect on timeout (bolt just fades out).
- `canHitEntity(Entity)` **[V protected]**: super (which skips the owner until it has left it **[U: checkLeftOwner semantics]**) and, while the owner is a `CrystalPoacher`, never other poachers (no friendly fire in the camp). Other bolts are not hit.
- `onHitEntity(EntityHitResult)` **[V]**: if the target is a `LivingEntity`, call `CombatHooks.onProjectileHit(target, this)` **[V]**. If the result is not `NONE`, do nothing more: the guard has redirected (`redirect`) or removed the bolt. Otherwise `target.hurtServer(serverLevel, damageSources().source(BLASTER_DAMAGE, this, owner), boltDamage)` **[V hurtServer, DamageSources#source]** (custom type approved in Phase 2, see section 10), send the `bolt_impact` effect (variant 1 = flesh: sparks only, no scorch), `discard()`. Non-living entities (boats, item frames, end crystals) take the same damage call without the guard check.
- `onHitBlock(BlockHitResult)` **[V]**: effect `bolt_impact` with `dir` = hit face normal (`getDirection()` **[U, standard]**), variant 0 (block: sparks + scorch mark particle that lingers 2 s), `discard()`. Blocks are never broken.
- Impact sound: played client-side in the effect handler (`blaster.impact`), so it exists once per impact.
- Effect send: `EffectEvents.around(serverLevel, BOLT_IMPACT, pos, dir, -1, 1f, variant)` **[V]**.
- `DeflectableProjectile`:
  - `deflectableVelocity()` -> `getDeltaMovement()` **[V standard]**.
  - `deflectableShooter()` -> `getOwner()` **[V]**.
  - `redirect(Vec3 newVelocity, Entity newOwner, boolean parried)`: `setDeltaMovement(newVelocity)`, `setOwner(newOwner)` **[V]** (so the original shooter is now hittable and `checkLeftOwner` starts over for the deflector), reset `age` to give the return trip a full `lifetimeTicks`, `updateRotation()` **[V protected]**, mark `hurtMarked = true` / `needsSync` style flag **[U: name; compare with body of Projectile#deflect]** so the new velocity is sent this tick; set synced `DATA_DEFLECTED` (Boolean) so the renderer can brighten the core slightly. The saber worker plays deflect/parry sounds and calls `QuestHooks.onBoltDeflected`.
- Damage credit after a parry: the new owner is the player, so a poacher killed by its own returned bolt credits the player (loot, quest).

### 4.1 Flyby

Client-side (`EnemyEffectsClient`, `ClientTickEvents.END_CLIENT_TICK` **[V]**): for each `BlasterBolt` within 6 blocks of the local player (`level.getEntitiesOfClass(BlasterBolt.class, player.getBoundingBox().inflate(6))` **[U: method on Level, standard]**) that the player does not own and that has not yet played its flyby (set of entity ids, pruned when bolts are gone): when the closest approach of its current segment (previous to current position) is within 3 blocks of the player's eye, play `ModSounds.BLASTER_BOLT_FLYBY` with `level.playLocalSound(x, y, z, sound, SoundSource.HOSTILE, 0.8f, pitch, false)` **[V ClientLevel#playLocalSound]**. The owner id on the client comes from the add-entity packet (`Projectile#recreateFromPacket` **[V]**).

## 5. Client rendering

### 5.1 Poacher

- `CrystalPoacherRenderer extends HumanoidMobRenderer<CrystalPoacher, PoacherRenderState, PoacherModel>` **[V class and generics]**; ctor `super(ctx, new PoacherModel(ctx.bakeLayer(LAYER)), 0.5f)` **[V ctor (Context, M, float), Context#bakeLayer]**.
  `HumanoidMobRenderer` is expected to add `ItemInHandLayer` itself **[U: body of ctor]**; if not, `addLayer(new ItemInHandLayer<>(this))` **[V addLayer protected]**.
- `PoacherRenderState extends HumanoidRenderState` **[V]** with `boolean charging; float chargeProgress; boolean aiming`.
- `createRenderState()` **[V]**, `extractRenderState(CrystalPoacher, PoacherRenderState, float)` **[V]** copies the synced flags; `chargeProgress` comes from a client-side counter kept on the entity (client field incremented in `tick()` while `DATA_CHARGING` is true, reset when false).
- `getTextureLocation(PoacherRenderState)` **[V abstract]** -> `oldrepublic:textures/entity/crystal_poacher.png`.
- Aiming pose: override `getArmPose(CrystalPoacher, HumanoidArm)` **[V protected]** to return `HumanoidModel.ArmPose.CROSSBOW_HOLD` (two-handed aim with a long gun) for the main arm while `aiming` and `ArmPose.ITEM` otherwise **[U: enum constants; probe HumanoidModel$ArmPose and how setupAnim reads them from ArmedEntityRenderState]**. While charging the arms are raised fully (CROSSBOW_HOLD or BOW_AND_ARROW, whichever reads better with the rifle model).
- `PoacherModel extends HumanoidModel<PoacherRenderState>` **[V ctor HumanoidModel(ModelPart)]**. Mesh: `HumanoidModel.createMesh(CubeDeformation.NONE, 0f)` **[V]** plus extra parts for the 64x64 layout: a jacket/scavenged chest plate overlay on `body`, shoulder plate on the left arm, goggles strap on `hat`, tool belt with a cutter. `LayerDefinition.create(mesh, 64, 64)` **[V, Fabric ArmorRenderingTests]**. (If `PlayerModel.createMesh` is available it gives the standard 64x64 overlay parts for free; probe `find:client/model/.*Player.*`.)
- `MuzzleGlowLayer extends RenderLayer<PoacherRenderState, PoacherModel>` **[U: RenderLayer submit signature]**: when `charging`, `getParentModel().translateToHand(state, arm, poseStack)` **[V HumanoidModel#translateToHand(HumanoidRenderState, HumanoidArm, PoseStack)]**, offset to the rifle muzzle (matches the item model's barrel end in `thirdperson_righthand`), then `submitCustomGeometry(poseStack, RenderTypes.<glow type>, (pose, buffer) -> ...)` **[V]**: two crossed camera-independent quads plus a small core, size and alpha growing with `chargeProgress` (pulsing in the last 4 ticks), light `LightCoordsUtil.FULL_BRIGHT` **[U constant name]**.

### 5.2 Bolt

- `BlasterBoltRenderer extends EntityRenderer<BlasterBolt, BlasterBoltRenderState>` **[V]**; `BlasterBoltRenderState extends EntityRenderState` **[V]** with `float yRot, xRot; boolean deflected; float age`.
- `extractRenderState` copies `getYRot(partialTick)`/`getXRot(partialTick)` **[U: partial-tick getters on Entity]**, age, deflected.
- `getBlockLightLevel(T, BlockPos)` **[V protected]** returns 15 (as `ThrownItemRenderer` does), so the shadow/light is never dark.
- `submit(state, poseStack, collector, camera)` **[V]**: rotate with `Axis.YP.rotationDegrees(yRot - 90)` and `Axis.ZP.rotationDegrees(xRot)` (arrow convention) **[U: Axis, confirm with body:ArrowRenderer#submit]**, then two `submitCustomGeometry` calls **[V]**:
  1. Core: an elongated box (length 0.9, cross-section 0.05) near-white red `#FFD8D0`, alpha 1.
  2. Glow: a larger box (length 1.3, cross-section 0.16) red `#FF2A1A`, alpha 0.45, tapering tail quads behind (the "short fading trail").
  Render types: candidates `RenderTypes.entityTranslucentEmissive(tex)` **[V exists]** (textured, translucent, emissive), `RenderTypes.eyes(tex)` **[V exists]** (additive, always bright), `RenderTypes.lightning()` **[V exists]** (untextured position-color, additive). Preferred: `lightning()` for the glow (additive halo, no texture needed) and `entityTranslucentEmissive` with a small white texture `textures/entity/blaster_bolt.png` for the core; final choice after reading the RenderTypes bodies (vertex formats) and `LightningBoltRenderer#submit`. Vertex writes use `VertexConsumer.addVertex(PoseStack.Pose, x, y, z).setColor(int).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz)` **[V all methods]**; for a position-color type only `addVertex` + `setColor`.
- Shadow: none (`shadowRadius = 0` **[V field]**).

### 5.3 Particles and effect handlers

- `muzzle_glow`: small additive red-orange flare, 6-10 ticks, shrinking; spawned by the client tick each tick (1-2, `Vfx.count(2)`) while a visible poacher is charging (from `DATA_CHARGING`), and as a larger 3-tick flash on `blaster_fire`.
- `bolt_impact`: bright spark streaks flying out of the surface normal, 4-8 ticks, slight gravity (`Vfx.count(10)`).
- `bolt_scorch`: dark smoke wisp + faint ember that lingers ~40 ticks at block impacts (`Vfx.count(3)`).
- Handlers registered with `ClientEffects.register(id, handler)` **[V]**; every handler begins with `Vfx.inRange(pos)` **[V]**; tells always spawn at least one particle (`Vfx.count` keeps a minimum of 1). No flashing-sensitive full-screen effects.
- Particle classes: `SingleQuadParticle` subclasses with a `SpriteSet` **[V classes exist; U ctor/`getLayer` signatures]**; `particles/<id>.json` sprite lists.
- Charge whine: on the client, when a poacher's `DATA_CHARGING` goes false -> true (tracked per entity id in the client tick, or `onSyncedDataUpdated` **[V]** setting a client-only field read by the tick), play `EntityBoundSoundInstance(ModSounds.BLASTER_CHARGE, SoundSource.HOSTILE, 1, 1, entity, seed)` **[U ctor]** through `Minecraft.getInstance().getSoundManager().play(...)` **[U]**; when the flag drops before the sound ends (cancelled shot, death, unload) the instance is stopped (`SoundManager#stop(SoundInstance)` **[U]**). The fire sound covers the end of the whine. Doing it client-side keeps the tell exactly aligned with the visible glow and allows stopping it.

## 6. Loot and bad-luck protection

- `data/oldrepublic/loot_table/entities/crystal_poacher.json` (`"type": "minecraft:entity"`, `random_sequence` set):
  - Pool 1: iron nuggets 1-4 (credits placeholder), `minecraft:enchanted_count_increase` with looting (format from vanilla zombie.json, probe).
  - Pool 2: `minecraft:killed_by_player` + `minecraft:random_chance_with_enchanted_bonus` 0.08 (+0.02 per looting level): one of `oldrepublic:grip_wrapped`, `grip_ridged`, `pommel_weighted`, `pommel_balanced` (equal weights). Item ids are referenced by name; they are registered by the saber worker.
- Code-side (`PoacherLoot`, called from `CrystalPoacher#die(DamageSource)` **[V]** override on the server, after super):
  1. Killer = `source.getEntity()` if a `ServerPlayer`, else `getLastHurtByPlayer()` **[V]** (credits kills by redirected bolts and fire/fall after a hit).
  2. `ForceData data = Progression.get(player)` **[V]**; `data.counters.merge("poacher_kills", 1, Integer::sum)`.
  3. If `!data.hasFlag("got_emitter")`: drop an emitter when `kills >= emitterGuaranteeKill` (4) or with `emitterChance` (0.12) before that. The item is chosen at random from `oldrepublic:emitter_guardian` / `oldrepublic:emitter_duelist`, looked up with `BuiltInRegistries.ITEM.getOptional(Identifier)` **[U: method name on DefaultedRegistry]**; if the saber items are missing nothing drops and nothing is flagged. Dropped with `spawnAtLocation(serverLevel, stack)` **[V]**, then `data.flags.add("got_emitter")`.
  4. `Progression.commit(player, data)` **[V]**, then `QuestHooks.onPoacherKilled(player)` **[V]**.
  The emitter drop respects the `doMobLoot` game rule (`shouldDropLoot` **[V Monster]** / game rule check) but the counter and quest hook do not.
  Note for the saber/world workers: the hidden chamber chest can also give an emitter. If that should satisfy bad-luck protection, they set flag `got_emitter` too (or the lead decides it should not).

## 7. Balance (`EnemyBalance`, section `enemy`)

| Field | Default | Meaning |
|---|---|---|
| `maxHealth` | 24 | poacher health |
| `armor` | 2 | |
| `movementSpeed` | 0.27 | |
| `followRange` | 32 | |
| `meleeDamage` | 3 | rifle-butt bash |
| `minRange` / `maxRange` | 8 / 14 | preferred band |
| `retreatRange` | 5 | sprint away below this |
| `chargeTicks` | 12 | tell before each shot (0.6 s) |
| `burstMin` / `burstMax` | 2 / 3 | shots per burst |
| `recoveryMinTicks` / `recoveryMaxTicks` | 40 / 60 | pause after a burst |
| `boltDamage` | 5 | |
| `boltSpeed` | 1.8 | blocks per tick |
| `boltLifetimeTicks` | 60 | 3 s |
| `aimSpreadDegrees` | 2.5 | base inaccuracy (x1.5 easy, x1 normal, x0.6 hard) |
| `coverHealthFraction` | 0.4 | |
| `coverHoldMinTicks` / `coverHoldMaxTicks` | 40 / 60 | |
| `coverCooldownTicks` | 200 | |
| `emitterGuaranteeKill` | 4 | |
| `emitterChance` | 0.12 | per kill before the guarantee |
| `naturalSpawns` | false | rare underground spawns outside camps |

## 8. Assets (owned)

- Textures from `tools/assets/gen_textures_enemy.py`: `textures/entity/crystal_poacher.png` (64x64, PoacherModel UVs), `textures/item/blaster_rifle.png` (GUI sprite), `textures/item/blaster_rifle_model.png` (swatches for the 3D model), `textures/item/crystal_poacher_spawn_egg.png`, `textures/particle/muzzle_glow_0..1.png`, `bolt_impact_0..1.png`, `bolt_scorch_0..2.png`. The bolt has no texture: it is drawn as untextured glow geometry.
- `items/blaster_rifle.json` selects on `minecraft:display_context` like the vanilla spyglass: flat `item/blaster_rifle` in gui/ground/fixed/on_shelf, 3D `item/blaster_rifle_in_hand` elsewhere (barrel along -Z, muzzle at model (8, 9.75, -11), third-person scale 0.6, so the muzzle sits at (0, 0.0675, -0.7125) in the item frame, which `MuzzleGlowLayer` uses). `items/crystal_poacher_spawn_egg.json` + `item/generated` model, `particles/*.json`.
- `src/main/lang-parts/enemy.json`: entity and item names plus the `death.attack.oldrepublic.blaster` messages. Subtitles stay in the audio part.
- Listed with artist briefs in `docs/assets/enemy.md`.

## 9. Save/load, death, multiplayer

- Poacher state that matters across saves: persistence flag (vanilla), equipment (vanilla). The shot cycle and cover state are transient and restart cleanly; `DATA_CHARGING` is false on load (default value, never saved).
- Bolts are not saved (`noSave()`).
- Kill counter and `got_emitter` live in `ForceData` (saved and copied by the foundation on respawn). A hidden-chest emitter does not set `got_emitter`.
- Multiplayer: tells are synced entity data (everyone tracking sees and hears them), impacts and muzzle flashes are one effect event each, flyby is local per player.
- Charge whine stops on cancel, death and unload: the client keeps one sound per charging poacher and stops it the first tick that poacher is no longer charging (or no longer in `entitiesForRendering()`).

## 10. Implementation notes (Phase 2)

- **Burst gap.** Between the shots of one burst the goal spends `burstGapTicks` (default 4, minimum 2) with the tell flag off. Flipping the synced flag off and on in the same tick would never reach clients, so every shot would not get its own tell.
- **Spawn rule.** `checkPoacherSpawnRules`: natural spawns inside an `#oldrepublic:crystal_caves` structure piece use `checkAnyLightMonsterSpawnRules`, so a lit camp can still fill from its `spawn_overrides`. Elsewhere, monster rules; natural spawns also need `naturalSpawns` (default false), y below sea level - 8, and 12 blocks below the surface. The biome modifier is always registered and gated by that rule.
- **Damage type.** Bolts deal `oldrepublic:blaster` (`data/oldrepublic/damage_type/blaster.json`, in `#minecraft:is_projectile`), direct entity = bolt, causing entity = owner.
- **Bolt rendering.** Three nested additive boxes (`RenderTypes.lightning()`, POSITION_COLOR) along the flight direction; a deflected bolt has a whiter core. No texture.
- **Balance is server side.** Bolt damage and lifetime are fixed when the bolt is created; attributes are re-applied on each poacher's first server tick (so a balance edit applies to loaded poachers after a reload).
- **Redirect.** After `redirect` the new owner is immune for 4 ticks so the bolt cannot hit its deflector; the old shooter can be hit at once.
- **Mixins.** None are needed; `oldrepublic-enemy.mixins.json` stays empty.

---

# Milestone 2: Sith Acolyte and Poacher Captain

Spec: `docs/MILESTONE_2.md` 9.7. Balance sections `sith_acolyte` (`AcolyteBalance`) and `poacher_captain`
(`CaptainBalance`). Neither spawns naturally: W2's encounters and `/oldrepublic enemy spawn` create them.

## 11. Content and ids

| Id | What |
|---|---|
| `oldrepublic:sith_acolyte` | Duelist with a red saber, skins `acolyte`, `brenn`, `deserter`; modes `HOSTILE` and `RIVAL` (Brenn). `SaberDuelist`, `Yieldable`. |
| `oldrepublic:poacher_captain` | Raider camp elite: vibroblade and blaster pistol. `Yieldable`, `PoacherCrew`. |
| `oldrepublic:vibroblade`, `oldrepublic:blaster_pistol` | Render-only held items (no recipe, not in the creative tab). |
| `oldrepublic:sith_acolyte_spawn_egg`, `oldrepublic:poacher_captain_spawn_egg` | Spawn eggs. |
| `tags/entity_type/push_resistant` | Adds both new entities (Push resistance in W4's Push). |
| `loot_table/entities/sith_acolyte`, `poacher_captain` | 1-3 / 2-5 iron nuggets (+looting), and a 10% / 15% player-kill chance of one hilt part. |

## 12. Sith Acolyte (9.7.1)

- 40 health, 4 armour, 6 damage, knockback resistance 0.3. Guard stamina 60 (regen 10/s after 30 ticks), parry chance
  0.35. Rival profile: guard 70, yields at 15% (trial) or 40% (lesson). Uses `DuelistCombatGoal` (strings of 2-3
  swings, first wind-up 10 ticks, gap 7, recovery 30) through `AcolyteCombatGoal`.
- **Force Shove:** cooldown 200, 16-tick wind-up (hand forward, `acolyte_shove` variant 0, `acolyte.shove_windup`), then
  a 50 degree cone within 6 blocks: strength 1.1, lift 0.3, 2 damage. A Push during the wind-up clashes
  (`acolyte_shove_clash`) and staggers the acolyte for 30 ticks; stagger or shock also cancels it. `startShove()` lets
  W2's scripts trigger it directly.
- **Yielding (W1):** `setYielded(true)` kneels, lowers the blade and stops the goals. `onYieldResolved`: anything but
  `EXECUTED` makes it flee for 200 ticks and then despawn. W1 plays the yield sounds.
- Rival mode: neutral and owned (`setOwner`), fights only inside a spar with its owner.
- Never targets `oldrepublic:frontier_settler` (`canAttack` and `setTarget` overrides; the same for the poachers and the
  Captain).

## 13. Poacher Captain (9.7.2)

- 36 health, 5 armour, knockback resistance 0.4. `CaptainCombatGoal`: telegraphed vibroblade swings (6 damage, 12-tick
  tell); an overhead heavy (16-tick wind-up, `captain_heavy`, `captain.heavy_windup`, 9 damage, guard drain x2,
  cooldown 100; a parry staggers her for 40 ticks); a single telegraphed pistol bolt (4 damage, cooldown 40) between 4
  and 12 blocks unless disarmed. Every attack has a tell; stagger, shock or disarm cancels it.
- Yields at 25% health to a player's blow (W1's `Yielding` clamps the damage). `setEncounter` and `setOwner` link her
  to W2's raider camp.
- Crew members' bolts never hit each other.

## 14. Client

- `SithAcolyteRenderer` / `AcolyteModel`: humanoid with robe, sleeves, skirt and sash overlays; saber via the item
  layer; the shove arm pose; the kneel (`KneelPose`).
- `PoacherCaptainRenderer` / `CaptainModel`: coat, sleeves, coat tails and hat brim; pistol aim, heavy wind-up and
  kneel poses; `PistolGlowLayer` draws the muzzle glow during the pistol tell.
- `EnemyTellFx`: `acolyte_shove` (dust and dark ripples at the palm), `acolyte_shove_clash`, `captain_heavy`.
- `CrystalPoacherRenderer` lowers the rifle while the poacher is disarmed by W4's Disarming Pull.

## 15. Emitter bad-luck protection follow-up

`PoacherLoot.onKilled` first calls `reconsiderEmitterFlag`, which clears the "emitter already dropped" flag when the
killer carries neither an emitter nor a lightsaber, so a lost emitter can drop again. The protected emitter now drops
at the killer's feet with an unlimited lifetime.

## 16. Commands and tests

`/oldrepublic enemy spawn <acolyte|captain|poacher> [hostile|rival] [acolyte|brenn|deserter]`. `EnemyGameTests`: Push during
the shove wind-up clashes, the Captain yields at a quarter of her health, a lost emitter can drop again.
