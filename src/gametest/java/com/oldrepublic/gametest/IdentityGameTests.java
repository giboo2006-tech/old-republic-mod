package com.oldrepublic.gametest;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueDefinition;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityAttachments;
import com.oldrepublic.identity.IdentityBalance;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.IdentityRules.Cap;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.YieldState;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.identity.Yielding;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Alignment, reputation, path and trust rules, yielding, and the dialogue engine. */
public class IdentityGameTests {
	private static final Identifier TEST_DIALOGUE = Identifier.fromNamespaceAndPath("oldrepublic_test", "conditions");
	private static final String REQ = "dialogue.oldrepublic.common.req.";

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static Integer shift(IdentityData data, int amount, Cap cap, int day, IdentityBalance b) {
		return IdentityRules.shift(data, cap == Cap.MAJOR ? "test." + UUID.randomUUID() : "", amount, cap, "test", day, b);
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "identity_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(at)));
		return player;
	}

	// ---------------------------------------------------------------- alignment

	@GameTest
	public void bandEdges(GameTestHelper helper) {
		IdentityBalance b = new IdentityBalance();
		int[] values = {100, 50, 49, 15, 14, 0, -14, -15, -49, -50, -100};
		AlignmentBand[] expected = {AlignmentBand.LIGHT, AlignmentBand.LIGHT, AlignmentBand.LEANING_LIGHT, AlignmentBand.LEANING_LIGHT,
				AlignmentBand.BALANCED, AlignmentBand.BALANCED, AlignmentBand.BALANCED, AlignmentBand.LEANING_DARK, AlignmentBand.LEANING_DARK,
				AlignmentBand.DARK, AlignmentBand.DARK};

		for (int i = 0; i < values.length; i++) {
			helper.assertTrue(IdentityRules.band(values[i], b) == expected[i], msg(values[i] + " must be " + expected[i]));
			helper.assertTrue(AlignmentBand.of(values[i]) == expected[i], msg("default edges: " + values[i] + " must be " + expected[i]));
		}

		helper.succeed();
	}

	@GameTest
	public void majorShiftsApplyOnceAndClamp(GameTestHelper helper) {
		IdentityBalance b = new IdentityBalance();
		IdentityData data = new IdentityData();

		helper.assertTrue(Integer.valueOf(12).equals(IdentityRules.shift(data, "reckoning.captain", 12, Cap.MAJOR, "r", 0, b)), msg("first spare applies +12"));
		helper.assertTrue(IdentityRules.shift(data, "reckoning.captain", -12, Cap.MAJOR, "r", 0, b) == null, msg("the same source never applies twice"));
		helper.assertTrue(data.alignment == 12, msg("alignment stays 12"));

		data.alignment = 80;
		helper.assertTrue(Integer.valueOf(20).equals(shift(data, 50, Cap.MAJOR, 0, b)), msg("a major shift clamps at +100"));
		helper.assertTrue(data.alignment == IdentityRules.ALIGNMENT_LIMIT, msg("alignment is 100"));
		data.alignment = -95;
		shift(data, -30, Cap.MAJOR, 0, b);
		helper.assertTrue(data.alignment == -IdentityRules.ALIGNMENT_LIMIT, msg("alignment clamps at -100"));
		helper.assertTrue(data.alignmentLog.size() <= IdentityData.LOG_SIZE, msg("the log keeps at most 8 entries"));
		helper.succeed();
	}

