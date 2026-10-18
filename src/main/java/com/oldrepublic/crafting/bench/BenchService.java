package com.oldrepublic.crafting.bench;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.crafting.CraftingRules;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Salvage;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.network.BenchActionPayload;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of the Engineering Bench (spec MILESTONE_4.md 7.1 to 7.5, 7.8): every
 * {@code bench_action}, the running crafts, salvage, fitting mods, discipline choices and trainer
 * commissions. Clients only send intents; each is checked against the sender's open menu.
 *
 * <p>A craft checks its inputs when it starts but takes them only when it finishes (checking again),
 * so cancelling never loses anything. It runs for the schematic's {@code craft_ticks} while the
 * screen stays open and the crafter stays within {@code craftCancelRange} of the bench.
 */
public final class BenchService {
	/** One running craft; at most one per player. */
	private static final class Job {
		final Identifier schematic;
		final EngineeringBenchMenu menu;
		final int total;
		int done;

		Job(Identifier schematic, EngineeringBenchMenu menu, int total) {
			this.schematic = schematic;
			this.menu = menu;
			this.total = total;
		}
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	/** Handles one {@code bench_action} from {@code p}. */
	public static void handle(ServerPlayer p, BenchActionPayload a) {
		if (!(p.containerMenu instanceof EngineeringBenchMenu menu) || !menu.stillValid(p)) {
			return;
		}

		switch (a.tab()) {
			case EngineeringBenchMenu.TAB_CRAFT -> onCraftTab(p, menu, a);
			case EngineeringBenchMenu.TAB_MODIFY -> {
				if (!menu.isCommission()) {
					onModify(p, menu, a.slot());
				}
			}
			case EngineeringBenchMenu.TAB_SALVAGE -> {
				if (!menu.isCommission() && a.slot() == BenchActionPayload.SALVAGE) {
					salvage(p, menu);
				}
			}
			case EngineeringBenchMenu.TAB_DISCIPLINES -> {
				if (!menu.isCommission() && a.slot() == BenchActionPayload.CHOOSE) {
					choose(p, menu, a.id());
				}
			}
			default -> {
			}
		}

		refresh(p, menu);
	}

	private static void onCraftTab(ServerPlayer p, EngineeringBenchMenu menu, BenchActionPayload a) {
		Identifier id = Identifier.tryParse(a.id());

		switch (a.slot()) {
			case BenchActionPayload.SELECT -> menu.select(id != null && Schematics.get(id) != null ? id : null);
			case BenchActionPayload.CRAFT -> {
				if (id == null) {
					return;
				}

				menu.select(id);
				menu.report(menu.isCommission() ? commission(p, id, menu.commissionDiscipline()) : start(p, menu, id));
			}
			case BenchActionPayload.CANCEL -> cancel(p, BenchResult.CANCELLED);
			case BenchActionPayload.BREAK_BUNDLE -> {
				if (!menu.isCommission()) {
					menu.report(breakBundle(p, menu.trayContainer()));
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- crafting

	/** Whether {@code p} has a craft running. */
	public static boolean isCrafting(ServerPlayer p) {
		return JOBS.containsKey(p.getUUID());
	}

	/** Starts crafting {@code id} at the bench of {@code menu}. */
	static BenchResult start(ServerPlayer p, EngineeringBenchMenu menu, Identifier id) {
		if (JOBS.containsKey(p.getUUID())) {
			return BenchResult.BUSY;
		}

		Schematic s = Schematics.get(id);

		if (s == null || Crafting.craftRefusal(CraftingAttachments.get(p), s) != null) {
			return BenchResult.LOCKED;
		}

		if (!Ingredients.has(Ingredients.sources(menu.trayContainer(), p), s.inputs())) {
			return BenchResult.MISSING_INPUTS;
		}

		JOBS.put(p.getUUID(), new Job(id, menu, s.craftTicks()));
		menu.set(EngineeringBenchMenu.DATA_PROGRESS, 0);
		menu.set(EngineeringBenchMenu.DATA_TOTAL, s.craftTicks());
		benchEffect(p, menu, CraftingEffects.CRAFT_START, s.craftTicks());
		return BenchResult.STARTED;
	}

	/** Advances every running craft (server tick). */
	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}

		CraftingBalance b = CraftingBalance.get();
		Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, Job> entry = it.next();
			Job job = entry.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());

			if (p == null || p.containerMenu != job.menu) {
				// The screen closed (or the player left): onClosed has already stopped the effect.
				it.remove();
				continue;
			}

			if (!inRange(p, job.menu, b.craftCancelRange)) {
				it.remove();
				stop(p, job, BenchResult.CANCELLED_RANGE);
				continue;
			}

			job.done++;
			job.menu.set(EngineeringBenchMenu.DATA_PROGRESS, job.done);

			if (job.done >= job.total) {
				it.remove();
				finish(p, job);
			}
		}
	}

