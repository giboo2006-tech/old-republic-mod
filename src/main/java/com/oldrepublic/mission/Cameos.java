package com.oldrepublic.mission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.IdentityEntities;
import com.oldrepublic.identity.Yielding;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.CaptivesFate;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Two cameos in the missions (spec 5.4), each cheap and flag-driven and noted in the
 * journal under the mission's entry:
 *
 * <ul>
 * <li>Hessa Dray spared: she runs the R2 cache instead of Rell Okkar, and when the owner comes close
 * she steps out, yields at once and hands over the record. Her people do not fight.</li>
 * <li>Hessa executed: her lieutenant runs it and fights to the end.</li>
 * <li>Otherwise Rell Okkar, a poacher captain who yields at the captain's fraction.</li>
 * <li>Captives freed: one of the freed settlers waits at the Republic outpost on the first visit and
 * points the way to an unused chamber (the 12,000-tick pull, like Interrogate).</li>
 * </ul>
 */
final class Cameos {
	/** {@link MissionData#cameos} id of the freed settler. */
	static final String SETTLER = "settler";
	/** Extra tag on Hessa when she runs the cache. */
	private static final String HESSA_TAG = MissionSites.ROLE_PREFIX + "hessa";
	/** Hessa steps out when the owner comes this close to her post. */
	private static final double HESSA_RANGE = 12;
	/** The settler goes when the player is this far. */
	private static final double SETTLER_LEAVE_DISTANCE = 64;
	/** How far beside the officer the settler stands. */
	private static final double BESIDE = 2.5;
	private static final int SETTLER_CHECK_INTERVAL = 20;

	/** The settler cameo per player, in memory only (a leftover after a restart is discarded on load). */
	private static final Map<UUID, UUID> SETTLERS = new HashMap<>();

	// ---------------------------------------------------------------- R2's chief

	/** Whether Hessa Dray, spared in Chapter Two, runs the R2 cache for this player. */
	static boolean hessaRunsCache(ServerPlayer owner) {
		return MissionRules.r2Chief(Story.get(owner).captainFate()) == MissionRules.Chief.HESSA;
	}

	/** Whether this mission's chief is Hessa Dray, who is not spawned until the owner nears her post (so the safety recount must not count her missing). */
	static boolean chiefWaitsAtPost(ServerPlayer owner, MissionDefinition base) {
		return MissionItems.byId(base.item()) == MissionItems.ARCHIVE_RECORD && hessaRunsCache(owner);
	}

	/**
	 * Spawns R2's chief at the {@code CHIEF} anchor by Hessa Dray's fate. Hessa herself is not spawned
	 * yet: {@link #tickChief} brings her out when the owner comes close.
	 */
	static void spawnR2Chief(ServerPlayer owner, Active active, BlockPos at) {
		ServerLevel level = owner.level();

		switch (MissionRules.r2Chief(Story.get(owner).captainFate())) {
			case RELL -> {
				PoacherCaptain rell = MissionSites.spawnCaptain(level, owner.getUUID(), active.id, at, "npc.oldrepublic.rell_okkar",
						MissionFeature.balance().chiefYield);

				if (rell == null) {
					active.set(MissionData.FLAG_CHIEF_DONE);
				}
			}
			case LIEUTENANT -> {
				LivingEntity lieutenant = MissionSites.spawnLieutenant(level, owner.getUUID(), active.id, at, "npc.oldrepublic.hessa_lieutenant");

				if (lieutenant == null) {
					active.set(MissionData.FLAG_CHIEF_DONE);
				}

				ChapterThreeService.addJournalNote(owner, JournalEntries.CREDITS_FROM_THE_CORE, "mission.oldrepublic.note.r2_lieutenant");
			}
			case HESSA -> {
			}
		}
	}

