package com.oldrepublic.saber;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Item data components private to the saber feature. */
public final class SaberComponents {
	/**
	 * How a lightsaber was built. {@code crystal} is the full attunement of the kyber crystal inside
	 * (its owner is the saber's owner), so taking the saber apart returns the same crystal.
	 * {@code second} is a saberstaff's second crystal and {@code lens} a fitted lens item id (spec
	 * MILESTONE_4.md 5.6, 5.7); both are empty on an M1–M3 saber.
	 */
	public record SaberConfig(CrystalAttunement crystal, BladeColor color, Emitter emitter, Grip grip, Pommel pommel,
			Optional<CrystalAttunement> second, Optional<Identifier> lens) {
		/** Used for stacks without data (creative or /give): a generic training saber. */
		public static final SaberConfig DEFAULT = new SaberConfig(
				new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.BLUE.rgb()),
				BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED);

		public static final Codec<SaberConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
				CrystalAttunement.CODEC.fieldOf("crystal").forGetter(SaberConfig::crystal),
				BladeColor.CODEC.fieldOf("color").forGetter(SaberConfig::color),
				Emitter.CODEC.fieldOf("emitter").forGetter(SaberConfig::emitter),
				Grip.CODEC.fieldOf("grip").forGetter(SaberConfig::grip),
				Pommel.CODEC.fieldOf("pommel").forGetter(SaberConfig::pommel),
				CrystalAttunement.CODEC.optionalFieldOf("second").forGetter(SaberConfig::second),
				Identifier.CODEC.optionalFieldOf("lens").forGetter(SaberConfig::lens)
		).apply(i, SaberConfig::new));

		private static final StreamCodec<ByteBuf, Optional<CrystalAttunement>> SECOND_STREAM_CODEC = ByteBufCodecs.optional(CrystalAttunement.STREAM_CODEC);
		private static final StreamCodec<ByteBuf, Optional<Identifier>> LENS_STREAM_CODEC = ByteBufCodecs.optional(Identifier.STREAM_CODEC);

		public static final StreamCodec<ByteBuf, SaberConfig> STREAM_CODEC = StreamCodec.of(
				(buf, c) -> {
					CrystalAttunement.STREAM_CODEC.encode(buf, c.crystal());
					BladeColor.STREAM_CODEC.encode(buf, c.color());
					Emitter.STREAM_CODEC.encode(buf, c.emitter());
					Grip.STREAM_CODEC.encode(buf, c.grip());
					Pommel.STREAM_CODEC.encode(buf, c.pommel());
					SECOND_STREAM_CODEC.encode(buf, c.second());
					LENS_STREAM_CODEC.encode(buf, c.lens());
				},
				buf -> new SaberConfig(CrystalAttunement.STREAM_CODEC.decode(buf), BladeColor.STREAM_CODEC.decode(buf), Emitter.STREAM_CODEC.decode(buf),
						Grip.STREAM_CODEC.decode(buf), Pommel.STREAM_CODEC.decode(buf), SECOND_STREAM_CODEC.decode(buf), LENS_STREAM_CODEC.decode(buf)));

		/** A single-crystal saber with no lens (every M1–M3 build). */
		public SaberConfig(CrystalAttunement crystal, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
			this(crystal, color, emitter, grip, pommel, Optional.empty(), Optional.empty());
		}

		public SaberConfig withColor(BladeColor newColor) {
			return new SaberConfig(crystal, newColor, emitter, grip, pommel, second, lens);
		}

		public SaberConfig withSecond(Optional<CrystalAttunement> newSecond) {
			return new SaberConfig(crystal, color, emitter, grip, pommel, newSecond, lens);
		}

		public SaberConfig withLens(Optional<Identifier> newLens) {
			return new SaberConfig(crystal, color, emitter, grip, pommel, second, newLens);
		}

		/** Owner name shown in tooltips; empty for the generic training saber. */
		public String ownerName() {
			return crystal.ownerName();
		}
	}

	/**
	 * Ignition state. {@code toggledAt} is the server game time of the last ignite/retract; clients
	 * animate the blade from it.
	 */
	public record SaberState(boolean ignited, long toggledAt) {
		public static final SaberState RETRACTED = new SaberState(false, 0L);

		public static final Codec<SaberState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("ignited").forGetter(SaberState::ignited),
				Codec.LONG.fieldOf("toggled_at").forGetter(SaberState::toggledAt)
		).apply(i, SaberState::new));

		public static final StreamCodec<ByteBuf, SaberState> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, SaberState::ignited,
				ByteBufCodecs.VAR_LONG, SaberState::toggledAt,
				SaberState::new);
	}

	public static final DataComponentType<SaberConfig> SABER_CONFIG = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_config"),
			DataComponentType.<SaberConfig>builder().persistent(SaberConfig.CODEC).networkSynchronized(SaberConfig.STREAM_CODEC).build());

	/** Changes on every ignite/retract, so it must not trigger the held-item swap animation. */
	public static final DataComponentType<SaberState> SABER_STATE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_state"),
			DataComponentType.<SaberState>builder().persistent(SaberState.CODEC).networkSynchronized(SaberState.STREAM_CODEC).ignoreSwapAnimation().build());

	/**
	 * Present while the saber is in flight as a {@code thrown_saber} entity: the throw's id. The
	 * item never leaves the inventory; the hand renders empty and the stack cannot be used. Cleared
	 * on the catch, the thrower's death, logout, dimension change, a server stop, or by
	 * {@link LightsaberItem#inventoryTick} once no entity with this id exists.
	 */
	// API-CHECK: UUIDUtil.STREAM_CODEC is a StreamCodec<ByteBuf, UUID> (UUIDUtil.CODEC is used in identity).
	public static final DataComponentType<UUID> SABER_THROWN = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_thrown"),
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).ignoreSwapAnimation().build());

	/**
	 * Present (true) on a saber built around a singing crystal (spec 8.8): the client layers the
	 * {@code saber.hum.singing} loop over the normal hum. Disassembly gives the singing crystal back.
	 */
	public static final DataComponentType<Boolean> SABER_SINGING = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_singing"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/**
	 * A saberstaff's second blade (spec MILESTONE_4.md 5.6): {@code single} while the Weapon Action
	 * has retracted it, and the game time of that change, so clients animate the second blade from
	 * it. Absent means both blades (the default).
	 */
	public record StaffMode(boolean single, long toggledAt) {
		public static final StaffMode BOTH = new StaffMode(false, 0L);

		public static final Codec<StaffMode> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.fieldOf("single").forGetter(StaffMode::single),
				Codec.LONG.fieldOf("toggled_at").forGetter(StaffMode::toggledAt)
		).apply(i, StaffMode::new));

		public static final StreamCodec<ByteBuf, StaffMode> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, StaffMode::single,
				ByteBufCodecs.VAR_LONG, StaffMode::toggledAt,
				StaffMode::new);
	}

	/** Changes with the Weapon Action, so like the ignition it must not trigger the swap animation. */
	public static final DataComponentType<StaffMode> STAFF_MODE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_staff_mode"),
			DataComponentType.<StaffMode>builder().persistent(StaffMode.CODEC).networkSynchronized(StaffMode.STREAM_CODEC).ignoreSwapAnimation().build());

	/**
	 * The quality (0 Standard, 1 Fine, 2 Masterwork) of the lens fitted in {@link SaberConfig#lens()}
	 * (spec 5.7): the frozen config keeps only the lens id, so its quality rides beside it and goes
	 * back onto the lens when the saber is taken apart. Absent means Standard.
	 */
	public static final DataComponentType<Integer> LENS_QUALITY = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("saber_lens_quality"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** The staff's second-blade state, or {@link StaffMode#BOTH}. */
	public static StaffMode staffMode(ItemStack stack) {
		StaffMode mode = stack.get(STAFF_MODE);
		return mode != null ? mode : StaffMode.BOTH;
	}

	/** The stack's configuration, or {@link SaberConfig#DEFAULT}. */
	public static SaberConfig config(ItemStack stack) {
		SaberConfig config = stack.get(SABER_CONFIG);
		return config != null ? config : SaberConfig.DEFAULT;
	}

	public static SaberState state(ItemStack stack) {
		SaberState state = stack.get(SABER_STATE);
		return state != null ? state : SaberState.RETRACTED;
	}

	public static boolean isIgnited(ItemStack stack) {
		return stack.getItem() instanceof LightsaberItem && state(stack).ignited();
	}

	/** Whether this saber was built around a singing crystal (see {@link #SABER_SINGING}). */
	public static boolean isSinging(ItemStack stack) {
		return stack.getItem() instanceof LightsaberItem && Boolean.TRUE.equals(stack.get(SABER_SINGING));
	}

	/** Whether this saber is in flight right now (see {@link #SABER_THROWN}). */
	public static boolean isThrown(ItemStack stack) {
		return stack.has(SABER_THROWN);
	}

	/** An ignited saber that is in the hand (not thrown): the blade can be used. */
	public static boolean isUsable(ItemStack stack) {
		return isIgnited(stack) && !isThrown(stack);
	}

	static void init() {
	}

	private SaberComponents() {
	}
}