	private static boolean inRange(ServerPlayer p, EngineeringBenchMenu menu, double range) {
		return menu.access().evaluate((level, pos) -> level == p.level() && p.position().distanceToSqr(Vec3.atCenterOf(pos)) <= range * range, true);
	}

	private static void finish(ServerPlayer p, Job job) {
		Schematic s = Schematics.get(job.schematic);
		EngineeringBenchMenu menu = job.menu;

		if (s == null || Crafting.craftRefusal(CraftingAttachments.get(p), s) != null) {
			stop(p, job, BenchResult.LOCKED);
			return;
		}

		if (!Ingredients.take(menu.trayContainer(), p, s.inputs())) {
			stop(p, job, BenchResult.MISSING_INPUTS);
			return;
		}

		Quality quality = Crafting.isMasterworkOnly(s) ? Quality.MASTERWORK : Crafting.roll(p, s);
		ItemStack result = s.result();

		if (s.quality() || Crafting.isMasterworkOnly(s)) {
			Crafting.applyQuality(result, quality);
		}

		deliver(p, menu, result);
		Crafting.awardCraft(p, s, quality);
		menu.set(EngineeringBenchMenu.DATA_TOTAL, 0);
		menu.set(EngineeringBenchMenu.DATA_PROGRESS, 0);
		boolean masterwork = quality == Quality.MASTERWORK && (s.quality() || Crafting.isMasterworkOnly(s));
		benchEffect(p, menu, masterwork ? CraftingEffects.CRAFT_MASTERWORK : CraftingEffects.CRAFT_DONE, 1f);
		menu.report(masterwork ? BenchResult.MASTERWORK : BenchResult.CRAFTED);
		refresh(p, menu);
	}

	/** Puts a finished craft in the output slot, or gives it to the player when the slot is taken. */
	private static void deliver(ServerPlayer p, EngineeringBenchMenu menu, ItemStack result) {
		ItemStack output = menu.output();

		if (output.isEmpty()) {
			menu.setOutput(result);
		} else if (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize()) {
			output.grow(result.getCount());
			menu.workChanged();
		} else {
			Rewards.give(p, result);
		}
	}

	/** Cancels {@code p}'s running craft (the Cancel button, unlearning, a reload); nothing is lost. */
	public static void cancel(ServerPlayer p, BenchResult reason) {
		Job job = JOBS.remove(p.getUUID());

		if (job != null) {
			stop(p, job, reason);
		}
	}

	private static void stop(ServerPlayer p, Job job, BenchResult reason) {
		job.menu.set(EngineeringBenchMenu.DATA_TOTAL, 0);
		job.menu.set(EngineeringBenchMenu.DATA_PROGRESS, 0);
		job.menu.report(reason);
		benchEffect(p, job.menu, CraftingEffects.CRAFT_STOP, 0f);
	}

	/** The menu closed: a running craft stops with no loss, and its weld loop with it. */
	static void onClosed(ServerPlayer p, EngineeringBenchMenu menu) {
		Job job = JOBS.get(p.getUUID());

		if (job != null && job.menu == menu) {
			JOBS.remove(p.getUUID());
			benchEffect(p, menu, CraftingEffects.CRAFT_STOP, 0f);
		}
	}

