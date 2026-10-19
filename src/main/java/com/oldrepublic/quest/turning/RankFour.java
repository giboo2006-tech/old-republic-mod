package com.oldrepublic.quest.turning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Rank 4 (spec MILESTONE_5.md 8.5): Master for a Jedi, Darth for a Sith, the Wayfarer title for an Independent. The
 * requirements are one pure rule ({@link TurningRules#rankFour}) read here on the server, so the checklist the client
 * shows ({@link RankFourChecklistPayload}) and the dialogue's fail reason always agree. The ceremonies
 * ({@code rank4_saelen}, {@code rank4_sorn}, {@code rank4_compass}; actions {@code rank4:ceremony} and
 * {@code rank4:wayfarer}) pay once per character: after a turning ({@code rank4.paid}) the new path's ceremony gives the
 * rank, the power, the robe, the entry and the banner, but no skill points and no XP. The robes come again once a day.
 */
public final class RankFour {
	/** The robes W7 makes, looked up by id (a missing one is logged, never fatal). */
	public static final String MASTER_ROBE = "jedi_master_robe";
	public static final String DARTH_ROBE = "sith_darth_robe";
	public static final String WAYFARER_MANTLE = "wayfarer_mantle";
	/** Ticks between two looks at a player's checklist (it is sent only when it changed). */
	private static final int CHECK_TICKS = 40;
	private static final Map<UUID, RankFourChecklistPayload> LAST_SENT = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();

	// ---------------------------------------------------------------- the requirements

	/** What the rank 4 checks read about the player. */
	static TurningRules.Standing standing(ServerPlayer p) {
		TurningBalance b = TurningService.balance();
		Path path = Identity.path(p);
		ForceData force = Progression.get(p);
		Faction own = TurningRules.factionOf(path);
		MentorRole role = path == Path.JEDI || path == Path.SITH ? MentorRole.of(path) : null;
		int trusted = 0;

		for (Faction faction : Faction.values()) {
			if (Identity.knows(p, faction) && Identity.reputation(p, faction) >= b.wayfarerTier) {
				trusted++;
			}
		}

		int mastered = 0;

		for (int points : force.mastery.values()) {
			if (Progression.masteryRank(points) >= b.rank4MasteryRank) {
				mastered++;
			}
		}

		return new TurningRules.Standing(path, ChapterFive.stage(p), force.level, Identity.rank(p), Identity.hasChoice(p, RankThree.TITLE_PATHFINDER),
				role == null ? 0 : Identity.trust(p, role.id()), own == null ? 0 : Identity.reputation(p, own), trusted, Identity.alignment(p), mastered,
				ChapterFive.deeds(p));
	}

	/** Whether the player holds rank 4 on their current path (Master, Darth or the Wayfarer title). */
	public static boolean done(ServerPlayer p) {
		return Identity.hasChoice(p, TurningRules.RANK4_DONE);
	}

	/** The {@code rank4:ready} fail reason ({@code key|need|have}), or null when the ceremony may be held now. */
	public static @Nullable String blocker(ServerPlayer p) {
		if (done(p)) {
			return "turning.oldrepublic.rank4.req.done";
		}

		StoryData.Turning t = Story.get(p).turning;

		if (!TurningRules.idle(t) && t.stage() != TurningStage.OFFERED) {
			// Halfway between two paths, nobody names a Master or a Darth.
			return "turning.oldrepublic.rank4.req.turning";
		}

		return TurningRules.rankFourBlocker(standing(p), TurningService.balance());
	}

	/** The checklist as the client gets it (hidden before Chapter Five's AFTERMATH and for a player without a path). */
	public static RankFourChecklistPayload checklist(ServerPlayer p) {
		Path path = Identity.path(p);

		if (!TurningRules.checklistVisible(path, ChapterFive.stage(p))) {
			return RankFourChecklistPayload.HIDDEN;
		}

		List<RankFourChecklistPayload.Entry> entries = new ArrayList<>();

		for (TurningRules.Requirement r : TurningRules.rankFour(standing(p), TurningService.balance())) {
			entries.add(new RankFourChecklistPayload.Entry(r.id(), r.met(), r.have(), r.need()));
		}

		return new RankFourChecklistPayload(true, kindOf(path), done(p), entries);
	}

	private static int kindOf(Path path) {
		return switch (path) {
			case SITH -> RankFourChecklistPayload.DARTH;
			case INDEPENDENT -> RankFourChecklistPayload.WAYFARER;
			default -> RankFourChecklistPayload.MASTER;
		};
	}

	/** Asks for a fresh look at the player's checklist on their next slow tick. */
	public static void markDirty(ServerPlayer p) {
		DIRTY.add(p.getUUID());
	}

	/** Slow tick: the checklist, sent when it changed (and never to a player who has never seen one). */
	static void tick(ServerPlayer p, long day) {
		boolean dirty = DIRTY.remove(p.getUUID());

		if (!dirty && Math.floorMod(p.tickCount, CHECK_TICKS) >= TurningService.SLOW_TICKS) {
			return;
		}

		RankFourChecklistPayload payload = checklist(p);
		RankFourChecklistPayload last = LAST_SENT.get(p.getUUID());

		if (last == null ? payload.visible() : !payload.equals(last)) {
			ServerPlayNetworking.send(p, payload);
			LAST_SENT.put(p.getUUID(), payload);
		}
	}

	// ---------------------------------------------------------------- the ceremonies

	/**
	 * {@code rank4:ceremony}: the Master's ceremony (Saelen), or the Darth's ({@code darthName} 1 to 6, Sorn). Re-checks
	 * every requirement and names the first unmet one.
	 */
	static void ceremony(ServerPlayer p, @Nullable Entity speaker, int darthName) {
		Path path = Identity.path(p);

		if (path != Path.JEDI && path != Path.SITH) {
			return;
		}

		String blocker = blocker(p);

		if (blocker != null) {
			sendReason(p, blocker);
			return;
		}

		boolean jedi = path == Path.JEDI;

		if (!jedi && (darthName < 1 || darthName > TurningRules.DARTH_NAMES)) {
			return;
		}

		boolean paid = pay(p);

		if (!jedi) {
			Identity.recordChoice(p, TurningRules.darthChoice(darthName));
		}

		Identity.promote(p, 4);
		Skills.grant(p, jedi ? TurningRules.FORCE_VALOR : TurningRules.FORCE_RAGE);
		finish(p, jedi ? MASTER_ROBE : DARTH_ROBE, jedi ? JournalEntries.MASTER : JournalEntries.DARTH, paid);
		EffectEvents.around(p.level(), TurningEffects.RANK_FOUR, p.position(), facing(p, speaker), p.getId(), 1f,
				jedi ? TurningEffects.MASTER : TurningEffects.DARTH);
		MentorRole role = MentorRole.of(path);

		if (jedi) {
			TurningService.mentorSays(p, role, "turning.oldrepublic.rank4.master.after");
		} else {
			TurningService.say(p, TurningService.mentorName(role), "turning.oldrepublic.rank4.darth.after",
					Component.translatable("rank4.oldrepublic.darth_name." + darthName));
		}
	}

	/** {@code rank4:wayfarer}: Tessaly Arn's echo gives an Independent her mantle; the title, not a rank. */
	static void wayfarer(ServerPlayer p, @Nullable Vec3 compass) {
		if (Identity.path(p) != Path.INDEPENDENT) {
			return;
		}

		String blocker = blocker(p);

		if (blocker != null) {
			sendReason(p, blocker);
			return;
		}

		boolean paid = pay(p);
		Identity.recordChoice(p, SkillService.WAYFARER_TITLE);
		Skills.grant(p, SkillIds.WAYFARERS_STRIDE);
		finish(p, WAYFARER_MANTLE, JournalEntries.WAYFARER, paid);
		Vec3 at = compass != null ? compass : p.position();
		Vec3 toward = p.position().subtract(at);
		EffectEvents.around(p.level(), TurningEffects.RANK_FOUR, at, toward.horizontalDistanceSqr() < 1.0e-4 ? null : toward.normalize(), p.getId(), 1f,
				TurningEffects.WAYFARER);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic.rank4.wayfarer.after", Component.translatable("title.oldrepublic.wayfarer")));
	}

	/** Records the ceremony; returns whether it was paid already (on an earlier path). */
	private static boolean pay(ServerPlayer p) {
		boolean paid = Identity.hasChoice(p, TurningRules.RANK4_PAID);
		Identity.recordChoice(p, TurningRules.RANK4_DONE);
		return paid;
	}

	/** What every ceremony gives: skill points and XP (once per character), Resonance, the robe, the entry, the banner. */
	private static void finish(ServerPlayer p, String robe, String entry, boolean paid) {
		TurningBalance b = TurningService.balance();

		if (!paid) {
			Progression.grantSkillPoints(p, b.ceremonySp);
			Progression.addXp(p, b.ceremonyXp, "rank_four");
		}

		Progression.grantResonanceMilestone(p, "third_rank", b.ceremonyResonance);
		giveRobe(p, robe);
		SkillService.refresh(p);
		TurningService.entry(p, entry);
		TurningService.banner(p, QuestEffects.LESSON_RANK_FOUR);
		markDirty(p);
	}

	private static @Nullable Vec3 facing(ServerPlayer p, @Nullable Entity speaker) {
		Vec3 dir = speaker == null ? null : p.position().subtract(speaker.position()).horizontal();
		return dir == null || dir.lengthSqr() < 1.0e-4 ? null : dir.normalize();
	}

	// ---------------------------------------------------------------- the robes

	/** The rank 4 robe of the player's path, or null when they hold no rank 4 now. */
	static @Nullable String robeOf(ServerPlayer p) {
		if (!done(p)) {
			return null;
		}

		return switch (Identity.path(p)) {
			case JEDI -> MASTER_ROBE;
			case SITH -> DARTH_ROBE;
			case INDEPENDENT -> WAYFARER_MANTLE;
			default -> null;
		};
	}

	/** Gives the robe and notes the day (the once-a-day reissue). */
	private static void giveRobe(ServerPlayer p, String robe) {
		Item item = item(robe);

		if (item == null) {
			OldRepublicMod.LOGGER.warn("Rank 4 robe {} is not registered; {} gets no robe", robe, p.getName().getString());
			return;
		}

		Rewards.give(p, new ItemStack(item));
		StoryData data = Story.get(p);
		data.counters.put(TurningRules.REISSUE_PREFIX + robe, (int) Clocks.day(p.level().getServer()));
		Story.commit(p, data);
	}

	/**
	 * The mentor or the Compass gives the robe again (once a day) to a holder of rank 4 who neither carries, wears nor
	 * keeps one in their ender chest. Returns whether one was given.
	 */
	public static boolean reissue(ServerPlayer p) {
		String robe = robeOf(p);
		Item item = robe == null ? null : item(robe);

		if (item == null || has(p, item)) {
			return false;
		}

		StoryData data = Story.get(p);
		String key = TurningRules.REISSUE_PREFIX + robe;
		int last = data.counters.containsKey(key) ? data.counter(key) : -1;

		if (!TurningRules.reissueDue(last, Clocks.day(p.level().getServer()))) {
			return false;
		}

		giveRobe(p, robe);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic.rank4.reissued", new ItemStack(item).getHoverName()));
		return true;
	}

	/** Whether the player carries, wears or keeps {@code item} in their ender chest. */
	static boolean has(ServerPlayer p, Item item) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (p.getItemBySlot(slot).is(item)) {
				return true;
			}
		}

		return contains(p.getInventory(), item) || contains(p.getEnderChestInventory(), item);
	}

	private static boolean contains(Container container, Item item) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				return true;
			}
		}

		return false;
	}

	private static @Nullable Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(id)).orElse(null);
	}

	/** A fail reason ({@code key|args}) as a chat line. */
	static void sendReason(ServerPlayer p, String reason) {
		String[] parts = reason.split("\\|");
		Object[] args = new Object[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);
		p.sendSystemMessage(Component.translatable(parts[0], args));
	}

	static void forget(UUID player) {
		LAST_SENT.remove(player);
		DIRTY.remove(player);
	}

	static void clear() {
		LAST_SENT.clear();
		DIRTY.clear();
	}

	private RankFour() {
	}
}
