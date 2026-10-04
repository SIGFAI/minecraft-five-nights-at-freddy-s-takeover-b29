package sigf.mod;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A haunted pizzeria robot: a tough, slow-to-kill walker that never burns in daylight. */
public class Animatronic extends Zombie {
	public static final int FREDDY = 0, BONNIE = 1, CHICA = 2, FOXY = 3;
	public static final String[] NAMES = {"Freddy", "Bonnie", "Chica", "Foxy"};
	private static final double[] HEALTH = {40, 30, 30, 26};
	private static final double[] SPEED = {0.22, 0.26, 0.24, 0.30};
	private static final double[] DAMAGE = {4, 3, 3, 3};
	private static final double[] SCALE = {1.3, 1.25, 1.15, 1.2};

	public final int kind;
	private int stun;
	private boolean wasWatched = true;

	public Animatronic(EntityType<? extends Zombie> type, Level level, int kind) {
		super(type, level);
		this.kind = kind;
	}

	public static AttributeSupplier.Builder attributes(int kind) {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH[kind])
			.add(Attributes.MOVEMENT_SPEED, SPEED[kind])
			.add(Attributes.ATTACK_DAMAGE, DAMAGE[kind])
			.add(Attributes.SCALE, SCALE[kind])
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	@Override
	protected boolean isSunSensitive() { return false; }

	@Override
	protected net.minecraft.world.entity.EntityType<? extends Zombie> convertsToWhenDrowning() { return getType(); }

	@Override
	public boolean isBaby() { return false; }

	/** The robots go after any real player, even one the game protects from damage. */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (target instanceof Player p) return !p.isCreative() && !p.isSpectator();
		return super.canAttack(target);
	}

	public boolean isStunned() { return stun > 0; }

	/** Freezes the robot in place for a while: it jitters and sparks. */
	public void stun(int ticks) {
		stun = Math.max(stun, ticks);
		setNoAi(true);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl) {
			if (stun > 0) {
				setDeltaMovement(0, getDeltaMovement().y, 0);
				if (tickCount % 3 == 0) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.7, getZ(), 3, 0.3, 0.4, 0.3, 0.1);
				if (--stun == 0) setNoAi(false);
			} else {
				if (tickCount % 10 == 0 && getTarget() == null) hunt(sl);
				if (kind == FOXY && tickCount % 4 == 0) foxyRush(sl);
			}
		}
	}

	/** Robots hunt any player in range, even one the game treats as untouchable. */
	private void hunt(ServerLevel sl) {
		Player best = null;
		double bd = 45 * 45;
		for (ServerPlayer p : sl.players()) {
			if (p.isSpectator() || p.isCreative()) continue;
			double d = p.distanceToSqr(this);
			if (d < bd) { bd = d; best = p; }
		}
		if (best != null) setTarget(best);
	}

	/** Foxy only sprints while nobody is looking at him; stare at him and he freezes. */
	private void foxyRush(ServerLevel sl) {
		boolean watched = false;
		for (ServerPlayer p : sl.players()) {
			Vec3 to = position().add(0, getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
			if (to.length() > 40 || to.length() < 0.5) continue;
			if (p.getLookAngle().dot(to.normalize()) > 0.82 && p.hasLineOfSight(this)) { watched = true; break; }
		}
		if (watched != wasWatched) {
			wasWatched = watched;
			for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) < 15 * 15)
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
					net.minecraft.network.chat.Component.literal(watched ? "Foxy freezes while you watch him!" : "Foxy is RUNNING - look at him!")));
		}
		var attr = getAttribute(Attributes.MOVEMENT_SPEED);
		if (attr != null) attr.setBaseValue(watched ? 0.04 : 0.46);
		if (!watched && tickCount % 8 == 0) sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 2, 0.2, 0.05, 0.2, 0.02);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hit = super.hurtServer(level, source, damage);
		if (hit) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6, getZ(), 14, 0.35, 0.5, 0.35, 0.25);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.4, 0.3, 0.2);
		}
		return hit;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.4, 0.5, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 40, 0.5, 0.8, 0.5, 0.4);
		spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(4)));
		if (random.nextInt(2) == 0) spawnAtLocation(level, new ItemStack(Items.REDSTONE, 1 + random.nextInt(3)));
		if (random.nextInt(3) != 0) spawnAtLocation(level, new ItemStack(SigfMod.PIZZA_SLICE, 1 + random.nextInt(2)));
		if (random.nextInt(8) == 0) spawnAtLocation(level, new ItemStack(SigfMod.BEAR_PLUSH));
	}

	@Override
	protected SoundEvent getAmbientSound() { return kind == FREDDY ? SigfMod.MUSIC_BOX : null; }

	@Override
	protected SoundEvent getHurtSound(DamageSource source) { return SigfMod.ROBOT_HIT; }

	@Override
	protected SoundEvent getDeathSound() { return SigfMod.ROBOT_DEATH; }

	@Override
	protected SoundEvent getStepSound() { return SigfMod.ROBOT_STEP; }

	/** Everything in front of the player's flashlight, within range, gets stunned. Returns how many were hit. */
	public static int beam(ServerPlayer p, double range, double cosAngle, int stunTicks) {
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		int n = 0;
		List<Animatronic> list = p.level().getEntitiesOfClass(Animatronic.class, p.getBoundingBox().inflate(range));
		for (Animatronic a : list) {
			Vec3 to = a.position().add(0, a.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > range || d < 0.1) continue;
			if (look.dot(to.scale(1 / d)) < cosAngle || !p.hasLineOfSight(a)) continue;
			a.stun(stunTicks);
			n++;
		}
		return n;
	}

	/** Honking makes every robot around dance on the spot for a moment. */
	public static int dance(Player p, double range, int ticks) {
		int n = 0;
		for (Animatronic a : p.level().getEntitiesOfClass(Animatronic.class, p.getBoundingBox().inflate(range))) {
			a.stun(ticks);
			a.setDeltaMovement(0, 0.42, 0);
			if (a.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.NOTE, a.getX(), a.getY() + a.getBbHeight() + 0.4, a.getZ(), 5, 0.4, 0.3, 0.4, 1.0);
			n++;
		}
		return n;
	}
}
