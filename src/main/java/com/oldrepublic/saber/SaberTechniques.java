package com.oldrepublic.saber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.network.SaberActionPayload;
import com.oldrepublic.skill.SkillPassives;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The heavy attack and the lunge (spec section 9.3), started from {@code saber_action} intents and
 * run on the server tick. Both spend saber stamina ({@link SaberGuard#trySpend}), cannot be parried
 * and go through {@link SaberCombat#strike}. Also runs the fear of {@code dread_presence}.
 */
public final class SaberTechniques {
	/** Movement penalty during the heavy wind-up. */
	public static final Identifier HEAVY_SLOW_ID = OldRepublicMod.id("heavy_windup_slow");

	private record Lunge(int targetId, long start) {
	}

	private record Fear(Vec3 from, long until) {
	}

	/** Player to the game time the heavy strike lands. */
	private static final Map<UUID, Long> HEAVY = new HashMap<>();
	private static final Map<UUID, Lunge> LUNGES = new HashMap<>();
	private static final Map<UUID, Long> HEAVY_READY = new HashMap<>();
	private static final Map<UUID, Long> LUNGE_READY = new HashMap<>();
	private static final Map<PathfinderMob, Fear> FEAR = new WeakHashMap<>();

	/** Handles a {@code saber_action} intent. */
	public static void onAction(ServerPlayer player, SaberActionPayload payload) {
		switch (payload.action()) {
			case HEAVY -> startHeavy(player);
			case LUNGE -> {
				if (player.level().getEntity(payload.targetId()) instanceof LivingEntity target) {
					startLunge(player, target);
				}
			}
			case FORM_CYCLE -> SaberForms.cycle(player);
		}
	}

	/** True during a heavy wind-up or a lunge: the player cannot attack, guard or start another. */
	public static boolean isBusy(ServerPlayer player) {
		return HEAVY.containsKey(player.getUUID()) || LUNGES.containsKey(player.getUUID());
	}

	public static boolean isWindingUp(Player player) {
		return HEAVY.containsKey(player.getUUID());
	}

	/** Whether a lit, un-thrown saber is in the main hand and nothing else blocks a technique. */
	private static boolean canAct(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && SaberComponents.isUsable(player.getMainHandItem())
				&& !isBusy(player) && !SaberForms.isSwitching(player);
	}

	/** Starts a heavy attack. Returns false if it was refused (no lit saber, cooldown, stamina). */
	public static boolean startHeavy(ServerPlayer player) {
		SaberBalance b = SaberToggle.balance();
		long now = player.level().getGameTime();

		if (!canAct(player) || now < HEAVY_READY.getOrDefault(player.getUUID(), 0L) || !SaberGuard.trySpend(player, b.heavyStaminaCost)) {
			return false;
		}

		int windup = SaberPassives.form(player).modifiers(b).heavyWindupTicks();
		player.stopUsingItem();
		HEAVY.put(player.getUUID(), now + windup);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			// API-CHECK: AttributeInstance#addOrUpdateTransientModifier / #removeModifier(Identifier) (1.21 names).
			speed.addOrUpdateTransientModifier(new AttributeModifier(HEAVY_SLOW_ID, b.heavyMoveMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}

		EffectEvents.toTracking(player, SaberEffects.HEAVY, player.getEyePosition(), player.getLookAngle(), player.getId(), windup, SaberEffects.HEAVY_WINDUP);
		return true;
	}

	/** Cancels a heavy wind-up (hit for enough damage, saber lost). The stamina stays spent. */
	public static void cancelHeavy(ServerPlayer player) {
		if (HEAVY.remove(player.getUUID()) != null) {
			removeSlow(player);
			EffectEvents.toTracking(player, SaberEffects.HEAVY, player.getEyePosition(), player.getLookAngle(), player.getId(), 0f, SaberEffects.HEAVY_CANCEL);
		}
	}

	/** Starts a lunge at {@code target}. Returns false if it was refused. */
	public static boolean startLunge(ServerPlayer player, LivingEntity target) {
		SaberBalance b = SaberToggle.balance();
		SaberForm.Modifiers form = SaberPassives.form(player).modifiers(b);
		long now = player.level().getGameTime();

		if (!canAct(player) || !form.lungeAllowed() || !SaberPassives.has(player, SaberPassives.LUNGE)
				|| now < LUNGE_READY.getOrDefault(player.getUUID(), 0L) || !validLungeTarget(player, target, form.lungeRange() + 0.5f)
				|| !SaberGuard.trySpend(player, form.lungeCost())) {
			return false;
		}

		player.stopUsingItem();
		LUNGES.put(player.getUUID(), new Lunge(target.getId(), now));
		LUNGE_READY.put(player.getUUID(), now + form.lungeCooldown());
		EffectEvents.toTracking(player, SaberEffects.LUNGE, player.position(), target.position().subtract(player.position()).normalize(),
				player.getId(), 1f, SaberEffects.LUNGE_START);
		return true;
	}

	/** The server-side lunge check: a living target within {@code maxRange} in line of sight. */
	public static boolean validLungeTarget(ServerPlayer player, LivingEntity target, float maxRange) {
		return target != player && target.isAlive() && !target.isSpectator() && target.level() == player.level()
				&& player.distanceTo(target) <= maxRange && player.hasLineOfSight(target);
	}

	/** Called by the damage pipeline when the player takes damage. */
	static void onHurt(ServerPlayer player, float amount) {
		if (amount >= SaberToggle.balance().heavyInterruptDamage) {
			cancelHeavy(player);
		}
	}

	/** Once per server tick per player. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();

		if ((HEAVY.containsKey(id) || LUNGES.containsKey(id)) && !SaberComponents.isUsable(player.getMainHandItem())) {
			cancelHeavy(player);
			LUNGES.remove(id);
			return;
		}

		Long strikeAt = HEAVY.get(id);

		if (strikeAt != null && player.level().getGameTime() >= strikeAt) {
			HEAVY.remove(id);
			removeSlow(player);
			heavyStrike(player);
		}

		Lunge lunge = LUNGES.get(id);

		if (lunge != null) {
			tickLunge(player, lunge);
		}
	}

	private static void heavyStrike(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		SaberBalance b = SaberToggle.balance();
		double reach = player.entityInteractionRange() + b.heavyReachBonus;
		Vec3 eye = player.getEyePosition();
		AABB box = player.getBoundingBox().inflate(reach);
		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
				&& !e.isSpectator() && e.getBoundingBox().distanceToSqr(eye) <= reach * reach
				&& SaberGuard.inFront(player, e.position(), b.heavyArcDegrees) && player.hasLineOfSight(e)));
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

		if (targets.size() > b.heavyMaxTargets) {
			targets = targets.subList(0, b.heavyMaxTargets);
		}

		player.swing(InteractionHand.MAIN_HAND, true);
		HEAVY_READY.put(player.getUUID(), level.getGameTime() + b.heavyCooldownTicks);
		Vec3 centre = eye.add(player.getLookAngle().scale(reach * 0.6));
		EffectEvents.toTracking(player, SaberEffects.HEAVY, centre, player.getLookAngle(), player.getId(), targets.size(), SaberEffects.HEAVY_STRIKE);

		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * b.heavyDamageMult;
		SaberCombat.Strike strike = SaberCombat.Strike.heavy(b);
		int fear = SkillPassives.dreadFleeTicks(player);

		for (LivingEntity target : targets) {
			SaberCombat.strike(target, level, player.damageSources().playerAttack(player), damage, strike);

			if (!isBoss(target)) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.heavyStaggerTicks, 3));
				ForceReactions.interruptWindup(target, player, ForceReactions.CAUSE_HEAVY_FEAR);

				if (fear > 0 && ForcePowers.isHostile(player, target) && target instanceof PathfinderMob mob) {
					FEAR.put(mob, new Fear(player.position(), level.getGameTime() + fear));
				}
			}
		}

		QuestHooks.onSaberTechnique(player, "heavy", targets.isEmpty() ? null : targets.get(0), !targets.isEmpty());
	}

	private static void tickLunge(ServerPlayer player, Lunge lunge) {
		SaberBalance b = SaberToggle.balance();
		ServerLevel level = (ServerLevel) player.level();
		Entity entity = level.getEntity(lunge.targetId);
		long elapsed = level.getGameTime() - lunge.start;

		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			endLunge(player, null, false);
			return;
		}

		if (player.distanceTo(target) <= b.lungeHitDistance) {
			player.swing(InteractionHand.MAIN_HAND, true);
			float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * b.lungeDamageMult;
			SaberCombat.strike(target, level, player.damageSources().playerAttack(player), damage, SaberCombat.Strike.lunge());
			Vec3 hit = target.getBoundingBox().getCenter().lerp(player.getEyePosition(), 0.3);
			EffectEvents.toTracking(player, SaberEffects.LUNGE, hit, player.getLookAngle(), player.getId(), 1f, SaberEffects.LUNGE_HIT);
			endLunge(player, target, true);
			return;
		}

		if (elapsed >= b.lungeMaxTicks) {
			EffectEvents.toTracking(player, SaberEffects.LUNGE, player.position(), player.getLookAngle(), player.getId(), 1f, SaberEffects.LUNGE_MISS);
			endLunge(player, target, false);
			return;
		}

		Vec3 to = target.position().subtract(player.position()).horizontal();

		if (to.lengthSqr() > 1.0e-4) {
			Vec3 dash = to.normalize().scale(b.lungeSpeed);
			player.setDeltaMovement(dash.x, Math.min(player.getDeltaMovement().y, 0.1), dash.z);
			player.hurtMarked = true;
		}
	}

	private static void endLunge(ServerPlayer player, @Nullable LivingEntity target, boolean hit) {
		LUNGES.remove(player.getUUID());
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
		player.hurtMarked = true;
		QuestHooks.onSaberTechnique(player, "lunge", target, hit);
	}

	/** {@code dread_presence}: feared mobs drop their target and run from the player. Once per server tick. */
	static void tickFear() {
		Iterator<Map.Entry<PathfinderMob, Fear>> it = FEAR.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<PathfinderMob, Fear> entry = it.next();
			PathfinderMob mob = entry.getKey();

			if (!mob.isAlive() || mob.level().getGameTime() >= entry.getValue().until) {
				it.remove();
				continue;
			}

			mob.setTarget(null);

			if (mob.getNavigation().isDone()) {
				Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, entry.getValue().from);

				if (away != null) {
					mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
				}
			}
		}
	}

	private static boolean isBoss(LivingEntity entity) {
		// Entity#is(TagKey) checks the c:bosses convention tag (EntityType has no is(TagKey) in 26.2).
		return entity.is(ConventionalEntityTypeTags.BOSSES);
	}

	private static void removeSlow(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			speed.removeModifier(HEAVY_SLOW_ID);
		}
	}

	static void forget(ServerPlayer player) {
		if (HEAVY.remove(player.getUUID()) != null) {
			removeSlow(player);
		}

		LUNGES.remove(player.getUUID());
		HEAVY_READY.remove(player.getUUID());
		LUNGE_READY.remove(player.getUUID());
	}

	private SaberTechniques() {
	}
}
