package com.oldrepublic.client.space;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.space.BlockadeGunship;
import com.oldrepublic.space.GunshipGenerator;
import com.oldrepublic.space.ShipWeapons;
import com.oldrepublic.space.SpaceRules;
import com.oldrepublic.space.Starfighter;
import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.space.network.SpaceEncounterPayload;
import com.oldrepublic.travel.ShipBody;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The combat HUD (spec MILESTONE_5.md 5.10, 13.2), shown while the local player pilots or guns,
 * beside W1's flight HUD:
 *
 * <ul>
 * <li>a panel on the left: the shield bar, the heat bar of the seat's guns (the cannons for the
 * pilot, the turret for the gunner) with its overheat lock, and the missile count with the lock's
 * state;</li>
 * <li>brackets on ship targets within 160 blocks (red hostile, green allied, white neutral), with
 * the name, distance, hull and shield of the one nearest the crosshair, its lead pip and the
 * boresight of the seat's guns, so laying the boresight on the pip is a hit;</li>
 * <li>the missile lock ring closing around the locked target (amber while locking, red once
 * locked);</li>
 * <li>a red edge pulse and a warning line while a missile locks on or flies at the ship (a steady
 * glow with flashing effects off);</li>
 * <li>the scan meter under the flight HUD's strip while a patrol scans the ship.</li>
 * </ul>
 *
 * The objective point of a run or a bounty (5.9) is marked from anywhere in its orbit, whatever the
 * seat, and the encounter's comm lines are subtitles in the M4 bark style. Every number comes from
 * the server's status; the brackets read the synced shields, sides and health.
 */
final class CombatHud {
	/** Ship targets are bracketed within this range (5.10). */
	private static final double BRACKET_RANGE = 160;
	private static final int MAX_BRACKETS = 24;
	/** The boresight's distance when nothing is focused. */
	private static final double BORESIGHT_DISTANCE = 80;
	private static final int PANEL_W = 104;
	private static final int PANEL_X = 6;
	private static final int BAR_H = 5;
	/** The scan meter sits under W1's flight strip at the top. */
	private static final int SCAN_Y = 44;
	private static final int SCAN_W = 140;
	/** The comm line sits just above where the troopers' barks show. */
	private static final int COMM_BOTTOM_OFFSET = 98;
	private static final int COMM_FADE_IN = 4;
	private static final int COMM_FADE_OUT = 14;
	private static final int HOSTILE = 0xFFE8584A;
	private static final int ALLIED = 0xFF8FE3A0;
	private static final int NEUTRAL = 0xFFE6EEF4;
	private static final int SHIELD_TOP = 0xFF9EE4FF;
	private static final int SHIELD_BOTTOM = 0xFF3E8CC0;
	private static final int HULL_TOP = 0xFFE8D6A8;
	private static final int HULL_BOTTOM = 0xFF9A7A4A;
	private static final int SHADOW = 0xA0000000;

