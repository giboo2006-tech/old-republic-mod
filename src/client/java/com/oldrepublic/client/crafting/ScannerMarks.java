package com.oldrepublic.client.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scanner's survey marks (spec 7.6): for a while after a pulse, planet resources and creatures
 * within its range are marked without outlines, by small dust motes hovering over them and an arrow
 * at the HUD edge pointing toward each. Creatures are found at the pulse; blocks are surveyed over
 * {@value #SCAN_TICKS} ticks in slices so a 48-block pulse never stalls a frame. The marks are a
 * client view only: the server already counted the survey.
 */
final class ScannerMarks {
	private static final int SCAN_TICKS = 40;
	private static final int VERTICAL = 24;
	private static final int MAX_BLOCKS = 24;
	private static final int MAX_CREATURES = 24;
	private static final int MAX_ARROWS = 12;
	private static final int FADE_TICKS = 40;
	private static final int MOTE_EVERY = 8;
	private static final int RESOURCE = 0xFF8FD8FF;
	private static final int CREATURE = 0xFFFFB347;
	/** W1's planet resource blocks, by id (the bench must not depend on W1's classes). */
	private static final String[] RESOURCE_IDS = {"aurelite_ore", "deepslate_aurelite_ore", "duskglass_cluster", "budding_duskglass", "silverleaf", "ashroot",
			"wreck_salvage", "durasteel_ore"};

	private static @Nullable Set<Block> resources;
	private static final List<BlockPos> BLOCKS = new ArrayList<>();
	private static final List<Entity> CREATURES = new ArrayList<>();
	private static @Nullable ClientLevel level;
	private static BlockPos centre = BlockPos.ZERO;
	private static int radius;
	private static int scanned;
	private static int scanTotal;
	private static int age;
	private static int life;

	static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("scanner_marks"), ScannerMarks::extract);
	}

	/** Starts a survey of {@code range} blocks around {@code player}, shown for {@code ticks}. */
	static void start(ClientLevel lvl, LocalPlayer player, int range, int ticks) {
		clear();
		level = lvl;
		centre = player.blockPosition();
		radius = range;
		life = ticks;
		age = 0;
		scanned = 0;
		int side = range * 2 + 1;
		scanTotal = side * side * (Math.min(range, VERTICAL) * 2 + 1);

		AABB box = player.getBoundingBox().inflate(range);
		List<LivingEntity> found = lvl.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && !(e instanceof Player) && e.isAlive() && e.distanceTo(player) <= range);
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

		for (int i = 0; i < Math.min(MAX_CREATURES, found.size()); i++) {
			CREATURES.add(found.get(i));
		}
	}

	private static Set<Block> resourceBlocks() {
		if (resources == null) {
			Set<Block> set = new HashSet<>();

			for (String id : RESOURCE_IDS) {
				Block block = BuiltInRegistries.BLOCK.getValue(OldRepublicMod.id(id));

				if (block != Blocks.AIR) {
					set.add(block);
				}
			}

			resources = set;
		}

		return resources;
	}

	/** Client tick: the next slice of the block survey, the motes, and the end of the marks. */
	static void tick(Minecraft mc) {
		if (level == null) {
			return;
		}

		if (mc.level != level || mc.player == null) {
			clear();
			return;
		}

		age++;

		if (age > life) {
			clear();
			return;
		}

		scanSlice(mc.level);
		CREATURES.removeIf(e -> e.isRemoved() || !e.isAlive());

		if (age % MOTE_EVERY == 0) {
			DustParticleOptions blue = new DustParticleOptions(RESOURCE & 0xFFFFFF, 0.6f);
			DustParticleOptions amber = new DustParticleOptions(CREATURE & 0xFFFFFF, 0.6f);

			for (BlockPos pos : BLOCKS) {
				Vec3 at = Vec3.atCenterOf(pos).add(0, 0.75, 0);

				if (Vfx.inRange(at)) {
					mc.level.addParticle(blue, at.x, at.y, at.z, 0, 0.005, 0);
				}
			}

			for (Entity e : CREATURES) {
				Vec3 at = e.position().add(0, e.getBbHeight() + 0.35, 0);

				if (Vfx.inRange(at)) {
					mc.level.addParticle(amber, at.x, at.y, at.z, 0, 0.005, 0);
				}
			}
		}
	}

	private static void scanSlice(ClientLevel lvl) {
		if (scanned >= scanTotal || BLOCKS.size() >= MAX_BLOCKS) {
			return;
		}

		int side = radius * 2 + 1;
		int vertical = Math.min(radius, VERTICAL);
		int height = vertical * 2 + 1;
		int per = Mth.ceil(scanTotal / (float) SCAN_TICKS);
		int end = Math.min(scanTotal, scanned + per);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Set<Block> wanted = resourceBlocks();
		int before = BLOCKS.size();

		for (int i = scanned; i < end && BLOCKS.size() < MAX_BLOCKS; i++) {
			int dx = i % side - radius;
			int dz = (i / side) % side - radius;
			int dy = i / (side * side) - vertical;

			if (dy >= height - vertical || dx * dx + dz * dz + dy * dy > radius * radius) {
				continue;
			}

			// An unloaded chunk reads as air on the client, so no load check is needed.
			pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
			var state = lvl.getBlockState(pos);

			if (wanted.contains(state.getBlock()) || state.is(ConventionalBlockTags.ORES)) {
				BLOCKS.add(pos.immutable());
			}
		}

		scanned = end;

		if (BLOCKS.size() > before && Minecraft.getInstance().player != null) {
			QuestSounds.ui(ModSounds.SCANNER_PING, 1.1f + Math.min(0.5f, BLOCKS.size() * 0.02f), 0.35f);
		}
	}

	// ---------------------------------------------------------------- HUD arrows

	private static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (level == null || player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float alpha = Mth.clamp((life - age) / (float) FADE_TICKS, 0f, 1f);

		if (alpha <= 0.02f) {
			return;
		}

		record Target(Vec3 pos, int color, double dist) {
		}

		List<Target> targets = new ArrayList<>();

		for (BlockPos pos : BLOCKS) {
			Vec3 at = Vec3.atCenterOf(pos);
			targets.add(new Target(at, RESOURCE, at.distanceTo(player.position())));
		}

		for (Entity e : CREATURES) {
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			targets.add(new Target(at, CREATURE, at.distanceTo(player.position())));
		}

		targets.sort(Comparator.comparingDouble(Target::dist));
		int w = g.guiWidth();
		int h = g.guiHeight();
		float yaw = player.getYRot();

		for (int i = 0; i < Math.min(MAX_ARROWS, targets.size()); i++) {
			Target t = targets.get(i);
			Vec3 d = t.pos.subtract(player.position());
			// Bearing relative to the view, in degrees: 0 ahead, positive to the right.
			float bearing = Mth.wrapDegrees((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f - yaw);
			float near = 1f - Mth.clamp((float) (t.dist / Math.max(1, radius)), 0f, 1f);
			drawArrow(g, w, h, bearing, Ui.a(t.color, alpha * (0.45f + 0.55f * near)));
		}
	}

	/** An arrow on the screen's edge: along the top for targets ahead, on the sides for the rest. */
	private static void drawArrow(GuiGraphicsExtractor g, int w, int h, float bearing, int color) {
		int x;
		int y;
		int dir;

		if (Math.abs(bearing) <= 45f) {
			x = w / 2 + Math.round(bearing / 45f * (w / 2f - 24));
			y = 6;
			dir = 0;
		} else {
			float t = Mth.clamp((Math.abs(bearing) - 45f) / 135f, 0f, 1f);
			x = bearing > 0 ? w - 7 : 6;
			y = 24 + Math.round(t * (h - 72));
			dir = bearing > 0 ? 1 : -1;
		}

		g.fill(x - 1, y - 1, x + 2, y + 2, UiColors.OUTLINE & 0x80FFFFFF);

		for (int i = 0; i < 4; i++) {
			if (dir == 0) {
				// Points up (ahead): a small chevron widening downward.
				g.fill(x - i, y + i, x + i + 1, y + i + 1, color);
			} else {
				// Points out to the side.
				int sx = x - dir * i;
				g.fill(sx, y - i, sx + 1, y + i + 1, color);
			}
		}
	}

	static void clear() {
		BLOCKS.clear();
		CREATURES.clear();
		level = null;
		age = 0;
		life = 0;
	}

	private ScannerMarks() {
	}
}
