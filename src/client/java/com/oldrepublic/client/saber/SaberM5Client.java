package com.oldrepublic.client.saber;

/**
 * Milestone 5 client initialisation for the off-hand saber, the forms' and tuning's screens, blade
 * length and hum profiles (section 10) and the legendary parts' looks (section 11). Owned by W7
 * sabers, crafting and gear; the lead calls it from {@code OldRepublicModClient}, so the worker
 * never edits the client entry point.
 *
 * <p>Here: the {@code offhand_strike} and {@code crystal_tune} effects ({@link SaberM5Fx}). The
 * off hand's input sits with the other saber inputs ({@link SaberActionsClient}, {@link SaberDualClient}),
 * the stances and the off arm's guard with the saber poses ({@link SaberStance}), and the Tune tab
 * and the length switch in the workbench screen.
 */
public final class SaberM5Client {
	public static void init() {
		SaberM5Fx.init();
	}

	private SaberM5Client() {
	}
}
