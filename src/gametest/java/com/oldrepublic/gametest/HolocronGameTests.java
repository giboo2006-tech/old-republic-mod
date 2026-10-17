package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronArchive;
import com.oldrepublic.holocron.HolocronAttachments;
import com.oldrepublic.holocron.HolocronAttunement;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronCarry;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronItem;
import com.oldrepublic.holocron.HolocronItems;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronPedestals;
import com.oldrepublic.holocron.HolocronPedestals.Outcome;
import com.oldrepublic.holocron.HolocronReturns;
import com.oldrepublic.holocron.HolocronRewards;
import com.oldrepublic.holocron.HolocronRules;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.holocron.HolocronStamp;
import com.oldrepublic.holocron.PedestalSite;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Holocron game tests (spec 17.1, W3): pedestals, the attunement moment, the choice matrix,
 * restoring and stale copies, returning, carrying, progression, echoes, shards, the Archive and the
 * saved formats.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}; players are listed mock players,
 * removed at the end; pedestals are {@link PedestalSite#of} seams through
 * {@link HolocronPedestals#use} with the story gate skipped, a fixed day and a seeded random, and a
 * hostile check limited to the test's own bounds so a neighbouring test's mobs cannot interfere.
 * Nothing here writes world, global or server state: all state lives on the mock players.
 */
public class HolocronGameTests {
	private static final String PEDESTAL_FIXTURE = "oldrepublic_test:pedestal_fixture";
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** Anchors of the pedestal fixture. */
	private static final BlockPos PEDESTAL = new BlockPos(3, 1, 3);
	private static final BlockPos GUARD = new BlockPos(3, 1, 6);
	private static final BlockPos SENTINEL_1 = new BlockPos(0, 1, 0);
	private static final long SEED = 31L;

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static ServerPlayer onPath(ServerPlayer p, Path path) {
		IdentityData data = Identity.get(p);
		data.path = path;
		Identity.commit(p, data);
		return p;
	}

	/** A pedestal at {@code relative} with no site piece, keyed by its position. */
	private static PedestalSite pedestal(GameTestHelper helper, BlockPos relative) {
		return pedestal(helper, relative, null);
	}

	private static PedestalSite pedestal(GameTestHelper helper, BlockPos relative, @Nullable String fixedId) {
		BlockPos pos = helper.absolutePos(relative);
		return PedestalSite.of(helper.getLevel().dimension(), pos, PedestalSite.looseKey(helper.getLevel().dimension(), pos), fixedId);
	}

	private static Outcome use(GameTestHelper helper, ServerPlayer p, PedestalSite site, HolocronKind kind, boolean sneaking, long day) {
		return HolocronPedestals.use(p, site, kind, sneaking, true, day, RandomSource.create(SEED), e -> helper.getBounds().contains(e.position()));
	}

	/** Uses the pedestal, expects an attunement of {@code expected}, and confirms it with {@code choice}. */
	private static void attuneAt(GameTestHelper helper, ServerPlayer p, PedestalSite site, HolocronKind kind, String expected, HolocronChoice choice) {
		Outcome outcome = use(helper, p, site, kind, false, 0);
		helper.assertTrue(outcome == Outcome.ATTUNE_STARTED, msg("the pedestal starts an attunement, got " + outcome));
		helper.assertTrue(expected.equals(HolocronAttunement.pendingId(p)), msg("the pedestal offers " + expected + ", got " + HolocronAttunement.pendingId(p)));
		helper.assertTrue(HolocronAttunement.confirm(p, choice), msg(choice + " confirms " + expected));
	}

	/** Own copies of {@code id} in the whole inventory (any serial). */
	private static int copies(ServerPlayer p, String id) {
		Inventory inventory = p.getInventory();
		int n = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			HolocronStamp stamp = inventory.getItem(i).get(ModComponents.HOLOCRON);

			if (stamp != null && stamp.id().equals(id)) {
				n++;
			}
		}

		return n;
	}

	private static @Nullable ItemStack find(ServerPlayer p, String id) {
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);

			if (stamp != null && stamp.id().equals(id)) {
				return stack;
			}
		}

		return null;
	}

	/** Marks ids attuned in the data alone (no rewards), as an earlier session would have left them. */
	private static void markAttuned(ServerPlayer p, String... ids) {
		HolocronData d = HolocronService.data(p);

		for (String id : ids) {
			d.attuned.add(id);
			d.choices.put(id, HolocronChoice.OWN.ordinal());
		}

		HolocronService.commit(p, d);
	}

	/** Whether a story flag or counter names the Archive or one of its choices. */
	private static boolean namesArchiveChoice(String key) {
		String lower = key.toLowerCase(Locale.ROOT);

		if (lower.contains("archive")) {
			return true;
		}

		for (ArchiveChoice choice : ArchiveChoice.values()) {
			if (lower.contains(choice.name().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}

		return false;
	}

	private static String[] worldIds(HolocronKind kind) {
		return HolocronCatalog.world(kind).stream().map(HolocronCatalog.Entry::id).toArray(String[]::new);
	}

	// ---------------------------------------------------------------- pedestals

	@GameTest(structure = ARENA, maxTicks = 40)
	public void pedestalOrderPerKind(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			BlockPos[] spots = {new BlockPos(3, 1, 3), new BlockPos(6, 1, 3), new BlockPos(9, 1, 3), new BlockPos(12, 1, 3)};
			String[] jedi = worldIds(HolocronKind.JEDI);

			for (int i = 0; i < spots.length; i++) {
				attuneAt(helper, player, pedestal(helper, spots[i]), HolocronKind.JEDI, jedi[i], HolocronChoice.OWN);
				helper.assertTrue(copies(player, jedi[i]) == 1, msg(jedi[i] + " is given as one item"));
			}

			helper.assertTrue(HolocronService.attunedCount(player, HolocronKind.JEDI) == 4 && HolocronService.attunedCount(player, null) == 4,
					msg("four Jedi holocrons attuned, in 4.2 order"));

			attuneAt(helper, player, pedestal(helper, new BlockPos(3, 1, 9)), HolocronKind.WAYFARER, HolocronCatalog.WAYFARERS_COMPASS, HolocronChoice.OWN);

			PedestalSite fifth = pedestal(helper, new BlockPos(15, 1, 3));
			helper.assertTrue(use(helper, player, fifth, HolocronKind.JEDI, false, 0) == Outcome.ECHO, msg("a fifth Jedi pedestal gives the echo"));
			helper.assertTrue(use(helper, player, fifth, HolocronKind.JEDI, false, 0) == Outcome.ALREADY_SPOKEN, msg("then it has already spoken"));
			helper.assertTrue(use(helper, player, pedestal(helper, spots[0]), HolocronKind.JEDI, false, 0) == Outcome.ECHO,
					msg("a pedestal that gave a holocron gives its echo once the kind is complete"));
			helper.assertTrue(!HolocronAttunement.pending(player), msg("nothing is left pending"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void samePedestalTwiceGivesOneHolocron(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);

		try {
			PedestalSite site = pedestal(helper, PEDESTAL);
			attuneAt(helper, player, site, HolocronKind.WAYFARER, HolocronCatalog.WAYFARERS_COMPASS, HolocronChoice.OWN);

			Outcome again = use(helper, player, site, HolocronKind.WAYFARER, false, 0);
			helper.assertTrue(again == Outcome.ALREADY_SPOKEN, msg("the same pedestal never gives a second holocron, got " + again));

			HolocronData data = HolocronService.view(player);
			helper.assertTrue(data != null && data.attunedAt.size() == 1 && data.gaveAt(site.dimId(), site.pos()),
					msg("attuned_at records the one pedestal"));
			helper.assertTrue(HolocronService.attunedCount(player, HolocronKind.WAYFARER) == 1, msg("one Wayfarer holocron"));
			SitePiece piece = new SitePiece(SiteKind.WRECK_FIELD, HolocronCatalog.dialogue("site_stand_in"), new BoundingBox(0, 0, 0, 1, 1, 1),
					Rotation.NONE, BlockPos.ZERO, site.siteKey());
			helper.assertTrue(HolocronService.usedSites(player).test(piece), msg("usedSites knows the site key"));
			helper.assertTrue(HolocronService.exhaustedSites(player).test(piece), msg("a wreck field whose Wayfarer pedestal was used is exhausted for Sense"));

			PedestalSite other = pedestal(helper, GUARD);
			helper.assertTrue(use(helper, player, other, HolocronKind.WAYFARER, false, 0) == Outcome.ATTUNE_STARTED
					&& HolocronCatalog.WAYFARER_PELL.equals(HolocronAttunement.pendingId(player)), msg("another pedestal offers the next one, Pell"));
			helper.assertTrue(use(helper, player, other, HolocronKind.WAYFARER, false, 0) == Outcome.REFUSED_BUSY,
					msg("using the pedestal again during one's own moment is refused"));
			ServerPlayer second = onPath(M3TestPlayers.listed(helper, new BlockPos(4, 1, 5)), Path.INDEPENDENT);

			try {
				helper.assertTrue(use(helper, second, other, HolocronKind.WAYFARER, false, 0) == Outcome.REFUSED_BUSY && !HolocronAttunement.pending(second),
						msg("while one player's moment is open at a pedestal, it refuses everyone else"));
			} finally {
				M3TestPlayers.remove(second);
			}

			HolocronAttunement.cancel(player, "test");
			helper.assertTrue(!HolocronService.hasAttuned(player, HolocronCatalog.WAYFARER_PELL), msg("a cancelled moment attunes nothing"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void couriersFallGivesOnlyCompass(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);

		try {
			PedestalSite fall = pedestal(helper, PEDESTAL, HolocronCatalog.WAYFARERS_COMPASS);
			attuneAt(helper, player, fall, HolocronKind.WAYFARER, HolocronCatalog.WAYFARERS_COMPASS, HolocronChoice.OWN);

			PedestalSite fallElsewhere = pedestal(helper, GUARD, HolocronCatalog.WAYFARERS_COMPASS);
			helper.assertTrue(use(helper, player, fallElsewhere, HolocronKind.WAYFARER, false, 0) == Outcome.ALREADY_SPOKEN,
					msg("a fixed Compass pedestal never gives Pell or a later Wayfarer holocron"));
			helper.assertTrue(!HolocronAttunement.pending(player), msg("no attunement started"));

			markAttuned(player, worldIds(HolocronKind.WAYFARER));
			helper.assertTrue(use(helper, player, fallElsewhere, HolocronKind.WAYFARER, false, 0) == Outcome.ECHO,
					msg("once the Wayfarer row is complete, the Compass pedestal gives only the echo"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void pedestalRefusesWithHostileNearAndNeverWakesSentinels(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			PedestalSite site = pedestal(helper, PEDESTAL);
			Zombie awake = helper.spawn(EntityTypes.ZOMBIE, GUARD);
			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, false, 0) == Outcome.REFUSED_HOSTILE,
					msg("an awake hostile within 16 blocks refuses the pedestal"));
			helper.assertTrue(!HolocronAttunement.pending(player), msg("a refusal starts nothing"));
			awake.discard();

			// A dormant sentinel is a no-AI mob: spawnWithNoFreeWill only drops the goals, so set the flag.
			Mob dormant = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, SENTINEL_1);
			dormant.setNoAi(true);
			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, false, 0) == Outcome.ATTUNE_STARTED,
					msg("a dormant (no-AI) sentinel stand-in does not count"));
			helper.assertTrue(dormant.isNoAi() && dormant.getTarget() == null, msg("using the pedestal never wakes or aggroes it"));
			HolocronAttunement.cancel(player, "test");
			dormant.discard();
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 120)
	public void closedDialogueOrDamageAttunesNothing(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);
		PedestalSite site = pedestal(helper, PEDESTAL);
		String first = worldIds(HolocronKind.JEDI)[0];
		helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, false, 0) == Outcome.ATTUNE_STARTED, msg("the moment starts"));

		helper.runAfterDelay(HolocronHelpers.DIALOGUE_DELAY, () -> {
			try {
				DialogueSession session = Dialogues.session(player);
				helper.assertTrue(session != null && session.dialogue().equals(HolocronCatalog.dialogue(first)),
						msg("after the rise the gatekeeper's dialogue is open"));
				Dialogues.close(player);
				helper.assertTrue(!HolocronAttunement.pending(player), msg("closing the dialogue with no choice ends the moment"));
				helper.assertTrue(!HolocronService.hasAttuned(player, first) && copies(player, first) == 0, msg("nothing attuned, nothing given"));
				HolocronData data = HolocronService.view(player);
				helper.assertTrue(data == null || data.attunedAt.isEmpty(), msg("the pedestal is not used up"));

				helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, false, 0) == Outcome.ATTUNE_STARTED, msg("the player can try again at once"));
				// A mock player's client never finishes loading, so vanilla lets nothing hurt it: fire the
				// event the server fires after real damage instead.
				ServerLevel level = helper.getLevel();
				ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(player, level.damageSources().fellOutOfWorld(), 1f, 1f, false);
				helper.assertTrue(!HolocronAttunement.pending(player), msg("damage during the rise ends the moment"));
				helper.assertTrue(!HolocronService.hasAttuned(player, first) && copies(player, first) == 0, msg("still nothing attuned"));
			} finally {
				M3TestPlayers.remove(player);
			}

			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- choices

	@GameTest(structure = ARENA, maxTicks = 40)
	public void sealListenBreakMatrix(GameTestHelper helper) {
		helper.assertTrue(HolocronRules.allowed(Path.JEDI, HolocronKind.SITH).equals(EnumSet.of(HolocronChoice.SEAL, HolocronChoice.LISTEN)),
				msg("Jedi reading Sith: Seal or Listen"));
		helper.assertTrue(HolocronRules.allowed(Path.SITH, HolocronKind.JEDI).equals(EnumSet.of(HolocronChoice.BREAK, HolocronChoice.LISTEN)),
				msg("Sith reading Jedi: Break or Listen"));
		helper.assertTrue(HolocronRules.allowed(Path.INDEPENDENT, HolocronKind.JEDI).equals(EnumSet.of(HolocronChoice.LISTEN))
				&& HolocronRules.allowed(Path.NONE, HolocronKind.SITH).equals(EnumSet.of(HolocronChoice.LISTEN)), msg("Independent: Listen only"));
		helper.assertTrue(HolocronRules.allowed(Path.SITH, HolocronKind.WAYFARER).equals(EnumSet.of(HolocronChoice.OWN))
				&& HolocronRules.allowed(Path.JEDI, HolocronKind.JEDI).equals(EnumSet.of(HolocronChoice.OWN)), msg("own side and Wayfarer: remember"));

		ServerPlayer jedi = onPath(M3TestPlayers.listed(helper, new BlockPos(2, 1, 2)), Path.JEDI);
		ServerPlayer sith = onPath(M3TestPlayers.listed(helper, new BlockPos(20, 1, 20)), Path.SITH);

		try {
			String[] sithIds = worldIds(HolocronKind.SITH);
			String[] jediIds = worldIds(HolocronKind.JEDI);

			// Jedi path, Sith holocrons: Seal, then Listen.
			PedestalSite tomb = pedestal(helper, new BlockPos(4, 1, 4));
			helper.assertTrue(use(helper, jedi, tomb, HolocronKind.SITH, false, 0) == Outcome.ATTUNE_STARTED, msg("the Jedi opens a Sith holocron"));
			helper.assertTrue(!HolocronAttunement.confirm(jedi, HolocronChoice.OWN) && HolocronAttunement.pending(jedi),
					msg("a choice the path does not allow is refused and the moment stays open"));
			int before = Identity.alignment(jedi);
			helper.assertTrue(HolocronAttunement.confirm(jedi, HolocronChoice.SEAL), msg("Seal confirms"));
			helper.assertTrue(Identity.alignment(jedi) - before == HolocronBalanceView.SEAL_SHIFT, msg("Seal: light +3, got " + (Identity.alignment(jedi) - before)));
			helper.assertTrue(Identity.hasChoice(jedi, "holocron." + sithIds[0] + ".seal"), msg("the Seal choice is recorded"));
			helper.assertTrue(copies(jedi, sithIds[0]) == 1, msg("a sealed holocron is still given"));
			helper.assertTrue(Skills.rank(jedi, SkillIds.TOMB_HUNGER) == 0, msg("Seal never grants the node"));

			before = Identity.alignment(jedi);
			attuneAt(helper, jedi, pedestal(helper, new BlockPos(6, 1, 4)), HolocronKind.SITH, sithIds[1], HolocronChoice.LISTEN);
			helper.assertTrue(Identity.alignment(jedi) - before == HolocronBalanceView.JEDI_LISTEN_SHIFT, msg("Jedi Listen: dark -4"));
			helper.assertTrue(Skills.rank(jedi, SkillIds.CRACKLING_GRASP) > 0 && Skills.dormant(jedi, SkillIds.CRACKLING_GRASP),
					msg("Listen grants the node, dormant while the Jedi is not Dark"));
			helper.assertTrue(Progression.get(jedi).hasFlag("resonance:holocron_" + sithIds[1]), msg("the Resonance milestone is granted"));

			// Sith path, Jedi holocrons: Break, then Listen.
			before = Identity.alignment(sith);
			int trust = Identity.trust(sith, MentorRole.SITH.id());
			attuneAt(helper, sith, pedestal(helper, new BlockPos(18, 1, 18)), HolocronKind.JEDI, jediIds[0], HolocronChoice.BREAK);
			helper.assertTrue(Identity.alignment(sith) - before == HolocronBalanceView.BREAK_SHIFT, msg("Break: dark -3"));
			helper.assertTrue(Identity.trust(sith, MentorRole.SITH.id()) - trust == HolocronBalanceView.BREAK_TRUST, msg("Break: Sorn trust +10"));
			helper.assertTrue(copies(sith, jediIds[0]) == 0 && HolocronService.hasAttuned(sith, jediIds[0]),
					msg("Break destroys the item but the holocron counts as attuned"));
			helper.assertTrue(Skills.rank(sith, SkillIds.WATCHERS_INSIGHT) == 0, msg("Break never grants the node"));

			before = Identity.alignment(sith);
			attuneAt(helper, sith, pedestal(helper, new BlockPos(16, 1, 18)), HolocronKind.JEDI, jediIds[1], HolocronChoice.LISTEN);
			helper.assertTrue(Identity.alignment(sith) - before == HolocronBalanceView.SITH_LISTEN_SHIFT, msg("Sith Listen: light +4"));
			helper.assertTrue(Skills.rank(sith, SkillIds.MENDING_BREATH) > 0 && Skills.dormant(sith, SkillIds.MENDING_BREATH),
					msg("Listen grants the node, dormant while the Sith is not Light"));
			helper.assertTrue(HolocronAttunement.pendingId(jedi) == null && HolocronAttunement.pendingId(sith) == null, msg("nothing left pending"));
		} finally {
			M3TestPlayers.remove(jedi);
			M3TestPlayers.remove(sith);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- restore and return

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void restoreBumpsSerialAndStaleCopyIsSilent(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			PedestalSite site = pedestal(helper, PEDESTAL);
			String id = worldIds(HolocronKind.JEDI)[0];
			attuneAt(helper, player, site, HolocronKind.JEDI, id, HolocronChoice.OWN);
			ItemStack held = find(player, id);
			helper.assertTrue(held != null && held.get(ModComponents.HOLOCRON).serial() == 1, msg("the first issue carries serial 1"));

			ItemStack stash = held.copy();
			player.getInventory().clearContent();
			helper.assertTrue(use(helper, player, pedestal(helper, GUARD), HolocronKind.JEDI, true, 3) == Outcome.RESTORED, msg("sneak-use restores it"));
			ItemStack restored = find(player, id);
			helper.assertTrue(restored != null && restored.get(ModComponents.HOLOCRON).serial() == 2 && HolocronService.view(player).serial(id) == 2,
					msg("the restored copy carries serial 2"));

			HolocronData data = HolocronService.data(player);
			helper.assertTrue(!HolocronRewards.isCurrent(stash.get(ModComponents.HOLOCRON), player, data), msg("the stashed copy is stale"));
			HolocronItem.replay(player, stash);
			helper.assertTrue(Dialogues.session(player) == null, msg("a stale copy does not answer"));

			HolocronItem.replay(player, restored);
			DialogueSession session = Dialogues.session(player);
			helper.assertTrue(session != null && session.dialogue().equals(HolocronCatalog.dialogue(id)) && "replay".equals(session.node()),
					msg("the current copy replays its gatekeeper"));
			Dialogues.close(player);

			player.getInventory().add(stash);
			helper.assertTrue(HolocronCarry.refresh(player) == 1, msg("stale and current together still count once"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void returnThenRestoreRefused(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			String id = worldIds(HolocronKind.JEDI)[0];
			attuneAt(helper, player, pedestal(helper, PEDESTAL), HolocronKind.JEDI, id, HolocronChoice.OWN);
			helper.assertTrue(id.equals(HolocronService.returnCandidate(player, HolocronReturns.JEDI)), msg("Saelen would take it"));
			helper.assertTrue(HolocronService.returnTo(player, HolocronReturns.JEDI), msg("the return succeeds"));
			helper.assertTrue(copies(player, id) == 0 && HolocronService.view(player).returned.contains(id), msg("the item is consumed and recorded"));
			helper.assertTrue(use(helper, player, pedestal(helper, GUARD), HolocronKind.JEDI, true, 0) == Outcome.NOTHING_TO_RESTORE,
					msg("a returned holocron can never be restored"));
			helper.assertTrue(copies(player, id) == 0, msg("no copy came back"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void breakThenRestoreRefused(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.SITH);

		try {
			String id = worldIds(HolocronKind.JEDI)[0];
			attuneAt(helper, player, pedestal(helper, PEDESTAL), HolocronKind.JEDI, id, HolocronChoice.BREAK);
			helper.assertTrue(HolocronService.view(player).choice(id) == HolocronChoice.BREAK, msg("the Break is stored"));
			helper.assertTrue(use(helper, player, pedestal(helper, GUARD), HolocronKind.JEDI, true, 0) == Outcome.NOTHING_TO_RESTORE,
					msg("a broken holocron can never be restored"));
			helper.assertTrue(copies(player, id) == 0, msg("no copy exists"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void restoreOncePerDay(GameTestHelper helper) {
		HolocronData pure = new HolocronData();
		helper.assertTrue(HolocronRules.mayRestore(pure, 0), msg("a player who never restored may restore"));
		pure.restoreDay = 4;
		helper.assertTrue(!HolocronRules.mayRestore(pure, 4) && HolocronRules.mayRestore(pure, 5), msg("one restore per day index"));

		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			String[] jedi = worldIds(HolocronKind.JEDI);
			PedestalSite site = pedestal(helper, PEDESTAL);
			attuneAt(helper, player, site, HolocronKind.JEDI, jedi[0], HolocronChoice.OWN);
			attuneAt(helper, player, pedestal(helper, GUARD), HolocronKind.JEDI, jedi[1], HolocronChoice.OWN);
			player.getInventory().clearContent();

			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, true, 5) == Outcome.RESTORED && copies(player, jedi[0]) == 1,
					msg("day 5: the first lost holocron in 4.2 order is restored"));
			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, true, 5) == Outcome.RESTORE_COOLDOWN && copies(player, jedi[1]) == 0,
					msg("a second restore the same day is refused"));
			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, true, 6) == Outcome.RESTORED && copies(player, jedi[1]) == 1,
					msg("day 6: the next lost one (the carried one is skipped) is restored"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void returnOncePerIdInOrder(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			String[] jedi = worldIds(HolocronKind.JEDI);
			String sith = worldIds(HolocronKind.SITH)[0];
			HolocronRewards.attune(player, HolocronCatalog.WAYFARERS_COMPASS, HolocronChoice.OWN, null);
			HolocronRewards.attune(player, sith, HolocronChoice.SEAL, null);
			HolocronRewards.attune(player, jedi[1], HolocronChoice.OWN, null);
			HolocronRewards.attune(player, jedi[0], HolocronChoice.OWN, null);

			List<String> order = new ArrayList<>();
			String next;

			while ((next = HolocronService.returnCandidate(player, HolocronReturns.JEDI)) != null) {
				order.add(next);
				helper.assertTrue(HolocronService.returnTo(player, HolocronReturns.JEDI), msg("Saelen takes " + next));
			}

			helper.assertTrue(order.equals(List.of(jedi[0], jedi[1], sith)), msg("Jedi row first, then the sealed Sith holocron: " + order));
			helper.assertTrue(HolocronService.returnCandidate(player, HolocronReturns.SITH) == null, msg("Sorn takes nothing from a Jedi"));
			helper.assertTrue(!HolocronService.returnTo(player, HolocronReturns.JEDI), msg("each id returns once"));

			int republic = Identity.reputation(player, Faction.REPUBLIC);
			helper.assertTrue(HolocronCatalog.WAYFARERS_COMPASS.equals(HolocronService.returnCandidate(player, HolocronReturns.REPUBLIC)),
					msg("an officer takes the Wayfarer holocron"));
			helper.assertTrue(HolocronService.returnTo(player, HolocronReturns.REPUBLIC)
					&& Identity.reputation(player, Faction.REPUBLIC) - republic == HolocronBalanceView.RETURN_REP, msg("Republic +40"));

			HolocronRewards.attune(player, jedi[2], HolocronChoice.OWN, null);
			Identity.setReputation(player, Faction.SITH_EMPIRE, -1000);
			helper.assertTrue(!HolocronService.returnTo(player, HolocronReturns.IMPERIAL) && copies(player, jedi[2]) == 1,
					msg("an officer whose army holds the player Hostile refuses"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- carry, progression, echoes, shards

	@GameTest(maxTicks = 20)
	public void carryBonusDistinctCurrentSerialMainInventoryCap3(GameTestHelper helper) {
		UUID owner = new UUID(0L, 1L);
		UUID other = new UUID(0L, 2L);
		Map<String, Integer> serials = new HashMap<>();
		String[] jedi = worldIds(HolocronKind.JEDI);
		String sith = worldIds(HolocronKind.SITH)[0];
		serials.put(jedi[0], 2);
		serials.put(jedi[1], 1);
		serials.put(sith, 1);
		List<HolocronStamp> stamps = List.of(new HolocronStamp(jedi[0], owner, 2), new HolocronStamp(jedi[0], owner, 2),
				new HolocronStamp(jedi[0], owner, 1), new HolocronStamp(jedi[1], other, 1), new HolocronStamp(sith, owner, 1));
		helper.assertTrue(HolocronRules.carryCount(stamps, owner, serials, Path.INDEPENDENT, 3) == 2,
				msg("duplicates count once, stale and foreign copies not at all"));
		helper.assertTrue(HolocronRules.carryCount(stamps, owner, serials, Path.JEDI, 3) == 1, msg("a Jedi draws on Jedi and Wayfarer only"));
		helper.assertTrue(HolocronRules.carryCount(stamps, owner, serials, Path.SITH, 3) == 1, msg("a Sith draws on Sith and Wayfarer only"));

		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);

		try {
			for (String id : worldIds(HolocronKind.WAYFARER)) {
				HolocronRewards.attune(player, id, HolocronChoice.OWN, null);
			}

			helper.assertTrue(HolocronCarry.refresh(player) == 3, msg("four carried: capped at 3"));
			helper.assertTrue(Math.abs(HolocronCarry.INSTANCE.regenMultiplier(player) - 1.12f) < 1.0E-4f, msg("3 x 4% = +12% regen"));

			ItemStack offhand = find(player, HolocronCatalog.WAYFARERS_COMPASS).copy();
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.OFF_HAND, offhand);
			player.getEnderChestInventory().addItem(HolocronItems.stack(HolocronCatalog.WAYFARER_PELL, player.getUUID(), 1));
			helper.assertTrue(HolocronCarry.refresh(player) == 0, msg("the offhand and the ender chest are not the main inventory"));
			helper.assertTrue(Math.abs(HolocronCarry.INSTANCE.regenMultiplier(player) - 1f) < 1.0E-4f, msg("no bonus"));
			player.getEnderChestInventory().clearContent();
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void spDripAndMilestones3_6_9_12(GameTestHelper helper) {
		int drip = HolocronData.DRIP_SHIFT;
		helper.assertTrue(HolocronRules.milestoneBits(1) == 0, msg("one holocron: nothing due"));
		helper.assertTrue(HolocronRules.milestoneBits(2) == 1 << drip, msg("two: the first drip"));
		helper.assertTrue(HolocronRules.milestoneBits(3) == (HolocronData.BIT_3 | 1 << drip), msg("three: the first milestone"));
		helper.assertTrue(HolocronRules.milestoneBits(12) == (0xF | 0x3F << drip), msg("twelve: everything, six drips"));

		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);

		try {
			List<String> world = HolocronCatalog.all().stream().filter(e -> !e.archive()).map(HolocronCatalog.Entry::id).toList();
			int[] steps = {2, 3, 6, 9, 12};
			int[] expected = {1, 0, 3, 1, 2};
			int done = 0;

			for (int i = 0; i < steps.length; i++) {
				markAttuned(player, world.subList(done, steps[i]).toArray(String[]::new));
				done = steps[i];
				int before = Progression.get(player).skillPoints;
				HolocronRewards.progress(player);
				int gained = Progression.get(player).skillPoints - before;
				helper.assertTrue(gained == expected[i], msg(steps[i] + " holocrons: expected +" + expected[i] + " SP, got +" + gained));
				before = Progression.get(player).skillPoints;
				HolocronRewards.progress(player);
				helper.assertTrue(Progression.get(player).skillPoints == before, msg("each bit is granted once"));
			}

			helper.assertTrue(Progression.get(player).hasFlag("resonance:holocrons_3") && Progression.get(player).hasFlag("resonance:holocrons_9")
					&& Progression.get(player).hasFlag("resonance:holocrons_12"), msg("Resonance at 3, 9 and 12"));
			helper.assertTrue(Identity.hasChoice(player, HolocronRewards.TITLE_KEEPER), msg("the Keeper of Echoes title at 12"));
			helper.assertTrue(HolocronService.view(player).milestones == (0xF | 0x3F << drip), msg("the mask is full"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void echoOncePerPedestalListCap64(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);

		try {
			PedestalSite a = pedestal(helper, PEDESTAL);
			helper.assertTrue(use(helper, player, a, HolocronKind.JEDI, false, 0) == Outcome.ATTUNE_STARTED, msg("no echo before the kind is complete"));
			HolocronAttunement.cancel(player, "test");

			markAttuned(player, worldIds(HolocronKind.JEDI));
			int shards = HolocronRewards.shards(player);
			helper.assertTrue(use(helper, player, a, HolocronKind.JEDI, false, 0) == Outcome.ECHO, msg("a complete kind gives the echo"));
			helper.assertTrue(HolocronRewards.shards(player) - shards == 1, msg("the echo gives one shard"));
			helper.assertTrue(use(helper, player, a, HolocronKind.JEDI, false, 0) == Outcome.ALREADY_SPOKEN, msg("once per pedestal"));
			helper.assertTrue(use(helper, player, pedestal(helper, GUARD), HolocronKind.JEDI, false, 0) == Outcome.ECHO, msg("another pedestal echoes too"));

			HolocronData d = HolocronService.data(player);
			HolocronData.PedestalRef oldest = d.echoes.getFirst();

			while (d.echoes.size() < HolocronData.MAX_ECHOES) {
				d.echoes.add(new HolocronData.PedestalRef("oldrepublic_test:filler", new BlockPos(d.echoes.size(), 0, 0)));
			}

			HolocronService.commit(player, d);
			PedestalSite last = pedestal(helper, SENTINEL_1);
			helper.assertTrue(use(helper, player, last, HolocronKind.JEDI, false, 0) == Outcome.ECHO, msg("a new pedestal echoes with the list full"));
			HolocronData after = HolocronService.view(player);
			helper.assertTrue(after.echoes.size() == HolocronData.MAX_ECHOES, msg("the list stays at 64"));
			helper.assertTrue(!after.echoes.contains(oldest) && after.echoClaimed(last.dimId(), last.pos()), msg("the oldest entry is dropped"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void shardExchangeAndAmends(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			player.getInventory().add(new ItemStack(M3Items.HOLOCRON_SHARD, 5));
			DialogueConditions.Condition shards = DialogueConditions.get("shards");
			helper.assertTrue(shards != null && shards.failReason(player, null, "5") == null && shards.failReason(player, null, "6") != null,
					msg("the shards condition counts the inventory"));

			int republic = Identity.reputation(player, Faction.REPUBLIC);
			helper.assertTrue(HolocronReturns.exchangeShards(player, HolocronReturns.REPUBLIC), msg("two shards buy a commendation"));
			helper.assertTrue(HolocronRewards.shards(player) == 3 && Identity.reputation(player, Faction.REPUBLIC) == republic,
					msg("the exchange takes two shards and gives no reputation"));

			Identity.setReputation(player, Faction.SITH_EMPIRE, -200);
			helper.assertTrue(HolocronReturns.amends(player, HolocronReturns.IMPERIAL), msg("amends at a Wary officer"));
			helper.assertTrue(Identity.reputation(player, Faction.SITH_EMPIRE) == -200 + HolocronBalanceView.AMENDS_REP && HolocronRewards.shards(player) == 1,
					msg("two shards for +40 standing"));
			helper.assertTrue(!HolocronReturns.amends(player, HolocronReturns.IMPERIAL) && HolocronRewards.shards(player) == 1,
					msg("too few shards: nothing happens"));

			player.getInventory().add(new ItemStack(M3Items.HOLOCRON_SHARD, 4));
			Identity.setReputation(player, Faction.REPUBLIC, 0);
			helper.assertTrue(!HolocronReturns.amends(player, HolocronReturns.REPUBLIC) && HolocronRewards.shards(player) == 5,
					msg("no amends where the army is not Wary"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the Archive

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void archiveChoiceOnceStoredOnlyInHolocronData(GameTestHelper helper) {
		ServerPlayer player = onPath(M3TestPlayers.listed(helper), Path.JEDI);

		try {
			HolocronService.markArchivePending(player);
			StoryData storyBefore = Story.get(player);
			StoryData.Chapter3 chapterBefore = storyBefore.chapter3;
			Set<String> flagsBefore = Set.copyOf(storyBefore.flags);
			Set<String> countersBefore = Set.copyOf(storyBefore.counters.keySet());
			HolocronService.resolveArchive(player, ArchiveChoice.KEEP);
			HolocronData d = HolocronService.view(player);
			helper.assertTrue(d.archiveChoice() == ArchiveChoice.KEEP && !d.archivePending, msg("the choice is stored in HolocronData"));

			// ...and only there (12.1): the story attachment never records it.
			StoryData storyAfter = Story.get(player);
			helper.assertTrue(storyAfter.chapter3.equals(chapterBefore), msg("the story's Chapter Three record (outcome, stance) is untouched"));
			List<String> added = new ArrayList<>(storyAfter.flags);
			added.removeAll(flagsBefore);
			storyAfter.counters.keySet().stream().filter(key -> !countersBefore.contains(key)).forEach(added::add);

			for (String key : added) {
				helper.assertTrue(!namesArchiveChoice(key), msg("the story data never records the Archive choice, found " + key));
			}

			for (HolocronCatalog.Entry entry : HolocronCatalog.archive()) {
				helper.assertTrue(copies(player, entry.id()) == 1 && d.isAttuned(entry.id()) && d.serial(entry.id()) == 1,
						msg("Keep gives the owner-bound " + entry.id()));
			}

			helper.assertTrue(Skills.rank(player, SkillIds.ARCHIVISTS_WARD) > 0 && Skills.rank(player, SkillIds.ARCHIVISTS_HUNGER) > 0
					&& Skills.rank(player, SkillIds.ARCHIVISTS_ROAD) > 0, msg("Keep grants the three Archivist nodes"));
			helper.assertTrue(Progression.get(player).hasFlag(JournalEntries.entryFlag(JournalEntries.ARCHIVE_KEPT)), msg("the journal entry"));
			helper.assertTrue(Identity.hasChoice(player, "archive.keep"), msg("the choice id is recorded"));
			helper.assertTrue(HolocronCarry.refresh(player) == 2, msg("a Jedi carries the Jedi and Wayfarer Archive holocrons for the bonus"));
			helper.assertTrue(HolocronService.attunedCount(player, null) == 0, msg("Archive holocrons never count toward the collection"));

			HolocronService.resolveArchive(player, ArchiveChoice.UNMAKE);
			helper.assertTrue(HolocronService.view(player).archiveChoice() == ArchiveChoice.KEEP
					&& !Progression.get(player).hasFlag("resonance:archive_unmade"), msg("the choice is made once"));

			player.getInventory().clearContent();
			PedestalSite site = pedestal(helper, PEDESTAL);
			helper.assertTrue(use(helper, player, site, HolocronKind.JEDI, true, 0) == Outcome.RESTORED && copies(player, HolocronCatalog.ARCHIVE_WATCH) == 1,
					msg("a kept Archive holocron can be restored at a pedestal of its kind"));

			JsonElement json = HolocronData.CODEC.encodeStart(JsonOps.INSTANCE, HolocronService.view(player)).getOrThrow();
			helper.assertTrue(json.getAsJsonObject().get("archive").getAsInt() == ArchiveChoice.KEEP.ordinal(), msg("saved under \"archive\": " + json));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 40)
	public void archivePendingForHelpers(GameTestHelper helper) {
		ServerPlayer helperPlayer = onPath(M3TestPlayers.listed(helper), Path.INDEPENDENT);
		BlockPos plinth = helper.absolutePos(PEDESTAL);

		try {
			HolocronService.openArchive(helperPlayer, plinth);
			helper.assertTrue(Dialogues.session(helperPlayer) == null, msg("without pending credit the hoard stays shut"));

			HolocronService.markArchivePending(helperPlayer);
			helper.assertTrue(HolocronService.view(helperPlayer).archivePending, msg("the helper owes a choice"));
			HolocronService.openArchive(helperPlayer, plinth);
			DialogueSession session = Dialogues.session(helperPlayer);
			helper.assertTrue(session != null && session.dialogue().equals(HolocronArchive.DIALOGUE), msg("the hoard opens for a pending helper"));
			Dialogues.close(helperPlayer);

			int frontier = Identity.reputation(helperPlayer, Faction.FRONTIER);
			HolocronService.resolveArchive(helperPlayer, ArchiveChoice.RETURN);
			HolocronData d = HolocronService.view(helperPlayer);
			helper.assertTrue(d.archiveChoice() == ArchiveChoice.RETURN && !d.archivePending, msg("the helper made their own choice"));
			helper.assertTrue(Identity.reputation(helperPlayer, Faction.FRONTIER) - frontier == HolocronBalanceView.ARCHIVE_FRONTIER_REP,
					msg("an Independent scatters it back: Frontier +100"));
			helper.assertTrue(copies(helperPlayer, HolocronCatalog.ARCHIVE_UNBOUND) == 0, msg("Return gives no items"));

			HolocronService.markArchivePending(helperPlayer);
			helper.assertTrue(!HolocronService.view(helperPlayer).archivePending, msg("a decided player is never pending again"));
			HolocronService.openArchive(helperPlayer, plinth);
			helper.assertTrue(Dialogues.session(helperPlayer) == null, msg("the hoard does not open twice"));
		} finally {
			M3TestPlayers.remove(helperPlayer);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- saved formats

	@GameTest(maxTicks = 20)
	public void holocronDataAndStampCodecsAndCopyOnDeath(GameTestHelper helper) {
		UUID owner = new UUID(0L, 3L);
		HolocronData data = new HolocronData();
		data.attuned.add(HolocronCatalog.WAYFARERS_COMPASS);
		data.choices.put(HolocronCatalog.WAYFARERS_COMPASS, HolocronChoice.OWN.ordinal());
		data.returned.add(HolocronCatalog.WAYFARERS_COMPASS);
		data.attunedAt.add(new HolocronData.AttunedAt("minecraft:overworld", new BlockPos(10, 64, -5), "minecraft:overworld|chunk|0|-1",
				HolocronCatalog.WAYFARERS_COMPASS));
		data.serials.put(HolocronCatalog.WAYFARERS_COMPASS, 3);
		data.milestones = HolocronData.BIT_3 | 1 << HolocronData.DRIP_SHIFT;
		data.archive = ArchiveChoice.UNMAKE.ordinal();
		data.archivePending = true;
		data.restoreDay = 12L;

		for (int i = 0; i < HolocronData.MAX_ECHOES + 6; i++) {
			data.echoes.add(new HolocronData.PedestalRef("minecraft:overworld", new BlockPos(i, 70, i)));
		}

		JsonElement json = HolocronData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		JsonObject object = json.getAsJsonObject();

		for (String key : List.of("attuned", "choices", "returned", "attuned_at", "serials", "echoes", "milestones", "archive", "archive_pending", "restore_day")) {
			helper.assertTrue(object.has(key), msg("saved key " + key + ": " + json));
		}

		HolocronData back = HolocronData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.attuned.equals(data.attuned) && back.choices.equals(data.choices) && back.returned.equals(data.returned),
				msg("ids survive"));
		helper.assertTrue(back.attunedAt.equals(data.attunedAt) && back.serials.equals(data.serials), msg("pedestals and serials survive"));
		helper.assertTrue(back.echoes.size() == HolocronData.MAX_ECHOES && back.echoes.getLast().equals(data.echoes.getLast()),
				msg("echoes are capped at 64, keeping the newest"));
		helper.assertTrue(back.milestones == data.milestones && back.archive == data.archive && back.archivePending && back.restoreDay == 12L,
				msg("the mask, the Archive and the restore day survive"));

		HolocronData empty = HolocronData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		helper.assertTrue(empty.attuned.isEmpty() && empty.archive == HolocronData.NO_ARCHIVE && empty.restoreDay == HolocronData.NEVER
				&& !empty.archivePending, msg("an old save without the attachment's fields loads as new"));

		HolocronStamp stamp = new HolocronStamp(HolocronCatalog.WAYFARER_PELL, owner, 4);
		JsonElement stampJson = HolocronStamp.CODEC.encodeStart(JsonOps.INSTANCE, stamp).getOrThrow();
		helper.assertTrue(HolocronStamp.CODEC.parse(JsonOps.INSTANCE, stampJson).getOrThrow().equals(stamp), msg("the stamp survives its codec"));
		ByteBuf buf = Unpooled.buffer();

		try {
			HolocronStamp.STREAM_CODEC.encode(buf, stamp);
			helper.assertTrue(HolocronStamp.STREAM_CODEC.decode(buf).equals(stamp), msg("the stamp survives the stream codec"));
		} finally {
			buf.release();
		}

		ItemStack stack = HolocronItems.stack(HolocronCatalog.WAYFARER_PELL, owner, 4);
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.is(HolocronItems.WAYFARER_HOLOCRON) && stamp.equals(loaded.get(ModComponents.HOLOCRON)),
				msg("a stamped item survives saving"));
		helper.assertTrue(loaded.getMaxStackSize() == 1, msg("holocrons stack to 1"));

		helper.assertTrue(HolocronAttachments.HOLOCRONS.copyOnDeath(), msg("holocron data must be copied on death"));
		helper.assertTrue(HolocronAttachments.HOLOCRONS.isSynced(), msg("holocron data is synced to its owner"));
		HolocronData sealed = data.copy().seal();
		HolocronData view = sealed.syncView();
		sealed.attuned.add(HolocronCatalog.WAYFARER_PELL);
		helper.assertTrue(sealed.isSealed() && view != sealed && !view.attuned.contains(HolocronCatalog.WAYFARER_PELL),
				msg("sealing takes a frozen view that later changes never reach"));
		helper.assertTrue(!new HolocronData().isSealed() && new HolocronData().seal().isSealed(), msg("only sealed data is attached"));
		helper.succeed();
	}

	/** Expected values, read from the balance defaults so the tests follow the spec table (14). */
	private static final class HolocronBalanceView {
		private static final HolocronBalance DEFAULTS = new HolocronBalance();
		static final int SEAL_SHIFT = DEFAULTS.sealShift;
		static final int JEDI_LISTEN_SHIFT = DEFAULTS.jediListenShift;
		static final int BREAK_SHIFT = DEFAULTS.breakShift;
		static final int SITH_LISTEN_SHIFT = DEFAULTS.sithListenShift;
		static final int BREAK_TRUST = DEFAULTS.breakTrust;
		static final int RETURN_REP = DEFAULTS.returnRep;
		static final int AMENDS_REP = DEFAULTS.amendsRep;
		static final int ARCHIVE_FRONTIER_REP = DEFAULTS.archiveReturnFrontierRep;
	}

	/** Timing of the attunement moment, from the balance defaults: the dialogue is open a few ticks after the gatekeeper forms. */
	private static final class HolocronHelpers {
		static final int DIALOGUE_DELAY = HolocronBalanceView.DEFAULTS.gatekeeperDelayTicks + 5;
	}
}
