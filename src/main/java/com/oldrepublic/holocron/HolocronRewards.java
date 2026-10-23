package com.oldrepublic.holocron;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What a holocron gives (spec 4.4, 4.5, 4.7): the first-attunement rewards and the choice matrix,
 * the skill point drip and the collection milestones, and the inventory helpers the other holocron
 * classes share (owner-only drops, current copies, shards).
 */
public final class HolocronRewards {
	/** Choice id of the Keeper of Echoes title, shown on the Standing tab. */
	public static final String TITLE_KEEPER = "title.keeper_of_echoes";

	/**
	 * Attunes {@code id} with {@code choice} and grants everything a first attunement gives, once
	 * per id: the data, XP, Resonance, the codex entry, the teaching (or +1 SP when already owned),
	 * the alignment consequences, the item (unless broken), the progression and the quest hook.
	 *
	 * @param site the pedestal that gave it, recorded in {@code attuned_at}; null for an operator grant
	 * @return false when the id was already attuned or is unknown
	 */
	public static boolean attune(ServerPlayer p, String id, HolocronChoice choice, @Nullable PedestalSite site) {
		HolocronCatalog.Entry entry = HolocronCatalog.find(id);
		HolocronData d = HolocronService.data(p);

		if (entry == null || d.isAttuned(id)) {
			return false;
		}

		HolocronBalance b = HolocronBalance.get();
		Path path = Identity.path(p);

		d.attuned.add(id);
		d.choices.put(id, choice.ordinal());

		if (site != null && !entry.archive() && d.attunedAt.size() < HolocronData.MAX_IDS) {
			d.attunedAt.add(new HolocronData.AttunedAt(site.dimId(), site.pos(), site.siteKey(), id));
		}

		int serial = 0;

		if (choice != HolocronChoice.BREAK) {
			serial = d.serial(id) + 1;
			d.serials.put(id, serial);
		}

		HolocronService.commit(p, d);

		long xp = b.baseXp;

		if (Skills.activeRank(p, SkillIds.RELIC_LORE) > 0) {
			xp = Math.round(xp * (1f + b.relicLoreXpBonus));
		}

		Progression.addXp(p, xp, "holocron");
		Progression.grantResonanceMilestone(p, "holocron_" + id, b.resonance + extraResonance(path, choice, b));
		Journal.unlockCodex(p, JournalEntries.holocronCodex(id));

		String node = HolocronCatalog.teaching(id, path, choice);

		if (node != null) {
			teach(p, node);
		}

		applyChoice(p, id, path, choice, node, b);

		if (serial > 0) {
			give(p, HolocronItems.stack(id, p.getUUID(), serial), site == null ? null : site.top());
		}

		Vec3 at = site == null ? p.position().add(0, 1, 0) : site.top();
		int effect = switch (choice) {
			case SEAL -> HolocronEffects.CHOICE_SEAL;
			case BREAK -> HolocronEffects.CHOICE_BREAK;
			case LISTEN -> HolocronEffects.CHOICE_LISTEN;
			case OWN -> HolocronEffects.CHOICE_OWN;
		};
		EffectEvents.around(p.level(), HolocronEffects.CHOICE, at, null, p.getId(), site == null ? 0f : 1f,
				effect | entry.kind().ordinal() << HolocronEffects.KIND_SHIFT);

		progress(p);
		QuestHooks.onHolocronAttuned(p, id, choice, true);
		return true;
	}

	/**
	 * The extra Resonance of 4.5: a Jedi-path Listen (to a Sith holocron) and a Sith-path Break. A
	 * Sith-path Listen and an Independent's Listen give none.
	 */
	static int extraResonance(Path path, HolocronChoice choice, HolocronBalance b) {
		return switch (choice) {
			case BREAK -> b.breakResonance;
			case LISTEN -> path == Path.JEDI ? b.listenResonance : 0;
			default -> 0;
		};
	}

