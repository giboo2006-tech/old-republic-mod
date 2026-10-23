package com.oldrepublic.boss.raid;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.drops.BossDrops;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.Story;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Long Hymn's rewards (spec MILESTONE_5.md 12.3, 11.4), paid by W8 to every participant listed at
 * the clear, alive or dead: once per raid week {@code clearXp}, {@code clearShards} rolls of
 * {@code gameplay/long_hymn} (a hymnstone shard each), {@code clearCredits} and a legendary schematic
 * roll ({@code schematicChance}, sure after {@code schematicPity} clears without one); the first clear
 * adds Resonance {@code long_hymn}, the {@code long_hymn} banner, the Cantor's Mask and the entry. A
 * participant already rewarded this week helps for {@code helpXp} and nothing else. Every participant
 * is reported through {@code QuestHooks.onRaidCleared} (W4 opens the codex page and counts the deed).
 */
public final class RaidRewards {
	/** One hymnstone shard a roll. */
	public static final ResourceKey<LootTable> LONG_HYMN = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/long_hymn"));
	/** The legendary schematics the Long Hymn gives (11.2), never one the player knows. */
	public static final List<Identifier> SCHEMATICS = List.of(
			OldRepublicMod.id("emitter_cantor_legendary"),
			OldRepublicMod.id("grip_sunforged_legendary"),
			OldRepublicMod.id("lens_stormglass_legendary"),
			OldRepublicMod.id("mod_hymnweave_lining_legendary"),
			OldRepublicMod.id("mod_sunstone_edge_legendary"),
			OldRepublicMod.id("hymn_tuned_drive_legendary"));
	/** The Resonance milestone of the first clear. */
	public static final String RESONANCE = "long_hymn";

	/**
	 * Pays {@code player} for a clear in raid week {@code week} and reports it. The week's rewarded clear
	 * also brings the Choir set's drops ({@link BossDrops#choirEmitter}, {@link BossDrops#choirWeek},
	 * Milestone 7); a second clear the same week does not.
	 *
	 * @return whether it was the player's first clear
	 */
	public static boolean pay(ServerPlayer player, long week) {
		RaidBalance b = RaidBalance.get();
		RaidData data = RaidData.of(player);
		boolean first = data.clears() == 0;

		if (data.clearedIn(week)) {
			Progression.addXp(player, b.helpXp, "long_hymn_help");
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.reward_helped", b.helpXp));
			QuestHooks.onRaidCleared(player, false);
			return false;
		}

		Progression.addXp(player, b.clearXp, "long_hymn");

		for (int i = 0; i < b.clearShards; i++) {
			Rewards.giveLoot(player, LONG_HYMN, Rewards.seed(player, "long_hymn", week * 64L + i));
		}

		Credits.give(player, b.clearCredits);
		Identifier schematic = rollSchematic(player, data.pity(), week, b);
		int pity = schematic != null || unknownSchematics(player).isEmpty() ? 0 : data.pity() + 1;
		RaidData.set(player, data.withClear(week, pity));
		player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.reward", b.clearXp, b.clearShards, b.clearCredits));

		if (schematic != null) {
			Crafting.teach(player, schematic);
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.reward_schematic", schematicName(schematic)));
		}

		if (first) {
			payFirstClear(player, b);
		}

		// Milestone 7 (MILESTONE_7.md 7.3): the Choir emitter, once (a player who cleared before it gets it at the next
		// rewarded clear), then the week's roll for a Choir grip or pommel, each under its own guard.
		BossDrops.choirEmitter(player);
		BossDrops.choirWeek(player, week);
		QuestHooks.onRaidCleared(player, first);
		return first;
	}

	/** The first clear's extras: Resonance, the banner, the mask and the entry. */
	private static void payFirstClear(ServerPlayer player, RaidBalance b) {
		Progression.grantResonanceMilestone(player, RESONANCE, b.firstClearResonance);
		EffectEvents.toPlayer(player, QuestEffects.LESSON_COMPLETE, player.position(), null, player.getId(), Story.get(player).storyPath().ordinal(),
				QuestEffects.LESSON_LONG_HYMN);
		// W7's keepsake (12.3): a cosmetic head piece with no armour.
		Rewards.give(player, new ItemStack(EquipmentItems.CANTORS_MASK));
		Journal.unlockEntry(player, JournalEntries.THE_FIRST_CANTOR_FALLS);
	}

	/**
	 * This clear's legendary schematic for {@code player} with {@code misses} clears behind them without
	 * one, or null (the roll missed, or they know them all). The roll is seeded by the player and the
	 * week, so it cannot be tried again.
	 */
	static @Nullable Identifier rollSchematic(ServerPlayer player, int misses, long week, RaidBalance b) {
		List<Identifier> unknown = unknownSchematics(player);

		if (unknown.isEmpty()) {
			return null;
		}

		RandomSource random = RandomSource.create(Rewards.seed(player, "long_hymn_schematic", week));
		return RaidRules.schematicDue(misses, random.nextFloat(), b) ? unknown.get(random.nextInt(unknown.size())) : null;
	}

	/** The Long Hymn's schematics {@code player} does not know yet. */
	public static List<Identifier> unknownSchematics(ServerPlayer player) {
		List<Identifier> out = new ArrayList<>();

		for (Identifier id : SCHEMATICS) {
			if (!Crafting.knows(player, id)) {
				out.add(id);
			}
		}

		return out;
	}

	/** A schematic's name for the message: its result's name, or its id when W7's data is not loaded. */
	private static Component schematicName(Identifier id) {
		Schematic schematic = Schematics.get(id);
		return schematic != null ? schematic.output().getHoverName() : Component.literal(id.getPath());
	}

	private RaidRewards() {
	}
}
