package com.oldrepublic.vendor.hub;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.planet.HubSpawners;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.vendor.VendorBalance;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.vendor.Vendors;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The hubs' people (spec MILESTONE_4.md 3.5, 7.8, 8.3): the {@code hub_spawner} factories W1's
 * spawners call (roles {@code host}, {@code apprentice}, {@code acolyte}, {@code guard} and
 * {@code vendor:<stock>}), who says what when used, the hub spar (casual, or for a spar contract),
 * Zarvos's echo, and the attitude rule that keeps area powers off them.
 *
 * <p>Milestone 5 (MILESTONE_5.md 3.2.1, 3.3.1, 6.11) adds the roles {@code varra}, {@code vessba},
 * {@code teel}, {@code doorman}, {@code bartender} and {@code dorrun}, the story persons Chapter
 * Five spawns for one player ({@link #spawnStory}: Dask, the debtor, the clan's champion), and a
 * table from each host's variant to its dialogue ({@link #hostDialogue}).
 *
 * <p>Dialogue actions: {@code hub:spar} (a casual spar with the speaking apprentice or acolyte) and
 * {@code contract:spar} (the active spar contract's bout). Condition: {@code contract:spar} (the
 * player's spar contract is waiting for its bout).
 *
 * <p>Who hosts each hub, who greets in chat and whom a hub meets coldly are per-planet tables
 * ({@link #host}, {@link #greets}, {@link #coldPath}; spec MILESTONE_5.md 3.1).
 */
public final class HubNpcs {
	/** Every hub person carries this tag. */
	public static final String HUB_TAG = "oldrepublic.hub";
	/** Clan Hask's champion, Varra's second in the duel of honour (MILESTONE_5.md 6.3): a story person with a real blade. */
	public static final String CHAMPION = "hask_champion";
	/** Vessba the Hutt's variant: her own entity type ({@link HubEntities#VESSBA}), a host by role. */
	public static final String VESSBA = "vessba";
	/** How far a hub person may stray from its spawner. */
	static final int LEASH = 6;
	private static final String TALKED_PREFIX = "c4.talked.";
	private static final int APPRENTICE_FACES = 3;
	private static final int ACOLYTE_FACES = 4;
	private static final int GUARD_BARKS = 3;
	private static final int BARTENDER_BARKS = 4;
	/** The Milestone 5 hub roles that make a host of the same name (3.2.1, 3.3.1); Vessba has her own body. */
	private static final List<String> M5_HOSTS = List.of("varra", "teel", "doorman", "bartender", "dorrun");

	public static void init() {
		HubEntities.init();
		HubSpawners.register("host", (level, at, planet, role) -> {
			String host = host(planet);
			return host == null ? null : spawn(level, at, HubRole.HOST, host);
		});
		HubSpawners.register("apprentice", (level, at, planet, role) -> spawn(level, at, HubRole.APPRENTICE, "apprentice_" + face(at, APPRENTICE_FACES)));
		HubSpawners.register("acolyte", (level, at, planet, role) -> spawn(level, at, HubRole.ACOLYTE, "acolyte_" + face(at, ACOLYTE_FACES)));
		HubSpawners.register("guard", (level, at, planet, role) -> spawn(level, at, HubRole.GUARD, "proving_guard"));
		HubSpawners.register("vendor", (level, at, planet, role) -> {
			int colon = role.indexOf(':');
			return colon < 0 ? null : spawn(level, at, HubRole.VENDOR, role.substring(colon + 1));
		});

		for (String host : M5_HOSTS) {
			HubSpawners.register(host, (level, at, planet, role) -> spawn(level, at, HubRole.HOST, host));
		}

		HubSpawners.register(VESSBA, (level, at, planet, role) -> add(level, create(level, HubEntities.VESSBA, at, VESSBA)));

		Attitudes.register(new Attitudes.Rule() {
			@Override
			public @Nullable Attitude toward(LivingEntity self, Player p) {
				return self instanceof HubNpc npc ? (cold(npc, p) ? Attitude.WARY : Attitude.NEUTRAL) : null;
			}
		});

		DialogueActions.register("hub:spar", (p, speaker) -> spar(p, speaker, false));
		DialogueActions.register("contract:spar", (p, speaker) -> spar(p, speaker, true));
		DialogueConditions.register("contract:spar", (p, speaker, arg) -> PlanetContracts.sparWaiting(p) ? null : "message.oldrepublic.mission.no_spar");
	}

	// ---------------------------------------------------------------- spawning

	/** One hub person at {@code at} (a free feet cell), leashed there, in the level; null when the type refused. */
	public static @Nullable HubNpc spawn(ServerLevel level, BlockPos at, HubRole role, String variant) {
		return add(level, create(level, HubEntities.type(role), at, variant));
	}

	/**
	 * A story person of {@code owner}'s (MILESTONE_5.md 6.3, 6.4): a {@code hub_host} of
	 * {@code variant} at {@code at} that talks only to its owner and is not saved, carrying
	 * {@code tag} and the owner's story tag so the story can find it and clear it up.
	 *
	 * <p>{@code tag} goes on once the person is in the level: Fabric's entity load event fires for a
	 * fresh spawn too, and the story's load rule discards any loaded entity carrying a chapter's tag.
	 */
	public static @Nullable HubNpc spawnStory(ServerLevel level, BlockPos at, String variant, UUID owner, String tag) {
		HubNpc npc = create(level, HubEntities.HUB_HOST, at, variant);

		if (npc == null) {
			return null;
		}

		npc.setStoryOwner(owner);
		StoryTags.setOwner(npc, owner);
		HubNpc added = add(level, npc);

		if (added != null) {
			added.addTag(tag);
		}

		return added;
	}

	private static @Nullable HubNpc create(ServerLevel level, EntityType<HubNpc> type, BlockPos at, String variant) {
		HubNpc npc = type.create(level, EntitySpawnReason.EVENT);

		if (npc == null) {
			return null;
		}

		npc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		npc.setVariant(variant);
		npc.setAnchor(at, LEASH);
		npc.addTag(HUB_TAG);
		npc.setPersistenceRequired();
		equip(npc);
		return npc;
	}

	private static @Nullable HubNpc add(ServerLevel level, @Nullable HubNpc npc) {
		return npc != null && level.addFreshEntity(npc) ? npc : null;
	}

	/** A seeded face for a spawner: the same spawner always makes the same apprentice. */
	static int face(BlockPos at, int faces) {
		return Math.floorMod(Long.hashCode(at.asLong() * 0x9E3779B97F4A7C15L), faces);
	}

	/**
	 * What a hub person carries: training sabers for apprentices and acolytes (W5's items, when
	 * present), a rifle for a guard, and a vibroblade for Clan Hask's champion (a real blade, 6.3).
	 */
	static void equip(HubNpc npc) {
		if (!npc.getMainHandItem().isEmpty()) {
			return;
		}

		String held = CHAMPION.equals(npc.variant()) ? "vibroblade" : switch (npc.role()) {
			case APPRENTICE -> "training_saber";
			case ACOLYTE -> "training_saber_red";
			case GUARD -> "blaster_rifle";
			default -> null;
		};

		Item item = held == null ? null : BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(held)).orElse(null);

		if (item != null) {
			npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
			npc.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
	}

	/** The lang key of a hub person's name: {@code npc.oldrepublic.<variant>.name}. */
	public static String nameKey(HubRole role, String variant) {
		return variant.isEmpty() ? "entity.oldrepublic." + BuiltInRegistries.ENTITY_TYPE.getKey(HubEntities.type(role)).getPath()
				: "npc.oldrepublic." + variant + ".name";
	}

	// ---------------------------------------------------------------- talking

	/** The player used a hub person: the host's, an apprentice's, a vendor's or a trainer's dialogue, or a guard's bark. */
	public static void onUsed(ServerPlayer p, HubNpc npc) {
		if (Spar.inSpar(p) || Spar.isPartner(npc)) {
			return;
		}

		switch (npc.role()) {
			case HOST -> host(p, npc);
			case APPRENTICE -> Dialogues.open(p, OldRepublicMod.id("apprentice"), npc);
			case ACOLYTE -> Dialogues.open(p, OldRepublicMod.id("acolyte"), npc);
			case GUARD -> say(p, npc, "npc.oldrepublic.proving_guard." + (cold(npc, p) ? "bark_wary" : "bark." + p.getRandom().nextInt(GUARD_BARKS)));
			case VENDOR -> {
				String stock = npc.variant();
				String dialogue = stock.startsWith("trainer_") ? stock : "vendor_" + stock;

				if (!Dialogues.open(p, OldRepublicMod.id(dialogue), npc)) {
					Vendors.open(p, npc, OldRepublicMod.id(stock));
				}
			}
		}
	}

	/**
	 * A host was used: a story person answers only its owner; Chapter Five may take the moment (the
	 * champion's bout); then Ansa's and Kaddor's Milestone 4 dialogues, each Milestone 5 host's dialogue
	 * by variant ({@link #hostDialogue}), or the bartender's bark.
	 */
	private static void host(ServerPlayer p, HubNpc npc) {
		String host = npc.variant();
		UUID owner = npc.storyOwner();

		if (owner != null && !owner.equals(p.getUUID())) {
			p.sendOverlayMessage(Component.translatable("npc.oldrepublic.hub.not_yours").withStyle(ChatFormatting.GRAY));
			return;
		}

		if (ChapterFiveService.onPersonUsed(p, npc)) {
			return;
		}

		if ("ansa".equals(host) || "kaddor".equals(host)) {
			StoryData data = Story.get(p);
			boolean first = data.flags.add(TALKED_PREFIX + host);

			if (first) {
				Story.commit(p, data);
			}

			Dialogues.open(p, OldRepublicMod.id(first ? "c4_" + host + "_meet" : "c4_" + host), npc);
			return;
		}

		String dialogue = hostDialogue(host);

		if (dialogue != null) {
			Dialogues.open(p, OldRepublicMod.id(dialogue), npc);
		} else if ("bartender".equals(host)) {
			say(p, npc, "npc.oldrepublic.bartender.bark." + p.getRandom().nextInt(BARTENDER_BARKS));
		}
	}

	/**
	 * The dialogue a Milestone 5 host opens, by variant (MILESTONE_5.md 6.11), or null for one who only
	 * barks or does not talk (the bartender, the champion, a contract's escort).
	 */
	public static @Nullable String hostDialogue(String variant) {
		return switch (variant) {
			case "harbourmaster" -> "c5_harbourmaster";
			case "teel" -> "c5_majordomo";
			case VESSBA -> "c5_vessba";
			case "varra" -> "c5_varra";
			case "dorrun" -> "c5_dorrun";
			case "doorman" -> "c5_doorman";
			case "dask" -> "c5_dask";
			case "debtor" -> "c5_debtor";
			case "warden" -> "turn_warden";
			default -> null;
		};
	}

	/** A player struck a hub person: a short line, nothing else (they cannot be hurt). */
	static void onStruck(ServerPlayer p, HubNpc npc) {
		p.sendOverlayMessage(Component.translatable("npc.oldrepublic.hub.struck." + npc.role().id()).withStyle(ChatFormatting.GRAY));
	}

	/**
	 * The host's welcome on the player's first landing on {@code planet} (8.3); the other side hears the
	 * cold peace. Only Ansa and Kaddor greet in chat ({@link #greets}).
	 */
	public static void greet(ServerPlayer p, Planets.Planet planet) {
		String host = greeter(planet);

		if (host == null) {
			return;
		}

		boolean cold = Identity.path(p) == coldPath(planet);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", Component.translatable("npc.oldrepublic." + host + ".name"),
				Component.translatable("npc.oldrepublic." + host + ".c4." + (cold ? "welcome_wary" : "welcome"))));
	}

	/** A line spoken by a hub person in chat ("Name: line"). */
	static void say(ServerPlayer p, HubNpc npc, String lineKey) {
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", npc.getDisplayName(), Component.translatable(lineKey)));
	}

	/** The other side's hub: Sith among the Watch, Jedi at the Proving (Wary, 3.5). */
	static boolean cold(HubNpc npc, Player p) {
		Planets.Planet planet = Planets.Planet.of(npc.level().dimension());
		Path cold = planet == null ? null : coldPath(planet);
		return cold != null && Identity.path(p) == cold;
	}

	// ---------------------------------------------------------------- per-planet rows (MILESTONE_5.md 3.1)

	/**
	 * The person a {@code host} spawner makes on {@code planet}: Ansa at the Greenwatch (and, as in
	 * Milestone 4, on the home world), Kaddor at the Proving, the harbourmaster at Saltgate (3.2.1);
	 * null at the holdfast, which has no host (3.3.1), and on a world without a row.
	 */
	public static @Nullable String host(Planets.Planet planet) {
		return switch (planet) {
			case HOME, SELVAREN -> "ansa";
			case VARKHAST -> "kaddor";
			case GHESSAR -> "harbourmaster";
			default -> null;
		};
	}

	/** Whether {@code planet}'s host greets a first landing in chat (8.3): Ansa and Kaddor do; the Milestone 5 hubs' people speak for themselves. */
	public static boolean greets(Planets.Planet planet) {
		return greeter(planet) != null;
	}

	/** The path a hub meets coldly (Wary): Sith on Selvaren, Jedi on Varkhast; null where nobody is (the Milestone 5 hubs judge by standing, 7). */
	public static @Nullable Path coldPath(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> Path.SITH;
			case VARKHAST -> Path.JEDI;
			default -> null;
		};
	}

	/** The host whose chat welcome a first landing on {@code planet} hears, or null. */
	private static @Nullable String greeter(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> "ansa";
			case VARKHAST -> "kaddor";
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the hub spar

	/**
	 * {@code hub:spar} / {@code contract:spar}: a bout with the speaking apprentice or acolyte under
	 * the M2 spar rules (first to yield; nobody dies). A contract bout won completes the spar contract.
	 */
	static void spar(ServerPlayer p, @Nullable Entity speaker, boolean contract) {
		if (!(speaker instanceof HubNpc npc) || (npc.role() != HubRole.APPRENTICE && npc.role() != HubRole.ACOLYTE) || cold(npc, p)) {
			return;
		}

		if (contract && !PlanetContracts.sparWaiting(p)) {
			return;
		}

		VendorBalance b = VendorFeature.balance();
		SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, (float) b.hubSparYield, (float) b.hubSparDamage);
		UUID id = p.getUUID();
		boolean started = Spar.start(p, npc, rules, result -> onSparResult(p.level().getServer().getPlayerList().getPlayer(id), contract, result));

		if (started) {
			npc.level().playSound(null, npc.getX(), npc.getY() + 1, npc.getZ(), ModSounds.HUB_APPRENTICE_SPAR, SoundSource.NEUTRAL, 0.8f, 1f);
		}
	}

	/**
	 * A Tyrvann spar contract's bout with Clan Hask's champion, who stands in for the holdfast's
	 * apprentices (MILESTONE_5.md 7.2; the partner {@link PlanetContracts} places near the site): the
	 * hub spar's rules, and a bout won completes the contract. False when no bout started (no contract
	 * waiting for its bout, or a spar already running).
	 */
	public static boolean contractSpar(ServerPlayer p, HubNpc partner) {
		if (!PlanetContracts.sparWaiting(p) || Spar.inSpar(p) || Spar.isPartner(partner)) {
			return false;
		}

		VendorBalance b = VendorFeature.balance();
		SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, (float) b.hubSparYield, (float) b.hubSparDamage);
		UUID id = p.getUUID();
		boolean started = Spar.start(p, partner, rules, result -> onSparResult(p.level().getServer().getPlayerList().getPlayer(id), true, result));

		if (started) {
			partner.level().playSound(null, partner.getX(), partner.getY() + 1, partner.getZ(), ModSounds.HUB_APPRENTICE_SPAR, SoundSource.NEUTRAL, 0.8f, 1f);
		}

		return started;
	}

	private static void onSparResult(@Nullable ServerPlayer p, boolean contract, SparResult result) {
		if (p != null && contract && result == SparResult.WON) {
			PlanetContracts.sparWon(p);
		}
	}

	// ---------------------------------------------------------------- Zarvos's echo

	/** Zarvos's echo for {@code owner}'s Sepulchre trial (8.4): still, untouchable, not saved. */
	public static @Nullable Entity spawnEcho(ServerLevel level, BlockPos at, UUID owner) {
		HubNpc echo = HubEntities.HUB_HOST.create(level, EntitySpawnReason.EVENT);

		if (echo == null) {
			return null;
		}

		echo.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		echo.setVariant("zarvos");
		echo.setEcho(true);

		if (!level.addFreshEntity(echo)) {
			return null;
		}

		level.playSound(null, echo.getX(), echo.getY() + 1, echo.getZ(), ModSounds.TRIAL_ECHO_FORM, SoundSource.HOSTILE, 1f, 0.8f);
		level.sendParticles(QuestParticles.ASH_MOTE, echo.getX(), echo.getY() + 1, echo.getZ(), 16, 0.4, 0.8, 0.4, 0.01);
		return echo;
	}

	/** The Choke landed on the echo: it comes apart in ash and is gone. */
	public static void breakEcho(Entity echo) {
		if (echo.level() instanceof ServerLevel level) {
			level.playSound(null, echo.getX(), echo.getY() + 1, echo.getZ(), ModSounds.BOSS_COLLECTOR_ECHO_FADE, SoundSource.HOSTILE, 1f, 0.9f);
			level.sendParticles(QuestParticles.ASH_MOTE, echo.getX(), echo.getY() + 1, echo.getZ(), 24, 0.35, 0.9, 0.35, 0.02);
		}

		echo.discard();
	}

	/** Whether an entity is a hub person (tests, commands). */
	public static boolean isHubNpc(Entity entity) {
		return entity instanceof HubNpc;
	}

	/** Whether a mob is an escort of {@code owner}. */
	public static boolean isEscortOf(Mob mob, UUID owner) {
		return mob instanceof HubNpc npc && owner.equals(npc.escortOwner());
	}

	/** The id of a variant's texture, {@code textures/entity/hub/<variant>.png} (the client's fallback is by role). */
	public static Identifier texture(String variant) {
		return OldRepublicMod.id("textures/entity/hub/" + variant + ".png");
	}

	private HubNpcs() {
	}
}
