package com.oldrepublic.force;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.force.barrier.BarrierParticles;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.lift.ForceLift;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/**
 * Milestone 4 server and common initialisation for Force Speed, Force Barrier and Force Choke
 * (spec MILESTONE_4.md section 9), and Telekinetic Lift for Jedi and Sith (9.4). Owned by W6; the
 * lead calls it from {@code OldRepublicMod} after the Phase 0 contracts.
 *
 * <p>Lift for the paths: buying {@code lift_learned_jedi} or {@code lift_learned_sith} teaches
 * {@code force_lift}, and the skills feature then gives the player the {@code force_lift} node as a
 * granted node (an ability taught outside the tree gets its node). Granted nodes survive a respec,
 * so {@link #tickLiftNodes} turns that node into a bought one with no cost while a path node owns
 * it: a respec then takes Lift away with the node that taught it, and refunds only the 2 SP paid.
 */
public final class ForceM4Feature {
	/** The three Milestone 4 powers. */
	private static final String[] POWERS = {ForceSpeed.ID, ForceBarrier.ID, ForceChoke.ID};
	/** Their branch and passive nodes, for the grant command. */
	private static final String[] NODES = {ForceSpeed.BURST, ForceSpeed.LONG_ROAD, ForceSpeed.WAYFARER, ForceBarrier.SHELTER, ForceBarrier.RETURN,
			ForceBarrier.GUARDIANS_PATIENCE, ForceChoke.CRUSH, ForceChoke.DREAD, ForceChoke.CRUEL_FOCUS};
	/** The path nodes that teach Lift (9.4). */
	public static final String LIFT_JEDI = "lift_learned_jedi";
	public static final String LIFT_SITH = "lift_learned_sith";
	private static final int LIFT_CHECK_INTERVAL = 10;
	private static final int PRUNE_INTERVAL = 100;

	public static void init() {
		BarrierParticles.init();

		Abilities.register(new ForceSpeed());
		Abilities.register(new ForceBarrier());
		Abilities.register(new ForceChoke());

		DamageModifiers.register(ForceBarrier::modifyDamage);
		DamageModifiers.register(ForceChoke::modifyDamage);
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public float onPushHit(ServerPlayer caster, LivingEntity target) {
				// A Jedi ally's Push on a choking caster breaks the grip (9.3).
				ForceChoke.onPushed(target);
				return 1f;
			}
		});
		CombatEvents.PARRY.register((defender, attacker, projectile) -> ForceBarrier.onParry(defender));

		ServerTickEvents.END_SERVER_TICK.register(ForceM4Feature::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerPlayerEvents.LEAVE.register(player -> forgetAll(player, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ForceSpeed.removeModifiers(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forgetAll(player, true);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forgetAll(player, true));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ForceSpeed::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			ForceBarrier.onDamaged(entity);
			ForceChoke.onDamaged(entity, damageTaken);
		});
		// Behind a barrier the hands hold the Force: no blows and no items until it drops (9.2).
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
				!level.isClientSide() && ForceBarrier.isChannelling(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				!level.isClientSide() && ForceBarrier.isChannelling(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		OldRepublicCommands.addSubcommand((context, root) -> root.then(forceCommand()));
	}

	private static void tick(MinecraftServer server) {
		ForceSpeed.tick();
		ForceBarrier.tick();
		ForceChoke.tick();

		long now = server.overworld().getGameTime();

		if (now % LIFT_CHECK_INTERVAL == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickLiftNodes(player);
			}
		}

		if (now % PRUNE_INTERVAL == 0) {
			ForceChoke.prune(now);
		}
	}

	/**
	 * While a path node ({@code lift_learned_jedi} or {@code lift_learned_sith}) is owned, the
	 * {@code force_lift} node it taught is owned as bought (no cost recorded), not granted, so a
	 * respec removes both together. Returns true when it changed the player's skills.
	 */
	public static boolean tickLiftNodes(ServerPlayer player) {
		if (Skills.rank(player, LIFT_JEDI) <= 0 && Skills.rank(player, LIFT_SITH) <= 0) {
			return false;
		}

		SkillData data = SkillService.data(player);

		if (!data.owns(ForceLift.ID)) {
			// Taught again by the skills feature on its next refresh (the ability is in the set).
			return false;
		}

		if (!data.granted.contains(ForceLift.ID)) {
			return false;
		}

		data.granted.remove(ForceLift.ID);
		SkillService.commit(player, data);
		return true;
	}

	/** Ends everything the player does with the three powers (death, disconnect, change of level). */
	public static void forgetAll(ServerPlayer player, boolean feedback) {
		ForceSpeed.forget(player, feedback);
		ForceBarrier.forget(player, feedback);
		ForceChoke.forget(player, feedback);
	}

	private static void clearAll() {
		ForceSpeed.clear();
		ForceBarrier.clear();
		ForceChoke.clear();
	}

	/**
	 * Adds the three powers and their nodes to {@code /oldrepublic force grant <id> [player]} and the
	 * powers to {@code /oldrepublic force use <id> start|release|cancel} (Brigadier merges these into
	 * the lead's {@code force} command).
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> forceCommand() {
		LiteralArgumentBuilder<CommandSourceStack> grant = literal("grant");
		LiteralArgumentBuilder<CommandSourceStack> use = literal("use");

		for (String id : List.of(POWERS)) {
			addGrant(grant, id);
			LiteralArgumentBuilder<CommandSourceStack> power = literal(id);

			for (AbilityAction action : AbilityAction.values()) {
				power.then(literal(action.name().toLowerCase(Locale.ROOT)).executes(ctx -> use(ctx.getSource().getPlayerOrException(), id, action)));
			}

			use.then(power);
		}

		for (String id : NODES) {
			addGrant(grant, id);
		}

		addGrant(grant, LIFT_JEDI);
		addGrant(grant, LIFT_SITH);
		return literal("force").then(grant).then(use);
	}

	private static void addGrant(LiteralArgumentBuilder<CommandSourceStack> grant, String id) {
		grant.then(literal(id)
				.executes(ctx -> grant(ctx.getSource(), ctx.getSource().getPlayerOrException(), id))
				.then(argument("player", EntityArgument.player())
						.executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), id))));
	}

	/** Grants a node as the story would ({@code Skills.grant}), or the bare ability when the trees do not know it. */
	private static int grant(CommandSourceStack source, ServerPlayer player, String id) {
		if (SkillTrees.server().node(id) != null) {
			Skills.grant(player, id);
		} else {
			ForceData data = Progression.get(player);
			data.abilities.add(id);
			Progression.commit(player, data);
		}

		source.sendSuccess(() -> Component.literal("Granted " + id + " to " + player.getName().getString()), false);
		return 1;
	}

	private static int use(ServerPlayer player, String id, AbilityAction action) {
		ForceAbility ability = Abilities.get(id);

		if (ability == null) {
			return 0;
		}

		ability.onInput(player, action);
		return 1;
	}

	private ForceM4Feature() {
	}
}
