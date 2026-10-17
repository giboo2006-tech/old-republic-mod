package com.oldrepublic.client.identity;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityEffects;
import com.oldrepublic.identity.IdentityParticles;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers of the identity effect events (toasts, banners, sounds and particles) and the
 * rope fibre particle. See {@code docs/design/identity.md} for the look of each.
 */
final class IdentityFx {
	/** Contextual shifts this small show only a text line. */
	private static final int SMALL_SHIFT = 3;

	static void init() {
		ParticleProviderRegistry.getInstance().register(IdentityParticles.ROPE_FIBRE,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new RopeFibre(level, x, y, z, dx, dy, dz, sprites));

		ClientEffects.register(IdentityEffects.ALIGNMENT_SHIFT, IdentityFx::onAlignmentShift);
		ClientEffects.register(IdentityEffects.REPUTATION_CHANGE, IdentityFx::onReputationChange);
		ClientEffects.register(IdentityEffects.RANK_UP, IdentityFx::onRankUp);
		ClientEffects.register(IdentityEffects.YIELD, IdentityFx::onYield);
		ClientEffects.register(IdentityEffects.SETTLER_FREED, IdentityFx::onSettlerFreed);
	}

	private static String signed(int amount) {
		return amount > 0 ? "+" + amount : Integer.toString(amount);
	}

	private static void onAlignmentShift(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int amount = Math.round(payload.intensity());
		boolean light = amount > 0;
		int flags = payload.variant();
		AlignmentBand band = AlignmentBand.values()[Mth.clamp(flags & IdentityEffects.ORDINAL_MASK, 0, AlignmentBand.values().length - 1)];
		int accent = light ? IdentityStyle.LIGHT : IdentityStyle.DARK;
		String side = light ? "light" : "dark";

		if ((flags & IdentityEffects.FLAG_MINOR) != 0 && Math.abs(amount) <= SMALL_SHIFT) {
			IdentityToasts.line(Component.translatable("alignment.oldrepublic.toast.minor_" + side, signed(amount)), accent);
		} else {
			IdentityToasts.framed(Component.translatable("alignment.oldrepublic.toast." + side, signed(amount)), accent);
			SoundEvent sound = light ? ModSounds.IDENTITY_ALIGNMENT_LIGHT : ModSounds.IDENTITY_ALIGNMENT_DARK;
			QuestSounds.ui(sound, 1f, 0.7f);
		}

		if ((flags & IdentityEffects.FLAG_BAND_CHANGED) != 0) {
			Banners.show(Component.translatable("alignment.oldrepublic.banner.title"),
					Component.translatable("alignment.oldrepublic.banner." + band.name().toLowerCase(Locale.ROOT)), IdentityStyle.band(band));
		}
	}

	private static void onReputationChange(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int amount = Math.round(payload.intensity());
		Faction faction = Faction.values()[Mth.clamp(payload.variant() & IdentityEffects.ORDINAL_MASK, 0, Faction.values().length - 1)];
		Component name = Component.translatable("faction.oldrepublic." + faction.id());

		if ((payload.variant() & IdentityEffects.FLAG_TIER_CHANGED) == 0) {
			IdentityToasts.framed(Component.translatable("faction.oldrepublic.toast", name, signed(amount)), IdentityStyle.faction(faction));
			return;
		}

		// The tier is read from the synced data once it has arrived.
		IdentityToasts.framedAfterSync(() -> {
			ReputationTier tier = client.player == null ? ReputationTier.NEUTRAL : Identity.tier(client.player, faction);
			Component tierName = Component.translatable("faction.oldrepublic.tier." + tier.name().toLowerCase(Locale.ROOT));
			return Component.translatable("faction.oldrepublic.toast.tier", name, signed(amount), tierName);
		}, IdentityStyle.faction(faction));
	}

