package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oldrepublic.identity.Path;

import net.minecraft.resources.Identifier;

/**
 * Parses one skill tree file. The same parser runs on the server (data load) and on the client
 * (the {@code skill_trees} payload carries the JSON), so both sides always agree. Errors throw
 * {@link IllegalArgumentException} with a message naming the field.
 *
 * <pre>{@code
 * { "order": 0, "visible": "push", "tint": "#E8D7A0",
 *   "nodes": [ { "id": "pull_disarm", "kind": "branch", "group": "pull_shape", "cost": [1], "min_level": [6],
 *                "requires": ["force_pull"], "pos": [3, 2], "icon": "oldrepublic:skill/pull_disarm",
 *                "info": ["disarm_knock", "disarm_seconds"] } ] }
 * }</pre>
 *
 * {@code requires} entries are a node id ({@code "force_pull"}) or a node id and rank
 * ({@code "deep_reserves:1"}).
 */
public final class SkillTreeLoader {
	public static SkillTree parse(String treeId, JsonObject json) {
		int order = json.has("order") ? json.get("order").getAsInt() : 0;
		String visibleId = string(json, "visible", "push");
		TreeVisibility visibility = TreeVisibility.byId(visibleId);

		if (visibility == null) {
			throw new IllegalArgumentException("Tree " + treeId + ": unknown visibility '" + visibleId + "'");
		}

		int tint = parseColor(string(json, "tint", ""));
		List<SkillNode> nodes = new ArrayList<>();

		if (!json.has("nodes") || !json.get("nodes").isJsonArray()) {
			throw new IllegalArgumentException("Tree " + treeId + ": missing 'nodes' array");
		}

		for (JsonElement element : json.getAsJsonArray("nodes")) {
			nodes.add(parseNode(treeId, element.getAsJsonObject()));
		}

		return new SkillTree(treeId, order, visibility, tint, List.copyOf(nodes));
	}

	private static SkillNode parseNode(String treeId, JsonObject json) {
		String id = string(json, "id", "");

		if (id.isEmpty() || !id.matches("[a-z0-9_]+")) {
			throw new IllegalArgumentException("Tree " + treeId + ": invalid node id '" + id + "'");
		}

		String where = "Tree " + treeId + ", node " + id + ": ";
		String kindId = string(json, "kind", "");
		NodeKind kind = NodeKind.byId(kindId);

		if (kind == null) {
			throw new IllegalArgumentException(where + "unknown kind '" + kindId + "'");
		}

		String group = nullableString(json, "group");

		if (kind == NodeKind.BRANCH && group == null) {
			throw new IllegalArgumentException(where + "a branch needs a group");
		}

		String ability = nullableString(json, "ability");

		if (kind == NodeKind.ABILITY && ability == null) {
			ability = id;
		}

		List<Integer> cost = ints(json, "cost", where);
		List<Integer> minLevel = ints(json, "min_level", where);
		List<SkillNode.Requirement> requires = new ArrayList<>();

		for (String req : strings(json, "requires")) {
			int colon = req.indexOf(':');
			String node = colon < 0 ? req : req.substring(0, colon);
			int rank = colon < 0 ? 1 : Integer.parseInt(req.substring(colon + 1));
			requires.add(new SkillNode.Requirement(node, Math.max(1, rank)));
		}

		Path path = parsePath(nullableString(json, "path"), where);
		List<Path> taughtBy = new ArrayList<>();

		for (String p : strings(json, "taught_by")) {
			taughtBy.add(parsePath(p, where));
		}

		Integer alignmentMin = json.has("alignment_min") && !json.get("alignment_min").isJsonNull() ? json.get("alignment_min").getAsInt() : null;
		Integer alignmentMax = json.has("alignment_max") && !json.get("alignment_max").isJsonNull() ? json.get("alignment_max").getAsInt() : null;
		boolean grantedOnly = json.has("granted_only") && json.get("granted_only").getAsBoolean();
		float x = 0;
		float y = 0;

		if (json.has("pos")) {
			JsonArray pos = json.getAsJsonArray("pos");
			x = pos.get(0).getAsFloat();
			y = pos.get(1).getAsFloat();
		}

		String iconId = string(json, "icon", "oldrepublic:skill/" + id);
		Identifier icon;

		try {
			icon = Identifier.parse(iconId);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(where + "invalid icon '" + iconId + "'", e);
		}

		String infoSource = string(json, "info_source", "");
		return new SkillNode(id, treeId, kind, group, ability, List.copyOf(cost), List.copyOf(minLevel), List.copyOf(requires), path,
				alignmentMin, alignmentMax, grantedOnly, List.copyOf(taughtBy), nullableString(json, "granted_hint"), x, y, icon,
				List.copyOf(strings(json, "info")), infoSource);
	}

	private static @Nullable Path parsePath(@Nullable String id, String where) {
		if (id == null) {
			return null;
		}

		Path path = Path.byId(id);

		if (path == Path.NONE) {
			throw new IllegalArgumentException(where + "unknown path '" + id + "'");
		}

		return path;
	}

	/** {@code "#RRGGBB"} or {@code "#AARRGGBB"} to ARGB; empty is 0 (no tint). */
	static int parseColor(String text) {
		if (text.isEmpty()) {
			return 0;
		}

		String hex = text.startsWith("#") ? text.substring(1) : text;
		long value = Long.parseLong(hex, 16);
		return (int) (hex.length() <= 6 ? 0xFF000000L | value : value);
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
	}

	private static @Nullable String nullableString(JsonObject json, String key) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
	}

	private static List<String> strings(JsonObject json, String key) {
		List<String> out = new ArrayList<>();

		if (json.has(key) && json.get(key).isJsonArray()) {
			for (JsonElement e : json.getAsJsonArray(key)) {
				out.add(e.getAsString());
			}
		}

		return out;
	}

	private static List<Integer> ints(JsonObject json, String key, String where) {
		List<Integer> out = new ArrayList<>();

		if (json.has(key) && json.get(key).isJsonArray()) {
			for (JsonElement e : json.getAsJsonArray(key)) {
				int value = e.getAsInt();

				if (value < 0) {
					throw new IllegalArgumentException(where + key + " must not be negative");
				}

				out.add(value);
			}
		}

		return out;
	}

	private SkillTreeLoader() {
	}
}
