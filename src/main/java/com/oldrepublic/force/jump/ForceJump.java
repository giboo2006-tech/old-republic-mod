package com.oldrepublic.force.jump;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Jump ({@code force_jump}, spec 8.2): hold to gather, release to leap. The leap velocity is
 * set on the server and sent with {@code hurtMarked}; clients only predict the visuals. Tracks each
 * leap until it lands for fall protection, the landing effects, Crushing Descent and the Soaring
 * Leap glide.
 */
public final class ForceJump implements ForceAbility {
	public static final String ID = "force_jump";
	private static final String ATARU = "form_ataru";
	private static final int STAGGER_AMPLIFIER = 2;
	private static final int GLIDE_COST_INTERVAL = 5;

	/** A gather in progress. */
	private static final class Gather {
		final ServerPlayer player;
		int age;

		Gather(ServerPlayer player) {
			this.player = player;
		}
	}

	/** A leap between take-off and landing. */
	private static final class Leap {
		final ServerPlayer player;
		final Vec3 start;
		final JumpShape shape;
		final boolean hostileAtTakeoff;
		double peakY;
		int age;
		boolean airborne;
		boolean fallProtectionUsed;
		boolean gliding;
		boolean glideUsed;
		int glideTicks;

		Leap(ServerPlayer player, JumpShape shape, boolean hostileAtTakeoff) {
			this.player = player;
			this.start = player.position();
			this.shape = shape;
			this.hostileAtTakeoff = hostileAtTakeoff;
			this.peakY = start.y;
		}
	}

	private static final Map<UUID, Gather> GATHERING = new HashMap<>();
	private static final Map<UUID, Leap> LEAPS = new HashMap<>();
	/** Server tick counter value when each online player last stood on the ground (coyote time). */
	private static final Map<UUID, Long> LAST_GROUND = new HashMap<>();
	private static long ticks;

