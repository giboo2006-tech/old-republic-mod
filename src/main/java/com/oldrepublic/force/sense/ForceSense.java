package com.oldrepublic.force.sense;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.AbilityInfoKeys;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Force Sense (spec 10.1): a tap on its own key (never a slot) that reveals what hides nearby and
 * points toward the nearest site that can still give the caster a holocron. The server validates
 * and spends, collects every {@link SenseTargets} provider's points, sends the reveal to the caster
 * and the gesture to everyone tracking them, and reads the holocron direction from the locator's
 * cache at once; when a fresh search answers within {@code holocron.senseFollowUpTicks}, the
 * caster gets one follow-up pulse. Mastery grows with casts that reveal something hidden.
 */
public final class ForceSense implements ForceAbility {
	public static final String ID = "force_sense";
	/** W1's echo cairn block (spec 16), the only {@code CACHE} point that was hidden. */
	private static final Identifier ECHO_CAIRN = OldRepublicMod.id("echo_cairn");

	/**
	 * What a cast did.
	 *
	 * @param points         the revealed points, nearest first (at most {@code maxPoints})
	 * @param hidden         how many were hidden: a block, cairn, anchor or urn, or a creature out of sight
	 * @param cachedHolocron the cached site whose direction went out with the cast, or null
	 */
	public record Cast(List<SenseTargets.Point> points, int hidden, @Nullable SitePiece cachedHolocron) {
	}

	/** The holocron searches of one cast still waiting for answers; at most one follow-up per cast. */
	private static final class FollowUp {
		final long castTick;
		final ResourceKey<Level> dimension;
		final int radius;
		final @Nullable String sentSite;
		final SenseSink sink;
		int unanswered;
		@Nullable SitePiece best;
		double bestDistance = Double.MAX_VALUE;

		FollowUp(long castTick, ResourceKey<Level> dimension, int radius, @Nullable String sentSite, SenseSink sink, int unanswered) {
			this.castTick = castTick;
			this.dimension = dimension;
			this.radius = radius;
			this.sentSite = sentSite;
			this.sink = sink;
			this.unanswered = unanswered;
		}
	}

	private static final Map<UUID, FollowUp> PENDING = new HashMap<>();

	public static SenseBalance balance() {
		return BalanceConfig.section("force_sense", SenseBalance.class, SenseBalance::new);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.TAP;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		if (action == AbilityAction.START) {
			cast(p, SenseLocator.WORLD, SenseSink.NETWORK, p.level().getGameTime());
		}
	}

	/** A cast is instant: there is nothing to stop. Its pending follow-up stays (a slot change does not end it). */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	@Override
	public Map<String, Float> info() {
		SenseBalance b = balance();
		Map<String, Float> values = new LinkedHashMap<>();
		values.put(AbilityInfoKeys.COST, b.cost);
		values.put(AbilityInfoKeys.COOLDOWN, b.cooldownTicks / 20f);
		values.put("creature_range", (float) b.creatureRange);
		values.put("block_range", (float) b.blockRange);
		values.put("reveal_s", b.durationTicks / 20f);
		values.put("holocron_range", (float) b.holocronRadius);
		values.put("mastery_sense_cost", b.masteryCostPerRank);
		values.put("mastery_sense_range", (float) b.masteryRangePerRank);
		values.put("danger_range", (float) b.dangerRange);
		values.put("danger_cone", (float) b.dangerConeDegrees);
		values.put("danger_repeat_s", b.dangerRepeatTicks / 20f);
		values.put("seeker_range", (float) b.seekerRadius);
		values.put("seeker_bonus_s", b.seekerBonusTicks / 20f);
		values.put("glint_range", (float) b.glintRange);
		return values;
	}

	/** Energy a cast costs {@code p} now: mastery, then the skill, alignment and other features' multipliers. */
	public static float cost(ServerPlayer p) {
		return SenseRules.cost(balance(), ForcePowers.masteryRank(p, ID)) * ForcePowers.costMultiplier(p, ID);
	}

