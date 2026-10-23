package com.oldrepublic.force.stasis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.combat.Attitudes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Force Stasis ({@code force_stasis}, Jedi, MILESTONE_5.md 9.3): a tap at the creature under the crosshair within 16
 * blocks (the M2 single-target aim with its aim-assist cone). The target is held in {@code stasis} for 60 ticks: no AI and
 * no input, while gravity and knockback still apply ({@link StasisHolds}). One hit of 8 or more ends it, and so does the
 * 20th tick after the first hit. Stasis Field ({@code stasis_field}) also holds every hostile within 4 blocks of the
 * target, all for 40 ticks; Stasis Lock ({@code stasis_lock}) holds the one target for 100 ticks and its first two hits do
 * not count.
 *
 * <p>The lengths go through {@code ControlEffects.diminished} alone: the M2 diminishing returns on push-resistant targets,
 * a boss at 25% (multiplied with its own rule) and a {@code #oldrepublic:force_resistant} creature at half (the duration
 * modifiers {@code ForceM5Feature} registers).
 */
public final class ForceStasis implements ForceAbility {
	public static final String ID = "force_stasis";
	public static final String FIELD = "stasis_field";
	public static final String LOCK = "stasis_lock";
	/**
	 * Effect event {@code force_stasis} (15.3): toTracking the target; entity = the target; variant =
	 * {@link ForceEffects#packCaster}(caster, 0 cast / 1 held / 2 break) (the caster is 0 on a break); intensity = seconds
	 * held (variant 1).
	 */
	public static final Identifier EFFECT = OldRepublicMod.id("force_stasis");
	public static final int VARIANT_CAST = 0;
	public static final int VARIANT_HELD = 1;
	public static final int VARIANT_BREAK = 2;
	/** Creatures the Force grips poorly: every Hymnsworn and the First Cantor's choir (half duration). */
	public static final TagKey<EntityType<?>> FORCE_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("force_resistant"));

	public static StasisBalance balance() {
		return BalanceConfig.section("force_stasis", StasisBalance.class, StasisBalance::new);
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

	/** A tap: nothing to cancel (the holds run on their own). */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code range}, {@code duration_s}, {@code break_damage},
	 * {@code break_delay_s}, {@code field_radius}, {@code field_s}, {@code lock_s}, {@code lock_hits},
	 * {@code boss_pct}, {@code resistant_pct}, {@code mastery_stasis_s} (rank 3).
	 */
	@Override
	public Map<String, Float> info() {
		StasisBalance b = balance();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("duration_s", b.durationTicks / 20f);
		info.put("break_damage", b.breakDamage);
		info.put("break_delay_s", b.breakDelayTicks / 20f);
		info.put("field_radius", b.fieldRadius);
		info.put("field_s", b.fieldTicks / 20f);
		info.put("lock_s", b.lockTicks / 20f);
		info.put("lock_hits", (float) b.lockIgnoredHits);
		info.put("boss_pct", b.bossMultiplier * 100f);
		info.put("resistant_pct", b.resistantMultiplier * 100f);
		info.put("mastery_stasis_s", masteryTicks(b, 3) / 20f);
		return info;
	}

	// ---------------------------------------------------------------- rules

	/** The extra ticks at mastery {@code rank} (0 to 3). */
	public static int masteryTicks(StasisBalance b, int rank) {
		int r = Mth.clamp(rank, 0, 3);
		return r == 0 || b.masteryTicks == null || b.masteryTicks.length < r ? 0 : b.masteryTicks[r - 1];
	}

	/** The hold's length before the control rules: the shape's, plus the mastery ticks. */
	public static int ticks(StasisBalance b, boolean field, boolean lock, int rank) {
		int base = field ? b.fieldTicks : lock ? b.lockTicks : b.durationTicks;
		return base + masteryTicks(b, rank);
	}

	/**
	 * The {@code stasis} duration multiplier on {@code target} from this power's balance: {@code bossMultiplier} on a boss,
	 * {@code resistantMultiplier} on a force-resistant creature (both multiply).
	 */
	public static float durationMultiplier(LivingEntity target, StasisBalance b) {
		float mult = 1f;

		if (target.is(ConventionalEntityTypeTags.BOSSES)) {
			mult *= b.bossMultiplier;
		}

		if (target.is(FORCE_RESISTANT)) {
			mult *= b.resistantMultiplier;
		}

		return mult;
	}

	// ---------------------------------------------------------------- cast

	static void cast(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return;
		}

		StasisBalance b = balance();
		ServerLevel level = player.level();
		LivingEntity target = PullTargeting.find(level, player, player.getEyePosition(), player.getLookAngle(), b.range, b.aimAssistHalfAngle,
				e -> ForcePowers.canAffect(player, e, b.pvpEffects), e -> !Attitudes.shieldedFromArea(player, e));

		if (target == null) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_stasis.no_target"));
			return;
		}

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return;
		}

		Progression.setCooldown(player, ID, ForcePowers.cooldown(player, ID, b.cooldownTicks));
		ForceData data = Progression.get(player);
		boolean field = ForcePowers.hasNode(player, data, FIELD);
		boolean lock = !field && ForcePowers.hasNode(player, data, LOCK);
		int base = ticks(b, field, lock, ForcePowers.masteryRank(player, ID));
		EffectEvents.toTracking(target, EFFECT, ForcePowers.chest(target), null, target.getId(), base / 20f,
				ForceEffects.packCaster(player.getId(), VARIANT_CAST));
		List<LivingEntity> targets = new ArrayList<>();
		targets.add(target);

		if (field) {
			targets.addAll(fieldTargets(player, target, b));
		}

		int held = 0;

		for (LivingEntity each : targets) {
			if (StasisHolds.apply(each, player, base, lock ? b.lockIgnoredHits : 0) <= 0) {
				continue;
			}

			held++;
			QuestHooks.onAbilityHit(player, ID, each);
		}

		if (held == 0) {
			player.sendOverlayMessage(Component.translatable("ability.oldrepublic.force_stasis.resisted"));
		}

		ForcePowers.addMastery(player, ID, held);
		QuestHooks.onAbilityUsed(player, ID, (int) targets.stream().filter(e -> ForcePowers.isHostile(player, e)).count());
	}

	/** Stasis Field: every other hostile within the radius of the target, in the caster's sight. */
	private static List<LivingEntity> fieldTargets(ServerPlayer player, LivingEntity target, StasisBalance b) {
		ServerLevel level = player.level();
		AABB box = target.getBoundingBox().inflate(b.fieldRadius);
		List<LivingEntity> out = new ArrayList<>();

		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != target && e != player && e.isAlive() && e.distanceTo(target) <= b.fieldRadius
						&& ForcePowers.canAffect(player, e, b.pvpEffects) && (e instanceof Player || ForcePowers.isHostile(player, e))
						&& !Attitudes.shieldedFromArea(player, e))) {
			if (PushTargeting.hasLineOfSight(level, player, player.getEyePosition(), ForcePowers.chest(other))) {
				out.add(other);
			}
		}

		return out;
	}
}
