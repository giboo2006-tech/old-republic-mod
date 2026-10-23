package com.oldrepublic.client.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.particle.DrainMoteParticle;
import com.oldrepublic.client.force.particle.DustMoteParticle;
import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.client.force.particle.StasisRimeParticle;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.force.valor.ForceValor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Milestone 5 client initialisation for the six powers' effects and sounds (section 9, 15): Stasis, Valor, Drain, Fear,
 * Rage and Mind Trick, their three particle types and Rage's vignette. The poses are drawn through
 * {@link PowerAnimations}; the second bar and the Focus key live in {@code client.ability.AbilityInput} and
 * {@code client.hud.ForceHud}. Owned by W6 powers; the lead calls it from {@code OldRepublicModClient}, so the worker never
 * edits the client entry point.
 *
 * <p>Every loop and held state also ends here when the level changes or the client disconnects.
 */
public final class ForceM5Client {
	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		ForceParticleProviders.register(ForceM5Feature.STASIS_RIME, StasisRimeParticle::new);
		ForceParticleProviders.register(ForceM5Feature.DRAIN_MOTE, DrainMoteParticle::new);
		ForceParticleProviders.register(ForceM5Feature.VALOR_DUST, DustMoteParticle::new);

		ClientEffects.register(ForceStasis.EFFECT, StasisFx::onEvent);
		ClientEffects.register(ForceValor.EFFECT, ValorFx::onEvent);
		ClientEffects.register(ForceDrain.EFFECT, DrainFx::onEvent);
		ClientEffects.register(ForceFear.EFFECT, FearFx::onEvent);
		ClientEffects.register(ForceRage.EFFECT, RageFx::onEvent);
		ClientEffects.register(MindTrick.EFFECT, MindTrickFx::onEvent);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, OldRepublicMod.id("rage_vignette"), RageFx::extractVignette);

		ClientTickEvents.END_CLIENT_TICK.register(ForceM5Client::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clear();
			lastLevel = null;
		});
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// A new level (dimension change or join): entity ids are no longer valid.
			lastLevel = mc.level;
			clear();
		}

		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			return;
		}

		StasisFx.tick(level);
		ValorFx.tick(level);
		DrainFx.tick(level);
		FearFx.tick(level);
		RageFx.tick(mc, level);
		MindTrickFx.tick(level);
	}

	private static void clear() {
		StasisFx.clear();
		ValorFx.clear();
		DrainFx.clear();
		FearFx.clear();
		RageFx.clear();
		MindTrickFx.clear();
	}

	private ForceM5Client() {
	}
}
