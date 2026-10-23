package com.oldrepublic.client.planet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.world.FadingLoop;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetEffects;
import com.oldrepublic.planet.PlanetParticles;
import com.oldrepublic.planet.block.VerseStoneBlock;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The verse stones on the client (spec MILESTONE_5.md 3.3.2, 15.2, 15.3, 15.5):
 * <ul>
 * <li><b>Awake</b>: two {@code verse_mote} a second drift up off the stone, and its positional
 * {@code verse_stone.hum} loop plays while the listener is within {@link #HUM_RANGE} blocks. The hum
 * is released when the stone is silenced, when the listener moves {@link #HUM_RANGE} blocks away and
 * when the stone stops ticking (unloaded or gone); it stops with the level.</li>
 * <li><b>The slowing hum</b> (effect variant 0): the air around the stone bends outward once (one
 * wide {@code air_ripple}), a few motes drift off, {@code verse_stone.pulse}.</li>
 * <li><b>A channel</b> (1 to 4): the positional {@code verse_stone.channel} loop from its start until
 * it completes or breaks (or, unseen, a second passes without progress); two motes a tick stream
 * from the stone to the channeller's hand; the local channeller sees a ring of 40 dots around the
 * crosshair fill with the progress (the HUD's "verse stone channel ring"). The Force's answer (4)
 * sends a pulse up the stream and the ring jumps.</li>
 * <li><b>Silenced</b> (5): the hum and the channel stop, {@code verse_stone.silence} plays, and the
 * faint glow drains: 30 motes fall from the stone as grit over 20 ticks.</li>
 * </ul>
 * Every loop is killed on disconnect and stops with the level on a dimension change. The stone's
 * own tells always play (they say which stones still sing), whatever {@code planetAmbience} says.
 */
final class VerseStoneClient {
	static final VerseStoneClient INSTANCE = new VerseStoneClient();

	private static final double HUM_RANGE = 24;
	private static final float HUM_VOLUME = 0.6f;
	private static final int HUM_FADE_TICKS = 30;
	/** A hum whose stone has not ticked for this long (unloaded, gone) is released. */
	private static final int HUM_STALE_TICKS = 5;
	private static final float CHANNEL_VOLUME = 0.8f;
	private static final int CHANNEL_FADE_TICKS = 10;
	/** A channel heard of no more for this long has ended out of sight. */
	private static final int CHANNEL_STALE_TICKS = 20;
	private static final int RING_SEGMENTS = 40;
	private static final float RING_RADIUS = 12f;
	private static final int RING_COLOR = 0xEADFB4;
	private static final int RING_LEAD_COLOR = 0xFFF6DA;

	private final Map<BlockPos, Hum> hums = new HashMap<>();
	private final Map<BlockPos, Channel> channels = new HashMap<>();
	private long ticks;
	private @Nullable Object lastLevel;

	/** A stone's hum loop and the tick its stone last ticked. */
	private static final class Hum {
		final FadingLoop loop;
		long seen;

		Hum(FadingLoop loop, long seen) {
			this.loop = loop;
			this.seen = seen;
		}
	}

	/** A running channel: its loop, who channels, the last progress heard and when. */
	private static final class Channel {
		final Vec3 stone;
		final FadingLoop loop;
		final int channeller;
		float progress;
		long heard;

		Channel(Vec3 stone, FadingLoop loop, int channeller, float progress, long heard) {
			this.stone = stone;
			this.loop = loop;
			this.channeller = channeller;
			this.progress = progress;
			this.heard = heard;
		}
	}

	private VerseStoneClient() {
	}

	// ---- the block's client ticker ----

	/** A verse stone's client tick ({@code PlanetBlockAmbience.Listener#verseStone}): its motes and its hum. */
	void stone(BlockState state, Level level, BlockPos pos) {
		Minecraft client = Minecraft.getInstance();

		if (!(level instanceof ClientLevel clientLevel) || client.player == null || level != client.level) {
			return;
		}

		syncLevel(client);
		Vec3 centre = Vec3.atCenterOf(pos);
		Hum hum = hums.get(pos);

		if (!state.getValue(VerseStoneBlock.AWAKE)) {
			if (hum != null) {
				hum.loop.release();
				hums.remove(pos);
			}

			return;
		}

		boolean near = client.player.position().distanceToSqr(centre) <= HUM_RANGE * HUM_RANGE;

		if (hum != null && (hum.loop.isStopped() || !near)) {
			hum.loop.release();
			hums.remove(pos);
			hum = null;
		}

		if (near && hum == null) {
			hums.put(pos.immutable(), new Hum(FadingLoop.play(ModSounds.VERSE_STONE_HUM, centre, HUM_VOLUME, HUM_FADE_TICKS), ticks));
		} else if (hum != null) {
			hum.seen = ticks;
		}

		RandomSource random = clientLevel.getRandom();

		// Two motes a second while awake.
		if (random.nextInt(10) == 0 && Vfx.inRange(centre)) {
			PlanetAmbience.repeat(random, 1, () -> {
				Vec3 at = centre.add((random.nextDouble() - 0.5) * 1.1, -0.3 + random.nextDouble() * 1.4, (random.nextDouble() - 0.5) * 1.1);
				clientLevel.addParticle(PlanetParticles.VERSE_MOTE, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.01, 0.012 + random.nextDouble() * 0.01,
						(random.nextDouble() - 0.5) * 0.01);
			});
		}
	}

	// ---- the verse_stone effect ----

	void effect(Minecraft client, ClientLevel level, EffectEventPayload p) {
		syncLevel(client);
		Vec3 pos = p.pos();
		BlockPos key = BlockPos.containing(pos);

		switch (p.variant()) {
			case PlanetEffects.VERSE_HUM -> hum(level, pos);
			case PlanetEffects.VERSE_CHANNEL_START, PlanetEffects.VERSE_CHANNEL_PROGRESS -> heard(key, pos, p.entityId(), p.intensity());
			case PlanetEffects.VERSE_FORCE -> {
				heard(key, pos, p.entityId(), p.intensity());
				forcePulse(level, pos, p.entityId());
			}
			case PlanetEffects.VERSE_CHANNEL_BROKEN -> {
				endChannel(key);
				scatter(level, pos);
			}
			case PlanetEffects.VERSE_SILENCED -> silenced(level, key, pos);
			default -> {
			}
		}
	}

	private void hum(ClientLevel level, Vec3 pos) {
		sound(level, pos, ModSounds.VERSE_STONE_PULSE, 0.9f, 0.95f + level.getRandom().nextFloat() * 0.1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		// The air bends outward once.
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(ForceParticles.AIR_RIPPLE, pos.x, pos.y + 0.4, pos.z, 0, 0, 0);

		if (particle instanceof ForceParticle ripple) {
			ripple.size(1.2f, 7f).alpha(0.16f).life(18);
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 out = PlanetAmbience.randomDirection(random).multiply(1, 0.3, 1).normalize();
			Vec3 at = pos.add(out.scale(0.6));
			level.addParticle(PlanetParticles.VERSE_MOTE, at.x, at.y, at.z, out.x * 0.05, 0.01 + out.y * 0.02, out.z * 0.05);
		}
	}

	/** A start or progress event: the channel's loop starts if it is not playing, its progress is noted. */
	private void heard(BlockPos key, Vec3 pos, int channeller, float progress) {
		Channel channel = channels.get(key);

		if (channel != null && (channel.channeller != channeller || channel.loop.isStopped())) {
			channel.loop.release();
			channels.remove(key);
			channel = null;
		}

		if (channel == null) {
			channels.put(key.immutable(), new Channel(pos, FadingLoop.play(ModSounds.VERSE_STONE_CHANNEL, pos, CHANNEL_VOLUME, CHANNEL_FADE_TICKS), channeller,
					progress, ticks));
		} else {
			channel.progress = Math.max(channel.progress, progress);
			channel.heard = ticks;
		}
	}

	private void endChannel(BlockPos key) {
		Channel channel = channels.remove(key);

		if (channel != null) {
			channel.loop.release();
		}
	}

	/** The Force answers: a bright run of motes up the stream and a higher pulse. */
	private static void forcePulse(ClientLevel level, Vec3 pos, int channeller) {
		sound(level, pos, ModSounds.VERSE_STONE_PULSE, 1f, 1.25f);
		Entity entity = level.getEntity(channeller);

		if (entity == null || !Vfx.inRange(pos)) {
			return;
		}

		Vec3 hand = hand(entity, pos);
		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			streamMote(level, pos, hand, random, random.nextDouble() * 0.4);
		}
	}

	/** A broken channel: the stream's motes scatter back into the stone. */
	private static void scatter(ClientLevel level, Vec3 pos) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 at = pos.add(PlanetAmbience.randomDirection(random).scale(1.2));
			Vec3 v = pos.subtract(at).scale(0.06);
			level.addParticle(PlanetParticles.VERSE_MOTE, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** Silenced: the loops stop and the glow drains as 30 motes fall as grit over 20 ticks. */
	private void silenced(ClientLevel level, BlockPos key, Vec3 pos) {
		endChannel(key);
		Hum hum = hums.remove(key);

		if (hum != null) {
			hum.loop.release();
		}

		sound(level, pos, ModSounds.VERSE_STONE_SILENCE, 1f, 1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, PlanetBlocks.VERSE_STONE.defaultBlockState());
		int[] spawned = new int[1];
		Bursts.add(level, 30, 20, random -> {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 1.2, 0.2 + random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 1.2);

			// Half pale motes going out as they fall, half the stone's own grit.
			if (spawned[0]++ % 2 == 0) {
				level.addParticle(PlanetParticles.VERSE_MOTE, at.x, at.y, at.z, 0, -0.03 - random.nextDouble() * 0.02, 0);
			} else {
				level.addParticle(grit, at.x, at.y, at.z, 0, 0, 0);
			}
		});
	}

	// ---- client tick ----

	void tick(Minecraft client) {
		syncLevel(client);
		ClientLevel level = client.level;

		if (level == null || client.player == null) {
			return;
		}

		ticks++;
		Iterator<Map.Entry<BlockPos, Hum>> humIt = hums.entrySet().iterator();

		while (humIt.hasNext()) {
			Hum hum = humIt.next().getValue();

			if (hum.loop.isStopped() || ticks - hum.seen > HUM_STALE_TICKS
					|| client.player.position().distanceToSqr(hum.loop.position()) > HUM_RANGE * HUM_RANGE) {
				hum.loop.release();
				humIt.remove();
			}
		}

		RandomSource random = level.getRandom();
		Iterator<Channel> channelIt = channels.values().iterator();

		while (channelIt.hasNext()) {
			Channel channel = channelIt.next();
			Entity entity = level.getEntity(channel.channeller);

			if (channel.loop.isStopped() || ticks - channel.heard > CHANNEL_STALE_TICKS || entity == null || !entity.isAlive()) {
				channel.loop.release();
				channelIt.remove();
				continue;
			}

			if (Vfx.inRange(channel.stone)) {
				Vec3 hand = hand(entity, channel.stone);
				PlanetAmbience.repeat(random, 2, () -> streamMote(level, channel.stone, hand, random, random.nextDouble() * 0.5));
			}
		}
	}

	/** Disconnect: every loop stops at once. */
	void reset() {
		hums.values().forEach(hum -> hum.loop.kill());
		channels.values().forEach(channel -> channel.loop.kill());
		hums.clear();
		channels.clear();
		lastLevel = null;
	}

	/** A new level: the loops stop with the old one, so they are only forgotten. */
	private void syncLevel(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			hums.values().forEach(hum -> hum.loop.kill());
			channels.values().forEach(channel -> channel.loop.kill());
			hums.clear();
			channels.clear();
		}
	}

	// ---- the channel ring ----

	/**
	 * The local player's channel progress (0 to 1) at {@code partialTick}, moving on between the
	 * server's events at the channel's pace; -1 when they channel no stone.
	 */
	float ownProgress(float partialTick) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return -1f;
		}

		int self = client.player.getId();

		for (Channel channel : channels.values()) {
			if (channel.channeller == self) {
				float step = 1f / Math.max(1, PlanetBalance.get().silenceTicks);
				float since = Math.min(5f, ticks - channel.heard + partialTick);
				return Mth.clamp(channel.progress + since * step, 0f, 1f);
			}
		}

		return -1f;
	}

	/** The ring of dots around the crosshair while the local player channels (after the crosshair). */
	static void extractRing(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.gui.hud.isHidden()) {
			return;
		}

		float progress = INSTANCE.ownProgress(deltaTracker.getGameTimeDeltaPartialTick(true));

		if (progress < 0f) {
			return;
		}

		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		float filled = progress * RING_SEGMENTS;
		int lead = Math.min(RING_SEGMENTS - 1, (int) filled);

		for (int i = 0; i < RING_SEGMENTS; i++) {
			double angle = -Math.PI / 2 + Math.PI * 2 * i / RING_SEGMENTS;
			int x = cx + (int) Math.round(Math.cos(angle) * RING_RADIUS);
			int y = cy + (int) Math.round(Math.sin(angle) * RING_RADIUS);
			float part = Mth.clamp(filled - i, 0f, 1f);
			int alpha = (int) (0x30 + (0xD0 - 0x30) * part);
			int rgb = i == lead && progress < 1f ? RING_LEAD_COLOR : RING_COLOR;
			graphics.fill(x, y, x + 1, y + 1, alpha << 24 | rgb);
		}
	}

	// ---- helpers ----

	/** Roughly where {@code entity}'s hand is, reaching toward the stone at {@code stone}. */
	private static Vec3 hand(Entity entity, Vec3 stone) {
		Vec3 chest = entity.position().add(0, entity.getBbHeight() * 0.55, 0);
		Vec3 toward = stone.subtract(chest);
		return toward.lengthSqr() < 1.0E-4 ? chest : chest.add(toward.normalize().scale(0.35));
	}

	/** One mote of the stream, {@code along} of the way from the stone, drifting on to the hand. */
	private static void streamMote(ClientLevel level, Vec3 stone, Vec3 hand, RandomSource random, double along) {
		Vec3 path = hand.subtract(stone);
		Vec3 at = stone.add(path.scale(along)).add((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25);
		// Motes slow by 3% a tick, so this carries them about the rest of the way over their life.
		Vec3 v = hand.subtract(at).scale(1 / 15.0);
		level.addParticle(PlanetParticles.VERSE_MOTE, at.x, at.y, at.z, v.x, v.y, v.z);
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, scaled, pitch, false);
		}
	}
}
