package com.oldrepublic.holocron;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.Commendations;
import com.oldrepublic.progression.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Handing holocrons and echo shards over (spec 4.6, 4.7): returning a carried world holocron to a
 * mentor or an officer, trading shards for a commendation, and making amends with shards.
 */
public final class HolocronReturns {
	public static final String JEDI = "jedi";
	public static final String SITH = "sith";
	public static final String REPUBLIC = "republic";
	public static final String IMPERIAL = "imperial";

	/**
	 * The holocron {@link #returnTo} would take now, or null: the first world holocron in return
	 * order (Jedi, Sith, Wayfarer rows) that the player carries as their own current copy, has not
	 * returned, and that {@code receiver} accepts.
	 */
	public static @Nullable String candidate(Player p, String receiver) {
		if (!(p instanceof ServerPlayer player)) {
			return null;
		}

		HolocronData d = HolocronService.data(player);
		Set<String> held = HolocronRewards.heldCurrent(player, d);
		Path path = Identity.path(player);

		for (HolocronCatalog.Entry entry : HolocronCatalog.returnOrder()) {
			String id = entry.id();

			if (held.contains(id) && !d.returned.contains(id) && accepts(receiver, path, entry.kind(), d.choice(id))) {
				return id;
			}
		}

		return null;
	}

	/**
	 * Whether {@code receiver} takes a holocron of {@code kind} from a player on {@code path}: Saelen
	 * takes Jedi holocrons and the Sith holocrons a Jedi sealed, Sorn takes Sith holocrons from a
	 * Sith, and either army's officer takes any world holocron.
	 */
	public static boolean accepts(String receiver, Path path, HolocronKind kind, @Nullable HolocronChoice choice) {
		return switch (receiver) {
			case JEDI -> path == Path.JEDI && (kind == HolocronKind.JEDI || (kind == HolocronKind.SITH && choice == HolocronChoice.SEAL));
			case SITH -> path == Path.SITH && kind == HolocronKind.SITH;
			case REPUBLIC, IMPERIAL -> true;
			default -> false;
		};
	}

	/**
	 * Returns the {@link #candidate} to {@code receiver}: takes the item, records the id as returned,
	 * bumps its serial (every other copy goes stale) and grants the receiver's rewards. Officers
	 * refuse a player their army holds Hostile. The knowledge and the nodes stay with the player.
	 */
	public static boolean returnTo(ServerPlayer p, String receiver) {
		Faction army = army(receiver);

		if (army != null && Identity.tier(p, army) == ReputationTier.HOSTILE) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.return.refused"));
			return false;
		}

		String id = candidate(p, receiver);
		HolocronData d = HolocronService.data(p);

		if (id == null || !HolocronRewards.takeCurrent(p, d, id)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.return.none"));
			return false;
		}

		HolocronBalance b = HolocronBalance.get();
		d.returned.add(id);
		d.serials.put(id, d.serial(id) + 1);
		HolocronService.commit(p, d);

		String source = "holocron_return:" + id;

		switch (receiver) {
			case JEDI -> {
				Identity.addTrust(p, MentorRole.JEDI.id(), b.returnTrust, "holocron.oldrepublic.trust.return");
				Identity.changeReputation(p, Faction.JEDI_ORDER, b.returnRep, source, true);
			}
			case SITH -> {
				Identity.addTrust(p, MentorRole.SITH.id(), b.returnTrust, "holocron.oldrepublic.trust.return");
				Identity.changeReputation(p, Faction.SITH_EMPIRE, b.returnRep, source, true);
			}
			case REPUBLIC, IMPERIAL -> {
				Identity.changeReputation(p, army, b.returnRep, source, true);
				HolocronRewards.give(p, Commendations.of(army, b.returnCommendations), null);
			}
		}

		p.sendSystemMessage(Component.translatable("holocron.oldrepublic.return.done", Component.translatable(HolocronCatalog.nameKey(id))));
		HolocronCarry.refresh(p);
		Progression.refreshModifiers(p);
		return true;
	}

	/** Trades {@code shardsPerCommendation} shards for one commendation of {@code receiver}'s army; no reputation. */
	public static boolean exchangeShards(ServerPlayer p, String receiver) {
		Faction army = army(receiver);
		int cost = HolocronBalance.get().shardsPerCommendation;

		if (army == null) {
			return false;
		}

		if (!HolocronRewards.takeShards(p, cost)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.shards.too_few", cost));
			return false;
		}

		HolocronRewards.give(p, Commendations.of(army, 1), null);
		p.sendSystemMessage(Component.translatable("holocron.oldrepublic.shards.exchanged", cost));
		return true;
	}

	/**
	 * "Make amends" at a Wary officer: {@code amendsShards} shards for +{@code amendsRep} standing with
	 * the army and no commendation. Only while the army holds the player Distrusted (Wary).
	 */
	public static boolean amends(ServerPlayer p, String receiver) {
		Faction army = army(receiver);
		HolocronBalance b = HolocronBalance.get();

		if (army == null) {
			return false;
		}

		if (Identity.tier(p, army) != ReputationTier.DISTRUSTED) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.shards.no_amends"));
			return false;
		}

		if (!HolocronRewards.takeShards(p, b.amendsShards)) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.shards.too_few", b.amendsShards));
			return false;
		}

		Identity.changeReputation(p, army, b.amendsRep, "shards_amends", false);
		p.sendSystemMessage(Component.translatable("holocron.oldrepublic.shards.amends", b.amendsShards));
		return true;
	}

	/** The army of an officer receiver ({@code republic} or {@code imperial}), or null for a mentor. */
	static @Nullable Faction army(String receiver) {
		return switch (receiver) {
			case REPUBLIC -> Faction.REPUBLIC;
			case IMPERIAL -> Faction.SITH_EMPIRE;
			default -> null;
		};
	}

	private HolocronReturns() {
	}
}
