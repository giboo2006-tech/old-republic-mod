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

# Milestone 3: Republic and Imperial troopers

Spec: `docs/MILESTONE_3.md` 5.2 (attitudes), 5.4 (downed troopers, escorts), 7.1 to 7.6 and 7.8, 12, 13
(enemy rows), 14 (`trooper`), 16 and 17.1. Balance section `trooper` (`enemy.trooper.TrooperBalance`). This
chapter covers the trooper simulation on the server: the entity, its AI and roles, attitudes, the cold peace,
incidents, squads, officers, downed troopers, escorts, yields, barks, the poacher changes and the commands.
The client presentation (models, skins, poses, bolt colours, every effect handler, `BarkHud`, the particles),
the frag grenade entity and the natural patrol spawner are W4 part B's; their sections follow this chapter.

Status legend as above: **[V]** verified against the 26.2 dumps or a compiling use in the repo, **[U]** carries an
`// API-CHECK:` comment.

## 17. Content and ids

| Kind | Id | Class or file |
|---|---|---|
| Entity (MISC) | `oldrepublic:republic_trooper` (Allegiance `REPUBLIC`, Faction `REPUBLIC`) | `enemy.trooper.TrooperEntity` |
| Entity (MISC) | `oldrepublic:sith_trooper` (Allegiance `EMPIRE`, Faction `SITH_EMPIRE`) | the same class |
| Entity (projectile) | `oldrepublic:frag_grenade` (registered here, built by part B) | `enemy.trooper.FragGrenade` |
| Items | `republic_trooper_spawn_egg`, `sith_trooper_spawn_egg` (`SpawnEggItem`, `Properties#spawnEgg` **[V]**) | `EnemyItems` |
| Loot tables | `entities/republic_trooper`, `entities/sith_trooper`: `{"type":"minecraft:entity","pools":[]}` | data |
| Effect events (server side) | `trooper_charge`, `trooper_vent`, `kolto_heal`, `trooper_tend`, `trooper_rally`, `flare_call`, `grenade_tell`, `trooper_bark`, `trooper_salute`, and the existing `blaster_fire` with the style variant | `enemy.trooper.TrooperEffects`, `EnemyEffects` |
| Commands | `/oldrepublic trooper spawn|squad|downed|act` | `enemy.trooper.TrooperCommands` |
| Balance | section `trooper` | `enemy.trooper.TrooperBalance` |

Server classes in `com.oldrepublic.enemy.trooper`:

| Class | What it does |
|---|---|
| `TrooperEntity` | The mob: synced role, action, downed, disarmed and yielded flags; saved state; setup; damage, death and drops; use (officers, tending); `Yieldable`. |
| `Troopers` | The frozen spawn and query API (`spawnSquad`, `spawnOfficer`, `spawnDowned`, `spawnEscort`, `tend`, ...) and the W4 additions (`defaultSquad`, `startPatrol`, `bindTerminal`, `bleedOut`, `setYield`, `registerDormancyCheck`, `registerBoundCheck`, `fightsOnSight`). |
| `TrooperRole` (frozen), `TrooperAction`, `BarkCategory` | Roles; the synced pose (append only); the bark categories (variant = category x 4 + line). |
| `TrooperRules` | Pure rules: role normalisation, compositions, role stats, incident expiry and merging, overflow-safe expiries, bark limits, the deflection answer, morale, the rallied cooldown, interrupts, death drops. |
| `TrooperAttitudeRule` | The `Attitudes.Rule` for troopers: the tier table, Peaceful cap, engaged and provoked. |
| `ColdPeace` | Pure `shouldEngage` and the `standoffStep` state machine. |
| `PlayerExpiries` | A player UUID to expiry map of at most 4 entries with its codec (`Provoked`, `Engaged`). |
| `Squads` | Squad lookups by squad UUID, the leader, shared targets, and the in-memory squad state (bark clock, Wary countdowns, greetings, standoff, flare, fire-sound window). |
| `TrooperSocial` | The leader's out-of-combat rules every 5 ticks: Wary warning and countdown, Neutral "move along", Allied salute, standoff. |
| `TrooperEvents` | Listeners: damage (provocation, ally defence), `CombatEvents.POWER_HIT`, `ALLOW_LOAD` (bleed out, duplicates at a post), server tick (reinforcements, pruning), server stop. |
| `Barks` | Picks the line and sends `trooper_bark` within the rate limits. |
| `Reinforcements` | The flare's scheduled arrivals: placement out of sight on the far side at the sergeant's level, then two riflemen run in; they walk off when the encounter ends. |
| `TrooperSight` | "Can any player see this point?" for despawns out of sight and hidden arrivals. |
| `TrooperPost` | Interface W1's bounty terminal implements: `memberAt(slot)`. |
| `TrooperCommands` | `/oldrepublic trooper ...`. |
| `TrooperBalance`, `TrooperEffects` | Balance keys; effect ids. |
| `goal/*` | `TrooperTargetGoal`, `TrooperShootGoal`, `TrooperSpecialGoal`, `TrooperHoldGoal`, `TrooperEscortGoal`, `TrooperLeaveGoal`, `TrooperMoraleGoal`, `TrooperStandoffGoal`, `TrooperRouteGoal`, `TrooperFollowGoal`, and the `TrooperPaths` helper. |

Registration (`EnemyEntities`): both trooper types use `EntityType.Builder.of(TrooperEntity::new, MobCategory.MISC)`,
`sized(0.6, 1.95)`, `eyeHeight(1.62)`, `clientTrackingRange(10)` and `FabricDefaultAttributeRegistry.register(...,
TrooperEntity.createAttributes())`. They have **no spawn placement**: troopers never spawn through vanilla. The grenade
type is `sized(0.25, 0.25)`, `clientTrackingRange(8)`, `noSave()`. `EnemyFeature.init()` follows the plan's order:
types, items and particles; `Attitudes.register(new TrooperAttitudeRule())` before any other W4 rule;
`ForceReactions.register(new EnemyForceReactions())`; `TrooperEvents`, `EnemyCommands`, `TrooperCommands`;
`PatrolSpawner.init()`; the poacher biome spawn.

## 18. The trooper entity (7.1)

- **Not a monster.** `TrooperEntity extends PathfinderMob implements Aligned, Yieldable`, never `Monster` or `Enemy`.
  So no `notInPeaceful` (the type is allowed in Peaceful), and a bed's monster check never sees a trooper, however
  hostile its squad (`troopersNeverBlockSleep`). Hostility comes only from `Attitudes` (section 19).
- **Never despawned by vanilla.** `removeWhenFarAway` is always false. Spawner troopers (`spawnSquad`, `spawnOfficer`,
  `spawnDowned`, terminal-bound, structure, command and egg spawns) call `setPersistenceRequired()`. Patrol members,
  escorts and troopers walking off return false from `shouldBeSaved()`, so they vanish with their chunk and never
  come back from a save.
