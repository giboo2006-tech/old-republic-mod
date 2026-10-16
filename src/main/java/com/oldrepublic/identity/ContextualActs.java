package com.oldrepublic.identity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Detects the small, everyday acts that move alignment, so no ability has to report that it was
 * used: hurting or killing innocents (villagers, wandering traders, Frontier Settlers), defending
 * them from hostiles, and Force cruelty to passive animals. Abilities report only
 * {@link ContextAct#HELPED_OTHER}. Ordinary combat against hostiles, farming and harvesting never
 * move alignment.
 */
public final class ContextualActs {
	/**
	 * Player Force damage that counts as cruelty against passive animals, and that captive settlers
	 * take reduced damage from (Force Lightning, the Force Jump shockwave, and Push slams once they
	 * use a tagged type). {@code data/oldrepublic/tags/damage_type/force_harm.json}.
	 */
	public static final TagKey<DamageType> FORCE_HARM = TagKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_harm"));

	/** (player, target) to the game time the act last counted, for the per-target repeat limit. */
	private static final Map<String, Long> LAST_HARM = new HashMap<>();
	/** Hostile entity to the game time it last hurt an innocent. */
	private static final Map<UUID, Long> THREATS = new HashMap<>();

	/** Whether killing or hurting this entity is an act against an innocent. */
	public static boolean isInnocent(Entity entity) {
		return entity.getType() == EntityType.VILLAGER || entity.getType() == EntityType.WANDERING_TRADER || entity instanceof FrontierSettler;
	}

	/** Applies one act: decides the amount, the cap and the per-target repeat limit. */
	static void report(ServerPlayer p, ContextAct act, @Nullable Entity target) {
		IdentityBalance b = Identity.balance();

		if (!b.contextualAlignment) {
			return;
		}

		long now = p.level().getGameTime();

		if ((act == ContextAct.HARMED_INNOCENT || act == ContextAct.CRUELTY) && target != null) {
			String key = p.getUUID() + "/" + target.getUUID() + "/" + act;
			Long last = LAST_HARM.get(key);

			if (last != null && now - last < b.harmRepeatTicks) {
				return;
			}

			LAST_HARM.put(key, now);
		}

		int amount = switch (act) {
			case HELPED_OTHER -> b.helpedOther;
			case DEFENDED_INNOCENT -> b.defendedInnocent;
			case CRUELTY -> b.cruelty;
			case HARMED_INNOCENT -> b.harmedInnocent;
			case KILLED_INNOCENT -> target instanceof FrontierSettler ? b.killedSettler : b.killedVillager;
		};

		IdentityRules.Cap cap = act == ContextAct.KILLED_INNOCENT ? IdentityRules.Cap.SEVERE : IdentityRules.Cap.MINOR;
		String name = act.name().toLowerCase(Locale.ROOT);
		Identity.applyShift(p, "context." + name, amount, cap, "alignment.oldrepublic.reason." + name);
	}

	/** Server damage hook: harming innocents, hostiles threatening them, cruelty to animals. */
	static void onDamaged(LivingEntity victim, DamageSource source, float damageTaken) {
		if (damageTaken <= 0f) {
			return;
		}

		Entity attacker = source.getEntity();

		if (isInnocent(victim)) {
			if (attacker instanceof Enemy && attacker != victim) {
				THREATS.put(attacker.getUUID(), victim.level().getGameTime());
			} else if (attacker instanceof ServerPlayer player && victim.isAlive()) {
				Identity.contextual(player, ContextAct.HARMED_INNOCENT, victim);
			}

			return;
		}

		if (attacker instanceof ServerPlayer player && victim.getType().getCategory() == MobCategory.CREATURE && !(victim instanceof Enemy)
				&& source.is(FORCE_HARM)) {
			Identity.contextual(player, ContextAct.CRUELTY, victim);
		}
	}

	/** Server death hook: killing innocents, or killing a hostile that was threatening one. */
	static void onDeath(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		ServerPlayer player = attacker instanceof ServerPlayer sp ? sp
				: victim.getLastHurtByPlayer() instanceof ServerPlayer sp ? sp : null;

		if (isInnocent(victim)) {
			if (player != null) {
				Identity.contextual(player, ContextAct.KILLED_INNOCENT, victim);

				if (victim instanceof FrontierSettler) {
					Identity.changeReputation(player, Faction.FRONTIER, Identity.balance().settlerKilledRep, "settler_killed", false);
				}
			}

			return;
		}

		Long threatened = THREATS.remove(victim.getUUID());

		if (threatened != null && attacker instanceof ServerPlayer killer
				&& victim.level().getGameTime() - threatened <= Identity.balance().defendWindowTicks) {
			Identity.contextual(killer, ContextAct.DEFENDED_INNOCENT, victim);
		}
	}

	/** Drops expired bookkeeping once a second. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		IdentityBalance b = Identity.balance();
		THREATS.values().removeIf(t -> now - t > b.defendWindowTicks || t > now);

		for (Iterator<Long> it = LAST_HARM.values().iterator(); it.hasNext();) {
			long t = it.next();

			if (now - t > b.harmRepeatTicks || t > now) {
				it.remove();
			}
		}
	}

	static void clear() {
		LAST_HARM.clear();
		THREATS.clear();
	}

	private ContextualActs() {
	}
}
