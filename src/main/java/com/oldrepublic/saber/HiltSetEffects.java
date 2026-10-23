package com.oldrepublic.saber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.HymnDamage;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss sets' three-part effects (MILESTONE_7.md 7.2); the two-part bonuses are plain stats
 * ({@link SaberStats#of(HiltParts.Emitter, HiltParts.Grip, HiltParts.Pommel, SaberBalance)}). A set counts on
 * the saber that acts: the one that parries or blocks, or the one that strikes (a dual wielder's off-hand
 * saber for its own hits). A saberstaff counts its emitter and grip, so it never has three.
 * <ul>
 * <li>Collector, <b>Curator's Eye</b>: a parry (melee or bolt) marks the attacker for
 * {@code collectorMarkTicks}; the wielder's next saber hit on it deals {@code collectorMarkDamagePct}
 * more. One mark per wielder; a new parry moves it.</li>
 * <li>Watchman, <b>Vigil</b>: guard stamina comes back {@code watchmanRegenPct} faster while the saber is
 * held, and a blocked melee blow slows its attacker for {@code watchmanSlowTicks}.</li>
 * <li>Penitent, <b>Contrition</b>: each saber hit restores {@code penitentEnergyOnHit} Force energy and
 * deals {@code penitentLowHealthPct} more to a foe under {@code penitentLowHealthFraction} of its health.</li>
 * <li>Choir, <b>Long Hymn</b>: every {@code choirPulseHits} saber hits within {@code choirPulseWindowTicks}
 * release a resonant pulse from the wielder: {@code choirPulseDamage} ({@code oldrepublic:hymn} damage,
 * by the wielder) and a push of {@code choirPulseKnockback} to every other foe within
 * {@code choirPulseRadius} in sight. Sound and particles only; nothing is drawn on the ground.</li>
 * </ul>
 * Effect events for the clients ({@code HiltSetFx}): {@link #MARK} and {@link #PULSE}. Server side, except
 * the tooltip lines.
 */
public final class HiltSetEffects {
	/**
	 * Curator's Eye. Sent to the players tracking the marked foe; entity: the foe; pos: its chest. Variant
	 * {@link #MARK_SET} (intensity: the mark's ticks; {@code saber.set_mark}) or {@link #MARK_STRUCK} (the
	 * marked hit landed; the mark ends).
	 */
	public static final Identifier MARK = OldRepublicMod.id("hilt_set_mark");
	public static final int MARK_SET = 0;
	public static final int MARK_STRUCK = 1;
	/**
	 * The Long Hymn's pulse. Sent to the players tracking the wielder; entity: the wielder; pos: the pulse's
	 * centre (the wielder's chest); intensity: its radius; variant: how many foes it hit. Plays {@code saber.set_pulse}.
	 */
	public static final Identifier PULSE = OldRepublicMod.id("hilt_set_pulse");

	/** The numbers tooltips show: a client never reads the balance file. */
	private static final SaberBalance SHOWN = new SaberBalance();

	/** A Curator's Eye mark: the foe and the last game tick it holds. */
	private record Mark(UUID target, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	/** The game ticks of each wielder's recent Choir saber hits, oldest first. */
	private static final Map<UUID, Deque<Long>> HYMN = new HashMap<>();

	/** The set {@code saber} carries all three parts of, or null (a saberstaff never has three). */
	public static @Nullable HiltSet fullSet(ItemStack saber) {
		if (!(saber.getItem() instanceof LightsaberItem)) {
			return null;
		}

		SaberConfig config = SaberComponents.config(saber);
		boolean staff = saber.getItem() instanceof SaberStaffItem;

		for (HiltSet set : HiltSet.values()) {
			if (HiltSet.count(config, set, staff) >= 3) {
				return set;
			}
		}

		return null;
	}

	/**
	 * Curator's Eye and Contrition on a strike of {@code saber} (the hand that struck) by {@code attacker}:
	 * a marked foe takes the mark's bonus once (any saber hit of the wielder's ends the mark), a foe under
	 * half health takes Contrition's from a lit full Penitent hilt. Read in the saber damage pipeline.
	 */
	static float strikeMultiplier(ServerPlayer attacker, ItemStack saber, LivingEntity target, long now, SaberBalance b) {
		float mult = 1f;

		if (consumeMark(attacker.getUUID(), target.getUUID(), now)) {
			mult *= 1f + b.collectorMarkDamagePct / 100f;
			EffectEvents.toTracking(target, MARK, chest(target), null, target.getId(), 0f, MARK_STRUCK);
		}

		if (SaberComponents.isUsable(saber) && fullSet(saber) == HiltSet.PENITENT && contrition(target.getHealth(), target.getMaxHealth(), b)) {
			mult *= 1f + b.penitentLowHealthPct / 100f;
		}

		return mult;
	}

	/** Whether Contrition's bonus counts against a foe of {@code health} out of {@code maxHealth}: under {@code penitentLowHealthFraction}. */
	public static boolean contrition(float health, float maxHealth, SaberBalance b) {
		return maxHealth > 0f && health < maxHealth * b.penitentLowHealthFraction;
	}

	/**
	 * A parry by {@code player} with {@code saber}, of a melee blow or a bolt from {@code attacker}: with a
	 * full Collector hilt, Curator's Eye marks the attacker (7.2).
	 */
	static void onParry(ServerPlayer player, ItemStack saber, @Nullable Entity attacker, SaberBalance b) {
		if (!(attacker instanceof LivingEntity foe) || foe == player || !foe.isAlive() || fullSet(saber) != HiltSet.COLLECTOR) {
			return;
		}

		mark(player.getUUID(), foe.getUUID(), player.level().getGameTime() + b.collectorMarkTicks);
		EffectEvents.toTracking(foe, MARK, chest(foe), null, foe.getId(), b.collectorMarkTicks, MARK_SET);
	}

	/** Marks {@code target} for {@code wielder} until the game tick {@code until} (a new mark replaces the old). */
	public static void mark(UUID wielder, UUID target, long until) {
		MARKS.put(wielder, new Mark(target, until));
	}

	/**
	 * Whether {@code wielder}'s mark is on {@code target} and still holds at {@code now}. A hit on the marked
	 * foe ends the mark, as does reading an expired one.
	 */
	public static boolean consumeMark(UUID wielder, UUID target, long now) {
		Mark mark = MARKS.get(wielder);

		if (mark == null) {
			return false;
		}

		if (now > mark.until()) {
			MARKS.remove(wielder);
			return false;
		}

		if (!mark.target().equals(target)) {
			return false;
		}

		MARKS.remove(wielder);
		return true;
	}

	/** Vigil's faster guard regeneration for the held {@code saber} (a multiplier, 1 without a full Watchman hilt). */
	public static float guardRegenMultiplier(ItemStack saber, SaberBalance b) {
		return fullSet(saber) == HiltSet.WATCHMAN ? 1f + b.watchmanRegenPct / 100f : 1f;
	}

	/** A melee blow {@code source} was blocked (not parried) by {@code player}'s {@code saber}: Vigil slows its attacker. */
	static void onBlock(ServerPlayer player, ItemStack saber, DamageSource source, SaberBalance b) {
		Entity direct = source.getDirectEntity();

		if (!(direct instanceof LivingEntity attacker) || direct != source.getEntity() || attacker == player || !attacker.isAlive()
				|| fullSet(saber) != HiltSet.WATCHMAN) {
			return;
		}

		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.watchmanSlowTicks, b.watchmanSlowAmplifier));
	}

	/**
	 * A saber hit of the lit {@code saber} (the hand that struck) landed on {@code target}, or killed it:
	 * Contrition's Force energy, and the Long Hymn's count. Called by {@link SaberCombat} once the hit went
	 * through, never in a spar.
	 */
	static void onHit(ServerPlayer attacker, ItemStack saber, LivingEntity target, SaberBalance b) {
		HiltSet set = fullSet(saber);

		if (set == HiltSet.PENITENT && b.penitentEnergyOnHit > 0f) {
			HiltTraits.restoreEnergy(attacker, b.penitentEnergyOnHit);
		}

		if (set == HiltSet.CHOIR && countHymnHit(attacker.getUUID(), attacker.level().getGameTime(), b)) {
			longHymn(attacker, target, b);
		}
	}

	/**
	 * Counts one of {@code wielder}'s Choir saber hits at {@code now}: true when it is the
	 * {@code choirPulseHits}-th within {@code choirPulseWindowTicks} (the count then starts again).
	 */
	public static boolean countHymnHit(UUID wielder, long now, SaberBalance b) {
		Deque<Long> hits = HYMN.computeIfAbsent(wielder, id -> new ArrayDeque<>());

		while (!hits.isEmpty() && (now - hits.peekFirst() >= b.choirPulseWindowTicks || hits.peekFirst() > now)) {
			hits.pollFirst();
		}

		hits.addLast(now);

		if (hits.size() >= Math.max(1, b.choirPulseHits)) {
			hits.clear();
			return true;
		}

		return false;
	}

	/**
	 * The Long Hymn's pulse: {@code choirPulseDamage} to every other foe within {@code choirPulseRadius} of
	 * the wielder that it can see, each pushed {@code choirPulseKnockback} away from it. The pulse is a plain
	 * hit ({@link SaberCombat#plainHit}): no saber bonus, never a saber hit of its own, and its source has no
	 * direct entity, so no guard turns it and no blade sparks show.
	 */
	private static void longHymn(ServerPlayer player, LivingEntity struck, SaberBalance b) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		double radius = b.choirPulseRadius;
		Vec3 centre = chest(player);
		AABB box = player.getBoundingBox().inflate(radius);
		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e != struck && e.isAlive()
				&& !e.isSpectator() && e.getBoundingBox().distanceToSqr(centre) <= radius * radius && ForcePowers.isHostile(player, e)
				&& player.hasLineOfSight(e)));
		DamageSource source = level.damageSources().source(HymnDamage.HYMN, null, player);
		int hit = 0;

		for (LivingEntity target : targets) {
			Vec3 before = target.getDeltaMovement();

			if (!SaberCombat.plainHit(target, level, source, b.choirPulseDamage)) {
				continue;
			}

			hit++;

			if (target.isAlive()) {
				// The pulse's own push takes the place of the hurt knock-back, so choirPulseKnockback is the whole push.
				Vec3 away = target.position().subtract(player.position()).horizontal();
				Vec3 dir = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
				double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
				double strength = b.choirPulseKnockback * (1.0 - resist);
				target.setDeltaMovement(before.x + dir.x * strength, Math.max(before.y, 0.1 * (1.0 - resist)), before.z + dir.z * strength);
				target.hurtMarked = true;
			}
		}

		EffectEvents.toTracking(player, PULSE, centre, player.getLookAngle(), player.getId(), (float) radius, hit);
	}

	private static Vec3 chest(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	/** Forgets a wielder's mark and Long Hymn count (logout, respawn). */
	public static void forget(UUID wielder) {
		MARKS.remove(wielder);
		HYMN.remove(wielder);
	}

	/** Server stop: forgets every mark and count. */
	static void clear() {
		MARKS.clear();
		HYMN.clear();
	}

	/** Registers the server-stop cleanup (from {@link SaberM7Feature}). */
	static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	/** {@code collector}'s display name: "The Collector's Set". */
	public static Component setName(HiltSet set) {
		return Component.translatable("saber.oldrepublic.set." + set.id());
	}

	/** The two-part bonus of {@code set} (7.2) with its numbers. */
	public static Component twoPartBonus(HiltSet set, SaberBalance b) {
		String key = "saber.oldrepublic.set." + set.id() + ".two";
		return switch (set) {
			case COLLECTOR -> Component.translatable(key, HiltTraits.number(b.collectorSet2Parry));
			case WATCHMAN -> Component.translatable(key, HiltTraits.number(b.watchmanSet2Guard));
			case PENITENT -> Component.translatable(key, HiltTraits.number(b.penitentSet2DamagePct));
			case CHOIR -> Component.translatable(key, HiltTraits.number(b.choirSet2CostPct));
		};
	}

	/** The three-part effect of {@code set} (7.2) with its numbers. */
	public static Component threePartBonus(HiltSet set, SaberBalance b) {
		String key = "saber.oldrepublic.set." + set.id() + ".three";
		return switch (set) {
			case COLLECTOR -> Component.translatable(key, HiltTraits.number(b.collectorMarkDamagePct), HiltTraits.number(b.collectorMarkTicks / 20f));
			case WATCHMAN -> Component.translatable(key, HiltTraits.number(b.watchmanRegenPct), HiltTraits.number(b.watchmanSlowTicks / 20f));
			case PENITENT -> Component.translatable(key, HiltTraits.number(b.penitentEnergyOnHit), HiltTraits.number(b.penitentLowHealthPct),
					HiltTraits.number(b.penitentLowHealthFraction * 100f));
			case CHOIR -> Component.translatable(key, String.valueOf(b.choirPulseHits), HiltTraits.number(b.choirPulseWindowTicks / 20f),
					HiltTraits.number(b.choirPulseDamage), HiltTraits.number(b.choirPulseRadius));
		};
	}

	/** A set part's tooltip lines (3.4): the set it belongs to and both its bonuses. Nothing for a part of no set. */
	public static void partLines(HiltParts.Part part, Consumer<Component> tooltip) {
		HiltSet set = HiltSet.of(part);

		if (set == null) {
			return;
		}

		tooltip.accept(Component.translatable("saber.oldrepublic.set.part", setName(set)).withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("saber.oldrepublic.set.two", twoPartBonus(set, SHOWN)).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("saber.oldrepublic.set.three", threePartBonus(set, SHOWN)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** A saber's set lines (3.4): its active set with the parts it carries, and the bonuses that count. Nothing without a set. */
	public static void saberLines(SaberConfig config, boolean staff, Consumer<Component> tooltip) {
		HiltSet set = HiltSet.active(config, staff);

		if (set == null) {
			return;
		}

		int count = HiltSet.count(config, set, staff);
		tooltip.accept(Component.translatable("saber.oldrepublic.set.worn", setName(set), String.valueOf(count)).withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("saber.oldrepublic.set.two", twoPartBonus(set, SHOWN)).withStyle(ChatFormatting.DARK_GRAY));

		if (count >= 3) {
			tooltip.accept(Component.translatable("saber.oldrepublic.set.three", threePartBonus(set, SHOWN)).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private HiltSetEffects() {
	}
}
