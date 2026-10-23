package com.oldrepublic.enemy.droid;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifier;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The security droid's resistances and weaknesses as the shared hooks see them (spec MILESTONE_4.md
 * 10.2):
 * <ul>
 * <li>{@link ForceReactions.Handler}: Push carries it {@code pushMult} as far as a normal target
 * (its knock-back resistance included); Lift is refused (too heavy); a stagger or shock interrupts
 * the spin. The seeker droid refuses Lift too.</li>
 * <li>{@link DamageModifier}: blaster bolts x{@code blasterMult}, a bolt a saber parried back
 * x{@code parriedBoltMult}, Force Lightning x{@code shockMult}. The electrostaff and shock edges
 * apply their own droid multiplier (W4); stun grenades stagger droids themselves (W5).</li>
 * <li>{@link Attitudes.Rule}: an Imperial guard droid (the Proving, the greater tombs' digs) is
 * not hostile to Sith; every other security droid keeps the monster rule.</li>
 * </ul>
 * Choke (it does not breathe), poison, wither, fire and the hunter's flamer are refused on the droid
 * itself.
 */
public final class DroidReactions implements ForceReactions.Handler, DamageModifier, Attitudes.Rule {
	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (!(target instanceof SecurityDroid)) {
			return 1f;
		}

		PushBalance push = ForcePush.balance();
		return pushFactor(target, DroidBalance.get().pushMult, push.resistantKnockbackMult, push.knockbackResistanceWeight);
	}

	/**
	 * The handler's share of the Push multiplier, so the knock-back the push code applies (already
	 * reduced for the resistant tag and the knock-back resistance attribute) comes out at
	 * {@code wanted} overall. Never above 1.
	 */
	static float pushFactor(LivingEntity target, float wanted, float resistantMult, float resistanceWeight) {
		float tag = ForcePowers.isResistant(target) ? Math.max(0.01f, resistantMult) : 1f;
		float attribute = Math.max(0.01f, ForcePowers.knockbackFactor(target, resistanceWeight));
		return Math.min(1f, wanted / (tag * attribute));
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		return target instanceof SecurityDroid || target instanceof SeekerDroid ? 0f : requested;
	}

	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof SecurityDroid droid) {
			droid.interrupt(cause);
		}
	}

	@Override
	public float modify(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof SecurityDroid)) {
			return amount;
		}

		DroidBalance b = DroidBalance.get();

		if (source.is(ForceDamage.LIGHTNING)) {
			return amount * b.shockMult;
		}

		if (source.is(EnemyEntities.BLASTER_DAMAGE)) {
			boolean returned = source.getDirectEntity() instanceof BlasterBolt bolt && bolt.parried();
			return amount * (returned ? b.parriedBoltMult : b.blasterMult);
		}

		return amount;
	}

	@Override
	public @Nullable Attitude toward(LivingEntity self, Player p) {
		if (self instanceof SecurityDroid droid && droid.isImperialGuard() && Identity.path(p) == Path.SITH) {
			return Attitude.NEUTRAL;
		}

		return null;
	}
}
