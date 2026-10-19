package com.oldrepublic.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.M4Components;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Disciplines, known schematics and quality for other features (spec MILESTONE_4.md 7, 15.1).
 * The first seven methods' signatures are frozen. Everything that changes a player's crafting
 * state goes through here and commits a whole new {@link CraftingData} record.
 */
public final class Crafting {
	/** The player's base attack damage, which an item's attack damage modifier adds to. */
	private static final double PLAYER_BASE_DAMAGE = 1.0;

	public static boolean has(ServerPlayer p, Discipline d) {
		return CraftingAttachments.get(p).has(d);
	}

	/** The player's level in {@code d} (1 to 5), or 0 without it. */
	public static int level(ServerPlayer p, Discipline d) {
		return CraftingAttachments.get(p).progress(d).map(CraftingData.Progress::level).orElse(0);
	}

	/**
	 * Whether the player knows {@code schematic}: taught to them, or an {@code auto} schematic of a
	 * discipline they have at its level (common {@code auto} schematics are known to everyone).
	 */
	public static boolean knows(ServerPlayer p, Identifier schematic) {
		Schematic s = Schematics.get(schematic);
		CraftingData data = CraftingAttachments.get(p);
		return s != null ? knows(data, s) : data.known().contains(schematic);
	}

	/** Teaches {@code schematic} (story rewards, vendors and schematic items call this). */
	public static void teach(ServerPlayer p, Identifier schematic) {
		CraftingData data = CraftingAttachments.get(p);

		if (!data.known().contains(schematic)) {
			CraftingAttachments.set(p, data.withKnown(schematic));
		}
	}

	/** Rolls the quality of one craft of {@code s} for {@code p}, pity included (7.4). */
	public static Quality roll(ServerPlayer p, Schematic s) {
		return roll(p, s, p.getRandom());
	}

	/**
	 * {@link #roll(ServerPlayer, Schematic)} with a given random source (tests seed it). A quality
	 * schematic of one of the player's disciplines also moves that discipline's pity counter: up by
	 * one below Masterwork, back to 0 on a Masterwork; at {@code pityCrafts} the roll is a
	 * Masterwork without drawing. Schematics without quality are always Standard.
	 */
	public static Quality roll(ServerPlayer p, Schematic s, RandomSource random) {
		if (!s.quality()) {
			return Quality.STANDARD;
		}

		CraftingBalance b = CraftingBalance.get();
		CraftingData data = CraftingAttachments.get(p);
		Optional<Progress> progress = s.discipline().flatMap(data::progress);
		int level = progress.map(Progress::level).orElse(s.level());
		Quality quality;

		if (progress.isPresent() && CraftingRules.pityDue(progress.get().pity(), b)) {
			quality = Quality.MASTERWORK;
		} else {
			quality = CraftingRules.pick(CraftingRules.fineChance(level, s.level(), b), CraftingRules.masterworkChance(level, s.level(), b), random.nextFloat());
		}

		if (progress.isPresent()) {
			Progress before = progress.get();
			CraftingAttachments.set(p, data.withProgress(new Progress(before.discipline(), before.level(), before.xp(),
					CraftingRules.pityAfter(before.pity(), quality))));
		}

		return quality;
	}

	/** The stack's quality; Standard when it has none. */
	public static Quality quality(ItemStack stack) {
		Integer q = stack.get(M4Components.QUALITY);
		return q == null ? Quality.STANDARD : Quality.of(q);
	}

	/** {@code stack} with quality {@code q} (the same stack, changed and returned). */
	public static ItemStack withQuality(ItemStack stack, Quality q) {
		stack.set(M4Components.QUALITY, q.ordinal());
		return stack;
	}

	// ---- Beyond the frozen contract (W5)

	/** {@link #knows(ServerPlayer, Identifier)} on a data record (either side). */
	public static boolean knows(CraftingData data, Schematic s) {
		if (data.known().contains(s.id())) {
			return true;
		}

		if (s.learned() != Schematic.Learned.AUTO) {
			return false;
		}

		return s.discipline().map(d -> data.progress(d).map(Progress::level).orElse(0) >= s.level()).orElse(true);
	}