	private static void onRankUp(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Path path = Path.values()[Mth.clamp(payload.variant(), 0, Path.values().length - 1)];
		int rank = Math.round(payload.intensity());
		int accent = IdentityStyle.path(path);

		if (entity != null && entity == client.player) {
			Banners.show(Component.translatable("rank.oldrepublic.banner.title"),
					Component.translatable("rank.oldrepublic." + path.id() + "." + rank), accent);
			QuestSounds.ui(ModSounds.IDENTITY_RANK_UP, 1f, 0.8f);
		} else {
			QuestSounds.at(level, payload.pos(), ModSounds.IDENTITY_RANK_UP, 0.6f, 1f);
		}

		Vec3 centre = entity != null ? entity.position() : payload.pos();

		if (!Vfx.inRange(centre)) {
			return;
		}

		// A slow column of pale motes rising around the player.
		RandomSource random = level.getRandom();
		int count = Vfx.count(24);

		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 0.5 + random.nextDouble() * 0.4;
			level.addParticle(QuestParticles.FORCE_MOTE, centre.x + Math.cos(angle) * radius, centre.y + random.nextDouble() * 0.4,
					centre.z + Math.sin(angle) * radius, 0, 0.04 + random.nextDouble() * 0.05, 0);
		}
	}

	private static void onYield(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		SoundEvent voice = entity == null ? null : yieldVoice(entity);

		if (voice != null) {
			QuestSounds.at(level, entity.position(), voice, 1f, 1f);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		// Dust kicked up as it drops to its knees.
		RandomSource random = level.getRandom();
		int count = Vfx.count(8);

		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			level.addParticle(ParticleTypes.POOF, pos.x + Math.cos(angle) * 0.4, pos.y + 0.05, pos.z + Math.sin(angle) * 0.4,
					Math.cos(angle) * 0.03, 0.01, Math.sin(angle) * 0.03);
		}
	}

	/** The surrender line of the yielding NPC, chosen by its entity type. */
	private static @Nullable SoundEvent yieldVoice(Entity entity) {
		return switch (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath()) {
			case "poacher_captain" -> ModSounds.CAPTAIN_YIELD;
			case "sith_acolyte" -> ModSounds.ACOLYTE_YIELD;
			default -> null;
		};
	}

	private static void onSettlerFreed(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		// Made to pay: the same cut, but a lower, heavier sigh of relief.
		float pitch = payload.variant() == 1 ? 0.85f : 1f;
		QuestSounds.at(level, pos, ModSounds.SETTLER_FREED, 0.9f, pitch);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		int count = Vfx.count(12);

		for (int i = 0; i < count; i++) {
			level.addParticle(IdentityParticles.ROPE_FIBRE, pos.x + jitter(random, 0.2), pos.y + jitter(random, 0.15), pos.z + jitter(random, 0.2),
					jitter(random, 0.08), 0.04 + random.nextDouble() * 0.06, jitter(random, 0.08));
		}
	}

	private static double jitter(RandomSource random, double spread) {
		return (random.nextDouble() * 2 - 1) * spread;
	}

	/** A short frayed strand of rope: tumbles, falls and settles, fading out at the end. */
	// API-CHECK: SingleQuadParticle exposes protected roll / oRoll / onGround fields, and Mth.TWO_PI exists.
	private static final class RopeFibre extends SingleQuadParticle {
		RopeFibre(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0.45f;
			this.friction = 0.92f;
			this.hasPhysics = true;
			this.lifetime = 24 + random.nextInt(16);
			this.quadSize = 0.05f + random.nextFloat() * 0.03f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = roll;
			float shade = 0.8f + random.nextFloat() * 0.2f;
			setColor(0.72f * shade, 0.58f * shade, 0.38f * shade);
		}

		@Override
		public void tick() {
			oRoll = roll;
			super.tick();

			if (!onGround) {
				roll += 0.25f;
			}

			float left = lifetime - age;
			alpha = Mth.clamp(left / 8f, 0f, 1f);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}
	}

	private IdentityFx() {
	}
}
