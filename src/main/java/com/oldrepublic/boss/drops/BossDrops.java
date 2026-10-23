package com.oldrepublic.boss.drops;

import java.util.List;
import java.util.Locale;

import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The bosses' set parts (spec MILESTONE_7.md 7.1, 7.3). The boss's own reward code calls each drop beside what it pays
 * today, which never changes; every drop is its own call under its own guard, kept in the player's
 * {@link BossDropsData}:
 *
 * <ul>
 * <li>a set's emitter comes once ever (Kaast's first clear, Ulvaine's fight pay, the Long Hymn's first clear): the
 * record of what the drops gave is its guard;</li>
 * <li>a repeating drop rolls once per occasion (a daily echo or helper roll, the raid week): its last occasion is its
 * guard, and its pity counter makes the N-th occasion in a row without one sure ({@link BossDropRules#due}).</li>
 * </ul>
 *
 * <p>"The part the player lacks" is read from the record of what the drops gave, not from the inventory: a part kept in
 * a chest, fitted to a saber or re-forged still counts as had, so storing the first one away never farms a second. When
 * the drops have given both a set's grip and pommel, Kaast's and Ulvaine's rolls stop; the Long Hymn's then gives
 * hymnsteel ingots instead. Rolls are seeded by the player, the drop and the occasion, so the same day always rolls the
 * same and cannot be tried again. Server only.
 */
public final class BossDrops {
	/** The pity counters ({@link BossDropsData#misses}); Ulvaine's echo and helper rolls share one. */
	public static final String COLLECTOR = "collector";
	public static final String WATCHMAN = "watchman";
	public static final String PENITENT = "penitent";
	public static final String CHOIR = "choir";
	/** The prefix of the drops' messages ({@code message.oldrepublic.boss_drops.<drop>}). */
	private static final String MESSAGE = "message.oldrepublic.boss_drops.";

	/** The repeating drops: each its own guard (its id), its pity counter, the parts it gives and their material. */
	public enum Repeat {
		/** The Collector's daily echo: the Reliquary grip, again and again (like his emitter). */
		COLLECTOR_ECHO(COLLECTOR, HiltMaterial.STARFALL, false, Grip.RELIQUARY),
		/** Kaast's daily rematch. */
		WATCHMAN_REMATCH(WATCHMAN, HiltMaterial.AURELITE, true, Grip.WATCHMAN, Pommel.WATCHMAN),
		/** Ulvaine's daily echo. */
		PENITENT_ECHO(PENITENT, HiltMaterial.CLAN_IRON, true, Grip.PENITENT, Pommel.PENITENT),
		/** A helper's daily roll in another player's Ulvaine fight. */
		PENITENT_HELPER(PENITENT, HiltMaterial.CLAN_IRON, true, Grip.PENITENT, Pommel.PENITENT),
		/** The Long Hymn's weekly clear. */
		CHOIR_WEEK(CHOIR, HiltMaterial.HYMNSTEEL, true, Grip.CHOIR, Pommel.CHOIR);

		private final String counter;
		private final HiltMaterial material;
		private final boolean onlyLacking;
		private final List<HiltParts.Part> parts;

		Repeat(String counter, HiltMaterial material, boolean onlyLacking, HiltParts.Part... parts) {
			this.counter = counter;
			this.material = material;
			this.onlyLacking = onlyLacking;
			this.parts = List.of(parts);
		}

		/** The guard's id in the record, and its message's key. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String counter() {
			return counter;
		}

		public HiltMaterial material() {
			return material;
		}

		/** The parts this roll may give {@code data}'s player: the ones the drops have not given yet, or all of them. */
		public List<HiltParts.Part> candidates(BossDropsData data) {
			return onlyLacking ? BossDropRules.lacking(data.given(), parts) : parts;
		}
	}

	// ---------------------------------------------------------------- the Collector (Chapter Three)

	/**
	 * A rewarded echo clear of the Collector ({@code CollectorRewards.echo}, once per in-game {@code day}): the Reliquary
	 * grip in starfall iron at {@code collectorGripChance}, sure by the {@code collectorGripPity}-th clear without one.
	 *
	 * @return whether it rolled now (false: already rolled on {@code day})
	 */
	public static boolean collectorEcho(ServerPlayer p, long day) {
		BossDropsBalance b = BossDropsBalance.get();
		return roll(p, Repeat.COLLECTOR_ECHO, day, b.collectorGripChance, b.collectorGripPity, ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- Torvin Kaast (Chapter Four)

	/**
	 * Kaast's first clear, whatever the choice ({@code KaastDuel}): the Watchman emitter in aurelite, once ever. The
	 * rematch also calls it for an owner whose first clear came before this build.
	 *
	 * @return whether it was given now
	 */
	public static boolean watchmanEmitter(ServerPlayer p) {
		return once(p, Emitter.WATCHMAN, HiltMaterial.AURELITE, "watchman_emitter");
	}

	/**
	 * The owner's rewarded rematch with Kaast's echo (once per in-game {@code day}): the Watchman grip or pommel the
	 * drops have not given, in aurelite, at {@code watchmanPartChance}, sure by the {@code watchmanPartPity}-th rematch
	 * without one. Nothing rolls once both are given.
	 *
	 * @return whether it rolled now
	 */
	public static boolean watchmanRematch(ServerPlayer p, long day) {
		BossDropsBalance b = BossDropsBalance.get();
		return roll(p, Repeat.WATCHMAN_REMATCH, day, b.watchmanPartChance, b.watchmanPartPity, ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- Darth Ulvaine (Chapter Five)

	/**
	 * The pay of the owner's fight with Ulvaine ({@code UlvaineChoice.payFight}'s occasions): the Penitent emitter in
	 * clan iron, once ever. Her echo also calls it for a player whose fight was paid before this build.
	 *
	 * @return whether it was given now
	 */
	public static boolean penitentEmitter(ServerPlayer p) {
		return once(p, Emitter.PENITENT, HiltMaterial.CLAN_IRON, "penitent_emitter");
	}

	/**
	 * A rewarded defeat of Ulvaine's echo ({@code UlvaineFight.payEcho}, once per in-game {@code day}): the Penitent grip
	 * or pommel the drops have not given, in clan iron, at {@code penitentPartChance}; the pity ({@code penitentPartPity})
	 * is shared with the helper's roll.
	 *
	 * @return whether it rolled now
	 */
	public static boolean penitentEcho(ServerPlayer p, long day) {
		BossDropsBalance b = BossDropsBalance.get();
		return roll(p, Repeat.PENITENT_ECHO, day, b.penitentPartChance, b.penitentPartPity, ItemStack.EMPTY);
	}

	/**
	 * A helper's rewarded day in another player's Ulvaine fight ({@code UlvaineChoice.helper}): the same roll as the
	 * echo's ({@link #penitentEcho}), under its own daily guard.
	 *
	 * @return whether it rolled now
	 */
	public static boolean penitentHelper(ServerPlayer p, long day) {
		BossDropsBalance b = BossDropsBalance.get();
		return roll(p, Repeat.PENITENT_HELPER, day, b.penitentPartChance, b.penitentPartPity, ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- the First Cantor (the Long Hymn)

	/**
	 * The Long Hymn's first clear ({@code RaidRewards.pay}): the Choir emitter in hymnsteel, once ever. Every later
	 * rewarded clear calls it too, so a player who cleared before this build gets it at their next.
	 *
	 * @return whether it was given now
	 */
	public static boolean choirEmitter(ServerPlayer p) {
		return once(p, Emitter.CHOIR, HiltMaterial.HYMNSTEEL, "choir_emitter");
	}

	/**
	 * A rewarded clear of raid week {@code week}: a Choir grip or pommel the drops have not given, in hymnsteel, at
	 * {@code choirPartChance}, sure by the {@code choirPartPity}-th week without one; once both are given,
	 * {@code choirIngots} hymnsteel ingots on the same roll instead.
	 *
	 * @return whether it rolled now (false: already rolled in {@code week})
	 */
	public static boolean choirWeek(ServerPlayer p, long week) {
		BossDropsBalance b = BossDropsBalance.get();
		return roll(p, Repeat.CHOIR_WEEK, week, b.choirPartChance, b.choirPartPity, new ItemStack(M7Items.HYMNSTEEL_INGOT, Math.max(1, b.choirIngots)));
	}

	// ---------------------------------------------------------------- the two kinds of drop

	/** A set part once ever: given unless the drops gave it before, then recorded. */
	private static boolean once(ServerPlayer p, HiltParts.Part part, HiltMaterial material, String message) {
		BossDropsData data = BossDropsData.get(p);

		if (data.gave(part)) {
			return false;
		}

		BossDropsData.set(p, data.withGiven(part));
		ItemStack stack = SaberItems.partStack(part, material);
		Rewards.give(p, stack);
		p.sendSystemMessage(Component.translatable(MESSAGE + message, stack.getHoverName()));
		return true;
	}

	/**
	 * A repeating drop on {@code occasion}, once per occasion: a seeded draw against {@code chance}, sure on the
	 * {@code pity}-th occasion in a row without one. It gives one of the drop's candidate parts, picked by the same
	 * draw, or {@code instead} when none is left; with neither, nothing is left to drop: nothing rolls, the guard stays
	 * open and the pity keeps its count.
	 *
	 * @return whether it rolled now
	 */
	private static boolean roll(ServerPlayer p, Repeat drop, long occasion, float chance, int pity, ItemStack instead) {
		BossDropsData data = BossDropsData.get(p);
		List<HiltParts.Part> candidates = drop.candidates(data);

		if (data.rolledOn(drop.id(), occasion) || candidates.isEmpty() && instead.isEmpty()) {
			return false;
		}

		// The same player on the same occasion always rolls the same, so the roll cannot be tried again.
		RandomSource random = RandomSource.create(Rewards.seed(p, "boss_drops." + drop.id(), occasion));
		int misses = data.misses(drop.counter());
		boolean due = BossDropRules.due(misses, random.nextFloat(), chance, pity);
		data = data.withRolled(drop.id(), occasion).withMisses(drop.counter(), due ? 0 : misses + 1);
		ItemStack stack = ItemStack.EMPTY;
		String message = MESSAGE + drop.id();

		if (due && !candidates.isEmpty()) {
			HiltParts.Part part = candidates.get(random.nextInt(candidates.size()));
			stack = SaberItems.partStack(part, drop.material());
			data = data.withGiven(part);
		} else if (due) {
			stack = instead.copy();
			message += ".instead";
		}

		BossDropsData.set(p, data);

		if (!stack.isEmpty()) {
			Rewards.give(p, stack);
			p.sendSystemMessage(Component.translatable(message, stack.getHoverName(), stack.getCount()));
		}

		return true;
	}

	private BossDrops() {
	}
}
