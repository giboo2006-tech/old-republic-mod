package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.identity.Path;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The mark (spec MILESTONE_4.md 8.1, 8.2): a day after Chapter Three, or on waking from a real
 * sleep, at a safe moment on the home world with no hostile near, a seeker droid comes down out of
 * the sky, sweeps the player with a red scan fan, chirps twice and climbs away. When it has gone
 * the chapter moves to SEEKER.
 *
 * <p>The droid is W6's {@code seeker_droid} (invulnerable, {@code noSave}); this class only flies
 * it through the scene by setting its position each tick. When the entity type is missing the
 * scene still plays its effect from where the droid would hover. Scenes live in memory: a logout,
 * a death or a dimension change ends the scene early and the stage still moves on.
 */
public final class SeekerScene {
	static final String SEEKER_TAG = "oldrepublic.c4_seeker";
	private static final Identifier SEEKER_TYPE = OldRepublicMod.id("seeker_droid");
	/** Ticks to come down, and to climb away after the chirps. */
	private static final int DESCEND_TICKS = 50;
	private static final int CLIMB_TICKS = 30;
	/** The droid hovers this far in front of and above the player's eyes. */
	private static final double HOVER_AHEAD = 3.0;
	private static final double HOVER_UP = 1.5;
	private static final int CHECK_INTERVAL = 20;

	private static final class Scene {
		final Vec3 hover;
		final Vec3 start;
		final ResourceKey<Level> dimension;
		@Nullable UUID droid;
		int tick;

		Scene(Vec3 start, Vec3 hover, ResourceKey<Level> dimension) {
			this.start = start;
			this.hover = hover;
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, Scene> SCENES = new HashMap<>();
	/** {@code tickCount} at which each sleeping player lay down. */
	private static final Map<UUID, Integer> SLEEP_START = new HashMap<>();

	/** MARKED's wait: plays the scene when it is due, on the home world, at a safe moment with no hostile near. */
	public static void check(ServerPlayer p) {
		if (SCENES.containsKey(p.getUUID()) || p.tickCount % CHECK_INTERVAL >= StoryService.SLOW_TICK) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();

		if (ChapterFourRules.seekerDue(Story.get(p), StoryService.now(p), b) && Planets.isHomeWorld(p.level()) && isSafe(p, b)) {
			start(p);
		}
	}

	/** The M2 safe moment (not hurt lately, on the ground, not in lava, not asleep) and no hostile within {@code seekerHostileRange}. */
	public static boolean isSafe(ServerPlayer p, ChapterFourBalance b) {
		int lastHurt = p.getLastHurtByMobTimestamp();
		boolean recentlyHurt = lastHurt > 0 && p.tickCount - lastHurt < StoryService.balance().crossingSafeTicks;
		boolean grounded = p.onGround() || p.isPassenger();

		if (recentlyHurt || !grounded || p.isInLava() || p.isSleeping()) {
			return false;
		}

		return p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(b.seekerHostileRange),
				m -> m.isAlive() && Attitudes.toward(m, p) == Attitude.HOSTILE).isEmpty();
	}

	/** Plays the scene now (also {@code /oldrepublic c4 seeker}). */
	static void start(ServerPlayer p) {
		ChapterFourBalance b = ChapterFourService.balance();
		Vec3 look = p.getLookAngle().horizontal();
		Vec3 ahead = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		Vec3 hover = p.getEyePosition().add(ahead.scale(HOVER_AHEAD)).add(0, HOVER_UP, 0);
		Vec3 start = hover.add(0, b.seekerDescend, 0);
		Scene scene = new Scene(start, hover, p.level().dimension());
		ServerLevel level = p.level();

		Entity droid = BuiltInRegistries.ENTITY_TYPE.getOptional(SEEKER_TYPE).<Entity>map(t -> t.create(level, EntitySpawnReason.EVENT)).orElse(null);

		if (droid != null) {
			droid.snapTo(start.x, start.y, start.z, p.getYRot() + 180f, 0f);
			droid.setNoGravity(true);
			StoryTags.setOwner(droid, p.getUUID());

			// The scene's tag goes on once the droid is in the level: the load rule discards a loaded
			// droid carrying it, and Fabric's entity load event fires for a fresh spawn too.
			if (level.addFreshEntity(droid)) {
				droid.addTag(SEEKER_TAG);
				scene.droid = droid.getUUID();
			}
		} else {
			OldRepublicMod.LOGGER.debug("No {} entity type; the seeker scene plays without its droid", SEEKER_TYPE);
		}

		SCENES.put(p.getUUID(), scene);
	}