- **Synced state:** `DATA_ROLE` (role ordinal), `DATA_ACTION` (`TrooperAction` ordinal), `DATA_DOWNED`,
  `DATA_DISARMED` (copied every AI tick from `ForceStatus.isDisarmed`) and `DATA_YIELDED`. `actionTicks()` counts
  from the last action change on both sides (the client resets it in `onSyncedDataUpdated`). Everything the renderer
  needs comes from these.
- **Setup.** On the first server tick (or at spawn through `Troopers`), the role's {health, armour} come from
  `TrooperRules.stats` (balance arrays per army and role; officers a fixed 20/0), speed and follow range from
  balance, and the weapon goes in the main hand with drop chance 0: rifleman and sergeant `blaster_rifle`, medic and
  grenadier `blaster_pistol`, officer nothing. Loot pick-up is off.
- **Roles.** `setRole` normalises (a Republic GRENADIER and an Imperial MEDIC become RIFLEMAN) and marks the role
  fixed. A role read from saved data is fixed too, so `finalizeSpawn` with `EntitySpawnReason.STRUCTURE` never
  re-rolls it (`structureSpawnKeepsRole`). A trooper without a fixed role (an egg) draws one from its army's patrol
  composition once.
- **Friendly fire.** Trooper bolts are `BlasterBolt.create(..., boltStyle(), false)`: `REPUBLIC` (blue-white) or
  `IMPERIAL` (crimson), never heavy. `BlasterBolt.canHitEntity` (Phase 0) skips `Allegiances.friendly` targets and
  players the shooter is ALLIED with; `boltStyleSyncedAndAllyPass` shows both.
- **Damage.** Officers and downed troopers take only damage in `#bypasses_invulnerability` (`/kill`, the void). A hit
  of `interruptDamage` or more during the medic's channel breaks it.
- **Death.** `die` marks the squad when the trooper fell in a fight (a living killer, or the trooper or a squad
  fighter had a target): a member fell, so the Imperial flare is now possible. Then, when a player killed it, it reports `QuestHooks.onTrooperKilled(killer, trooper, army, role, Attitudes.hostileSnapshot(trooper, killer))`.
  W2 applies the reputation and the lead the kill XP through the same snapshot. A player whose kill was not hostile
  has struck the squad first: the rest are provoked.
- **Drops.** Only a player's kill drops anything: one `durasteel_plate` at `durasteelChance` through the pure
  `TrooperRules.deathDrops(random, byPlayer, balance)` in `dropCustomDeathLoot`. Never armour, never commendations.
  The loot tables stay empty so datapacks can add to them, and the balance key applies.
- **Sounds:** `trooper.hurt` and `trooper.death` through `getHurtSound`/`getDeathSound`, sound source HOSTILE. Every
  other trooper sound is played by part B's client handlers from the effect events (section 27).
- **Force.** Troopers are not push-resistant. `EnemyForceReactions.interruptWindup(target, caster, cause)` calls
  `interruptTell()`: a stagger, Push, Pull, Disarming Pull or shock cancels the charge before a shot, the kolto
  channel, the Rally fist, the flare and the grenade arm. Disarming Pull's 120 ticks without firing come from
  `ForceStatus.disarm`, which the shoot and special goals read every tick; the weapon is hidden while disarmed.
- **Deflection answer.** `BlasterBolt.redirect` tells a trooper owner its bolt was deflected. A rifleman with
  `deflectCount` (2) deflections within `deflectWindowTicks` (100) holds fire `holdFireTicks` (40), sidesteps 4 to 8
  blocks across the line of fire and barks `flank`.
- **Cover.** `PoacherCoverGoal` now takes any `PathfinderMob` with a settings supplier, a "may take cover" predicate
  and a callback that drops the current tell. Riflemen below `coverHealthFraction` (50%) break line of sight for 40 to
  60 ticks, then wait `coverCooldownTicks`. Poachers keep their M1 behaviour and numbers.

## 19. Attitudes (5.2)

`TrooperAttitudeRule` answers for troopers only (never for vanilla mobs, which the contract tests use as "no rule"):

| Reputation tier with the trooper's army | Attitude | What the squad does |
|---|---|---|
| HOSTILE | HOSTILE | Attacks on sight within `hostileRange` (24). Kills count as hostile. |
| DISTRUSTED | WARY | The leader warns within `warnRange` (16), at most every `warnRepeatTicks`. After the warning, `waryTicks` (120) within `waryRange` (8), or a lit saber that close, and the whole squad is **engaged** (fires first: hostile for `engagedTicks`). |
| NEUTRAL | NEUTRAL | Ignores. "Move along." (WARN line 2) to a player with a lit saber within `moveAlongRange` (4). |
| TRUSTED, HONOURED | ALLIED | The nearest member salutes within `saluteRange` (8) out of combat; bolts pass through the player; the squad defends them. |

- Only the trooper's own army's reputation counts (`Identity.tier(p, army)`); Imperial troopers read `sith_empire`.
- **Peaceful** caps every tier at NEUTRAL (ALLIED stays ALLIED), and on Peaceful `engaged` and `provokedBy` are false
  and `wants(player)` is false: troopers never attack players. The test checks the pure rule
  (`TrooperAttitudeRule.attitude(tier, true)`), never the world difficulty.
- **Provoked.** A hit by a player the trooper does not count as hostile (`AFTER_DAMAGE`), or an aimed power on it
  (`CombatEvents.POWER_HIT`), provokes the whole squad for `provokedTicks`: it fights back, but the fight still counts
  as not hostile (no kill XP, no reputation loss as a hostile kill). `shieldedFromArea` turns false for that player.
- **Engaged.** Set by the Wary countdown and by the ally defence against another player; also implied for the owner
  of an `ARMY_AND_OWNER` squad. Engaged fights count as hostile.
- **Ally defence.** When a player takes (or blocks) a hit, every standing trooper within `allyDefendRange` (16) that is
  ALLIED to them turns on the attacker, once per squad: another player becomes engaged with the squad; a trooper of
  the other army starts an `ARMY` incident on **both** squads for `incidentTicks` (1,200) and the defenders target
  the attacker; anything else is simply fought for `engagedTicks`. A trooper never turns on its own army
  (`TrooperEvents.defendAlly`, public so the tests drive it without hurting a mock player).
- **Other targets** (`TrooperEntity.wants`): crew poachers (`Allegiance.CREW`) on sight; ANCIENT and ARCHIVE
  constructs on sight when bound (`Troopers.fightsOnSight`: a check W5 registers with `Troopers.registerBoundCheck`
  says so; bound sentinels are always hostile, 5.2), and otherwise only while awake (`Troopers.awake`: no registered
  dormancy check says dormant, and the mob has a target or is aggressive); the other army only when `ColdPeace.shouldEngage` allows it; whatever hurt the
  trooper (200 ticks) or the player it defends. Never `FrontierSettler`s (`wants`, `canAttack` and `setTarget` all
  refuse them, `neverAttackSettlers`), never friendly entities, never anything that cannot be seen as an enemy
  (creative and spectator players, downed troopers, officers).