	public static JumpBalance balance() {
		return BalanceConfig.section("force_jump", JumpBalance.class, JumpBalance::new);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.HOLD_RELEASE;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		switch (action) {
			case START -> start(p);
			case RELEASE -> release(p);
			case CANCEL -> cancel(p, true);
		}
	}

	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
		if (GATHERING.remove(p.getUUID()) != null && feedback) {
			sendGatherStop(p);
		}
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return GATHERING.containsKey(p.getUUID());
	}

	/**
	 * Keys: {@code cost_min}, {@code cost_max}, {@code cooldown_s}, {@code min_charge_s},
	 * {@code full_charge_s}, {@code height_min}, {@code height_max}, {@code distance_min},
	 * {@code distance_max}, {@code fall_protection_extra}, {@code mastery_cost_pct},
	 * {@code mastery_height_pct}, {@code crush_min_fall}, {@code crush_radius},
	 * {@code crush_damage_min}, {@code crush_damage_max}, {@code crush_stagger_s},
	 * {@code soar_distance_mult}, {@code soar_height_mult}, {@code glide_seconds}, {@code glide_cost_per_s}.
	 */
	@Override
	public Map<String, Float> info() {
		JumpBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost_min", b.costMin);
		info.put("cost_max", b.costMax);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("min_charge_s", b.minChargeTicks / 20f);
		info.put("full_charge_s", b.fullChargeTicks / 20f);
		info.put("height_min", (float) apexHeight(b.verticalMin));
		info.put("height_max", (float) apexHeight(b.verticalMax));
		info.put("distance_min", (float) flightDistance(b.verticalMin, b.horizontalMin));
		info.put("distance_max", (float) flightDistance(b.verticalMax, b.horizontalMax));
		info.put("fall_protection_extra", b.fallProtectionExtra);
		info.put("mastery_cost_pct", b.masteryCostPerRank * 100f);
		info.put("mastery_height_pct", b.masteryHeightPerRank * 100f);
		info.put("crush_min_fall", b.crushMinFall);
		info.put("crush_radius", b.crushRadius);
		info.put("crush_damage_min", crushDamage(b, b.crushMinFall));
		info.put("crush_damage_max", b.crushDamageMax);
		info.put("crush_stagger_s", b.crushStaggerTicks / 20f);
		info.put("soar_distance_mult", b.soarHorizontalMult);
		info.put("soar_height_mult", b.soarVerticalMult);
		info.put("glide_seconds", b.glideMaxTicks / 20f);
		info.put("glide_cost_per_s", b.glideCostPerTick * 20f);
		return info;
	}

	/** The player's jump upgrade. */
	public static JumpShape shape(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (JumpShape shape : JumpShape.values()) {
			if (shape != JumpShape.NONE && ForcePowers.hasNode(player, data, shape.nodeId())) {
				return shape;
			}
		}

		return JumpShape.NONE;
	}

	/** True between a Force Jump take-off and its landing (Ataru's aerial strike reads this). */
	public static boolean isLeaping(Player player) {
		return LEAPS.containsKey(player.getUUID());
	}

	/** Charge 0 to 1 from the gather time; negative below the minimum. */
	public static float charge(JumpBalance b, int heldTicks) {
		if (heldTicks < b.minChargeTicks) {
			return -1f;
		}

		int span = Math.max(1, b.fullChargeTicks - b.minChargeTicks);
		return Mth.clamp((heldTicks - b.minChargeTicks) / (float) span, 0f, 1f);
	}

	/** Force Energy cost at {@code charge} after mastery (before skill, alignment and form multipliers). */
	public static float baseCost(JumpBalance b, float charge, int masteryRank) {
		return Mth.lerp(charge, b.costMin, b.costMax) * Math.max(0f, 1f - b.masteryCostPerRank * Mth.clamp(masteryRank, 0, 3));
	}

	/** Take-off velocity at {@code charge} along the horizontal part of {@code look}. */
	public static Vec3 launchVelocity(JumpBalance b, float charge, Vec3 look, JumpShape shape, int masteryRank) {
		double vertical = Mth.lerp(charge, b.verticalMin, b.verticalMax) * (1f + b.masteryHeightPerRank * Mth.clamp(masteryRank, 0, 3));
		double horizontal = Mth.lerp(charge, b.horizontalMin, b.horizontalMax);

		if (shape == JumpShape.SOAR) {
			vertical *= b.soarVerticalMult;
			horizontal *= b.soarHorizontalMult;
		}

		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		return dir.scale(horizontal).add(0, vertical, 0);
	}

	/** Blocks of fall the protection cancels after a leap that rose {@code apex} blocks. */
	public static double fallAllowance(JumpBalance b, double apex) {
		return Math.max(0.0, apex) + b.fallProtectionExtra;
	}

	/** Crushing Descent damage after falling {@code fallen} blocks. */
	public static float crushDamage(JumpBalance b, double fallen) {
		return (float) Math.min(b.crushDamageMax, b.crushDamageBase + b.crushDamagePerBlock * fallen);
	}

	/** Apex height of a vertical take-off speed with player gravity (0.08) and drag (0.98). */
	static double apexHeight(double vy) {
		double height = 0;
		double v = vy;

		while (v > 0) {
			height += v;
			v = (v - 0.08) * 0.98;
		}

		return height;
	}

	/** Horizontal distance covered until the jumper is back at take-off height (air drag 0.91 per tick). */
	static double flightDistance(double vy, double vh) {
		double y = 0;
		double v = vy;
		double distance = 0;
		double h = vh;

		for (int t = 0; t < 200 && (t == 0 || y > 0); t++) {
			y += v;
			v = (v - 0.08) * 0.98;
			distance += h;
			h *= 0.91;
		}

		return distance;
	}

	private static boolean grounded(ServerPlayer player, JumpBalance b) {
		if (player.onGround()) {
			return true;
		}

		Long last = LAST_GROUND.get(player.getUUID());
		return last != null && ticks - last <= b.coyoteTicks;
	}

	static void start(ServerPlayer player) {
		JumpBalance b = balance();

		if (GATHERING.containsKey(player.getUUID()) || !ForcePowers.mayStart(player, ID)) {
			return;
		}

		if (!grounded(player, b)) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_jump.not_grounded"));
			return;
		}

		if (!ForcePowers.canAfford(player, cost(player, 0f))) {
			return;
		}

		GATHERING.put(player.getUUID(), new Gather(player));
		EffectEvents.toTracking(player, ForceEffects.JUMP_GATHER, player.position(), null, player.getId(), 0f, ForceEffects.CHARGE_START);
	}

	private static float cost(ServerPlayer player, float charge) {
		return baseCost(balance(), charge, ForcePowers.masteryRank(player, ID)) * ForcePowers.costMultiplier(player, ID);
	}

	static void release(ServerPlayer player) {
		Gather gather = GATHERING.remove(player.getUUID());

		if (gather == null) {
			return;
		}

		JumpBalance b = balance();
		float charge = charge(b, gather.age);

		if (charge < 0f || !player.isAlive() || !grounded(player, b) || !Progression.trySpendEnergy(player, cost(player, charge))) {
			// Too short, no longer on the ground, or out of energy: no leap, no cost.
			sendGatherStop(player);
			return;
		}

		leap(player, charge);
	}

	/** Launches {@code player} at {@code charge} (energy already spent). */
	public static void leap(ServerPlayer player, float charge) {
		JumpBalance b = balance();
		JumpShape shape = shape(player);
		Vec3 velocity = launchVelocity(b, charge, player.getLookAngle(), shape, ForcePowers.masteryRank(player, ID));
		player.setDeltaMovement(velocity);
		player.hurtMarked = true;
		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		LEAPS.put(player.getUUID(), new Leap(player, shape, ForcePowers.hostileNear(player, b.masteryHostileRadius)));
		EffectEvents.toTracking(player, ForceEffects.JUMP_LAUNCH, player.position(), velocity, player.getId(), charge, shape.ordinal());
		QuestHooks.onAbilityUsed(player, ID, 0);
	}

	private static void sendGatherStop(ServerPlayer player) {
		EffectEvents.toTracking(player, ForceEffects.JUMP_GATHER, player.position(), null, player.getId(), 0f, ForceEffects.CHARGE_STOP);
	}

	/**
	 * Fall protection, called from the fall damage mixin with the distance about to be applied:
	 * the first landing of a leap ignores up to the leap's apex height plus
	 * {@code fallProtectionExtra} blocks of fall; anything beyond counts normally.
	 */
	public static double adjustFall(LivingEntity entity, double fallDistance) {
		if (!(entity instanceof ServerPlayer player)) {
			return fallDistance;
		}

		Leap leap = LEAPS.get(player.getUUID());

		if (leap == null || leap.fallProtectionUsed) {
			return fallDistance;
		}

		leap.fallProtectionUsed = true;
		double apex = Math.max(leap.peakY, player.getY()) - leap.start.y;
		return Math.max(0.0, fallDistance - fallAllowance(balance(), apex));
	}

	/** Coyote time bookkeeping, gathers and leaps; called every server tick. */
	public static void tick(MinecraftServer server) {
		ticks++;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround()) {
				LAST_GROUND.put(player.getUUID(), ticks);
			}
		}

		JumpBalance b = balance();
		Iterator<Gather> gathers = GATHERING.values().iterator();

		while (gathers.hasNext()) {
			Gather gather = gathers.next();

			if (!gather.player.isAlive() || gather.player.isRemoved()) {
				gathers.remove();
			} else if (++gather.age > b.maxHoldTicks) {
				gathers.remove();
				sendGatherStop(gather.player);
			}
		}

		Iterator<Leap> leaps = LEAPS.values().iterator();

		while (leaps.hasNext()) {
			Leap leap = leaps.next();

			if (tickLeap(leap, b)) {
				leaps.remove();
			}
		}
	}

	/** Returns true when the leap is over. */
	private static boolean tickLeap(Leap leap, JumpBalance b) {
		ServerPlayer player = leap.player;

		if (!player.isAlive() || player.isRemoved() || ++leap.age > b.protectionWindowTicks) {
			stopGlide(leap);
			return true;
		}

		leap.peakY = Math.max(leap.peakY, player.getY());

		if (!player.onGround()) {
			leap.airborne = true;
		}

		if (leap.age > 2 && player.onGround() && (leap.airborne || leap.age > 10)) {
			stopGlide(leap);
			land(leap, b);
			return true;
		}

		if (leap.shape == JumpShape.SOAR) {
			tickGlide(leap, b);
		}

		return false;
	}

	/**
	 * Soaring Leap: holding Jump in the air glides once per leap, for at most {@code glideMaxTicks}.
	 * The jumper's own client caps the fall speed (movement is client side); the server decides when
	 * the glide may run and charges for it.
	 */
	private static void tickGlide(Leap leap, JumpBalance b) {
		ServerPlayer player = leap.player;
		// API-CHECK: ServerPlayer#getLastClientInput() returns net.minecraft.world.entity.player.Input with jump()
		boolean holding = player.getLastClientInput().jump();

		if (!leap.gliding) {
			if (!leap.glideUsed && holding && leap.airborne && player.getY() < leap.peakY - 0.05) {
				leap.gliding = true;
				EffectEvents.toTracking(player, ForceEffects.JUMP_GLIDE, player.position(), null, player.getId(), 0f, ForceEffects.CHARGE_START);
			}

			return;
		}

		leap.glideTicks++;
		boolean paid = leap.glideTicks % GLIDE_COST_INTERVAL != 1
				|| Progression.trySpendEnergy(player, b.glideCostPerTick * GLIDE_COST_INTERVAL * ForcePowers.costMultiplier(player, ID));

		if (!holding || !paid || leap.glideTicks >= b.glideMaxTicks) {
			stopGlide(leap);
		}
	}

	private static void stopGlide(Leap leap) {
		if (leap.gliding) {
			leap.gliding = false;
			leap.glideUsed = true;
			EffectEvents.toTracking(leap.player, ForceEffects.JUMP_GLIDE, leap.player.position(), null, leap.player.getId(), 0f, ForceEffects.CHARGE_STOP);
		}
	}

	private static void land(Leap leap, JumpBalance b) {
		ServerPlayer player = leap.player;
		ServerLevel level = player.level();
		double fallen = Math.max(0.0, leap.peakY - player.getY());
		int surface = ForceEffects.surface(player.getBlockStateOn());
		EffectEvents.around(level, ForceEffects.JUMP_LAND, player.position(), null, player.getId(), (float) fallen, surface);

		if (!ATARU.equals(Skills.activeForm(player)) && b.landingSlowTicks > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.landingSlowTicks, 1, false, false, true));
		}

		if (leap.shape == JumpShape.CRUSH && fallen >= b.crushMinFall) {
			shockwave(player, fallen, b);
		}

		double distance = player.position().subtract(leap.start).horizontalDistance();

		if (leap.hostileAtTakeoff && distance >= b.masteryMinDistance) {
			ForcePowers.addMastery(player, ID, 1);
		}
	}

	/** Crushing Descent: damage, outward knock and a short stagger around the landing point; shielded bystanders are skipped. */
	static void shockwave(ServerPlayer player, double fallen, JumpBalance b) {
		ServerLevel level = player.level();
		Vec3 center = player.position();
		float damage = crushDamage(b, fallen);
		double r = b.crushRadius;
		PushBalance push = ForcePush.balance();

		for (Entity entity : level.getEntities(player, new AABB(center, center).inflate(r),
				e -> ForcePowers.canAffect(player, e, b.pvpEffects) && !Attitudes.shieldedFromArea(player, e))) {
			LivingEntity living = (LivingEntity) entity;

			if (living.position().distanceTo(center) > r + living.getBbWidth() * 0.5) {
				continue;
			}

			boolean resistant = ForcePowers.isResistant(living);
			float dealt = damage * (resistant ? b.crushResistantMult : 1f) * ForceReactions.areaDamageMultiplier(living);
			living.hurtServer(level, ForceDamage.source(level, ForceDamage.IMPACT, player), dealt);

			Vec3 out = living.position().subtract(center).horizontal();
			Vec3 dir = out.lengthSqr() < 1.0e-4 ? player.getLookAngle().horizontal().normalize() : out.normalize();
			float knock = b.crushKnock * ForcePowers.knockbackFactor(living, push.knockbackResistanceWeight) * (resistant ? push.resistantKnockbackMult : 1f);
			living.setDeltaMovement(living.getDeltaMovement().add(dir.scale(knock)).add(0, 0.3 * knock, 0));
			living.hurtMarked = true;
			ControlEffects.stagger(player, living, b.crushStaggerTicks, STAGGER_AMPLIFIER, ControlEffects.STAGGER);
			QuestHooks.onAbilityHit(player, ID, living);
		}

		EffectEvents.around(level, ForceEffects.JUMP_SLAM, center, null, player.getId(), damage, 0);
	}

	/** Drops the player's state (death, disconnect, dimension change). */
	public static void forget(ServerPlayer player) {
		GATHERING.remove(player.getUUID());
		Leap leap = LEAPS.remove(player.getUUID());

		if (leap != null) {
			stopGlide(leap);
		}

		LAST_GROUND.remove(player.getUUID());
	}

	public static void clear() {
		GATHERING.clear();
		LEAPS.clear();
		LAST_GROUND.clear();
		ticks = 0;
	}
}
