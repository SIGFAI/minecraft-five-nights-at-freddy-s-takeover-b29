"""Paints the four animatronic skins (128x128, standard humanoid UVs in the top-left 64x64,
extra parts in slots on the right) and writes mod/java/sigf/mod/AnimatronicParts.java with the extra boxes.
Run from the work folder: python tools/skins.py"""
import random
from pathlib import Path
from PIL import Image

OUT = Path("mod/resources/assets/sigf/textures/entity")
OUT.mkdir(parents=True, exist_ok=True)


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def faces(self, u, v, w, h, d):
        return {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        }

    def fill(self, rect, base, noise=7, f=1.0):
        x0, y0, w, h = rect
        for y in range(h):
            for x in range(w):
                n = 1 + (self.rnd.random() - 0.5) * noise / 100.0 * 4
                self.img.putpixel((x0 + x, y0 + y), shade(base, f * n))

    def box(self, u, v, w, h, d, base, noise=7):
        fc = self.faces(u, v, w, h, d)
        for name, r in fc.items():
            f = {"top": 1.12, "bottom": 0.7, "front": 1.0, "back": 0.9, "left": 0.85, "right": 0.85}[name]
            self.fill(r, base, noise, f)
        return fc

    def charmap(self, rect, rows, pal):
        x0, y0, w, h = rect
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal and pal[ch] is not None:
                    self.img.putpixel((x0 + x, y0 + y), pal[ch])

    def rowband(self, rect, y, color, f=1.0):
        x0, y0, w, h = rect
        for x in range(w):
            self.img.putpixel((x0 + x, y0 + y), shade(color, f))

    def save(self, name):
        self.img.save(OUT / f"{name}.png")


METAL = hexc(0x8A8F99)
DMETAL = hexc(0x4B4F58)
BLACK = hexc(0x0D0D10)
WHITE = hexc(0xFFFFFF)


def standard(sk, fur, light, dark, belly, head_rows, pal, joint=METAL):
    # head (0,0 8x8x8)
    fc = sk.box(0, 0, 8, 8, 8, fur)
    sk.fill(fc["bottom"], dark)
    sk.charmap(fc["front"], head_rows, pal)
    # body (16,16 8x12x4)
    fc = sk.box(16, 16, 8, 12, 4, fur)
    sk.fill(fc["top"], joint, 4)
    sk.fill(fc["bottom"], dark)
    sk.charmap(fc["front"], [], pal)
    # belly patch on the front
    x0, y0, w, h = fc["front"]
    for y in range(2, 11):
        for x in range(1, 7):
            sk.img.putpixel((x0 + x, y0 + y), shade(belly, 1 + (sk.rnd.random() - 0.5) * 0.1))
    # arms (40,16 4x12x4)
    fc = sk.box(40, 16, 4, 12, 4, fur)
    sk.fill(fc["top"], joint, 4)
    sk.fill(fc["bottom"], dark)
    for k in ("front", "back", "left", "right"):
        sk.rowband(fc[k], 8, joint)
        sk.rowband(fc[k], 9, shade(joint, 0.7))
        sk.rowband(fc[k], 10, joint)
        sk.rowband(fc[k], 11, shade(joint, 0.6))
    # legs (0,16 4x12x4)
    fc = sk.box(0, 16, 4, 12, 4, fur)
    sk.fill(fc["bottom"], dark)
    sk.fill(fc["top"], joint, 4)
    for k in ("front", "back", "left", "right"):
        sk.rowband(fc[k], 0, joint)
        sk.rowband(fc[k], 9, light, 0.9)
        sk.rowband(fc[k], 10, dark)
        sk.rowband(fc[k], 11, shade(dark, 0.8))


parts_java = {}


def reg(kind, parent, slot, box):
    parts_java.setdefault(kind, []).append((parent, slot, box))


def extra(sk, kind, parent, u, v, box, base, front=None, pal=None, noise=7):
    x, y, z, w, h, d = box
    fc = sk.box(u, v, w, h, d, base, noise)
    if front:
        sk.charmap(fc["front"], front, pal)
    reg(kind, parent, (u, v), box)
    return fc


