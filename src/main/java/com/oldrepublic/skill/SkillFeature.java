package com.oldrepublic.skill;

import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.ability.network.AbilityInfoPayload;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.identity.IdentityEvents;
import com.oldrepublic.skill.network.AssignSlotPayload;
import com.oldrepublic.skill.network.LearnNodePayload;
import com.oldrepublic.skill.network.OpenSkillsPayload;
import com.oldrepublic.skill.network.RespecConfirmPayload;
import com.oldrepublic.skill.network.SetFormPayload;
import com.oldrepublic.skill.network.SkillTreesPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server and common initialisation for the skills feature (skill trees, ability runtime and slots).
 * Called from OldRepublicMod.
 */
public final class SkillFeature {
	/** Dialogue action of the mentors' "Unburden" option: offers the full respec. */
	public static final String UNBURDEN_ACTION = "skills:unburden";
	/** Derived state (open slots, energy modifiers, migration) is re-checked this often. */
	private static final int REFRESH_INTERVAL = 20;

	public static void init() {
		SkillAttachments.init();
		SkillNodeEffects.init();
		NodeInfo.register("skills", NodeInfo::skillValues);
		registerPayloads();

		ResourceManagerReloadListener reloader = SkillTrees::load;
		DataResourceLoader.get().registerReloadListener(SkillTrees.RELOADER_ID, reloader);
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			ServerPlayNetworking.send(player, new SkillTreesPayload(SkillTrees.serverJson()));
			ServerPlayNetworking.send(player, AbilityRuntime.infoPayload(player));

			if (!joined) {
				SkillService.refresh(player);
			}
		});

		ServerPlayerEvents.JOIN.register(player -> {
			SkillService.migrate(player);
			SkillService.refresh(player);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			AbilityRuntime.forget(player);
			CombatTracker.forget(player);
			EnergyRefunds.forget(player);
			SkillAttributes.forget(player);
			SkillNodeEffects.forget(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SkillService.refresh(newPlayer));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> onDamage(entity, source, taken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				AbilityRuntime.cancelAll(player, true);
			}

			SkillService.onKill(entity, source);
			SkillNodeEffects.onKill(entity, source);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> AbilityRuntime.cancelAll(player, true));

		ServerTickEvents.END_SERVER_TICK.register(SkillFeature::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AbilityRuntime.clear();
			CombatTracker.clear();
			EnergyRefunds.clear();
			SkillAttributes.clear();
		});

		IdentityEvents.ALIGNMENT_CHANGED.register(SkillService::onAlignmentChanged);
		IdentityEvents.PATH_CHANGED.register(SkillService::onPathChanged);
		DialogueActions.register(UNBURDEN_ACTION, (player, speaker) -> SkillService.offerRespec(player));

		SkillCommands.init();
	}

	private static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().register(AbilityInputPayload.TYPE, AbilityInputPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LearnNodePayload.TYPE, LearnNodePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AssignSlotPayload.TYPE, AssignSlotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RespecConfirmPayload.TYPE, RespecConfirmPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetFormPayload.TYPE, SetFormPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SkillTreesPayload.TYPE, SkillTreesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilityInfoPayload.TYPE, AbilityInfoPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilityStatePayload.TYPE, AbilityStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenSkillsPayload.TYPE, OpenSkillsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(AbilityInputPayload.TYPE, (payload, context) -> AbilityRuntime.onInput(context.player(), payload.slot(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(LearnNodePayload.TYPE, (payload, context) -> SkillService.learn(context.player(), payload.nodeId()));
		ServerPlayNetworking.registerGlobalReceiver(AssignSlotPayload.TYPE, (payload, context) -> SkillService.assignSlot(context.player(), payload.slot(), payload.abilityId()));
		ServerPlayNetworking.registerGlobalReceiver(RespecConfirmPayload.TYPE, (payload, context) -> SkillService.respec(context.player(), false));
		ServerPlayNetworking.registerGlobalReceiver(SetFormPayload.TYPE, (payload, context) -> SkillService.requestForm(context.player(), payload.formId()));
	}

	/**
	 * Marks both sides as fighting; a player who just entered combat loses the
	 * {@code pathfinders_poise} speed at once rather than at the next refresh.
	 */
	private static void onDamage(LivingEntity entity, DamageSource source, float taken) {
		CombatTracker.onDamage(entity, source, taken);

		if (taken <= 0f) {
			return;
		}

		if (entity instanceof ServerPlayer victim && Skills.rank(victim, SkillIds.PATHFINDERS_POISE) > 0) {
			SkillAttributes.refresh(victim);
		}

		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Skills.rank(attacker, SkillIds.PATHFINDERS_POISE) > 0) {
			SkillAttributes.refresh(attacker);
		}
	}

	private static void tick(MinecraftServer server) {
		AbilityRuntime.tick(server);
		SkillNodeEffects.tick(server);

		if (server.getTickCount() % REFRESH_INTERVAL != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SkillService.migrate(player);
			SkillService.refresh(player);
			AbilityRuntime.refreshCostModifiers(player);
		}
	}

	private SkillFeature() {
	}
}
