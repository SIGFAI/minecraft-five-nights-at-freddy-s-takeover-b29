package sigf.mod;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** The night shift: a clock from 12 AM to 6 AM, robot waves every hour, and the scripted demo. */
public final class Night {
	static final long START = 13000, SPAN = 10500; // world time of 12 AM, and 6 hours
	static double elapsed, hourSec;
	static boolean running, fighting, camOn, kite;
	static Vec3 camSpot;
	static int night = 1, lastHour = -1;

	private Night() {}

	static void init() {
		if (Sigf.isDemo()) demo();
		else Sigf.after(6, () -> start(40));
		Sigf.every(1, Night::tick);
		Sigf.every(0.55, Night::botFight);
		Sigf.every(0.05, Night::camera);
	}

	/** The demo bot's eyes: glide toward the closest robot (or a fixed spot). */
	private static void camera() {
		ServerPlayer h = Sigf.host();
		if (!camOn || h == null) return;
		Vec3 target = camSpot;
		List<Animatronic> all = h.level().getEntitiesOfClass(Animatronic.class, h.getBoundingBox().inflate(30));
		Animatronic best = null, closest = null;
		double bd = 1e9, cd = 1e9;
		for (Animatronic a : all) {
			double d = a.distanceToSqr(h);
			if (d < cd && !a.isStunned()) { cd = d; closest = a; }
			d += a.isStunned() ? 400 : 0;
			if (d < bd) { bd = d; best = a; }
		}
		if (best != null) target = best.position().add(0, best.getBbHeight() * 0.65, 0);
		if (target == null) return;
		Vec3 d = target.subtract(h.getEyePosition());
		float yaw = (float) (Math.atan2(d.z, d.x) * 57.29578) - 90f;
		float pitch = (float) -(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 57.29578);
		float dy = net.minecraft.util.Mth.wrapDegrees(yaw - h.getYRot());
		float dp = pitch - h.getXRot();
		float ny = h.getYRot() + Math.max(-9f, Math.min(9f, dy));
		float np = h.getXRot() + Math.max(-5f, Math.min(5f, dp));
		double x = h.getX(), y = h.getY(), z = h.getZ();
		// Back away from the closest robot so it stays in frame, a little slower than it walks.
		if (kite && closest != null && cd < 2.6 * 2.6) {
			double dx = x - closest.getX(), dz = z - closest.getZ(), len = Math.sqrt(dx * dx + dz * dz);
			if (len > 0.01) {
				double nx = x + dx / len * 0.16, nz = z + dz / len * 0.16;
				var top = Sigf.ground(new Vec3(nx, y, nz), 0.0);
				if (Math.abs(top.y - y) < 1.1) { x = nx; z = nz; y = top.y; }
			}
		}
		h.connection.teleport(x, y, z, ny, np);
	}

	static void start(double secPerHour) {
		hourSec = secPerHour;
		elapsed = 0;
		lastHour = -1;
		running = true;
		Pizzeria.wake();
		Sigf.sound(SigfMod.MUSIC_BOX, Pizzeria.at(0, 2, 17), 1.5f, 1f);
	}

	static String label(int h) { return h == 0 ? "12 AM" : h + " AM"; }

	private static void tick() {
		if (!running) return;
		elapsed += 1;
		double hours = elapsed / hourSec;
		Sigf.command("time set " + (START + (long) (Math.min(hours, 6.0) / 6.0 * SPAN)));
		int h = (int) hours;
		if (h != lastHour) {
			lastHour = h;
			if (h >= 6) { sixAm(); return; }
			for (ServerPlayer p : Sigf.players()) p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Night " + night + "  -  " + label(h))));
			if (!Sigf.isDemo() && h > 0) {
				for (int i = 0; i < 1 + h / 2; i++) SigfMod.spawnRandom(Sigf.ground(22));
			}
		}
	}

