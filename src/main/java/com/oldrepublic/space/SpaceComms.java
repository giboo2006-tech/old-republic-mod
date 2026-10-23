package com.oldrepublic.space;

import net.minecraft.util.RandomSource;

/**
 * The comm lines heard in space (spec MILESTONE_5.md 5.6, 5.9, 6.5), as lang keys in
 * {@code lang-parts/space.json}: patrols hail, the blockade's pilots and Varra speak through the
 * battle, a scanning patrol warns, and a bounty's ace taunts. The HUD shows each as a subtitle in
 * the M4 bark style, under its speaker ({@link SpaceEffects}{@code .SPEAKER_*}).
 */
final class SpaceComms {
	private static final String PREFIX = "space.oldrepublic.comm.";

	/** Patrol hails by who flies: pirates (3), the Hymn's pilots (3), the clan (2). */
	static final String PIRATE_HAIL = PREFIX + "pirate.hail.";
	static final int PIRATE_HAILS = 3;
	static final String HYMN_HAIL = PREFIX + "hymn.hail.";
	static final int HYMN_HAILS = 3;
	static final String CLAN_HAIL = PREFIX + "clan.hail.";
	static final int CLAN_HAILS = 2;

	/** The blockade (6.5). */
	static final String BLOCKADE_HAIL = PREFIX + "blockade.hail";
	static final String BLOCKADE_VARRA = PREFIX + "blockade.varra";
	static final String BLOCKADE_WAVE = PREFIX + "blockade.wave";
	static final String BLOCKADE_GENERATOR = PREFIX + "blockade.generator";
	static final String BLOCKADE_BROKEN = PREFIX + "blockade.broken";
	static final String BLOCKADE_VARRA_BROKEN = PREFIX + "blockade.varra_broken";

	/** The Peaceful pass (6.5): the interceptors hail the singer, then let them through. */
	static final String PASS_HAIL = PREFIX + "pass.hail";
	static final String PASS_THROUGH = PREFIX + "pass.through";

	/** A scanning patrol on a cargo run (5.9). */
	static final String SCAN_CLAN = PREFIX + "scan.clan";
	static final String SCAN_PIRATE = PREFIX + "scan.pirate";
	static final String SCAN_DONE = PREFIX + "scan.done";
	static final String SCAN_PROVOKED = PREFIX + "scan.provoked";

	/** A bounty's ace (5.8): a taunt (3), and a wingman when the ace falls. */
	static final String ACE_HAIL = PREFIX + "ace.hail.";
	static final int ACE_HAILS = 3;
	static final String ACE_DOWN = PREFIX + "ace.down";

	/** The aces' names ({@code bounty.oldrepublic.ace.<n>}). */
	static final String ACE_NAME = "bounty.oldrepublic.ace.";
	static final int ACE_NAMES = 8;

	/** A patrol's hail for fighters of {@code kind}. */
	static String hail(SpaceRules.Kind kind, RandomSource random) {
		return switch (kind) {
			case HYMN_INTERCEPTOR -> HYMN_HAIL + random.nextInt(HYMN_HAILS);
			case HASK_FIGHTER -> CLAN_HAIL + random.nextInt(CLAN_HAILS);
			default -> PIRATE_HAIL + random.nextInt(PIRATE_HAILS);
		};
	}

	/** Who speaks for fighters of {@code kind}. */
	static int speaker(SpaceRules.Kind kind) {
		return switch (kind) {
			case HYMN_INTERCEPTOR, GUNSHIP, GENERATOR -> SpaceEffects.SPEAKER_HYMN_PILOT;
			case HASK_FIGHTER -> SpaceEffects.SPEAKER_CLAN_PILOT;
			default -> SpaceEffects.SPEAKER_PIRATE;
		};
	}

	private SpaceComms() {
	}
}
