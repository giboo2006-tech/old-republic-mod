package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.combat.DangerTellTable;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealBalance;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.lightning.LightningShape;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.force.pull.PullShape;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.LocatorQueue;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 Phase 0 contracts (spec 17.1), green before the workers start: allegiances and
 * attitude rules, kill XP, damage and Force modifiers, holds and ability locks, the combat events and
 * the switched Force call sites (interrupt causes, control kinds, area shields, Heal mastery),
 * Force-reactive blocks, Force Sense and Guidance registries, promotion, the Danger Sense tell
 * table, site anchors under every rotation, the runtime site search and terrain adaptation, the
 * locator queue (with its own fake lookup, never the server's queue), detached {@code WorldSites}
 * values, the outpost decision and the bolt styles.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}; fixtures are the small
 * {@code oldrepublic_test} ones; no test writes {@code WorldSites} or the server's locator queue.
 * The global registries have no unregister, so the tests register inert entries once: modifiers,
 * Attitudes rules, a duration modifier, Force reaction handlers, combat event listeners, a Sense
 * provider, Guidance providers and a handler for the stand-in block {@code minecraft:target}. Each
 * answers only for an entity or player carrying an {@code oldrepublic_test.*} tag, for a test
 * ability id or for the test act, so it changes nothing else; what the hooks log is kept per entity
 * and removed by the test that made it.
 */
