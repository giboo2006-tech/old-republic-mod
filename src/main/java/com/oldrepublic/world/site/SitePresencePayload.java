package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the site piece the player now stands in (spec 3.7, sent by {@link SiteTracker}
 * whenever it changes). The client's site ambience plays the matching bed.
 *
 * @param kind     the {@link SiteKind} ordinal, or -1 for none
 * @param template the piece's template id, or "" for none
 */
public record SitePresencePayload(int kind, String template) implements CustomPacketPayload {
	public static final Type<SitePresencePayload> TYPE = new Type<>(OldRepublicMod.id("site_presence"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SitePresencePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SitePresencePayload::kind,
			ByteBufCodecs.STRING_UTF8, SitePresencePayload::template,
			SitePresencePayload::new);
	/** The payload for "in no site". */
	public static final SitePresencePayload NONE = new SitePresencePayload(-1, "");

	/** The payload for a piece, or {@link #NONE}. */
	public static SitePresencePayload of(@Nullable SitePiece piece) {
		return piece == null ? NONE : new SitePresencePayload(piece.kind().ordinal(), piece.template().toString());
	}

	/** The site kind, or null for none or an ordinal this client does not know. */
	public @Nullable SiteKind siteKind() {
		return kind >= 0 && kind < SiteKind.values().length ? SiteKind.values()[kind] : null;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