- Expiries are game times through `TrooperRules.expiry` (overflow-safe); each list keeps at most 4 players, dropping
  the one that expires soonest.

## 20. Cold peace, incidents and standoffs (7.5)

- `ColdPeace.shouldEngage(a, b, coldPeace)`: without the cold peace always; with it, only when either side carries
  an incident. `coldPeace` is a balance key (default true).
- Incidents: `IncidentMode.ARMY` (the squad fights the other army, never players) and `ARMY_AND_OWNER` (also its
  owner, whatever their standing). `spawnSquad` gives a never-expiring incident; the ally defence a timed `ARMY` one.
  `addIncident` merges through `TrooperRules.mergeIncident`: the stronger mode and the later expiry win, and an
  expired incident is simply replaced. `incidentMode()` reads NONE once `Until` has passed.
- **Standoff.** The leader of an untagged squad out of combat looks for a standing, untagged trooper of the other
  army within `standoffRange` (16). `ColdPeace.standoffStep` then runs NONE, HALTED (on entry the leader barks the
  STANDOFF line 0: "Treaty line's behind you, Imperial." / "Keep walking, Republic."), WALKING_ON after
  `standoffTicks` (200), and back to NONE once the rival is gone. `TrooperStandoffGoal` stops each member, turns it
  to the rival with the rifle lowered (the STANDOFF pose) and keeps `standoffDistance` (12); walking on, it only keeps
  that distance. Garrisons do the same, as they are squads too. A fight ends it at once.

## 21. Squads, morale and barks (7.5, 11.2)

- **Compositions** (`Troopers.defaultSquad`, pure `TrooperRules.defaultSquad`): sergeant (first, he leads), two
  riflemen and a medic (Republic) or grenadier (Imperial); one more rifleman when the nearest player is level
  `extraRiflemanLevel` (11) or more. The first Imperial rifleman of a squad is its flanker.
- A squad is a fresh UUID per `spawnSquad`, saved as `Squad` with the starting size. Members find each other within 48
  blocks (`Squads.squadOf`). The leader is the standing sergeant, else the standing member with the lowest UUID, so
  every member agrees without messages. Targets are shared with members that have none; members follow the leader
  in a loose fan 3 to 5 blocks apart (`TrooperFollowGoal`).
- **Encounters:** the squad's encounter lasts while a member has a target, and a fall or the flare also marks it
  (`Squads.State.lastCombat`). The leader's social tick (`Squads.encounterTick`) ends it once `encounterEndTicks`
  (600) pass with none of these (`TrooperRules.encounterOver`): the fallen member and the used flare are forgotten, so
  a garrison or mission squad flares again in its next fight, and only after someone falls in that fight.
- **Morale:** once per encounter, when no sergeant stands and at most half the squad stands
  (`TrooperRules.moraleBreaks`), each remaining member falls back `moraleRetreatDistance` (24) from its target for
  `moraleRetreatTicks` (200) without firing (bark `retreat`), then returns.
- **Barks:** `Barks.say` sends `trooper_bark` (`around`, pos = eyes, intensity = `barkRange`, variant =
  `category * 4 + line`) only if `TrooperRules.barkAllowed`: one per trooper per `barkTrooperTicks` (120) and one per
  squad per `barkSquadTicks` (40). Lines: SPOT on a new target and when a Wary squad opens fire, WARN (Wary lines 0-1, Neutral line 2),
  STANDOFF (leader line 0), SALUTE, HURT (one hit in three), RELOAD (vent), FLANK (deflection answer), RALLY,
  GRENADE, REINFORCE, RETREAT and YIELD. Showing and filtering (`ClientConfig.showBarks`, 24 blocks) is part B's
  `BarkHud`.
- **Fire sounds:** `Squads.claimFireSound` gives each squad 3 audible shots per 4-tick window; later shots go out as
  `blaster_fire` intensity 0 (muzzle flash only), which keeps a squad volley under the 13.3 limit.

## 22. Combat AI

Goal priorities (`TrooperEntity.registerGoals`):

| Priority | Goal | Runs when |
|---|---|---|
| 0 | `FloatGoal` | in water |
| 1 | `TrooperHoldGoal` | yielded, bound waiting for an escort, or an officer at its post |
| 1 | `TrooperEscortGoal` | escorts |
| 1 | `TrooperLeaveGoal` | walking off (tended, spared, unclaimed prisoner) |
| 2 | `TrooperMoraleGoal` | falling back |
| 3 | `TrooperSpecialGoal` | grenade, flare, Rally, kolto (section 23) |
| 4 | `PoacherCoverGoal` | riflemen below half health |
| 5 | `TrooperShootGoal` | a target |
| 6 | `TrooperStandoffGoal` | the squad stands off against a rival patrol |
| 7 | `TrooperRouteGoal` | a patrol leader with route points left |
| 8 | `TrooperFollowGoal` | members out of combat |
| 9 to 12 | home restriction, stroll (lone troopers only), look at player, look around | idle |
| target 1 | `TrooperTargetGoal` | picks targets by `wants`: on-sight targets within `hostileRange` with line of sight; provoked, engaged, incident and defended-against targets anywhere within follow range |

`TrooperShootGoal`:
- **Position.** Riflemen and sergeants keep the `bandMin`-`bandMax` band (10 to 18), strafing with a clear line,
  closing in when too far, backing off when rushed, moving to regain a line of fire after 20 ticks without one. The
  Imperial flanker circles to a fixed side, 90 degrees off the squad's line, at a fixed distance in `flankerRange`
  (12 to 16). The medic stays 4 blocks behind the leader, away from the target; the grenadier keeps to its grenade
  range. After an answered deflection, the sidestep.
- **Fire.** Every shot or burst starts with the `chargeTicks` (12) tell: action CHARGING and `trooper_charge`. Losing
  sight for more than 6 ticks, an interrupt, a Disarming Pull or holding fire cancels the shot with no bolt.
  - Republic rifle: a burst of `burstSize` (3) bolts of `republicBoltDamage` (4) every `burstGap` (4) ticks, then
    `burstCooldown` (50, shortened by `rallyFireRate` while rallied); after every `ventAfterBursts` (3) bursts it vents
    for `ventTicks` (40) on top of the gap (VENTING, `trooper_vent`, bark `reload`).
  - Imperial rifle: one slow, hard bolt of `imperialBoltDamage` (7) per `imperialCooldown` (34), not heavy.
  - Pistol (medic, grenadier): `pistolDamage` (4) per `pistolCooldown` (40).
  - Bolts aim at the chest (the eyes when only those are visible), lead a moving target by half the flight time,
    and spread by `aimInaccuracy` (x1.5 Easy, x0.6 Hard). Speeds are W4 keys: `boltSpeed` 1.8, `imperialBoltSpeed` 1.35.
  - A squad opens fire on staggered ticks, not all at once.