	static void sixAm() {
		running = false;
		fighting = false;
		camOn = false;
		kite = false;
		Sigf.command("time set 23800");
		Sigf.sound(SigfMod.SIX_AM, Sigf.host() == null ? Pizzeria.at(0, 1, 0) : Sigf.host().position(), 1.5f, 1f);
		Sigf.title("6 AM", "You survived night " + night + "!", 5);
		for (Entity e : Sigf.level().getEntities(EntityTypes.ZOMBIE, x -> true)) e.discard();
		for (Animatronic a : Sigf.level().getEntitiesOfClass(Animatronic.class, Sigf.host().getBoundingBox().inflate(80))) {
			a.stun(400);
			Sigf.particles(ParticleTypes.HAPPY_VILLAGER, a.position().add(0, 2, 0), 12, 0.6);
		}
		if (!Sigf.isDemo()) {
			night++;
			Sigf.after(25, () -> { Sigf.command("time set 13000"); Pizzeria.wake(); start(Math.max(25, 40 - 3 * night)); });
		}
	}

	/** The demo bot punches whatever stands next to it. */
	private static void botFight() {
		if (!fighting || Sigf.host() == null) return;
		ServerPlayer h = Sigf.host();
		List<Animatronic> near = h.level().getEntitiesOfClass(Animatronic.class, h.getBoundingBox().inflate(3.2));
		if (near.isEmpty()) return;
		Animatronic a = near.getFirst();
		for (Animatronic b : near) if (b.distanceToSqr(h) < a.distanceToSqr(h)) a = b;
		h.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		h.attack(a);
	}

	private static void hold(int slot) {
		ServerPlayer h = Sigf.host();
		h.getInventory().setSelectedSlot(slot);
		h.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(slot));
	}

	/** n robots appear in a ring around the player, r blocks away, and come for them. */
	static void wave(int n, double r) {
		Vec3 c = Sigf.host().position();
		r = Math.min(r, 11);
		for (int i = 0; i < n; i++) {
			double a = Math.toRadians(25 + 130.0 * (i + 0.5) / n);
			Vec3 at = Sigf.ground(new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), 0.5);
			SigfMod.spawnRandom(at);
		}
	}

	private static Vec3 ahead(double d) {
		ServerPlayer h = Sigf.host();
		Vec3 look = h.getLookAngle();
		return h.position().add(look.x * d, 0, look.z * d);
	}

	private static void demo() {
		Sigf.after(5, () -> {
			Sigf.command("give @a sigf:flashlight");
			Sigf.command("give @a sigf:bear_plush");
			Sigf.command("give @a sigf:pizza_slice 3");
			Sigf.command("give @a iron_sword");
			Sigf.command("time set 13000");
			Sigf.command("effect give @a minecraft:night_vision infinite 0 true");
			Sigf.teleport(Sigf.host(), Pizzeria.at(0, 0.1, 5), null);
			Sigf.lookAt(Sigf.host(), Pizzeria.at(0, 2.5, 17));
		});
		Sigf.demo(1, () -> {
			Sigf.title("FIVE NIGHTS AT FREDDY'S", "Night 1  -  12 AM", 4);
			start(10.5);
		});
		Sigf.demo(4, () -> {
			Sigf.sound(SigfMod.POWER_OUT, Sigf.host().position(), 1.2f, 1f);
			camSpot = Pizzeria.at(0, 2.5, 17);
			camOn = true;
			kite = true;
		});
		Sigf.demo(10.5, () -> SigfMod.FlashlightItem.fire(Sigf.host()));
		Sigf.demo(12, () -> { hold(3); fighting = true; });
		Sigf.demo(24, () -> wave(3, 15));
		Sigf.demo(32, () -> {
			Vec3 p = ahead(6);
			Sigf.spawn(EntityTypes.IRON_GOLEM, p);
			wave(3, 13);
		});
		Sigf.demo(42, () -> {
			Sigf.spawn(EntityTypes.TNT, ahead(5));
			wave(2, 9);
		});
		Sigf.demo(46, () -> wave(4, 10));
		Sigf.demo(47.5, () -> { hold(0); SigfMod.FlashlightItem.fire(Sigf.host()); });
		Sigf.demo(50, () -> hold(3));
		Sigf.demo(52, () -> { hold(1); SigfMod.PlushItem.honk(Sigf.host()); });
		Sigf.demo(54.5, () -> hold(3));
		Sigf.demo(57, () -> {
			wave(3, 12);
			Sigf.command("effect give @a minecraft:instant_health 1 1");
		});
	}
}