	/** Every tick: flies the droid down, holds it for the scan, sends it away, and ends the scene. */
	static void tick(ServerPlayer p) {
		Scene scene = SCENES.get(p.getUUID());

		if (scene == null) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		int scanEnd = DESCEND_TICKS + Math.max(1, b.seekerScanTicks);
		int end = scanEnd + CLIMB_TICKS;
		Entity droid = scene.droid == null ? null : p.level().getEntity(scene.droid);
		boolean droidGone = scene.droid != null && (droid == null || !droid.isAlive());
		int t = scene.tick++;

		if (droidGone && t < scanEnd) {
			// Struck, it dodged and left early: the scene ends, the mark stands.
			finish(p);
			return;
		}

		Vec3 at;

		if (t < DESCEND_TICKS) {
			double f = easeOut(t / (double) DESCEND_TICKS);
			at = scene.start.lerp(scene.hover, f);
		} else if (t < scanEnd) {
			at = scene.hover.add(0, Math.sin(t * 0.3) * 0.05, 0);

			if (t == DESCEND_TICKS) {
				EffectEvents.toPlayer(p, QuestEffects.SEEKER_SCAN, at, p.position().add(0, p.getBbHeight() * 0.55, 0).subtract(at).normalize(),
						droid != null ? droid.getId() : p.getId(), b.seekerScanTicks, QuestEffects.SEEKER_SCANNING);
			}
		} else {
			double f = (t - scanEnd) / (double) CLIMB_TICKS;
			at = scene.hover.add(0, f * f * b.seekerDescend * 1.5, f * 6);

			if (t == scanEnd) {
				EffectEvents.toPlayer(p, QuestEffects.SEEKER_SCAN, at, null, droid != null ? droid.getId() : p.getId(), 0f, QuestEffects.SEEKER_LEAVING);
			}
		}

		if (droid != null) {
			Vec3 toPlayer = p.getEyePosition().subtract(at);
			float yaw = (float) (Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z)));
			droid.snapTo(at.x, at.y, at.z, yaw, 0f);
			droid.setDeltaMovement(Vec3.ZERO);
		}

		if (t >= end) {
			finish(p);
		}
	}

	private static double easeOut(double f) {
		double clamped = Math.max(0, Math.min(1, f));
		return 1 - (1 - clamped) * (1 - clamped);
	}

	/** The droid leaves; the chapter moves to SEEKER. */
	private static void finish(ServerPlayer p) {
		removeDroid(p, SCENES.remove(p.getUUID()));
		ChapterFourService.enterStage(p, ChapterFourStage.SEEKER);
	}

	private static void removeDroid(ServerPlayer p, @Nullable Scene scene) {
		ServerLevel level = scene == null || scene.droid == null ? null : p.level().getServer().getLevel(scene.dimension);
		Entity droid = level == null ? null : level.getEntity(scene.droid);

		if (droid != null) {
			droid.discard();
		}
	}

	/** SEEKER begins: the journal entry, the codex, and who to talk to. */
	static void onSeekerStage(ServerPlayer p) {
		Journal.unlockEntry(p, JournalEntries.A_PRICE);
		Journal.unlockCodex(p, JournalEntries.CODEX_BOUNTY_HUNTERS);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.price"));
		Path path = Story.get(p).storyPath();
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.seeker." + (path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent")));
	}

	static void onStartSleeping(ServerPlayer p) {
		SLEEP_START.put(p.getUUID(), p.tickCount);
	}

	/** Waking from a long enough sleep while MARKED makes the seeker due at the next safe moment. */
	static void onStopSleeping(ServerPlayer p) {
		Integer start = SLEEP_START.remove(p.getUUID());

		if (start == null || p.tickCount - start < ChapterFourService.balance().minSleepTicks) {
			return;
		}

		StoryData data = Story.get(p);

		if (data.chapter4.stage() == ChapterFourStage.MARKED && data.flags.add(ChapterFourRules.SEEKER_DUE)) {
			Story.commit(p, data);
		}
	}

	/** Whether the player's scene is playing (tests). */
	public static boolean playing(ServerPlayer p) {
		return SCENES.containsKey(p.getUUID());
	}

	/** A cut-off scene (logout, death, a dimension change) plays again at the next safe moment: the mark stays due. */
	public static void forget(ServerPlayer p) {
		SLEEP_START.remove(p.getUUID());
		removeDroid(p, SCENES.remove(p.getUUID()));
	}

	static void clear() {
		SCENES.clear();
		SLEEP_START.clear();
	}

	static boolean isLeftover(Entity entity) {
		return entity.entityTags().contains(SEEKER_TAG);
	}

	private SeekerScene() {
	}
}