	@GameTest
	public void minorCapsPerDirectionAndLimit(GameTestHelper helper) {
		IdentityBalance b = new IdentityBalance();
		IdentityData data = new IdentityData();

		for (int i = 0; i < b.minorDailyCap; i++) {
			helper.assertTrue(Integer.valueOf(1).equals(shift(data, 1, Cap.MINOR, 3, b)), msg("minor +1 number " + (i + 1) + " applies"));
		}

		helper.assertTrue(shift(data, 1, Cap.MINOR, 3, b) == null, msg("the light cap of " + b.minorDailyCap + " per day holds"));
		helper.assertTrue(Integer.valueOf(-2).equals(shift(data, -2, Cap.MINOR, 3, b)), msg("the dark direction has its own cap"));
		helper.assertTrue(Integer.valueOf(1).equals(shift(data, 1, Cap.MINOR, 4, b)), msg("the next day allows light again"));

		data.alignment = b.minorLimit - 1;
		helper.assertTrue(Integer.valueOf(1).equals(shift(data, 5, Cap.MINOR, 9, b)), msg("a minor shift stops at the +60 limit"));
		helper.assertTrue(shift(data, 1, Cap.MINOR, 9, b) == null, msg("nothing past +60"));
		helper.assertTrue(Integer.valueOf(-1).equals(shift(data, -1, Cap.MINOR, 9, b)), msg("moving back toward the centre is allowed"));

		data.alignment = 75;
		helper.assertTrue(shift(data, 1, Cap.MINOR, 10, b) == null, msg("minor shifts never push further past +60"));
		helper.succeed();
	}

