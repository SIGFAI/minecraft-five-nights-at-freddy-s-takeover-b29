package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;
import sigf.kit.SigfKit;

/** Freddy Fazbear's Pizza: a checkered hall with a show stage, party tables and Pirate Cove, built south of the spawn point. */
public final class Pizzeria {
	/** Hall: x -15..15 around the spawn, z +3 (open front) .. +20 (back wall). */
	static final int X0 = -15, X1 = 15, Z0 = 3, Z1 = 20, H = 15;
	public static final List<Animatronic> onStage = new ArrayList<>();
	static int fy;
	static BlockPos origin;
	static boolean built;

	private Pizzeria() {}

	static void init() {
		Sigf.after(1.5, Pizzeria::build);
	}

	/** World position of a hall-relative spot (dx from the spawn x, dz from the spawn z, dy above the floor). */
	public static Vec3 at(double dx, double dy, double dz) {
		return new Vec3(origin.getX() + 0.5 + dx, fy + dy, origin.getZ() + 0.5 + dz);
	}

	private static void set(ServerLevel l, int dx, int dy, int dz, Block b) {
		l.setBlock(origin.offset(dx, dy - origin.getY() + fy, dz), b.defaultBlockState(), 2);
	}

	private static final String[][] FONT = {
		{"111", "100", "110", "100", "100"}, // F
		{"010", "101", "111", "101", "101"}, // A
		{"111", "001", "010", "100", "111"}, // Z
		{"110", "101", "110", "101", "110"}, // B
		{"111", "100", "110", "100", "111"}, // E
		{"110", "101", "110", "101", "101"}, // R
	};