	/**
	 * Hessa's moment: while R2's objective is active and nobody has dealt with the chief, the owner
	 * coming within {@value #HESSA_RANGE} blocks of her post brings her out. She yields at once, says
	 * her line about debts and hands the record over; her fate is then the usual Spare / Execute.
	 */
	static void tickChief(ServerPlayer owner) {
		MissionData d = MissionService.get(owner);
		Active active = d.active;
		MissionDefinition base = active == null ? null : MissionService.baseDefinition(active);

		if (active == null || base == null || active.stage != Stage.ACTIVE || active.site == null || !active.has(MissionData.FLAG_SPAWNED)
				|| active.has(MissionData.FLAG_CHIEF_DONE) || MissionItems.byId(base.item()) != MissionItems.ARCHIVE_RECORD || !hessaRunsCache(owner)) {
			return;
		}

		ServerLevel level = owner.level();
		SitePiece piece = MissionSites.piece(level, active.site);
		BlockPos post = MissionSites.spot(level, piece, active.site, SiteAnchors.Anchor.CHIEF);

		if (owner.position().distanceToSqr(Vec3.atBottomCenterOf(post)) > HESSA_RANGE * HESSA_RANGE) {
			return;
		}

		PoacherCaptain hessa = MissionSites.spawnCaptain(level, owner.getUUID(), active.id, post, "npc.oldrepublic.hessa_dray",
				MissionFeature.balance().chiefYield);
		MissionService.markChiefDone(owner);

		if (hessa != null) {
			hessa.addTag(HESSA_TAG);
			hessa.lookAt(owner, 180f, 180f);
			Yielding.forceYield(hessa);
			say(owner, hessa.getDisplayName(), "mission.oldrepublic.cameo.hessa.line");
		} else {
			OldRepublicMod.LOGGER.warn("Mission {}: Hessa Dray did not spawn at {}; the record is handed over without her", active.id, post);
		}

		ChapterThreeService.addJournalNote(owner, JournalEntries.CREDITS_FROM_THE_CORE, "mission.oldrepublic.note.r2_hessa");
		MissionService.issueItem(owner, base);
	}

	// ---------------------------------------------------------------- the freed settler

	/**
	 * The player entered a Republic outpost: the first time after freeing the captives in Chapter
	 * Two, one of the freed settlers waits beside the officer and starts the pull toward an unused
	 * chamber.
	 */
	static void onOutpostEntered(ServerPlayer p, SitePiece piece) {
		if (piece.kind() != SiteKind.REPUBLIC_OUTPOST || Story.get(p).captivesFate() != CaptivesFate.FREED || SETTLERS.containsKey(p.getUUID())
				|| MissionService.get(p).cameos.contains(SETTLER)) {
			return;
		}

		ServerLevel level = p.level();
		BlockPos officer = StructureLocator.anchor(piece, SiteAnchors.Anchor.OFFICER);
		Vec3 base = Vec3.atBottomCenterOf(officer != null ? officer : piece.box().getCenter());
		Vec3 side = p.position().subtract(base).multiply(1, 0, 1);
		Vec3 offset = side.lengthSqr() < 1.0e-4 ? new Vec3(BESIDE, 0, 0) : new Vec3(-side.z, 0, side.x).normalize().scale(BESIDE);
		int x = (int) Math.floor(base.x + offset.x);
		int z = (int) Math.floor(base.z + offset.z);
		BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		FrontierSettler settler = IdentityEntities.FRONTIER_SETTLER.create(level, EntitySpawnReason.EVENT);

		if (settler == null) {
			return;
		}

		settler.snapTo(Vec3.atBottomCenterOf(at), 0f, 0f);
		settler.lookAt(p, 180f, 180f);
		settler.setVariant(p.getUUID().hashCode());
		settler.makeFree(at);
		settler.setInvulnerable(true);

		if (!level.addFreshEntity(settler)) {
			return;
		}

		// Tagged once it is in the level: the mission load rule discards a loaded cameo, and Fabric's
		// entity load event fires for a fresh spawn too.
		MissionSites.tag(settler, p.getUUID(), SETTLER, MissionSites.ROLE_CAMEO);
		SETTLERS.put(p.getUUID(), settler.getUUID());
		MissionData d = MissionService.get(p);
		d.markCameo(SETTLER);
		MissionService.commit(p, d);
		say(p, settler.getDisplayName(), "mission.oldrepublic.cameo.settler.line");
		MissionGuidance.startPull(p);
		ChapterThreeService.addJournalNote(p, JournalEntries.CUT_LOOSE, "mission.oldrepublic.note.settler");
	}

	/** Slow tick: the settler goes when the player is far, in another level, or it is gone already. */
	static void tick(ServerPlayer p) {
		UUID id = SETTLERS.get(p.getUUID());

		if (id == null || p.level().getGameTime() % SETTLER_CHECK_INTERVAL != 0) {
			return;
		}

		Entity settler = p.level().getEntity(id);

		if (settler == null || settler.distanceTo(p) > SETTLER_LEAVE_DISTANCE) {
			if (settler != null) {
				settler.discard();
			}

			SETTLERS.remove(p.getUUID());
		}
	}

	/** The player left, respawned or changed level: their settler, in {@code level}, goes. */
	static void forget(ServerPlayer p, ServerLevel level) {
		UUID id = SETTLERS.remove(p.getUUID());
		Entity settler = id == null ? null : level.getEntity(id);

		if (settler != null) {
			settler.discard();
		}
	}

	/** Forgets all in-memory state (server stopped). */
	static void clear() {
		SETTLERS.clear();
	}

	/** A spoken line in chat: "Name: line". */
	static void say(ServerPlayer p, Component speaker, String lineKey) {
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", speaker, Component.translatable(lineKey)));
	}

	private Cameos() {
	}
}
