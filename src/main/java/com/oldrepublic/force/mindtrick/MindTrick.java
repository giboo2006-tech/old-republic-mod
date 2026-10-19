package com.oldrepublic.force.mindtrick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * Mind Trick ({@code force_mind_trick}, Exploration, every path, MILESTONE_5.md 9.8): a tap at the creature under the
 * crosshair within 8 blocks. A weak mind ({@code #oldrepublic:mind_trickable} and not {@code #oldrepublic:strong_willed},
 * never a boss) is pacified for 120 ticks: it stops fighting and wanders ({@link Pacified}). Any other mind refuses it
 * with a line and nothing is spent. Confusion ({@code mind_trick_confuse}) turns the target on its nearest ally for 80
 * ticks instead; Many Minds ({@code mind_trick_mass}) pacifies up to three weak minds within 6 blocks of the first, for
 * 80 ticks. Out of combat the dialogue action {@code mind_trick:use} spends the cost and cooldown and moves the
 * conversation on ({@link PowerDialogues}).
 */
public final class MindTrick implements ForceAbility {
	public static final String ID = "force_mind_trick";
	public static final String CONFUSE = "mind_trick_confuse";
	public static final String MASS = "mind_trick_mass";
	/**
	 * Effect event {@code force_mind_trick} (15.3): toTracking the target; entity = the target; variant =
	 * {@link ForceEffects#packCaster}(caster, value): 0 success (intensity = seconds; 0 for a dialogue use), 1 refused,
	 * 2 the calm ended early (a hit; no caster), 3 success under Confusion (the target turns on its own side, so it does
	 * not lower its guard).
	 */
	public static final Identifier EFFECT = OldRepublicMod.id("force_mind_trick");
	public static final int VARIANT_SUCCESS = 0;
	public static final int VARIANT_REFUSED = 1;
	public static final int VARIANT_ENDED = 2;
	public static final int VARIANT_CONFUSED = 3;
	/** Weak minds: troopers and salvage crews, poachers, the cartel and its wall guards. */
	public static final TagKey<EntityType<?>> MIND_TRICKABLE = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("mind_trickable"));
	/** Minds too strong for it (Clan Hask warriors, Vessba), even when listed as weak. */
	public static final TagKey<EntityType<?>> STRONG_WILLED = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("strong_willed"));

	public static MindTrickBalance balance() {
		return BalanceConfig.section("force_mind_trick", MindTrickBalance.class, MindTrickBalance::new);
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
	 * Keys: {@code cost}, {@code cooldown_s}, {@code range}, {@code duration_s}, {@code confuse_s}, {@code mass_targets},
	 * {@code mass_radius}, {@code mass_s}, {@code mastery_trick_s}.
	 */
	@Override
	public Map<String, Float> info() {
		MindTrickBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("confuse_s", b.confuseTicks / 20f);
		info.put("mass_targets", (float) b.massTargets);
		info.put("mass_radius", b.massRadius);
		info.put("mass_s", b.massTicks / 20f);
		info.put("mastery_trick_s", b.masteryTicksPerRank / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** Whether a trick can take {@code target}'s mind: a weak mind, not a strong will, never a boss. */
	public static boolean trickable(LivingEntity target) {
		return target instanceof Mob && target.is(MIND_TRICKABLE) && !target.is(STRONG_WILLED) && !target.is(ConventionalEntityTypeTags.BOSSES);
	}

	/** The trick's length before the control rules: the shape's, plus the mastery ticks. */
	public static int ticks(MindTrickBalance b, boolean confuse, boolean mass, int rank) {
		int base = confuse ? b.confuseTicks : mass ? b.massTicks : b.durationTicks;
		return base + b.masteryTicksPerRank * Mth.clamp(rank, 0, 3);
	}

	// ---------------------------------------------------------------- cast

	static void cast(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		MindTrickBalance b = balance();
		ServerLevel level = player.level();
		LivingEntity target = PullTargeting.find(level, player, player.getEyePosition(), player.getLookAngle(), b.range, b.aimAssistHalfAngle,
				e -> ForcePowers.canAffect(player, e, b.pvpEffects), e -> e instanceof Mob);

		if (target == null) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_mind_trick.no_target"));
			return;
		}

		if (!trickable(target)) {
			// The mind is closed: nothing is spent.
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_mind_trick.closed"));
			EffectEvents.toTracking(target, EFFECT, ForcePowers.chest(target), null, target.getId(), 0f,
					ForceEffects.packCaster(player.getId(), VARIANT_REFUSED));
			return;
		}

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		ForceData data = Progression.get(player);
		boolean confuse = ForcePowers.hasNode(player, data, CONFUSE);
		boolean mass = !confuse && ForcePowers.hasNode(player, data, MASS);
		int base = ticks(b, confuse, mass, ForcePowers.masteryRank(player, ID));
		List<Mob> minds = new ArrayList<>();
		minds.add((Mob) target);

		if (mass) {
			minds.addAll(others((Mob) target, player, b));
		}

		int tricked = 0;

		for (Mob mind : minds) {
			int applied = Pacified.apply(mind, player, base, confuse);

			if (applied <= 0) {
				continue;
			}

			tricked++;
			EffectEvents.toTracking(mind, EFFECT, ForcePowers.chest(mind), null, mind.getId(), applied / 20f,
					ForceEffects.packCaster(player.getId(), confuse ? VARIANT_CONFUSED : VARIANT_SUCCESS));
			QuestHooks.onAbilityHit(player, ID, mind);
		}

		if (tricked == 0) {
			// Every mind had its fill of tricks (the diminishing returns).
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_mind_trick.resisted"));
		} else {
			ForcePowers.addMastery(player, ID, 1);
		}

		QuestHooks.onAbilityUsed(player, ID, tricked);
	}

	/** Many Minds: the other weak minds within the radius of the first, nearest first, in sight of the caster. */
	private static List<Mob> others(Mob first, ServerPlayer player, MindTrickBalance b) {
		AABB box = first.getBoundingBox().inflate(b.massRadius);
		ServerLevel level = player.level();
		List<Mob> near = new ArrayList<>(level.getEntitiesOfClass(Mob.class, box,
				m -> m != first && m.isAlive() && m.distanceTo(first) <= b.massRadius && trickable(m) && ForcePowers.canAffect(player, m, b.pvpEffects)
						&& PushTargeting.hasLineOfSight(level, player, player.getEyePosition(), ForcePowers.chest(m))));
		near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(first)));
		return near.subList(0, Math.min(near.size(), Math.max(0, b.massTargets - 1)));
	}
}