	/** Forgets {@code p}'s craft without effects (logout, server stop). */
	public static void forget(ServerPlayer p) {
		JOBS.remove(p.getUUID());
	}

	public static void clear() {
		JOBS.clear();
	}

	private static void benchEffect(ServerPlayer p, EngineeringBenchMenu menu, int variant, float intensity) {
		Optional<BlockPos> pos = menu.access().evaluate((level, at) -> at);

		if (pos.isPresent()) {
			EffectEvents.around(p.level(), CraftingEffects.BENCH_CRAFT, Vec3.atCenterOf(pos.get()).add(0, 0.55, 0), null, p.getId(), intensity, variant);
		} else {
			EffectEvents.toPlayer(p, CraftingEffects.BENCH_CRAFT, p.position().add(0, 1, 0), null, p.getId(), intensity, variant);
		}
	}

	// ---------------------------------------------------------------- credits exchange (7.7)

	/** Breaks one {@code credit_bundle} from the tray or inventory into 100 chits. */
	static BenchResult breakBundle(ServerPlayer p, @Nullable Container tray) {
		Schematic.Input bundle = new Schematic.Input(Either.left(M4Items.CREDIT_BUNDLE), 1);

		if (!Ingredients.take(tray, p, List.of(bundle))) {
			return BenchResult.NOTHING_TO_EXCHANGE;
		}

		int left = Credits.BUNDLE;

		while (left > 0) {
			int n = Math.min(left, new ItemStack(M4Items.CREDIT_CHIT).getMaxStackSize());
			Rewards.give(p, new ItemStack(M4Items.CREDIT_CHIT, n));
			left -= n;
		}

		return BenchResult.EXCHANGED;
	}

	// ---------------------------------------------------------------- modify (6.3)

	private static void onModify(ServerPlayer p, EngineeringBenchMenu menu, int slot) {
		ItemStack gear = menu.gear();

		if (gear.isEmpty()) {
			return;
		}

		if (slot == BenchActionPayload.FIT) {
			ItemStack mod = menu.mod();

			for (ModSlot s : Mods.slotsOf(gear)) {
				if (!mod.isEmpty() && Mods.fits(gear, s, mod) && Mods.fitted(gear).get(s).isEmpty()) {
					Mods.fit(gear, s, mod);
					menu.workChanged();
					EffectEvents.toPlayer(p, CraftingEffects.MOD_FIT, p.position().add(0, 1, 0), null, p.getId(), 1f, CraftingEffects.MOD_FITTED);
					menu.report(BenchResult.FITTED);
					return;
				}
			}

			menu.report(BenchResult.NO_SOCKET);
			return;
		}

		ModSlot[] all = ModSlot.values();

		if (slot < 0 || slot >= all.length) {
			return;
		}

		ItemStack removed = Mods.remove(gear, all[slot]);

		if (removed.isEmpty()) {
			return;
		}

		ItemStack mod = menu.mod();

		if (mod.isEmpty()) {
			menu.slots.get(EngineeringBenchMenu.MOD).set(removed);
		} else {
			Rewards.give(p, removed);
		}

		menu.workChanged();
		EffectEvents.toPlayer(p, CraftingEffects.MOD_FIT, p.position().add(0, 1, 0), null, p.getId(), 1f, CraftingEffects.MOD_REMOVE);
		menu.report(BenchResult.REMOVED);
	}

	// ---------------------------------------------------------------- salvage (7.5)

	private static void salvage(ServerPlayer p, EngineeringBenchMenu menu) {
		ItemStack input = menu.salvageInput();
		BenchResult result = salvage(p, input);

		if (result == BenchResult.SALVAGED) {
			menu.setSalvageInput(ItemStack.EMPTY);
			Optional<BlockPos> pos = menu.access().evaluate((level, at) -> at);
			Vec3 at = pos.map(bp -> Vec3.atCenterOf(bp).add(0, 0.55, 0)).orElse(p.position().add(0, 1, 0));
			EffectEvents.around(p.level(), CraftingEffects.SALVAGE, at, null, p.getId(), 1f, 0);
		}

		menu.report(result);
	}

