package com.oldrepublic.world.puzzle;

import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;

/**
 * The Force-reactive blocks' handlers (spec 10.6): each answers only its own act and changes only
 * its own block (or its keeper). W3's powers find the block on the aim and dispatch; a handler
 * returns true when the block took the act.
 */
final class PuzzleHandlers {
	static void init() {
		ForceBlockInteractions.register(PuzzleBlocks.VEILED_STONE, PuzzleHandlers::veiledStone);
		ForceBlockInteractions.register(PuzzleBlocks.COUNTERWEIGHT_STONE, PuzzleHandlers::counterweight);
		ForceBlockInteractions.register(PuzzleBlocks.ECHO_URN, PuzzleHandlers::echoUrn);
		ForceBlockInteractions.register(PuzzleBlocks.PULL_ANCHOR, PuzzleHandlers::pullAnchor);
		ForceBlockInteractions.register(PuzzleBlocks.COLLAPSED_RUBBLE, PuzzleHandlers::rubble);
	}

	/** Push opens a revealed group. */
	private static boolean veiledStone(ForceBlockContext ctx) {
		return ctx.act() == ForceAct.PUSH && VeiledStoneBlock.open(ctx.level(), ctx.pos(), ctx.caster()) > 0;
	}

	/** Push sinks a raised counterweight. */
	private static boolean counterweight(ForceBlockContext ctx) {
		return ctx.act() == ForceAct.PUSH && CounterweightStoneBlock.sink(ctx.level(), ctx.pos(), ctx.caster());
	}

	/** Push reports to the urn's keeper. */
	private static boolean echoUrn(ForceBlockContext ctx) {
		if (ctx.act() != ForceAct.PUSH) {
			return false;
		}

		UrnKeeperBlockEntity keeper = EchoUrnBlock.findKeeper(ctx.level(), ctx.pos());
		return keeper != null && keeper.push(ctx.level(), ctx.pos(), ctx.caster()).outcome() != UrnKeeperBlockEntity.Outcome.NOT_MINE;
	}

	/** Pull makes an armed anchor strain. */
	private static boolean pullAnchor(ForceBlockContext ctx) {
		return ctx.act() == ForceAct.PULL && PullAnchorBlock.pull(ctx.level(), ctx.pos(), ctx.caster());
	}

	/** Lift, every tick of the channel, counts toward clearing. */
	private static boolean rubble(ForceBlockContext ctx) {
		return ctx.act() == ForceAct.LIFT && CollapsedRubbleBlock.lift(ctx.level(), ctx.pos(), ctx.caster());
	}

	private PuzzleHandlers() {
	}
}
