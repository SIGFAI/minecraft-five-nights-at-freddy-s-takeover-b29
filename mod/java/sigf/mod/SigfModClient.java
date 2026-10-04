package sigf.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import sigf.kit.Sigf;

/** Client side: the four robot models, the night clock on screen and the jumpscare. */
public final class SigfModClient implements ClientModInitializer {
	static final Object[][][] PARTS = {AnimatronicParts.FREDDY, AnimatronicParts.BONNIE, AnimatronicParts.CHICA, AnimatronicParts.FOXY};
	static final Identifier JUMPSCARE = Sigf.id("textures/gui/jumpscare.png");
	static long scareUntil, scareCooldown;

	@Override
	public void onInitializeClient() {
		for (int k = 0; k < 4; k++) {
			final int kind = k;
			String name = Animatronic.NAMES[k].toLowerCase();
			ModelLayerLocation layer = new ModelLayerLocation(Sigf.id(name), "main");
			Identifier tex = Sigf.id("textures/entity/" + name + ".png");
			ModelLayerRegistry.registerModelLayer(layer, () -> definition(PARTS[kind]));
			Identifier eyes = Sigf.id("textures/entity/" + name + "_eyes.png");
			EntityRendererRegistry.register(SigfMod.TYPES[k], ctx -> new RobotRenderer(ctx, layer, tex, eyes));
		}
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Sigf.id("night_clock"), SigfModClient::drawClock);
		HudElementRegistry.addLast(Sigf.id("jumpscare"), SigfModClient::drawScare);
		ClientTickEvents.END_CLIENT_TICK.register(SigfModClient::tick);
	}

	static LayerDefinition definition(Object[][] parts) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		int i = 0;
		for (Object[] p : parts) {
			PartDefinition parent = root.getChild((String) p[0]);
			CubeListBuilder cube = CubeListBuilder.create().texOffs((Integer) p[1], (Integer) p[2])
				.addBox((Float) p[3], (Float) p[4], (Float) p[5], (Float) p[6], (Float) p[7], (Float) p[8]);
			parent.addOrReplaceChild("extra" + i++, cube, PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	static final class RobotRenderer extends AbstractZombieRenderer<Animatronic, ZombieRenderState, ZombieModel<ZombieRenderState>> {
		private final Identifier texture;

		RobotRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, Identifier texture, Identifier eyes) {
			super(ctx, new ZombieModel<ZombieRenderState>(ctx.bakeLayer(layer)), new ZombieModel<ZombieRenderState>(ctx.bakeLayer(layer)),
				ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, ctx.getModelSet(), ZombieModel::new),
				ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, ctx.getModelSet(), ZombieModel::new));
			this.texture = texture;
			RenderType eyeType = RenderTypes.eyes(eyes);
			addLayer(new EyesLayer<ZombieRenderState, ZombieModel<ZombieRenderState>>(this) {
				@Override
				public RenderType renderType() { return eyeType; }
			});
		}

		@Override
		public ZombieRenderState createRenderState() { return new ZombieRenderState(); }

		@Override
		public Identifier getTextureLocation(ZombieRenderState state) { return texture; }
	}

	private static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null || mc.level == null || p.isSpectator()) return;
		long now = mc.level.getGameTime();
		if (now < scareCooldown) return;
		AABB box = p.getBoundingBox().inflate(2.3, 0.8, 2.3);
		for (Animatronic a : mc.level.getEntitiesOfClass(Animatronic.class, box)) {
			if (a.isNoAi() || !a.isAlive()) continue;
			scareUntil = now + 26;
			scareCooldown = now + 20 * 25;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SigfMod.JUMPSCARE, 1.0f, 1.4f));
			return;
		}
	}

	private static void drawScare(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long left = scareUntil - mc.level.getGameTime();
		if (left <= 0) return;
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, 0xFF000000);
		double t = 26 - left;
		int size = (int) (h * (1.0 + 0.35 * Math.min(1.0, t / 6.0)));
		int sx = (int) (Math.sin(t * 2.7) * 14), sy = (int) (Math.cos(t * 3.9) * 12);
		int x = (w - size) / 2 + sx, y = (h - size) / 2 + sy;
		g.blit(JUMPSCARE, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
		if (t < 3) g.fill(0, 0, w, h, 0x66FF0000);
	}

	private static void drawClock(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		long t = mc.level.getOverworldClockTime() % 24000;
		if (t < 12900 || t > 23900) return;
		int hour = (int) Math.min(6, (t - Night.START) * 6 / Night.SPAN);
		String clock = hour == 0 ? "12 AM" : hour + " AM";
		int power = Math.max(8, 100 - hour * 15);
		int w = g.guiWidth();
		var font = mc.font;
		g.pose().pushMatrix();
		g.pose().translate(w - 8, 6);
		g.pose().scale(2.0f, 2.0f);
		int cw = font.width(clock);
		g.text(font, clock, -cw, 0, hour >= 6 ? 0xFF55FF55 : 0xFFFFFFFF, true);
		g.pose().popMatrix();
		String pw = "Power left: " + power + "%";
		g.text(font, pw, w - 8 - font.width(pw), 28, power < 40 ? 0xFFFF5555 : 0xFFFFFFFF, true);
	}
}
