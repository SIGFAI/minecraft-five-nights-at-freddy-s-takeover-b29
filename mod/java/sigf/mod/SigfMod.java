package sigf.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Five Nights at Freddy's Takeover: haunted pizzeria robots replace the zombies, a night shift lasts until 6 AM. */
public final class SigfMod implements ModInitializer {
	public static Item PIZZA_SLICE, FLASHLIGHT, BEAR_PLUSH;
	public static SoundEvent HONK, JUMPSCARE, MUSIC_BOX, POWER_OUT, ROBOT_DEATH, ROBOT_HIT, ROBOT_STEP, SIX_AM;
	@SuppressWarnings("unchecked")
	public static final EntityType<Animatronic>[] TYPES = new EntityType[4];

	@Override
	public void onInitialize() {
		HONK = Sigf.registerSound("honk");
		JUMPSCARE = Sigf.registerSound("jumpscare_sfx");
		MUSIC_BOX = Sigf.registerSound("music_box");
		POWER_OUT = Sigf.registerSound("power_out");
		ROBOT_DEATH = Sigf.registerSound("robot_death");
		ROBOT_HIT = Sigf.registerSound("robot_hit");
		ROBOT_STEP = Sigf.registerSound("robot_step");
		SIX_AM = Sigf.registerSound("six_am");

		PIZZA_SLICE = Sigf.item("pizza_slice", p -> new Item(p.stacksTo(32).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8f).build())));
		FLASHLIGHT = Sigf.item("flashlight", p -> new FlashlightItem(p.stacksTo(1)));
		BEAR_PLUSH = Sigf.item("bear_plush", p -> new PlushItem(p.stacksTo(1)));

		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(o -> { o.accept(FLASHLIGHT); o.accept(BEAR_PLUSH); o.accept(PIZZA_SLICE); });

		for (int k = 0; k < 4; k++) {
			final int kind = k;
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Sigf.id(Animatronic.NAMES[k].toLowerCase()));
			TYPES[k] = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<Animatronic>of((t, l) -> new Animatronic(t, l, kind), MobCategory.MONSTER)
					.sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(10).notInPeaceful().build(key));
			FabricDefaultAttributeRegistry.register(TYPES[k], Animatronic.attributes(k));
		}

		// Takeover: every zombie that appears becomes a pizzeria robot.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType() != EntityTypes.ZOMBIE || !(level instanceof ServerLevel sl)) return;
			Vec3 at = entity.position();
			sl.getServer().execute(() -> {
				if (entity.isRemoved()) return;
				entity.discard();
				spawnRandom(at);
			});
		});

		Pizzeria.init();
		Night.init();
	}

	public static Animatronic spawnKind(int kind, Vec3 pos) {
		Animatronic a = Sigf.spawn(TYPES[kind], pos);
		if (a != null) Sigf.particles(ParticleTypes.ELECTRIC_SPARK, pos.add(0, 1, 0), 18, 0.5);
		return a;
	}

	public static Animatronic spawnRandom(Vec3 pos) {
		return spawnKind(Sigf.level().getRandom().nextInt(4), pos);
	}

	/** The security guard's flashlight: right-click to freeze the robots in the beam. */
	public static final class FlashlightItem extends Item {
		public FlashlightItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				fire(sp);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 30);
			}
			return InteractionResult.SUCCESS;
		}

		public static void fire(ServerPlayer sp) {
			ServerLevel sl = (ServerLevel) sp.level();
			Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
			for (int i = 1; i <= 16; i += 1) {
				Vec3 p = eye.add(look.scale(i + 1.5)).add(0, -0.25, 0);
				sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, i * 0.04, i * 0.04, i * 0.04, 0.0);
			}
			Sigf.sound(SoundEvents.LEVER_CLICK, sp.position(), 1f, 1.6f);
			int n = Animatronic.beam(sp, 16, 0.88, 100);
			if (n > 0) Sigf.sound(POWER_OUT, sp.position(), 0.5f, 1.8f);
		}
	}

	/** A squeaky bear plush: honk, and every robot nearby dances on the spot. */
	public static final class PlushItem extends Item {
		public PlushItem(Properties p) { super(p); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel sl) {
				honk(player);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
			}
			return InteractionResult.SUCCESS;
		}

		public static void honk(Player player) {
			Sigf.sound(HONK, player.position(), 1.2f, 1f);
			Sigf.particles(ParticleTypes.NOTE, player.position().add(0, 2, 0), 12, 0.7);
			int n = Animatronic.dance(player, 16, 70);
			Sigf.log("plush honk danced " + n);
		}
	}
}
