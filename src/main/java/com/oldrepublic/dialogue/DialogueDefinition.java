package com.oldrepublic.dialogue;

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

import net.minecraft.resources.Identifier;

/**
 * One dialogue file, {@code data/<namespace>/dialogue/<path>.json}, parsed and validated.
 *
 * <pre>{@code
 * {
 *   "speaker": "oldrepublic:jedi_mentor",          // optional, informational
 *   "name": "npc.oldrepublic.saelen",              // optional lang key; else the speaker's name
 *   "portrait": "oldrepublic:portrait/jedi_mentor",// optional GUI sprite
 *   "accent": "#8FE3A0",                           // optional rule colour under the name
 *   "start": "greet",                              // or [{"if": {...}, "node": "a"}, {"node": "b"}]
 *   "nodes": {
 *     "greet": {
 *       "line": "dialogue.oldrepublic.x.greet", "args": ["{player}"],
 *       "options": [
 *         {"text": "...", "if": {"path": "none"}, "hidden_if_unmet": false,
 *          "stance": {"source": "dialogue.x.greet", "alignment": 3, "trust": {"jedi": 3}},
 *          "action": "story:join_jedi", "next": "confirm", "close": false}
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * Arguments are plain text, {@code {player}} / {@code {speaker}} (replaced by names), or
 * {@code @lang.key} (translated on the client). An option without {@code next} closes the dialogue.
 */
public record DialogueDefinition(Identifier id, String nameKey, String portrait, int accent, List<StartRule> start, Map<String, Node> nodes) {
	/** A conditional start node; the first rule whose conditions hold is used. */
	public record StartRule(Map<String, String> conditions, String node) {
	}

	public record Node(String id, String line, List<String> args, List<Option> options) {
	}

	public record Option(String text, List<String> args, Map<String, String> conditions, boolean hiddenIfUnmet,
			@Nullable String next, boolean close, @Nullable String action, @Nullable Stance stance) {
	}

	/** A one-time alignment shift and trust change applied when the option is picked. */
	public record Stance(String source, int alignment, Map<String, Integer> trust) {
	}

	/** Parses and validates one dialogue. Throws {@link JsonParseException} with a readable message. */
	public static DialogueDefinition parse(Identifier id, JsonElement json) {
		if (!json.isJsonObject()) {
			throw new JsonParseException("dialogue must be a JSON object");
		}

		JsonObject root = json.getAsJsonObject();
		JsonObject nodesJson = object(root, "nodes");
		Map<String, Node> nodes = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> entry : nodesJson.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				throw new JsonParseException("node '" + entry.getKey() + "' must be an object");
			}