	static synchronized void build() {
		if (built) return;
		built = true;
		ServerLevel l = Sigf.level();
		origin = BlockPos.containing(SigfKit.center());
		fy = origin.getY();

		// Plaza in front of the hall: flat polished stone instead of bumpy grass, so the fight is easy to see.
		for (int x = -22; x <= 22; x++) for (int z = -26; z < Z0; z++) {
			for (int y = 0; y <= H; y++) set(l, x, y, z, Blocks.AIR);
			set(l, x, -1, z, (Math.abs(x) <= 1 || z == Z0 - 1) ? Blocks.CONCRETE.red() : ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
			for (int y = -2; y >= -6; y--) if (l.getBlockState(origin.offset(x, y, z)).isAir()) set(l, x, y, z, Blocks.DIRT);
		}
		for (int x = -22; x <= 22; x += 11) for (int z = -26; z < Z0; z += 7) {
			set(l, x, 0, z, Blocks.COBBLESTONE_WALL);
			set(l, x, 1, z, Blocks.COBBLESTONE_WALL);
			set(l, x, 2, z, Blocks.LANTERN);
		}

		// Clear the volume and lay the foundation.
		for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
			for (int y = 0; y <= H; y++) set(l, x, y, z, Blocks.AIR);
			set(l, x, -1, z, ((x + z) & 1) == 0 ? Blocks.CONCRETE.black() : Blocks.CONCRETE.white());
			set(l, x, -2, z, Blocks.STONE);
			set(l, x, -3, z, Blocks.STONE);
		}
		// Walls: purple with a white stripe and a black top.
		for (int y = 0; y <= H; y++) {
			Block wall = y == 3 ? Blocks.CONCRETE.white() : (y >= H - 1 ? Blocks.CONCRETE.black() : Blocks.CONCRETE.purple());
			for (int z = Z0; z <= Z1; z++) { set(l, X0, y, z, wall); set(l, X1, y, z, wall); }
			for (int x = X0; x <= X1; x++) set(l, x, y, Z1, wall);
		}
		// Wall lamps and party stars.
		for (int z = Z0 + 3; z < Z1; z += 5) for (int dx : new int[] {X0 + 1, X1 - 1}) {
			set(l, dx, 6, z, Blocks.SEA_LANTERN);
			set(l, dx, 8, z, Blocks.CONCRETE.yellow());
			set(l, dx, 9, z, Blocks.CONCRETE.yellow());
		}
		// Sign "FAZBEAR" in glowing letters on the back wall.
		String word = "FAZBEAR";
		int[] order = {0, 1, 2, 3, 4, 1, 5};
		int sx = 13; // text runs toward -x: looking south, east is on the viewer's left
		for (int i = 0; i < word.length(); i++) {
			String[] glyph = FONT[order[i]];
			for (int gy = 0; gy < 5; gy++) for (int gx = 0; gx < 3; gx++) {
				if (glyph[gy].charAt(gx) == '1') set(l, sx - gx - i * 4, 12 - gy, Z1, Blocks.GLOWSTONE);
			}
		}
		// Show stage with red curtains and a starry backdrop.
		for (int x = -8; x <= 8; x++) for (int z = 14; z <= 19; z++) {
			set(l, x, 0, z, Blocks.DARK_OAK_PLANKS);
			if (x == -8 || x == 8 || z == 14) set(l, x, 0, z, Blocks.GOLD_BLOCK);
		}
		for (int x = -8; x <= 8; x++) for (int y = 1; y <= 10; y++) {
			set(l, x, y, 19, y > 8 ? Blocks.WOOL.red() : Blocks.WOOL.blue());
		}
		for (int x = -7; x <= 7; x += 3) for (int y = 3; y <= 7; y += 2) set(l, x, y, 19, Blocks.CONCRETE.yellow());
		for (int y = 1; y <= 10; y++) for (int z = 15; z <= 19; z++) { set(l, -8, y, z, Blocks.WOOL.red()); set(l, 8, y, z, Blocks.WOOL.red()); }
		for (int x = -8; x <= 8; x++) set(l, x, 10, 14, Blocks.WOOL.red());
		for (int x = -8; x <= 8; x += 4) set(l, x, 9, 14, Blocks.SHROOMLIGHT);
		// Party tables with cakes.
		int[][] tables = {{-12, 6}, {-5, 8}, {5, 8}, {12, 6}, {-10, 12}, {10, 12}};
		for (int[] t : tables) {
			for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) set(l, t[0] + x, 0, t[1] + z, Blocks.SPRUCE_PLANKS);
			set(l, t[0], 1, t[1], Blocks.CAKE);
			set(l, t[0] + 1, 1, t[1] + 1, Blocks.DYED_CANDLE.red());
			set(l, t[0] - 1, 1, t[1] - 1, Blocks.DYED_CANDLE.yellow());
		}
		// Pirate Cove: a purple-curtained corner with a fence.
		for (int x = 10; x <= 14; x++) for (int z = 15; z <= 20; z++) set(l, x, 0, z, Blocks.DARK_OAK_PLANKS);
		for (int y = 1; y <= 7; y++) for (int x = 10; x <= 14; x++) set(l, x, y, 20, Blocks.WOOL.purple());
		for (int y = 1; y <= 7; y++) for (int z = 15; z <= 20; z++) set(l, 10, y, z, Blocks.WOOL.purple());
		set(l, 10, 1, 14, Blocks.DARK_OAK_FENCE);
		set(l, 14, 1, 14, Blocks.DARK_OAK_FENCE);
		set(l, 10, 2, 14, Blocks.LANTERN);
		set(l, 14, 2, 14, Blocks.LANTERN);

		// The robots wait on stage, switched off.
		spawnStage();
		Sigf.log("pizzeria built at " + origin + " floor " + fy);
	}

	static void spawnStage() {
		onStage.clear();
		int[] kinds = {Animatronic.FREDDY, Animatronic.BONNIE, Animatronic.CHICA, Animatronic.FOXY};
		double[][] spots = {{0, 17.5}, {-4.5, 17.5}, {4.5, 17.5}, {12, 18}};
		for (int i = 0; i < 4; i++) {
			Vec3 p = at(spots[i][0], i == 3 ? 1 : 1, spots[i][1]);
			if (i == 3) p = at(spots[i][0], 1, spots[i][1]);
			Animatronic a = SigfMod.spawnKind(kinds[i], p);
			if (a == null) continue;
			a.setYRot(180f);
			a.setYHeadRot(180f);
			a.setNoAi(true);
			a.setPersistenceRequired();
			onStage.add(a);
		}
	}

	/** Switches the stage robots on. */
	public static void wake() {
		for (Animatronic a : onStage) if (!a.isRemoved()) a.setNoAi(false);
	}
}
