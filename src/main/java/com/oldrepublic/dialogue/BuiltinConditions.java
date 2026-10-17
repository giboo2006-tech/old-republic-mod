package com.oldrepublic.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.progression.Progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The dialogue conditions every dialogue can use. Failure reasons are a lang key, optionally
 * followed by {@code |}-separated arguments ({@code @key} arguments are translated on the client):
 *
 * <ul>
 * <li>{@code "path": "jedi"} or {@code ["jedi", "independent"]}: the player's path is one of these;
 * {@code "path_not"} is the opposite.</li>
 * <li>{@code "rank_min": 2}</li>
 * <li>{@code "alignment_min": 0}, {@code "alignment_max": 0}</li>
 * <li>{@code "trust_min": {"mentor": "jedi", "value": 30}}</li>
 * <li>{@code "reputation_min": {"faction": "jedi_order", "value": "trusted"}} (a tier name or a
 * number)</li>
 * <li>{@code "flag": "story:brenn_spared"}: a progression flag or a recorded identity choice;
 * {@code "!id"} requires its absence.</li>
 * </ul>
 */
final class BuiltinConditions {
	static final String REQ = "dialogue.oldrepublic.common.req.";

	static void register() {
		DialogueConditions.register("path", (p, speaker, arg) -> paths(arg).contains(Identity.path(p)) ? null
				: REQ + "path|@path.oldrepublic." + paths(arg).getFirst().id());
		DialogueConditions.register("path_not", (p, speaker, arg) -> !paths(arg).contains(Identity.path(p)) ? null
				: REQ + "path_not|@path.oldrepublic." + Identity.path(p).id());
		DialogueConditions.register("rank_min", (p, speaker, arg) -> Identity.rank(p) >= integer(arg) ? null : REQ + "rank_min|" + integer(arg));
		DialogueConditions.register("alignment_min", (p, speaker, arg) -> Identity.alignment(p) >= integer(arg) ? null : REQ + "alignment_min|" + integer(arg));
		DialogueConditions.register("alignment_max", (p, speaker, arg) -> Identity.alignment(p) <= integer(arg) ? null : REQ + "alignment_max|" + integer(arg));
		DialogueConditions.register("trust_min", BuiltinConditions::trustMin);
		DialogueConditions.register("reputation_min", BuiltinConditions::reputationMin);
		DialogueConditions.register("flag", BuiltinConditions::flag);
	}

	private static @Nullable String trustMin(ServerPlayer p, @Nullable Entity speaker, String arg) {
		JsonObject json = object(arg);
		String mentor = json.get("mentor").getAsString();
		int value = json.get("value").getAsInt();
		return Identity.trust(p, mentor) >= value ? null : REQ + "trust|@screen.oldrepublic.standing.mentor." + mentor + "|" + value;
	}

	private static @Nullable String reputationMin(ServerPlayer p, @Nullable Entity speaker, String arg) {
		JsonObject json = object(arg);
		Faction faction = Faction.byId(json.get("faction").getAsString());

		if (faction == null) {
			throw new JsonParseException("unknown faction " + json.get("faction"));
		}

		JsonElement value = json.get("value");
		int rep = Identity.reputation(p, faction);
		String factionArg = "|@faction.oldrepublic." + faction.id();

		if (value.getAsJsonPrimitive().isNumber()) {
			return rep >= value.getAsInt() ? null : REQ + "reputation_value|" + value.getAsInt() + factionArg;
		}

		ReputationTier tier = ReputationTier.valueOf(value.getAsString().toUpperCase(Locale.ROOT));
		boolean met = IdentityRules.tier(rep, Identity.balance()).ordinal() >= tier.ordinal();
		return met ? null : REQ + "reputation|@faction.oldrepublic.tier." + tier.name().toLowerCase(Locale.ROOT) + factionArg;
	}

	private static @Nullable String flag(ServerPlayer p, @Nullable Entity speaker, String arg) {
		boolean negate = arg.startsWith("!");
		String id = negate ? arg.substring(1) : arg;
		boolean present = Progression.get(p).hasFlag(id) || Identity.hasChoice(p, id);
		return present != negate ? null : REQ + "flag";
	}

	/** A path id, a comma-separated list, or a JSON list of ids. */
	private static List<Path> paths(String arg) {
		List<Path> paths = new ArrayList<>();
		String trimmed = arg.trim();

		if (trimmed.startsWith("[")) {
			for (JsonElement element : JsonParser.parseString(trimmed).getAsJsonArray()) {
				paths.add(Path.byId(element.getAsString()));
			}
		} else {
			for (String part : trimmed.split(",")) {
				paths.add(Path.byId(part.trim()));
			}
		}

		return paths;
	}

	private static int integer(String arg) {
		return Integer.parseInt(arg.trim());
	}

	private static JsonObject object(String arg) {
		return JsonParser.parseString(arg).getAsJsonObject();
	}

	private BuiltinConditions() {
	}
}
