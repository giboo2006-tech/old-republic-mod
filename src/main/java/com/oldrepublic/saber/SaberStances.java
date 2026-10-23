package com.oldrepublic.saber;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Which form a player fights in, for everyone who sees them (spec MILESTONE_5.md 10.1, 19.2: the
 * Makashi, Djem So and Jar'Kai stances). The skills attachment that holds the chosen form is synced
 * to its owner only, so the chosen form's ordinal travels to every tracking client in its own small
 * attachment, kept current from the server tick ({@link #update}); absent means Unformed. Not
 * persistent: the tick sets it again after a login or respawn. The client draws the stance from it.
 */
public final class SaberStances {
	/** The chosen {@link SaberForm}'s ordinal; synced to everyone, not saved. */
	public static final AttachmentType<Integer> FORM = AttachmentRegistry.create(
			OldRepublicMod.id("saber_stance"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Loads the class, registering {@link #FORM} on both sides. */
	static void init() {
	}

	/** Server tick: keeps {@link #FORM} equal to the player's chosen form (only a change is sent). */
	static void update(ServerPlayer player) {
		SaberForm form = SaberPassives.selectedForm(player);
		Integer current = player.getAttached(FORM);

		if (form == SaberForm.UNFORMED) {
			if (current != null) {
				player.removeAttached(FORM);
			}
		} else if (current == null || current != form.ordinal()) {
			player.setAttached(FORM, form.ordinal());
		}
	}

	/**
	 * The form whose stance {@code entity} shows: a player's chosen form, except that Jar'Kai shows only
	 * with two lit blades (it acts as Unformed otherwise, 10.1). Everything else is Unformed.
	 */
	public static SaberForm stance(LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return SaberForm.UNFORMED;
		}

		Integer ordinal = player.getAttached(FORM);
		SaberForm[] forms = SaberForm.values();
		SaberForm form = ordinal != null && ordinal >= 0 && ordinal < forms.length ? forms[ordinal] : SaberForm.UNFORMED;
		return form == SaberForm.JAR_KAI && !SaberDual.isDualWielding(player) ? SaberForm.UNFORMED : form;
	}

	private SaberStances() {
	}
}
