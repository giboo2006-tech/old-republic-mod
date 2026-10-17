package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.SaberDuelist;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side saber animation state, one per entity: which technique pose it holds, since when and
 * for how long. Fed by the saber effect events, so it works the same for players and saber NPCs.
 * A saber NPC without an active technique pose shows its synced guard state (blade across the body
 * while guarding, arm dropped while broken). Drives {@link SaberPose}.
 *
 * <p>Every pose ends by itself after its length (an open heavy wind-up at a watchdog limit), and
 * all of them when the entity is gone or the level changes.
 */
public final class SaberAnimations {
	/** The saber poses. */
	public enum Kind {
		/** Combo finisher: the blade raised overhead and chopped down. */
		FINISHER,
		/** Heavy wind-up: the blade drawn back over the shoulder, body twisted (the tell). */
		HEAVY_WINDUP,
		/** Heavy strike: a wide horizontal sweep. */
		HEAVY_STRIKE,
		/** Lunge: the arm thrust forward along the look. */
		LUNGE,
		/** Form salute: the blade raised vertically before the face, then lowered. */
		SALUTE,
		/** Saber Throw: the arm whips forward and opens. */
		THROW,
		/** The thrown saber caught: the hand snaps up and back. */
		CATCH,
		/** A saber NPC's attack tell: the blade drawn back. */
		NPC_WINDUP,
		/** A saber NPC guarding: the blade held across the body. */
		GUARD,
		/** A saber NPC whose guard broke: the arm dropped, head down. */
		BROKEN
	}

	static final int FINISHER_TICKS = 8;
	static final int HEAVY_STRIKE_TICKS = 8;
	static final int LUNGE_TICKS = 10;
	static final int THROW_TICKS = 7;
	static final int CATCH_TICKS = 6;
	/** Longest a heavy wind-up pose may last without its strike or cancel event. */
	private static final int WINDUP_WATCHDOG = 40;
	private static final float BLEND_TICKS = 2f;

	private record State(Kind kind, long start, long until) {
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static long ticks;

	/** Starts {@code kind} on {@code entityId} for {@code length} ticks, replacing its current pose. */
	static void start(int entityId, Kind kind, int length) {
		STATES.put(entityId, new State(kind, ticks, ticks + Math.max(1, length)));
	}

	/** Starts an open heavy wind-up lasting {@code windup} ticks (bounded by a watchdog). */
	static void startWindup(int entityId, Kind kind, int windup) {
		start(entityId, kind, Math.min(Math.max(windup, 1) + 4, WINDUP_WATCHDOG));
	}

	/** Ends the pose if it is {@code kind}. */
	static void stop(int entityId, Kind kind) {
		State state = STATES.get(entityId);

		if (state != null && state.kind == kind) {
			STATES.remove(entityId);
		}
	}

	static void tick(Minecraft mc) {
		ticks++;

		if (mc.level == null) {
			STATES.clear();
			return;
		}

		STATES.entrySet().removeIf(e -> e.getValue().until < ticks || mc.level.getEntity(e.getKey()) == null);
	}

	static void clear() {
		STATES.clear();
	}

	/** The pose to render for {@code entity}, or null. */
	public static @Nullable SaberPose pose(LivingEntity entity, float partialTick) {
		State state = STATES.get(entity.getId());
		boolean left = saberInLeftArm(entity);

		if (state != null) {
			float age = ticks - state.start + partialTick;
			float length = state.until - state.start;
			float blendIn = Mth.clamp(age / BLEND_TICKS, 0f, 1f);
			float blendOut = Mth.clamp((state.until - ticks - partialTick) / BLEND_TICKS, 0f, 1f);
			return new SaberPose(state.kind, Math.min(blendIn, blendOut), Mth.clamp(age / length, 0f, 1f), left);
		}

		if (entity instanceof SaberDuelist duelist && SaberComponents.isUsable(entity.getMainHandItem())) {
			GuardState guard = duelist.guardState();

			if (guard == GuardState.GUARDING) {
				return new SaberPose(Kind.GUARD, 1f, 0f, left);
			}

			if (guard == GuardState.BROKEN) {
				return new SaberPose(Kind.BROKEN, 1f, 0f, left);
			}
		}

		return null;
	}

	/** True when the saber is held in the left arm (in the main hand of a left-handed entity, or in the off hand of a right-handed one). */
	static boolean saberInLeftArm(LivingEntity entity) {
		ItemStack main = entity.getMainHandItem();
		boolean inMain = SaberComponents.isIgnited(main) || !SaberComponents.isIgnited(entity.getOffhandItem());
		boolean leftHanded = entity.getMainArm() == HumanoidArm.LEFT;
		return inMain == leftHanded;
	}

	private SaberAnimations() {
	}
}