	/**
	 * Why {@code data} cannot craft {@code s} at a bench, as a translation key with arguments, or
	 * null when it can (inputs not checked). A known schematic of an unlearned discipline is locked.
	 */
	public static @Nullable Component craftRefusal(CraftingData data, Schematic s) {
		if (!knows(data, s)) {
			return Component.translatable("crafting.oldrepublic.refuse.unknown");
		}

		if (s.discipline().isPresent()) {
			Discipline d = s.discipline().get();
			int level = data.progress(d).map(Progress::level).orElse(0);

			if (level < s.level()) {
				return Component.translatable("crafting.oldrepublic.refuse.level", name(d), s.level());
			}
		}

		return null;
	}

	/** The schematics {@code data} can craft at a bench, in load order. */
	public static List<Schematic> craftable(CraftingData data) {
		List<Schematic> out = new ArrayList<>();

		for (Schematic s : Schematics.all()) {
			if (craftRefusal(data, s) == null) {
				out.add(s);
			}
		}

		return out;
	}

	/**
	 * Applies {@code q} to a freshly crafted stack (7.4): the quality component, and the stack's own
	 * attack damage, armour and toughness modifiers and maximum durability scaled by the
	 * multipliers. Registered values never change. Returns the same stack.
	 */
	public static ItemStack applyQuality(ItemStack stack, Quality q) {
		withQuality(stack, q);

		if (q == Quality.STANDARD) {
			return stack;
		}

		// The name colour (7.4): Fine shows as rare, Masterwork and Legendary as epic (MILESTONE_5.md 11.1); a higher rarity is kept.
		Rarity rarity = q == Quality.MASTERWORK || q == Quality.LEGENDARY ? Rarity.EPIC : Rarity.RARE;

		if (stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON).ordinal() < rarity.ordinal()) {
			stack.set(DataComponents.RARITY, rarity);
		}

		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

