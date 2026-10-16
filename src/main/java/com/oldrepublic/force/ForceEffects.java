package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Effect event ids of the force feature and the packing of their {@code variant} field. See
 * {@code docs/design/force.md} section 3 for what each field carries.
 */
public final class ForceEffects {
	/** Charge started ({@link #CHARGE_START}) or ended without a cast ({@link #CHARGE_STOP}). */
	public static final Identifier PUSH_CHARGE = OldRepublicMod.id("force_push_charge");
	/** Push released. Intensity = charge 0..1, variant = {@link #packRelease}. */
	public static final Identifier PUSH_RELEASE = OldRepublicMod.id("force_push");
	/** One target hit. Intensity = impulse, variant = hit index in the cast or {@link #IMPACT_PROJECTILE}. */
	public static final Identifier PUSH_IMPACT = OldRepublicMod.id("force_push_impact");
	/** Focused Blast target slammed into a wall. Direction = wall normal, intensity = damage. */
	public static final Identifier PUSH_SLAM = OldRepublicMod.id("force_push_slam");
	/** The player has just learned Force Push. */
	public static final Identifier PUSH_LEARNED = OldRepublicMod.id("force_push_learned");

	/** Force Pull reach. Entity = caster, pos = hand, variant = {@link com.oldrepublic.force.pull.PullShape} ordinal. */
	public static final Identifier PULL = OldRepublicMod.id("force_pull");
	/**
	 * A pull moment. Variant {@link #PULL_LAUNCHED}: entity = target, intensity = speed, dir = toward
	 * the caster. {@link #PULL_ARRIVED}: entity = target, pos = the caster's feet. {@link #PULL_ITEMS}:
	 * pos = where the items lay, dir = offset to the caster's hand, intensity = item count.
	 */
	public static final Identifier PULL_HIT = OldRepublicMod.id("force_pull_hit");
	/** Disarming Pull wrenched a weapon aside. Entity = target, pos = the weapon hand. */
	public static final Identifier PULL_DISARM = OldRepublicMod.id("force_pull_disarm");
	/** Force Jump gather started ({@link #CHARGE_START}) or ended without a leap ({@link #CHARGE_STOP}). */
	public static final Identifier JUMP_GATHER = OldRepublicMod.id("force_jump_gather");
	/** Leap. Entity = jumper, pos = take-off point, dir = velocity, intensity = charge, variant = shape ordinal. */
	public static final Identifier JUMP_LAUNCH = OldRepublicMod.id("force_jump_launch");
	/** Landing. Entity = jumper, pos = landing point, intensity = blocks fallen, variant = {@link #surface}. */
	public static final Identifier JUMP_LAND = OldRepublicMod.id("force_jump_land");
	/** Crushing Descent shockwave. Entity = jumper, pos = landing point, intensity = damage. */
	public static final Identifier JUMP_SLAM = OldRepublicMod.id("force_jump_slam");
	/** Soaring Leap glide started ({@link #CHARGE_START}) or ended ({@link #CHARGE_STOP}). Entity = jumper. */
	public static final Identifier JUMP_GLIDE = OldRepublicMod.id("force_jump_glide");
	/** Force Heal channel. Entity = caster, variant {@link #CHANNEL_START}, {@link #CHANNEL_END} or {@link #CHANNEL_INTERRUPTED}. */
	public static final Identifier HEAL = OldRepublicMod.id("force_heal");
	/** Shared Light ally stream. Entity = ally, variant = {@link #packCaster}(caster, 0 start / 1 stop). */
	public static final Identifier HEAL_ALLY = OldRepublicMod.id("force_heal_ally");
	/** Serene Ward granted. Entity = caster, intensity = duration in ticks. */
	public static final Identifier HEAL_WARD = OldRepublicMod.id("force_heal_ward");
	/**
	 * Force Lightning phase. Entity = caster, intensity = mastery rank, variant = {@link #packLightning}
	 * (phase {@link #LIGHTNING_CHARGE}, {@link #LIGHTNING_ARCS} or {@link #LIGHTNING_STOP} in the low
	 * bits, as the spec numbers them; the shape ordinal above).
	 */
	public static final Identifier LIGHTNING = OldRepublicMod.id("force_lightning");
	/**
	 * An arc target changed. Entity = target, pos = its chest, variant = {@link #packCaster}(caster,
	 * slot 0 to 2), intensity 1 on, 0 off.
	 */
	public static final Identifier LIGHTNING_TARGET = OldRepublicMod.id("force_lightning_target");
	/**
	 * A damage tick. Entity = caster, pos = primary contact (or the struck block), intensity = number
	 * of targets hit (0 = the arcs strike a block), variant = bit mask of target slots that were
	 * blocked by a saber guard.
	 */
	public static final Identifier LIGHTNING_HIT = OldRepublicMod.id("force_lightning_hit");

