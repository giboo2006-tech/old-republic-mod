package com.oldrepublic.equipment.blaster;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.WeaponActions;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.GearEffects;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.M4Components;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of the player's blasters (spec MILESTONE_4.md 5.1): the trigger, heat and
 * overheat, the Weapon Action (vent; the rifle's charged shot), the repeater's spin-up, piercing and
 * suppression, the draw delay and the pistol's quick draw, and hit feedback for the reticle.
 * <p>
 * Per-player state ({@link Hand}) lives only while a blaster is in the main hand: drawing another
 * stack, putting it away, death, logout and a change of level all drop it, so a rifle's charge, a
 * loaded heavy bolt and the repeater's spin never outlive the weapon in hand. Heat lives on the
 * stack ({@code oldrepublic:blaster_heat}). Every bolt fired is remembered weakly with its shooter
 * and mod damage ({@link Shot}) until it is gone.
 */
public final class Blasters {
	/** Height below the eyes a bolt leaves from, so it travels along the crosshair from the gun. */
	private static final double BOLT_DROP = 0.1;
	/** How far along the look the muzzle effects sit. */
	private static final double MUZZLE_FORWARD = 0.7;
	/** Horizontal blocks per tick faster than walking (vanilla walking is about 0.216). */
	private static final double WALK_SPEED = 0.24;

	/** What one player is doing with the blaster in their main hand. */
	private static final class Hand {
		final ItemStack stack;
		final long drawnAt;
		long lastFire = Long.MIN_VALUE / 2;
		/** Repeater: when the barrel began to spin, or -1 when it is still. */
		long spinStart = -1;
		/** Repeater: the last tick the barrel was asked to fire (keeps the spin alive). */
		long spinActive;
		/** Rifle: when Weapon Action went down, or -1 while it is up. */
		long actionDown = -1;
		/** Rifle: a heavy bolt is loaded and goes with the next shot. */
		boolean loaded;

		Hand(ItemStack stack, long now) {
			this.stack = stack;
			this.drawnAt = now;
		}
	}

	/** A fired bolt: who fired it and what its mods add. */
	private static final class Shot {
		final UUID shooter;
		final BlasterItem.Kind kind;
		final float modDamage;
		int pierceLeft;
		boolean deflectSeen;

		Shot(UUID shooter, BlasterItem.Kind kind, float modDamage, int pierceLeft) {
			this.shooter = shooter;
			this.kind = kind;
			this.modDamage = modDamage;
			this.pierceLeft = pierceLeft;
		}
	}

	private static final Map<UUID, Hand> HANDS = new HashMap<>();
	private static final Map<BlasterBolt, Shot> SHOTS = new WeakHashMap<>();
	/** Repeater hits per mob, oldest first, for suppression. */
	private static final Map<UUID, ArrayDeque<Long>> SUPPRESS = new HashMap<>();

	/** Registers the Weapon Action handler and the tick, damage and cleanup listeners. */
	public static void init() {
		WeaponActions.register(stack -> stack.getItem() instanceof BlasterItem, Blasters::onAction);
		ServerTickEvents.END_SERVER_TICK.register(Blasters::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> onHit(entity, source, damage, blocked, false));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onHit(entity, source, 0f, false, true));
		ServerPlayerEvents.LEAVE.register(player -> drop(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> drop(oldPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> drop(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HANDS.clear();
			SHOTS.clear();
			SUPPRESS.clear();
		});
	}

	// ---------------------------------------------------------------- the trigger

	/** Use was pressed with {@code item} in the main hand: the first bolt leaves now if it may. */
	static void pressTrigger(ServerPlayer player, ItemStack stack, BlasterItem item) {
		tryFire(player, stack, item, player.level().getGameTime());
	}

	/** Use is held: the next bolt at the weapon's rate. */
	static void holdTrigger(ServerPlayer player, ItemStack stack, BlasterItem item) {
		tryFire(player, stack, item, player.level().getGameTime());
	}

	/** Use was let go. The repeater's barrel keeps turning for {@code spinHoldTicks}. */
	static void releaseTrigger(ServerPlayer player, BlasterItem item) {
		Hand hand = HANDS.get(player.getUUID());

		if (hand != null && item.kind() == BlasterItem.Kind.REPEATER && hand.spinStart >= 0) {
			hand.spinActive = player.level().getGameTime();
		}
	}

	/**
	 * Fires one bolt at {@code now} if the blaster may: not locked, drawn long enough, the rate since
	 * the last shot, the repeater spun up, and the rifle not mid-charge. Returns whether it fired.
	 */
	public static boolean tryFire(ServerPlayer player, ItemStack stack, BlasterItem item, long now) {
		if (player.isSpectator() || stack.isEmpty()) {
			return false;
		}

		Hand hand = hand(player, stack, now);
		BlasterBalance b = BlasterBalance.get();
		EquipmentM4Balance.ModNumbers m = EquipmentM4Balance.mods();
		BlasterItem.Kind kind = item.kind();
		BlasterHeat heat = heat(stack);
		int rate = BlasterBalance.of(b.rate, kind) + ModEffects.rateTicks(stack, m);

		if (BlasterRules.locked(heat, now) || now - hand.drawnAt < BlasterRules.drawDelay(kind, rate, b)
				|| !BlasterRules.rateReady(hand.lastFire, now, rate) || hand.actionDown >= 0) {
			return false;
		}

		if (kind == BlasterItem.Kind.REPEATER) {
			hand.spinActive = now;

			if (hand.spinStart < 0) {
				hand.spinStart = now;
				EffectEvents.toTracking(player, GearEffects.REPEATER_SPIN, muzzle(player), player.getLookAngle(), player.getId(), 1f, GearEffects.SPIN_UP);
				return false;
			}

			if (!BlasterRules.spunUp(hand.spinStart, now, b)) {
				return false;
			}
		}

		fire(player, stack, item, hand, now, b, m);
		return true;
	}

	private static void fire(ServerPlayer player, ItemStack stack, BlasterItem item, Hand hand, long now, BlasterBalance b, EquipmentM4Balance.ModNumbers m) {
		ServerLevel level = player.level();
		BlasterItem.Kind kind = item.kind();
		boolean charged = hand.loaded && kind == BlasterItem.Kind.RIFLE;
		float quality = Crafting.quality(stack).weapon();
		float damage = charged ? b.chargedDamage * quality : BlasterRules.boltDamage(kind, quality, b);
		float heatAdded = (charged ? b.chargedHeat : BlasterBalance.of(b.heatPerShot, kind)) * ModEffects.heat(stack, m);
		float spread = BlasterRules.spread(BlasterBalance.of(b.hipSpread, kind), steadied(player), fast(player), !player.onGround(),
				ModEffects.spread(stack, m), b);
		Vec3 dir = cone(player, spread, player.getRandom());

		BlasterBolt bolt = BlasterBolt.create(level, player, player.getEyePosition().add(0, -BOLT_DROP, 0), damage,
				BlasterBalance.of(b.life, kind), BlasterBolt.Style.PLAYER, charged);
		bolt.shoot(dir.x, dir.y, dir.z, BlasterBalance.of(b.speed, kind), 0f);
		level.addFreshEntity(bolt);
		int pierce = kind == BlasterItem.Kind.REPEATER ? b.repeaterPierce : 0;
		SHOTS.put(bolt, new Shot(player.getUUID(), kind, ModEffects.boltDamage(stack, m), pierce));

		hand.lastFire = now;
		hand.loaded = hand.loaded && !charged;
		BlasterHeat before = heat(stack);
		BlasterHeat after = BlasterRules.afterShot(before, now, heatAdded, kind, b);
		stack.set(M4Components.BLASTER_HEAT, after);

		int variant = charged ? GearEffects.FIRE_CHARGED : switch (kind) {
			case PISTOL -> GearEffects.FIRE_PISTOL;
			case RIFLE -> GearEffects.FIRE_RIFLE;
			default -> GearEffects.FIRE_REPEATER;
		};
		EffectEvents.toTracking(player, GearEffects.PLAYER_BLASTER_FIRE, muzzle(player), dir, player.getId(), after.heat() / BlasterRules.MAX_HEAT, variant);

		if (BlasterRules.overheated(after, now)) {
			overheat(player, hand);
		}

		stack.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
	}

	/** Overheated: the trigger lets go, the barrel stops, the vents scream and steam. */
	private static void overheat(ServerPlayer player, Hand hand) {
		EffectEvents.toTracking(player, GearEffects.BLASTER_HEAT, muzzle(player), player.getLookAngle(), player.getId(), 1f, GearEffects.HEAT_OVERHEAT);
		stopSpin(player, hand, false);
		player.stopUsingItem();
	}

	/** The bash staggered its target (spec 5.1). */
	static void onBash(ServerPlayer player, LivingEntity target) {
		ControlEffects.stagger(player, target, BlasterBalance.get().bashStagger, 1);
	}

	// ---------------------------------------------------------------- Weapon Action

	/**
	 * Weapon Action with a blaster in the main hand. Pistol and repeater: a press vents. Rifle: a
	 * press starts the charge; letting go before {@code chargedTicks} vents (a tap), holding loads the
	 * heavy bolt, which leaves with the next shot.
	 */
	static void onAction(ServerPlayer player, ItemStack held, boolean pressed) {
		if (!(held.getItem() instanceof BlasterItem item)) {
			return;
		}

		long now = player.level().getGameTime();
		Hand hand = hand(player, held, now);

		if (item.kind() != BlasterItem.Kind.RIFLE) {
			if (pressed) {
				vent(player, held, item, hand, now);
			}

			return;
		}

		if (pressed) {
			if (hand.actionDown < 0 && !BlasterRules.locked(heat(held), now)) {
				hand.actionDown = now;

				if (!hand.loaded) {
					EffectEvents.toTracking(player, GearEffects.PLAYER_BLASTER_FIRE, muzzle(player), player.getLookAngle(), player.getId(), 1f, GearEffects.CHARGE_START);
				}
			}

			return;
		}

		if (hand.actionDown < 0) {
			return;
		}

		boolean tap = !BlasterRules.chargeLoaded(hand.actionDown, now, BlasterBalance.get());
		hand.actionDown = -1;

		if (tap) {
			if (!hand.loaded) {
				// The charge was dropped: its rising whine stops.
				EffectEvents.toTracking(player, GearEffects.PLAYER_BLASTER_FIRE, muzzle(player), player.getLookAngle(), player.getId(), 0f, GearEffects.CHARGE_START);
			}

			vent(player, held, item, hand, now);
		}
	}

	/** Vents at {@code now} when the heat allows (30 or more); returns whether it vented. */
	public static boolean vent(ServerPlayer player, ItemStack stack, BlasterItem item, long now) {
		return vent(player, stack, item, hand(player, stack, now), now);
	}

	private static boolean vent(ServerPlayer player, ItemStack stack, BlasterItem item, Hand hand, long now) {
		BlasterBalance b = BlasterBalance.get();
		BlasterHeat before = heat(stack);
		BlasterRules.VentResult result = BlasterRules.ventResult(before, now, item.kind(), b);
		BlasterHeat after = BlasterRules.vent(before, now, item.kind(), b);

		if (after == null) {
			return false;
		}

		stack.set(M4Components.BLASTER_HEAT, after);
		int variant = result == BlasterRules.VentResult.PERFECT ? GearEffects.HEAT_PERFECT : GearEffects.HEAT_VENT;
		EffectEvents.toTracking(player, GearEffects.BLASTER_HEAT, muzzle(player), player.getLookAngle(), player.getId(),
				BlasterRules.heatAt(before, now, item.kind(), b) / BlasterRules.MAX_HEAT, variant);
		stopSpin(player, hand, false);
		return true;
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}

		for (Iterator<Map.Entry<BlasterBolt, Shot>> it = SHOTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlasterBolt, Shot> entry = it.next();
			BlasterBolt bolt = entry.getKey();
			Shot shot = entry.getValue();

			if (bolt.isRemoved()) {
				it.remove();
			} else if (bolt.isDeflected() && !shot.deflectSeen) {
				shot.deflectSeen = true;

				if (server.getPlayerList().getPlayer(shot.shooter) instanceof ServerPlayer shooter && shooter.level() == bolt.level()) {
					EffectEvents.toPlayer(shooter, GearEffects.PLAYER_BLASTER_FIRE, bolt.position(), null, bolt.getId(), 1f, GearEffects.DEFLECTED);
				}
			}
		}

		if (server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			int window = BlasterBalance.get().suppressWindow;
			SUPPRESS.values().removeIf(hits -> hits.isEmpty() || now - hits.peekLast() > window);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		Hand hand = HANDS.get(player.getUUID());

		if (!(main.getItem() instanceof BlasterItem item) || !player.isAlive()) {
			if (hand != null) {
				drop(player);
			}

			return;
		}

		long now = player.level().getGameTime();
		hand = hand(player, main, now);
		BlasterBalance b = BlasterBalance.get();

		if (item.kind() == BlasterItem.Kind.RIFLE && hand.actionDown >= 0 && !hand.loaded && BlasterRules.chargeLoaded(hand.actionDown, now, b)) {
			hand.loaded = true;
			EffectEvents.toPlayer(player, GearEffects.PLAYER_BLASTER_FIRE, muzzle(player), player.getLookAngle(), player.getId(), 1f, GearEffects.CHARGE_READY);
		}

		if (item.kind() == BlasterItem.Kind.REPEATER && hand.spinStart >= 0 && !player.isUsingItem() && now - hand.spinActive > b.spinHoldTicks) {
			stopSpin(player, hand, true);
		}
	}

	/** The player's state for {@code stack}; drawing a different stack starts a new one (the draw delay). */
	private static Hand hand(ServerPlayer player, ItemStack stack, long now) {
		Hand hand = HANDS.get(player.getUUID());

		if (hand != null && hand.stack == stack) {
			return hand;
		}

		if (hand != null) {
			stopSpin(player, hand, true);
		}

		Hand drawn = new Hand(stack, now);
		HANDS.put(player.getUUID(), drawn);
		return drawn;
	}

	/** Drops the player's blaster state: the spin winds down audibly, a charge is lost. */
	private static void drop(ServerPlayer player) {
		Hand hand = HANDS.remove(player.getUUID());

		if (hand != null) {
			stopSpin(player, hand, true);

			if (hand.actionDown >= 0 && !hand.loaded) {
				EffectEvents.toTracking(player, GearEffects.PLAYER_BLASTER_FIRE, player.getEyePosition(), player.getLookAngle(), player.getId(), 0f, GearEffects.CHARGE_START);
			}
		}
	}

	/** Stops the repeater's barrel; {@code audible} sends the wind-down (a vent's hiss covers it otherwise). */
	private static void stopSpin(ServerPlayer player, Hand hand, boolean audible) {
		if (hand.spinStart < 0) {
			return;
		}

		hand.spinStart = -1;

		if (audible && player.isAlive()) {
			EffectEvents.toTracking(player, GearEffects.REPEATER_SPIN, muzzle(player), player.getLookAngle(), player.getId(), 1f, GearEffects.SPIN_DOWN);
		}
	}

	// ---------------------------------------------------------------- hits

	private static void onHit(LivingEntity target, DamageSource source, float damage, boolean blocked, boolean killed) {
		if (!(source.getDirectEntity() instanceof BlasterBolt bolt) || !(target.level() instanceof ServerLevel level)) {
			return;
		}

		Shot shot = SHOTS.get(bolt);

		if (shot == null || !(source.getEntity() instanceof ServerPlayer shooter) || !shooter.getUUID().equals(shot.shooter)) {
			// Not a player's bolt, or a deflected one that now belongs to someone else.
			return;
		}

		EffectEvents.toPlayer(shooter, GearEffects.PLAYER_BLASTER_FIRE, target.getEyePosition(), null, target.getId(), killed ? 2f : 1f, GearEffects.HIT);

		if (shot.kind != BlasterItem.Kind.REPEATER) {
			return;
		}

		if (!killed && !(target instanceof Player)) {
			suppress(shooter, target, level.getGameTime());
		}

		if (!blocked && shot.pierceLeft > 0) {
			pierce(level, shooter, bolt, shot, target);
		}
	}

	/** A repeater hit on a mob: four within the window slow it ({@code ControlEffects.SLOW}). */
	private static void suppress(ServerPlayer shooter, LivingEntity target, long now) {
		BlasterBalance b = BlasterBalance.get();
		ArrayDeque<Long> hits = SUPPRESS.computeIfAbsent(target.getUUID(), id -> new ArrayDeque<>());
		hits.addLast(now);

		while (!hits.isEmpty() && now - hits.peekFirst() >= b.suppressWindow) {
			hits.removeFirst();
		}

		long[] ticks = hits.stream().mapToLong(Long::longValue).toArray();

		if (BlasterRules.suppressed(ticks, now, b)) {
			hits.clear();
			int duration = ControlEffects.diminished(target, b.suppressTicks, ControlEffects.SLOW);

			if (duration > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1), shooter);
			}
		}
	}

	/** The repeater's bolt goes on through the target it hit: a fresh bolt on the same line, beyond it. */
	private static void pierce(ServerLevel level, ServerPlayer shooter, BlasterBolt bolt, Shot shot, LivingEntity through) {
		Vec3 velocity = bolt.getDeltaMovement();

		if (velocity.lengthSqr() < 1.0e-6) {
			return;
		}

		Vec3 dir = velocity.normalize();
		Vec3 centre = through.getBoundingBox().getCenter();
		double along = centre.subtract(bolt.position()).dot(dir);
		Vec3 start = bolt.position().add(dir.scale(along + through.getBbWidth() * 0.75 + 0.35));
		BlasterBalance b = BlasterBalance.get();
		BlasterBolt next = BlasterBolt.create(level, shooter, start, bolt.damage(), BlasterBalance.of(b.life, shot.kind) / 2, BlasterBolt.Style.PLAYER, bolt.heavy());
		next.shoot(dir.x, dir.y, dir.z, (float) velocity.length(), 0f);
		level.addFreshEntity(next);
		Shot continued = new Shot(shot.shooter, shot.kind, shot.modDamage, shot.pierceLeft - 1);
		SHOTS.put(next, continued);
	}

	/**
	 * The mod damage multiplier of a player's bolt (heavy barrel, overcharged cell) for the equipment
	 * damage modifier, or 1 for any other source.
	 */
	public static float modDamage(DamageSource source) {
		if (source.getDirectEntity() instanceof BlasterBolt bolt) {
			Shot shot = SHOTS.get(bolt);
			return shot == null ? 1f : shot.modDamage;
		}

		return 1f;
	}

	// ---------------------------------------------------------------- aim

	/** Sneaking on the ground steadies the aim. */
	public static boolean steadied(Player player) {
		return player.isShiftKeyDown() && player.onGround();
	}

	/** Moving faster than walking: sprinting, or covering more ground per tick than a walk. */
	public static boolean fast(Player player) {
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		return player.isSprinting() || dx * dx + dz * dz > WALK_SPEED * WALK_SPEED;
	}

	/** A direction inside the cone of half-angle {@code degrees} around the look, uniform over the disc. */
	public static Vec3 cone(Player player, float degrees, RandomSource random) {
		double radius = degrees * Math.sqrt(random.nextDouble());
		double angle = random.nextDouble() * Math.PI * 2.0;
		float pitch = Mth.clamp(player.getXRot() + (float) (radius * Math.sin(angle)), -89.9f, 89.9f);
		return Vec3.directionFromRotation(pitch, player.getYRot() + (float) (radius * Math.cos(angle)));
	}

	/** Where the muzzle effects go: a little ahead of the eyes, at gun height. */
	public static Vec3 muzzle(Player player) {
		return player.getEyePosition().add(player.getLookAngle().scale(MUZZLE_FORWARD)).add(0, -BOLT_DROP - 0.05, 0);
	}

	/** The heat component of {@code stack}, cold when it has none. */
	public static BlasterHeat heat(ItemStack stack) {
		return stack.getOrDefault(M4Components.BLASTER_HEAT, BlasterHeat.COLD);
	}

	/** Whether the rifle in {@code player}'s hand has a heavy bolt loaded. Tests and commands. */
	public static boolean loaded(ServerPlayer player) {
		Hand hand = HANDS.get(player.getUUID());
		return hand != null && hand.loaded;
	}

	/** Forgets the player's state at once (tests). */
	public static void forget(ServerPlayer player) {
		HANDS.remove(player.getUUID());
	}

	/** The tick {@code player} drew the blaster now in hand, for tests that skip the draw delay. */
	public static void drawnLongAgo(ServerPlayer player, ItemStack stack) {
		HANDS.put(player.getUUID(), new Hand(stack, Long.MIN_VALUE / 4));
	}

	/** Test hook: a Weapon Action press or release at the server's current tick. */
	public static void action(ServerPlayer player, boolean pressed) {
		onAction(player, player.getMainHandItem(), pressed);
	}

	/** Test hook: the rifle charge started {@code ticksAgo} ticks ago. */
	public static void chargeSince(ServerPlayer player, long ticksAgo) {
		Hand hand = HANDS.get(player.getUUID());

		if (hand != null) {
			hand.actionDown = player.level().getGameTime() - ticksAgo;
		}
	}

	/** Test hook: one tick of the player's blaster state (charge loading, spin wind-down). */
	public static void tickFor(ServerPlayer player) {
		tickPlayer(player);
	}

	/** The repeater's spin start tick for {@code player}, or -1 (tests). */
	public static long spinStart(@Nullable ServerPlayer player) {
		Hand hand = player == null ? null : HANDS.get(player.getUUID());
		return hand == null ? -1 : hand.spinStart;
	}

	private Blasters() {
	}
}
