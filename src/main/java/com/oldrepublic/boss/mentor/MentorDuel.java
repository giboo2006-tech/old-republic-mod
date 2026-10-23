package com.oldrepublic.boss.mentor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.duel.DuelistProfiles;
import com.oldrepublic.saber.duel.Spar;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The mentor duels of the Fall and the Return (spec MILESTONE_5.md 8.3, 12.1): the old mentor at full
 * strength, with real damage both ways and the duellist profile {@code mentor_true}, {@code health}
 * health for the duel, a WHITE boss bar with 10 notches, leashed to {@code leash} blocks around where
 * the duel began, and their own powers: Saelen pushes and raises a Barrier, Sorn calls Lightning and
 * chokes (the M3 {@link Holds}). The duel ends once, reported through {@code onEnd} on a later server
 * tick: the mentor yields at {@code yieldAt} ({@link Result#YIELDED}), or the player dies, or is away
 * (out of the leash, in another dimension or offline) for {@code leaveTicks} ({@link Result#RESET}).
 * Everything the duel changes on the mentor is restored at the end, and the health is a transient
 * modifier, so a mentor saved mid-duel keeps its own. Sessions live in memory only (W5 calls it and
 * handles the choice; the duel never uses the boss hooks, 12.6).
 */
public final class MentorDuel {
	/** Whose duel: Saelen (Push and Barrier) or Sorn (Lightning and Choke). */
	public enum Kind {
		SAELEN,
		SORN
	}

	/** How a duel ended: the mentor yielded at 10%, or it reset (the player died or left for 200 ticks). */
	public enum Result {
		YIELDED,
		RESET
	}

	/** The mentor's duel health, a transient modifier on its maximum (never saved). */
	private static final Identifier HEALTH_MODIFIER = OldRepublicMod.id("mentor_duel_health");
	/** The M3 Choke's damage type and W6's Choke events, by id (start and end on the caster, on and off on the held). */
	private static final ResourceKey<DamageType> CHOKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_choke"));
	private static final Identifier CHOKE_EVENT = OldRepublicMod.id("force_choke");
	private static final Identifier CHOKE_TARGET_EVENT = OldRepublicMod.id("force_choke_target");
	private static final int CHOKE_START = 0;
	private static final int CHOKE_END = 1;
	private static final String HOLD_SOURCE = "mentor_choke";
	/** How high the choked player is lifted. */
	private static final double CHOKE_LIFT = 0.6;
	/** The Push's lift. */
	private static final double PUSH_LIFT = 0.35;
	/** The bar's player list is checked once a second (a relog makes a new player object). */
	private static final int BAR_SYNC_TICKS = 20;
	/** A mentor past its leash walks back, and fights again once this far inside it. */
	private static final double RETURN_MARGIN = 4.0;
	/** A power whose moment has come but whose range is wrong is tried again this much later. */
	private static final int POWER_RETRY_TICKS = 20;

	private enum Power {
		NONE,
		PUSH,
		BARRIER,
		LIGHTNING,
		CHOKE
	}

	/** One running duel. */
	private static final class Duel {
		final UUID player;
		final Mob mentor;
		final Kind kind;
		final Consumer<Result> onEnd;
		final BlockPos start;
		final ServerBossEvent bar;
		final boolean hadHome;
		final BlockPos home;
		final int homeRadius;
		int away;
		int age;
		boolean returning;
		boolean ended;
		// The power in use, its ticks so far, and the next one's moment (game time).
		Power power = Power.NONE;
		int powerTicks;
		boolean held;
		long nextPowerAt;
		boolean secondTurn;

		Duel(ServerPlayer player, Mob mentor, Kind kind, Consumer<Result> onEnd) {
			this.player = player.getUUID();
			this.mentor = mentor;
			this.kind = kind;
			this.onEnd = onEnd;
			this.start = mentor.blockPosition();
			this.bar = new ServerBossEvent(UUID.randomUUID(), mentor.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
			this.hadHome = mentor.hasHome();
			this.home = mentor.getHomePosition();
			this.homeRadius = mentor.getHomeRadius();
		}
	}

	private static final Map<UUID, Duel> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Duel> BY_MENTOR = new HashMap<>();
	/** Ends waiting to be reported on the next server tick (never inside {@link #start} or a damage callback). */
	private static final List<Runnable> PENDING = new ArrayList<>();

	/** Registers the tick, the yield floor, the Barrier and the interrupts. Called from {@code FoesM5Feature}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MentorDuel::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MentorDuel::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(MentorDuel::allowDeath);
		// Before the levels save, so every mentor is itself again.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
				MentorDuel.interrupt(target);
			}
		});
	}

	/**
	 * Starts {@code kind}'s duel between {@code p} and {@code mentor}; {@code onEnd} hears how it ended,
	 * once, on a later server tick (12.1). A duel already running for {@code p} resets first. With no
	 * mob to fight (a test's stand-in) or on Peaceful (the M4 rule) the mentor yields at once; a mentor
	 * that cannot fight now (dead, elsewhere, or {@code p} in a spar) resets.
	 */
	public static void start(ServerPlayer p, Entity mentor, Kind kind, Consumer<Result> onEnd) {
		Duel running = BY_PLAYER.get(p.getUUID());

		if (running != null) {
			finish(running, Result.RESET);
		}

		if (!(mentor instanceof Mob mob) || p.level().getDifficulty() == Difficulty.PEACEFUL) {
			PENDING.add(() -> onEnd.accept(Result.YIELDED));
			return;
		}

		if (!mob.isAlive() || mob.level() != p.level() || !p.isAlive() || p.isSpectator() || BY_MENTOR.containsKey(mob.getUUID()) || Spar.inSpar(p)) {
			PENDING.add(() -> onEnd.accept(Result.RESET));
			return;
		}

		Duel duel = new Duel(p, mob, kind, onEnd);
		BY_PLAYER.put(duel.player, duel);
		BY_MENTOR.put(mob.getUUID(), duel);
		begin(duel, p);
	}

	/** Whether {@code p} is duelling a mentor now. */
	public static boolean isDuelling(ServerPlayer p) {
		return BY_PLAYER.containsKey(p.getUUID());
	}

	/** Whether {@code mentor} is in a duel now. */
	public static boolean isDuelling(Entity mentor) {
		return BY_MENTOR.containsKey(mentor.getUUID());
	}

	// ---------------------------------------------------------------- the duel's course

	private static void begin(Duel duel, ServerPlayer p) {
		MentorDuelBalance b = MentorDuelBalance.get();
		Mob mentor = duel.mentor;
		AttributeInstance health = mentor.getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.removeModifier(HEALTH_MODIFIER);
			health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MODIFIER, Math.max(0.0, b.health - health.getValue()),
					AttributeModifier.Operation.ADD_VALUE));
		}

		mentor.setHealth(mentor.getMaxHealth());
		mentor.setHomeTo(duel.start, Math.max(1, Math.round(b.leash)));

		if (mentor instanceof MentorEntity m) {
			m.setProfile(DuelistProfiles.MENTOR_TRUE);
			m.setSparPartner(p);
			m.setBladeLit(true);
		} else {
			mentor.setTarget(p);
		}

		duel.bar.addPlayer(p);
		duel.nextPowerAt = mentor.level().getGameTime() + Math.max(1, b.firstPowerTicks);
		EffectEvents.toTracking(mentor, SaberEffects.SPAR, mentor.position(), null, mentor.getId(), 1f, SaberEffects.SPAR_BEGIN);
	}

	/** Every server tick: the reported ends, then each duel's rules and powers. */
	private static void tick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			List<Runnable> due = new ArrayList<>(PENDING);
			PENDING.clear();
			due.forEach(Runnable::run);
		}

		for (Duel duel : new ArrayList<>(BY_PLAYER.values())) {
			tick(server, duel);
		}
	}

	private static void tick(MinecraftServer server, Duel duel) {
		MentorDuelBalance b = MentorDuelBalance.get();
		Mob mentor = duel.mentor;
		ServerPlayer p = server.getPlayerList().getPlayer(duel.player);

		if (!mentor.isAlive() || mentor.isRemoved() || p != null && !p.isAlive()) {
			// The mentor is gone (an unload, an operator), or the player died: the duel starts over later.
			finish(duel, Result.RESET);
			return;
		}

		duel.age++;
		Vec3 centre = Vec3.atBottomCenterOf(duel.start);
		boolean here = p != null && p.level() == mentor.level() && !p.isSpectator() && p.position().distanceToSqr(centre) <= b.leash * b.leash;
		duel.away = here ? 0 : duel.away + 1;

		if (duel.away >= b.leaveTicks) {
			finish(duel, Result.RESET);
			return;
		}

		duel.bar.setProgress(Math.clamp(mentor.getHealth() / Math.max(1f, mentor.getMaxHealth()), 0f, 1f));

		if (p != null && duel.age % BAR_SYNC_TICKS == 0 && !duel.bar.getPlayers().contains(p)) {
			duel.bar.addPlayer(p);
		}

		// The leash: past it the mentor walks back to where the duel began, and fights again well inside it.
		double fromStart = mentor.position().distanceTo(centre);

		if (fromStart > b.leash) {
			duel.returning = true;
		} else if (fromStart < b.leash - RETURN_MARGIN) {
			duel.returning = false;
		}

		if (duel.returning || !here) {
			stopPower(duel, p);
			mentor.setTarget(null);

			if (duel.returning && mentor.getNavigation().isDone()) {
				mentor.getNavigation().moveTo(centre.x, centre.y, centre.z, 1.0);
			}

			return;
		}

		if (mentor.getTarget() != p) {
			mentor.setTarget(p);
		}

		tickPower(duel, p, b);
	}

	/** {@code ALLOW_DAMAGE}: the Barrier turns the player's blows; a blow that would take the mentor to {@code yieldAt} makes it yield. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		Duel duel = BY_MENTOR.get(entity.getUUID());

		if (duel == null || duel.ended || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(entity.level() instanceof ServerLevel level)) {
			return true;
		}

		if (duel.power == Power.BARRIER) {
			Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : entity.getEyePosition();
			Vec3 out = from.subtract(entity.getBoundingBox().getCenter());
			Vec3 at = entity.getBoundingBox().getCenter().add(out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : out.normalize().scale(MentorDuelBalance.get().barrierRadius * 0.8));
			EffectEvents.around(level, ForceBarrier.IMPACT, at, out.lengthSqr() < 1.0e-4 ? null : out.normalize(), entity.getId(), 0f,
					source.getDirectEntity() == source.getEntity() ? ForceBarrier.IMPACT_MELEE : ForceBarrier.IMPACT_PROJECTILE);
			return false;
		}

		float floor = yieldFloor(entity);

		if (entity.getHealth() - amount > floor) {
			return true;
		}

		if (entity.getHealth() > floor) {
			entity.setHealth(floor);
		}

		yieldNow(duel);
		return false;
	}

	/** {@code ALLOW_DEATH}: any killing blow during the duel stops at the yield instead ({@code /kill} still kills). */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		Duel duel = BY_MENTOR.get(entity.getUUID());

		if (duel == null || duel.ended || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}

		entity.setHealth(Math.max(1f, yieldFloor(entity)));
		yieldNow(duel);
		return false;
	}

	private static float yieldFloor(LivingEntity mentor) {
		return mentor.getMaxHealth() * Math.clamp(MentorDuelBalance.get().yieldAt, 0f, 1f);
	}

	/** The mentor yields: the fight stops at once; the end is reported on the next tick. */
	private static void yieldNow(Duel duel) {
		finish(duel, Result.YIELDED);
	}

	/**
	 * Ends {@code duel} once with {@code result}: its power stops, the bar goes, the mentor is restored
	 * (profile, spar partner, blade, health and home) and {@code onEnd} is queued for the next tick.
	 */
	private static void finish(Duel duel, Result result) {
		if (duel.ended) {
			return;
		}

		duel.ended = true;
		BY_PLAYER.remove(duel.player, duel);
		BY_MENTOR.remove(duel.mentor.getUUID(), duel);
		MinecraftServer server = duel.mentor.level().getServer();
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(duel.player);
		stopPower(duel, p);
		duel.bar.removeAllPlayers();
		restore(duel);

		if (!duel.mentor.isRemoved()) {
			EffectEvents.toTracking(duel.mentor, SaberEffects.SPAR, duel.mentor.position(), null, duel.mentor.getId(), 1f,
					result == Result.YIELDED ? SaberEffects.SPAR_WON : SaberEffects.SPAR_OVER);
		}

		PENDING.add(() -> duel.onEnd.accept(result));
	}

	private static void restore(Duel duel) {
		Mob mentor = duel.mentor;
		AttributeInstance health = mentor.getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.removeModifier(HEALTH_MODIFIER);
		}

		if (mentor.isAlive()) {
			mentor.setHealth(mentor.getMaxHealth());
		}

		if (duel.hadHome) {
			mentor.setHomeTo(duel.home, duel.homeRadius);
		} else {
			mentor.clearHome();
		}

		if (mentor instanceof MentorEntity m) {
			m.setSparPartner(null);
			m.setProfile(DuelistProfiles.JEDI_MASTER);

			if (m.isAlive()) {
				m.setBladeLit(false);
			}
		} else {
			mentor.setTarget(null);
		}
	}

	/** Server stop: every duel resets and is reported at once (there is no next tick). */
	public static void clear() {
		for (Duel duel : new ArrayList<>(BY_PLAYER.values())) {
			finish(duel, Result.RESET);
		}

		List<Runnable> due = new ArrayList<>(PENDING);
		PENDING.clear();
		due.forEach(Runnable::run);
	}

	// ---------------------------------------------------------------- the powers

	/** A Push or a stagger on the mentor ends its power's tell or channel (the M2 interrupt rule). */
	private static void interrupt(LivingEntity target) {
		Duel duel = BY_MENTOR.get(target.getUUID());

		if (duel == null || duel.power == Power.NONE || duel.power == Power.BARRIER) {
			return;
		}

		MinecraftServer server = target.level().getServer();
		stopPower(duel, server == null ? null : server.getPlayerList().getPlayer(duel.player));
	}

	private static void tickPower(Duel duel, ServerPlayer p, MentorDuelBalance b) {
		Mob mentor = duel.mentor;
		ServerLevel level = p.level();

		if (duel.power != Power.NONE) {
			duel.powerTicks++;
			mentor.getLookControl().setLookAt(p, 30f, 30f);
			boolean going = switch (duel.power) {
				case PUSH -> tickPush(duel, p, level, b);
				case BARRIER -> duel.powerTicks < Math.max(1, b.barrierTicks);
				case LIGHTNING -> tickLightning(duel, p, level, b);
				case CHOKE -> tickChoke(duel, p, level, b);
				case NONE -> false;
			};

			if (!going) {
				stopPower(duel, p);
			}

			return;
		}

		long now = level.getGameTime();

		if (now < duel.nextPowerAt) {
			return;
		}

		// The powers take turns; when this turn's does not fit the range, the other may.
		Power first = duel.kind == Kind.SAELEN ? Power.PUSH : Power.LIGHTNING;
		Power second = duel.kind == Kind.SAELEN ? Power.BARRIER : Power.CHOKE;
		Power turn = duel.secondTurn ? second : first;
		Power other = duel.secondTurn ? first : second;
		Power wanted = fits(turn, mentor, p, b) ? turn : fits(other, mentor, p, b) ? other : Power.NONE;

		if (wanted == Power.NONE) {
			duel.nextPowerAt = now + POWER_RETRY_TICKS;
			return;
		}

		duel.secondTurn = wanted == first;
		duel.nextPowerAt = now + Math.max(1, b.powerInterval);
		duel.power = wanted;
		duel.powerTicks = 0;
		duel.held = false;
		mentor.getNavigation().stop();

		switch (wanted) {
			case PUSH, CHOKE -> {
				// The palm drawn back: the acolyte's shove tell, by id (a Danger Sense tell).
				int tell = wanted == Power.PUSH ? b.pushTellTicks : b.chokeTellTicks;
				EffectEvents.toTracking(mentor, EnemyEffects.ACOLYTE_SHOVE, ForcePowers.chest(mentor), mentor.getLookAngle(), mentor.getId(), tell,
						EnemyEffects.SHOVE_WINDUP);
			}
			case BARRIER -> EffectEvents.toTracking(mentor, ForceBarrier.EFFECT, mentor.getBoundingBox().getCenter(), null, mentor.getId(), b.barrierRadius,
					ForceBarrier.VARIANT_START);
			case LIGHTNING -> EffectEvents.toTracking(mentor, ForceEffects.LIGHTNING, ForcePowers.chest(mentor), null, mentor.getId(), 1f,
					ForceEffects.packLightning(ForceEffects.LIGHTNING_CHARGE, 0));
			default -> {
			}
		}
	}

	/** Whether {@code power} can be used on {@code p} from where the mentor stands. */
	private static boolean fits(Power power, Mob mentor, ServerPlayer p, MentorDuelBalance b) {
		double distance = mentor.distanceTo(p);
		return switch (power) {
			case PUSH -> distance <= b.pushRange - 0.5 && mentor.hasLineOfSight(p);
			case LIGHTNING -> distance >= 3.0 && distance <= b.lightningRange && mentor.hasLineOfSight(p);
			case CHOKE -> distance <= b.chokeRange && mentor.hasLineOfSight(p);
			case BARRIER -> true;
			case NONE -> false;
		};
	}

	/** Saelen's Push: after the tell, the player within range is thrown back for {@code pushDamage}. */
	private static boolean tickPush(Duel duel, ServerPlayer p, ServerLevel level, MentorDuelBalance b) {
		if (duel.powerTicks < Math.max(1, b.pushTellTicks)) {
			return true;
		}

		Mob mentor = duel.mentor;
		Vec3 to = p.position().subtract(mentor.position());

		if (to.length() <= b.pushRange && mentor.hasLineOfSight(p)) {
			double resist = Math.clamp(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
			Vec3 flat = to.horizontal().lengthSqr() < 1.0e-4 ? mentor.getLookAngle().horizontal() : to.horizontal();
			Vec3 push = flat.normalize().scale(b.pushStrength * (1.0 - 0.6 * resist));
			p.setDeltaMovement(p.getDeltaMovement().scale(0.2).add(push.x, PUSH_LIFT, push.z));
			p.hurtMarked = true;
			p.hurtServer(level, mentor.damageSources().indirectMagic(mentor, mentor), b.pushDamage);
		}

		EffectEvents.toTracking(mentor, EnemyEffects.ACOLYTE_SHOVE, ForcePowers.chest(mentor), to.lengthSqr() < 1.0e-4 ? mentor.getLookAngle() : to.normalize(),
				mentor.getId(), 1f, EnemyEffects.SHOVE_RELEASE);
		return false;
	}

	/**
	 * Sorn's Lightning: the charge is the tell; then {@code lightningDamage} every {@code lightningInterval}
	 * for {@code lightningChannel} ticks, which a saber guard can turn. Breaking line of sight or range ends it.
	 */
	private static boolean tickLightning(Duel duel, ServerPlayer p, ServerLevel level, MentorDuelBalance b) {
		Mob mentor = duel.mentor;
		int charge = Math.max(1, b.lightningChargeTicks);

		if (duel.powerTicks < charge) {
			return true;
		}

		if (!mentor.hasLineOfSight(p) || mentor.distanceTo(p) > b.lightningRange + 2) {
			return false;
		}

		int channel = duel.powerTicks - charge;

		if (channel == 0) {
			duel.held = true;
			EffectEvents.toTracking(mentor, ForceEffects.LIGHTNING, ForcePowers.chest(mentor), null, mentor.getId(), 1f,
					ForceEffects.packLightning(ForceEffects.LIGHTNING_ARCS, 0));
			EffectEvents.toTracking(p, ForceEffects.LIGHTNING_TARGET, ForcePowers.chest(p), null, p.getId(), 1f, ForceEffects.packCaster(mentor.getId(), 0));
		}

		if (channel % Math.max(1, b.lightningInterval) == 0) {
			DamageSource source = ForceDamage.source(level, ForceDamage.LIGHTNING, mentor);
			boolean landed = SaberCombat.strike(p, level, source, b.lightningDamage, SaberCombat.Strike.NPC_LIGHT);
			EffectEvents.toTracking(mentor, ForceEffects.LIGHTNING_HIT, ForcePowers.chest(p), null, mentor.getId(), 1f, landed ? 0 : 1);
		}

		return channel < Math.max(1, b.lightningChannel);
	}

	/** Sorn's Choke: after the tell, the M3 hold for {@code chokeTicks} and {@code chokeDamage} a second. */
	private static boolean tickChoke(Duel duel, ServerPlayer p, ServerLevel level, MentorDuelBalance b) {
		Mob mentor = duel.mentor;
		int tell = Math.max(1, b.chokeTellTicks);

		if (duel.powerTicks < tell) {
			return true;
		}

		int held = duel.powerTicks - tell;

		if (held == 0) {
			if (mentor.distanceTo(p) > b.chokeRange || !mentor.hasLineOfSight(p)) {
				return false;
			}

			int ticks = ControlEffects.duration(p, Math.max(1, b.chokeTicks), ControlEffects.HOLD);

			if (ticks <= 0) {
				return false;
			}

			duel.held = true;
			Holds.hold(p, ticks + 1, HOLD_SOURCE, p.position().add(0, CHOKE_LIFT, 0), true);
			EffectEvents.toTracking(mentor, CHOKE_EVENT, ForcePowers.chest(p), null, mentor.getId(), ticks / 20f, CHOKE_START);
			EffectEvents.toTracking(p, CHOKE_TARGET_EVENT, ForcePowers.chest(p), null, p.getId(), 1f, 0);
			return true;
		}

		if (!Holds.isHeld(p) || held >= b.chokeTicks) {
			return false;
		}

		if (held % 20 == 0) {
			p.hurtServer(level, level.damageSources().source(CHOKE_DAMAGE, mentor, mentor), b.chokeDamage);
		}

		return true;
	}

	/** Stops the power in use on any path: the Barrier's end, the arcs' stop, the hold's release. */
	private static void stopPower(Duel duel, @Nullable ServerPlayer p) {
		Mob mentor = duel.mentor;
		Power power = duel.power;
		boolean held = duel.held;
		duel.power = Power.NONE;
		duel.powerTicks = 0;
		duel.held = false;

		switch (power) {
			case BARRIER -> EffectEvents.toTracking(mentor, ForceBarrier.EFFECT, mentor.getBoundingBox().getCenter(), null, mentor.getId(), 0f,
					ForceBarrier.VARIANT_END);
			case LIGHTNING -> {
				EffectEvents.toTracking(mentor, ForceEffects.LIGHTNING, ForcePowers.chest(mentor), null, mentor.getId(), 1f,
						ForceEffects.packLightning(ForceEffects.LIGHTNING_STOP, 0));

				if (held && p != null) {
					EffectEvents.toTracking(p, ForceEffects.LIGHTNING_TARGET, ForcePowers.chest(p), null, p.getId(), 0f, ForceEffects.packCaster(mentor.getId(), 0));
				}
			}
			case CHOKE -> {
				if (held && p != null) {
					if (Holds.isHeld(p)) {
						Holds.release(p, HOLD_SOURCE);
					}

					EffectEvents.toTracking(p, CHOKE_TARGET_EVENT, ForcePowers.chest(p), null, p.getId(), 0f, 0);
					EffectEvents.toTracking(mentor, CHOKE_EVENT, ForcePowers.chest(p), null, mentor.getId(), 0f, CHOKE_END);
				}
			}
			default -> {
			}
		}
	}

	private MentorDuel() {
	}
}
