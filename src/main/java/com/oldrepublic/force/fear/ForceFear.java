package com.oldrepublic.force.fear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Fear ({@code force_fear}, Sith, MILESTONE_5.md 9.6): a tap that sends a ripple of dread through a 60° cone of
 * 10 blocks; every hostile in it is afraid ({@code fear}) for 80 ticks. Terror ({@code fear_terror}) reaches all around
 * the caster instead (radius 8, 60 ticks); Nightmare ({@code fear_dread}) takes one target, the crosshair pick, for 160
 * ticks, and it takes more damage while afraid. Bosses, droids and {@code #oldrepublic:fearless} creatures are immune,
 * and the M2 diminishing returns apply ({@link Frightened}).
 *
 * <p>{@link #apply} is the shared fear every source uses: Choke's Dread and the saber's Dread Presence call it too.
 */
public final class ForceFear implements ForceAbility {
	public static final String ID = "force_fear";
	public static final String TERROR = "fear_terror";
	public static final String NIGHTMARE = "fear_dread";
	/** Effect event {@code force_fear} (15.3): toTracking; variant 0 cast (entity = the caster, dir = the look, or zero for Terror; intensity = the reach), 1 a victim (entity = the victim; intensity = seconds). */
	public static final Identifier EFFECT = OldRepublicMod.id("force_fear");
	public static final int VARIANT_CAST = 0;
	public static final int VARIANT_VICTIM = 1;
	/** Creatures nothing frightens (Clan Hask warriors, the First Cantor's choir). */
	public static final TagKey<EntityType<?>> FEARLESS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("fearless"));

	public static FearBalance balance() {
		return BalanceConfig.section("force_fear", FearBalance.class, FearBalance::new);
	}

	/**
	 * The shared fear (MILESTONE_5.md 9.2, 17.1): frightens {@code target} for {@code ticks} before the control rules
	 * ({@code ControlEffects.diminished} with the {@code fear} kind), running from {@code source} when there is one.
	 * Bosses, droids and fearless creatures are immune. Returns the ticks applied, 0 when nothing took. Server side.
	 */
	public static int apply(LivingEntity target, @Nullable ServerPlayer source, int ticks) {
		return Frightened.apply(target, source, ticks, null, false);
	}

	/** {@link #apply(LivingEntity, ServerPlayer, int)}, running from {@code from} (Choke's Dread: the choked creature). */
	public static int apply(LivingEntity target, @Nullable ServerPlayer source, int ticks, Vec3 from) {
		return Frightened.apply(target, source, ticks, from, false);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.TAP;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		if (action == AbilityAction.START) {
			cast(p);
		}
	}

	/** A tap: nothing to cancel. */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code range}, {@code cone}, {@code duration_s}, {@code terror_radius},
	 * {@code terror_s}, {@code nightmare_s}, {@code nightmare_damage_pct}, {@code mastery_fear_s}.
	 */
	@Override
	public Map<String, Float> info() {
		FearBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("cone", b.coneDegrees);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("terror_radius", b.terrorRadius);
		info.put("terror_s", b.terrorTicks / 20f);
		info.put("nightmare_s", b.nightmareTicks / 20f);
		info.put("nightmare_damage_pct", b.nightmareDamagePct);
		info.put("mastery_fear_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** The fear's length before the control rules: the shape's, plus the mastery ticks. */
	public static int ticks(FearBalance b, boolean terror, boolean nightmare, int rank) {
		int base = terror ? b.terrorTicks : nightmare ? b.nightmareTicks : b.durationTicks;
		return base + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	/** Whether Force Fear may frighten {@code e}: a creature that counts as hostile, or a player when PvP effects are on. */
	static boolean eligible(ServerPlayer caster, Entity e, FearBalance b) {
		return ForcePowers.canAffect(caster, e, b.pvpEffects) && (e instanceof Player || ForcePowers.isHostile(caster, e))
				&& !Attitudes.shieldedFromArea(caster, e);
	}

	// ---------------------------------------------------------------- cast

	static void cast(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		FearBalance b = balance();
		ForceData data = Progression.get(player);
		boolean terror = ForcePowers.hasNode(player, data, TERROR);
		boolean nightmare = !terror && ForcePowers.hasNode(player, data, NIGHTMARE);
		List<LivingEntity> targets = nightmare ? single(player, b) : terror ? around(player, b) : cone(player, b);

		if (nightmare && targets.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_fear.no_target"));
			return;
		}

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		Vec3 look = player.getLookAngle();
		Vec3 dir = terror ? Vec3.ZERO : nightmare ? ForcePowers.chest(targets.getFirst()).subtract(player.getEyePosition()).normalize() : look;
		EffectEvents.toTracking(player, EFFECT, player.getEyePosition(), dir, player.getId(), terror ? b.terrorRadius : b.range, VARIANT_CAST);
		int base = ticks(b, terror, nightmare, ForcePowers.masteryRank(player, ID));
		int frightened = 0;

		for (LivingEntity target : targets) {
			int applied = Frightened.apply(target, player, base, player.position(), nightmare);

			if (applied <= 0) {
				continue;
			}

			frightened++;
			EffectEvents.toTracking(target, EFFECT, target.position(), null, target.getId(), applied / 20f, VARIANT_VICTIM);
			QuestHooks.onAbilityHit(player, ID, target);
		}

		if (frightened == 0 && !targets.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_fear.unmoved"));
		}

		ForcePowers.addMastery(player, ID, frightened);
		QuestHooks.onAbilityUsed(player, ID, frightened);
	}

	/** Every eligible creature in the cone, in sight. */
	private static List<LivingEntity> cone(ServerPlayer player, FearBalance b) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		return near(player, b.range, e -> PushTargeting.inShape(eye, look, ForcePowers.chest(e), b.range, b.coneDegrees / 2f, false), b);
	}

	/** Terror: every eligible creature within the radius, in sight. */
	private static List<LivingEntity> around(ServerPlayer player, FearBalance b) {
		Vec3 eye = player.getEyePosition();
		return near(player, b.terrorRadius, e -> PushTargeting.inShape(eye, Vec3.ZERO, ForcePowers.chest(e), b.terrorRadius, 180.0, true), b);
	}

	/** Nightmare: the crosshair pick (the M2 Pull aim). */
	private static List<LivingEntity> single(ServerPlayer player, FearBalance b) {
		LivingEntity target = PullTargeting.find(player.level(), player, player.getEyePosition(), player.getLookAngle(), b.range, b.aimAssistHalfAngle,
				e -> eligible(player, e, b), e -> true);
		return target == null ? List.of() : List.of(target);
	}

	private static List<LivingEntity> near(ServerPlayer player, float radius, Predicate<LivingEntity> inShape, FearBalance b) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		List<LivingEntity> out = new ArrayList<>();
		AABB box = player.getBoundingBox().inflate(radius + 1.0);

		for (Entity entity : level.getEntities(player, box, e -> e instanceof LivingEntity && eligible(player, e, b))) {
			LivingEntity living = (LivingEntity) entity;

			if (inShape.test(living) && PushTargeting.hasLineOfSight(level, player, eye, ForcePowers.chest(living))) {
				out.add(living);
			}
		}

		return out;
	}
}