		if (!modifiers.modifiers().isEmpty()) {
			List<ItemAttributeModifiers.Entry> scaled = new ArrayList<>();

			for (ItemAttributeModifiers.Entry e : modifiers.modifiers()) {
				AttributeModifier m = e.modifier();
				double amount = m.amount();

				if (m.operation() == AttributeModifier.Operation.ADD_VALUE) {
					if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
						amount = (amount + PLAYER_BASE_DAMAGE) * q.weapon() - PLAYER_BASE_DAMAGE;
					} else if (e.attribute().value() == Attributes.ARMOR.value()) {
						amount = Math.floor(amount * q.armour());
					} else if (e.attribute().value() == Attributes.ARMOR_TOUGHNESS.value()) {
						amount = amount * q.armour();
					}
				}

				scaled.add(new ItemAttributeModifiers.Entry(e.attribute(), new AttributeModifier(m.id(), amount, m.operation()), e.slot(), e.display()));
			}

			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(scaled));
		}

		Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);

		if (maxDamage != null && maxDamage > 0) {
			stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(maxDamage * q.durability())));
		}

		return stack;
	}

	/**
	 * Whether {@code s} is a trainer's level 5 Masterwork-only recipe (7.8, 16.1): by convention its
	 * id ends in {@code _masterwork}, and every craft of it is Masterwork without a roll.
	 */
	public static boolean isMasterworkOnly(Schematic s) {
		return s.id().getPath().endsWith("_masterwork");
	}

	/** The outcome of a finished craft, for the bench's status line and effects. */
	public record Award(boolean firstCraft, int disciplineXp, int newLevel) {
	}

	/**
	 * Credits a finished craft of {@code s} at quality {@code q} (7.2): the discipline XP (a quarter
	 * for low-level schematics), a level-up message and hook, the first-craft player XP once per
	 * schematic, and {@link QuestHooks#onCrafted}.
	 */
	public static Award awardCraft(ServerPlayer p, Schematic s, Quality q) {
		CraftingBalance b = CraftingBalance.get();
		CraftingData data = CraftingAttachments.get(p);
		boolean first = !data.firstCrafts().contains(s.id());

		if (first) {
			data = data.withFirstCraft(s.id());
		}

		int gained = 0;
		int newLevel = 0;

		if (s.discipline().isPresent() && data.has(s.discipline().get())) {
			Progress before = data.progress(s.discipline().get()).orElseThrow();
			gained = CraftingRules.craftXp(s.xp(), before.level(), s.level(), b);
			data = data.withProgress(gainXp(before, gained, b));
			int after = data.progress(before.discipline()).map(Progress::level).orElse(before.level());
			newLevel = after > before.level() ? after : 0;
		}

		CraftingAttachments.set(p, data);

		if (first) {
			Progression.addXp(p, CraftingRules.firstCraftXp(s.level(), b), "craft");
		}

		if (newLevel > 0) {
			onLevelUp(p, s.discipline().get(), newLevel);
		}

		QuestHooks.onCrafted(p, s.id(), q.ordinal(), first);
		return new Award(first, gained, newLevel);
	}

	/** Adds {@code xp} discipline XP to {@code d} if the player has it (salvage, 7.5). */
	public static void addDisciplineXp(ServerPlayer p, Discipline d, int xp) {
		CraftingData data = CraftingAttachments.get(p);
		Optional<Progress> before = data.progress(d);

		if (before.isEmpty() || xp <= 0) {
			return;
		}

		Progress after = gainXp(before.get(), xp, CraftingBalance.get());
		CraftingAttachments.set(p, data.withProgress(after));

		if (after.level() > before.get().level()) {
			onLevelUp(p, d, after.level());
		}
	}

	private static Progress gainXp(Progress before, int xp, CraftingBalance b) {
		// Capped well past level 5 so the saved number never overflows.
		int total = (int) Math.min(1_000_000L, (long) before.xp() + Math.max(0, xp));
		return new Progress(before.discipline(), CraftingRules.levelFor(total, b.levelXp), total, before.pity());
	}

	private static void onLevelUp(ServerPlayer p, Discipline d, int level) {
		p.sendSystemMessage(Component.translatable("crafting.oldrepublic.level_up", name(d), level).withStyle(ChatFormatting.GOLD));
		QuestHooks.onDisciplineLevel(p, d.id(), level);
	}

	/** The outcome of a discipline choice. */
	public enum ChoiceResult {
		CHOSEN,
		ALREADY_HAS,
		FULL,
		SECOND_LOCKED
	}

	/** Whether {@code p} may choose a second discipline now (7.2). */
	public static boolean secondOpen(ServerPlayer p) {
		CraftingData data = CraftingAttachments.get(p);

		if (data.disciplines().isEmpty()) {
			return false;
		}

		return CraftingRules.secondOpen(data.disciplines().get(0).level(), Progression.get(p).level, CraftingBalance.get());
	}

	/**
	 * Chooses {@code d} at a bench (7.1, 7.2): the first freely, the second once it is open. A
	 * discipline chosen again after unlearning starts over at level 1, and its learned schematics
	 * unlock. Reports level 1 through {@link QuestHooks#onDisciplineLevel}.
	 */
	public static ChoiceResult choose(ServerPlayer p, Discipline d) {
		CraftingData data = CraftingAttachments.get(p);

		if (data.has(d)) {
			return ChoiceResult.ALREADY_HAS;
		}

		if (data.disciplines().size() >= CraftingData.MAX_DISCIPLINES) {
			return ChoiceResult.FULL;
		}

		if (!data.disciplines().isEmpty() && !secondOpen(p)) {
			return ChoiceResult.SECOND_LOCKED;
		}

		List<Discipline> unlearned = new ArrayList<>(data.unlearned());
		unlearned.remove(d);
		CraftingData next = new CraftingData(data.disciplines(), unlearned, data.known(), data.firstCrafts(), data.chosenAt(), data.legendaryMisses())
				.withProgress(new Progress(d, 1, 0, 0));

		if (next.chosenAt() < 0) {
			next = next.withChosenAt(Clocks.day(p.level().getServer()));
		}

		CraftingAttachments.set(p, next);
		p.sendSystemMessage(Component.translatable("crafting.oldrepublic.chosen", name(d)).withStyle(ChatFormatting.GOLD));
		QuestHooks.onDisciplineLevel(p, d.id(), 1);
		return ChoiceResult.CHOSEN;
	}

	/** Drops {@code d} (a trainer's service, 7.2): level, XP and pity are lost; learned schematics stay, locked. */
	public static boolean unlearn(ServerPlayer p, Discipline d) {
		CraftingData data = CraftingAttachments.get(p);

		if (!data.has(d)) {
			return false;
		}

		CraftingAttachments.set(p, data.withoutDiscipline(d));
		return true;
	}

	/** The translated name of a discipline. */
	public static Component name(Discipline d) {
		return Component.translatable("discipline.oldrepublic." + d.id());
	}

	/** The client's data record for {@code player} (the synced attachment). */
	public static CraftingData data(Player player) {
		return CraftingAttachments.get(player);
	}

	private Crafting() {
	}
}