	/**
	 * A validated cast at game time {@code now}. Returns null when it is refused (the reason and the
	 * failure feedback have already been sent): not awakened, not known, another power active, on
	 * cooldown, refused by alignment, locked by another feature, or not enough energy.
	 */
	public static @Nullable Cast cast(ServerPlayer p, SenseLocator locator, SenseSink sink, long now) {
		if (!ForcePowers.mayStart(p, ID)) {
			return null;
		}

		SenseBalance b = balance();
		float cost = cost(p);

		if (!ForcePowers.canAfford(p, cost) || !Progression.trySpendEnergy(p, cost)) {
			return null;
		}

		int rank = ForcePowers.masteryRank(p, ID);
		boolean road = Skills.active(p, SkillIds.ARCHIVISTS_ROAD);
		Progression.setCooldown(p, ID, SenseRules.cooldown(ForcePowers.cooldown(p, ID, b.cooldownTicks), road, SkillService.balance().archivistsRoadSenseCooldown));
		boolean seeker = ForcePowers.hasNode(p, Progression.get(p), SkillIds.SENSE_SEEKER);

		List<SenseTargets.Point> points = SenseTargets.collect(p, b.blockRange, SenseRules.creatureRange(b, rank),
				Math.min(b.maxPoints, SenseRevealPayload.MAX_POINTS));
		sink.reveal(p, new SenseRevealPayload(points, b.durationTicks + (seeker ? b.seekerBonusTicks : 0)));
		sink.gesture(p, points.size());
		warnWatched(p, points);
		SitePiece cached = holocron(p, locator, sink, seeker ? b.seekerRadius : b.holocronRadius, now);

		int hidden = countHidden(p, points);

		if (hidden > 0) {
			ForcePowers.addMastery(p, ID, 1);
		}

		QuestHooks.onForceSense(p, points.size());
		return new Cast(points, hidden, cached);
	}

	/**
	 * Points that were hidden (spec 10.1 mastery): veiled stone and the other block points, anchors,
	 * urns, cairns, and creatures the caster cannot see. A {@code CACHE} point is either an
	 * Independent's cairn (W2) or a Seeker's Sight glint on a pedestal or a site chest (W1). Those glints
	 * were never hidden, so a cache point counts only when it is an echo cairn.
	 */
	static int countHidden(ServerPlayer p, List<SenseTargets.Point> points) {
		int hidden = 0;

		for (SenseTargets.Point point : points) {
			boolean counts = switch (point.kind()) {
				case CREATURE -> {
					Entity entity = p.level().getEntity(point.entityId());
					yield entity != null && !p.hasLineOfSight(entity);
				}
				case CACHE -> isEchoCairn(p.level(), point.pos());
				default -> true;
			};

			if (counts) {
				hidden++;
			}
		}

		return hidden;
	}