	/** The ship targets near enough to bracket, gathered every client tick. */
	private static final List<Mob> TARGETS = new ArrayList<>();

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("combat_hud"), CombatHud::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, OldRepublicMod.id("space_comm"), CombatHud::extractComm);
	}

	/** Client tick: the ship targets within bracket range, nearest first. */
	static void tick(Minecraft mc) {
		TARGETS.clear();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;

		if (player == null || level == null || !SpaceClientState.active()) {
			return;
		}

		Vec3 at = player.position();
		AABB box = player.getBoundingBox().inflate(BRACKET_RANGE + BlockadeGunship.REACH);
		List<Mob> found = level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && isShipTarget(e) && e.distanceToSqr(at) <= BRACKET_RANGE * BRACKET_RANGE);
		found.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
		TARGETS.addAll(found.subList(0, Math.min(MAX_BRACKETS, found.size())));
	}

	private static boolean isShipTarget(Entity e) {
		return e instanceof Starfighter || e instanceof BlockadeGunship || e instanceof GunshipGenerator;
	}

	static void clear() {
		TARGETS.clear();
	}

	// ---------------------------------------------------------------- the HUD element

	private static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null || mc.gui.hud.isHidden()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float time = SpaceClientState.ticks() + partial;
		View view = View.of(mc, g);
		SpaceEncounterPayload encounter = SpaceClientState.encounter();

		if (view != null && encounter.point().isPresent()) {
			objective(g, mc.font, view, encounter.kind(), encounter.point().get());
		}

		ShipCombatStatusPayload status = SpaceClientState.status();

		if (status.role() == ShipCombatStatusPayload.NONE) {
			return;
		}

		ShipBody ship = SpaceCrew.ship();

		if (view != null) {
			Mob focus = brackets(g, mc.font, view, partial);

			if (focus != null) {
				aim(g, view, player, ship, focus, status, partial);
			} else if (ship != null) {
				boresight(g, view, gunMount(ship, status, partial), gunAim(player, ship, status, partial).scale(BORESIGHT_DISTANCE));
			}

			lockRing(g, mc, view, status, partial, time);
		}

		panel(g, mc.font, status, time);

		if (status.scan() >= 0f) {
			scanMeter(g, mc.font, status.scan(), time);
		}

		if (SpaceLoops.incoming()) {
			incoming(g, mc.font, time);
		}
	}

	// ---------------------------------------------------------------- brackets, the pip and the lock

	/** Brackets every target on screen; returns the one nearest the crosshair, whose readout is drawn. */
	private static @Nullable Mob brackets(GuiGraphicsExtractor g, Font font, View view, float partial) {
		Mob focus = null;
		Screen focusAt = null;
		double best = Double.MAX_VALUE;

		for (Mob target : TARGETS) {
			if (target.isRemoved() || !target.isAlive()) {
				continue;
			}

			Vec3 centre = centre(target, partial);
			Screen at = view.project(centre);

			if (at == null) {
				continue;
			}

			int r = Mth.clamp((int) Math.round(radius(target) / at.depth() * view.scale()), 5, 64);

			if (at.x() < -r || at.y() < -r || at.x() > g.guiWidth() + r || at.y() > g.guiHeight() + r) {
				continue;
			}

			int colour = colourOf(target);
			corners(g, Math.round(at.x()), Math.round(at.y()), r, colour, 0.9f);
			double off = Mth.square(at.x() - view.cx()) + Mth.square(at.y() - view.cy());

			if (off < best) {
				best = off;
				focus = target;
				focusAt = at;
			}
		}

		if (focus != null && focusAt != null) {
			readout(g, font, focus, focusAt, view, partial);
		}

		return focus;
	}

	/** The focused target's name and distance over its bracket, its shield and hull under it. */
	private static void readout(GuiGraphicsExtractor g, Font font, Mob target, Screen at, View view, float partial) {
		int r = Mth.clamp((int) Math.round(radius(target) / at.depth() * view.scale()), 5, 64);
		int x = Math.round(at.x());
		int y = Math.round(at.y());
		int colour = colourOf(target);
		Component name = target.getDisplayName();
		String distance = Component.translatable("hud.oldrepublic.combat.distance", Math.round(centre(target, partial).distanceTo(view.eye()))).getString();
		int top = y - r - 3 - font.lineHeight;
		Ui.text(g, font, name, x - font.width(name) / 2, top, colour, 0.95f);
		Ui.text(g, font, distance, x + r + 4, y - font.lineHeight / 2, UiColors.TEXT_MUTED, 0.9f);

		int w = Math.max(30, r * 2);
		int bx = x - w / 2;
		int by = y + r + 4;

		if (target instanceof Starfighter fighter && fighter.shieldMax() > 0f) {
			Ui.bar(g, bx, by, w, 3, fighter.shield() / fighter.shieldMax(), SHIELD_TOP, SHIELD_BOTTOM, 0, 0.9f);
			by += 5;
		} else if (target instanceof BlockadeGunship gunship && gunship.shielded()) {
			Component shielded = Component.translatable("hud.oldrepublic.combat.shielded");
			Ui.text(g, font, shielded, x - font.width(shielded) / 2, by, SHIELD_TOP, 0.9f);
			by += font.lineHeight + 1;
		}

		Ui.bar(g, bx, by, w, 3, target.getHealth() / Math.max(1f, target.getMaxHealth()), HULL_TOP, HULL_BOTTOM, 0, 0.9f);
	}

	/**
	 * The lead pip on the focused target (where to point the guns to meet it at the bolts' speed,
	 * against the ship's own motion, which the bolts inherit), and the guns' boresight at the same
	 * distance: the two meet on screen when the shot is good.
	 */
	private static void aim(GuiGraphicsExtractor g, View view, LocalPlayer player, @Nullable ShipBody ship, Mob target, ShipCombatStatusPayload status,
			float partial) {
		if (ship == null) {
			return;
		}

		Vec3 mount = gunMount(ship, status, partial);
		Vec3 centre = centre(target, partial);
		Vec3 relative = SpaceFx.motion(target).subtract(SpaceFx.motion(ship));
		Vec3 lead = SpaceRules.lead(mount, centre, relative, Math.max(0.5f, status.boltSpeed()));
		double distance = Math.max(4, lead.distanceTo(mount));
		boresight(g, view, mount, gunAim(player, ship, status, partial).scale(distance));

		if (target instanceof Starfighter fighter && fighter.side() == SpaceRules.Side.ALLIED) {
			return;
		}

		Screen pip = view.project(lead);

		if (pip != null) {
			int x = Math.round(pip.x());
			int y = Math.round(pip.y());
			int colour = colourOf(target);

			// A small diamond, shadowed so it reads on the planet and the stars.
			for (int i = 0; i <= 3; i++) {
				g.fill(x - 3 + i, y - i - 1, x + 4 - i, y - i + 1, SHADOW);
				g.fill(x - 3 + i, y + i - 1, x + 4 - i, y + i + 1, SHADOW);
			}

			for (int i = 0; i <= 2; i++) {
				g.fill(x - 2 + i, y - i, x + 3 - i, y - i + 1, colour);
				g.fill(x - 2 + i, y + i, x + 3 - i, y + i + 1, colour);
			}
		}
	}

	/**
	 * Where the seat's guns sit, for the lead pip: between the twin cannons beside the nose, or the
	 * turret on the back (the ship class's own points, where the server fires from).
	 */
	private static Vec3 gunMount(ShipBody ship, ShipCombatStatusPayload status, float partial) {
		double[] at = status.role() == ShipCombatStatusPayload.GUNNER ? ship.turret() : ship.cannons()[0];
		double right = status.role() == ShipCombatStatusPayload.GUNNER ? at[0] : 0;
		return ship.getPosition(partial).add(ShipBody.local(right, at[1], at[2], ship.getYRot(partial), ship.getXRot(partial)));
	}

	/** Where the seat's guns point (a unit vector): the ship's nose, or the gunner's look as the turret can follow it. */
	private static Vec3 gunAim(LocalPlayer player, ShipBody ship, ShipCombatStatusPayload status, float partial) {
		if (status.role() == ShipCombatStatusPayload.GUNNER) {
			return ShipWeapons.turretAim(player.getXRot(partial), player.getYRot(partial));
		}

		return ShipBody.local(0, 0, 1, ship.getYRot(partial), ship.getXRot(partial)).normalize();
	}

	/** The guns' boresight: a small open ring where a shot from {@code mount} along {@code reach} goes. */
	private static void boresight(GuiGraphicsExtractor g, View view, Vec3 mount, Vec3 reach) {
		Screen at = view.project(mount.add(reach));

		if (at == null) {
			return;
		}

		int x = Math.round(at.x());
		int y = Math.round(at.y());
		int colour = Ui.a(UiColors.HOLO_BRIGHT, 0.85f);

		for (int[] side : new int[][] {{-6, -1, -4, 2}, {5, -1, 7, 2}, {-1, -6, 2, -4}, {-1, 5, 2, 7}}) {
			g.fill(x + side[0] - 1, y + side[1] - 1, x + side[2] + 1, y + side[3] + 1, SHADOW);
			g.fill(x + side[0], y + side[1], x + side[2], y + side[3], colour);
		}
	}

	/** The missile lock's ring around its target: closing amber while it locks, red and labelled once locked. */
	private static void lockRing(GuiGraphicsExtractor g, Minecraft mc, View view, ShipCombatStatusPayload status, float partial, float time) {
		if (status.lockTarget() < 0 || mc.level == null || !(mc.level.getEntity(status.lockTarget()) instanceof Mob target)) {
			return;
		}

		Screen at = view.project(centre(target, partial));

		if (at == null) {
			return;
		}

		int r = Mth.clamp((int) Math.round(radius(target) / at.depth() * view.scale()), 5, 64) + 6;
		r += r % 2;
		boolean locked = status.lockProgress() >= 1f;
		int lit = locked ? UiColors.DANGER : UiColors.AMBER;
		float alpha = locked ? 0.6f + 0.4f * UiAnim.pulse(time, 8f) : 0.9f;
		int x = Math.round(at.x());
		int y = Math.round(at.y());
		Ui.ring(g, x - r, y - r, r * 2, r * 2, 4, locked ? 1f : status.lockProgress(), lit, 0x40FFFFFF, alpha);

		if (locked) {
			Component label = Component.translatable("hud.oldrepublic.combat.locked");
			Ui.text(g, mc.font, label, x - mc.font.width(label) / 2, y + r + 2, UiColors.DANGER, alpha);
		}
	}

	// ---------------------------------------------------------------- the panel, the scan and the warning

	private static void panel(GuiGraphicsExtractor g, Font font, ShipCombatStatusPayload status, float time) {
		boolean gunner = status.role() == ShipCombatStatusPayload.GUNNER;
		boolean missiles = !gunner && status.missiles() >= 0;
		int rows = missiles ? 3 : 2;
		int rowH = font.lineHeight + BAR_H + 4;
		int x = PANEL_X;
		int y = Math.min(g.guiHeight() / 2 + 18, g.guiHeight() - rows * rowH - 30);
		Ui.plate(g, x - 3, y - 3, PANEL_W + 6, rows * rowH + 3, 0.85f);

		// The shield.
		Component shieldLabel = Component.translatable("hud.oldrepublic.combat.shield");
		Ui.text(g, font, shieldLabel, x, y, UiColors.HOLO, 0.95f);

		if (status.shieldMax() > 0f) {
			String value = Math.round(status.shield()) + "/" + Math.round(status.shieldMax());
			Ui.text(g, font, value, x + PANEL_W - font.width(value), y, UiColors.TEXT_MUTED, 0.95f);
			Ui.bar(g, x, y + font.lineHeight + 1, PANEL_W, BAR_H, status.shield() / status.shieldMax(), SHIELD_TOP, SHIELD_BOTTOM, 4, 0.95f);
		} else {
			Component none = Component.translatable("hud.oldrepublic.combat.no_shield");
			Ui.text(g, font, none, x + PANEL_W - font.width(none), y, UiColors.TEXT_DIM, 0.95f);
			Ui.bar(g, x, y + font.lineHeight + 1, PANEL_W, BAR_H, 0f, SHIELD_TOP, SHIELD_BOTTOM, 4, 0.6f);
		}

		// The seat's guns: their heat, and the lock an overheat puts on them.
		y += rowH;
		float heat = Mth.clamp((gunner ? status.turretHeat() : status.cannonHeat()) / 100f, 0f, 1f);
		boolean jammed = gunner ? status.turretLocked() : status.cannonLocked();
		Component guns = Component.translatable(gunner ? "hud.oldrepublic.combat.turret" : "hud.oldrepublic.combat.cannons");
		Ui.text(g, font, guns, x, y, UiColors.AMBER, 0.95f);

		if (jammed) {
			Component overheat = Component.translatable("hud.oldrepublic.combat.overheat");
			Ui.text(g, font, overheat, x + PANEL_W - font.width(overheat), y, UiColors.DANGER, 0.55f + 0.45f * UiAnim.pulse(time, 10f));
		}

		int top = heat < 0.7f && !jammed ? UiColors.AMBER_BRIGHT : UiColors.DANGER;
		int bottom = heat < 0.7f && !jammed ? UiColors.AMBER_DIM : 0xFF8A2A22;
		Ui.bar(g, x, y + font.lineHeight + 1, PANEL_W, BAR_H, heat, top, bottom, 10, 0.95f);

		// The missiles and the lock.
		if (missiles) {
			y += rowH;
			Component label = Component.translatable("hud.oldrepublic.combat.missiles", status.missiles());
			Ui.text(g, font, label, x, y, status.missiles() > 0 ? UiColors.TEXT : UiColors.TEXT_DIM, 0.95f);
			Component lock = lockState(status);

			if (lock != null) {
				boolean held = status.lockProgress() >= 1f;
				Ui.text(g, font, lock, x + PANEL_W - font.width(lock), y, held ? UiColors.DANGER : UiColors.AMBER, 0.95f);
			}

			Ui.pips(g, x, y + font.lineHeight + 1, Math.min(10, Math.max(1, status.missiles())), Math.min(10, status.missiles()), UiColors.AMBER, 0.95f);
		}
	}

	/** "Locking 40%" or "Locked", or null without a lock. */
	private static @Nullable Component lockState(ShipCombatStatusPayload status) {
		if (status.lockTarget() < 0) {
			return null;
		}

		if (status.lockProgress() >= 1f) {
			return Component.translatable("hud.oldrepublic.combat.locked");
		}

		return Component.translatable("hud.oldrepublic.combat.locking", Math.round(status.lockProgress() * 100));
	}

	/** The scan meter (5.9) under the flight strip: amber, turning red as it nears full. */
	private static void scanMeter(GuiGraphicsExtractor g, Font font, float scan, float time) {
		int x = (g.guiWidth() - SCAN_W) / 2;
		int y = SCAN_Y;
		boolean close = scan > 0.75f;
		Component label = Component.translatable("hud.oldrepublic.combat.scan", Math.round(scan * 100));
		float alpha = close ? 0.6f + 0.4f * UiAnim.pulse(time, 8f) : 0.95f;
		Ui.text(g, font, label, (g.guiWidth() - font.width(label)) / 2, y, close ? UiColors.DANGER : UiColors.AMBER, alpha);
		Ui.bar(g, x, y + font.lineHeight + 1, SCAN_W, BAR_H, scan, close ? UiColors.DANGER : UiColors.AMBER_BRIGHT, close ? 0xFF8A2A22 : UiColors.AMBER_DIM,
				10, 0.95f);
	}

	/** A missile locks on or flies at the ship: a red pulse at the screen's edges and a warning line (steady with flashing off). */
	private static void incoming(GuiGraphicsExtractor g, Font font, float time) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float pulse = Vfx.flashing() ? 0.35f + 0.65f * UiAnim.pulse(time, 9f) : 0.6f;
		int band = Math.max(12, h / 14);

		for (int i = 0; i < band; i += 2) {
			float fade = pulse * 0.45f * (1f - (float) i / band);
			int colour = Ui.a(0xFFD02A20, fade);
			g.fill(0, i, w, i + 2, colour);
			g.fill(0, h - i - 2, w, h - i, colour);
			g.fill(i, band, i + 2, h - band, colour);
			g.fill(w - i - 2, band, w - i, h - band, colour);
		}

		Component warning = Component.translatable("hud.oldrepublic.combat.incoming");
		Ui.text(g, font, warning, (w - font.width(warning)) / 2, h / 2 - 46, UiColors.DANGER, 0.5f + 0.5f * pulse);
	}

	// ---------------------------------------------------------------- the objective point

	/** The run's buoy or the bounty's group: a marker with its distance, or an arrow at the screen's edge toward it. */
	private static void objective(GuiGraphicsExtractor g, Font font, View view, int kind, Vec3 point) {
		int colour = switch (kind) {
			case SpaceEncounterPayload.RUN -> UiColors.OK;
			case SpaceEncounterPayload.CLEAR -> UiColors.AMBER;
			default -> UiColors.DANGER;
		};
		int w = g.guiWidth();
		int h = g.guiHeight();
		Screen at = view.project(point);
		String distance = Component.translatable("hud.oldrepublic.combat.distance", Math.round(point.distanceTo(view.eye()))).getString();

		if (at != null && at.x() > 12 && at.x() < w - 12 && at.y() > 12 && at.y() < h - 12) {
			int x = Math.round(at.x());
			int y = Math.round(at.y());

			for (int i = 0; i < 4; i++) {
				g.fill(x - 4 + i, y - i - 1, x + 5 - i, y - i, Ui.a(colour, 0.9f));
				g.fill(x - 4 + i, y + i, x + 5 - i, y + i + 1, Ui.a(colour, 0.9f));
			}

			Component label = Component.translatable("hud.oldrepublic.combat.objective." + objectiveName(kind));
			Ui.text(g, font, label, x - font.width(label) / 2, y - 8 - font.lineHeight, colour, 0.9f);
			Ui.text(g, font, distance, x - font.width(distance) / 2, y + 7, UiColors.TEXT_MUTED, 0.9f);
			return;
		}

		// Off screen or behind: an arrow on the edge, pointing the way to turn.
		Vec3 d = point.subtract(view.eye());
		double sx = d.dot(view.right());
		double sy = -d.dot(view.up());

		if (Math.abs(sx) < 1.0e-6 && Math.abs(sy) < 1.0e-6) {
			sy = 1;
		}

		double angle = Math.atan2(sy, sx);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double halfW = w / 2.0 - 14;
		double halfH = h / 2.0 - 14;
		double t = Math.min(halfW / Math.max(1.0e-6, Math.abs(cos)), halfH / Math.max(1.0e-6, Math.abs(sin)));
		int x = (int) Math.round(w / 2.0 + cos * t);
		int y = (int) Math.round(h / 2.0 + sin * t);

		for (int i = 0; i < 5; i++) {
			int px = (int) Math.round(x - cos * i);
			int py = (int) Math.round(y - sin * i);
			int span = i;
			int nx = (int) Math.round(-sin * span);
			int ny = (int) Math.round(cos * span);
			g.fill(Math.min(px - nx, px + nx), Math.min(py - ny, py + ny), Math.max(px - nx, px + nx) + 1, Math.max(py - ny, py + ny) + 1,
					Ui.a(colour, 0.9f));
		}

		int tx = Mth.clamp(x - font.width(distance) / 2, 2, w - font.width(distance) - 2);
		int ty = Mth.clamp(y + (sin > 0 ? -14 : 7), 2, h - font.lineHeight - 2);
		Ui.text(g, font, distance, tx, ty, UiColors.TEXT_MUTED, 0.85f);
	}

	private static String objectiveName(int kind) {
		return switch (kind) {
			case SpaceEncounterPayload.RUN -> "run";
			case SpaceEncounterPayload.CLEAR -> "clear";
			case SpaceEncounterPayload.BLOCKADE, SpaceEncounterPayload.PASS -> "blockade";
			default -> "patrol";
		};
	}

	// ---------------------------------------------------------------- the comm line

	/** The encounter's comm line, low in the centre, in the M4 bark style (hidden with the barks). */
	private static void extractComm(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		SpaceClientState.Comm line = SpaceClientState.comm();

		if (line == null || mc.player == null || mc.gui.hud.isHidden() || !ClientConfig.get().showBarks) {
			return;
		}

		float age = SpaceClientState.ticks() - line.start() + deltaTracker.getGameTimeDeltaPartialTick(true);
		float alpha = Math.min(Mth.clamp(age / COMM_FADE_IN, 0f, 1f), Mth.clamp((SpaceClientState.COMM_TICKS - age) / COMM_FADE_OUT, 0f, 1f));

		if (alpha <= 0f) {
			return;
		}

		Font font = mc.font;
		int tagWidth = font.width(line.tag());
		int width = tagWidth + font.width(line.text());
		int cx = g.guiWidth() / 2;
		int x = cx - width / 2;
		int y = g.guiHeight() - COMM_BOTTOM_OFFSET;

		// The bark style: a soft dark well behind the text, edged by a rule in the speaker's colour.
		g.fill(x - 6, y - 3, x + width + 6, y + font.lineHeight + 2, Ui.a(UiColors.WELL, 0.55f * alpha));
		Ui.fadeLine(g, cx, y + font.lineHeight + 2, width / 2 + 10, line.tint(), 0.6f * alpha);
		Ui.text(g, font, line.tag(), x, y, line.tint(), alpha);
		Ui.text(g, font, line.text(), x + tagWidth, y, UiColors.TEXT, alpha);
	}

	// ---------------------------------------------------------------- geometry

	/** A target's centre at {@code partial}. */
	private static Vec3 centre(Entity target, float partial) {
		return target.getPosition(partial).add(0, target.getBbHeight() / 2, 0);
	}

	/** A target's size for its bracket (blocks from its centre): the gunship's hull, not its square box. */
	private static double radius(Entity target) {
		if (target instanceof BlockadeGunship) {
			return BlockadeGunship.HALF_LENGTH * 0.8;
		}

		return Math.max(target.getBbWidth(), target.getBbHeight()) * 0.6;
	}

	private static int colourOf(Entity target) {
		if (target instanceof Starfighter fighter) {
			return switch (fighter.side()) {
				case ALLIED -> ALLIED;
				case NEUTRAL -> NEUTRAL;
				default -> HOSTILE;
			};
		}

		return HOSTILE;
	}

	/** Four corner brackets {@code r} pixels out from ({@code x}, {@code y}), shadowed. */
	private static void corners(GuiGraphicsExtractor g, int x, int y, int r, int colour, float alpha) {
		int arm = Math.max(3, r / 3);
		int c = Ui.a(colour, alpha);

		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int cx = x + sx * r;
				int cy = y + sy * r;
				int hx0 = Math.min(cx, cx - sx * arm);
				int hx1 = Math.max(cx, cx - sx * arm) + 1;
				int vy0 = Math.min(cy, cy - sy * arm);
				int vy1 = Math.max(cy, cy - sy * arm) + 1;
				g.fill(hx0 - 1, cy - 1, hx1 + 1, cy + 2, SHADOW);
				g.fill(cx - 1, vy0 - 1, cx + 2, vy1 + 1, SHADOW);
				g.fill(hx0, cy, hx1, cy + 1, c);
				g.fill(cx, vy0, cx + 1, vy1, c);
			}
		}
	}

	/** A point on screen and its depth in front of the camera. */
	private record Screen(float x, float y, double depth) {
	}

	/**
	 * The camera for projecting world points onto the HUD: its eye, its axes and the pixels per unit
	 * of the tangent (the vertical field of view across the GUI's height).
	 */
	private record View(Vec3 eye, Vec3 forward, Vec3 up, Vec3 right, double scale, float cx, float cy) {
		static @Nullable View of(Minecraft mc, GuiGraphicsExtractor g) {
			Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.mainCamera();

			if (camera == null || !camera.isInitialized()) {
				return null;
			}

			// Probed (tools/api-probe/m5_w2.txt): Camera#setRotation turns FORWARDS, UP and LEFT by the view's yaw and pitch into these world axes.
			Vector3fc left = camera.leftVector();
			// The frame's own field of view (speed and boost widen it), else the options' setting.
			// Probed (tools/api-probe/m5_w2.txt, force.txt): Camera#update stores calculateFov for getFov and setupPerspective: the options'
			// fov in degrees times the lerped fov modifier, then the death and fluid change.
			float frameFov = camera.getFov();
			double fov = frameFov > 1f && frameFov < 179f ? frameFov : mc.options.fov().get();
			double halfFov = Math.toRadians(fov / 2.0);
			double scale = g.guiHeight() / 2.0 / Math.tan(halfFov);
			return new View(camera.position(), vec(camera.forwardVector()), vec(camera.upVector()), new Vec3(-left.x(), -left.y(), -left.z()), scale,
					g.guiWidth() / 2f, g.guiHeight() / 2f);
		}

		private static Vec3 vec(Vector3fc v) {
			return new Vec3(v.x(), v.y(), v.z());
		}

		/** Where {@code point} shows on screen, or null behind the camera. */
		@Nullable Screen project(Vec3 point) {
			Vec3 d = point.subtract(eye);
			double depth = d.dot(forward);

			if (depth < 0.1) {
				return null;
			}

			return new Screen((float) (cx + d.dot(right) / depth * scale), (float) (cy - d.dot(up) / depth * scale), depth);
		}
	}

	private CombatHud() {
	}
}