## 23. Role specials and their counterplay (7.2, 7.3)

| Role | Special | Tell (action, event) | Effect | Broken by |
|---|---|---|---|---|
| Field medic (Republic) | Kolto: an ally at or below `medicThreshold` (50%) within `medicRange` (16); cooldown `medicCooldown` (160) | walks within 2.5, kneels: HEALING, `kolto_heal` on the patient for `medicChannelTicks` (20) | heals `medicHeal` (8) | a hit of `interruptDamage` (4) or more, Push, Pull, stagger, shock, the patient moving off (4.5), the kill |
| Sergeant (Republic) | Rally: once per `rallyCooldown` (600) while `rallyMinAlive` (2) or more stand | raised fist: RALLY_TELL, `trooper_rally` for `rallyTellTicks` (30) | Resistance I and a `rallyFireRate` (25%) shorter burst gap for `rallyTicks` (200) within `rallyRadius` (12); bark `rally` | stagger, Push, Pull, Disarming Pull, shock, the kill |
| Grenadier (Imperial) | Frag grenade: every `grenadeInterval` (200) at a target 6 to 14 blocks away in sight | arm drawn back: GRENADE_TELL, `grenade_tell` for `grenadeTellTicks` (20); bark `grenade` | `FragGrenade.throwAt(level, grenadier, target)` (part B's entity: fuse, burst, Push and Pull answers) | the same, and Disarming Pull |
| Sergeant (Imperial) | Reinforcements: once per encounter, after any squad member fell | flare pistol raised: FLARE_TELL for `reinforceTellTicks` (30) | `flare_call` (the flare climbs 20 blocks), bark `reinforce`; `reinforceDelayTicks` (200) later `reinforceCount` (2) Imperial riflemen appear 24 to 32 blocks away, on the side away from the threat, out of every player's sight when a spot allows, and run in | the same, and Disarming Pull |

A cancelled special ends its tell without effect and may start again after 100 ticks (the medic after 40). The kill
cancels anything, and pending reinforcements of a squad can be cancelled (`Reinforcements.cancel`).

**Flare arrivals** (`Reinforcements`). Each arrival needs standing room (a sturdy floor, no solid block or liquid at the
feet or head). Under open sky (the sergeant's feet at the `MOTION_BLOCKING_NO_LEAVES` height of its column) it stands
on the surface; under a roof or underground it looks within 8 blocks above and below the sergeant's feet, so the
riflemen join the fight where it is and never appear on the ground above it. On arrival every loaded member, old and
new, gets the same squad size (the size before plus the arrivals), so the morale check counts the same squad for all.
Arrivals of a patrol join it (section 39). The others are never saved: when the squad's encounter ends (section 21,
`encounterEndTicks`), the leader's social tick sends them off (`Reinforcements.dismiss`): they leave the squad, walk
away from the nearest player and are removed out of sight, and the rest of the squad shrinks back by as many. So a
garrison that flares gains nobody for good. The debug command
`trooper act` and the tests call `perform(...)`, which skips cooldowns and combat conditions but never the tell.

## 24. Officers, downed troopers, escorts and yields (7.1, 7.4, 5.4)

- **Officers** (`spawnOfficer(level, pos, army, nameSeed)`): role OFFICER, unarmed, never a combatant, never seen as an
  enemy, invulnerable except to `/kill` and the void, persistent. The custom name is the frozen key
  `npc.oldrepublic.officer.<republic|imperial>.<nameSeed mod 6>` (W2's `quest.json`). Using one opens
  `oldrepublic:officer_republic` or `officer_imperial` through `Dialogues.open(player, id, officer)`. It stands at its
  post (`TrooperHoldGoal`; pushed away, the home restriction walks it back).
- **Terminal binding** (`Troopers.bindTerminal(entity, pos, slot, leash)`, W1): saved as `Terminal`, `setHomeTo(pos,
  leash)` and persistence. As the trooper loads (`ALLOW_LOAD`), and every 100 ticks, a loaded `TrooperPost` block entity at that
  position that lists a different UUID in the slot makes it discard itself (a stale duplicate).
- **Downed troopers** (`spawnDowned(level, pos, army, owner)`): a rifleman at a quarter of its health with
  `DATA_DOWNED`, `setNoAi(true)`, never targetable or hurt (except `/kill` and the void). Tending:
  - sneak-use (`mobInteract` with the player crouching) starts a kneel; each tick the player stays crouched within 3
    blocks counts one kneel tick, and so does every `Troopers.tend(p, downed, false)` call, but at most one per game
    tick whoever drives it; a gap of more than 10 ticks restarts the count;
  - `trooper_tend` variant 0 every 20 kneel ticks; at `missions.tendTicks` (60) or on
    `tend(p, downed, true)` (W3's Heal revive) it stands: at least half health, `trooper_tend` variant 1,
    `QuestHooks.onTrooperTended(p, trooper, army)`, then it walks home to its army's nearest outpost terminal within
    `homeRange` (384) or 32 blocks away from the tender, and is removed out of sight.
  - `Troopers.bleedOut(downed, gameTime)` (W2, when the mission ends) sets `BleedAt`; the trooper is discarded on its
    tick at that time, or as it loads if the time has passed: `ServerEntityEvents.ALLOW_LOAD` discards it and cancels
    the load, so it never enters the level or its tick list. Without the call it
    never bleeds out.
- **Escorts** (`spawnEscort(level, at, army, prisoner, exit)`): two unsaved riflemen without combat goals flank the
  prisoner and walk it to `exit`; a trooper prisoner is bound (BOUND pose). The three are removed together once out
  of every player's sight after a first stretch or at the exit, or after `escortDespawnTicks` (300) regardless; a
  player prisoner is never removed. A bound trooper no escort claims walks off on its own after `escortDespawnTicks`.
- **Yields** (`Yieldable`, the M2 yield flow): only sergeants with a threshold yield: `Troopers.setYield(sergeant, fraction,
  dialogue)` (W2: I2 0.20 `yield_border`, contracts 0.25 `yield_intercept`), or by default the sergeant of an
  `ARMY_AND_OWNER` squad at `interceptYield` (0.25) into `yield_intercept` (`yield_border` and `borderYield` when the
  W2 entity tag `oldrepublic.mission.i2_border_incident` is set). The owner decides. Yielded: it kneels, stops and
  barks `yield`. Resolved: SPARED, INTERROGATED or FLED walk off; HANDED_OVER binds it for W2's escort; EXECUTED is
  dealt by the identity feature.

## 25. Patrol members (7.6)

Part B's `PatrolSpawner` decides when and where; this side gives it `Troopers.startPatrol(squad, route)`: every member
is marked patrol (never saved) and the leader (the sergeant) walks the route point by point in pathfinder-sized
legs (`TrooperRouteGoal`), pausing while the squad fights or stands off; the rest follow. `Troopers.isPatrol` and
`squadId` let the spawner count and despawn whole squads. Reinforcements called by a patrol are patrol members too.

## 26. Existing enemies (7.8)

- `CrystalPoacher` and `PoacherCaptain` fire `Style.POACHER` through the 7-argument `BlasterBolt.create` and send
  `blaster_fire` with the style variant (0); their M1 server sounds are unchanged.
- Both get a `NearestAttackableTargetGoal<>(this, TrooperEntity.class, true)` after the player goal: poachers and
  troopers fight on sight. Downed troopers and officers cannot be seen as enemies, so they are never picked.
- `Poachers.spawnCrew(level, pos, count, owner)`: `count` crystal poachers in a 1.5-block ring (on the spawn point
  when the ring is blocked), `finalizeSpawn(EVENT)`, persistent, tagged `StoryTags.CREW` and, with an owner,
  `StoryTags.setOwner`. Like any monster they are removed in Peaceful.

## 27. Effect events sent by the server

| Id | When | Call | pos / dir / entity / intensity / variant |
|---|---|---|---|
| `trooper_charge` | a shot's tell starts | `toTracking(trooper)` | muzzle / look / trooper / `chargeTicks` / style ordinal |
| `blaster_fire` | each bolt | `around` | muzzle / flight / shooter / 1 sound or 0 flash only / style ordinal |
| `trooper_vent` | the vent starts | `toTracking` | muzzle / - / trooper / `ventTicks` / 0 |
| `kolto_heal` | the channel starts | `toTracking(patient)` | patient chest / - / patient / `medicChannelTicks` / 0 |
| `trooper_tend` | every 20 kneel ticks; standing up | `toTracking(downed)` | downed / - / downed / 1 / 0 tending, 1 stands |
| `trooper_rally` | the Rally tell starts | `toTracking` | sergeant / - / sergeant / `rallyTellTicks` / 0 |
| `flare_call` | the flare fires (end of tell) | `around` | above the head / up / sergeant / 20 / 0 |
| `grenade_tell` | the grenade tell starts | `toTracking` | hand / toward the target / grenadier / `grenadeTellTicks` / 0 |
| `trooper_bark` | a bark passes the limits | `around` | eyes / - / speaker / `barkRange` / category x 4 + line |
| `trooper_salute` | a salute | `toTracking` | trooper / look / trooper / 30 / 0 |

`grenade_fuse`, `grenade_burst` and `grenade_return` are sent by part B's grenade.

## 28. Commands

All under `/oldrepublic trooper` with the operator permission of `/oldrepublic enemy`; player arguments are single
players.
- `spawn <republic|imperial|sith_empire> <rifleman|sergeant|medic|grenadier|officer> <pos>`: one lone, persistent
  trooper without a squad (an officer through `spawnOfficer(..., 0)`).
- `squad <army> <pos> [none|army|army_and_owner] [owner]`: `defaultSquad` sized for the owner's level; the owner
  defaults to the source player.
- `downed <army> <pos> [owner]`: the owner defaults to the source player, else the nearest player, else it fails.
- `act <grenade|flare|rally|vent|heal|salute> <near> [at]`: the nearest trooper within 32 blocks of `near` that can,
  performs it now with its full tell; `at` is the grenade target (default 8 blocks ahead).

## 29. Saved state

Frozen keys: `Role` (role name), `Squad` (UUID), `Incident` {`Mode` ordinal, `Until` long}, `Provoked` and `Engaged`
(lists of {`Player`, `Until`}, at most 4), `BleedAt` (long), `Terminal` {`Pos`, `Slot`}. W4 additions: `Owner`
(UUID), `SquadSize`, `Flanker`, `Yield` {`Fraction`, `Dialogue`}, `Downed` (bool), `NameSeed` (officers), and the
yield state `Yielded`, `YieldResolved` and `Bound` (bools, spec 12.2). All through
`ValueOutput`/`ValueInput` with `UUIDUtil.CODEC`, `BlockPos.CODEC` and `PlayerExpiries.CODEC` **[V]**. A saved downed
trooper reloads downed and without AI. A yielded sergeant reloads yielded (the M2 `Yielding.onEntityLoad` tracks it
again, so the yield is still pending after a relog); a resolved one never yields again; a handed-over prisoner reloads
bound and restarts its `escortDespawnTicks` wait for W2's escort. Transient (never saved): patrol, route, escort,
leaving, morale, rally, vent, cooldowns; patrol members, escorts and leavers are not saved at all. Squad state (bark clock, warnings,
standoff, flare) lives in memory and is cleared on server stop.

## 30. Balance (`trooper`)

Every spec 14 key is a public field read through `TrooperBalance.get()` on the server only: the role {health,
armour} arrays, `speed`, `chargeTicks`, `bandMin`/`bandMax`, the Republic burst keys, the Imperial shot keys,
`flankerRange`, the pistol keys, the medic, Rally, grenade, reinforcement and deflection keys, `durasteelChance`,
the attitude ranges and expiries, the standoff keys, the bark limits, `extraRiflemanLevel`, the morale keys and
`coldPeace`. Troopers read `tendTicks`, `escortDespawnTicks`, `borderYield` and `interceptYield` from the
`missions` section; Force Heal reads `missions.healTendRange`. W4 additions (not in spec 14): `boltSpeed`, `imperialBoltSpeed`,
`boltLifetimeTicks`, `aimInaccuracy`, `followRange`, `rallyRadius`, `rallyMinAlive`, `medicRange`, `squadSpacing`,
the cover keys (`coverHealthFraction`, `coverSpeed`, `coverHoldTicks`, `coverCooldownTicks`), `saluteRange`,
`moveAlongRange`, `homeRange`, `encounterEndTicks` (600: a squad's encounter ends after this long without a fight,
section 21; flare arrivals that are not on patrol walk off after it, section 23). Roles pick up a changed file on their next setup (a new trooper or a reload).

## 31. Tests (`TrooperGameTests`, 17.1)

22 methods, each with `maxTicks` of at most 300, on `oldrepublic_test:arena_24x7x24` or the default structure,
listed mock players (`M3TestPlayers`, removed at the end), seeded randomness and fixed UUIDs, no global state. Long timers go
through pure rules (`TrooperRules`, `ColdPeace`, `PlayerExpiries`) with explicit game times. Mock players never
finish loading and cannot be hurt, so the ally defence is driven through `TrooperEvents.defendAlly`, and a bolt
stopped by a player is seen by the villager behind it staying whole.

`attitudeTableEveryTier`, `oneSquadAlliedToAWaryToB`, `waryWarnsThenEngagesAfter120Ticks`, `neutralHitProvokesSquad`,
`incidentModes`, `allyDefenceStartsIncident`, `peacefulNeverAttacksPlayers`, `neverAttackSettlers`,
`coldPeaceShouldEngageAndStandoffStep`, `provocationAndIncidentExpiry`, `trooperNbtRoundTrip`,
`structureSpawnKeepsRole`, `downedNotTargetableAndBleedsOutOnLoad`, `dropsNeverArmourOrCommendations`,
`boltStyleSyncedAndAllyPass`, `imperialBoltIsNormal`, `medicChannelInterruptedByHit4`,
`rallyAndReinforcementCancelWindows`, `barkRateLimits`, `notRemovedWhenFarAway`, `officersSurvivePeacefulTick`,
`troopersNeverBlockSleep`.

`trooperNbtRoundTrip` covers every saved key, including `Yielded`, `YieldResolved` and `Bound` (a yielded sergeant
reloads yielded; a handed-over one reloads bound, never yields again and never fights).
`rallyAndReinforcementCancelWindows` also checks that the flare marks the squad's encounter and that
`TrooperRules.encounterOver` ends it exactly `encounterEndTicks` after the last fight.

## 32. Integration points and limits

- W1's bounty terminal implements `TrooperPost` and calls `Troopers.bindTerminal` after spawning its officer and
  garrison. W2 calls `Troopers.bleedOut` when a mission with downed troopers ends and `Troopers.setYield` for I2.
  W3's Heal revive calls `Troopers.tend(p, downed, true)`. W5 registers `Troopers.registerBoundCheck` for bound
  sentinels (attacked on sight, 5.2) and may register `Troopers.registerDormancyCheck` for dormant ones. W4 cannot
  name `RuinSentinel` itself, because W5 merges after W4. Until W5 registers the bound check, a bound sentinel standing
  guard with no target is not attacked on sight (an awake one with a target is); without the dormancy check a
  construct with no target is already treated as dormant.
- Kneel tending is driven by W4 (sneak-use); a mission may also drive `tend` each tick, and the two never double up.
- Squad membership is found by proximity (48 blocks): members scattered farther than that act as separate squads
  until they regroup.
- The reinforcement placement prefers spots no player can see; when none of 16 tries qualifies, the rifleman arrives
  at the first spot tried with standing room, which a player might see. When none of the 16 has room (a tight cave),
  that rifleman does not come.

## 33. Trooper client presentation (7.1, 7.4)

Client classes (`src/client/java/com/oldrepublic/client/enemy/`): `TrooperModel`, `TrooperRenderState`,
`TrooperRenderer`, `TrooperGlowLayer`, `BlasterBoltRenderer` (all four styles), `FragGrenadeModel`,
`FragGrenadeRenderState`, `FragGrenadeRenderer`, `EnemyParticleProviders` (the four new particles), `EnemyEffectsClient`
(the styled `blaster_fire`), `TrooperEffectsClient` (every trooper and grenade effect event), `BarkHud`. `EnemyClient`
registers the two trooper model layers, the grenade layer and the three renderers. Everything reads synced data only:
`role()`, `action()`, `actionTicks()`, `isDowned()`, `isDisarmed()`, `isYielded()`, `boltStyle()`, `muzzlePosition()`.

- **Model (`TrooperModel`, one layer per army):** the vanilla humanoid with plate parts that use the same textures as
  the player's trooper armour.
  - Republic: the hat layer is a rounded helmet shell under a low dome, with one wide horizontal visor band across the
    face and round ear pieces.
  - Imperial: an angular helmet: a brow ridge over two narrow eye slits (painted), a vertical nose ridge, angled cheek
    plates and a low crest.
  - Both: a cuirass, shoulder pauldrons and a belt. Role marks: the medic's kolto injector on the hip (and a teal
    shoulder band, painted), the grenadier's bandolier, the sergeant's chevrons (painted on the helmet sides).
  - Officers (either army): a peaked cap, a tunic with sleeves, no armour; the armour and helmet parts are hidden.
  - The skin is `textures/entity/trooper/<armyKey>_<role>.png`. A Republic grenadier and an Imperial medic (pairs no
    army fields) draw as riflemen.
- **Poses (`TrooperModel.setupAnim` from `action()`):**
  - IDLE: rifle carried at the hip, or shouldered (`CROSSBOW_HOLD`) while the trooper is aggressive.
  - CHARGING: rifle shouldered; a pistol levelled along the look.
  - VENTING: rifle lowered across the hips, muzzle down.
  - HEALING (and the reserved KNEEL_TEND): knelt by the patient, both hands reaching forward and down.
  - RALLY_TELL: the off-hand fist raised high.
  - FLARE_TELL: the off arm straight up with the flare pistol.
  - GRENADE_TELL: the throwing arm drawn back over the shoulder, the body twisted into the throw.
  - SALUTE: the main hand to the brow; the weapon is put away.
  - STANDOFF: rifle held low across the body, watchful, not aiming.
  - BOUND: hands behind the back; the weapon is hidden.
- **Renderer (`TrooperRenderer`):** a downed trooper lies on its side (rotated 90 degrees about the body axis and lifted
  0.3 so it rests on the ground) with slack limbs and one knee drawn up; a yielded trooper kneels (the shared
  `KneelPose`); healing and tending ease into a kneel over 4 ticks. The weapon is hidden while disarmed, downed,
  saluting or bound.
- **Tell glows (`TrooperGlowLayer`):** CHARGING draws a muzzle glow in the bolt colour (blue-white Republic, crimson
  Imperial) that grows over the 12-tick charge and pulses just before the shot; GRENADE_TELL draws the grenade's red
  arming light in the drawn-back hand, blinking faster as the throw nears; FLARE_TELL draws the flare pistol's ember in
  the raised hand. With flashing effects off every blink and pulse is a steady glow.

## 34. Bolt styles (7.1, 7.7)

`BlasterBoltRenderer` draws every `BlasterBolt.Style` as three nested additive boxes (`RenderTypes.lightning()`,
full-bright): a near-white core inside a glow and a haze that reach further back than forward, which reads as a short
fading trail.

| Style | Core / glow / haze | Used by |
|---|---|---|
| POACHER | warm white / orange / red-orange | poachers and the Poacher Captain (the M1 look) |
| REPUBLIC | blue-white / blue / deep blue | Republic troopers |
| IMPERIAL | pink-white / crimson / dark red | Imperial troopers |
| SENTINEL | warm white / amber / orange | W5's Ruin Sentinel lance bolt |

A `heavy()` bolt is 1.6 times as thick and 1.3 times as long. A deflected bolt has a hotter, whiter core. The muzzle
flash of `blaster_fire` is tinted the same way (orange, blue-white, crimson, amber).

## 35. Effect handlers phase by phase (13.3)

Tells (`trooper_charge`, `grenade_tell`, `grenade_fuse`) spend `Vfx.tellCount` and are never thinned by the preset;
everything else spends `Vfx.count`. Budgets: 8 or fewer particles per shot, 40 or fewer per grenade burst. No effect
draws a ring on the ground. Every sound goes through `ClientConfig.modSoundVolume`. Besides the events, a client tick
follows synced state, so a player who arrives mid-tell still sees and hears it, and every entity-bound loop is stopped
when the action ends, the entity dies or unloads, or the level changes.

- **`trooper_charge`** (toTracking, variant = bolt style). Anticipation: the army's charge whine
  (`trooper.republic.charge` or `trooper.imperial.charge`), entity-bound, plus a few motes drawn in to the muzzle; while
  `action() == CHARGING`, small glows gather at the muzzle (more in the last 4 ticks) and the model glow grows. The
  whine stops the moment the action changes (the shot, a stagger, a disarm or death).
- **`blaster_fire`** (around, variant = style, intensity 1 = sound, 0 = flash only). Activation: a muzzle flash of 5
  sparks along the shot in the style's tint, a white core and a curl of barrel smoke (7 particles at most). Trooper
  shots with intensity above 0 play `trooper.<army>.fire` on the client; part A sends 0 beyond 3 shots per squad per 4
  ticks. Poacher shots keep their server-played M1 sound; the sentinel flash is silent here.
- **`trooper_vent`**. Activation: `trooper.vent` (entity-bound) and steam jetting out of both sides of the barrel.
  Aftermath: a thinning plume from the muzzle for the 40-tick vent, rifle lowered.
- **`kolto_heal`** (entity = the patient). Activation: `trooper.kolto` and a teal mist at the patient's chest. Travel:
  a thin teal stream from the healing medic's hand to the patient. Aftermath: the mist circles the patient's chest for
  the channel; if it completes, the mist lifts off with a few bright motes. An interrupted channel (the medic leaves
  HEALING) just stops.
- **`trooper_tend`** (0 every 20 ticks of tending, 1 stands). Tending: `trooper.tend` (at most one every 10 ticks per
  downed trooper, so one tending player gives one sound) and teal vapour at the trooper's chest. Stands: `trooper.stand`,
  dust scuffing off the armour as it rises from its side, and a last puff of kolto.
- **`trooper_rally`**. Anticipation: the raised fist and gold motes rising round it through the 30-tick tell.
  Activation: `trooper.rally` and a dust scuff as the sergeant plants his feet. Aftermath: if the tell completed, every
  Republic trooper within 12 blocks (the Rally radius) is lit by rising gold motes; a cancelled tell shows nothing more.
- **`flare_call`** (around, intensity = climb height). Anticipation: the raised flare pistol with its ember (pose and
  glow layer). Activation: `trooper.flare` and a smoke puff at the hand. Travel: a red flare climbs 20 blocks over 20
  ticks (eased), trailing smoke. Aftermath: it hangs and drifts down for 40 ticks, flickering and shedding embers
  (steady with flashing off), then fades over 10.
- **`grenade_tell`**. Anticipation: `grenade.arm` (entity-bound) and sparks at the hand; the drawn-back arm and the
  blinking red light (pose and glow layer) for the 20-tick tell, with a short spark at the hand every 4 ticks. With
  flashing off the glow layer's light is steady and the spark is not drawn.
- **`grenade_fuse`** (entity = the landed grenade). Anticipation of the burst: `grenade.fuse`, a rising tick bound to
  the grenade (two at most at once), and thin smoke curling off the cap, faster as the fuse burns down; the cap light
  blinks and quickens to a flicker (renderer).
- **`grenade_burst`** (around, intensity = radius). Activation: `grenade.burst` (two per 10 ticks at most), a
  white-orange flash (with flashing off a dimmer, half-alpha glow that fades over 12 ticks instead) and a camera shake
  for the local player within three radii. Travel: sparks thrown out and up.
  Impact: debris of the ground block. Aftermath: irregular smoke puffs at varied distances and heights with gaps (never
  a ring) and two faint, wide pressure-shimmer glows. 40 particles at most whatever the preset.
- **`grenade_return`** (around, 0 push, 1 pull, dir = new velocity). A bent-air streak of six fading wisps along the new
  path (push white, pull pale blue). No sound: the power plays its own.
- **`trooper_bark`** (around, variant = category x 4 + line). For a player within the bark range (intensity): the
  `trooper.radio` squelch, a tiny comm light at the speaker's helmet in the army colour, and the line in the bark HUD.
- **`trooper_salute`**. The salute pose and a small glint at the brow. No sound.

## 36. Bark HUD (7.1, 11.2)

`BarkHud` is a HUD element attached after `VanillaHudElements.OVERLAY_MESSAGE`. One line, centred low above the hotbar
and the action bar, for 60 ticks (4-tick fade in, 12-tick fade out) over a soft dark well with a tinted rule under it.
The speaker tag ("Republic Sergeant:", `trooper.oldrepublic.tag.<armyKey>.<role>` through
`trooper.oldrepublic.bark.format`) is tinted blue (Republic) or red (Imperial); the line is
`trooper.oldrepublic.bark.<armyKey>.<category>.<line>` (72 lines, 3 per category and army). A new bark replaces the line
on screen. The text is resolved when the bark arrives, so it stays readable if the speaker dies. The range check is the
effect handler's; `ClientConfig.showBarks` hides the line, and the radio click still plays.

## 37. Particles (13.2)

Registered in `EnemyParticles` (`FabricParticleTypes.simple()`), providers in `EnemyParticleProviders`, sprite lists in
`particles/*.json`. All share a base particle with exact velocity and fluent `tint`, `size`, `life`, `alpha` and
`gravity` adjusters (`EnemyParticleProviders.spawn`), which the equipment flourish also uses.

| Particle | Look | Used by |
|---|---|---|
| `kolto_mist` | soft teal-green puff that swells, drifts and thins out; faintly self-lit so it reads in shade | `kolto_heal`, `trooper_tend` |
| `vent_steam` | white steam wisp that rises and spreads | `trooper_vent`; the `grenade_return` streak and the burst shimmer (tinted, low alpha) |
| `grenade_smoke` | grey-brown lumpy smoke that grows as it rises | the fuse curl, the burst, the flare trail, dust scuffs |
| `flare_ember` | bright falling ember, full-bright | the flare, burst sparks, rally motes, the armour flourish |

## 38. The frag grenade (7.3)

`enemy.trooper.FragGrenade extends ThrowableProjectile implements ForceReactiveEntity`, registered by part A as
`oldrepublic:frag_grenade` (`noSave`).

- **Throw (`throwAt`):** from the grenadier's right hand, aimed within 1.2 blocks of the target (a centred square
  spread from the thrower's random), with the exact launch velocity (`lob`) that lands it in 10 to 26 ticks of free
  flight (gravity 0.04 per tick, drag 0.99, stepped as `ThrowableProjectile` steps). It plays nothing: the tell was
  `grenade_tell`.
- **Flight and landing:** it lands on a top face (synced `landed`), bounces off walls and ceilings (up to 4 times, then
  it settles), knocks against a body and drops at its feet. Resting, it settles under gravity with friction and rolls
  off ledges. The fuse (synced length from `TrooperBalance.grenadeFuseTicks`, 40) starts at the first landing (synced
  start time, so `fuseTicks()` works on both sides), or after 100 ticks in the air (thrown into a ravine).
- **Burst:** `grenadeDamage` (8) to every living body within `grenadeRadius` (3) that the centre can see (a wall or
  floor shields), falling off linearly to half at the edge (`damageAt`); knockback `grenadeKnockback` (0.5) away from
  the centre with a small hop, reduced by knockback resistance. Damage type `oldrepublic:frag_grenade`, direct entity
  the grenade, causing entity the thrower. It never damages terrain (no `level.explode`). Then `grenade_burst`.
- **Who it spares (`spares`):** until a redirect, the thrower, its side (`Allegiances.friendly`) and players the thrower
  is Allied with, as bolts do. After a Push or Pull it hits everyone, its thrower included.
- **Push (`onForcePush`):** velocity along the push at `grenadePushSpeed` (1.2 blocks per tick), lofted so it climbs at
  least 0.25 per unit of speed (a push along the floor sends it flying instead of skidding); it is in flight again and
  the fuse keeps running. Sends `grenade_return` variant 0.
- **Pull (`onForcePull`):** a ballistic arc (6 to 22 ticks) landing at the thrower's feet, or at the caster's if the
  thrower is gone. Sends `grenade_return` variant 1.
- **Client (`FragGrenadeRenderer`):** a ribbed cylinder with a grip band, a fuse cap and its lever, 32x16 texture. It
  tumbles end over end in flight and lies on its side once landed. The cap light is the fuse tell: a dim steady light
  before landing, then a slow red blink that quickens to a flicker as the burst nears, with a soft halo so it can be
  found in grass (steady and brightening with flashing off).

## 39. The natural patrol spawner (7.6)

Server classes: `PatrolBalance` (section `patrols`), `PatrolRules` (pure), `PatrolState`, `PatrolSpawner`,
`PatrolCommands`. `PatrolSpawner.init()` is called by `EnemyFeature` (4.10) and registers the server tick, the server
stop and the commands; it reads no balance at init.

- **Rolls:** every `intervalTicks` (1,200) each eligible player gets one roll at `chance` (15%); rolls are offset per
  player by a salt from the UUID, so players are spread out. Eligible: not a spectator, in the overworld, not Peaceful,
  Chapter Three at SUMMONS or later (`ChapterThree.atLeast`), and standing on the surface ("sky visible" is checked
  with the `MOTION_BLOCKING_NO_LEAVES` heightmap: nothing but leaves above the feet; `canSeeSky` is not in the dumps).
  `enabled = false` turns natural rolls off.
- **Caps:** at most `perPlayerCap` (1) patrol with a member within `perPlayerRange` (128) of the player, and
  `globalCap` (6) on the server.
- **Placement:** up to 12 tries per roll. A spot at a random bearing `minDistance` to `maxDistance` (48 to 80) blocks
  away, in a loaded chunk, on `#story_ground` (`MeetingSites.STORY_GROUND`), with room for a trooper (no liquid, nothing
  solid at the feet or head), and out of the player's sight: more than 70 degrees from where they look, or with blocks
  between their eyes and a trooper's head there.
- **Army:** the one whose outpost terminal (`Outposts.nearestTerminal`) is nearer within `outpostBiasRange` (384), else
  a random one.
- **Route:** a point `passDistance` (16) from the player on their left or right as seen from the start, then a point
  `routeLength` (96) further on the same heading, each put on the ground where its chunk is loaded. The start and the
  route, sampled every 8 blocks, must stay `siteClearance` (64) from every `MISSION_SITE` and `RELIQUARY` site
  (`WorldSites.all`) in the level.
- **Squad:** `Troopers.spawnSquad(level, spot, army, Troopers.defaultSquad(army, the player's level), null, NONE)`, then
  `Troopers.startPatrol(squad, route)` (part A: members are marked as patrol, never saved, and the leader walks the
  route).
- **Despawn:** every `despawnCheckTicks` (100) a squad whose members are all more than `despawnDistance` (128) from
  every non-spectator player of its level is discarded as a whole, and so is a squad in a level with no players. Dead or
  unloaded members drop out of their squad; a squad with none left is forgotten. Riflemen called by a patrol
  sergeant's flare join its patrol (`PatrolSpawner.join` from `Reinforcements`), so they count, despawn and clear with
  it. Every patrol is discarded when the server stops.
- **Commands (debug, the `EnemyCommands` permission pattern):** `/oldrepublic patrol spawn <player>` ignores the roll
  and the Chapter Three stage but honours the dimension, Peaceful, the caps and the placement, and says why it failed
  (`capped`, `no_site`, `dimension`, `peaceful`, `failed`); `/oldrepublic patrol clear` discards every live patrol.
  Messages: `commands.oldrepublic.patrol.*`.
- **Tests:** the live state is never touched. `patrolCapsAndSiteClearance` and `patrolDespawnsAsWholeSquad` use a
  detached `PatrolState` and the pure `PatrolRules`.

## 40. Tests and screenshots (part B)

`EnemyGameTests` additions (the three M2 tests now declare `maxTicks = 20`):

| Test | Checks |
|---|---|
| `grenadePushTimes1_2AndPullBack` (arena, 100 ticks) | the lob ballistics are exact; a thrown grenade spares its thrower; Push sends it on at x1.2 `grenadePushSpeed`, lofted; Pull flings it to the thrower's feet (to the caster's when the thrower is gone); the returned grenade lands, burns its fuse and bursts on its own thrower, and the caster 12 blocks away is untouched. Push and Pull are called directly (W3's dispatch is not needed). |
| `patrolCapsAndSiteClearance` (arena, 20 ticks) | the per-player and global caps, the 64-block site clearance (start and route), the route geometry (16 then 96), the out-of-sight rule, the placement distance, the army choice and the per-player roll |
| `patrolDespawnsAsWholeSquad` (arena, 20 ticks) | one player in range of one member keeps the whole squad; a removed member drops out; the whole squad goes at once when every member is out of range, or when the level has no players |

Client steps (`m3/TrooperClientSteps`, run by the lead's exploration client test, screenshots 40 to 46 here; 47 to 49
are in `docs/design/equipment.md`): the steps build a stone stage 40 blocks above the player and use the trooper debug
commands.

- `oldrepublic_m3_40_republic_roles`, `oldrepublic_m3_41_imperial_roles`: four roles in a row 8 blocks ahead, NoAI,
  facing the player.
- `oldrepublic_m3_42_squad_firing`: an `army`-incident Republic squad and Imperial squad firing across the view (the
  step waits for a bolt in flight).
- `oldrepublic_m3_43_downed_trooper`: a downed Republic trooper at 5 blocks.
- `oldrepublic_m3_44_grenade_tell`, `oldrepublic_m3_45_grenade_fuse`: a grenadier's tell, then the landed grenade's
  fuse (the step waits for the landing).
- `oldrepublic_m3_46_flare`: an Imperial sergeant's flare climbing and hanging; its reinforcements are cancelled.

Everything is removed afterwards (troopers, grenades, bolts and stands near the stage, the stage itself, the player's
armour; camera and robe models reset; the player put back).
