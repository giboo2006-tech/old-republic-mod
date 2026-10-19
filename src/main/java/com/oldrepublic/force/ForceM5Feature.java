package com.oldrepublic.force;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.fear.Frightened;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.mindtrick.Pacified;
import com.oldrepublic.force.mindtrick.PowerDialogues;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.force.stasis.StasisHolds;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Milestone 5 server and common initialisation for the second power bar and the six Milestone 5
 * powers (section 9): Stasis, Valor, Drain, Fear, Rage and Mind Trick, the control kinds and their
 * status effects (spec MILESTONE_5.md). Owned by W6 powers; the lead calls it from {@code
 * OldRepublicMod} after the Phase 0 contracts, so the worker never edits the mod's entry point.
 *
 * <p>What it wires: the six status effects ({@link ForceMobEffects}); the six abilities; the dialogue condition
 * {@code ability:knows} and the actions {@code mind_trick:use} and {@code fear:use}; the confused test of
 * {@link Allegiances}; the duration rules of the new control kinds (stasis on a boss at 25% and on a force-resistant
 * creature at half, bosses immune to fear and pacify, Steadfast immune to stagger); Nightmare's and Rage's damage
 * modifiers; Drain's reactions (a staggered caster lets go, a drained player pays double); the runtimes' ticks and the
 * Valor and Rage modifier checks; the damage, load and unload hooks; the three particle types; and the operator
 * commands {@code /oldrepublic force grant|use} for the new powers and nodes.
 */
public final class ForceM5Feature {
	/** The six Milestone 5 powers. */
	private static final String[] POWERS = {ForceStasis.ID, ForceValor.ID, ForceDrain.ID, ForceFear.ID, ForceRage.ID, MindTrick.ID};
	/** Their branch nodes, and the Wayfarer's passive, for the grant command. */
	private static final String[] NODES = {ForceStasis.FIELD, ForceStasis.LOCK, ForceValor.SHARED, ForceValor.STEADFAST, ForceDrain.LIFE,
			ForceDrain.ESSENCE, ForceFear.TERROR, ForceFear.NIGHTMARE, ForceRage.UNBOUND, ForceRage.COLD, MindTrick.CONFUSE, MindTrick.MASS,
			SkillIds.WAYFARERS_STRIDE};

	/** Rime-like motes that settle on a held target and hang still, then fall as grit (Force Stasis, 15.2). */
	public static final SimpleParticleType STASIS_RIME = Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id("stasis_rime"),
			FabricParticleTypes.simple());
	/** Dark-red motes drifting from the drained target to the caster's hand (Force Drain). */
	public static final SimpleParticleType DRAIN_MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id("drain_mote"),
			FabricParticleTypes.simple());
	/** Bright dust lifted by Valor's warm gust (Force Valor). */
	public static final SimpleParticleType VALOR_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id("valor_dust"),
			FabricParticleTypes.simple());

	public static void init() {
		ForceMobEffects.init();

		Abilities.register(new ForceStasis());
		Abilities.register(new ForceValor());
		Abilities.register(new ForceDrain());
		Abilities.register(new ForceFear());
		Abilities.register(new ForceRage());
		Abilities.register(new MindTrick());
		PowerDialogues.init();
		Allegiances.setConfusedTest(Pacified::isConfused);

		ControlEffects.registerDurationModifier(ForceM5Feature::durationMultiplier);
		DamageModifiers.register(Frightened::modifyDamage);
		DamageModifiers.register(ForceRage::modifyDamage);
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
				// A staggered caster lets go of the drain (9.5).
				ForceDrain.onInterrupted(target, cause);
			}

			@Override
			public float costMultiplier(ServerPlayer player, String abilityId) {
				return ForceDrain.costMultiplier(player);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(ForceM5Feature::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerPlayerEvents.LEAVE.register(player -> {
			forgetAll(player, false);
			ForceValor.forget(player);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forgetAll(player, true);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forgetAll(player, true));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			StasisHolds.onDamaged(entity, damageTaken);

			if (damageTaken > 0f) {
				Pacified.onDamaged(entity, source);
			}

			ForceRage.onDamaged(entity, source, damageTaken);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Frightened.onLoad(entity);
			Pacified.onLoad(entity);
			StasisHolds.onLoad(entity);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			Frightened.onUnload(entity);
			Pacified.onUnload(entity);
			StasisHolds.onUnload(entity);
		});

		OldRepublicCommands.addSubcommand((context, root) -> root.then(forceCommand()));
	}

	/**
	 * The duration rules of the Milestone 5 control kinds (a {@link ControlEffects.DurationModifier}): {@code stasis} on a
	 * boss at {@code force_stasis.bossMultiplier} and on a {@code #oldrepublic:force_resistant} creature at
	 * {@code resistantMultiplier}; bosses immune to {@code fear} and {@code pacify}; a player under Steadfast Valor immune
	 * to {@code stagger}.
	 */
	public static float durationMultiplier(LivingEntity target, String kind) {
		return switch (kind) {
			case ControlEffects.STASIS -> ForceStasis.durationMultiplier(target, ForceStasis.balance());
			case ControlEffects.FEAR, ControlEffects.PACIFY -> target.is(ConventionalEntityTypeTags.BOSSES) ? 0f : 1f;
			case ControlEffects.STAGGER -> ForceValor.durationMultiplier(target, kind);
			default -> 1f;
		};
	}

	private static void tick(MinecraftServer server) {
		StasisHolds.tick();
		Frightened.tick();
		Pacified.tick();
		ForceDrain.tick();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ForceValor.sync(player);
			ForceRage.sync(player);
		}
	}

	/** Ends what the player does with the new powers (death, disconnect, change of level). */
	public static void forgetAll(ServerPlayer player, boolean feedback) {
		ForceDrain.forget(player, feedback);
	}

	private static void clearAll() {
		StasisHolds.clear();
		Frightened.clear();
		Pacified.clear();
		ForceDrain.clear();
		ForceValor.clear();
	}

	/**
	 * Adds the six powers and their nodes to {@code /oldrepublic force grant <id> [player]} and the powers to
	 * {@code /oldrepublic force use <id> start|release|cancel} (Brigadier merges these into the lead's {@code force}
	 * command, as the Milestone 4 powers do).
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

	private ForceM5Feature() {
	}
}
