package com.oldrepublic.saber;

import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.network.ToggleSaberPayload;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

		PayloadTypeRegistry.serverboundPlay().register(ToggleSaberPayload.TYPE, ToggleSaberPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ToggleSaberPayload.TYPE, (payload, context) -> SaberToggle.toggleHeld(context.player()));

		CombatHooks.setProjectileGuard(SaberGuard::onProjectile);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !SaberGuard.consumeCancel(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> onAfterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				retractHeld(player);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				SaberGuard.tick(player);
			}
		});

		// Dropped or death-dropped sabers never stay lit.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && SaberComponents.isIgnited(item.getItem())) {
				SaberToggle.setIgnited(item.getItem(), false, level.getGameTime());
				SaberToggle.playToggleSound(level, entity, false);
			}
		});

		ServerPlayerEvents.LEAVE.register(player -> {
			retractHeld(player);
			SaberGuard.forget(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			SaberGuard.forget(oldPlayer);
			retractHeld(newPlayer);
		});

		SaberCommands.init();
	}

	/** Sizzle and sparks where an ignited blade lands a direct hit. */
	private static void onAfterDamage(LivingEntity target, DamageSource source, float damageTaken) {
		if (damageTaken <= 0 || !source.isDirect() || !(source.getEntity() instanceof ServerPlayer attacker)) {
			return;
		}

		ItemStack weapon = attacker.getMainHandItem();

		if (!SaberComponents.isIgnited(weapon)) {
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

	private SaberFeature() {
	}
}