	/** Shifts, trust and the recorded choice of Seal, Listen and Break (4.5). */
	private static void applyChoice(ServerPlayer p, String id, Path path, HolocronChoice choice, @Nullable String node, HolocronBalance b) {
		String source = "holocron." + id + "." + choice.name().toLowerCase(Locale.ROOT);

		switch (choice) {
			case OWN -> {
				return;
			}
			case SEAL -> Identity.shift(p, new AlignmentShift(source, b.sealShift, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.seal"));
			case BREAK -> {
				Identity.shift(p, new AlignmentShift(source, b.breakShift, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.break"));
				Identity.addTrust(p, MentorRole.SITH.id(), b.breakTrust, "holocron.oldrepublic.trust.break");
			}
			case LISTEN -> {
				int shift = path == Path.JEDI ? b.jediListenShift : path == Path.SITH ? b.sithListenShift : 0;

				if (shift != 0) {
					Identity.shift(p, new AlignmentShift(source, shift, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.listen"));
				}

				if (node != null && Skills.dormant(p, node)) {
					p.sendSystemMessage(Component.translatable("holocron.oldrepublic.lesson_waits"));
				}
			}
		}

		Identity.recordChoice(p, source);
	}

	/** Grants a teaching node, or +1 SP when the player already owns it (4.2). */
	static void teach(ServerPlayer p, String node) {
		if (Skills.rank(p, node) > 0) {
			Progression.grantSkillPoints(p, 1);
			p.sendSystemMessage(Component.translatable("holocron.oldrepublic.teaching_known", Component.translatable("skill.oldrepublic.node." + node + ".name")));
			return;
		}

		SkillService.grant(p, node);
	}

	/**
	 * Grants the skill point drip and the collection milestones that are due and not yet in the
	 * mask (4.7): +1 SP every second world holocron, Resonance at 3 and 9, +1 SP at 6, and at 12
	 * the Keeper of Echoes title with its Resonance. Each bit is granted once, so calling it again
	 * grants nothing more.
	 */
	public static void progress(ServerPlayer p) {
		HolocronBalance b = HolocronBalance.get();
		HolocronData d = HolocronService.data(p);
		int fresh = HolocronRules.milestoneBits(HolocronRules.worldCount(d), b.spEvery) & ~d.milestones;

		if (fresh == 0) {
			return;
		}

		d.milestones |= fresh;
		HolocronService.commit(p, d);

		int points = HolocronRules.dripPoints(fresh) + ((fresh & HolocronData.BIT_6) != 0 ? b.milestone6Sp : 0);

		if (points > 0) {
			Progression.grantSkillPoints(p, points);
			p.sendSystemMessage(Component.translatable("holocron.oldrepublic.skill_points", points));
		}

		if ((fresh & HolocronData.BIT_3) != 0) {
			Progression.grantResonanceMilestone(p, "holocrons_3", b.milestone3Resonance);
		}

		if ((fresh & HolocronData.BIT_9) != 0) {
			Progression.grantResonanceMilestone(p, "holocrons_9", b.milestone9Resonance);
		}

		if ((fresh & HolocronData.BIT_12) != 0) {
			Identity.recordChoice(p, TITLE_KEEPER);
			Progression.grantResonanceMilestone(p, "holocrons_12", b.milestone12Resonance);
			p.sendSystemMessage(Component.translatable("holocron.oldrepublic.title_keeper"));
		}
	}

	// ---------------------------------------------------------------- inventory helpers

	/**
	 * Puts {@code stack} in the player's inventory; whatever does not fit is dropped at {@code at}
	 * (or at the player) for the player alone, never despawning (the CrystalGrant pattern).
	 */
	public static void give(ServerPlayer p, ItemStack stack, @Nullable Vec3 at) {
		if (stack.isEmpty() || p.getInventory().add(stack) || stack.isEmpty()) {
			return;
		}

		Vec3 pos = at != null ? at.add(0, 0.3, 0) : p.position();
		ItemEntity entity = new ItemEntity(p.level(), pos.x, pos.y, pos.z, stack, 0, 0, 0);
		entity.setTarget(p.getUUID());
		entity.setUnlimitedLifetime();
		entity.setPickUpDelay(20);
		p.level().addFreshEntity(entity);
		p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.dropped"));
	}

	/** Every holocron stamp in the main inventory (hotbar and storage, no armour, offhand or ender chest). */
	public static List<HolocronStamp> mainInventoryStamps(Inventory inventory) {
		List<HolocronStamp> stamps = new ArrayList<>();

		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);

			if (stamp != null) {
				stamps.add(stamp);
			}
		}

		return stamps;
	}

	/** Ids of which the player carries their own current copy anywhere in the inventory. */
	public static Set<String> heldCurrent(ServerPlayer p, HolocronData d) {
		Set<String> ids = new HashSet<>();
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			HolocronStamp stamp = inventory.getItem(i).get(ModComponents.HOLOCRON);

			if (stamp != null && isCurrent(stamp, p, d)) {
				ids.add(stamp.id());
			}
		}

		return ids;
	}

	/** The player's own copy carrying the current serial of an attuned, unreturned holocron. */
	public static boolean isCurrent(HolocronStamp stamp, ServerPlayer p, HolocronData d) {
		return stamp.owner().equals(p.getUUID()) && d.isAttuned(stamp.id()) && !d.returned.contains(stamp.id())
				&& d.serial(stamp.id()) > 0 && stamp.serial() == d.serial(stamp.id());
	}

	/** Removes one current copy of {@code id} from the inventory; false when there is none. */
	static boolean takeCurrent(ServerPlayer p, HolocronData d, String id) {
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);

			if (stamp != null && stamp.id().equals(id) && isCurrent(stamp, p, d)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}

		return false;
	}

	/** Echo shards in the whole inventory. */
	public static int shards(ServerPlayer p) {
		Inventory inventory = p.getInventory();
		int count = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(M3Items.HOLOCRON_SHARD)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	/** Removes {@code n} shards if the player has them all; false (and nothing taken) otherwise. */
	static boolean takeShards(ServerPlayer p, int n) {
		if (shards(p) < n) {
			return false;
		}

		Inventory inventory = p.getInventory();
		int left = n;

		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(M3Items.HOLOCRON_SHARD)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}

		inventory.setChanged();
		return true;
	}

	private HolocronRewards() {
	}
}