# ---------------- Freddy ----------------
def freddy():
    fur, light, dark = hexc(0x7A4A22), hexc(0xB98A55), hexc(0x4A2A12)
    sk = Skin(1)
    pal = {"F": None, "K": hexc(0x2A180A), "B": BLACK, "W": WHITE, "c": shade(fur, 0.85)}
    rows = ["FFFFFFFF", "FKKFFKKF", "FBWFFWBF", "FBBFFBBF", "FFFFFFFF", "FcFFFFcF", "FFFFFFFF", "FFFFFFFF"]
    standard(sk, fur, light, dark, hexc(0xC9A06A), rows, pal)
    fc = extra(sk, "freddy", "head", 64, 0, (2, -11, -1, 3, 3, 1), fur, ["FFF", "FcF", "FFF"], {"c": light, "F": None})
    extra(sk, "freddy", "head", 64, 0, (-5, -11, -1, 3, 3, 1), fur)
    extra(sk, "freddy", "head", 64, 16, (-5, -9, -5, 10, 1, 10), hexc(0x16161A), noise=3)
    fc = extra(sk, "freddy", "head", 64, 32, (-3, -14, -3, 6, 5, 6), hexc(0x1B1B20), noise=3)
    sk.rowband(fc["front"], 4, hexc(0x5A5A66)); sk.rowband(fc["left"], 4, hexc(0x5A5A66)); sk.rowband(fc["right"], 4, hexc(0x5A5A66)); sk.rowband(fc["back"], 4, hexc(0x5A5A66))
    extra(sk, "freddy", "head", 64, 48, (-2, -3, -6, 4, 3, 2), hexc(0xC9A06A),
          ["MMMM", "MMMM", "DTTD"], {"M": None, "D": hexc(0x2A1A0A), "T": hexc(0xF0F0E0)})
    extra(sk, "freddy", "head", 100, 48, (-1, -4, -7, 2, 1, 1), BLACK, noise=2)
    extra(sk, "freddy", "body", 64, 64, (-2, 0, -3, 4, 2, 1), hexc(0x14141A),
          ["BbbB", "BBBB"], {"b": hexc(0x3A3A44), "B": None})
    sk.save("freddy")


def bonnie():
    fur, light, dark = hexc(0x6B5BB5), hexc(0x9488D8), hexc(0x3C3278)
    sk = Skin(2)
    pal = {"F": None, "K": hexc(0x2A2060), "B": BLACK, "W": hexc(0xFF3030), "c": shade(fur, 0.85)}
    rows = ["FFFFFFFF", "FKKFFKKF", "FBWFFWBF", "FBBFFBBF", "FFFFFFFF", "FcFFFFcF", "FFFFFFFF", "FFFFFFFF"]
    standard(sk, fur, light, dark, hexc(0x8C80D0), rows, pal)
    for x in (1, -3):
        fc = extra(sk, "bonnie", "head", 64, 0, (x if x > 0 else -3, -19, -0.5, 2, 11, 1), fur)
        sk.fill((fc["front"][0] + 0, fc["front"][1] + 1, 2, 8), light)
    extra(sk, "bonnie", "head", 64, 48, (-2, -3, -6, 4, 3, 2), hexc(0x9488D8),
          ["MMMM", "MMMM", "DTTD"], {"M": None, "D": hexc(0x2A1A50), "T": hexc(0xF0F0F0)})
    extra(sk, "bonnie", "head", 100, 48, (-1, -4, -7, 2, 1, 1), hexc(0xD05080), noise=2)
    extra(sk, "bonnie", "body", 64, 64, (-2, 0, -3, 4, 2, 1), hexc(0xC02020), ["BbbB", "BBBB"], {"b": hexc(0xE04040), "B": None})
    sk.save("bonnie")


def chica():
    fur, light, dark = hexc(0xE5C02A), hexc(0xF4DD6A), hexc(0xA88410)
    sk = Skin(3)
    pal = {"F": None, "K": hexc(0xB08A12), "B": BLACK, "W": hexc(0xFF5CC8), "c": shade(fur, 0.9)}
    rows = ["FFFFFFFF", "FKKFFKKF", "FBWFFWBF", "FBBFFBBF", "FFFFFFFF", "FcFFFFcF", "FFFFFFFF", "FFFFFFFF"]
    standard(sk, fur, light, dark, hexc(0xF4DD6A), rows, pal)
    for x in (-3, -1, 1):
        extra(sk, "chica", "head", 64, 0, (x, -11 if x == -1 else -10, -1, 2, 3, 2), fur)
    extra(sk, "chica", "head", 64, 48, (-3, -3, -8, 6, 2, 4), hexc(0xE87A1E))
    extra(sk, "chica", "head", 100, 48, (-2, -1, -7, 4, 1, 3), hexc(0xD0620E))
    fc = extra(sk, "chica", "body", 64, 64, (-3.5, 0.5, -3, 7, 9, 1), hexc(0xF2F2F2),
               ["WWWWWWW", "WPPPPPW", "WWWWWWW", "WPPPPPW", "WWWWWWW", "WPPPPPW", "WWWWWWW", "WWWWWWW", "WWWWWWW"],
               {"W": None, "P": hexc(0xE8559C)})
    sk.save("chica")


