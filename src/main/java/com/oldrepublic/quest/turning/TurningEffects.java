package com.oldrepublic.quest.turning;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The turning's effect events (spec MILESTONE_5.md 15.3), drawn by {@code TurningFx} on the client within the 15.1
 * budgets. The banners of the rites and the ceremonies are W4's {@code lesson_complete} variants 12 and 13.
 */
public final class TurningEffects {
	/**
	 * To the player: variant {@link #OFFER} when the messenger makes the offer (the {@code story.turning.offer} sting),
	 * {@link #RITE_FALL} at the Fall's rite (a cold wind lifts grit, fire dims), {@link #RITE_RETURN} at the Return's (the
	 * dust settles, fire brightens); entity = the player.
	 */
	public static final Identifier TURNING = OldRepublicMod.id("turning");
	/**
	 * Around the player at a rank 4 ceremony (sound {@code story.rank_four}): variant {@link #MASTER} (a lit blade laid
	 * across open hands, pale motes fall), {@link #DARTH} (the writ burns from its edges, ash lifts) or {@link #WAYFARER}
	 * (Tessaly's figure stands up from the Compass and steps aside to show the road); entity = the player.
	 */
	public static final Identifier RANK_FOUR = OldRepublicMod.id("rank_four");

	public static final int OFFER = 0;
	public static final int RITE_FALL = 1;
	public static final int RITE_RETURN = 2;

	public static final int MASTER = 0;
	public static final int DARTH = 1;
	public static final int WAYFARER = 2;

	private TurningEffects() {
	}
}
