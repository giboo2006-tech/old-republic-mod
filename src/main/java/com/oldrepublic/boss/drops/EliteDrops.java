package com.oldrepublic.boss.drops;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.cartel.CartelEnforcer;
import com.oldrepublic.enemy.creature.Hollowhorn;
import com.oldrepublic.enemy.hask.HaskWarrior;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.scour.SandScour;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The elites' drops (spec MILESTONE_7.md 7.4), on top of their own loot: the stronger kinds of five foes and the
 * trooper captains drop the materials of their worlds, and some a steel part of a craftable design (6.4). Modelled on
 * {@code vendor/CreditDrops}: only a kill whose blow came from a player, never anything a hub spawned, never a hunter
 * that may not drop loot (a Hask warrior of another player's bounty) and, for the foes of a faction, only a fight that
 * counted as hostile (a friendly clan's champion is no farm). The beasts drop for any player's kill, as their own loot
 * does. The poacher captain's Ringed pommel is in its loot table ({@code entities/poacher_captain}).
 */
public final class EliteDrops {
	/** The elites of 7.4. */
	public enum Elite {
		/** Clan Hask's champion (the shield kit). */
		HASK_CHAMPION(false),
		/** The cartel's captain. */
		CARTEL_CAPTAIN(false),
		/** The Hymnsworn storm-caller (never a raid shade or a boss fight's minion). */
		HYMNSWORN_CALLER(false),
		/** A trooper sergeant or officer, off patrol (the patrols' anti-farm rule). */
		TROOPER_CAPTAIN(false),
		/** The sand scour matriarch. */
		SCOUR_MATRIARCH(true),
		/** A hollowhorn herd's bull (never a corrupted one). */
		HOLLOWHORN_BULL(true);

		private final boolean beast;

		Elite(boolean beast) {
			this.beast = beast;
		}

		/** A beast drops for any player's kill; the others only for a fight that counted as hostile. */
		public boolean beast() {
			return beast;
		}

		/** The elite {@code entity} is, or null. */
		public static @Nullable Elite of(LivingEntity entity) {
			if (entity instanceof HaskWarrior hask) {
				return hask.variant() == HaskWarrior.Variant.CHAMPION ? HASK_CHAMPION : null;
			}

			if (entity instanceof CartelEnforcer cartel) {
				return cartel.variant() == CartelEnforcer.Variant.CAPTAIN ? CARTEL_CAPTAIN : null;
			}

			if (entity instanceof Hymnsworn hymnsworn) {
				return hymnsworn.variant() == Hymnsworn.Variant.CALLER && !hymnsworn.isShade() && !hymnsworn.isSummoned() ? HYMNSWORN_CALLER : null;
			}

			if (entity instanceof TrooperEntity trooper) {
				return (trooper.role() == TrooperRole.SERGEANT || trooper.role() == TrooperRole.OFFICER) && !trooper.isPatrol() ? TROOPER_CAPTAIN : null;
			}

			if (entity instanceof SandScour scour) {
				return scour.isMatriarch() ? SCOUR_MATRIARCH : null;
			}

			if (entity instanceof Hollowhorn hollowhorn) {
				return hollowhorn.isBull() && !hollowhorn.isCalf() && !hollowhorn.isCorrupted() ? HOLLOWHORN_BULL : null;
			}

			return null;
		}
	}

	/** From {@code ServerLivingEntityEvents.AFTER_DEATH} ({@link BossDropsFeature#init()}). */
	static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}

		Elite elite = Elite.of(entity);

		if (elite == null || entity.entityTags().contains(HubNpcs.HUB_TAG) || entity instanceof MandalorianHunter hunter && !hunter.mayDropLoot(level)
				|| !elite.beast() && !Attitudes.hostileSnapshot(entity, killer)) {
			return;
		}

		for (ItemStack stack : roll(elite, entity.getRandom(), BossDropsBalance.get())) {
			ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
	}

	/** What {@code elite} drops, rolled with {@code random}: its material first, then its part on its own chance. */
	public static List<ItemStack> roll(Elite elite, RandomSource random, BossDropsBalance b) {
		List<ItemStack> out = new ArrayList<>(2);

		switch (elite) {
			case HASK_CHAMPION -> add(out, M5Items.CLAN_IRON, BossDropRules.count(random.nextDouble(), b.championClanIron));
			case CARTEL_CAPTAIN -> {
				add(out, M5Items.SUNSTONE, BossDropRules.count(random.nextDouble(), b.captainSunstone));

				if (random.nextFloat() < b.captainKnurledChance) {
					out.add(SaberItems.partStack(Grip.KNURLED, HiltMaterial.STEEL));
				}
			}
			case HYMNSWORN_CALLER -> {
				if (random.nextFloat() < b.callerHymnstoneChance) {
					add(out, M5Items.HYMNSTONE_SHARD, 1);
				}

				if (random.nextFloat() < b.callerDomedChance) {
					out.add(SaberItems.partStack(Pommel.DOMED, HiltMaterial.STEEL));
				}
			}
			case TROOPER_CAPTAIN -> {
				if (random.nextFloat() < b.trooperVanguardChance) {
					out.add(SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.STEEL));
				}
			}
			case SCOUR_MATRIARCH -> add(out, M4Items.DUSKGLASS_SHARD, BossDropRules.count(random.nextDouble(), b.matriarchDuskglass));
			case HOLLOWHORN_BULL -> add(out, M4Items.AURELITE_SHARD, BossDropRules.count(random.nextDouble(), b.bullAurelite));
		}

		return out;
	}

	private static void add(List<ItemStack> out, Item item, int count) {
		if (count > 0) {
			out.add(new ItemStack(item, count));
		}
	}

	private EliteDrops() {
	}
}