			nodes.put(entry.getKey(), parseNode(entry.getKey(), entry.getValue().getAsJsonObject()));
		}

		if (nodes.isEmpty()) {
			throw new JsonParseException("dialogue has no nodes");
		}

		List<StartRule> start = parseStart(root.get("start"));
		String portrait = string(root, "portrait", "");

		if (!portrait.isEmpty() && Identifier.tryParse(portrait) == null) {
			throw new JsonParseException("invalid portrait id '" + portrait + "'");
		}

		DialogueDefinition definition = new DialogueDefinition(id, string(root, "name", ""), portrait, parseColour(string(root, "accent", "")),
				List.copyOf(start), Collections.unmodifiableMap(nodes));
		definition.validate();
		return definition;
	}

	private void validate() {
		for (StartRule rule : start) {
			requireNode(rule.node(), "start");
		}

		for (Node node : nodes.values()) {
			for (Option option : node.options()) {
				if (option.next() != null) {
					requireNode(option.next(), "option '" + option.text() + "' of node '" + node.id() + "'");
				}
			}
		}
	}

	private void requireNode(String nodeId, String where) {
		if (!nodes.containsKey(nodeId)) {
			throw new JsonParseException(where + " refers to unknown node '" + nodeId + "'");
		}
	}

	private static List<StartRule> parseStart(@Nullable JsonElement json) {
		List<StartRule> rules = new ArrayList<>();

		if (json == null) {
			throw new JsonParseException("missing 'start'");
		}

		if (json.isJsonPrimitive()) {
			rules.add(new StartRule(Map.of(), json.getAsString()));
			return rules;
		}

		if (!json.isJsonArray()) {
			throw new JsonParseException("'start' must be a node id or a list of {\"if\", \"node\"}");
		}

		for (JsonElement element : json.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				throw new JsonParseException("each start rule must be an object");
			}

			JsonObject rule = element.getAsJsonObject();
			rules.add(new StartRule(conditions(rule), requiredString(rule, "node")));
		}

		if (rules.isEmpty() || !rules.getLast().conditions().isEmpty()) {
			throw new JsonParseException("the last start rule must have no conditions (the fallback)");
		}

		return rules;
	}

	private static Node parseNode(String nodeId, JsonObject json) {
		List<Option> options = new ArrayList<>();

		if (json.has("options")) {
			for (JsonElement element : array(json, "options")) {
				if (!element.isJsonObject()) {
					throw new JsonParseException("options of node '" + nodeId + "' must be objects");
				}

				options.add(parseOption(element.getAsJsonObject()));
			}
		}

		if (options.isEmpty()) {
			throw new JsonParseException("node '" + nodeId + "' has no options (add a leave option)");
		}

		return new Node(nodeId, requiredString(json, "line"), strings(json, "args"), List.copyOf(options));
	}

	private static Option parseOption(JsonObject json) {
		Stance stance = null;

		if (json.has("stance")) {
			JsonObject s = object(json, "stance");
			Map<String, Integer> trust = new LinkedHashMap<>();

			if (s.has("trust")) {
				for (Map.Entry<String, JsonElement> entry : object(s, "trust").entrySet()) {
					trust.put(entry.getKey(), entry.getValue().getAsInt());
				}
			}

			stance = new Stance(requiredString(s, "source"), s.has("alignment") ? s.get("alignment").getAsInt() : 0, Map.copyOf(trust));
		}

		String next = json.has("next") ? json.get("next").getAsString() : null;
		boolean close = json.has("close") && json.get("close").getAsBoolean();
		String action = json.has("action") ? json.get("action").getAsString() : null;
		boolean hidden = json.has("hidden_if_unmet") && json.get("hidden_if_unmet").getAsBoolean();
		return new Option(requiredString(json, "text"), strings(json, "args"), conditions(json), hidden, next, close || next == null, action, stance);
	}

	/** The {@code "if"} object: condition id to its argument (primitives as text, objects as JSON). */
	private static Map<String, String> conditions(JsonObject json) {
		if (!json.has("if")) {
			return Map.of();
		}

		Map<String, String> conditions = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> entry : object(json, "if").entrySet()) {
			JsonElement value = entry.getValue();
			conditions.put(entry.getKey(), value.isJsonPrimitive() ? value.getAsString() : value.toString());
		}

		return Collections.unmodifiableMap(conditions);
	}

	private static int parseColour(String text) {
		if (text.isEmpty()) {
			return 0;
		}

		String hex = text.startsWith("#") ? text.substring(1) : text;

		try {
			return 0xFF000000 | Integer.parseInt(hex.toLowerCase(Locale.ROOT), 16);
		} catch (NumberFormatException e) {
			throw new JsonParseException("invalid accent colour '" + text + "'");
		}
	}

	private static JsonObject object(JsonObject json, String key) {
		JsonElement element = json.get(key);

		if (element == null || !element.isJsonObject()) {
			throw new JsonParseException("'" + key + "' must be an object");
		}

		return element.getAsJsonObject();
	}

	private static JsonArray array(JsonObject json, String key) {
		JsonElement element = json.get(key);

		if (element == null || !element.isJsonArray()) {
			throw new JsonParseException("'" + key + "' must be a list");
		}

		return element.getAsJsonArray();
	}

	private static String requiredString(JsonObject json, String key) {
		JsonElement element = json.get(key);

		if (element == null || !element.isJsonPrimitive()) {
			throw new JsonParseException("missing '" + key + "'");
		}

		return element.getAsString();
	}

	private static String string(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}

	private static List<String> strings(JsonObject json, String key) {
		if (!json.has(key)) {
			return List.of();
		}

		List<String> values = new ArrayList<>();

		for (JsonElement element : array(json, key)) {
			values.add(element.getAsString());
		}

		return List.copyOf(values);
	}
}
