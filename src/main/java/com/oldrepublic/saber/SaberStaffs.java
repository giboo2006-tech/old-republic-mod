package com.oldrepublic.saber;

import com.oldrepublic.combat.WeaponActions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberComponents.StaffMode;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The saberstaff's rules beyond its stats (spec MILESTONE_4.md 5.6): the Weapon Action's single
 * blade, and with both blades lit the wider guard (the back blade covers {@code staffConeDegrees}
 * more) and the heavier drain on each block ({@code staffGuardDrainPct}). The whirl is
 * {@link SaberTechniques}'s and the throw {@code ThrownSaber}'s.
 */
public final class SaberStaffs {
	/** Whether {@code stack} is a saberstaff. */
	public static boolean isStaff(ItemStack stack) {
		return stack.getItem() instanceof SaberStaffItem;
	}

	/** A saberstaff with its second blade on (whether or not the staff is lit). */
	public static boolean bothBlades(ItemStack stack) {
		return isStaff(stack) && !SaberComponents.staffMode(stack).single();
	}

	/** The guard arc for {@code saber}: the form's, plus {@code staffConeDegrees} for a lit staff with both blades. */
	public static float guardArc(float formArc, ItemStack saber, SaberBalance b) {
		return bothBlades(saber) && SaberComponents.isIgnited(saber) ? formArc + b.staffConeDegrees : formArc;
	}

	/** The multiplier on guard drain for {@code saber}: {@code 1 + staffGuardDrainPct} for a staff with both blades. */
	public static float drainMult(ItemStack saber, SaberBalance b) {
		return bothBlades(saber) ? 1f + b.staffGuardDrainPct / 100f : 1f;
	}

	/**
	 * The Weapon Action on a held staff: retracts or ignites the second blade. The stats follow at
	 * once (a normal saber's damage and parry window on one blade); igniting it plays the second
	 * blade's ignition to everyone tracking the player.
	 */
	public static void toggleSingle(ServerPlayer p, ItemStack staff) {
		if (!isStaff(staff) || SaberComponents.isThrown(staff) || SaberTechniques.isBusy(p)) {
			return;
		}

		long now = p.level().getGameTime();
		boolean single = !SaberComponents.staffMode(staff).single();
		staff.set(SaberComponents.STAFF_MODE, new StaffMode(single, now));
		boolean ignited = SaberComponents.state(staff).ignited();
		// Keeps the ignition (and its animation clock) and rewrites the attribute modifiers for the new blade count.
		SaberToggle.refreshModifiers(staff, ignited);

		if (ignited) {
			EffectEvents.toTracking(p, SaberEffects.SABER_STAFF, p.getEyePosition(), p.getLookAngle(), p.getId(), single ? 0f : 1f,
					SaberEffects.STAFF_SECOND_BLADE);
		}
	}

	static void init() {
		WeaponActions.register(SaberStaffs::isStaff, (p, held, pressed) -> {
			if (pressed) {
				toggleSingle(p, held);
			}
		});
	}

	private SaberStaffs() {
	}
}
