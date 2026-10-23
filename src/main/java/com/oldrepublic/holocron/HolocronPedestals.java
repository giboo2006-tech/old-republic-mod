package com.oldrepublic.holocron;

import java.util.List;
import java.util.function.Predicate;

import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.registry.M3Items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * What a holocron pedestal does for one player (spec 4.3): the gates, then the next holocron of its
 * kind with the attunement moment, then the echo, then "already spoken"; sneaking restores a lost
 * holocron instead. A pedestal only reads the world: it never wakes or changes anything near it.
 */
public final class HolocronPedestals {
	/** What one use did. */
	public enum Outcome {
		ATTUNE_STARTED,
		ECHO,
		ALREADY_SPOKEN,
		RESTORED,
		REFUSED_STORY,
		REFUSED_HOSTILE,
		/** The player's own moment is open, or another player's moment is open at this pedestal. */
		REFUSED_BUSY,
		NOTHING_TO_RESTORE,
		/** A restore was already used today. */
		RESTORE_COOLDOWN
	}

	/**
	 * Uses the pedestal at {@code site}. The test seam: {@code skipStoryGate} skips the awakened and
	 * Chapter Three checks (the hostile check always runs), and the day and randomness come in as
	 * arguments.
	 *
	 * @param day the day index for the restore rule ({@code game time / restoreCooldownTicks})
	 */
	public static Outcome use(ServerPlayer p, PedestalSite site, HolocronKind kind, boolean sneaking, boolean skipStoryGate, long day,
			RandomSource random) {
		return use(p, site, kind, sneaking, skipStoryGate, day, random, creature -> true);
	}

	/**
	 * {@link #use} where the hostile check only sees creatures {@code counted} accepts: game tests
	 * pass their own structure bounds, so a neighbouring test's mobs cannot refuse the pedestal.
	 */
	public static Outcome use(ServerPlayer p, PedestalSite site, HolocronKind kind, boolean sneaking, boolean skipStoryGate, long day,
			RandomSource random, Predicate<LivingEntity> counted) {
		HolocronBalance b = HolocronBalance.get();

		if (!skipStoryGate && (!Progression.get(p).isAwakened() || !ChapterThree.atLeast(p, ChapterThreeStage.SUMMONS))) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.pedestal.not_ready"));
			return Outcome.REFUSED_STORY;
		}

		if (HolocronAttunement.pending(p)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.pedestal.listening"));
			return Outcome.REFUSED_BUSY;
		}

		if (HolocronAttunement.pendingAt(site.dim(), site.pos())) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.pedestal.busy"));
			return Outcome.REFUSED_BUSY;
		}

		if (hostileNear(p, site, b.pedestalClearRange, counted)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.pedestal.stirs"));
			return Outcome.REFUSED_HOSTILE;
		}

		HolocronData d = HolocronService.data(p);

		if (sneaking) {
			return restore(p, d, kind, day);
		}

		if (!d.gaveAt(site.dimId(), site.pos())) {
			String id = site.fixedId() != null ? (d.isAttuned(site.fixedId()) ? null : site.fixedId()) : HolocronRules.nextWorldId(d, kind);

			if (id != null) {
				HolocronAttunement.start(p, site, id);
				return Outcome.ATTUNE_STARTED;
			}
		}

		if (HolocronRules.complete(d, kind) && !d.echoClaimed(site.dimId(), site.pos())) {
			echo(p, d, site, kind, random, b);
			return Outcome.ECHO;
		}

		p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.pedestal.spoken"));
		return Outcome.ALREADY_SPOKEN;
	}

	/**
	 * Whether an awake creature that counts as hostile to the player (hostile attitude, an engaged
	 * squad, or one the player provoked) is within {@code range} of the pedestal. No-AI creatures
	 * (dormant sentinels) never count; nothing is woken or touched.
	 */
	static boolean hostileNear(ServerPlayer p, PedestalSite site, int range, Predicate<LivingEntity> counted) {
		if (p.level().dimension() != site.dim()) {
			return false;
		}

		AABB box = new AABB(site.pos()).inflate(range);
		double rangeSqr = (double) range * range;
		List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && e.isAlive() && !(e instanceof Mob mob && mob.isNoAi()) && e.distanceToSqr(site.top()) <= rangeSqr && counted.test(e));

		for (LivingEntity e : near) {
			if (Attitudes.countsAsHostile(p, e) || Attitudes.provokedBy(e, p)) {
				return true;
			}
		}

		return false;
	}

	/** One echo of a completed kind: XP, a shard and a gatekeeper's line; the pedestal joins the capped list. */
	private static void echo(ServerPlayer p, HolocronData d, PedestalSite site, HolocronKind kind, RandomSource random, HolocronBalance b) {
		d.echoes.add(new HolocronData.PedestalRef(site.dimId(), site.pos()));
		int cap = Math.min(Math.max(1, b.echoRing), HolocronData.MAX_ECHOES);

		while (d.echoes.size() > cap) {
			d.echoes.removeFirst();
		}

		HolocronService.commit(p, d);

		List<HolocronCatalog.Entry> speakers = HolocronCatalog.world(kind);
		String speaker = speakers.get(random.nextInt(speakers.size())).id();
		Progression.addXp(p, b.echoXp, "holocron_echo");
		HolocronRewards.give(p, new ItemStack(M3Items.HOLOCRON_SHARD, Math.max(1, b.echoShards)), site.top());
		p.sendSystemMessage(Component.translatable("holocron.oldrepublic.echo", Component.translatable(HolocronCatalog.nameKey(speaker)),
				Component.translatable("holocron.oldrepublic.echo." + speaker)));
		EffectEvents.around(p.level(), HolocronEffects.ECHO, site.top(), null, p.getId(), 1f, HolocronCatalog.gatekeeperIndex(speaker));
	}

	/** Sneak-use: re-issues one lost holocron of this kind, once per day, with no rewards (4.3). */
	private static Outcome restore(ServerPlayer p, HolocronData d, HolocronKind kind, long day) {
		String id = HolocronRules.restoreCandidate(d, kind, HolocronRewards.heldCurrent(p, d));

		if (id == null) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.restore.nothing"));
			return Outcome.NOTHING_TO_RESTORE;
		}

		if (!HolocronRules.mayRestore(d, day)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.restore.cooldown"));
			return Outcome.RESTORE_COOLDOWN;
		}

		int serial = d.serial(id) + 1;
		d.serials.put(id, serial);
		d.restoreDay = day;
		HolocronService.commit(p, d);
		HolocronRewards.give(p, HolocronItems.stack(id, p.getUUID(), serial), null);
		p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.restore.done", Component.translatable(HolocronCatalog.nameKey(id))));
		return Outcome.RESTORED;
	}

	private HolocronPedestals() {
	}
}
