package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.Bolt;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Flare;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Spark;
import com.oldrepublic.client.force.energy.Stroke;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Lightning visuals and sounds. Each caster's channel lives here between the charge event and the stop event; the
 * light itself is drawn by {@link EnergyRender}.
 *
 * <ul>
 * <li>Charge (the tell): tiny arcs snap between the fingertips, a glare grows in the hand, and from the third tick feelers
 * of lightning probe the air in front of it while sparks drip from the fingers.</li>
 * <li>Arcs: every tick fresh strokes leave each casting hand (both without a saber), branching, zigzagging bolts with a
 * white-hot core in a violet glow that fan out and meet again on the target, each striking hard and dying in two or three
 * ticks, so the whole channel crackles. The hands glare; where the bolts land the target is lit, small arcs crawl over its
 * body and now and then run down to the ground, and sparks spray off it and bounce on the floor. Chain Lightning leaps on
 * from the first target to the others. Without a target the bolts strike the block in front, crawl over its face and
 * scorch it. A lit saber that takes the arcs splashes them aside in a spray of sparks.</li>
 * <li>Damage ticks: a hard flash at the contact, a burst of sparks and a breath of scorch smoke.</li>
 * <li>Stop: every bolt goes at once; the hands' glare dies away over a few ticks, a last spark or two snaps at the
 * fingers, and smoke curls up from the hands and from the scorched target.</li>
 * </ul>
 *
 * <p>With flashing effects off the strokes hold a steady 0.85, come and go gently and the glare does not flicker. Watchdogs:
 * the arcs are hidden when no damage-tick refresh has arrived for {@link #REFRESH_TICKS} (they come back with the next one),
 * and the whole channel is forgotten after {@link #LOST_TICKS} without one, or when the caster dies, is removed or the level
 * changes.
 */
final class LightningFx {
	/** Spec 8.5: no refresh for this many ticks hides the arcs. */
	private static final int REFRESH_TICKS = 6;
	/** No refresh for this long: the stop event was lost; forget the channel. */
	private static final int LOST_TICKS = 40;
	/** Charge (8 ticks) plus slack before a charge that never turned into arcs is dropped. */
	private static final int CHARGE_WATCHDOG = 8 + 12;
	/** Longest channel (charge 8 + arcs 80) plus slack, for the pose watchdog. */
	static final int CHANNEL_WATCHDOG = 8 + 80 + 20;
	private static final int CHARGE_TICKS = 8;
	private static final float CASTER_SHAKE = 0.03f;
	private static final double RANGE = 9.0;
	private static final int IMPACT_SOUND_WINDOW = 10;
	/** A guarded hit keeps the arcs on the blade this long. */
	private static final int GUARD_TICKS = 7;
	/** Ticks the last crackle at the fingers lasts after a stop. */
	private static final int AFTER_TICKS = 7;
	/** {@link EnergyRender} owner kind of a channel's bolts and glare. */
	private static final int OWNER_KIND = 1;
	/** Scorch smoke: dark, a little blue. */
	private static final float[] SMOKE = {0.2f, 0.2f, 0.25f};

	private static final class Channel {
		final int casterId;
		final long owner;
		int rank;
		boolean arcs;
		final long started;
		long lastRefresh;
		final int[] targets = {-1, -1, -1};
		/** Until which tick each target's arcs end on its saber instead of its body. */
		final long[] guardedUntil = new long[3];
		/** Where the arcs strike when there is no target (the last damage tick's block). */
		@Nullable Vec3 contact;
		/** The last two impact sounds (rate limit: at most 2 per 10 ticks). */
		long impactA = -100;
		long impactB = -100;
		long lastBlockSound = -100;

		Channel(int casterId, int rank, long now) {
			this.casterId = casterId;
			this.owner = EnergyRender.owner(OWNER_KIND, casterId);
			this.rank = rank;
			this.started = now;
			this.lastRefresh = now;
		}
	}

	/** The last crackle at a stopped caster's fingers. */
	private record After(int casterId, boolean both, boolean left, long until) {
	}

	private static final Map<Integer, Channel> CHANNELS = new HashMap<>();
	private static final Map<Integer, After> AFTER = new HashMap<>();

	/** {@code force_lightning}: charge, arcs or stop. Entity = caster, intensity = mastery rank. */
	static void onPhase(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		int phase = ForceEffects.lightningPhase(payload.variant());
		int rank = Math.max(0, Math.round(payload.intensity()));
		long now = PowerAnimations.ticks();

		switch (phase) {
			case ForceEffects.LIGHTNING_CHARGE -> {
				ForceFxUtil.play(level, ForceFxUtil.chest(caster), ModSounds.FORCE_LIGHTNING_CHARGE, 0.8f, ForceFxUtil.pitchVariance(0.04f));
				startChannel(caster, rank, caster == mc.player);
			}
			case ForceEffects.LIGHTNING_ARCS -> {
				Channel channel = CHANNELS.get(caster.getId());

				if (channel == null) {
					// Joined mid-cast or the charge event was missed.
					startChannel(caster, rank, false);
					channel = CHANNELS.get(caster.getId());
				}

				channel.arcs = true;
				channel.rank = rank;
				channel.lastRefresh = now;
				PowerAnimations.loop(caster, PowerAnimations.Kind.LIGHTNING, ModSounds.FORCE_LIGHTNING_LOOP, 0.7f, 1f);
			}
			case ForceEffects.LIGHTNING_STOP -> stop(level, caster);
			default -> {
			}
		}
	}

	/** Starts the pose and the channel; for the own player ({@code own}) it confirms the prediction. */
	private static void startChannel(LivingEntity caster, int rank, boolean own) {
		Channel existing = CHANNELS.get(caster.getId());

		if (own && existing != null && !existing.arcs) {
			existing.rank = rank;
			PowerAnimations.confirm(caster.getId(), PowerAnimations.Kind.LIGHTNING);
			return;
		}

		if (existing != null) {
			EnergyRender.kill(existing.owner);
		}

		CHANNELS.put(caster.getId(), new Channel(caster.getId(), rank, PowerAnimations.ticks()));
		AFTER.remove(caster.getId());

		if (!PowerAnimations.isActive(caster.getId(), PowerAnimations.Kind.LIGHTNING)) {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.LIGHTNING, -1, CHANNEL_WATCHDOG);
		}
	}

	/** Own prediction at the key press: the charge pose and crackle before the server answers. */
	static void predict(LivingEntity caster) {
		if (CHANNELS.containsKey(caster.getId())) {
			return;
		}

		CHANNELS.put(caster.getId(), new Channel(caster.getId(), 0, PowerAnimations.ticks()));
		AFTER.remove(caster.getId());
		PowerAnimations.startPredicted(caster.getId(), PowerAnimations.Kind.LIGHTNING, -1, CHANNEL_WATCHDOG);
	}

	/** {@code force_lightning_target}: entity = target, variant = (caster, slot), intensity 1 on, 0 off. */
	static void onTarget(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Channel channel = CHANNELS.get(ForceEffects.casterOf(payload.variant()));
		int slot = ForceEffects.valueOf(payload.variant());

		if (channel == null || slot >= channel.targets.length) {
			return;
		}

		if (payload.intensity() > 0.5f) {
			channel.targets[slot] = payload.entityId();
		} else if (channel.targets[slot] == payload.entityId()) {
			channel.targets[slot] = -1;
		}
	}

	/** {@code force_lightning_hit}: a damage tick. Entity = caster, pos = contact, intensity = hits, variant = blocked mask. */
	static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		Channel channel = CHANNELS.get(caster.getId());
		long now = PowerAnimations.ticks();

		if (channel == null) {
			return;
		}

		channel.lastRefresh = now;
		channel.arcs = true;
		int hits = Math.round(payload.intensity());
		channel.contact = hits == 0 ? payload.pos() : null;

		if (now - channel.impactB >= IMPACT_SOUND_WINDOW) {
			ForceFxUtil.play(level, payload.pos(), ModSounds.FORCE_LIGHTNING_IMPACT, 0.7f, ForceFxUtil.pitchVariance(0.08f));
			channel.impactB = channel.impactA;
			channel.impactA = now;
		}

		int blocked = payload.variant();

		for (int slot = 0; slot < channel.targets.length; slot++) {
			Entity target = channel.targets[slot] >= 0 ? level.getEntity(channel.targets[slot]) : null;

			if (target == null) {
				continue;
			}

			boolean guarded = (blocked >> slot & 1) != 0;

			if (guarded) {
				channel.guardedUntil[slot] = EnergyRender.ticks() + GUARD_TICKS;

				if (now - channel.lastBlockSound >= IMPACT_SOUND_WINDOW) {
					ForceFxUtil.play(level, EnergyKit.chest(target), ModSounds.SABER_LIGHTNING_BLOCK, 0.8f, ForceFxUtil.pitchVariance(0.05f));
					channel.lastBlockSound = now;
				}
			}

			Vec3 at = contactPoint(caster, target, guarded);

			if (Vfx.inRange(at)) {
				strike(level, channel, caster, target, at, guarded);
			}
		}

		if (hits == 0 && Vfx.inRange(payload.pos())) {
			groundStrike(level, payload.pos(), caster);
		}
	}

	/** The crackle of every channel, the charge tells and the last crackle after a stop. Called every client tick. */
	static void tick(Minecraft mc, ClientLevel level) {
		if (!AFTER.isEmpty()) {
			afterTick(level);
		}

		if (CHANNELS.isEmpty()) {
			return;
		}

		long now = PowerAnimations.ticks();
		Iterator<Channel> it = CHANNELS.values().iterator();

		while (it.hasNext()) {
			Channel channel = it.next();
			Entity entity = level.getEntity(channel.casterId);
			boolean lost = channel.arcs ? now - channel.lastRefresh > LOST_TICKS : now - channel.started > CHARGE_WATCHDOG;

			if (!(entity instanceof LivingEntity caster) || caster.isRemoved() || !caster.isAlive() || lost
					|| !PowerAnimations.isActive(channel.casterId, PowerAnimations.Kind.LIGHTNING)) {
				EnergyRender.kill(channel.owner);
				PowerAnimations.stop(channel.casterId, PowerAnimations.Kind.LIGHTNING);
				it.remove();
				continue;
			}

			if (!Vfx.inRange(caster.position())) {
				continue;
			}

			if (!channel.arcs) {
				chargeTick(caster, channel, now - channel.started);
				continue;
			}

			if (now - channel.lastRefresh > REFRESH_TICKS) {
				// Refresh overdue: arcs off until the next damage tick arrives.
				EnergyRender.kill(channel.owner);
				continue;
			}

			if (caster == mc.player) {
				CameraShake.add(CASTER_SHAKE);
			}

			arcTick(level, channel, caster);
		}
	}

	/** The hands that cast: both without a saber, else the free one. */
	private static boolean[] hands(LivingEntity caster) {
		return PowerAnimations.holdsSaber(caster) ? new boolean[] {EnergyKit.freeArmIsLeft(caster)} : new boolean[] {true, false};
	}

	/**
	 * Charge: tiny arcs snap between the fingertips, the glare in the hand grows, and from the third tick feelers of lightning
	 * probe the air ahead while sparks drip from the fingers.
	 */
	private static void chargeTick(LivingEntity caster, Channel channel, long age) {
		float build = Mth.clamp((age + 1) / (float) CHARGE_TICKS, 0f, 1f);
		int snaps = Vfx.count(2);
		Vec3 look = caster.getViewVector(1f);

		for (boolean left : hands(caster)) {
			Anchor hand = Anchor.hand(caster.getId(), left, 0.85f);
			EnergyRender.add(Flare.on(channel.owner, hand, Palette.LIGHTNING).life(2, 0f, 2f).radius(0.1f + 0.22f * build)
					.strength(0.22f + 0.3f * build).core(0.012f + 0.02f * build).flicker(0.6f));

			for (int i = 0; i < snaps; i++) {
				Vec3 a = EnergyKit.jitter(0.07);
				Vec3 b = a.add(EnergyKit.randomUnit().scale(0.12 + 0.18 * RANDOM.nextFloat()));
				EnergyRender.add(Stroke.of(channel.owner, hand.offset(a), hand.offset(b), Bolt.CRAWL, RANDOM.nextInt(2), Palette.LIGHTNING, 2,
						0.75f + 0.25f * build, 0.32f));
			}

			if (age >= 2 && RANDOM.nextFloat() < 0.35f + 0.5f * build) {
				Vec3 reach = EnergyKit.cone(look, 1.15).scale(0.3 + 0.55 * build * RANDOM.nextFloat());
				EnergyRender.add(Stroke.of(channel.owner, hand, hand.offset(reach), Bolt.FEELER, 1 + RANDOM.nextInt(2), Palette.LIGHTNING, 2,
						0.85f, 0.45f));
			}

			if (RANDOM.nextFloat() < 0.5f) {
				Vec3 at = hand.at(1f);

				if (at != null) {
					spark(at.add(EnergyKit.jitter(0.05)), EnergyKit.jitter(0.03).add(0, -0.02, 0), 5 + RANDOM.nextInt(5), 0.8f);
				}
			}
		}
	}

	/**
	 * One tick of arcs: fresh strokes from each casting hand to the target (or the struck block), the hands' glare, the
	 * contact's light, arcs crawling over the target, sparks off it, and the leaps of Chain Lightning.
	 */
	private static void arcTick(ClientLevel level, Channel channel, LivingEntity caster) {
		ClientConfig.Preset preset = ClientConfig.get().preset;
		int strokes = Vfx.count(2);
		int forks = preset == ClientConfig.Preset.REDUCED ? 1 : 2 + Math.min(3, channel.rank) + (preset == ClientConfig.Preset.CINEMATIC ? 1 : 0);
		Entity primary = target(level, channel, 0);
		boolean guarded = primary != null && channel.guardedUntil[0] > EnergyRender.ticks();
		Vec3 ground = null;

		if (primary == null) {
			ground = channel.contact != null ? channel.contact : caster.pick(RANGE, 1f, false).getLocation();
		}

		for (boolean left : hands(caster)) {
			Anchor hand = Anchor.hand(caster.getId(), left, 1f);
			EnergyRender.add(Flare.on(channel.owner, hand, Palette.LIGHTNING).life(2, 0f, 2f).radius(0.36f).strength(0.42f).core(0.03f)
					.flicker(0.7f));

			for (int i = 0; i < strokes; i++) {
				Anchor to = primary != null ? bodyPoint(caster, primary, guarded) : Anchor.fixed(ground.add(EnergyKit.jitter(0.12)));
				boolean heavy = RANDOM.nextFloat() < 0.22f;
				EnergyRender.add(Stroke.of(channel.owner, hand.offset(EnergyKit.jitter(0.045)), to, Bolt.CHANNEL, forks + (heavy ? 1 : 0),
						Palette.LIGHTNING, 2 + RANDOM.nextInt(2), heavy ? 1.1f : 0.85f, heavy ? 1.3f : 0.95f));
			}
		}

		if (primary != null) {
			struck(level, channel, caster, primary, guarded, 1f);

			// Chain Lightning: bolts leap from the first target to the others.
			for (int slot = 1; slot < channel.targets.length; slot++) {
				Entity next = target(level, channel, slot);

				if (next != null) {
					EnergyRender.add(Stroke.of(channel.owner, bodyPoint(next, primary, false), bodyPoint(primary, next, false), Bolt.CHAIN,
							Math.max(1, forks - 1), Palette.LIGHTNING, 2 + RANDOM.nextInt(2), 0.85f, 0.85f));
					struck(level, channel, primary, next, channel.guardedUntil[slot] > EnergyRender.ticks(), 0.7f);
				}
			}
		} else {
			// Struck ground or air: a glare where it lands, arcs crawling over the face of the block.
			Anchor spot = Anchor.fixed(ground);
			EnergyRender.add(Flare.on(channel.owner, spot, Palette.LIGHTNING).life(2, 0f, 2f).radius(0.6f).strength(0.4f).core(0.04f)
					.flicker(0.7f).lift(0.2f));
			BlockPos block = EnergyKit.groundBelow(level, ground.add(0, 0.05, 0), 1);

			if (block != null && RANDOM.nextFloat() < 0.8f) {
				double top = block.getY() + 1.02;
				Vec3 from = new Vec3(ground.x, Math.max(top, ground.y), ground.z);
				Vec3 to = from.add(EnergyKit.randomFlat().scale(0.35 + 0.6 * RANDOM.nextFloat()));
				EnergyRender.add(Stroke.of(channel.owner, Anchor.fixed(from), Anchor.fixed(new Vec3(to.x, top, to.z)), Bolt.CRAWL, 1,
						Palette.LIGHTNING, 2, 0.8f, 0.5f));
			}

			if (RANDOM.nextFloat() < 0.6f) {
				spark(ground, EnergyKit.randomUnit().scale(0.08).add(0, 0.1, 0), 6 + RANDOM.nextInt(6), 1f);
			}
		}
	}

	/**
	 * A target in the arcs from {@code source} (the caster, or the creature a chained bolt leapt from): lit, with arcs crawling
	 * over it and sparks spraying off; {@code weight} thins it for chained targets.
	 */
	private static void struck(ClientLevel level, Channel channel, Entity caster, Entity target, boolean guarded, float weight) {
		int id = target.getId();
		Vec3 contact = contactPoint(caster, target, guarded);
		Anchor centre = Anchor.body(id, 0.5f, 0.6f, 0.5f);
		// The struck body glows in the light of the bolts; the contact glares.
		EnergyRender.add(Flare.on(channel.owner, centre, Palette.LIGHTNING).life(2, 0f, 2f).radius(Math.max(1.1f, target.getBbHeight() * 0.85f))
				.strength(0.11f * weight).flicker(0.6f).lift(0.45f));
		Anchor glare = guarded ? Anchor.fixed(contact) : bodyPoint(caster, target, false);
		EnergyRender.add(Flare.on(channel.owner, glare, Palette.LIGHTNING).life(2, 0f, 2f).radius(0.5f * weight).strength(0.5f).core(0.04f)
				.flicker(0.7f).lift(0.25f));

		if (guarded) {
			// The blade takes the arcs and throws them aside.
			if (RANDOM.nextFloat() < 0.7f) {
				Vec3 off = EnergyKit.randomUnit().scale(0.5 + 0.7 * RANDOM.nextFloat());
				EnergyRender.add(Stroke.of(channel.owner, Anchor.fixed(contact), Anchor.fixed(contact.add(off)), Bolt.FEELER, 1, Palette.LIGHTNING, 2,
						0.8f, 0.5f));
			}

			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				spark(contact, away(caster, contact, 0.18), 5 + RANDOM.nextInt(6), 1f);
			}

			return;
		}

		// Arcs crawl over the body, and now and then one runs down it to the ground.
		int crawls = weight < 1f ? 1 : 1 + RANDOM.nextInt(2);

		for (int i = 0; i < crawls; i++) {
			EnergyRender.add(Stroke.of(channel.owner, bodyPoint(caster, target, false), bodyPoint(caster, target, false), Bolt.CRAWL, 1,
					Palette.LIGHTNING, 2, 0.8f, 0.45f));
		}

		if (RANDOM.nextFloat() < 0.16f * weight) {
			Vec3 feet = target.position();
			BlockPos block = EnergyKit.groundBelow(level, feet.add(0, 0.1, 0), 1);

			if (block != null) {
				Vec3 spot = new Vec3(feet.x, block.getY() + 1.02, feet.z).add(EnergyKit.randomFlat().scale(target.getBbWidth() * 0.6 + 0.3));
				EnergyRender.add(Stroke.of(channel.owner, Anchor.body(id, 0.5f, 0.3f + 0.2f * RANDOM.nextFloat(), 0.5f), Anchor.fixed(spot),
						Bolt.CRAWL, 1, Palette.LIGHTNING, 2, 0.85f, 0.55f));
				spark(spot, EnergyKit.randomUnit().scale(0.06).add(0, 0.08, 0), 6 + RANDOM.nextInt(5), 0.9f);
			}
		}

		if (RANDOM.nextFloat() < 0.75f * weight) {
			spark(contact, away(caster, contact, 0.12), 5 + RANDOM.nextInt(7), 0.9f);
		}
	}

	/** A damage tick on a target: a hard flash at the contact, a burst of sparks, scorch smoke. */
	private static void strike(ClientLevel level, Channel channel, LivingEntity caster, Entity target, Vec3 at, boolean guarded) {
		EnergyRender.add(Flare.on(channel.owner, Anchor.fixed(at), Palette.LIGHTNING).burst(4).radius(guarded ? 1f : 0.85f, 0.5f)
				.strength(Vfx.flashing() ? 0.85f : 0.5f).core(0.07f).lift(0.3f));

		for (int i = 0, n = Vfx.count(guarded ? 14 : 9); i < n; i++) {
			spark(at, away(caster, at, guarded ? 0.24 : 0.16), 6 + RANDOM.nextInt(8), 1f);
		}

		if (!guarded) {
			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				smoke(EnergyKit.chest(target).add(EnergyKit.jitter(target.getBbWidth() * 0.4)), 0.14f + 0.06f * RANDOM.nextFloat(), 26);
			}
		}
	}

	/** Lightning into a block: a flash, sparks skittering over it, scorch dust in the block's colour and a breath of smoke. */
	private static void groundStrike(ClientLevel level, Vec3 at, LivingEntity caster) {
		EnergyRender.add(Flare.on(0L, Anchor.fixed(at), Palette.LIGHTNING).burst(4).radius(0.9f, 0.5f).strength(Vfx.flashing() ? 0.8f : 0.5f)
				.core(0.07f).lift(0.2f));

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			spark(at.add(0, 0.05, 0), EnergyKit.randomUnit().scale(0.14).add(0, 0.12, 0), 6 + RANDOM.nextInt(8), 1f);
		}

		BlockPos block = EnergyKit.groundBelow(level, at.add(0, 0.05, 0), 1);
		float[] rgb = EnergyKit.dustColor(level, block);

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			ForceParticle p = EnergyKit.spawn(EnergyParticles.DUST, at.add(EnergyKit.jitter(0.15)).add(0, 0.05, 0),
					EnergyKit.randomFlat().scale(0.03).add(0, 0.03, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.15f, 0.4f).alpha(0.55f).life(16 + RANDOM.nextInt(8)).drag(0.88f);
			}
		}

		smoke(at.add(0, 0.1, 0), 0.16f, 28);
	}

	/** The last crackle at stopped casters' fingers. */
	private static void afterTick(ClientLevel level) {
		long now = EnergyRender.ticks();

		for (Iterator<After> it = AFTER.values().iterator(); it.hasNext();) {
			After after = it.next();
			Entity entity = level.getEntity(after.casterId());

			if (now >= after.until() || !(entity instanceof LivingEntity caster) || !caster.isAlive()) {
				it.remove();
				continue;
			}

			float left = (after.until() - now) / (float) AFTER_TICKS;

			if (RANDOM.nextFloat() < 0.6f * left) {
				boolean side = after.both() ? RANDOM.nextBoolean() : after.left();
				Anchor hand = Anchor.hand(caster.getId(), side, 0.8f);
				Vec3 a = EnergyKit.jitter(0.06);
				EnergyRender.add(Stroke.of(0L, hand.offset(a), hand.offset(a.add(EnergyKit.randomUnit().scale(0.1 + 0.12 * RANDOM.nextFloat()))),
						Bolt.CRAWL, 0, Palette.LIGHTNING, 2, 0.6f * left + 0.2f, 0.28f));
			}
		}
	}

	/** Where the bolts land on {@code target}: its side facing the caster, or in front of it on a guarding blade. */
	private static Vec3 contactPoint(Entity caster, Entity target, boolean guarded) {
		Vec3 chest = EnergyKit.chest(target);
		Vec3 toward = caster.position().subtract(target.position());
		Vec3 flat = new Vec3(toward.x, 0, toward.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		return chest.add(flat.scale(target.getBbWidth() * 0.5 + (guarded ? 0.45 : 0.05))).add(0, guarded ? 0.15 : 0, 0);
	}

	/**
	 * A random point on the side of {@code target} facing {@code from} (head to hips), just outside its body so the bolt
	 * visibly lands on it; for a guarded target, a point on the blade held up in front of it.
	 */
	private static Anchor bodyPoint(Entity from, Entity target, boolean guarded) {
		Vec3 toward = from.position().subtract(target.position());
		Vec3 flat = new Vec3(toward.x, 0, toward.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 across = new Vec3(-flat.z, 0, flat.x);
		double width = target.getBbWidth();

		if (guarded) {
			double out = width * 0.5 + 0.45;
			double side = EnergyKit.jitter1(0.12);
			float up = 0.55f + 0.3f * RANDOM.nextFloat();
			return Anchor.body(target.getId(), 0.5f, up, 0.5f).offset(flat.scale(out).add(across.scale(side)));
		}

		double out = width * (0.5 + 0.1 * RANDOM.nextFloat()) + 0.02;
		double side = EnergyKit.jitter1(width * 0.45);
		float up = 0.35f + 0.6f * RANDOM.nextFloat();
		return Anchor.body(target.getId(), 0.5f, up, 0.5f).offset(flat.scale(out).add(across.scale(side)));
	}

	/** A velocity away from the bolts' source, spread wide and thrown a little upward. */
	private static Vec3 away(Entity caster, Vec3 at, double speed) {
		Vec3 back = caster.position().add(0, caster.getBbHeight() * 0.6, 0).subtract(at);
		back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : back.normalize();
		return back.scale(0.4).add(EnergyKit.randomUnit()).add(0, 0.5, 0).normalize().scale(speed * (0.5 + RANDOM.nextFloat()));
	}

	private static @Nullable Entity target(ClientLevel level, Channel channel, int slot) {
		Entity target = channel.targets[slot] >= 0 ? level.getEntity(channel.targets[slot]) : null;
		return target == null || target.isRemoved() ? null : target;
	}

	/** A lightning spark that outlives the channel (it is no one's, so a stop leaves it to fall). */
	private static void spark(Vec3 at, Vec3 velocity, int life, float size) {
		EnergyRender.add(new Spark(0L, at, velocity, life, size * (0.8f + 0.4f * RANDOM.nextFloat()), 0.035f, 0.9f, Palette.SPARK_HOT,
				Palette.SPARK_COOL));
	}

	/** A puff of dark scorch smoke rising from {@code at}. */
	private static void smoke(Vec3 at, float size, int life) {
		ForceParticle p = EnergyKit.spawn(EnergyParticles.SMOKE, at, new Vec3(EnergyKit.jitter1(0.006), 0.012 + 0.01 * RANDOM.nextDouble(),
				EnergyKit.jitter1(0.006)));

		if (p != null) {
			p.color(SMOKE[0], SMOKE[1], SMOKE[2]).size(size, size * 3f).alpha(0.42f).life(life + RANDOM.nextInt(10)).drag(0.96f);
		}
	}

	/** Stop: every bolt goes at once; the glare dies away, a last crackle snaps at the fingers, smoke curls up. */
	private static void stop(ClientLevel level, LivingEntity caster) {
		Channel channel = CHANNELS.remove(caster.getId());
		PowerAnimations.stop(caster.getId(), PowerAnimations.Kind.LIGHTNING);

		if (channel == null) {
			return;
		}

		EnergyRender.kill(channel.owner);
		Vec3 hand = ForceFxUtil.handPos(caster, ForceFxUtil.freeArmIsLeft(caster), 1f);

		if (channel.arcs) {
			ForceFxUtil.play(level, hand, ModSounds.FORCE_LIGHTNING_RELEASE, 0.8f, ForceFxUtil.pitchVariance(0.05f));
		}

		if (!Vfx.inRange(hand)) {
			return;
		}

		boolean[] hands = hands(caster);

		for (boolean left : hands) {
			Anchor at = Anchor.hand(caster.getId(), left, 0.8f);
			EnergyRender.add(Flare.on(0L, at, Palette.LIGHTNING).life(9, 0f, 9f).radius(0.3f, 0.12f).strength(channel.arcs ? 0.4f : 0.25f)
					.core(0.02f));
			Vec3 point = at.at(1f);

			if (point != null) {
				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					smoke(point.add(EnergyKit.jitter(0.06)), 0.08f + 0.04f * RANDOM.nextFloat(), 22);
				}

				spark(point, EnergyKit.jitter(0.03).add(0, -0.02, 0), 6 + RANDOM.nextInt(5), 0.8f);
			}
		}

		if (channel.arcs) {
			AFTER.put(caster.getId(), new After(caster.getId(), hands.length > 1, hands[0], EnergyRender.ticks() + AFTER_TICKS));
			Entity primary = target(level, channel, 0);

			if (primary != null && Vfx.inRange(primary.position())) {
				for (int i = 0, n = Vfx.count(3); i < n; i++) {
					smoke(EnergyKit.chest(primary).add(EnergyKit.jitter(primary.getBbWidth() * 0.45)), 0.14f + 0.08f * RANDOM.nextFloat(), 30);
				}
			}
		}
	}

	static void clear() {
		for (Channel channel : CHANNELS.values()) {
			EnergyRender.kill(channel.owner);
		}

		CHANNELS.clear();
		AFTER.clear();
	}

	private LightningFx() {
	}
}
