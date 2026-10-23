package com.oldrepublic.saber;

import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.identity.ContextualActs;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The hilt materials' traits (MILESTONE_7.md 3.3), counted per part of that material: a full starfall
 * hilt has three times one part's trait. A saberstaff counts its emitter (once) and its grip. Every trait
 * acts while its saber is lit in the main hand; a dual wielder's off-hand saber counts for its own hits.
 * <ul>
 * <li>Starfall iron: {@code starfallDroidDamagePct} more saber damage to droids and constructs, in the
 * saber damage pipeline ({@link SaberCombat}, {@link #strikeMultiplier});</li>
 * <li>Aurelite: Force powers cost {@code aureliteForceCostPct} less, a {@link ForceModifier} (whose clamp
 * holds) that also carries the hilt's own Force cost ({@link SaberStats#forceCostPct()}: the Choir
 * emitter, the Domed pommel, the Choir set's two parts);</li>
 * <li>Duskglass: each saber hit restores {@code duskglassEnergyOnHit} Force energy;</li>
 * <li>Sunstone: {@code sunstoneIgniteChancePct} chance a saber hit sets the foe alight for
 * {@code sunstoneIgniteTicks};</li>
 * <li>Clan iron: guard drain {@code clanIronGuardDrainPct} lower, in {@link SaberGuard} with the hilt's own
 * guard drain ({@link SaberStats#guardDrainPct()}: the Warden emitter);</li>
 * <li>Hymnsteel: Force power damage {@code hymnsteelForceDamagePct} higher, a {@link DamageModifiers}
 * modifier on {@code oldrepublic:force_harm} damage (whose clamp holds).</li>
 * </ul>
 * Steel and durasteel have no trait. The tooltip lines of 3.4 are built here too. Server side, except
 * the tooltip lines.
 */
public final class HiltTraits {
	/** The numbers tooltips show: a client never reads the balance file (as {@code LensItem}). */
	private static final SaberBalance SHOWN = new SaberBalance();
	/** How far a material's tint is lightened toward white for its tooltip line, so the dark metals stay readable. */
	private static final float TEXT_LIGHTEN = 0.4f;
	/** Lang key of a part's name in a material: the material's prefix, then the part's name. */
	private static final String NAMED_KEY = "saber.oldrepublic.part.named";
	/** Entity ids ending in this are constructs (iron, snow and copper golems). */
	private static final String GOLEM_SUFFIX = "golem";

	/**
	 * How many of {@code config}'s parts are made of {@code material}: its emitter, grip and pommel; a
	 * saberstaff ({@code staff}) its emitter, once, and its grip.
	 */
	public static int count(SaberConfig config, boolean staff, HiltMaterial material) {
		HiltMaterials materials = config.materials();
		return (materials.emitter() == material ? 1 : 0) + (materials.grip() == material ? 1 : 0) + (!staff && materials.pommel() == material ? 1 : 0);
	}

	/** {@link #count(SaberConfig, boolean, HiltMaterial)} of a saber stack; 0 for anything that is not a lightsaber. */
	public static int count(ItemStack saber, HiltMaterial material) {
		if (!(saber.getItem() instanceof LightsaberItem)) {
			return 0;
		}

		return count(SaberComponents.config(saber), saber.getItem() instanceof SaberStaffItem, material);
	}

	/** Starfall iron: the extra saber damage to droids and constructs of {@code parts} parts, in percent. */
	public static float starfallPct(int parts, SaberBalance b) {
		return Math.max(0, parts) * b.starfallDroidDamagePct;
	}

	/** Duskglass: the Force energy {@code parts} parts restore per saber hit. */
	public static float energyOnHit(int parts, SaberBalance b) {
		return Math.max(0, parts) * b.duskglassEnergyOnHit;
	}

	/** Sunstone: the chance (0 to 1) that a saber hit of {@code parts} parts sets the foe alight. */
	public static float igniteChance(int parts, SaberBalance b) {
		return Math.min(1f, Math.max(0, parts) * b.sunstoneIgniteChancePct / 100f);
	}

	/** Hymnsteel: the multiplier on Force power damage of {@code parts} parts. */
	public static float forceDamageMultiplier(int parts, SaberBalance b) {
		return 1f + Math.max(0, parts) * b.hymnsteelForceDamagePct / 100f;
	}

	/**
	 * The lit hilt's multiplier on Force costs: its own {@code hiltCostPct} ({@link SaberStats#forceCostPct()})
	 * and aurelite's {@code aureliteForceCostPct} per part, added up (never below 0; {@code ForceModifiers}
	 * clamps the product of every source).
	 */
	public static float forceCostMultiplier(int aureliteParts, float hiltCostPct, SaberBalance b) {
		return Math.max(0f, 1f + (hiltCostPct - Math.max(0, aureliteParts) * b.aureliteForceCostPct) / 100f);
	}

	/**
	 * The guarding hilt's multiplier on guard drain: its own {@code hiltDrainPct} ({@link SaberStats#guardDrainPct()},
	 * the Warden emitter) and clan iron's {@code clanIronGuardDrainPct} per part, added up (never below 0).
	 */
	public static float guardDrainMultiplier(int clanIronParts, float hiltDrainPct, SaberBalance b) {
		return Math.max(0f, 1f + (hiltDrainPct - Math.max(0, clanIronParts) * b.clanIronGuardDrainPct) / 100f);
	}

	/** {@link #guardDrainMultiplier(int, float, SaberBalance)} of the saber that blocks, with its stats. */
	public static float guardDrainMultiplier(ItemStack saber, SaberStats stats, SaberBalance b) {
		return guardDrainMultiplier(count(saber, HiltMaterial.CLAN_IRON), stats.guardDrainPct(), b);
	}

	/**
	 * Whether starfall iron's trait counts against {@code entity}: a droid ({@code MeleeCombat.isDroid}) or a
	 * construct, a Ruin Sentinel or a golem by its entity id (the rule the Starfall Edge mod uses for vibroweapons).
	 */
	public static boolean isDroidOrConstruct(Entity entity) {
		return MeleeCombat.isDroid(entity) || Sentinels.isSentinel(entity) || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().endsWith(GOLEM_SUFFIX);
	}

	/** Starfall iron's multiplier for a strike of the lit {@code saber} (the hand that struck) on {@code target}. */
	public static float strikeMultiplier(ItemStack saber, LivingEntity target, SaberBalance b) {
		int parts = count(saber, HiltMaterial.STARFALL);

		if (parts == 0 || !SaberComponents.isUsable(saber) || !isDroidOrConstruct(target)) {
			return 1f;
		}

		return 1f + starfallPct(parts, b) / 100f;
	}

	/** The Force cost multiplier of the saber lit in {@code player}'s main hand (its own cost and aurelite), else 1. */
	public static float forceCostMultiplier(Player player, SaberBalance b) {
		ItemStack saber = player.getMainHandItem();

		if (!SaberComponents.isUsable(saber)) {
			return 1f;
		}

		return forceCostMultiplier(count(saber, HiltMaterial.AURELITE), SaberStats.of(saber, b).forceCostPct(), b);
	}

	/** Hymnsteel's multiplier on {@code caster}'s Force damage: the saber lit in a player's main hand, else 1. */
	public static float forceDamageMultiplier(LivingEntity caster, SaberBalance b) {
		if (!(caster instanceof Player player) || !SaberComponents.isUsable(player.getMainHandItem())) {
			return 1f;
		}

		return forceDamageMultiplier(count(player.getMainHandItem(), HiltMaterial.HYMNSTEEL), b);
	}

	/**
	 * A saber hit of the lit {@code saber} (the hand that struck) landed on {@code target}, or killed it:
	 * duskglass restores Force energy, sunstone may set a living foe alight. Called by {@link SaberCombat}
	 * once the hit went through, never in a spar.
	 */
	static void onHit(ServerPlayer attacker, ItemStack saber, LivingEntity target, boolean killed, SaberBalance b) {
		float energy = energyOnHit(count(saber, HiltMaterial.DUSKGLASS), b);

		if (energy > 0f) {
			restoreEnergy(attacker, energy);
		}

		float chance = igniteChance(count(saber, HiltMaterial.SUNSTONE), b);

		if (!killed && chance > 0f && target.isAlive() && attacker.getRandom().nextFloat() < chance) {
			target.igniteForTicks(b.sunstoneIgniteTicks);
		}
	}

	/** Gives {@code player} up to {@code amount} Force energy back, never above the maximum. Returns what was restored. */
	public static float restoreEnergy(ServerPlayer player, float amount) {
		ForceData data = Progression.get(player);
		float restored = Math.min(amount, Math.max(0f, data.maxEnergy - data.energy));

		if (restored > 0f) {
			data.energy += restored;
			Progression.commit(player, data);
		}

		return Math.max(0f, restored);
	}

	/**
	 * A loose part's name in its material (3.4): "Starfall Vanguard Emitter"; a steel part keeps {@code base},
	 * its plain name. A name that already carries a material (a legendary part's gold name copied while it
	 * was made of one) is not prefixed twice.
	 */
	public static Component partName(HiltMaterial material, Component base) {
		if (material == HiltMaterial.STEEL || base.getContents() instanceof TranslatableContents named && named.getKey().equals(NAMED_KEY)) {
			return base;
		}

		return Component.translatable(NAMED_KEY, Component.translatable(material.translationKey() + ".prefix"), base);
	}

	/** {@link #partName(HiltMaterial, Component)} with the part's own item name: the parts line of a saber's tooltip. */
	public static Component partName(HiltParts.Part part, HiltMaterial material) {
		return partName(material, Component.translatable("item.oldrepublic." + part.itemId()));
	}

	/**
	 * The material lines of a loose part in {@code slot} (3.4), under its stat line: the material's name,
	 * tier and what the tier adds in that slot, in the material's colour; then its trait, if it has one.
	 */
	public static void materialLines(HiltParts.Slot slot, HiltMaterial material, Consumer<Component> tooltip) {
		Component name = Component.translatable(material.translationKey());

		if (material.tier() == 0) {
			tooltip.accept(Component.translatable("saber.oldrepublic.material.line.plain", name, String.valueOf(material.tier())).withStyle(ChatFormatting.GRAY));
		} else {
			MutableComponent line = Component.translatable("saber.oldrepublic.material.line", name, String.valueOf(material.tier()), slotBonus(slot, material, SHOWN));
			tooltip.accept(line.withStyle(style -> style.withColor(textColor(material))));
		}

		Component trait = traitText(material, 1, SHOWN);

		if (trait != null) {
			tooltip.accept(Component.translatable("saber.oldrepublic.trait.line", trait).withStyle(ChatFormatting.BLUE));
		}
	}

	/** What {@code material}'s tier adds to a part in {@code slot} (3.2): damage, attack speed and guard, or parry ticks and guard. */
	public static Component slotBonus(HiltParts.Slot slot, HiltMaterial material, SaberBalance b) {
		int tier = material.tier();
		return switch (slot) {
			case EMITTER -> Component.translatable("saber.oldrepublic.material.bonus.emitter", number(SaberStats.tier(b.materialEmitterDamage, tier)));
			case GRIP -> Component.translatable("saber.oldrepublic.material.bonus.grip", number(SaberStats.tier(b.materialGripSpeedPct, tier)),
					number(SaberStats.tier(b.materialGripGuard, tier)));
			case POMMEL -> Component.translatable("saber.oldrepublic.material.bonus.pommel", number(SaberStats.tier(b.materialPommelParry, tier)),
					number(SaberStats.tier(b.materialPommelGuard, tier)));
		};
	}

	/** What {@code parts} parts of {@code material} add up to (3.3), or null for a material without a trait (steel, durasteel). */
	public static @Nullable Component traitText(HiltMaterial material, int parts, SaberBalance b) {
		return switch (material) {
			case STARFALL -> Component.translatable("saber.oldrepublic.trait.starfall", number(starfallPct(parts, b)));
			case AURELITE -> Component.translatable("saber.oldrepublic.trait.aurelite", number(parts * b.aureliteForceCostPct));
			case DUSKGLASS -> Component.translatable("saber.oldrepublic.trait.duskglass", number(energyOnHit(parts, b)));
			case SUNSTONE -> Component.translatable("saber.oldrepublic.trait.sunstone", number(igniteChance(parts, b) * 100f), number(b.sunstoneIgniteTicks / 20f));
			case CLAN_IRON -> Component.translatable("saber.oldrepublic.trait.clan_iron", number(parts * b.clanIronGuardDrainPct));
			case HYMNSTEEL -> Component.translatable("saber.oldrepublic.trait.hymnsteel", number(parts * b.hymnsteelForceDamagePct));
			default -> null;
		};
	}

	/** A saber's trait lines (3.4): one for each material with a trait, its parts on this hilt added up. */
	public static void saberTraitLines(SaberConfig config, boolean staff, Consumer<Component> tooltip) {
		for (HiltMaterial material : HiltMaterial.values()) {
			int parts = count(config, staff, material);
			Component text = parts > 0 ? traitText(material, parts, SHOWN) : null;

			if (text != null) {
				tooltip.accept(Component.translatable("saber.oldrepublic.trait.saber_line", Component.translatable(material.translationKey()), text)
						.withStyle(ChatFormatting.BLUE));
			}
		}
	}

	/** The colour of a material's tooltip line: its tint, lightened so the dark metals read on a tooltip. */
	public static int textColor(HiltMaterial material) {
		int tint = material.tint();
		int r = lighten((tint >> 16) & 0xFF);
		int g = lighten((tint >> 8) & 0xFF);
		int bl = lighten(tint & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	private static int lighten(int channel) {
		return Math.min(255, Math.round(channel + (255 - channel) * TEXT_LIGHTEN));
	}

	/** A number without trailing zeros, to two places: 4 is "4", 0.5 is "0.5", 0.15 is "0.15". */
	public static String number(float value) {
		if (value == Math.rint(value)) {
			return Integer.toString(Math.round(value));
		}

		String text = String.format(Locale.ROOT, "%.2f", value);

		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}

		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	/** Registers the aurelite (and hilt) Force cost modifier and hymnsteel's Force damage modifier (from {@link SaberM7Feature}). */
	static void init() {
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float costMultiplier(Player p, String abilityId) {
				return forceCostMultiplier(p, SaberToggle.balance());
			}
		});
		DamageModifiers.register((victim, source, amount) -> {
			if (source.is(ContextualActs.FORCE_HARM) && source.getEntity() instanceof LivingEntity caster) {
				return amount * forceDamageMultiplier(caster, SaberToggle.balance());
			}

			return amount;
		});
	}

	private HiltTraits() {
	}
}