	/**
	 * Salvages {@code input} for {@code p}: gives the returns (7.5) and the salvage XP. The caller
	 * removes the input on {@link BenchResult#SALVAGED}.
	 */
	public static BenchResult salvage(ServerPlayer p, ItemStack input) {
		if (!Salvage.canSalvage(input)) {
			return BenchResult.NOT_SALVAGEABLE;
		}

		CraftingBalance b = CraftingBalance.get();
		Schematic s = Salvage.schematicFor(input);

		for (ItemStack back : Salvage.returns(input, b.salvageReturn)) {
			Rewards.give(p, back);
		}

		if (s != null && s.discipline().isPresent()) {
			Crafting.addDisciplineXp(p, s.discipline().get(), Salvage.xp(input, b.salvageXp));
		}

		return BenchResult.SALVAGED;
	}

	// ---------------------------------------------------------------- disciplines (7.2)

	private static void choose(ServerPlayer p, EngineeringBenchMenu menu, String id) {
		Discipline d = Discipline.byId(id);

		if (d == null) {
			return;
		}

		BenchResult result = switch (Crafting.choose(p, d)) {
			case CHOSEN -> BenchResult.CHOSEN;
			case SECOND_LOCKED -> BenchResult.SECOND_LOCKED;
			case FULL -> BenchResult.FULL;
			default -> BenchResult.NONE;
		};

		if (result == BenchResult.CHOSEN) {
			EffectEvents.toPlayer(p, CraftingEffects.BENCH_CRAFT, p.position().add(0, 1, 0), null, p.getId(), 1f, CraftingEffects.CRAFT_DONE);
		}

		menu.report(result);
	}

	// ---------------------------------------------------------------- commissions (7.8)