	@GameTest
	public void severeCapAndPastLimit(GameTestHelper helper) {
		IdentityBalance b = new IdentityBalance();
		IdentityData data = new IdentityData();
		data.alignment = -55;

		helper.assertTrue(Integer.valueOf(-8).equals(shift(data, -8, Cap.SEVERE, 1, b)), msg("a settler killing applies -8"));
		helper.assertTrue(data.alignment == -63, msg("severe shifts go past -60"));
		shift(data, -8, Cap.SEVERE, 1, b);
		shift(data, -8, Cap.SEVERE, 1, b);
		helper.assertTrue(shift(data, -8, Cap.SEVERE, 1, b) == null, msg("the severe cap of -24 per day holds"));
		helper.assertTrue(data.alignment == -55 - b.severeDailyCap, msg("exactly the daily severe cap applied"));
		helper.assertTrue(Integer.valueOf(-4).equals(shift(data, -4, Cap.SEVERE, 2, b)), msg("the next day allows more"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- reputation, path and trust

	@GameTest
	public void reputationTiersAndClamp(GameTestHelper helper) {
		IdentityBalance b = new IdentityBalance();
		int[] values = {1000, 500, 499, 100, 99, 0, -99, -100, -499, -500, -1000};
		ReputationTier[] expected = {ReputationTier.HONOURED, ReputationTier.HONOURED, ReputationTier.TRUSTED, ReputationTier.TRUSTED,
				ReputationTier.NEUTRAL, ReputationTier.NEUTRAL, ReputationTier.NEUTRAL, ReputationTier.DISTRUSTED, ReputationTier.DISTRUSTED,
				ReputationTier.HOSTILE, ReputationTier.HOSTILE};

		for (int i = 0; i < values.length; i++) {
			helper.assertTrue(IdentityRules.tier(values[i], b) == expected[i], msg(values[i] + " must be " + expected[i]));
		}

		IdentityData data = new IdentityData();
		helper.assertTrue(Integer.valueOf(150).equals(IdentityRules.changeReputation(data, Faction.FRONTIER, 150, "captives", true)), msg("first change applies"));
		helper.assertTrue(IdentityRules.changeReputation(data, Faction.FRONTIER, 150, "captives", true) == null, msg("a one-shot source applies once"));
		IdentityRules.changeReputation(data, Faction.FRONTIER, 5000, "x", false);
		helper.assertTrue(data.reputation(Faction.FRONTIER) == IdentityRules.REPUTATION_LIMIT, msg("reputation clamps at +1000"));
		IdentityRules.setReputation(data, Faction.SITH_EMPIRE, -4000);
		helper.assertTrue(data.reputation(Faction.SITH_EMPIRE) == -IdentityRules.REPUTATION_LIMIT, msg("reputation clamps at -1000"));
		helper.succeed();
	}

	@GameTest
	public void pathChangeRules(GameTestHelper helper) {
		IdentityData data = new IdentityData();
		helper.assertTrue(IdentityRules.pathChangeAllowed(data, Path.JEDI, false), msg("NONE may become a Jedi"));
		IdentityRules.applyPath(data, Path.JEDI);
		helper.assertTrue(data.rank == 1, msg("joining starts at rank 1"));
		helper.assertTrue(!IdentityRules.pathChangeAllowed(data, Path.SITH, false), msg("JEDI to SITH is refused"));
		helper.assertTrue(!IdentityRules.pathChangeAllowed(data, Path.INDEPENDENT, false), msg("JEDI to INDEPENDENT is refused"));
		helper.assertTrue(IdentityRules.pathChangeAllowed(data, Path.SITH, true), msg("the operator can force JEDI to SITH"));
		helper.assertTrue(!IdentityRules.pathChangeAllowed(data, Path.JEDI, true), msg("the same path is never a change"));

		IdentityData wanderer = new IdentityData();
		IdentityRules.applyPath(wanderer, Path.INDEPENDENT);
		helper.assertTrue(wanderer.rank == 0, msg("Independents have rank 0"));
		helper.assertTrue(IdentityRules.pathChangeAllowed(wanderer, Path.SITH, false), msg("an Independent may join later"));
		IdentityRules.applyPath(wanderer, Path.SITH);
		wanderer.path = Path.INDEPENDENT;
		helper.assertTrue(!IdentityRules.pathChangeAllowed(wanderer, Path.JEDI, false), msg("leaving Independent is allowed only once"));
		helper.succeed();
	}

	@GameTest
	public void trustClamps(GameTestHelper helper) {
		IdentityData data = new IdentityData();
		helper.assertTrue(IdentityRules.addTrust(data, "jedi", 150) == IdentityRules.TRUST_MAX, msg("trust stops at 100"));
		helper.assertTrue(IdentityRules.addTrust(data, "jedi", -300) == -IdentityRules.TRUST_MAX, msg("trust stops at 0"));
		helper.assertTrue(data.trust("jedi") == 0 && data.trust("sith") == 0, msg("trust is per mentor"));
		helper.succeed();
	}

	@GameTest
	public void codecRoundTripAndCopyOnDeath(GameTestHelper helper) {
		IdentityData data = new IdentityData();
		IdentityRules.shift(data, "reckoning.captives", 10, Cap.MAJOR, "reason.free", 2, new IdentityBalance());
		IdentityRules.changeReputation(data, Faction.JEDI_ORDER, 150, "join.jedi", true);
		IdentityRules.applyPath(data, Path.JEDI);
		IdentityRules.addTrust(data, "jedi", 35);

		JsonElement json = IdentityData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		IdentityData back = IdentityData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.alignment == 10 && back.path == Path.JEDI && back.rank == 1, msg("alignment, path and rank survive: " + json));
		helper.assertTrue(back.reputation(Faction.JEDI_ORDER) == 150 && back.trust("jedi") == 35, msg("reputation and trust survive"));
		helper.assertTrue(back.choices.equals(data.choices) && back.alignmentLog.equals(data.alignmentLog), msg("choices and log survive"));
		helper.assertTrue(back.capDay == data.capDay, msg("the cap day survives"));
		helper.assertTrue(IdentityAttachments.IDENTITY.copyOnDeath(), msg("identity must be copied on death"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- yielding

	/** A Yieldable stand-in for any living entity, so the clamp can be tested without W5's NPCs. */
	private static final class StubYieldable implements Yieldable {
		boolean yielded;

		@Override
		public boolean canYield() {
			return true;
		}

		@Override
		public float yieldHealthFraction() {
			return 0.25f;
		}

		@Override
		public @Nullable UUID yieldDecider() {
			return null;
		}

		@Override
		public boolean isYielded() {
			return yielded;
		}

		@Override
		public void setYielded(boolean yielded) {
			this.yielded = yielded;
		}

		@Override
		public void onYieldResolved(YieldOutcome outcome) {
			yielded = false;
		}
	}

	@GameTest
	public void yieldClampsAtThreshold(GameTestHelper helper) {
		LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		ServerPlayer attacker = player(helper, new BlockPos(3, 1, 2));
		StubYieldable stub = new StubYieldable();
		float threshold = zombie.getMaxHealth() * stub.yieldHealthFraction();

		helper.assertTrue(!Yielding.tryYield(zombie, stub, attacker, 2f), msg("a hit that stays above the threshold must not yield"));
		zombie.setHealth(threshold + 3f);
		helper.assertTrue(Yielding.tryYield(zombie, stub, attacker, 6f), msg("a hit past the threshold yields"));
		helper.assertTrue(Math.abs(zombie.getHealth() - threshold) < 1.0e-4, msg("health ends exactly at the threshold, got " + zombie.getHealth()));
		helper.assertTrue(stub.yielded && Yielding.isPending(zombie), msg("it is yielded and waiting for a decision"));

		Yielding.resolve(zombie, YieldOutcome.FLED, null);
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void executeNeedsTwoStrikesInWindow(GameTestHelper helper) {
		UUID decider = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		YieldState state = new YieldState(UUID.randomUUID(), Level.OVERWORLD, 0);
		int window = new IdentityBalance().executeConfirmTicks;

		helper.assertTrue(!state.strike(decider, 100, window), msg("the first strike only warns"));
		helper.assertTrue(state.strike(decider, 100 + window, window), msg("a second strike inside the window executes"));
		helper.assertTrue(!state.strike(decider, 500, window), msg("after an execution confirm, a strike starts over"));
		helper.assertTrue(!state.strike(decider, 501 + window, window), msg("a second strike after the window only warns again"));
		helper.assertTrue(!state.strike(other, 502 + window, window), msg("another player's strike does not confirm"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- dialogue

	@GameTest
	public void dialogueConditionsAndReasons(GameTestHelper helper) {
		ServerPlayer p = player(helper, new BlockPos(2, 1, 2));
		Identity.commit(p, new IdentityData());

		helper.assertTrue(Dialogues.failReason(Map.of(), p, null) == null, msg("no conditions always pass"));
		helper.assertTrue((REQ + "alignment_min|10").equals(Dialogues.failReason(Map.of("alignment_min", "10"), p, null)), msg("alignment_min reason"));
		helper.assertTrue(Dialogues.failReason(Map.of("alignment_max", "0"), p, null) == null, msg("alignment 0 meets alignment_max 0"));
		helper.assertTrue((REQ + "path|@path.oldrepublic.jedi").equals(Dialogues.failReason(Map.of("path", "jedi"), p, null)), msg("path reason"));
		helper.assertTrue(Dialogues.failReason(Map.of("path", "[\"none\",\"independent\"]"), p, null) == null, msg("a path list matches NONE"));

		String rep = "{\"faction\":\"jedi_order\",\"value\":\"trusted\"}";
		helper.assertTrue((REQ + "reputation|@faction.oldrepublic.tier.trusted|@faction.oldrepublic.jedi_order").equals(Dialogues.failReason(Map.of("reputation_min", rep), p, null)),
				msg("reputation reason names the tier and faction"));
		Identity.setReputation(p, Faction.JEDI_ORDER, 100);
		helper.assertTrue(Dialogues.failReason(Map.of("reputation_min", rep), p, null) == null, msg("Trusted meets reputation_min trusted"));

		String trust = "{\"mentor\":\"jedi\",\"value\":30}";
		helper.assertTrue((REQ + "trust|@screen.oldrepublic.standing.mentor.jedi|30").equals(Dialogues.failReason(Map.of("trust_min", trust), p, null)), msg("trust reason"));
		helper.assertTrue((REQ + "flag").equals(Dialogues.failReason(Map.of("flag", "test:never"), p, null)), msg("a missing flag fails"));
		helper.assertTrue(Dialogues.failReason(Map.of("flag", "!test:never"), p, null) == null, msg("a negated missing flag passes"));
		helper.assertTrue((REQ + "invalid").equals(Dialogues.failReason(Map.of("no_such_condition", "1"), p, null)), msg("unknown conditions fail safely"));
		helper.assertTrue((REQ + "invalid").equals(Dialogues.failReason(Map.of("rank_min", "lots"), p, null)), msg("malformed arguments fail safely"));
		helper.succeed();
	}

	@GameTest
	public void dialogueDefinitionValidation(GameTestHelper helper) {
		Identifier id = OldRepublicMod.id("test_inline");
		String good = "{\"start\":\"a\",\"nodes\":{\"a\":{\"line\":\"l\",\"options\":[{\"text\":\"t\",\"next\":\"a\"},{\"text\":\"bye\"}]}}}";
		DialogueDefinition definition = DialogueDefinition.parse(id, JsonParser.parseString(good));
		helper.assertTrue(definition.nodes().get("a").options().get(1).close(), msg("an option without next closes"));

		String[] bad = {
			"{\"start\":\"a\",\"nodes\":{\"a\":{\"line\":\"l\",\"options\":[{\"text\":\"t\",\"next\":\"missing\"}]}}}",
			"{\"start\":\"b\",\"nodes\":{\"a\":{\"line\":\"l\",\"options\":[{\"text\":\"t\"}]}}}",
			"{\"start\":\"a\",\"nodes\":{\"a\":{\"line\":\"l\",\"options\":[]}}}",
			"{\"start\":[{\"if\":{\"path\":\"jedi\"},\"node\":\"a\"}],\"nodes\":{\"a\":{\"line\":\"l\",\"options\":[{\"text\":\"t\"}]}}}",
		};

		for (String json : bad) {
			boolean refused = false;

			try {
				DialogueDefinition.parse(id, JsonParser.parseString(json));
			} catch (JsonParseException e) {
				refused = true;
			}

			helper.assertTrue(refused, msg("an invalid dialogue must be refused: " + json));
		}

		helper.succeed();
	}

	@GameTest
	public void dialogueSessionRejectsStaleAndFar(GameTestHelper helper) {
		ServerPlayer p = player(helper, new BlockPos(2, 1, 2));
		Identity.commit(p, new IdentityData());
		Entity speaker = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 2));

		try {
			helper.assertTrue(Dialogues.open(p, TEST_DIALOGUE, speaker), msg("the test dialogue opens (is oldrepublic_test:conditions loaded?)"));
			DialogueSession session = Dialogues.session(p);
			helper.assertTrue(session != null && "greet".equals(session.node()), msg("it starts at greet"));
			int id = session.id();

			helper.assertTrue(Dialogues.choose(p, id + 1, 0) == Dialogues.ChoiceResult.STALE_SESSION, msg("a stale session id is rejected"));
			helper.assertTrue(Dialogues.choose(p, id, 9) == Dialogues.ChoiceResult.INVALID_OPTION, msg("an unknown option is rejected"));
			helper.assertTrue(Dialogues.choose(p, id, 1) == Dialogues.ChoiceResult.UNMET, msg("a disabled option cannot be picked"));

			speaker.setPos(speaker.getX() + 10, speaker.getY(), speaker.getZ());
			helper.assertTrue(Dialogues.choose(p, id, 0) == Dialogues.ChoiceResult.OUT_OF_RANGE, msg("a speaker over 6 blocks away is rejected"));
			speaker.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));

			helper.assertTrue(Dialogues.choose(p, id, 0) == Dialogues.ChoiceResult.ACCEPTED, msg("an enabled option in range is accepted"));
			helper.assertTrue("again".equals(Dialogues.session(p).node()), msg("it moves to the next node"));
			Dialogues.close(p);
			helper.assertTrue(Dialogues.choose(p, id, 0) == Dialogues.ChoiceResult.NO_SESSION, msg("a closed session accepts nothing"));
			helper.succeed();
		} finally {
			Dialogues.close(p);
			speaker.discard();
		}
	}
}
