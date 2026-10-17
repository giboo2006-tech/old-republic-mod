package com.oldrepublic.skill;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;

/**
 * The loaded skill trees. The server set is loaded from {@code data/<namespace>/skill_tree/*.json}
 * on server start and {@code /reload}; the client set is whatever the server last sent in the
 * {@code skill_trees} payload. Both live here (common code) so {@link Skills} can answer on either
 * side; {@link #of(Player)} picks the right one.
 */
public final class SkillTrees {
	/** Reload listener id. */
	public static final Identifier RELOADER_ID = OldRepublicMod.id("skill_trees");
	private static final FileToIdConverter FILES = FileToIdConverter.json("skill_tree");

	private static volatile SkillTreeSet server = SkillTreeSet.EMPTY;
	private static volatile Map<String, String> serverJson = Map.of();
	private static volatile SkillTreeSet client = SkillTreeSet.EMPTY;

	/** The server's trees (empty until data has loaded). */
	public static SkillTreeSet server() {
		return server;
	}

	/** The trees the client last received (empty in a dedicated server). */
	public static SkillTreeSet client() {
		return client;
	}

	/** The trees for the side {@code player} lives on. */
	public static SkillTreeSet of(Player player) {
		return player.level().isClientSide() ? client : server;
	}

	/** Compact JSON of every server tree, keyed by tree id, for the {@code skill_trees} payload. */
	public static Map<String, String> serverJson() {
		return serverJson;
	}

	/** Installs the trees received from the server (client thread). */
	public static void setClient(SkillTreeSet trees) {
		client = trees;
	}

	/** Builds a set from tree JSON texts (the payload contents, or test data). Bad trees are logged and skipped. */
	public static SkillTreeSet fromJson(Map<String, String> trees) {
		List<SkillTree> parsed = new ArrayList<>();

		for (Map.Entry<String, String> entry : trees.entrySet()) {
			try {
				parsed.add(SkillTreeLoader.parse(entry.getKey(), JsonParser.parseString(entry.getValue()).getAsJsonObject()));
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("Skipping skill tree {}: {}", entry.getKey(), e.getMessage());
			}
		}

		return new SkillTreeSet(parsed);
	}

	/** Reload listener body: reads every tree file, replaces the server set and logs problems. */
	static void load(ResourceManager manager) {
		Map<String, String> json = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			String treeId = FILES.fileToId(entry.getKey()).getPath();

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
				SkillTreeLoader.parse(treeId, object);

				if (json.put(treeId, object.toString()) != null) {
					OldRepublicMod.LOGGER.warn("Skill tree {} is defined more than once; using {}", treeId, entry.getKey());
				}
			} catch (Exception e) {
				OldRepublicMod.LOGGER.error("Could not load skill tree {}: {}", entry.getKey(), e.getMessage());
			}
		}

		SkillTreeSet set = fromJson(json);

		for (String error : set.errors()) {
			OldRepublicMod.LOGGER.error("Skill trees: {}", error);
		}

		serverJson = Collections.unmodifiableMap(json);
		server = set;
		OldRepublicMod.LOGGER.info("Loaded {} skill trees with {} nodes", set.trees().size(), set.nodes().size());
	}

	private SkillTrees() {
	}
}