	public static final int CHARGE_START = 0;
	public static final int CHARGE_STOP = 1;
	/** Impact variant for a projectile that was turned: small visual, no impact sound. */
	public static final int IMPACT_PROJECTILE = 1000;
	/** Impacts with an index below this play the impact sound (spec: max 3 per cast). */
	public static final int IMPACT_SOUND_LIMIT = 3;

	public static final int CHANNEL_START = 0;
	public static final int CHANNEL_END = 1;
	public static final int CHANNEL_INTERRUPTED = 2;
	/** Lightning phases (spec 8.5): the charge started, the arcs strike, the channel stopped. */
	public static final int LIGHTNING_CHARGE = 0;
	public static final int LIGHTNING_ARCS = 1;
	public static final int LIGHTNING_STOP = 2;
	public static final int PULL_LAUNCHED = 0;
	public static final int PULL_ARRIVED = 1;
	public static final int PULL_ITEMS = 2;

	/** Landing surface groups for {@link #JUMP_LAND}. */
	public static final int SURFACE_SOFT = 0;
	public static final int SURFACE_STONE = 1;
	public static final int SURFACE_SAND = 2;
	public static final int SURFACE_SNOW = 3;

	/** Packs a caster entity id with a small value (0 to 3) into one variant. */
	public static int packCaster(int casterId, int value) {
		return casterId << 2 | value & 3;
	}

	public static int casterOf(int variant) {
		return variant >>> 2;
	}

	public static int valueOf(int variant) {
		return variant & 3;
	}

	/** Packs a lightning phase (0 to 3) and the shape ordinal. */
	public static int packLightning(int phase, int shape) {
		return phase & 3 | shape << 2;
	}

	public static int lightningPhase(int variant) {
		return variant & 3;
	}

	public static int lightningShape(int variant) {
		return variant >>> 2;
	}

	/** Landing surface group of a block (shared by server and client so both agree). */
	public static int surface(BlockState state) {
		Block block = state.getBlock();

		// API-CHECK: BlockState#is(TagKey) via TypedInstance (as in PushFx)
		if (state.is(BlockTags.SAND) || block == Blocks.GRAVEL) {
			return SURFACE_SAND;
		}

		if (state.is(BlockTags.SNOW) || block == Blocks.SNOW || block == Blocks.POWDER_SNOW) {
			return SURFACE_SNOW;
		}

		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS) || block == Blocks.COBBLESTONE) {
			return SURFACE_STONE;
		}

		return SURFACE_SOFT;
	}

	/** Packs branch, range (quarter blocks) and half-angle (degrees) into one varint. */
	public static int packRelease(PushBranch branch, PushShape shape) {
		int range = Mth.clamp(Math.round(shape.range() * 4f), 0, 1023);
		int angle = Mth.clamp(Math.round(shape.halfAngle()), 0, 180);
		return branch.ordinal() | range << 2 | angle << 12;
	}

	public static PushBranch releaseBranch(int variant) {
		return PushBranch.byOrdinal(variant & 3);
	}

	public static float releaseRange(int variant) {
		return (variant >> 2 & 1023) / 4f;
	}

	public static float releaseHalfAngle(int variant) {
		return variant >> 12 & 255;
	}

	private ForceEffects() {
	}
}