public class ContractsM3GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final Identifier ANCHOR_FIXTURE = Identifier.fromNamespaceAndPath("oldrepublic_test", "anchor_fixture");

	private static final String TAG_DAMAGE_X10 = "oldrepublic_test.damage_x10";
	private static final String TAG_DAMAGE_X0_1 = "oldrepublic_test.damage_x0_1";
	private static final String TAG_DAMAGE_X1_5 = "oldrepublic_test.damage_x1_5";
	private static final String TAG_REGEN_LOW = "oldrepublic_test.regen_low";
	private static final String TAG_REGEN_HIGH = "oldrepublic_test.regen_high";
	private static final String ABILITY_CHEAP = "oldrepublic_test:cheap";
	private static final String ABILITY_DEAR = "oldrepublic_test:dear";
	private static final String ABILITY_PLAIN = "oldrepublic_test:plain";

	/** Attitudes test rules: the first registered rule answers ALLIED for this tag only. */
	private static final String TAG_FIRST_RULE_ALLIED = "oldrepublic_test.first_rule_allied";
	/** The second test rule: a reputation tier, engagement or provocation for a tagged fixture. */
	private static final String TAG_RULE_HOSTILE = "oldrepublic_test.rule_hostile";
	private static final String TAG_RULE_WARY = "oldrepublic_test.rule_wary";
	private static final String TAG_RULE_NEUTRAL = "oldrepublic_test.rule_neutral";
	private static final String TAG_RULE_ALLIED = "oldrepublic_test.rule_allied";
	private static final String TAG_ENGAGED = "oldrepublic_test.engaged";
	private static final String TAG_PROVOKED = "oldrepublic_test.provoked";
	/** Fixtures whose control kinds, interrupts or combat events the test hooks log. */
	private static final String TAG_DURATION_LOG = "oldrepublic_test.duration_log";
	private static final String TAG_INTERRUPT_LOG = "oldrepublic_test.interrupt_log";
	private static final String TAG_COMBAT_EVENTS = "oldrepublic_test.combat_events";
	/** Players the test Sense provider and the test Guidance providers answer for. */
	private static final String TAG_SENSE_FLOOD = "oldrepublic_test.sense_flood";
	private static final String TAG_GUIDANCE_TIE = "oldrepublic_test.guidance_tie";
	private static final String TAG_GUIDANCE_HIGHER = "oldrepublic_test.guidance_higher";
	/** The only act the test block handler consumes. */
	private static final String ABILITY_BLOCK = "oldrepublic_test:block";
	/**
	 * The Force-reactive stand-in. Only the game-test datapack lists it in
	 * {@code #oldrepublic:force_reactive}; no fixture or worker uses it.
	 */
	private static final Block TEST_BLOCK = Blocks.TARGET;

	/** The test duration modifier's multiplier on a logged fixture. */
	private static final float DURATION_TEST_MULT = 0.5f;
	/** The frozen control kinds (15.1). */
	private static final List<String> CONTROL_KINDS = List.of("stagger", "slow", "disarm", "shock", "hold");
	/**
	 * The seed of the acolyte's own random before its parry rolls, so the NPC parry is reproducible
	 * (with the legacy generator the third try parries). At the acolyte's 35% parry chance, 32 tries
	 * that all miss would be a one-in-a-million seed.
	 */
	private static final long NPC_PARRY_SEED = 3L;
	private static final int NPC_PARRY_TRIES = 32;
	/** The healer's health when a test Force Heal channel starts. */
	private static final float HEAL_START_HEALTH = 6f;
	/** Force Sense's point cap (10.1, {@code force_sense.maxPoints}). */
	private static final int SENSE_CAP = 32;
	/** Points the test Sense provider offers, {@link #SENSE_STEP} apart along +z from the eyes. */
	private static final int SENSE_FLOOD = 40;
	private static final double SENSE_STEP = 0.01;
	/** Above every real provider (STORY is 100), so only the test providers compete. */
	private static final int GUIDANCE_TIE_PRIORITY = Integer.MAX_VALUE - 1;
	private static final String GUIDANCE_FIRST = "oldrepublic_test:first";
	private static final String GUIDANCE_SECOND = "oldrepublic_test:second";
	private static final String GUIDANCE_HIGHER = "oldrepublic_test:higher";

	/** What the test hooks saw, per entity UUID. Each test removes its own entries ({@link #cleanUp}). */
	private static final Map<UUID, List<String>> DURATION_KINDS = new HashMap<>();
	private static final Map<UUID, List<String>> INTERRUPTS = new HashMap<>();
	private static final Map<UUID, List<String>> COMBAT_EVENTS = new HashMap<>();

	private static boolean testModifiersRegistered;
	private static boolean contractHooksRegistered;
	private static boolean testBlockHandlerRegistered;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-4;
	}

	/** The inert test modifiers (see the class comment), registered on first use. */
	private static void registerTestModifiers() {
		if (testModifiersRegistered) {
			return;
		}

		testModifiersRegistered = true;
		DamageModifiers.register((victim, source, amount) -> {
			if (victim.entityTags().contains(TAG_DAMAGE_X10)) {
				return amount * 10f;
			} else if (victim.entityTags().contains(TAG_DAMAGE_X0_1)) {
				return amount * 0.1f;
			} else if (victim.entityTags().contains(TAG_DAMAGE_X1_5)) {
				return amount * 1.5f;
			}

			return amount;
		});
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float costMultiplier(Player p, String abilityId) {
				return ABILITY_CHEAP.equals(abilityId) ? 0.1f : ABILITY_DEAR.equals(abilityId) ? 10f : 1f;
			}

			@Override
			public float regenMultiplier(Player p) {
				return p.entityTags().contains(TAG_REGEN_LOW) ? 0.1f : p.entityTags().contains(TAG_REGEN_HIGH) ? 10f : 1f;
			}
		});
	}

	/**
	 * The inert contract hooks (see the class comment), registered once and in this order: two
	 * Attitudes rules, a control duration modifier, two Force reaction handlers (one written against
	 * the interrupt causes, one against the M2 one-argument form), listeners on the three combat
	 * events, a Force Sense provider and two Guidance providers.
	 */
	private static void registerContractHooks() {
		if (contractHooksRegistered) {
			return;
		}

		contractHooksRegistered = true;
		// Asked first: an opinion only about TAG_FIRST_RULE_ALLIED.
		Attitudes.register((self, p) -> self.entityTags().contains(TAG_FIRST_RULE_ALLIED) ? Attitude.ALLIED : null);
		Attitudes.register(new Attitudes.Rule() {
			@Override
			public @Nullable Attitude toward(LivingEntity self, Player p) {
				Set<String> tags = self.entityTags();
				return tags.contains(TAG_RULE_HOSTILE) ? Attitude.HOSTILE
						: tags.contains(TAG_RULE_WARY) ? Attitude.WARY
						: tags.contains(TAG_RULE_NEUTRAL) ? Attitude.NEUTRAL
						: tags.contains(TAG_RULE_ALLIED) ? Attitude.ALLIED
						: null;
			}

			@Override
			public boolean engaged(LivingEntity self, Player p) {
				return self.entityTags().contains(TAG_ENGAGED);
			}

			@Override
			public boolean provokedBy(LivingEntity self, Player p) {
				return self.entityTags().contains(TAG_PROVOKED);
			}
		});
		ControlEffects.registerDurationModifier((target, kind) -> {
			if (!target.entityTags().contains(TAG_DURATION_LOG)) {
				return 1f;
			}

			log(DURATION_KINDS, target, kind);
			return DURATION_TEST_MULT;
		});
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
				if (target.entityTags().contains(TAG_INTERRUPT_LOG)) {
					log(INTERRUPTS, target, "new:" + cause + ":" + (caster == null ? "nobody" : caster.getUUID()));
				}
			}
		});
		// Written before the causes existed: it overrides only the M2 one-argument method.
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public void interruptWindup(LivingEntity target) {
				if (target.entityTags().contains(TAG_INTERRUPT_LOG)) {
					log(INTERRUPTS, target, "old");
				}
			}
		});
		CombatEvents.PARRY.register((defender, attacker, projectile) ->
				logCombat(defender, "parry:" + projectile + ":" + (attacker == null ? "nobody" : attacker.getUUID())));
		CombatEvents.POWER_HIT.register((caster, abilityId, target) -> logCombat(caster, "power_hit:" + abilityId + ":" + target.getUUID()));
		CombatEvents.TECHNIQUE.register((p, technique, target, hit) -> logCombat(p, "technique:" + technique));
		// SENSE_FLOOD block points within 0.4 blocks of the eyes, offered farthest first.
		SenseTargets.register((caster, blockRange, creatureRange, out) -> {
			if (caster.entityTags().contains(TAG_SENSE_FLOOD)) {
				Vec3 eye = caster.getEyePosition();

				for (int i = SENSE_FLOOD; i >= 1; i--) {
					out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, eye.add(0, 0, i * SENSE_STEP), -1, Attitude.NEUTRAL));
				}
			}
		});
		// Both ask at the same priority for TAG_GUIDANCE_TIE; the second asks one higher for TAG_GUIDANCE_HIGHER.
		Guidance.register(p -> p.entityTags().contains(TAG_GUIDANCE_TIE)
				? new Guidance.Request(GUIDANCE_TIE_PRIORITY, p.position(), 0, GUIDANCE_FIRST) : null);
		Guidance.register(p -> p.entityTags().contains(TAG_GUIDANCE_HIGHER)
				? new Guidance.Request(GUIDANCE_TIE_PRIORITY + 1, p.position(), 0, GUIDANCE_HIGHER)
				: p.entityTags().contains(TAG_GUIDANCE_TIE) ? new Guidance.Request(GUIDANCE_TIE_PRIORITY, p.position(), 0, GUIDANCE_SECOND) : null);
	}

	/** The inert test block handler, registered once: {@link #TEST_BLOCK} consumes only the act {@link #ABILITY_BLOCK}. */
	private static void registerTestBlockHandler() {
		if (testBlockHandlerRegistered) {
			return;
		}

		testBlockHandlerRegistered = true;
		ForceBlockInteractions.register(TEST_BLOCK, ctx -> ABILITY_BLOCK.equals(ctx.abilityId()));
	}

	private static void log(Map<UUID, List<String>> book, Entity entity, String entry) {
		book.computeIfAbsent(entity.getUUID(), id -> new ArrayList<>()).add(entry);
	}

	private static void logCombat(ServerPlayer player, String event) {
		if (player.entityTags().contains(TAG_COMBAT_EVENTS)) {
			log(COMBAT_EVENTS, player, event);
		}
	}

	private static List<String> logged(Map<UUID, List<String>> book, Entity entity) {
		return List.copyOf(book.getOrDefault(entity.getUUID(), List.of()));
	}

	private static List<String> kinds(Entity entity) {
		return logged(DURATION_KINDS, entity);
	}

	private static @Nullable String lastKind(Entity entity) {
		List<String> kinds = kinds(entity);
		return kinds.isEmpty() ? null : kinds.getLast();
	}

	/** Drops what the hooks logged for these entities and logs out the listed players among them. */
	private static void cleanUp(Entity... entities) {
		for (Entity entity : entities) {
			DURATION_KINDS.remove(entity.getUUID());
			INTERRUPTS.remove(entity.getUUID());
			COMBAT_EVENTS.remove(entity.getUUID());

			if (entity instanceof ServerPlayer player) {
				M3TestPlayers.remove(player);
			}
		}
	}

	/** A vanilla stand-in without AI (it never wanders or attacks), carrying the given test tags. */
	private static <E extends Mob> E fixture(GameTestHelper helper, EntityType<E> type, BlockPos relative, String... tags) {
		E mob = helper.spawnWithNoFreeWill(type, relative);

		for (String tag : tags) {
			mob.addTag(tag);
		}

		return mob;
	}

	/** {@code player} turned to face +z (south), head included: powers aim along the head's view. */
	private static ServerPlayer facingSouth(ServerPlayer player) {
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		return player;
	}

	/** A listed player at {@code relative} facing +z, awakened (Chapter One's FOCUS stage), who knows {@code abilities}. */
	private static ServerPlayer awakened(GameTestHelper helper, BlockPos relative, String... abilities) {
		ServerPlayer player = facingSouth(M3TestPlayers.listed(helper, relative));
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		Progression.commit(player, data);
		return player;
	}

	// ---------------------------------------------------------------- combat contracts

	@GameTest(maxTicks = 20)
	public void attitudeFallbackEnemyHostileElseNeutral(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
			LivingEntity villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 2));
			helper.assertTrue(zombie instanceof Enemy && !(villager instanceof Enemy), msg("the M2 rule: a zombie is an Enemy, a villager is not"));
			helper.assertTrue(Attitudes.toward(zombie, player) == Attitude.HOSTILE, msg("no rule claims a zombie: the fallback is HOSTILE"));
			helper.assertTrue(Attitudes.toward(villager, player) == Attitude.NEUTRAL, msg("no rule claims a villager: the fallback is NEUTRAL"));
			helper.assertTrue(Attitudes.countsAsHostile(player, zombie), msg("killing a zombie counts as hostile, as in M2"));
			helper.assertTrue(!Attitudes.countsAsHostile(player, villager), msg("killing a villager does not count as hostile, as in M2"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, villager) && !Attitudes.shieldedFromArea(player, zombie),
					msg("entities no rule covers are never shielded from area powers"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void damageModifiersClampQuarterToDouble(GameTestHelper helper) {
		registerTestModifiers();
		ServerLevel level = helper.getLevel();
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		ProgressionBalance b = Progression.balance();
		float plain = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(plain, 4f), msg("an untagged victim takes the damage unchanged, got " + plain));

		zombie.addTag(TAG_DAMAGE_X10);
		float high = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(high, 4f * b.damageModifierMax), msg("x10 is clamped to x2, got " + high));

		zombie.removeTag(TAG_DAMAGE_X10);
		zombie.addTag(TAG_DAMAGE_X0_1);
		float low = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(low, 4f * b.damageModifierMin), msg("x0.1 is clamped to x0.25, got " + low));

		zombie.removeTag(TAG_DAMAGE_X0_1);
		zombie.addTag(TAG_DAMAGE_X1_5);
		float inside = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(inside, 6f), msg("x1.5 lies inside the range and applies as is, got " + inside));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void forceModifiersClampCostAndRegen(GameTestHelper helper) {
		registerTestModifiers();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			ProgressionBalance b = Progression.balance();
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_PLAIN), 1f), msg("a neutral ability costs x1"));
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_CHEAP), b.modifierCostMin), msg("x0.1 cost is clamped to modifierCostMin"));
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_DEAR), b.modifierCostMax), msg("x10 cost is clamped to modifierCostMax"));
			helper.assertTrue(near(ForceModifiers.regen(player), 1f), msg("an untagged player regenerates at x1"));

			player.addTag(TAG_REGEN_LOW);
			helper.assertTrue(near(ForceModifiers.regen(player), b.modifierRegenMin), msg("x0.1 regen is clamped to modifierRegenMin"));
			player.removeTag(TAG_REGEN_LOW);
			player.addTag(TAG_REGEN_HIGH);
			helper.assertTrue(near(ForceModifiers.regen(player), b.modifierRegenMax), msg("x10 regen is clamped to modifierRegenMax"));
			player.removeTag(TAG_REGEN_HIGH);
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void abilityLocksRefuseWhileHeld(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) == null, msg("a free player may cast"));

			Holds.hold(player, 100, "test", player.position(), true);
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) != null, msg("a held player may not cast Lightning"));
			helper.assertTrue(AbilityLocks.refusal(player, ForcePush.ABILITY_ID) == null, msg("Push stays allowed when the hold allows it"));

			Holds.hold(player, 100, "test", player.position(), false);
			helper.assertTrue(AbilityLocks.refusal(player, ForcePush.ABILITY_ID) != null, msg("Push is refused when the hold forbids it"));

			Holds.release(player, "release");
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) == null && AbilityLocks.refusal(player, ForcePush.ABILITY_ID) == null,
					msg("after the release every power is allowed again"));
		} finally {
			Holds.release(player, "release");
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void holdsReleaseOnEveryPathRemovesGravityModifier(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);
		double free = player.getAttributeValue(Attributes.GRAVITY);

		// Path 1: an explicit release.
		Holds.hold(player, 200, "test", player.position(), true);
		helper.assertTrue(Holds.isHeld(player) && heldGravity(player), msg("a hold adds the gravity modifier"));
		helper.assertTrue(near(player.getAttributeValue(Attributes.GRAVITY), 0), msg("a held player has no gravity"));
		Holds.release(player, "release");
		assertFree(helper, player, free, "release");

		// Path 2: the hold runs out (the level tick sees listed players only).
		Holds.hold(player, 3, "test", player.position(), true);
		helper.runAfterDelay(8, () -> {
			assertFree(helper, player, free, "expired");

			// Path 3: logging out.
			Holds.hold(player, 200, "test", player.position(), true);
			helper.assertTrue(heldGravity(player), msg("held again before the logout"));
			M3TestPlayers.remove(player);
			assertFree(helper, player, free, "logout");
			helper.succeed();
		});
	}

	private static boolean heldGravity(ServerPlayer player) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(Holds.GRAVITY_MODIFIER);
	}

	private static void assertFree(GameTestHelper helper, ServerPlayer player, double free, String path) {
		helper.assertTrue(!Holds.isHeld(player), msg(path + ": the hold is gone"));
		helper.assertTrue(!heldGravity(player), msg(path + ": the gravity modifier is gone"));
		helper.assertTrue(near(player.getAttributeValue(Attributes.GRAVITY), free), msg(path + ": gravity is back to " + free));
	}

	@GameTest(maxTicks = 20)
	public void dangerTellTableMatches13_3(GameTestHelper helper) {
		String[] firstVariantOnly = {"npc_windup", "captain_heavy", "acolyte_shove", "tomb_vent", "sentinel_slam", "sentinel_lance",
				"boss_hurl", "boss_palm", "boss_lash", "boss_surge", "boss_grip", "boss_lunge"};
		String[] anyVariant = {"trooper_charge", "grenade_tell", "grenade_fuse"};

		for (String id : firstVariantOnly) {
			helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id(id), 0), msg(id + " variant 0 is a tell"));
			helper.assertTrue(!DangerTellTable.isTell(OldRepublicMod.id(id), 1), msg(id + " variant 1 is not a tell"));
		}

		for (String id : anyVariant) {
			for (int variant = 0; variant < 4; variant++) {
				helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id(id), variant), msg(id + " variant " + variant + " is a tell"));
			}
		}

		for (String id : new String[] {"veil_reveal", "urn_wrong", "pedestal_ready", "force_sense", "lift"}) {
			helper.assertTrue(!DangerTellTable.isTell(OldRepublicMod.id(id), 0), msg(id + " is not a tell"));
		}

		helper.assertTrue(!DangerTellTable.isTell(Identifier.fromNamespaceAndPath("minecraft", "npc_windup"), 0), msg("only oldrepublic ids are tells"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- allegiances, attitude rules and kill XP

	/** Poachers are the only {@code Aligned} entities before the workers land (CREW); vanilla mobs and players are NONE. */
	@GameTest(maxTicks = 20)
	public void allegianceFriendlySameNotNone(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			LivingEntity poacher = fixture(helper, EnemyEntities.CRYSTAL_POACHER, new BlockPos(2, 1, 3));
			LivingEntity crewmate = fixture(helper, EnemyEntities.CRYSTAL_POACHER, new BlockPos(4, 1, 3));
			LivingEntity captain = fixture(helper, EnemyEntities.POACHER_CAPTAIN, new BlockPos(6, 1, 3));
			LivingEntity zombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 5));
			LivingEntity otherZombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 5));

			helper.assertTrue(Allegiances.of(poacher) == Allegiance.CREW && Allegiances.of(captain) == Allegiance.CREW, msg("poachers and their Captain are CREW"));
			helper.assertTrue(Allegiances.friendly(poacher, crewmate) && Allegiances.friendly(poacher, captain) && Allegiances.friendly(captain, poacher),
					msg("the same allegiance is friendly, both ways"));
			helper.assertTrue(Allegiances.of(zombie) == Allegiance.NONE && Allegiances.of(player) == Allegiance.NONE,
					msg("a vanilla mob and an undisguised player are NONE"));
			helper.assertTrue(!Allegiances.friendly(zombie, otherZombie) && !Allegiances.friendly(zombie, zombie) && !Allegiances.friendly(player, zombie),
					msg("NONE is never friendly, not with another NONE and not with itself"));
			helper.assertTrue(!Allegiances.friendly(poacher, zombie) && !Allegiances.friendly(zombie, poacher) && !Allegiances.friendly(player, poacher),
					msg("different allegiances are not friendly"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void attitudeFirstNonNullRuleWins(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			LivingEntity both = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 3), TAG_FIRST_RULE_ALLIED, TAG_RULE_HOSTILE);
			LivingEntity secondOnly = fixture(helper, EntityTypes.VILLAGER, new BlockPos(4, 1, 3), TAG_RULE_HOSTILE);
			LivingEntity ruledNeutral = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 3), TAG_RULE_NEUTRAL);
			LivingEntity unruled = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 5));

			helper.assertTrue(Attitudes.toward(both, player) == Attitude.ALLIED,
					msg("two rules answer: the one registered first (ALLIED) wins over the later one (HOSTILE), got " + Attitudes.toward(both, player)));
			helper.assertTrue(Attitudes.toward(secondOnly, player) == Attitude.HOSTILE,
					msg("the first rule has no opinion (null): the next rule's HOSTILE is used, not the villager's NEUTRAL fallback"));
			helper.assertTrue(Attitudes.toward(ruledNeutral, player) == Attitude.NEUTRAL, msg("any non-null answer wins over the Enemy fallback"));
			helper.assertTrue(Attitudes.toward(unruled, player) == Attitude.HOSTILE, msg("every rule answers null: the zombie falls back to HOSTILE"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void provokedAloneNeverCountsAsHostile(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			List<LivingEntity> provoked = List.of(
					fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 3), TAG_RULE_NEUTRAL, TAG_PROVOKED),
					fixture(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 3), TAG_RULE_WARY, TAG_PROVOKED),
					fixture(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 3), TAG_RULE_ALLIED, TAG_PROVOKED),
					fixture(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 5), TAG_PROVOKED));

			for (LivingEntity entity : provoked) {
				helper.assertTrue(Attitudes.provokedBy(entity, player), msg(entity.entityTags() + ": the player provoked it"));
				helper.assertTrue(!Attitudes.countsAsHostile(player, entity), msg(entity.entityTags() + ": provocation alone never counts as hostile"));
			}

			LivingEntity engaged = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 5), TAG_RULE_NEUTRAL, TAG_ENGAGED);
			helper.assertTrue(Attitudes.countsAsHostile(player, engaged) && !Attitudes.provokedBy(engaged, player),
					msg("the contrast: engagement does count as hostile"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void shieldedFromAreaSkipsNeutralAndAllied(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			LivingEntity neutral = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 3), TAG_RULE_NEUTRAL);
			LivingEntity allied = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 3), TAG_RULE_ALLIED);
			LivingEntity hostile = fixture(helper, EntityTypes.VILLAGER, new BlockPos(6, 1, 3), TAG_RULE_HOSTILE);
			LivingEntity provoked = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 5), TAG_RULE_NEUTRAL, TAG_PROVOKED);
			LivingEntity engaged = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 5), TAG_RULE_ALLIED, TAG_ENGAGED);
			LivingEntity zombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 5));
			LivingEntity villager = fixture(helper, EntityTypes.VILLAGER, new BlockPos(2, 1, 7));

			helper.assertTrue(Attitudes.shieldedFromArea(player, neutral), msg("a NEUTRAL bystander is shielded from area powers"));
			helper.assertTrue(Attitudes.shieldedFromArea(player, allied), msg("an ALLIED bystander is shielded from area powers"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, hostile), msg("a HOSTILE one is not shielded"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, provoked), msg("a NEUTRAL one the player provoked is not shielded"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, engaged), msg("an ALLIED one engaged with the player is not shielded"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, zombie) && !Attitudes.shieldedFromArea(player, villager),
					msg("creatures no rule covers are never shielded (their M2 behaviour)"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	/** Kill XP the player has earned in total, across levels. */
	private static long totalXp(ServerPlayer player) {
		ForceData data = Progression.get(player);
		long total = data.xp;

		for (int level = 1; level < data.level; level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	/** One lethal blow from {@code player}: the damage hook takes the snapshot, then kill XP is paid or not. */
	private static void killAsPlayer(GameTestHelper helper, ServerPlayer player, LivingEntity victim) {
		ServerLevel level = helper.getLevel();
		victim.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(!victim.isAlive(), msg(victim.entityTags() + " must die to the player's blow"));
	}

	@GameTest(maxTicks = 20)
	public void alliedOrProvokedKillPaysNoXp(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = awakened(helper, new BlockPos(1, 1, 1));

		try {
			LivingEntity allied = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), TAG_RULE_ALLIED);
			LivingEntity provoked = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(5, 1, 3), TAG_RULE_NEUTRAL, TAG_PROVOKED);
			LivingEntity plain = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 5));
			long before = totalXp(player);

			killAsPlayer(helper, player, allied);
			helper.assertTrue(totalXp(player) == before, msg("killing an ALLIED zombie pays no XP, got " + (totalXp(player) - before)));
			killAsPlayer(helper, player, provoked);
			helper.assertTrue(totalXp(player) == before, msg("killing a NEUTRAL zombie the player provoked pays no XP, got " + (totalXp(player) - before)));

			killAsPlayer(helper, player, plain);
			helper.assertTrue(totalXp(player) > before, msg("the control: a zombie no rule covers does pay, so this player can earn kill XP"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void hostileKillPaysXp(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = awakened(helper, new BlockPos(1, 1, 1));

		try {
			LivingEntity hostile = fixture(helper, EntityTypes.VILLAGER, new BlockPos(3, 1, 3), TAG_RULE_HOSTILE);
			LivingEntity engaged = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(5, 1, 3), TAG_RULE_NEUTRAL, TAG_ENGAGED);
			long before = totalXp(player);

			killAsPlayer(helper, player, hostile);
			long afterHostile = totalXp(player);
			helper.assertTrue(afterHostile > before, msg("killing a villager the rule makes HOSTILE pays kill XP (without the rule it is NEUTRAL and pays nothing)"));
			killAsPlayer(helper, player, engaged);
			helper.assertTrue(totalXp(player) > afterHostile, msg("killing a NEUTRAL zombie engaged with the player pays kill XP"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- combat events and the switched Force call sites

	/**
	 * A real bolt parry (the saber guard's projectile path) fires PARRY once, as a projectile parry
	 * with the shooter, and no TECHNIQUE; a melee parry fires it once too, as melee.
	 */
	@GameTest(maxTicks = 20)
	public void boltParryFiresParryOnce(GameTestHelper helper) {
		registerContractHooks();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = facingSouth(M3TestPlayers.listed(helper, new BlockPos(2, 1, 2)));
		player.addTag(TAG_COMBAT_EVENTS);
		LivingEntity shooter = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));

		try {
			// A guard raised this tick: inside the parry window.
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
					Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
			SaberToggle.setIgnited(saber, true, level.getGameTime());
			player.setItemInHand(InteractionHand.MAIN_HAND, saber);
			player.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(!SaberGuard.guardingSaber(player).isEmpty() && player.getTicksUsingItem() == 0, msg("the player must be guarding, fresh"));

			// The zombie's bolt, arriving at the chest from straight ahead.
			BlasterBolt bolt = BlasterBolt.create(level, shooter, player.position().add(0, 1.2, 0.4), 5f, 60);
			bolt.setDeltaMovement(new Vec3(0, 0, -1.8));
			CombatHooks.DeflectResult result = CombatHooks.onProjectileHit(player, bolt);
			helper.assertTrue(result == CombatHooks.DeflectResult.PARRIED, msg("the bolt must be parried, got " + result));
			helper.assertTrue(logged(COMBAT_EVENTS, player).equals(List.of("parry:true:" + shooter.getUUID())),
					msg("a bolt parry fires PARRY exactly once, as a projectile parry with the shooter; got " + logged(COMBAT_EVENTS, player)));

			COMBAT_EVENTS.remove(player.getUUID());
			QuestHooks.onBoltDeflected(player, false, shooter);
			helper.assertTrue(logged(COMBAT_EVENTS, player).isEmpty(), msg("a deflection that is not a parry fires no PARRY"));

			QuestHooks.onSaberTechnique(player, "parry", shooter, true);
			List<String> melee = logged(COMBAT_EVENTS, player);
			helper.assertTrue(melee.stream().filter(event -> event.startsWith("parry:")).count() == 1 && melee.contains("parry:false:" + shooter.getUUID())
					&& melee.contains("technique:parry"), msg("a melee parry fires PARRY once too, as melee, beside its TECHNIQUE; got " + melee));
		} finally {
			player.stopUsingItem();
			SaberGuard.forget(player);
			cleanUp(player, shooter);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void powerHitOnlyForLivingTargets(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);
		player.addTag(TAG_COMBAT_EVENTS);
		LivingEntity zombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		ItemEntity item = helper.spawnItem(Items.IRON_INGOT, new BlockPos(5, 1, 3));

		try {
			QuestHooks.onAbilityHit(player, ABILITY_PLAIN, zombie);
			helper.assertTrue(logged(COMBAT_EVENTS, player).equals(List.of("power_hit:" + ABILITY_PLAIN + ":" + zombie.getUUID())),
					msg("a power hitting a living target fires POWER_HIT once with the caster, power and target; got " + logged(COMBAT_EVENTS, player)));

			COMBAT_EVENTS.remove(player.getUUID());
			QuestHooks.onAbilityHit(player, ABILITY_PLAIN, item);
			QuestHooks.onAbilityHit(player, ABILITY_PLAIN, null);
			helper.assertTrue(logged(COMBAT_EVENTS, player).isEmpty(),
					msg("an item entity and a batch of items (null) fire no POWER_HIT; got " + logged(COMBAT_EVENTS, player)));
		} finally {
			item.discard();
			cleanUp(player, zombie);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void oldInterruptReachesHandlersAsUnknown(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);
		LivingEntity zombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), TAG_INTERRUPT_LOG);

		try {
			helper.assertTrue("unknown".equals(ForceReactions.CAUSE_UNKNOWN), msg("the frozen cause string is unknown"));
			ForceReactions.interruptWindup(zombie);
			helper.assertTrue(logged(INTERRUPTS, zombie).equals(List.of("new:unknown:nobody", "old")),
					msg("the M2 one-argument interrupt reaches every handler with cause unknown and no caster; got " + logged(INTERRUPTS, zombie)));

			INTERRUPTS.remove(zombie.getUUID());
			ForceReactions.interruptWindup(zombie, player, ForceReactions.CAUSE_FORCE_PUSH);
			helper.assertTrue(logged(INTERRUPTS, zombie).equals(List.of("new:force_push:" + player.getUUID(), "old")),
					msg("a caused interrupt reaches the new handler with caster and cause, and a handler written for M2 still hears it; got "
							+ logged(INTERRUPTS, zombie)));
		} finally {
			cleanUp(player, zombie);
		}

		helper.succeed();
	}

	/**
	 * Every frozen kind reaches the duration modifiers through every {@link ControlEffects} entry
	 * point (the M2 forms as stagger), and so does every switched call site: Repulse Wave's stagger,
	 * Lightning's shock, Disarming Pull's disarm, Reeling Pull's stagger, an NPC saber parry's
	 * stagger ({@code NpcGuard}, its parry roll seeded) and Crushing Descent's landing stagger. Each
	 * power has its own caster and target, far enough apart that no power reaches another's target.
	 * A mock player runs no movement physics, so the crush leap is landed by hand: it starts 4 blocks
	 * up, and 3 ticks later the jumper is put on the floor and grounded; the next Force Jump tick
	 * lands it with a 4-block fall (the crush needs 3).
	 */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void controlKindsReachDurationModifiers(GameTestHelper helper) {
		registerContractHooks();
		ServerLevel level = helper.getLevel();
		LivingEntity target = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(12, 1, 22), TAG_DURATION_LOG);
		// Repulse Wave through the skill-node mirror in the ability set: the skills feature rewrites the
		// pushBranch field from owned nodes on its periodic refresh of listed players.
		ServerPlayer pusher = awakened(helper, new BlockPos(6, 1, 1), ForcePush.ABILITY_ID, PushBranch.REPULSE_WAVE.nodeId());
		LivingEntity pushed = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 4), TAG_DURATION_LOG);
		ServerPlayer shocker = awakened(helper, new BlockPos(21, 1, 1), ForceLightning.ID);
		LivingEntity shocked = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(21, 1, 5), TAG_DURATION_LOG);
		ServerPlayer puller = awakened(helper, new BlockPos(21, 1, 12));
		LivingEntity reeled = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(21, 1, 14), TAG_DURATION_LOG);
		LivingEntity disarmed = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(21, 1, 17), TAG_DURATION_LOG);
		// Crushing Descent through the skill-node mirror in the ability set (as the test command sets it).
		ServerPlayer jumper = awakened(helper, new BlockPos(13, 5, 4), JumpShape.CRUSH.nodeId());
		LivingEntity crushed = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(14, 1, 5), TAG_DURATION_LOG);
		// The NPC parry: a saber player strikes a NoAI acolyte that faces it (north), as in the M2 parry tests.
		ServerPlayer duelist = facingSouth(M3TestPlayers.listed(helper, new BlockPos(2, 1, 19)));
		duelist.addTag(TAG_DURATION_LOG);
		SithAcolyte acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, new BlockPos(2, 1, 21));
		acolyte.setNoAi(true);
		acolyte.setYRot(180f);
		acolyte.setYBodyRot(180f);
		acolyte.setYHeadRot(180f);
		Entity[] all = {target, pusher, pushed, shocker, shocked, puller, reeled, disarmed, jumper, crushed, duelist};
		ForceAbility push = Abilities.get(ForcePush.ABILITY_ID);
		ForceAbility lightning = Abilities.get(ForceLightning.ID);

		try {
			helper.assertTrue(CONTROL_KINDS.equals(List.of(ControlEffects.STAGGER, ControlEffects.SLOW, ControlEffects.DISARM, ControlEffects.SHOCK, ControlEffects.HOLD)),
					msg("the frozen control kinds are stagger, slow, disarm, shock and hold"));

			// The entry points; the test modifier halves a logged fixture's 40 ticks to 20.
			for (String kind : CONTROL_KINDS) {
				helper.assertTrue(ControlEffects.duration(target, 40, kind) == 20 && kind.equals(lastKind(target)),
						msg("duration(" + kind + ") reaches the modifiers as " + kind + "; got " + kinds(target)));
				helper.assertTrue(ControlEffects.diminished(target, 40, kind) == 20 && kind.equals(lastKind(target)),
						msg("diminished(" + kind + ") reaches the modifiers as " + kind + "; got " + kinds(target)));
				helper.assertTrue(ControlEffects.stagger(null, target, 40, 0, kind) == 20 && kind.equals(lastKind(target)),
						msg("stagger(" + kind + ") reaches the modifiers as " + kind + "; got " + kinds(target)));
			}

			helper.assertTrue(ControlEffects.diminished(target, 40) == 20 && ControlEffects.STAGGER.equals(lastKind(target)),
					msg("the M2 two-argument diminished reaches the modifiers as stagger"));
			helper.assertTrue(ControlEffects.stagger(null, target, 40, 0) == 20 && ControlEffects.STAGGER.equals(lastKind(target)),
					msg("the M2 four-argument stagger reaches the modifiers as stagger"));

			// The call sites. First NpcGuard's parry stagger: the parry roll is the acolyte's own random,
			// seeded here; each try refills the guard and readies it, as the M2 parry test does.
			DuelState duel = NpcGuard.state(acolyte);
			helper.assertTrue(duel != null, msg("the acolyte's guard is registered by its combat goal"));
			acolyte.getRandom().setSeed(NPC_PARRY_SEED); // API-CHECK: RandomSource#setSeed(long) (not in the probe dumps)
			DamageSource strike = level.damageSources().playerAttack(duelist);
			boolean npcParried = false;

			for (int i = 0; i < NPC_PARRY_TRIES && !npcParried; i++) {
				duel.refill();
				acolyte.setGuardState(GuardState.READY);
				npcParried = NpcGuard.onHit(acolyte, duel, level, strike, 2f, SaberCombat.Strike.LIGHT) < 0f;
			}

			helper.assertTrue(npcParried, msg("the acolyte must parry one of " + NPC_PARRY_TRIES + " seeded light strikes"));
			helper.assertTrue(kinds(duelist).contains(ControlEffects.STAGGER),
					msg("an NPC saber parry's stagger reaches the modifiers as stagger; got " + kinds(duelist)));
			acolyte.discard();

			helper.assertTrue(push != null && lightning != null, msg("Force Push and Force Lightning are registered abilities"));
			helper.assertTrue(ForcePush.branch(pusher) == PushBranch.REPULSE_WAVE, msg("the pusher's branch is Repulse Wave"));
			push.onInput(pusher, AbilityAction.START);
			lightning.onInput(shocker, AbilityAction.START);
			helper.assertTrue(push.isActive(pusher) && lightning.isActive(shocker),
					msg("the Push charge and the Lightning channel must start (awakened, learned, enough energy)"));
			ForcePull.pull(puller, disarmed, PullShape.DISARM);
			ForcePull.pull(puller, reeled, PullShape.REEL);
			helper.assertTrue(kinds(disarmed).contains(ControlEffects.DISARM), msg("Disarming Pull reaches the modifiers as disarm; got " + kinds(disarmed)));

			ForceJump.leap(jumper, 0f);
			jumper.setOnGround(false);
			helper.assertTrue(ForceJump.isLeaping(jumper) && ForceJump.shape(jumper) == JumpShape.CRUSH, msg("the Crushing Descent leap must start"));
		} catch (RuntimeException | Error e) {
			acolyte.discard();
			cleanUp(all);
			throw e;
		}

		// The leap's first ticks record the 4-block start as its peak; then the jumper is on the floor.
		helper.runAfterDelay(3, () -> {
			try {
				helper.assertTrue(ForceJump.isLeaping(jumper) && kinds(crushed).isEmpty(), msg("the jumper must still be in the air, nothing crushed yet"));
				Vec3 floor = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(13, 1, 4)));
				jumper.snapTo(floor.x, floor.y, floor.z);
				jumper.setDeltaMovement(Vec3.ZERO);
				jumper.setOnGround(true);
			} catch (RuntimeException | Error e) {
				cleanUp(all);
				throw e;
			}
		});
		// Released after 8 ticks (the minimum charge is 5): Repulse Wave staggers what it pushes.
		helper.runAfterDelay(8, () -> {
			try {
				push.onInput(pusher, AbilityAction.RELEASE);
			} catch (RuntimeException | Error e) {
				cleanUp(all);
				throw e;
			}
		});
		// By then the channel (8 charge ticks) has struck several times, and the reeled zombie arrived on the first tick.
		helper.runAfterDelay(24, () -> {
			try {
				helper.assertTrue(kinds(pushed).contains(ControlEffects.STAGGER), msg("Repulse Wave's stagger reaches the modifiers as stagger; got " + kinds(pushed)));
				helper.assertTrue(kinds(shocked).contains(ControlEffects.SHOCK), msg("Force Lightning's shock reaches the modifiers as shock; got " + kinds(shocked)));
				helper.assertTrue(kinds(reeled).contains(ControlEffects.STAGGER), msg("Reeling Pull's arrival stagger reaches the modifiers as stagger; got " + kinds(reeled)));
				helper.assertTrue(!ForceJump.isLeaping(jumper), msg("the grounded jumper must have landed"));
				helper.assertTrue(kinds(crushed).contains(ControlEffects.STAGGER), msg("Crushing Descent's stagger reaches the modifiers as stagger; got " + kinds(crushed)));
				helper.succeed();
			} finally {
				cleanUp(all);
			}
		});
	}

	/**
	 * The Push cone and the Lightning chain skip creatures the area rule shields (NEUTRAL and ALLIED
	 * bystanders) and keep the others. Both read their targets through the public pickers the casts use.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void pushConeAndLightningChainSkipShielded(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer caster = facingSouth(M3TestPlayers.listed(helper, new BlockPos(12, 1, 2)));

		try {
			// Every creature stands well inside the base cone (6 blocks, 35 degrees either side).
			LivingEntity open = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(12, 1, 5));
			LivingEntity bystander = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(11, 1, 6), TAG_RULE_NEUTRAL);
			LivingEntity ally = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(13, 1, 6), TAG_RULE_ALLIED);
			LivingEntity provoked = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(12, 1, 7), TAG_RULE_NEUTRAL, TAG_PROVOKED);
			PushBalance pushBalance = ForcePush.balance();
			List<Entity> pushed = PushTargeting.find(caster, PushShape.of(pushBalance, PushBranch.NONE, 0, 1f), pushBalance).stream()
					.map(PushTargeting.Target::entity).toList();
			helper.assertTrue(pushed.contains(open) && pushed.contains(provoked), msg("the Push cone keeps a zombie no rule covers and a provoked one"));
			helper.assertTrue(!pushed.contains(bystander) && !pushed.contains(ally), msg("the Push cone skips the NEUTRAL and the ALLIED bystander"));
			open.discard();
			bystander.discard();
			ally.discard();
			provoked.discard();

			// The primary straight ahead; the chain candidates beside it, all outside the 20-degree primary cone.
			LivingEntity primary = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(12, 1, 6));
			LivingEntity nearBystander = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(14, 1, 6), TAG_RULE_NEUTRAL);
			LivingEntity nearAlly = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(10, 1, 6), TAG_RULE_ALLIED);
			LivingEntity foe = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(15, 1, 8));
			List<LivingEntity> chain = ForceLightning.pickTargets(caster, LightningShape.CHAIN, ForceLightning.balance());
			helper.assertTrue(!chain.isEmpty() && chain.getFirst() == primary, msg("the arcs start at the zombie on the crosshair"));
			helper.assertTrue(!chain.contains(nearBystander) && !chain.contains(nearAlly),
					msg("the chain skips the NEUTRAL and the ALLIED bystander, though both are nearer the primary than the foe"));
			helper.assertTrue(chain.size() == 2 && chain.get(1) == foe, msg("the chain jumps to the farther zombie no rule covers; got " + chain.size() + " targets"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	/**
	 * A Force Heal channel beside an ALLIED zombie (an Enemy the rule calls ALLIED) earns no Heal
	 * mastery; the control, the same channel once the rule no longer covers the zombie, does. The
	 * healer stands on the dais at the arena's centre, 11.5 to 12.5 blocks from its edges; with the
	 * runner's gap between test structures, no other test's fixture is within the 16-block mastery
	 * radius unless a creature leaves its own structure.
	 */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void healMasteryNotGrantedBesideAllied(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer healer = awakened(helper, new BlockPos(12, 2, 12), ForceHeal.ID);
		LivingEntity zombie = fixture(helper, EntityTypes.ZOMBIE, new BlockPos(14, 1, 12), TAG_RULE_ALLIED);
		ForceAbility heal = Abilities.get(ForceHeal.ID);
		HealBalance hb = ForceHeal.balance();
		float radius = hb.masteryHostileRadius;

		try {
			helper.assertTrue(heal != null, msg("Force Heal is a registered ability"));
			helper.assertTrue(!ForcePowers.hostileNear(healer, radius), msg("nothing within " + radius + " blocks counts as hostile: the zombie is ALLIED"));
			startHeal(helper, healer, heal);
		} catch (RuntimeException | Error e) {
			cleanUp(healer, zombie);
			throw e;
		}

		// With the default balance the first heal tick comes after 6 startup ticks and then one every 5:
		// four by tick 30, 4 health, one mastery point's worth (masteryHpPerPoint).
		helper.runAfterDelay(30, () -> {
			try {
				heal.onInput(healer, AbilityAction.RELEASE);
				float healed = healer.getHealth() - HEAL_START_HEALTH;
				helper.assertTrue(healed >= hb.masteryHpPerPoint - 1.0e-3f,
						msg("the channel must heal at least one mastery point's worth (" + hb.masteryHpPerPoint + "), healed " + healed));
				helper.assertTrue(Progression.get(healer).mastery(ForceHeal.ID) == 0, msg("healing beside an ALLIED zombie grants no Heal mastery"));

				zombie.removeTag(TAG_RULE_ALLIED);
				helper.assertTrue(ForcePowers.hostileNear(healer, radius), msg("without the rule the zombie falls back to HOSTILE"));
				Progression.setCooldown(healer, ForceHeal.ID, 0);
				startHeal(helper, healer, heal);
			} catch (RuntimeException | Error e) {
				cleanUp(healer, zombie);
				throw e;
			}
		});
		helper.runAfterDelay(64, () -> {
			try {
				heal.onInput(healer, AbilityAction.RELEASE);
				helper.assertTrue(Progression.get(healer).mastery(ForceHeal.ID) > 0, msg("the control: the same channel beside a hostile zombie earns Heal mastery"));
				helper.succeed();
			} finally {
				cleanUp(healer, zombie);
			}
		});
	}

	/** Refills the healer's energy, sets their health to {@link #HEAL_START_HEALTH} and starts a Force Heal channel. */
	private static void startHeal(GameTestHelper helper, ServerPlayer healer, ForceAbility heal) {
		ForceData data = Progression.get(healer);
		data.energy = data.maxEnergy;
		Progression.commit(healer, data);
		healer.setHealth(HEAL_START_HEALTH);
		heal.onInput(healer, AbilityAction.START);
		helper.assertTrue(heal.isActive(healer), msg("the Force Heal channel must start (awakened, learned, enough energy, standing still)"));
	}

	// ---------------------------------------------------------------- Force-reactive blocks, Force Sense, Guidance and rank

	private static ForceBlockContext blockAct(ServerPlayer caster, BlockPos pos, String abilityId) {
		ServerLevel level = caster.level();
		return new ForceBlockContext(caster, level, pos, level.getBlockState(pos), ForceAct.PUSH, new Vec3(0, 0, 1), 1f, abilityId);
	}

	@GameTest(maxTicks = 20)
	public void forceBlockRegisterDuplicateThrows(GameTestHelper helper) {
		registerTestBlockHandler();
		ServerPlayer caster = M3TestPlayers.listed(helper);

		try {
			RuntimeException thrown = null;

			try {
				ForceBlockInteractions.register(TEST_BLOCK, ctx -> true);
			} catch (RuntimeException e) {
				thrown = e;
			}

			helper.assertTrue(thrown != null, msg("a second handler for the same block must throw"));

			helper.setBlock(new BlockPos(3, 1, 3), TEST_BLOCK);
			BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
			helper.assertTrue(ForceBlockInteractions.dispatch(blockAct(caster, pos, ABILITY_BLOCK)), msg("the first handler still answers its act"));
			helper.assertTrue(!ForceBlockInteractions.dispatch(blockAct(caster, pos, ForcePush.ABILITY_ID)),
					msg("the refused duplicate (which would consume every act) never replaced it"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	private static void assertAimFinds(GameTestHelper helper, ServerPlayer caster, BlockPos expected, String what) {
		BlockHitResult hit = ForceBlockInteractions.findOnAim(caster, 8);
		helper.assertTrue(hit != null && hit.getBlockPos().equals(helper.absolutePos(expected)),
				msg(what + ": expected " + expected + ", got " + (hit == null ? "none" : helper.relativePos(hit.getBlockPos()))));
	}

	/**
	 * Needs the game-test datapack to list the stand-in {@code minecraft:target} in
	 * {@code #oldrepublic:force_reactive}; the main tag stays W1's.
	 */
	@GameTest(maxTicks = 20)
	public void findOnAimReturnsFirstReactiveBlock(GameTestHelper helper) {
		helper.assertTrue(TEST_BLOCK.defaultBlockState().is(ForceBlockInteractions.REACTIVE),
				msg("minecraft:target must be in #oldrepublic:force_reactive through src/gametest/resources/data/oldrepublic/tags/block/force_reactive.json"));
		// The eyes are 1.62 above the feet at y = 1, so the ray runs along +z through the blocks at y = 2.
		ServerPlayer caster = facingSouth(M3TestPlayers.listed(helper, new BlockPos(2, 1, 0)));
		BlockPos front = new BlockPos(2, 2, 2);
		BlockPos first = new BlockPos(2, 2, 3);
		BlockPos second = new BlockPos(2, 2, 6);

		try {
			helper.setBlock(first, TEST_BLOCK);
			helper.setBlock(second, TEST_BLOCK);
			assertAimFinds(helper, caster, first, "two reactive blocks on the ray: the nearer one");
			helper.assertTrue(ForceBlockInteractions.findOnAim(caster, 2) == null, msg("a reactive block beyond the range is not found"));

			helper.setBlock(front, Blocks.COBWEB);
			assertAimFinds(helper, caster, first, "a non-reactive block without collision (a cobweb) does not hide the reactive block behind it");

			helper.setBlock(front, Blocks.STONE);
			helper.assertTrue(ForceBlockInteractions.findOnAim(caster, 8) == null, msg("a non-reactive solid block first on the ray hides every reactive block behind it"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void guidanceTiesByRegistrationOrder(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			player.addTag(TAG_GUIDANCE_TIE);
			Guidance.Request tie = Guidance.current(player);
			helper.assertTrue(tie != null && GUIDANCE_FIRST.equals(tie.source()),
					msg("two providers ask at the same priority: the one registered first wins; got " + (tie == null ? "none" : tie.source())));

			player.addTag(TAG_GUIDANCE_HIGHER);
			Guidance.Request higher = Guidance.current(player);
			helper.assertTrue(higher != null && GUIDANCE_HIGHER.equals(higher.source()),
					msg("a higher priority beats registration order; got " + (higher == null ? "none" : higher.source())));

			player.removeTag(TAG_GUIDANCE_TIE);
			player.removeTag(TAG_GUIDANCE_HIGHER);
			Guidance.Request untagged = Guidance.current(player);
			helper.assertTrue(untagged == null || !untagged.source().startsWith("oldrepublic_test:"), msg("the test providers ask nothing for an untagged player"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void senseTargetsCapAt32(GameTestHelper helper) {
		registerContractHooks();
		ServerPlayer caster = M3TestPlayers.listed(helper);

		try {
			caster.addTag(TAG_SENSE_FLOOD);
			Vec3 eye = caster.getEyePosition();
			List<SenseTargets.Point> points = SenseTargets.collect(caster, 16, 24, SENSE_CAP);
			helper.assertTrue(points.size() == SENSE_CAP, msg(SENSE_FLOOD + " points are offered and at most " + SENSE_CAP + " kept, got " + points.size()));

			// Offered farthest first, kept nearest first: the 8 farthest are the ones dropped.
			for (int i = 0; i < SENSE_CAP; i++) {
				double expected = (i + 1) * SENSE_STEP;
				double distance = points.get(i).pos().distanceTo(eye);
				helper.assertTrue(Math.abs(distance - expected) < 1.0e-6, msg("point " + i + " must be " + expected + " from the eyes, got " + distance));
			}
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void promoteRefusesIndependent(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			IdentityData independent = Identity.get(player);
			independent.path = Path.INDEPENDENT;
			independent.rank = 0;
			Identity.commit(player, independent);
			Identity.promote(player, 1);
			Identity.promote(player, 3);
			helper.assertTrue(Identity.rank(player) == 0 && Identity.path(player) == Path.INDEPENDENT,
					msg("an Independent is never promoted (titles, not ranks); rank is " + Identity.rank(player)));

			IdentityData jedi = Identity.get(player);
			jedi.path = Path.JEDI;
			jedi.rank = 1;
			Identity.commit(player, jedi);
			Identity.promote(player, 2);
			helper.assertTrue(Identity.rank(player) == 2, msg("the control: the same call promotes a Jedi Initiate to rank 2"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- site anchors and runtime sites

	/** The anchor fixture's marker block under each anchor (see gen_test_fixtures_m3.py). */
	private static final Map<SiteAnchors.Anchor, Block> MARKERS = Map.of(
			SiteAnchors.Anchor.HEART, Blocks.GOLD_BLOCK,
			SiteAnchors.Anchor.PEDESTAL, Blocks.DIAMOND_BLOCK,
			SiteAnchors.Anchor.GUARD, Blocks.EMERALD_BLOCK,
			SiteAnchors.Anchor.CHEST_1, Blocks.LAPIS_BLOCK);

	/**
	 * Stamps the anchor fixture into the arena under all four rotations (90 included) and checks that
	 * every sidecar anchor, mapped by {@link SiteAnchors#toWorld}, lands on its marker block.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void siteAnchorsRotated90(GameTestHelper helper) {
		helper.assertTrue(SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.HEART).equals(List.of(new BlockPos(1, 1, 5))),
				msg("the anchor fixture's sidecar must load: HEART at (1, 1, 5), got " + SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.HEART)));
		helper.assertTrue(SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.CHEST_1).size() == 2, msg("CHEST_1 has two cells, in sidecar order"));

		// Non-overlapping origins inside the 24 x 24 arena for the 5 x 3 x 7 fixture under each rotation.
		Map<Rotation, BlockPos> origins = Map.of(
				Rotation.NONE, new BlockPos(1, 1, 1),
				Rotation.CLOCKWISE_90, new BlockPos(20, 1, 1),
				Rotation.CLOCKWISE_180, new BlockPos(8, 1, 22),
				Rotation.COUNTERCLOCKWISE_90, new BlockPos(14, 1, 22));

		for (Map.Entry<Rotation, BlockPos> entry : origins.entrySet()) {
			Rotation rotation = entry.getKey();
			BlockPos origin = helper.absolutePos(entry.getValue());
			SitePiece piece = RuntimeSites.stamp(helper.getLevel(), SiteKind.JEDI_RUIN, ANCHOR_FIXTURE, origin, rotation);
			helper.assertTrue(piece != null, msg("the anchor fixture must place under " + rotation));

			for (Map.Entry<SiteAnchors.Anchor, Block> marker : MARKERS.entrySet()) {
				for (BlockPos pos : SiteAnchors.all(piece, marker.getKey())) {
					helper.assertTrue(piece.box().isInside(pos), msg(marker.getKey() + " under " + rotation + " lies inside the piece"));
					helper.assertTrue(helper.getLevel().getBlockState(pos).is(marker.getValue()),
							msg(marker.getKey() + " under " + rotation + " must be on its marker at " + helper.relativePos(pos)));
				}
			}

			if (rotation == Rotation.CLOCKWISE_90) {
				// Clockwise 90 about the origin maps local (x, z) to (-z, x).
				helper.assertTrue(origin.offset(-5, 1, 1).equals(SiteAnchors.find(piece, SiteAnchors.Anchor.HEART)), msg("HEART (1, 1, 5) turned 90 is (-5, 1, 1)"));
			}
		}

		helper.succeed();
	}

	/** Lays grass over the whole arena floor and clears everything above it. */
	private static void openArena(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);

				for (int y = 1; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/**
	 * The surface search on injected rings: the first candidate lies along {@code forward}, the
	 * template turns its entrance (local north) toward the origin, is centred on the stand with its
	 * lowest layer in the ground, and an empty ring list falls back to the fallback rings. Uses the
	 * non-recording seams ({@code findSurface}, {@code stamp}): {@code placeAt} writes
	 * {@code WorldSites}, which no test may.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void surfaceSpotOnInjectedRingsAndFallback(GameTestHelper helper) {
		openArena(helper);
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 12)));
		Vec3 east = new Vec3(1, 0, 0);
		MeetingSites.Rules rules = new MeetingSites.Rules(5, 7, 2, pos -> false, List.of(), 0);

		RuntimeSites.Spot spot = RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[] {6}, new int[0], rules, 1);
		helper.assertTrue(spot != null, msg("open grass on ring 6 must pass"));
		helper.assertTrue(spot.stand().equals(helper.absolutePos(new BlockPos(18, 1, 12))), msg("the first candidate is 6 blocks east, got " + helper.relativePos(spot.stand())));
		helper.assertTrue(spot.rotation() == Rotation.COUNTERCLOCKWISE_90, msg("the entrance turns west, toward the origin; got " + spot.rotation()));
		helper.assertTrue(spot.box().minX() + spot.box().maxX() == 2 * spot.stand().getX() && spot.box().minZ() + spot.box().maxZ() == 2 * spot.stand().getZ(),
				msg("the template is centred on the stand: " + spot.box()));
		helper.assertTrue(spot.box().minY() == spot.stand().getY() - 1, msg("buried 1: the lowest layer replaces the ground"));

		RuntimeSites.Spot fallback = RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[0], new int[] {6}, rules, 1);
		helper.assertTrue(spot.equals(fallback), msg("with no rings the fallback rings are searched"));

		MeetingSites.Rules steep = new MeetingSites.Rules(5, 7, -1, pos -> false, List.of(), 0);
		helper.assertTrue(RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[] {6}, new int[] {4}, steep, 1) == null,
				msg("when every candidate fails the search returns null (the caller retries)"));

		SitePiece piece = RuntimeSites.stamp(level, SiteKind.JEDI_RUIN, ANCHOR_FIXTURE, spot.templateOrigin(), spot.rotation());
		helper.assertTrue(piece != null && piece.box().equals(spot.box()), msg("the stamped piece has the spot's box"));
		BlockPos heart = SiteAnchors.find(piece, SiteAnchors.Anchor.HEART);
		helper.assertTrue(heart != null && level.getBlockState(heart).is(Blocks.GOLD_BLOCK), msg("the stamped fixture's HEART is on its marker"));
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void adaptationNeverFillsBesideBuiltBlocks(GameTestHelper helper) {
		// Ground: dirt at y = 1, grass at y = 2, over the arena floor.
		for (int x = 2; x <= 11; x++) {
			for (int z = 2; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);

				for (int y = 3; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}

		// A two-deep hole beside a player's planks, and one in open ground; a tuft of grass above.
		helper.setBlock(new BlockPos(4, 2, 5), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.AIR);
		helper.setBlock(new BlockPos(9, 3, 9), Blocks.SHORT_GRASS);

		BlockPos min = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos max = helper.absolutePos(new BlockPos(10, 4, 10));
		RuntimeSites.adaptTerrain(helper.getLevel(), BoundingBox.fromCorners(min, max), min.getY());

		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 2, 5));
		helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 5)).isAir() && helper.getBlockState(new BlockPos(5, 1, 5)).isAir(),
				msg("the hole beside the planks is never filled"));
		helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(8, 2, 8));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(8, 1, 8));
		helper.assertTrue(helper.getBlockState(new BlockPos(9, 3, 9)).isAir(), msg("natural clutter above the footprint is cleared"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the locator queue (fake lookup)

	/** A fake lookup: a fixed plan, fixed pieces per cell, and a log of the cells checked. */
	private static final class FakeLookup implements LocatorQueue.Lookup {
		private final List<LocatorQueue.Cell> plan;
		private final Map<LocatorQueue.Cell, List<SitePiece>> pieces = new HashMap<>();
		private final List<LocatorQueue.Cell> checked = new ArrayList<>();

		FakeLookup(List<LocatorQueue.Cell> plan) {
			this.plan = plan;
		}

		FakeLookup with(int x, int z, int ring, SitePiece... found) {
			pieces.put(new LocatorQueue.Cell(x, z, ring), List.of(found));
			return this;
		}

		@Override
		public List<LocatorQueue.Cell> plan(LocatorQueue.Search search) {
			return plan;
		}

		@Override
		public List<SitePiece> check(LocatorQueue.Search search, LocatorQueue.Cell cell) {
			checked.add(cell);
			return pieces.getOrDefault(cell, List.of());
		}
	}

	/** A callback that remembers whether and with what it ran. */
	private static final class Result {
		boolean done;
		@Nullable SitePiece piece;

		void accept(@Nullable SitePiece found) {
			done = true;
			piece = found;
		}
	}

	private static final BlockPos FROM = new BlockPos(0, 64, 0);

	/** A Jedi ruin start piece whose box is centred on ({@code x}, {@code z}). */
	private static SitePiece ruinAt(int x, int z, String template) {
		BoundingBox box = new BoundingBox(x - 2, 60, z - 2, x + 2, 64, z + 2);
		return new SitePiece(SiteKind.JEDI_RUIN, OldRepublicMod.id(template), box, Rotation.NONE, new BlockPos(x - 2, 60, z - 2),
				SitePiece.worldgenKey(Level.OVERWORLD, new ChunkPos(x >> 4, z >> 4)));
	}

	private static LocatorQueue.Search search(GameTestHelper helper, UUID player, int radius) {
		return new LocatorQueue.Search(helper.getLevel(), player, SiteKind.JEDI_RUIN, FROM, radius, null);
	}

	@GameTest(maxTicks = 20)
	public void locatorQueueChecksFourCellsPerTick(GameTestHelper helper) {
		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, 3));
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result first = new Result();
		Result second = new Result();
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, first::accept);
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, second::accept);
		helper.assertTrue(lookup.checked.isEmpty() && !first.done, msg("submitting checks nothing"));

		int ticks = 0;

		while (!queue.idle() && ticks < 100) {
			ticks++;
			int checked = queue.tick(ticks);
			helper.assertTrue(checked <= 4, msg("tick " + ticks + " checked " + checked + " cells; at most 4 across all searches"));
		}

		helper.assertTrue(lookup.checked.size() == 50, msg("two searches of 25 cells check 50 cells, got " + lookup.checked.size()));
		helper.assertTrue(ticks == 13, msg("50 cells at 4 per tick take 13 ticks, took " + ticks));
		helper.assertTrue(first.done && second.done && first.piece == null && second.piece == null, msg("both searches end with null"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void locatorNotFoundRadius768FinishesWithinBudget(GameTestHelper helper) {
		int rings = LocatorQueue.rings(768, 32, 4);
		helper.assertTrue(rings == 3, msg("768 blocks at spacing 32 is ceil(1.5) + 1 = 3 rings, got " + rings));
		helper.assertTrue(LocatorQueue.rings(100_000, 32, 4) == 4, msg("rings are capped at 4"));
		helper.assertTrue(LocatorQueue.ringCells(0, 0, rings).size() == 25, msg("3 rings are 1 + 8 + 16 cells"));

		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, rings));
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result result = new Result();
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, result::accept);
		int ticks = 0;

		while (!result.done && ticks < 100) {
			ticks++;
			helper.assertTrue(queue.tick(ticks) <= 4, msg("never more than 4 cells per tick"));
		}

		helper.assertTrue(result.done && result.piece == null, msg("a search that finds nothing calls back with null"));
		helper.assertTrue(ticks == 7, msg("25 cells at 4 per tick finish on tick 7, took " + ticks));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void locatorDropsResultsBeyondRadius(GameTestHelper helper) {
		SitePiece far = ruinAt(300, 0, "jedi_ruin/courtyard");
		SitePiece kept = ruinAt(200, 0, "jedi_ruin/courtyard");
		SitePiece nearer = ruinAt(0, 180, "jedi_ruin/courtyard");
		SitePiece taken = ruinAt(-100, 0, "jedi_ruin/taken");
		SitePiece outerRing = ruinAt(50, 0, "jedi_ruin/courtyard");
		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, 3))
				.with(0, 0, 0, far)
				.with(1, 0, 1, kept)
				.with(0, 1, 1, nearer)
				.with(-1, 0, 1, taken)
				.with(2, 0, 2, outerRing);
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result result = new Result();
		UUID player = UUID.randomUUID();
		LocatorQueue.Search filtered = new LocatorQueue.Search(helper.getLevel(), player, SiteKind.JEDI_RUIN, FROM, 256,
				piece -> piece.template().getPath().equals("jedi_ruin/taken"));
		queue.submit(filtered, 0, result::accept);
		long finished = 0;

		while (!result.done && finished < 10) {
			finished++;
			queue.tick(finished);
		}

		helper.assertTrue(result.done, msg("the search must finish"));
		helper.assertTrue(nearer.equals(result.piece), msg("the nearest kept result wins: the 300-block start is beyond 256, the taken one is excluded; got " + result.piece));
		helper.assertTrue(lookup.checked.stream().noneMatch(cell -> cell.ring() == 2), msg("once ring 1 holds a result, ring 2 is not checked"));

		// The cache: fresh for the same player, kind and filter; stale after 600 ticks or a 48-block move.
		ResourceKey<Level> dimension = helper.getLevel().dimension();
		long now = finished + 2;
		Result again = new Result();
		queue.submit(filtered, now, again::accept);
		helper.assertTrue(again.done && nearer.equals(again.piece), msg("a fresh cached result is handed back at once"));
		helper.assertTrue(nearer.equals(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM, now)), msg("cached() reads it synchronously"));
		helper.assertTrue(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM.offset(48, 0, 0), now) == null, msg("a 48-block move makes it stale"));
		helper.assertTrue(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM, finished + LocatorQueue.DEFAULT_CACHE_TICKS) == null, msg("600 ticks make it stale"));
		helper.assertTrue(queue.cached(UUID.randomUUID(), SiteKind.JEDI_RUIN, dimension, FROM, now) == null, msg("the cache is per player"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- WorldSites.Data and outposts (detached values)

	private static WorldSites.SiteRecord record(SiteKind kind, ResourceKey<Level> dimension, BlockPos origin, String template) {
		return new WorldSites.SiteRecord(kind, dimension, origin, Rotation.CLOCKWISE_90, OldRepublicMod.id(template), UUID.randomUUID(), 3L);
	}

	@GameTest(maxTicks = 20)
	public void worldSitesDataCap512AndNearest(GameTestHelper helper) {
		WorldSites.Data data = new WorldSites.Data();

		for (int i = 0; i < 4; i++) {
			data.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(i * 1000, 64, 0), "outpost/republic_post"));
		}

		// The cap was 512 in M3 and is 1,024 from M4 (spec MILESTONE_4.md 15.1): 92 more than fit are added either way.
		int max = WorldSites.Data.MAX_RECORDS;

		for (int i = 0; i < max + 88; i++) {
			data.add(record(SiteKind.MISSION_SITE, Level.OVERWORLD, new BlockPos(0, 64, 5000 + 10 * i), "mission/fence_camp"));
		}

		helper.assertTrue(data.all().size() == max, msg("at most " + max + " records, got " + data.all().size()));
		helper.assertTrue(data.count(SiteKind.REPUBLIC_OUTPOST) == 4, msg("outposts are never dropped for the cap"));
		helper.assertTrue(data.count(SiteKind.MISSION_SITE) == max - 4, msg("the oldest mission sites are dropped"));
		helper.assertTrue(data.all().get(4).origin().getZ() == 5000 + 10 * 92, msg("the oldest kept mission site is the 93rd"));

		WorldSites.Data copy = data.copy();
		copy.add(record(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(1, 64, 1), "site/echo_shrine_jedi"));
		helper.assertTrue(data.count(SiteKind.ECHO_SHRINE) == 0 && copy.count(SiteKind.ECHO_SHRINE) == 1, msg("a copy is independent"));

		WorldSites.Data near = new WorldSites.Data();
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(100, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(300, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(-50, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.NETHER, new BlockPos(10, 64, 0), "outpost/republic_post"));
		BlockPos from = new BlockPos(0, 64, 0);
		WorldSites.SiteRecord nearest = near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 512);
		helper.assertTrue(nearest != null && nearest.origin().getX() == -50, msg("the nearest overworld outpost is at -50"));
		helper.assertTrue(near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 49) == null, msg("nothing within 49 blocks"));
		helper.assertTrue(near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 50) != null, msg("the radius is inclusive"));
		WorldSites.SiteRecord nether = near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.NETHER, from, 512);
		helper.assertTrue(nether != null && nether.origin().getX() == 10, msg("nearest stays in its dimension"));
		helper.assertTrue(near.nearest(SiteKind.IMPERIAL_OUTPOST, Level.OVERWORLD, from, 512) == null, msg("nearest stays in its kind"));

		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(100, 64, 0), "outpost/republic_post"));
		helper.assertTrue(near.all().size() == 4, msg("the same placement added twice is kept once"));

		List<UUID> owners = new ArrayList<>();

		for (int i = 0; i < 70; i++) {
			UUID owner = UUID.randomUUID();
			owners.add(owner);
			near.expireCairnOwner(owner);
		}

		helper.assertTrue(near.expiredCairnOwners().size() == WorldSites.Data.MAX_EXPIRED_OWNERS, msg("at most 64 expired cairn owners"));
		helper.assertTrue(!near.expiredCairnOwners().contains(owners.getFirst()) && near.expiredCairnOwners().contains(owners.getLast()),
				msg("the oldest expired owners are dropped first"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void worldSitesDataCodecRoundTrip(GameTestHelper helper) {
		WorldSites.Data data = new WorldSites.Data();
		data.add(record(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(12, 70, -40), "site/echo_shrine_sith"));
		data.add(new WorldSites.SiteRecord(SiteKind.IMPERIAL_OUTPOST, Level.NETHER, new BlockPos(-3, 40, 9), Rotation.CLOCKWISE_180,
				OldRepublicMod.id("outpost/imperial_post"), null, 12L));
		UUID expired = UUID.randomUUID();
		data.expireCairnOwner(expired);

		JsonElement json = WorldSites.Data.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		WorldSites.Data back = WorldSites.Data.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.all().equals(data.all()), msg("records survive the codec: " + json));
		helper.assertTrue(back.expiredCairnOwners().equals(data.expiredCairnOwners()), msg("expired cairn owners survive the codec"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void outpostDecideEveryDecision(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		BlockPos player = new BlockPos(0, 64, 0);

		helper.assertTrue(decide(b, player, new BlockPos(600, 64, 0), null, 0) == Outposts.Decision.WORLDGEN, msg("a worldgen outpost within 1024 is used"));
		helper.assertTrue(decide(b, player, new BlockPos(1024, 64, 0), new BlockPos(10, 64, 0), 3) == Outposts.Decision.WORLDGEN,
				msg("1024 is inclusive, and worldgen wins over a nearer runtime outpost"));
		helper.assertTrue(decide(b, player, new BlockPos(1100, 64, 0), new BlockPos(0, 64, 500), 3) == Outposts.Decision.REUSE,
				msg("else a runtime outpost within 768 is reused"));
		helper.assertTrue(decide(b, player, null, new BlockPos(0, 64, 800), 3) == Outposts.Decision.PLACE, msg("else a new one is placed"));
		helper.assertTrue(decide(b, player, null, null, 7) == Outposts.Decision.PLACE, msg("7 runtime outposts are below the cap"));
		helper.assertTrue(decide(b, player, null, new BlockPos(0, 64, 2000), 8) == Outposts.Decision.CAP_NEAREST, msg("at 8 the nearest is used"));

		SiteBalance off = new SiteBalance();
		off.runtimeOutposts = false;
		helper.assertTrue(decide(off, player, null, null, 0) == Outposts.Decision.CAP_NEAREST, msg("with runtime outposts off nothing is placed"));
		helper.succeed();
	}

	private static Outposts.Decision decide(SiteBalance b, BlockPos player, @Nullable BlockPos worldgen, @Nullable BlockPos runtime, int count) {
		return Outposts.decide(new Outposts.OutpostQuery(player, worldgen, runtime, count), b);
	}

	// ---------------------------------------------------------------- bolts

	@GameTest(maxTicks = 20)
	public void blasterBoltCreateEveryStyle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 muzzle = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		BlasterBolt plain = BlasterBolt.create(level, null, muzzle, 5f, 60);
		helper.assertTrue(plain.style() == BlasterBolt.Style.POACHER && !plain.heavy(), msg("the five-argument form makes a light poacher bolt"));

		for (BlasterBolt.Style style : BlasterBolt.Style.values()) {
			for (boolean heavy : new boolean[] {false, true}) {
				BlasterBolt bolt = BlasterBolt.create(level, null, muzzle, 5f, 60, style, heavy);
				helper.assertTrue(bolt.style() == style && bolt.heavy() == heavy, msg("create keeps style " + style + " and heavy " + heavy));
			}
		}

		helper.succeed();
	}
}
