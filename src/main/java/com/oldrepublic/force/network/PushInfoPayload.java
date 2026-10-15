package com.oldrepublic.force.network;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.force.PushBalance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's live Force Push balance numbers, sent on join so the Force screen tooltips show the
 * real values instead of client-side guesses. Angles are full cone angles in degrees, times in
 * seconds.
 */
public record PushInfoPayload(
		float baseCone, float baseRange, float knockbackMin, float knockbackMax, float costMin, float costMax, float cooldownSeconds,
		float repulseCone, float repulseRange, float repulseKnockback, float staggerSeconds,
		float focusedCone, float focusedRange, float focusedKnockback, float slamDamageMin, float slamDamageMax,
		float deflectRadius, float deflectKnockback, float deflectCooldownSeconds,
		float minChargeSeconds, float masteryRangePerRank, float masteryCostPerRank,
		int unlockLevel, int firstCost, int rechooseCost) implements CustomPacketPayload {
	public static final Type<PushInfoPayload> TYPE = new Type<>(OldRepublicMod.id("push_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PushInfoPayload> CODEC = StreamCodec.of(PushInfoPayload::write, PushInfoPayload::read);

	public static PushInfoPayload from(PushBalance b) {
		return new PushInfoPayload(
				b.baseHalfAngle * 2f, b.baseRange, b.knockbackMin, b.knockbackMax, b.costMin, b.costMax, b.cooldownTicks / 20f,
				b.repulseHalfAngle * 2f, b.repulseRange, b.repulseKnockback, b.repulseStaggerTicks / 20f,
				b.focusedHalfAngle * 2f, b.focusedRange, b.focusedKnockback, b.slamDamageBase, b.slamDamageBase + b.slamDamagePerCharge,
				b.deflectRadius, b.deflectKnockback, Math.round(b.cooldownTicks * b.deflectCooldownMult) / 20f,
				b.minChargeTicks / 20f, b.masteryRangePerRank, b.masteryCostPerRank,
				b.branchUnlockLevel, b.branchFirstCost, b.branchRechooseCost);
	}

	private static void write(RegistryFriendlyByteBuf buf, PushInfoPayload p) {
		float[] floats = {
				p.baseCone, p.baseRange, p.knockbackMin, p.knockbackMax, p.costMin, p.costMax, p.cooldownSeconds,
				p.repulseCone, p.repulseRange, p.repulseKnockback, p.staggerSeconds,
				p.focusedCone, p.focusedRange, p.focusedKnockback, p.slamDamageMin, p.slamDamageMax,
				p.deflectRadius, p.deflectKnockback, p.deflectCooldownSeconds,
				p.minChargeSeconds, p.masteryRangePerRank, p.masteryCostPerRank
		};

		for (float f : floats) {
			buf.writeFloat(f);
		}

		buf.writeVarInt(p.unlockLevel);
		buf.writeVarInt(p.firstCost);
		buf.writeVarInt(p.rechooseCost);
	}

	private static PushInfoPayload read(RegistryFriendlyByteBuf buf) {
		float[] f = new float[22];

		for (int i = 0; i < f.length; i++) {
			f[i] = buf.readFloat();
		}

		return new PushInfoPayload(
				f[0], f[1], f[2], f[3], f[4], f[5], f[6],
				f[7], f[8], f[9], f[10],
				f[11], f[12], f[13], f[14], f[15],
				f[16], f[17], f[18],
				f[19], f[20], f[21],
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
