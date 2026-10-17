package com.oldrepublic.saber;

import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.network.SaberActionPayload;
import com.oldrepublic.saber.network.ToggleSaberPayload;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;
import com.oldrepublic.skill.FormChanges;
import com.oldrepublic.skill.NodeInfo;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server and common initialisation for the saber feature. Called from OldRepublicMod. */
public final class SaberFeature {
	public static void init() {
		SaberComponents.init();
		SaberItems.init();
		SaberParticles.init();
		SaberGuard.init();
		LightsaberWorkbenchMenu.init();
		SaberThrow.init();

		PayloadTypeRegistry.serverboundPlay().register(ToggleSaberPayload.TYPE, ToggleSaberPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaberActionPayload.TYPE, SaberActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ToggleSaberPayload.TYPE, (payload, context) -> SaberToggle.toggleHeld(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SaberActionPayload.TYPE, (payload, context) -> SaberTechniques.onAction(context.player(), payload));

		CombatHooks.setProjectileGuard(SaberGuard::onProjectile);
		ForceReactions.register(new SaberForceReactions());
		SaberNodes.init();
		FormChanges.setHandler(SaberForms::request);
		NodeInfo.register("saber", SaberPassives::nodeInfo);
		AttackEntityCallback.EVENT.register(SaberCombat::onAttack);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> SaberCombat.allowDamage(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			onAfterDamage(entity, source, damageTaken);
			SaberCombat.afterDamage(entity, damageTaken);
		});
		// Before the death drops: a thrown saber returns to the inventory first.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				SaberThrow.recall(player);
			}

			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Spar.endWithoutResult(entity);
			SaberNodes.clearDominion(entity);

			if (entity instanceof ServerPlayer player) {
				retractHeld(player);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				SaberForms.tick(player);
				SaberTechniques.tick(player);
				SaberCombat.tick(player);
				SaberGuard.tick(player);
			}

			SaberTechniques.tickFear();
			Spar.tick(server);
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				ItemStack stack = item.getItem();

				// A dropped saber is never "in flight", and never stays lit.
				if (stack.has(SaberComponents.SABER_THROWN)) {
					stack.remove(SaberComponents.SABER_THROWN);
				}

				if (SaberComponents.isIgnited(stack)) {
					SaberToggle.setIgnited(stack, false, level.getGameTime());
					SaberToggle.playToggleSound(level, entity, false);
				}
			}
		});

		ServerPlayerEvents.LEAVE.register(player -> {
			SaberThrow.recall(player);
			Spar.endWithoutResult(player);
			retractHeld(player);
			forget(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			forget(oldPlayer);
			SaberThrow.clearAll(newPlayer);
			retractHeld(newPlayer);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			SaberThrow.recall(player);
			Spar.endWithoutResult(player);
			SaberTechniques.forget(player);
			SaberForms.forget(player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SaberThrow.recallAll(server);
			Spar.endAll();
		});

		SaberCommands.init();
	}

	/** Sizzle and sparks where an ignited blade (a player's or a saber NPC's) lands a direct hit. */
	private static void onAfterDamage(LivingEntity target, DamageSource source, float damageTaken) {
		if (damageTaken <= 0 || !source.isDirect() || !(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}

		ItemStack weapon = attacker.getMainHandItem();

		if (!SaberComponents.isUsable(weapon)) {
			return;
		}

		Vec3 hitPoint = target.getBoundingBox().getCenter().lerp(attacker.getEyePosition(), 0.25);
		EffectEvents.toTracking(target, SaberEffects.HIT, hitPoint, attacker.getLookAngle(), target.getId(), damageTaken,
				SaberComponents.config(weapon).color().ordinal());
	}

	/** Silently retracts sabers in both hands (logout, death, respawn with kept inventory). */
	private static void retractHeld(Player player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (SaberComponents.isIgnited(stack)) {
				SaberToggle.setIgnited(stack, false, player.level().getGameTime());
			}
		}
	}

	private static void forget(ServerPlayer player) {
		SaberGuard.forget(player);
		SaberCombat.forget(player);
		SaberTechniques.forget(player);
		SaberForms.forget(player);
		SaberNodes.forget(player);
	}

	private SaberFeature() {
	}
}
