package com.oldrepublic.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Awakening: a short dream (client overlay), then an uncontrolled Force burst, the journal and
 * the move to the Focus stage. Dream timers are transient: if the player leaves mid-dream nothing
 * is saved and the trigger simply fires again later.
 */
final class Awakening {
	/** Remaining dream ticks per dreaming player. */
	private static final Map<UUID, Integer> DREAMING = new HashMap<>();
	/** {@code tickCount} at which each sleeping player lay down. */
	private static final Map<UUID, Integer> SLEEP_START = new HashMap<>();

	static void onDue(ServerPlayer player, String reason) {
		ForceData data = Progression.get(player);

		if (!QuestRules.awakeningCanStart(data, DREAMING.containsKey(player.getUUID())) || !player.isAlive()) {
			return;
		}

		if ("time".equals(reason) && !isSafe(player)) {
			// Progression asks again every tick; wait for a calm moment instead of dreaming mid-fight.
			return;
		}

		int ticks = QuestFeature.balance().dreamTicks;
		DREAMING.put(player.getUUID(), ticks);
		EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), ticks, QuestEffects.DREAM_START);
	}

	private static boolean isSafe(ServerPlayer player) {
		QuestBalance b = QuestFeature.balance();
		int lastHurt = player.getLastHurtByMobTimestamp();
		boolean recentlyHurt = lastHurt > 0 && player.tickCount - lastHurt < b.awakeningSafeTicks;
		boolean grounded = player.onGround() || player.isPassenger();
		return !recentlyHurt && grounded && !player.isInLava() && !player.isSleeping();
	}

	static void tick(ServerPlayer player) {
		Integer left = DREAMING.get(player.getUUID());

		if (left == null) {
			return;
		}

		if (left <= 1) {
			DREAMING.remove(player.getUUID());
			burst(player);
		} else {
			DREAMING.put(player.getUUID(), left - 1);
		}
	}

	/** Taking damage wakes the dreamer at once: the overlay is cancelled and the burst happens now. */
	static void onDamaged(ServerPlayer player) {
		if (DREAMING.remove(player.getUUID()) != null) {
			EffectEvents.toPlayer(player, QuestEffects.AWAKENING_DREAM, player.position(), null, player.getId(), 0, QuestEffects.DREAM_CANCEL);

			if (player.isAlive()) {
				burst(player);
			}
		}
	}

	static void onStartSleeping(ServerPlayer player) {
		SLEEP_START.put(player.getUUID(), player.tickCount);
	}

	static void onStopSleeping(ServerPlayer player) {
		Integer start = SLEEP_START.remove(player.getUUID());

		if (start != null && player.tickCount - start >= QuestFeature.balance().awakeningMinSleepTicks) {
			QuestHooks.onAwakeningDue(player, "sleep");
		}
	}

	static void forget(ServerPlayer player) {
		DREAMING.remove(player.getUUID());
		SLEEP_START.remove(player.getUUID());
	}

	static void clear() {
		DREAMING.clear();
		SLEEP_START.clear();
	}

	private static void burst(ServerPlayer player) {
		if (Progression.get(player).isAwakened()) {
			return;
		}

		QuestBalance b = QuestFeature.balance();
		ServerLevel level = player.level();
		Vec3 center = player.position();
		double radius = b.burstRadius;
		AABB box = player.getBoundingBox().inflate(radius);

		for (Entity entity : level.getEntities(player, box, e -> e.isAlive() && e.distanceToSqr(center) <= radius * radius)) {
			Vec3 away = entity.position().subtract(center).horizontal();
			Vec3 dir = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : away.normalize();
			double falloff = 1.0 - Math.min(1.0, Math.sqrt(entity.distanceToSqr(center)) / radius) * 0.6;

			if (entity instanceof ItemEntity) {
				entity.push(dir.x * 0.25 * falloff, 0.25 * falloff, dir.z * 0.25 * falloff);
				entity.hurtMarked = true;
			} else if (entity instanceof LivingEntity && !(entity instanceof Player) && entity.getBbWidth() <= b.burstMaxEntityWidth) {
				entity.push(dir.x * 0.6 * falloff, 0.2 * falloff, dir.z * 0.6 * falloff);
				entity.hurtMarked = true;
			}
		}

		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.burstSlownessTicks, 1));
		EffectEvents.around(level, QuestEffects.AWAKENING_BURST, center, null, player.getId(), (float) radius, 0);

		ForceData data = Progression.get(player);
		QuestRules.advance(data, QuestStage.FOCUS, QuestStage.STIRRING);
		Progression.commit(player, data);

		Journal.giveIfMissing(player);
		Journal.unlockEntry(player, JournalEntries.THE_DREAM);
		Journal.unlockCodex(player, JournalEntries.CODEX_THE_FORCE);
		Progression.grantResonanceMilestone(player, "awakening", b.awakeningResonance);
	}

	private Awakening() {
	}
}
