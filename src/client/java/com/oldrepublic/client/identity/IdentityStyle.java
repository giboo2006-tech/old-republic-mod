package com.oldrepublic.client.identity;

import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;

/** Colours of the identity feature's UI: the two sides, the factions and the tiers. */
final class IdentityStyle {
	/** Pale gold-white: the Light end of the alignment bar and the Jedi tint. */
	static final int LIGHT = 0xFFF3E3A6;
	/** Deep red: the Dark end of the alignment bar and the Sith tint. */
	static final int DARK = 0xFFD0463A;
	static final int LIGHT_DEEP = 0xFF9C8A52;
	static final int DARK_DEEP = 0xFF2A0808;
	static final int FRONTIER = 0xFFC9A66B;
	/** Pale Republic blue. */
	static final int REPUBLIC = 0xFF8FB8E8;

	static int band(AlignmentBand band) {
		return switch (band) {
			case LIGHT, LEANING_LIGHT -> LIGHT;
			case DARK, LEANING_DARK -> DARK;
			case BALANCED -> UiColors.HOLO;
		};
	}

	static int faction(Faction faction) {
		return switch (faction) {
			case JEDI_ORDER -> LIGHT;
			case SITH_EMPIRE -> DARK;
			case FRONTIER -> FRONTIER;
			case REPUBLIC -> REPUBLIC;
		};
	}

	static int path(Path path) {
		return switch (path) {
			case JEDI -> LIGHT;
			case SITH -> DARK;
			case INDEPENDENT -> FRONTIER;
			case NONE -> UiColors.HOLO;
		};
	}

	static int tier(ReputationTier tier) {
		return switch (tier) {
			case HOSTILE -> UiColors.DANGER;
			case DISTRUSTED -> UiColors.AMBER;
			case NEUTRAL -> UiColors.TEXT_MUTED;
			case TRUSTED -> UiColors.OK;
			case HONOURED -> UiColors.RESONANCE;
		};
	}

	private IdentityStyle() {
	}
}
