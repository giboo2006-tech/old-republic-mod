package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Brenn's cameo (spec 5.4): a Sith who spared Brenn in the Trial of Ascension finds him beside the
 * officer the first time they visit an Imperial outpost, attached to Imperial Intelligence now. He
 * is the M2 acolyte in {@code RIVAL} mode (neutral), invulnerable, spawned for that player, greets
 * them once and is removed when they are {@value #LEAVE_DISTANCE} blocks away. He is tracked in
 * memory only: one found after a restart is a leftover and is discarded on load.
 */
final class BrennCameo {
	/** Marks the cameo entity. */
	static final String TAG = "oldrepublic.c3.brenn_cameo";
	/** Beyond this distance from the player he is gone. */
	static final int LEAVE_DISTANCE = 64;
	/** How far beside the officer he stands. */
	private static final double BESIDE = 1.8;

	/** The cameo per player. */
	private static final Map<UUID, UUID> CAMEOS = new HashMap<>();

	/** The player entered an Imperial outpost: the first time, Brenn waits beside the officer. */
	static void onImperialOutpost(ServerPlayer p, SitePiece piece) {
		StoryData data = Story.get(p);

		if (data.storyPath() != Path.SITH || !data.hasFlag(StoryRules.BRENN_SPARED) || data.hasFlag(ChapterThreeRules.BRENN_MET)
				|| CAMEOS.containsKey(p.getUUID())) {
			return;
		}

		ServerLevel level = p.level();
		BlockPos officer = StructureLocator.anchor(piece, SiteAnchors.Anchor.OFFICER);
		Vec3 base = officer != null ? Vec3.atBottomCenterOf(officer) : Vec3.atBottomCenterOf(piece.box().getCenter());
		Vec3 side = p.position().subtract(base).horizontal();
		Vec3 offset = side.lengthSqr() < 1.0e-4 ? new Vec3(BESIDE, 0, 0) : new Vec3(-side.z, 0, side.x).normalize().scale(BESIDE);
		Vec3 at = StoryMentors.ground(level, base.x + offset.x, base.z + offset.z);
		SithAcolyte brenn = EnemyEntities.SITH_ACOLYTE.create(level, EntitySpawnReason.STRUCTURE);

		if (brenn == null) {
			return;
		}

		float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - at.z, p.getX() - at.x)) - 90f;
		brenn.snapTo(at.x, at.y, at.z, yaw, 0f);
		// RIVAL mode: neutral and persistent; invulnerable, so the cameo never turns into a fight.
		brenn.setMode(SithAcolyte.Mode.RIVAL);
		brenn.setSkin(SithAcolyte.Skin.BRENN);
		brenn.setOwner(p.getUUID());
		brenn.setInvulnerable(true);
		brenn.setCustomName(Component.translatable("npc.oldrepublic.brenn.name"));
		level.addFreshEntity(brenn);
		// Tagged once he is in the level: the load rule discards a loaded cameo no one tracks, and
		// Fabric's entity load event fires for a fresh spawn too.
		brenn.addTag(TAG);
		CAMEOS.put(p.getUUID(), brenn.getUUID());

		data.flags.add(ChapterThreeRules.BRENN_MET);
		Story.commit(p, data);
		StoryService.say(p, Component.translatable("npc.oldrepublic.brenn.name"), "npc.oldrepublic.brenn.cameo_greeting");
		ChapterThreeService.addJournalNote(p, JournalEntries.TWO_FLAGS, "quest.oldrepublic.c3.note.brenn");
	}

	/** Slow tick: he goes when the player is far, in another level, or he is gone already. */
	static void tick(ServerPlayer p) {
		UUID id = CAMEOS.get(p.getUUID());

		if (id == null) {
			return;
		}

		Entity brenn = p.level().getEntityInAnyDimension(id);

		if (brenn == null || !brenn.isAlive()) {
			CAMEOS.remove(p.getUUID());
		} else if (brenn.level() != p.level() || brenn.distanceToSqr(p) > (double) LEAVE_DISTANCE * LEAVE_DISTANCE) {
			brenn.discard();
			CAMEOS.remove(p.getUUID());
		}
	}

	/** Whether a loaded entity is a cameo no one tracks any more (after a restart or a logout). */
	static boolean isLeftover(Entity entity) {
		return entity.entityTags().contains(TAG) && !CAMEOS.containsValue(entity.getUUID());
	}

	/** The player left: the cameo goes too. */
	static void forget(ServerPlayer p) {
		UUID id = CAMEOS.remove(p.getUUID());
		Entity brenn = id == null ? null : p.level().getEntityInAnyDimension(id);

		if (brenn != null) {
			brenn.discard();
		}
	}

	static void clear() {
		CAMEOS.clear();
	}

	private BrennCameo() {
	}
}
