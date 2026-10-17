package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.registry.M3Items;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The effects of the Milestone 3 nodes that act in combat (spec 10.3 and 10.4), beside the attribute
 * passives of {@link SkillAttributes}:
 * <ul>
 * <li>{@code watchers_insight}: Force Heal costs 10% less (a {@link ForceModifier});</li>
 * <li>{@code tomb_hunger}: a kill within 60 ticks of hitting that target with a power restores 6 FE
 * (through {@link EnergyRefunds}, shared with {@code hungering_dark} under the per-kill cap);</li>
 * <li>{@code crackling_grasp}: after Force Lightning ends, everything its arcs struck is 30% slower
 * for 40 ticks (a transient speed modifier);</li>
 * <li>{@code archivists_hunger}: Push and Lightning deal 15% more to targets above 75% health
 * (asked by the powers through {@link #archivistsHunger});</li>
 * <li>{@code scavenger}: trooper and sentinel kills have a 35% chance of one extra crafting material.</li>
 * </ul>
 * The Mending Breath cleanse lives in Force Heal and the Archivist's Road numbers in Sense and Lift.
 * Server side.
 */
public final class SkillNodeEffects {
	/** Transient speed modifier of {@code crackling_grasp}. */
	public static final Identifier CRACKLING_MODIFIER = OldRepublicMod.id("crackling_slow");
	/** Ruin sentinels count for {@code scavenger} (W5's entity, looked up by id). */
	private static final Identifier RUIN_SENTINEL = OldRepublicMod.id("ruin_sentinel");
	private static final int PRUNE_INTERVAL = 100;

	/** A power hit of one caster on one victim. */
	private record Hit(UUID caster, UUID victim) {
	}

	/** A creature slowed by {@code crackling_grasp} until game tick {@code until}. */
	private record Slowed(LivingEntity entity, long until) {
	}

	private static final Map<Hit, Long> HITS = new HashMap<>();
	private static final Map<UUID, Slowed> SLOWED = new HashMap<>();

	static void init() {
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float costMultiplier(Player p, String abilityId) {
				return watchersInsight(p, abilityId);
			}
		});
		CombatEvents.POWER_HIT.register(SkillNodeEffects::onPowerHit);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> unslow(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	// ---------------------------------------------------------------- watchers_insight

	/** {@code watchers_insight}: the Force Heal cost multiplier for {@code p}, else 1. */
	static float watchersInsight(Player p, String abilityId) {
		return ForceHeal.ID.equals(abilityId) && Skills.active(p, SkillIds.WATCHERS_INSIGHT)
				? Math.max(0f, 1f - SkillService.balance().watchersInsightHealCost) : 1f;
	}

	// ---------------------------------------------------------------- tomb_hunger and scavenger

	private static void onPowerHit(ServerPlayer caster, String abilityId, LivingEntity target) {
		if (Skills.rank(caster, SkillIds.TOMB_HUNGER) > 0) {
			HITS.put(new Hit(caster.getUUID(), target.getUUID()), caster.level().getGameTime());
		}
	}

	/** Pure: whether a kill at {@code now} still follows a power hit at {@code hitTick} closely enough. */
	public static boolean tombHungerDue(long hitTick, long now, int windowTicks) {
		return now >= hitTick && now - hitTick <= windowTicks;
	}

	/** A creature died: {@code tomb_hunger} and {@code scavenger} for the player who killed it. */
	static void onKill(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || victim == killer) {
			HITS.keySet().removeIf(hit -> hit.victim().equals(victim.getUUID()));
			return;
		}

		tombHunger(killer, victim, source);
		HITS.keySet().removeIf(hit -> hit.victim().equals(victim.getUUID()));
		scavenge(killer, victim, victim.getRandom());
	}

	/**
	 * {@code tomb_hunger}: restores {@code tombHungerFe} when {@code killer} hit {@code victim} with a
	 * power within the window, or the killing blow itself was a power. Returns the energy restored.
	 */
	static float tombHunger(ServerPlayer killer, LivingEntity victim, DamageSource source) {
		if (!Skills.active(killer, SkillIds.TOMB_HUNGER)) {
			return 0f;
		}

		SkillBalance b = SkillService.balance();
		Long hit = HITS.get(new Hit(killer.getUUID(), victim.getUUID()));
		boolean byPower = source.is(ForceDamage.LIGHTNING) || source.is(ForceDamage.IMPACT);

		if (!byPower && (hit == null || !tombHungerDue(hit, killer.level().getGameTime(), b.tombHungerWindowTicks))) {
			return 0f;
		}

		return EnergyRefunds.forKill(killer, victim, b.tombHungerFe);
	}

	/**
	 * {@code scavenger}: a trooper or sentinel {@code killer} killed drops one extra material with
	 * {@code scavengerChance}: a durasteel plate from a trooper, lumen thread or grave silk from a
	 * sentinel. Never commendations or armour. Returns the dropped stack, or empty.
	 */
	public static ItemStack scavenge(ServerPlayer killer, LivingEntity victim, RandomSource random) {
		boolean trooper = Troopers.isTrooper(victim);
		boolean sentinel = RUIN_SENTINEL.equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));

		if (!trooper && !sentinel || !Skills.active(killer, SkillIds.SCAVENGER) || !(victim.level() instanceof ServerLevel level)) {
			return ItemStack.EMPTY;
		}

		Item material = scavengerDrop(trooper, random.nextFloat(), random.nextFloat(), SkillService.balance());

		if (material == null) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = new ItemStack(material);
		victim.spawnAtLocation(level, stack.copy());
		return stack;
	}

	/** Pure: what a scavenged kill drops for the two rolls (each 0 to 1), or null for nothing. */
	public static @Nullable Item scavengerDrop(boolean trooper, float chanceRoll, float pickRoll, SkillBalance b) {
		if (chanceRoll >= b.scavengerChance) {
			return null;
		}

		if (trooper) {
			return M3Items.DURASTEEL_PLATE;
		}

		return pickRoll < 0.5f ? M3Items.LUMEN_THREAD : M3Items.GRAVE_SILK;
	}

	// ---------------------------------------------------------------- crackling_grasp

	/**
	 * {@code crackling_grasp}: Force Lightning by {@code caster} ended; every living creature in
	 * {@code struck} that its arcs hit is slowed for {@code cracklingTicks}. Does nothing without the
	 * node. Returns how many were slowed.
	 */
	public static int crackle(ServerPlayer caster, Collection<LivingEntity> struck) {
		if (struck.isEmpty() || !Skills.active(caster, SkillIds.CRACKLING_GRASP)) {
			return 0;
		}

		SkillBalance b = SkillService.balance();
		long until = caster.level().getGameTime() + Math.max(1, b.cracklingTicks);
		int slowed = 0;

		for (LivingEntity target : struck) {
			AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);

			if (speed == null || !target.isAlive() || target == caster) {
				continue;
			}

			speed.addOrUpdateTransientModifier(new AttributeModifier(CRACKLING_MODIFIER, -Math.max(0f, b.cracklingSlow),
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			SLOWED.put(target.getUUID(), new Slowed(target, until));
			slowed++;
		}

		return slowed;
	}

	/** Whether {@code entity} is slowed by {@code crackling_grasp} right now. */
	public static boolean crackling(Entity entity) {
		Slowed slowed = SLOWED.get(entity.getUUID());
		return slowed != null && slowed.entity() == entity;
	}

	private static void unslow(Entity entity) {
		Slowed slowed = SLOWED.remove(entity.getUUID());

		if (slowed != null) {
			AttributeInstance speed = slowed.entity().getAttribute(Attributes.MOVEMENT_SPEED);

			if (speed != null) {
				speed.removeModifier(CRACKLING_MODIFIER);
			}
		}
	}

	// ---------------------------------------------------------------- archivists_hunger

	/**
	 * {@code archivists_hunger}: the damage multiplier for {@code caster}'s Push or Lightning hit on
	 * {@code target}: +15% while the target is above 75% of its max health, else 1. Ask before the
	 * damage lands.
	 */
	public static float archivistsHunger(ServerPlayer caster, LivingEntity target) {
		if (!Skills.active(caster, SkillIds.ARCHIVISTS_HUNGER)) {
			return 1f;
		}

		SkillBalance b = SkillService.balance();
		return target.getHealth() > target.getMaxHealth() * b.archivistsHungerThreshold ? 1f + b.archivistsHungerBonus : 1f;
	}

	// ---------------------------------------------------------------- upkeep

	/** Ends expired slows and forgets old power hits. Called every server tick. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();

		if (!SLOWED.isEmpty()) {
			List<Entity> done = new ArrayList<>();

			for (Slowed slowed : SLOWED.values()) {
				if (now >= slowed.until() || !slowed.entity().isAlive() || slowed.entity().isRemoved()) {
					done.add(slowed.entity());
				}
			}

			done.forEach(SkillNodeEffects::unslow);
		}

		if (now % PRUNE_INTERVAL == 0 && !HITS.isEmpty()) {
			int window = SkillService.balance().tombHungerWindowTicks;
			Iterator<Long> it = HITS.values().iterator();

			while (it.hasNext()) {
				if (!tombHungerDue(it.next(), now, window)) {
					it.remove();
				}
			}
		}
	}

	/** Forgets a player's power hits (disconnect); a slow on them ends with their entity. */
	static void forget(ServerPlayer player) {
		HITS.keySet().removeIf(hit -> hit.caster().equals(player.getUUID()));
		unslow(player);
	}

	/** Lifts every slow and forgets everything (server stopping). */
	static void clear() {
		for (Slowed slowed : List.copyOf(SLOWED.values())) {
			unslow(slowed.entity());
		}

		SLOWED.clear();
		HITS.clear();
	}

	private SkillNodeEffects() {
	}
}
