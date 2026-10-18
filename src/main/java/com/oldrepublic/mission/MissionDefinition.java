package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.oldrepublic.identity.ContextAct;
import com.oldrepublic.identity.Faction;

import net.minecraft.resources.Identifier;

/**
 * One mission file, {@code data/<namespace>/mission/<id>.json}, parsed and validated (spec 5.7).
 * Definitions load on server start and {@code /reload} and never leave the server.
 *
 * <pre>{@code
 * {
 *   "kind": "story",                                   // story, contract or duty
 *   "issuer": "republic",                              // a faction id, "terminal" (the board's army) or "mentor"
 *   "short": "r1",                                     // story missions: choice ids are "mission.<short>.<choice>"
 *   "gate": {"c3:stage": "summons"},                   // dialogue conditions, all must hold
 *   "requires": ["r1_silent_patrol"],                  // story missions done first
 *   "objective": "rescue",                             // clear, recover, rescue, survey, secure, intercept, yield (not for duties)
 *   "site": "oldrepublic:mission/patrol_ambush",       // runtime template, absent for worldgen objectives
 *   "counts": {"sentinels": 3, "downed": 3},           // what the objective spawns
 *   "loot": "oldrepublic:gameplay/frontier_contract",  // contracts: pays this table instead of commendations
 *   "item": "oldrepublic:archive_record",              // recover objectives: the item to bring back
 *   "base": ["clear_camp", "survey", "secure_site"],   // duties: the contract types they draw from
 *   "entry": "silent_patrol",                          // journal entry on completion
 *   "lead": true,                                      // a Chapter Three lead "mission:<id>"
 *   "briefings": 3,                                    // briefing variants per issuer
 *   "choices": {"tend": {"rep": {"republic": 100}, "context": ["helped_other"], "commendations": 2, ...}},
 *   "driven": true,                                    // Chapter Four: the story code moves the objective (no site placing)
 *   "planet": "selvaren",                              // a planet contract: offered at that hub's contract terminal
 *   "site_kinds": ["waystone"]                         // planet contracts: the worldgen sites it works at
 * }
 * }</pre>
 *
 * A choice may also give {@code "alignment"} (a major shift), {@code "commendation_army"} (instead
 * of the giver's army), {@code "loot"}, {@code "codex"}, {@code "resonance": {"id", "amount"}},
 * {@code "consume": true} (hands the mission item over) and {@code "if"} (dialogue conditions).
 * A duty has no objective of its own: it runs one of its {@code base} contracts, so
 * {@link #objective()} is null for duties.
 */