def foxy():
    fur, light, dark = hexc(0xA8381C), hexc(0xCC6A3C), hexc(0x641E0E)
    sk = Skin(4)
    pal = {"F": None, "K": hexc(0x3A0E06), "B": BLACK, "W": hexc(0xFFE030), "c": shade(fur, 0.85), "P": hexc(0x0A0A0A)}
    rows = ["FFFFFFFF", "FKKFFKKF", "FBWFFPPP", "FBBFFPPP", "FFFFFFFF", "FcFFFFcF", "FFFFFFFF", "FFFFFFFF"]
    standard(sk, fur, light, dark, hexc(0xE6D8C0), rows, pal)
    # torn chest: dark patches
    for (x, y) in [(18, 29), (19, 30), (20, 30), (21, 31), (23, 33), (22, 34), (24, 35), (19, 36), (20, 37)]:
        sk.img.putpixel((x + 2, y), shade(hexc(0x30303a), 1.0))
    extra(sk, "foxy", "head", 64, 0, (2, -11, -1, 3, 3, 1), fur, ["FFF", "FcF", "FFF"], {"c": light, "F": None})
    extra(sk, "foxy", "head", 64, 0, (-5, -11, -1, 3, 3, 1), fur)
    extra(sk, "foxy", "head", 64, 48, (-2, -4, -9, 4, 2, 5), fur)
    extra(sk, "foxy", "head", 100, 48, (-2, -2, -8, 4, 1, 4), hexc(0xE6D8C0))
    extra(sk, "foxy", "head", 64, 96, (-2, -2, -9, 4, 1, 1), hexc(0xF0F0E0), noise=2)  # teeth strip
    extra(sk, "foxy", "right_arm", 64, 80, (-1, 10, -0.5, 1, 4, 1), hexc(0xC0C6D0), noise=3)
    extra(sk, "foxy", "right_arm", 100, 80, (-1, 13, -2.5, 1, 1, 2), hexc(0xC0C6D0), noise=3)
    sk.save("foxy")


for fn in (freddy, bonnie, chica, foxy):
    fn()

# Glowing eyes (drawn on top, full bright): head front is at (8,8).
EYES = {"freddy": [(0xFFFFFF, [(2, 2), (5, 2), (2, 3), (5, 3)])],
        "bonnie": [(0xFF2020, [(2, 2), (5, 2), (2, 3), (5, 3)])],
        "chica": [(0xFF5CC8, [(2, 2), (5, 2), (2, 3), (5, 3)])],
        "foxy": [(0xFFE030, [(2, 2), (2, 3)])]}
for name, groups in EYES.items():
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for col, pts in groups:
        for (x, y) in pts:
            im.putpixel((8 + x, 8 + y), hexc(col))
    im.save(OUT / f"{name}_eyes.png")


def fmt(v):
    return f"{float(v)}f"


lines = ["package sigf.mod;", "", "/** Generated by tools/skins.py: extra boxes of each animatronic model {parent, u, v, x, y, z, w, h, d}. */",
         "final class AnimatronicParts {", "\tprivate AnimatronicParts() {}", ""]
for kind, parts in parts_java.items():
    lines.append(f"\tstatic final Object[][] {kind.upper()} = {{")
    for parent, (u, v), (x, y, z, w, h, d) in parts:
        lines.append(f'\t\t{{"{parent}", {u}, {v}, {fmt(x)}, {fmt(y)}, {fmt(z)}, {fmt(w)}, {fmt(h)}, {fmt(d)}}},')
    lines.append("\t};")
lines.append("}")
Path("mod/java/sigf/mod").mkdir(parents=True, exist_ok=True)
Path("mod/java/sigf/mod/AnimatronicParts.java").write_text("\n".join(lines) + "\n")
print("ok")