	/**
	 * Whether a trainer of {@code trainer} (null: any discipline) will commission {@code s} for
	 * {@code p}: a discipline schematic of that discipline that the player knows, or carries as a
	 * {@code schematic} item, or (for the Mandalorian set) Kaast's pattern.
	 */
	public static boolean mayCommission(Player p, Schematic s, @Nullable Discipline trainer) {
		if (s.discipline().isEmpty() || Crafting.isMasterworkOnly(s) || (trainer != null && s.discipline().get() != trainer)) {
			return false;
		}

		CraftingData data = CraftingAttachments.get(p);

		if (Crafting.knows(data, s) || data.known().contains(s.id())) {
			return true;
		}

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			Identifier taught = stack.get(M4Components.SCHEMATIC);

			if (s.id().equals(taught)) {
				return true;
			}

			if (s.id().getPath().startsWith("mandalorian_") && isKaastPattern(stack)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isKaastPattern(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("kaast_pattern");
	}

	/**
	 * A trainer's commission (7.8): crafts {@code id} at once, at Standard quality, from the player's
	 * inputs plus a fee of {@code commissionPerLevel} x level credits. No XP, no pity, no first craft.
	 */
	public static BenchResult commission(ServerPlayer p, Identifier id, @Nullable Discipline trainer) {
		Schematic s = Schematics.get(id);

		if (s == null || !mayCommission(p, s, trainer)) {
			return BenchResult.LOCKED;
		}

		int fee = CraftingRules.commissionFee(s.level(), CraftingBalance.get());

		if (!Ingredients.has(Ingredients.sources(null, p), s.inputs())) {
			return BenchResult.MISSING_INPUTS;
		}

		if (Credits.count(p, true) < fee || !Credits.take(p, fee, true)) {
			return BenchResult.NO_CREDITS;
		}

		if (!Ingredients.take(null, p, s.inputs())) {
			// Paying moved chits around; the inputs were checked first, so this is a safety net.
			Credits.give(p, fee);
			return BenchResult.MISSING_INPUTS;
		}

		ItemStack result = s.result();

		if (s.quality()) {
			Crafting.applyQuality(result, Quality.STANDARD);
		}

		Rewards.give(p, result);
		EffectEvents.toPlayer(p, CraftingEffects.BENCH_CRAFT, p.position().add(0, 1, 0), null, p.getId(), 1f, CraftingEffects.CRAFT_DONE);
		return BenchResult.COMMISSIONED;
	}

	// ---------------------------------------------------------------- synced numbers for the screen

	/** Recomputes the screen's numbers for {@code menu}: the selection's odds and XP, salvage and discipline state. */
	public static void refresh(ServerPlayer p, EngineeringBenchMenu menu) {
		CraftingBalance b = CraftingBalance.get();
		CraftingData data = CraftingAttachments.get(p);
		int count = data.disciplines().size();
		boolean open = count == 0 || (count < CraftingData.MAX_DISCIPLINES && Crafting.secondOpen(p));
		menu.set(EngineeringBenchMenu.DATA_SECOND_OPEN, open ? 1 : 0);
		menu.set(EngineeringBenchMenu.DATA_SALVAGE_PCT, Math.round(b.salvageReturn * 100f));
		ItemStack salvage = menu.salvageInput();
		Schematic salvaged = Salvage.schematicFor(salvage);
		boolean salvageXp = salvaged != null && salvaged.discipline().map(data::has).orElse(false);
		menu.set(EngineeringBenchMenu.DATA_SALVAGE_XP, salvageXp ? Salvage.xp(salvage, b.salvageXp) : 0);

		Identifier id = menu.selected();
		Schematic s = id == null ? null : Schematics.get(id);

		if (s == null) {
			menu.set(EngineeringBenchMenu.DATA_FINE, -1);
			menu.set(EngineeringBenchMenu.DATA_MASTERWORK, -1);
			menu.set(EngineeringBenchMenu.DATA_XP, 0);
			menu.set(EngineeringBenchMenu.DATA_PITY, 0);
			menu.set(EngineeringBenchMenu.DATA_FIRST_XP, 0);
			menu.set(EngineeringBenchMenu.DATA_FEE, 0);
			return;
		}

		Optional<CraftingData.Progress> progress = s.discipline().flatMap(data::progress);
		int level = progress.map(CraftingData.Progress::level).orElse(s.level());
		boolean commission = menu.isCommission();

		if (Crafting.isMasterworkOnly(s)) {
			menu.set(EngineeringBenchMenu.DATA_FINE, 0);
			menu.set(EngineeringBenchMenu.DATA_MASTERWORK, commission ? 0 : 1000);
		} else if (s.quality() && !commission) {
			menu.set(EngineeringBenchMenu.DATA_FINE, Math.round(CraftingRules.fineChance(level, s.level(), b) * 1000f));
			menu.set(EngineeringBenchMenu.DATA_MASTERWORK, Math.round(CraftingRules.masterworkChance(level, s.level(), b) * 1000f));
		} else {
			menu.set(EngineeringBenchMenu.DATA_FINE, -1);
			menu.set(EngineeringBenchMenu.DATA_MASTERWORK, -1);
		}

		boolean pity = s.quality() && !commission && !Crafting.isMasterworkOnly(s) && progress.isPresent() && b.pityCrafts > 0;
		menu.set(EngineeringBenchMenu.DATA_PITY, pity ? CraftingRules.craftsToGuarantee(progress.get().pity(), b) : 0);
		menu.set(EngineeringBenchMenu.DATA_XP, progress.isPresent() && !commission ? CraftingRules.craftXp(s.xp(), level, s.level(), b) : 0);
		menu.set(EngineeringBenchMenu.DATA_FIRST_XP, !commission && !data.firstCrafts().contains(s.id()) ? CraftingRules.firstCraftXp(s.level(), b) : 0);
		menu.set(EngineeringBenchMenu.DATA_FEE, commission ? CraftingRules.commissionFee(s.level(), b) : 0);
	}

	private BenchService() {
	}
}
