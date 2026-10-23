package com.oldrepublic.client.crafting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.saber.SaberSparks;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the crafting effect events (spec 13.3): {@code mod_fit}, {@code bench_craft}
 * (the welding arm's loop and sparks, the done puff and the Masterwork shimmer, the Legendary rise
 * of MILESTONE_5.md 15.3, and the fusion cutter's sparks), {@code salvage}, {@code consumable},
 * {@code scanner_pulse} (with {@link ScannerMarks}) and {@code grenade_player}. The weld loop is
 * keyed by the crafter and stops on done, Masterwork, Legendary or stop, and on its own watchdog (the
 * craft's ticks plus a margin, the level changing, or the listener moving away), so it can never
 * outlive the craft (13.4).
 */
final class CraftingFx {
	/** Welding arc: a hot white-orange. */
	private static final int WELD = 0xFFD9A0;
	/** Masterwork shimmer: warm gold. */
	private static final int GOLD = 0xFFE3A6;
	/** The Legendary glow: a deeper, warmer gold. */
	private static final int LEGENDARY_GLOW = 0xFFC060;
	/** A Legendary craft's rise (MILESTONE_5.md 15.1: one {@code spark_shower} every 2 ticks for 40 ticks). */
	private static final int RISE_TICKS = 40;
	/** Kolto: pale blue. */
	private static final int KOLTO_BLUE = 0x7FC8F0;
	private static final int GRIT = 0x6E655A;
	/** Ticks the cutter's loop lives after its last spark event. */
	private static final int CUT_KEEPALIVE = 10;
	/** Extra ticks a weld may run past its craft time before the watchdog stops it. */
	private static final int WELD_MARGIN = 40;
	private static final double WELD_HEAR_RANGE = 24;

	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Integer, Weld> WELDS = new HashMap<>();
	/** The running Legendary rises, by crafter. */
	private static final Map<Integer, Rise> RISES = new HashMap<>();

	/** One Legendary craft's slow rise of sparks and its glow over the finished item. */
	private static final class Rise {
		final Vec3 pos;
		int age;

		Rise(Vec3 pos) {
			this.pos = pos;
		}
	}

	/** One running weld (a bench craft or a fusion cutter) and its loop. */
	private static final class Weld {
		final Vec3 pos;
		final boolean cutter;
		final WeldLoop loop;
		long deadline;
		int age;

		Weld(Vec3 pos, boolean cutter, WeldLoop loop, long deadline) {
			this.pos = pos;
			this.cutter = cutter;
			this.loop = loop;
			this.deadline = deadline;
		}
	}

	static void init() {
		ClientEffects.register(CraftingEffects.MOD_FIT, CraftingFx::onModFit);
		ClientEffects.register(CraftingEffects.BENCH_CRAFT, CraftingFx::onBenchCraft);
		ClientEffects.register(CraftingEffects.SALVAGE, CraftingFx::onSalvage);
		ClientEffects.register(CraftingEffects.CONSUMABLE, CraftingFx::onConsumable);
		ClientEffects.register(CraftingEffects.SCANNER_PULSE, CraftingFx::onScannerPulse);
		ClientEffects.register(CraftingEffects.GRENADE_PLAYER, CraftingFx::onGrenade);
	}

	// ---------------------------------------------------------------- mod_fit

	private static void onModFit(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		boolean fit = p.variant() == CraftingEffects.MOD_FITTED;
		QuestSounds.ui(fit ? ModSounds.BENCH_MOD_FIT : ModSounds.BENCH_MOD_REMOVE, 0.95f + RANDOM.nextFloat() * 0.1f, 0.9f);
	}

	// ---------------------------------------------------------------- bench_craft

	private static void onBenchCraft(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		switch (p.variant()) {
			case CraftingEffects.CRAFT_START -> startWeld(level, p.entityId(), p.pos(), false, level.getGameTime() + Math.max(1, (int) p.intensity()) + WELD_MARGIN);
			case CraftingEffects.CRAFT_DONE -> {
				stopWeld(p.entityId());
				QuestSounds.at(level, p.pos(), ModSounds.BENCH_DONE, 1f, 1f);
				puff(level, p.pos(), 6);
			}
			case CraftingEffects.CRAFT_MASTERWORK -> {
				stopWeld(p.entityId());
				QuestSounds.at(level, p.pos(), ModSounds.BENCH_MASTERWORK, 1f, 1f);
				puff(level, p.pos(), 4);
				shimmer(level, p.pos());

				if (entity != null && entity == mc.player) {
					CraftingToasts.masterwork();
				}
			}
			case CraftingEffects.CRAFT_STOP -> stopWeld(p.entityId());
			case CraftingEffects.TOOL_CUT -> cut(level, p);
			case CraftingEffects.CRAFT_LEGENDARY -> {
				stopWeld(p.entityId());
				boolean mine = entity != null && entity == mc.player;
				// Intensity 2: the crafter's first Legendary, whose banner brings the same chime and replaces the toast.
				boolean banner = mine && p.intensity() >= 2f;

				if (!banner) {
					QuestSounds.at(level, p.pos(), ModSounds.BENCH_LEGENDARY, 1f, 1f);
				}

				puff(level, p.pos(), 3);
				RISES.put(p.entityId(), new Rise(p.pos()));

				if (mine && !banner) {
					CraftingToasts.legendary();
				}
			}
			default -> {
			}
		}
	}

	private static void startWeld(ClientLevel level, int key, Vec3 pos, boolean cutter, long deadline) {
		Weld old = WELDS.remove(key);

		if (old != null) {
			old.loop.release();
		}

		WeldLoop loop = new WeldLoop(ModSounds.BENCH_WELD, pos, cutter ? 0.55f : 0.8f);
		Minecraft.getInstance().getSoundManager().play(loop);
		WELDS.put(key, new Weld(pos, cutter, loop, deadline));
	}

	private static void stopWeld(int key) {
		Weld weld = WELDS.remove(key);

		if (weld != null) {
			weld.loop.release();
		}
	}

	/** The fusion cutter's sparks at the cut, and its short-lived loop kept alive by each event. */
	private static void cut(ClientLevel level, EffectEventPayload p) {
		Weld weld = WELDS.get(p.entityId());
		long deadline = level.getGameTime() + CUT_KEEPALIVE;

		if (weld == null || !weld.cutter || weld.pos.distanceToSqr(p.pos()) > 4) {
			startWeld(level, p.entityId(), p.pos(), true, deadline);
		} else {
			weld.deadline = deadline;
		}

		if (Vfx.inRange(p.pos())) {
			SaberSparks.sparks(p.pos(), p.dir(), 0.7, Vfx.count(5), WELD, 0.18);
		}
	}

	/** Client tick: the welding arm's sparks, the Legendary rises, and the watchdog that ends every loop. */
	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null || mc.player == null) {
			clear();
			return;
		}

		tickRises(level);

		long now = level.getGameTime();

		for (Iterator<Weld> it = WELDS.values().iterator(); it.hasNext();) {
			Weld weld = it.next();
			weld.age++;

			if (now > weld.deadline || weld.loop.isStopped() || mc.player.position().distanceTo(weld.pos) > WELD_HEAR_RANGE) {
				weld.loop.release();
				it.remove();
				continue;
			}

			// The arm spits a few sparks every other tick, with an uneven rhythm, and a wisp of smoke.
			if (!weld.cutter && weld.age % 2 == 0 && Vfx.inRange(weld.pos)) {
				Vec3 tip = weld.pos.add((RANDOM.nextDouble() - 0.5) * 0.25, 0.05, (RANDOM.nextDouble() - 0.5) * 0.25);
				SaberSparks.sparks(tip, new Vec3(0, 1, 0), 1.1, Vfx.count(RANDOM.nextInt(3) + 1), WELD, 0.12);

				if (weld.age % 10 == 0) {
					level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y + 0.1, tip.z, 0, 0.02, 0);
				}

				if (weld.age % 16 == 0) {
					SaberSparks.flash(tip, WELD);
				}
			}
		}
	}

	/**
	 * The Legendary rise (15.3): bright sparks lifting off the finished item, one every other tick,
	 * under a steady warm glow that climbs with them; 40 ticks, then gone.
	 */
	private static void tickRises(ClientLevel level) {
		for (Iterator<Rise> it = RISES.values().iterator(); it.hasNext();) {
			Rise rise = it.next();
			rise.age++;

			if (rise.age > RISE_TICKS) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(rise.pos)) {
				continue;
			}

			float t = rise.age / (float) RISE_TICKS;

			if (rise.age % 2 == 0) {
				ParticleOptions spark = sparkShower();

				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					level.addParticle(spark, rise.pos.x + (RANDOM.nextDouble() - 0.5) * 0.3, rise.pos.y + 0.05, rise.pos.z + (RANDOM.nextDouble() - 0.5) * 0.3,
							(RANDOM.nextDouble() - 0.5) * 0.03, 0.2 + RANDOM.nextDouble() * 0.08, (RANDOM.nextDouble() - 0.5) * 0.03);
				}
			}

			if (rise.age % 3 == 1) {
				SaberSparks.glow(rise.pos.add(0, 0.12 + 0.35 * t, 0), LEGENDARY_GLOW);
			}
		}
	}

	/** The travel feature's {@code spark_shower}, by id (MILESTONE_5.md 15.2), or vanilla's electric spark if it is missing. */
	private static ParticleOptions sparkShower() {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("spark_shower")) instanceof ParticleOptions options
				? options
				: ParticleTypes.ELECTRIC_SPARK;
	}

	private static void puff(ClientLevel level, Vec3 pos, int base) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			level.addParticle(ParticleTypes.POOF, pos.x + (RANDOM.nextDouble() - 0.5) * 0.4, pos.y + 0.1, pos.z + (RANDOM.nextDouble() - 0.5) * 0.4,
					0, 0.03 + RANDOM.nextDouble() * 0.02, 0);
		}
	}

	/** A short shimmer of gold sparks rising from the finished item. */
	private static void shimmer(ClientLevel level, Vec3 pos) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		SaberSparks.sparks(pos.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.35, Vfx.count(14), GOLD, 0.16);
		SaberSparks.flash(pos.add(0, 0.2, 0), GOLD);

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			level.addParticle(ParticleTypes.END_ROD, pos.x + (RANDOM.nextDouble() - 0.5) * 0.5, pos.y + 0.1, pos.z + (RANDOM.nextDouble() - 0.5) * 0.5,
					0, 0.04 + RANDOM.nextDouble() * 0.03, 0);
		}
	}

	// ---------------------------------------------------------------- salvage

	private static void onSalvage(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		QuestSounds.at(level, p.pos(), ModSounds.BENCH_SALVAGE, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		BlockParticleOption metal = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());
		DustParticleOptions grit = new DustParticleOptions(GRIT, 0.7f);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			double dx = (RANDOM.nextDouble() - 0.5) * 0.2;
			double dz = (RANDOM.nextDouble() - 0.5) * 0.2;
			level.addParticle(metal, p.pos().x, p.pos().y + 0.1, p.pos().z, dx, 0.12, dz);
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			level.addParticle(grit, p.pos().x + (RANDOM.nextDouble() - 0.5) * 0.6, p.pos().y, p.pos().z + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------- consumable

	private static void onConsumable(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		SoundEvent sound = switch (p.variant()) {
			case CraftingEffects.KOLTO -> ModSounds.KOLTO_INJECT;
			case CraftingEffects.STIM -> ModSounds.STIM_INJECT;
			case CraftingEffects.CRASH -> ModSounds.STIM_CRASH;
			default -> ModSounds.MEDPAC_USE;
		};
		QuestSounds.at(level, p.pos(), sound, 0.9f, 1f);

		if (!Vfx.inRange(p.pos()) || p.variant() == CraftingEffects.CRASH) {
			return;
		}

		// A small hiss at the arm: a wisp of vapour; kolto adds a blue mist puff.
		Vec3 arm = p.pos();

		if (entity != null) {
			Vec3 look = entity.getLookAngle().multiply(1, 0, 1);
			Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.35);
			arm = entity.position().add(0, entity.getBbHeight() * 0.55, 0).add(look.scale(0.3)).add(side);
		}

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			level.addParticle(ParticleTypes.CLOUD, arm.x, arm.y, arm.z, (RANDOM.nextDouble() - 0.5) * 0.02, 0.015, (RANDOM.nextDouble() - 0.5) * 0.02);
		}

		if (p.variant() == CraftingEffects.KOLTO) {
			DustParticleOptions mist = new DustParticleOptions(KOLTO_BLUE, 1.1f);

			for (int i = 0, n = Vfx.count(10); i < n; i++) {
				level.addParticle(mist, arm.x + (RANDOM.nextDouble() - 0.5) * 0.5, arm.y + (RANDOM.nextDouble() - 0.3) * 0.5,
						arm.z + (RANDOM.nextDouble() - 0.5) * 0.5, 0, 0, 0);
			}
		}
	}

	// ---------------------------------------------------------------- scanner_pulse

	private static void onScannerPulse(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		QuestSounds.ui(ModSounds.SCANNER_PULSE, 1f, 0.9f);

		if (mc.player == null) {
			return;
		}

		// A single quick brightening of motes along the look direction: no ring, no outline.
		Vec3 eye = mc.player.getEyePosition();
		Vec3 dir = p.dir().lengthSqr() < 1.0E-4 ? mc.player.getLookAngle() : p.dir().normalize();
		DustParticleOptions mote = new DustParticleOptions(0x9CE8FF, 0.5f);

		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double d = 1.5 + i * 0.9;
			Vec3 at = eye.add(dir.scale(d)).add((RANDOM.nextDouble() - 0.5) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.4);
			level.addParticle(mote, at.x, at.y, at.z, dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
		}

		ScannerMarks.start(level, mc.player, Math.max(4, Math.min(64, (int) p.intensity())), Math.max(20, p.variant()));
	}

	// ---------------------------------------------------------------- grenade_player

	private static void onGrenade(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == CraftingEffects.GRENADE_THROW) {
			QuestSounds.at(level, p.pos(), ModSounds.GRENADE_THROW, 0.8f, 0.95f + RANDOM.nextFloat() * 0.1f);
			return;
		}

		QuestSounds.at(level, p.pos(), ModSounds.GRENADE_STUN, 1f, 1f);

		if (Vfx.inRange(p.pos())) {
			// A white flash (a steady glow with flashing off) and a ring-free dust kick.
			SaberSparks.flash(p.pos().add(0, 0.4, 0), 0xFFFFFF);
			SaberSparks.sparks(p.pos().add(0, 0.3, 0), new Vec3(0, 1, 0), 1.2, Vfx.count(10), 0xE8F2FF, 0.22);
			double radius = Math.max(1, p.intensity());

			for (int i = 0, n = Vfx.count(14); i < n; i++) {
				double dx = (RANDOM.nextDouble() - 0.5) * radius;
				double dz = (RANDOM.nextDouble() - 0.5) * radius;
				level.addParticle(ParticleTypes.POOF, p.pos().x + dx, p.pos().y + 0.1, p.pos().z + dz, dx * 0.05, 0.02, dz * 0.05);
			}
		}

		if (mc.player != null && mc.player.position().distanceTo(p.pos()) < p.intensity() + 2) {
			CameraShake.add(0.2f * Vfx.shakeScale());
		}
	}

	/** Stops every loop and rise at once (disconnect, level change). */
	static void clear() {
		for (Weld weld : WELDS.values()) {
			weld.loop.kill();
		}

		WELDS.clear();
		RISES.clear();
	}

	/** A looping sound at a fixed position that fades in, and fades out when released. */
	private static final class WeldLoop extends AbstractTickableSoundInstance {
		private static final float FADE = 0.12f;
		private final float target;
		private final Object level;
		private boolean released;

		WeldLoop(SoundEvent event, Vec3 pos, float volume) {
			super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
			this.level = Minecraft.getInstance().level;
			this.target = volume * ClientConfig.get().modSoundVolume;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.x = pos.x;
			this.y = pos.y;
			this.z = pos.z;
		}

		void release() {
			released = true;
		}

		void kill() {
			released = true;
			stop();
		}

		@Override
		public void tick() {
			if (Minecraft.getInstance().level != level) {
				stop();
				return;
			}

			if (released) {
				volume -= FADE * Math.max(target, 0.1f);

				if (volume <= 0.01f) {
					stop();
				}
			} else if (volume < target) {
				volume = Math.min(target, volume + FADE * target);
			}
		}
	}

	private CraftingFx() {
	}
}