public record MissionDefinition(String id, Kind kind, String issuer, String shortId, Map<String, String> gate, List<String> requires,
		@Nullable Objective objective, @Nullable Identifier site, Map<String, Integer> counts, @Nullable Identifier loot, @Nullable Identifier item, List<String> base,
		@Nullable String entry, boolean lead, int briefings, Map<String, Choice> choices, boolean driven, @Nullable String planet, List<String> siteKinds) {
	/** What kind of work this is. Stored by ordinal in {@link MissionData}; append only. */
	public enum Kind {
		STORY, CONTRACT, DUTY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The objective types (5.7), named by id in the JSON and never saved. */
	public enum Objective {
		CLEAR, RECOVER, RESCUE, SURVEY, SECURE, INTERCEPT, YIELD,
		/** Milestone 4 (8.7): walk a hub apprentice or acolyte to a waystone or tomb mouth. */
		ESCORT,
		/** Milestone 4 (8.7): win a spar with a hub apprentice or acolyte under the M2 {@code Spar} rules. */
		SPAR;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Whether reaching the site resolves this objective's fighting on Peaceful (6.9): survey, escort and spar never fight. */
		public boolean combat() {
			return this != SURVEY && this != ESCORT && this != SPAR;
		}
	}

	/** Issuer value for contracts issued by the terminal's own army. */
	public static final String TERMINAL = "terminal";
	/** Issuer value for mentor duties. */
	public static final String MENTOR = "mentor";

	/**
	 * One story choice's values (5.4).
	 *
	 * @param commendations     commendations paid, or -1 for {@code missions.storyCommendations}
	 * @param commendationArmy  the army whose commendations are paid, or null for the giver's
	 */
	public record Choice(String id, Map<String, Integer> rep, int alignment, List<ContextAct> context, int commendations,
			@Nullable Faction commendationArmy, @Nullable Identifier loot, @Nullable String codex, @Nullable String resonance,
			int resonanceAmount, boolean consume, Map<String, String> conditions) {
	}

	/** The giver's army for commendations: the issuer when it is an army, else null. */
	public @Nullable Faction issuerArmy() {
		Faction faction = Faction.byId(issuer);
		return faction == Faction.REPUBLIC || faction == Faction.SITH_EMPIRE ? faction : null;
	}

	/** The choice id recorded with {@code Identity.recordChoice}: {@code mission.<short>.<choice>}. */
	public String choiceId(String choice) {
		return "mission." + shortId + "." + choice;
	}

	/** Whether this is a planet contract (offered at a hub's contract terminal, 8.7). */
	public boolean isPlanetContract() {
		return planet != null;
	}

	/** A count from {@code counts}, or {@code fallback}. */
	public int count(String key, int fallback) {
		return counts.getOrDefault(key, fallback);
	}

	/** Parses and validates one definition. Throws {@link JsonParseException} with a readable message. */
	public static MissionDefinition parse(String id, JsonElement json) {
		if (!json.isJsonObject()) {
			throw new JsonParseException("mission must be a JSON object");
		}

		JsonObject root = json.getAsJsonObject();
		Kind kind = byName(Kind.values(), string(root, "kind", ""), "kind");
		// A duty runs one of its base contracts and takes that contract's objective.
		Objective objective = kind == Kind.DUTY && !root.has("objective") ? null : byName(Objective.values(), string(root, "objective", ""), "objective");
		String issuer = string(root, "issuer", "");

		if (!issuer.equals(TERMINAL) && !issuer.equals(MENTOR) && Faction.byId(issuer) == null) {
			throw new JsonParseException("unknown issuer '" + issuer + "'");
		}

		Map<String, Integer> counts = new LinkedHashMap<>();

		if (root.has("counts")) {
			for (Map.Entry<String, JsonElement> entry : object(root, "counts").entrySet()) {
				counts.put(entry.getKey(), entry.getValue().getAsInt());
			}
		}

		Map<String, Choice> choices = new LinkedHashMap<>();

		if (root.has("choices")) {
			for (Map.Entry<String, JsonElement> entry : object(root, "choices").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					throw new JsonParseException("choice '" + entry.getKey() + "' must be an object");
				}

				choices.put(entry.getKey(), parseChoice(entry.getKey(), entry.getValue().getAsJsonObject()));
			}
		}

		String shortId = string(root, "short", id);

		if (kind == Kind.STORY && !choices.isEmpty() && !root.has("short")) {
			throw new JsonParseException("a story mission with choices needs 'short'");
		}

		List<String> base = strings(root, "base");

		if (kind == Kind.DUTY && base.isEmpty()) {
			throw new JsonParseException("a duty needs 'base' contract types");
		}

		String planet = nullableString(root, "planet");

		if (planet != null && !planet.equals("selvaren") && !planet.equals("varkhast")) {
			throw new JsonParseException("unknown planet '" + planet + "'");
		}

		if (planet != null && kind != Kind.CONTRACT) {
			throw new JsonParseException("only a contract can have a 'planet'");
		}

		boolean driven = root.has("driven") && root.get("driven").getAsBoolean();

		if (driven && kind != Kind.STORY) {
			throw new JsonParseException("only a story mission can be 'driven'");
		}

		return new MissionDefinition(id, kind, issuer, shortId, conditions(root, "gate"), strings(root, "requires"), objective,
				identifier(root, "site"), Collections.unmodifiableMap(counts), identifier(root, "loot"), identifier(root, "item"), base, nullableString(root, "entry"),
				root.has("lead") && root.get("lead").getAsBoolean(), Math.max(1, root.has("briefings") ? root.get("briefings").getAsInt() : 1),
				Collections.unmodifiableMap(choices), driven, planet, strings(root, "site_kinds"));
	}

	private static Choice parseChoice(String id, JsonObject json) {
		Map<String, Integer> rep = new LinkedHashMap<>();

		if (json.has("rep")) {
			for (Map.Entry<String, JsonElement> entry : object(json, "rep").entrySet()) {
				if (Faction.byId(entry.getKey()) == null) {
					throw new JsonParseException("choice '" + id + "': unknown faction '" + entry.getKey() + "'");
				}

				rep.put(entry.getKey(), entry.getValue().getAsInt());
			}
		}

		List<ContextAct> context = new ArrayList<>();

		for (String act : strings(json, "context")) {
			context.add(byName(ContextAct.values(), act, "context act"));
		}

		Faction army = null;

		if (json.has("commendation_army")) {
			army = Faction.byId(json.get("commendation_army").getAsString());

			if (army != Faction.REPUBLIC && army != Faction.SITH_EMPIRE) {
				throw new JsonParseException("choice '" + id + "': commendation_army must be republic or sith_empire");
			}
		}

		String resonance = null;
		int resonanceAmount = 0;

		if (json.has("resonance")) {
			JsonObject r = object(json, "resonance");
			resonance = string(r, "id", "");
			resonanceAmount = r.has("amount") ? r.get("amount").getAsInt() : 0;

			if (resonance.isEmpty()) {
				throw new JsonParseException("choice '" + id + "': resonance needs an id");
			}
		}

		return new Choice(id, Collections.unmodifiableMap(rep), json.has("alignment") ? json.get("alignment").getAsInt() : 0, List.copyOf(context),
				json.has("commendations") ? json.get("commendations").getAsInt() : -1, army, identifier(json, "loot"), nullableString(json, "codex"),
				resonance, resonanceAmount, json.has("consume") && json.get("consume").getAsBoolean(), conditions(json, "if"));
	}

	private static <E extends Enum<E>> E byName(E[] values, String name, String what) {
		for (E value : values) {
			if (value.name().equalsIgnoreCase(name)) {
				return value;
			}
		}

		throw new JsonParseException("unknown " + what + " '" + name + "'");
	}

	/** Condition id to its argument: primitives as text, objects as JSON (the dialogue convention). */
	private static Map<String, String> conditions(JsonObject json, String key) {
		if (!json.has(key)) {
			return Map.of();
		}

		Map<String, String> conditions = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> entry : object(json, key).entrySet()) {
			JsonElement value = entry.getValue();
			conditions.put(entry.getKey(), value.isJsonPrimitive() ? value.getAsString() : value.toString());
		}

		return Collections.unmodifiableMap(conditions);
	}

	private static @Nullable Identifier identifier(JsonObject json, String key) {
		String text = nullableString(json, key);

		if (text == null) {
			return null;
		}

		Identifier id = Identifier.tryParse(text);

		if (id == null) {
			throw new JsonParseException("invalid id '" + text + "' in '" + key + "'");
		}

		return id;
	}

	private static List<String> strings(JsonObject json, String key) {
		if (!json.has(key)) {
			return List.of();
		}

		JsonElement element = json.get(key);

		if (!element.isJsonArray()) {
			throw new JsonParseException("'" + key + "' must be a list");
		}

		JsonArray array = element.getAsJsonArray();
		List<String> list = new ArrayList<>(array.size());

		for (JsonElement item : array) {
			list.add(item.getAsString());
		}

		return List.copyOf(list);
	}

	private static JsonObject object(JsonObject json, String key) {
		JsonElement element = json.get(key);

		if (element == null || !element.isJsonObject()) {
			throw new JsonParseException("'" + key + "' must be an object");
		}

		return element.getAsJsonObject();
	}

	private static String string(JsonObject json, String key, String fallback) {
		String text = nullableString(json, key);
		return text == null ? fallback : text;
	}

	private static @Nullable String nullableString(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}
}
