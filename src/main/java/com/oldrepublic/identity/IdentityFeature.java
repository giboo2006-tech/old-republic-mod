package com.oldrepublic.identity;

import com.oldrepublic.dialogue.Dialogues;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

/**
 * Server and common initialisation for the identity feature (alignment, reputation, path, rank,
 * trust, dialogue, yielding and Frontier Settlers). Called from OldRepublicMod.
 */
public final class IdentityFeature {
	public static void init() {
		IdentityAttachments.init();
		IdentityEntities.init();
		IdentityParticles.init();

		Dialogues.init();
		IdentityDialogueActions.register();
		IdentityCommands.register();

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Yielding::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				ContextualActs.onDamaged(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register(ContextualActs::onDeath);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> Yielding.onUse(player, level, hand, entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Yielding.onEntityLoad(entity));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Yielding.tick(server);
			ContextualActs.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Yielding.clear();
			ContextualActs.clear();
		});
	}

	private IdentityFeature() {
	}
}