	/** Whether the block at {@code pos} is an echo cairn, looked up by its id (spec 16) so the power needs no world class. */
	private static boolean isEchoCairn(ServerLevel level, Vec3 pos) {
		return ECHO_CAIRN.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(pos)).getBlock()));
	}

	/** Sensed players (only with {@code pvpEffects}) feel a prickle. */
	private static void warnWatched(ServerPlayer p, List<SenseTargets.Point> points) {
		for (SenseTargets.Point point : points) {
			if (point.kind() == SenseTargets.Kind.CREATURE && p.level().getEntity(point.entityId()) instanceof ServerPlayer other && other != p) {
				other.sendOverlayMessage(Component.translatable("message.oldrepublic.force.sense_watched"));
			}
		}
	}

	/**
	 * The holocron direction: the nearest cached site that can still give {@code p} a holocron goes
	 * out with the cast (variant 1), and a fresh search of every kind is queued for the follow-up.
	 * Returns the cached site used, or null.
	 */
	private static @Nullable SitePiece holocron(ServerPlayer p, SenseLocator locator, SenseSink sink, int radius, long now) {
		PENDING.remove(p.getUUID());
		List<SiteKind> kinds = HolocronService.senseSearchKinds(p);

		if (kinds.isEmpty()) {
			return null;
		}

		Predicate<SitePiece> exclude = HolocronService.exhaustedSites(p);
		Vec3 from = p.position();
		SitePiece nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (SiteKind kind : kinds) {
			SitePiece site = locator.cached(p, kind);
			double distance = site == null ? Double.MAX_VALUE : distance(from, site);

			if (site != null && !exclude.test(site) && distance <= radius && distance < nearestDistance) {
				nearest = site;
				nearestDistance = distance;
			}
		}

		if (nearest != null) {
			sink.holocron(p, SenseRules.horizontalDirection(from, center(nearest)), SenseRules.closeness(nearestDistance, radius),
					HolocronService.senseKind(p, nearest));
		}

		FollowUp followUp = new FollowUp(now, p.level().dimension(), radius, nearest == null ? null : nearest.siteKey(), sink, kinds.size());
		PENDING.put(p.getUUID(), followUp);
		UUID id = p.getUUID();
		MinecraftServer server = p.level().getServer();

		for (SiteKind kind : kinds) {
			locator.find(p, kind, radius, exclude, site -> answer(server, id, followUp, site));
		}

		return nearest;
	}

	/** One search answered. The pulse goes out once every search answered, or at the deadline ({@link #tick}). */
	private static void answer(MinecraftServer server, UUID id, FollowUp followUp, @Nullable SitePiece site) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);

		if (PENDING.get(id) != followUp || player == null) {
			return;
		}

		long now = player.level().getGameTime();

		if (!SenseRules.followUpDue(followUp.castTick, now, HolocronBalance.get().senseFollowUpTicks)
				|| !player.level().dimension().equals(followUp.dimension)) {
			PENDING.remove(id);
			return;
		}

		followUp.unanswered--;

		if (site != null) {
			double distance = distance(player.position(), site);

			if (distance <= followUp.radius && distance < followUp.bestDistance) {
				followUp.best = site;
				followUp.bestDistance = distance;
			}
		}

		if (followUp.unanswered <= 0) {
			PENDING.remove(id);
			pulse(player, followUp);
		}
	}

	/** Sends the follow-up pulse toward the best fresh site, unless it is the site the cast already pointed at. */
	private static void pulse(ServerPlayer player, FollowUp followUp) {
		SitePiece best = followUp.best;

		if (best == null || best.siteKey().equals(followUp.sentSite)) {
			return;
		}

		Vec3 from = player.position();
		followUp.sink.followUp(player, SenseRules.horizontalDirection(from, center(best)), SenseRules.closeness(distance(from, best), followUp.radius),
				HolocronService.senseKind(player, best));
	}

	/** Ends follow-ups whose window passed: the best answer so far (if any) still gets its pulse. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		int window = HolocronBalance.get().senseFollowUpTicks;
		Iterator<Map.Entry<UUID, FollowUp>> it = PENDING.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, FollowUp> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			FollowUp followUp = entry.getValue();

			if (player == null || !player.level().dimension().equals(followUp.dimension)) {
				it.remove();
			} else if (!SenseRules.followUpDue(followUp.castTick, player.level().getGameTime() + 1, window)) {
				// The next tick is past the window: send what arrived in time, then forget the cast.
				it.remove();
				pulse(player, followUp);
			}
		}
	}

	private static Vec3 center(SitePiece site) {
		return Vec3.atCenterOf(site.box().getCenter());
	}

	private static double distance(Vec3 from, SitePiece site) {
		Vec3 to = center(site);
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Forgets a player's pending follow-up (death, disconnect, dimension change). */
	public static void forget(ServerPlayer p) {
		PENDING.remove(p.getUUID());
	}

	public static void clear() {
		PENDING.clear();
	}
}
