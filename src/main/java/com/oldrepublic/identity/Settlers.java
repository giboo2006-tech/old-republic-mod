package com.oldrepublic.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frontier Settlers for story encounters: spawning captives (and free settlers for tests), and
 * resolving a captive group once for the deciding player: cut loose, made to pay, or left bound.
 */
public final class Settlers {
	/** The captives dialogue ({@code data/oldrepublic/dialogue/captives.json}). */
	public static final Identifier CAPTIVES_DIALOGUE = OldRepublicMod.id("captives");
	/** Supply bundle freed settlers leave when the Frontier trusts the player. */
	public static final ResourceKey<LootTable> THANKS_LOOT = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/settler_thanks"));
	/** What captives hand over when made to pay for their freedom. */
	public static final ResourceKey<LootTable> PAYMENT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/captive_payment"));

	/** Captives run this far toward home when cut loose (they vanish long before arriving). */
	private static final double HOME_DISTANCE = 40;
	/** Group members are looked for within this distance of the speaker or player. */
	private static final double GROUP_RANGE = 96;

	/**
	 * Spawns one captive settler bound at each post, owned by {@code owner} and tagged with
	 * {@code groupId} so the captives can be resolved together. Each position is the block the
	 * settler kneels in (its feet), facing the centre of the group; home lies away from the group.
	 *
	 * @return the spawned settlers
	 */
	public static List<Entity> spawnCaptives(ServerLevel l, List<BlockPos> posts, UUID owner, String groupId) {
		List<Entity> spawned = new ArrayList<>();

		if (posts.isEmpty()) {
			return spawned;
		}

		Vec3 centre = Vec3.ZERO;

		for (BlockPos post : posts) {
			centre = centre.add(Vec3.atBottomCenterOf(post));
		}

		centre = centre.scale(1.0 / posts.size());

		for (BlockPos post : posts) {
			Vec3 at = Vec3.atBottomCenterOf(post);
			Vec3 out = at.subtract(centre).multiply(1, 0, 1);

			if (out.lengthSqr() < 1.0e-4) {
				float angle = l.getRandom().nextFloat() * Mth.TWO_PI;
				out = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
			}

			out = out.normalize();
			BlockPos home = BlockPos.containing(at.add(out.scale(HOME_DISTANCE)));
			FrontierSettler settler = create(l, at, centre);
			settler.makeCaptive(owner, groupId, home);
			l.addFreshEntity(settler);
			spawned.add(settler);
		}

		return spawned;
	}

	/** Spawns a free settler living around {@code pos} (commands and tests). */
	public static FrontierSettler spawnFree(ServerLevel l, BlockPos pos) {
		Vec3 at = Vec3.atBottomCenterOf(pos);
		FrontierSettler settler = create(l, at, null);
		settler.makeFree(pos);
		settler.setPersistenceRequired();
		l.addFreshEntity(settler);
		return settler;
	}

	private static FrontierSettler create(ServerLevel l, Vec3 at, @Nullable Vec3 face) {
		FrontierSettler settler = new FrontierSettler(IdentityEntities.FRONTIER_SETTLER, l);
		float yaw = l.getRandom().nextFloat() * 360f;

		if (face != null && face.distanceToSqr(at) > 1.0e-4) {
			Vec3 look = face.subtract(at);
			yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
		}

		settler.setPos(at.x, at.y, at.z);
		settler.setYRot(yaw);
		settler.setYHeadRot(yaw);
		settler.setYBodyRot(yaw);
		settler.setVariant(l.getRandom().nextInt(FrontierSettler.VARIANTS));
		return settler;
	}

	/** The choice id recorded when a group is resolved. */
	public static String choiceId(String groupId) {
		return "captives:" + groupId;
	}

	/** Whether this player already resolved the group. */
	public static boolean isResolved(ServerPlayer player, String groupId) {
		return Identity.hasChoice(player, choiceId(groupId));
	}

	/**
	 * Resolves a captive group for the deciding player, once. Loaded members near the player are cut
	 * loose (FREED, EXPLOITED) or stay bound (ABANDONED), then the quest feature hears of it through
	 * {@link QuestHooks#onCaptivesResolved} (which applies the choice's reputation). Freed settlers
	 * then leave a supply bundle when the Frontier trusts the player, and captives made to pay hand
	 * over theirs. The story feature also calls
	 * this with ABANDONED when the player leaves the site with the captives unresolved.
	 *
	 * @param speaker the captive the player spoke to, or null (the bundle then drops at the player)
	 * @return false if the group was already resolved
	 */
	public static boolean resolveCaptives(ServerPlayer player, String groupId, CaptiveOutcome outcome, @Nullable FrontierSettler speaker) {
		if (isResolved(player, groupId)) {
			return false;
		}

		Identity.recordChoice(player, choiceId(groupId));
		Identity.recordChoice(player, choiceId(groupId) + "=" + outcome.name().toLowerCase(Locale.ROOT));
		ServerLevel level = player.level();
		Vec3 centre = speaker != null ? speaker.position() : player.position();
		List<FrontierSettler> members = level.getEntitiesOfClass(FrontierSettler.class, new AABB(centre, centre).inflate(GROUP_RANGE),
				s -> groupId.equals(s.groupId()) && s.state() == FrontierSettler.State.CAPTIVE);

		if (outcome != CaptiveOutcome.ABANDONED) {
			int variant = outcome == CaptiveOutcome.FREED ? 0 : 1;

			for (FrontierSettler settler : members) {
				EffectEvents.around(level, IdentityEffects.SETTLER_FREED, settler.wristPosition(), null, settler.getId(), 1f, variant);
				settler.startFleeing();
			}
		}

		// The story feature applies this choice's reputation first, so the bundle below follows the
		// Frontier standing the choice itself earned.
		QuestHooks.onCaptivesResolved(player, groupId, outcome);
		Vec3 drop = speaker != null ? speaker.position() : player.position();

		if (outcome == CaptiveOutcome.EXPLOITED) {
			dropLoot(level, PAYMENT_LOOT, drop);
		} else if (outcome == CaptiveOutcome.FREED) {
			if (Identity.tier(player, Faction.FRONTIER).ordinal() >= ReputationTier.TRUSTED.ordinal()) {
				dropLoot(level, THANKS_LOOT, drop);
			}

			Identity.contextual(player, ContextAct.HELPED_OTHER, speaker);
		}

		return true;
	}

	/** Rolls a loot table at {@code pos} and drops the items there. */
	static void dropLoot(ServerLevel level, ResourceKey<LootTable> table, Vec3 pos) {
		// API-CHECK: MinecraftServer#reloadableRegistries().getLootTable(ResourceKey), LootParams.Builder(level).withParameter(..).create(LootContextParamSets.CHEST) and LootTable#getRandomItems(LootParams, long).
		LootTable loot = level.getServer().reloadableRegistries().getLootTable(table);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, pos).create(LootContextParamSets.CHEST);

		for (ItemStack stack : loot.getRandomItems(params, level.getRandom().nextLong())) {
			ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, stack);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private Settlers() {
	}
}
