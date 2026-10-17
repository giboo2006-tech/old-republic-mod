package com.oldrepublic.world.puzzle;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.world.WorldDamage;
import com.oldrepublic.world.puzzle.PuzzleStates.VentPhase;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;

/**
 * A grate in a tomb floor that breathes miasma (spec 3.3). On its own scheduled tick, while a player
 * is within {@code sites.ventActiveRange}, it cycles IDLE ({@code ventMinTicks}..{@code ventMaxTicks})
 * → TELL ({@code ventTellTicks}: a hiss and grit dancing up, a Danger Sense tell) → PUFF: every
 * living thing in the 1×2×1 column above (inflated by 0.5) takes {@code ventDamage}
 * {@code tomb_miasma} and Slowness I for {@code ventSlownessTicks} through
 * {@link ControlEffects#diminished} (kind {@code slow}). With nobody near it rests. Worldgen never
 * runs {@code onPlace}, so resting vents are randomly ticking and {@link #kick} starts them, and
 * the site tracker kicks the processional's vents when a player walks in.
 */
public class TombVentBlock extends Block {
	public static final MapCodec<TombVentBlock> CODEC = simpleCodec(TombVentBlock::new);

	public TombVentBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PuzzleStates.PHASE, VentPhase.IDLE));
	}

	@Override
	protected MapCodec<TombVentBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PuzzleStates.PHASE);
	}

	/** If {@code pos} holds an IDLE vent with no tick pending, schedules its first step ({@code ventMinTicks}..{@code ventMaxTicks}). */
	public static void kick(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof TombVentBlock block) || state.getValue(PuzzleStates.PHASE) != VentPhase.IDLE
				|| PuzzleWorld.tickPending(level, pos, block)) {
			return;
		}

		level.scheduleTick(pos, block, PuzzleRules.ventWait(level.getRandom(), SiteBalance.get()));
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(PuzzleStates.PHASE) == VentPhase.IDLE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		kick(level, pos);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			kick(server, pos);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		PuzzleDisplay.get().tombVent(state, level, pos, random);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		SiteBalance b = SiteBalance.get();
		VentPhase phase = state.getValue(PuzzleStates.PHASE);
		PuzzleRules.VentStep step = PuzzleRules.ventStep(phase, PuzzleWorld.playerNear(level, pos, b.ventActiveRange), random, b);

		if (step.next() != phase) {
			level.setBlock(pos, state.setValue(PuzzleStates.PHASE, step.next()), Block.UPDATE_CLIENTS);
		}

		if (step.next() == VentPhase.TELL && phase != VentPhase.TELL) {
			PuzzleEffects.send(level, PuzzleEffects.TOMB_VENT, pos, b.ventTellTicks, PuzzleEffects.VENT_TELL);
		}

		if (step.puff()) {
			puff(level, pos, b);
		}

		if (step.delay() > 0) {
			level.scheduleTick(pos, this, step.delay());
		}
	}

	/** The puff: damage and slowness in the column above, and the cloud for everyone near. Returns how many it caught. */
	public static int puff(ServerLevel level, BlockPos pos, SiteBalance b) {
		PuzzleEffects.send(level, PuzzleEffects.TOMB_VENT, pos, 1f, PuzzleEffects.VENT_PUFF);
		DamageSource source = level.damageSources().source(WorldDamage.TOMB_MIASMA);
		AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1).inflate(0.5);
		int caught = 0;

		for (Entity entity : level.getEntities((Entity) null, column, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			LivingEntity living = (LivingEntity) entity;
			living.hurtServer(level, source, b.ventDamage);
			int ticks = ControlEffects.diminished(living, b.ventSlownessTicks, ControlEffects.SLOW);

			if (ticks > 0) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0));
			}

			caught++;
		}

		return caught;
	}
}
