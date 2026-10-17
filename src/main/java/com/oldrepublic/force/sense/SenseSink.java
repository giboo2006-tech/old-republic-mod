package com.oldrepublic.force.sense;

import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestEffects;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where the results of a Force Sense cast go: {@link #NETWORK} sends them to the players (the
 * reveal to the caster, the gesture to everyone tracking the caster, holocron direction to the
 * caster only); game tests record them instead. Direction only, never a position (spec 4.3).
 */
public interface SenseSink {
	/** The caster's reveal ({@code sense_reveal}). */
	void reveal(ServerPlayer caster, SenseRevealPayload payload);

	/** The cast gesture for everyone tracking the caster ({@code force_sense} variant 0). */
	void gesture(ServerPlayer caster, int points);

	/** The cached holocron direction at the cast ({@code force_sense} variant 1, caster only). */
	void holocron(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind);

	/** A fresh holocron direction shortly after the cast ({@code calling} variant 4, caster only). */
	void followUp(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind);

	/** The network. */
	SenseSink NETWORK = new SenseSink() {
		@Override
		public void reveal(ServerPlayer caster, SenseRevealPayload payload) {
			ServerPlayNetworking.send(caster, payload);
		}

		@Override
		public void gesture(ServerPlayer caster, int points) {
			EffectEvents.toTracking(caster, ForceEffects.SENSE, caster.getEyePosition(), null, caster.getId(), points, ForceEffects.SENSE_GESTURE);
		}

		@Override
		public void holocron(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind) {
			EffectEvents.toPlayer(caster, ForceEffects.SENSE, caster.getEyePosition(), dir, kind.ordinal(), closeness, ForceEffects.SENSE_HOLOCRON);
		}

		@Override
		public void followUp(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind) {
			EffectEvents.toPlayer(caster, QuestEffects.CALLING, caster.getEyePosition(), dir, kind.ordinal(), closeness, QuestEffects.CALLING_HOLOCRON);
		}
	};
}
