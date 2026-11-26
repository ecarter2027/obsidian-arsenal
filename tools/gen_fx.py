"""v27 particles: crisp, hard-edged 16x16 sprites (no soft round glows, no ordinary flames), one signature particle per
spell plus sharp replacements for the shared ones. Each sprite has a dark outline, a coloured body and a bright core,
drawn without anti-aliasing so it stays crisp. Three random variants per particle. Run by gen_assets.py (v27), or alone:
    python tools/gen_fx.py
Writes textures/particle/<name>_<i>.png, particles/<name>.json and src/main/java/.../registry/FxList.java (the names and
styles, which ModParticles / ClientModEvents read).
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, "..", "src", "main", "resources", "assets", "obsidian_arsenal")
T = os.path.join(A, "textures", "particle")
JAVA = os.path.join(HERE, "..", "src", "main", "java", "com", "obsidianarsenal", "registry", "FxList.java")
MOD = "obsidian_arsenal"

# palettes: (outline, body, core)
VIOLET = ((40, 8, 70), (170, 70, 255), (250, 235, 255))
LILAC = ((60, 20, 110), (200, 150, 255), (255, 255, 255))
VOID = ((10, 4, 18), (70, 20, 120), (190, 120, 255))
OBSID = ((6, 4, 10), (40, 26, 60), (200, 120, 255))
CRIMSON = ((50, 0, 6), (210, 20, 40), (255, 210, 210))
BLOOD = ((20, 0, 2), (140, 0, 20), (255, 90, 90))
GOLD = ((70, 20, 0), (255, 120, 30), (255, 245, 200))
BLACKFIRE = ((4, 2, 6), (14, 10, 18), (120, 10, 30))       # Amaterasu: pitch black, a dark crimson rim inside
PLASMA = ((80, 0, 10), (255, 60, 60), (255, 255, 255))
HOTWHITE = ((120, 20, 40), (255, 170, 170), (255, 255, 255))
ION = ((10, 30, 80), (90, 200, 255), (240, 255, 255))      # Kirin / Indra lightning edge, used only as accent


def poly(d, pts, pal_i, pal):
    d.polygon([(round(x), round(y)) for x, y in pts], fill=pal[pal_i] + (255,))


def scale_pts(pts, k, cx=7.5, cy=7.5):
    return [(cx + (x - cx) * k, cy + (y - cy) * k) for x, y in pts]


def layered(d, pts, pal):
    """Outline, body, core: the same polygon drawn three times, shrinking toward its centroid."""
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    poly(d, pts, 0, pal)
    poly(d, scale_pts(pts, 0.78, cx, cy), 1, pal)
    poly(d, scale_pts(pts, 0.38, cx, cy), 2, pal)


def line(d, pts, pal, width=1):
    d.line([(round(x), round(y)) for x, y in pts], fill=pal[0] + (255,), width=width + 2)
    d.line([(round(x), round(y)) for x, y in pts], fill=pal[1] + (255,), width=width + 1 if width > 1 else width)
    d.line([(round(x), round(y)) for x, y in pts], fill=pal[2] + (255,), width=1)


# ---- shapes: all point "up" (toward y = 0) so a velocity-aligned particle flies tip first
def shard(d, pal, r):
    w = r.uniform(2.2, 3.2)
    layered(d, [(7.5, 0), (7.5 + w, 7 + r.uniform(-1, 1)), (7.5, 15), (7.5 - w, 8 + r.uniform(-1, 1))], pal)


def needle(d, pal, r):
    layered(d, [(7.5, 0), (8.8, 11), (7.5, 15), (6.2, 11)], pal)


def lance(d, pal, r):
    layered(d, [(7.5, 0), (10, 5), (8.5, 5), (8.5, 15), (6.5, 15), (6.5, 5), (5, 5)], pal)


def chevron(d, pal, r):
    layered(d, [(7.5, 1), (14, 12), (10, 12), (7.5, 7), (5, 12), (1, 12)], pal)


def tri(d, pal, r):
    layered(d, [(7.5 + r.uniform(-1, 1), 1), (14, 14), (1, 14)], pal)


def diamond(d, pal, r):
    k = r.uniform(4.5, 6)
    layered(d, [(7.5, 7.5 - k * 1.3), (7.5 + k, 7.5), (7.5, 7.5 + k * 1.3), (7.5 - k, 7.5)], pal)


def star4(d, pal, r):
    a0 = r.uniform(0, 0.4)
    pts = []
    for i in range(8):
        a = a0 + i * math.pi / 4
        rr = 7.4 if i % 2 == 0 else 1.6
        pts.append((7.5 + math.sin(a) * rr, 7.5 - math.cos(a) * rr))
    layered(d, pts, pal)


def star8(d, pal, r):
    pts = []
    for i in range(16):
        a = i * math.pi / 8
        rr = (7.4 if i % 4 == 0 else 5.2) if i % 2 == 0 else 1.8
        pts.append((7.5 + math.sin(a) * rr, 7.5 - math.cos(a) * rr))
    layered(d, pts, pal)


def cross(d, pal, r):
    line(d, [(7.5, 0), (7.5, 15)], pal, 2)
    line(d, [(1, 7.5), (14, 7.5)], pal, 2)


def bolt(d, pal, r):
    x, pts, s = 7.5, [], 1
    for y in range(0, 16, 2):
        pts.append((x, y))
        s = -s if r.random() < 0.7 else s
        x = min(13, max(2, x + s * r.choice([3, 4])))
    pts.append((7.5 + r.uniform(-2, 2), 15))
    line(d, pts, pal, 1)


def crack(d, pal, r):
    pts, x = [], 7.5
    for y in range(0, 16, 4):
        pts.append((x, y)); x += r.uniform(-3, 3)
    line(d, pts, pal, 1)
    by = r.choice([4, 8])
    line(d, [pts[by // 4], (pts[by // 4][0] + r.choice([-5, 5]), by + 4)], pal, 1)


def slash(d, pal, r):
    """A thin sharp-ended crescent cut (not a circle: a blade of light)."""
    outer = [(1 + 13 * t, 13 - 11 * math.sin(math.pi * t)) for t in [i / 10 for i in range(11)]]
    inner = [(1 + 13 * t, 13 - 7.5 * math.sin(math.pi * t)) for t in [i / 10 for i in range(10, -1, -1)]]
    poly(d, outer + inner, 0, pal)
    o2 = [(2 + 11 * t, 12.5 - 10 * math.sin(math.pi * t)) for t in [i / 10 for i in range(11)]]
    i2 = [(2 + 11 * t, 12.5 - 8.2 * math.sin(math.pi * t)) for t in [i / 10 for i in range(10, -1, -1)]]
    poly(d, o2 + i2, 1, pal)
    d.line([(round(3 + 9 * t), round(12 - 9.2 * math.sin(math.pi * t))) for t in [i / 8 for i in range(9)]], fill=pal[2] + (255,))


def hexplate(d, pal, r):
    pts = [(7.5 + math.sin(i * math.pi / 3) * 7, 7.5 - math.cos(i * math.pi / 3) * 7.2) for i in range(6)]
    layered(d, pts, pal)
    line(d, [(7.5, 1), (7.5 + r.uniform(-2, 2), 14)], (pal[0], pal[0], pal[2]), 1)


def glyph(d, pal, r):
    """A hard-edged square rune: frame, a cross, corner notches."""
    d.rectangle([1, 1, 14, 14], outline=pal[0] + (255,), width=2)
    d.rectangle([2, 2, 13, 13], outline=pal[1] + (255,), width=1)
    line(d, [(7.5, 3), (7.5, 12)], pal, 1)
    line(d, [(4, 7.5 + r.choice([-2, 2])), (11, 7.5)], pal, 1)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=pal[2] + (255,))


def link(d, pal, r):
    """A square-cornered chain link."""
    d.rectangle([4, 0, 11, 15], fill=pal[0] + (255,))
    d.rectangle([5, 1, 10, 14], fill=pal[1] + (255,))
    d.rectangle([7, 3, 8, 12], fill=(0, 0, 0, 0))
    d.line([(5, 1), (5, 14)], fill=pal[2] + (255,))


def blackflame(d, pal, r):
    """Amaterasu (lore: pitch-black flames that cling and never go out): a crisp black flame, tongues licking up,
    a thin dark-crimson rim inside."""
    tips = [(7.5 + r.uniform(-1.5, 1.5), 0), (3 + r.uniform(-1, 1), 4), (12 + r.uniform(-1, 1), 3)]
    body = [(7.5, 15), (2, 11), (2.5, 7), tips[1], (5.5, 6), tips[0], (9.5, 6), tips[2], (13, 7), (13.5, 11)]
    poly(d, body, 0, pal)
    poly(d, scale_pts(body, 0.72, 7.5, 10), 1, pal)
    poly(d, scale_pts(body, 0.35, 7.5, 11.5), 2, pal)


def blade(d, pal, r):
    """Kagutsuchi: black flame drawn out into a long cutting edge."""
    layered(d, [(7.5, 0), (10.5, 4), (9.5, 15), (5.5, 15), (4.5, 4)], pal)
    for _ in range(3):
        y = r.randint(3, 12)
        d.point((r.choice([4, 11]), y), fill=pal[2] + (255,))


SHAPES = {f.__name__: f for f in (shard, needle, lance, chevron, tri, diamond, star4, star8, cross, bolt, crack, slash, hexplate,
                                  glyph, link, blackflame, blade)}

# name -> (shape, palette, style). Styles (FxStyle in Java): STREAK flies tip first along its velocity; FLASH pops and
# spins in place; BOLT flickers for a few ticks; FLAME rises upright; DRIFT hangs and falls slowly.
FX = {
    # ---- the shared particles, re-drawn sharp (their old names are kept, so every model / entity using them changes)
    "hellfire_flame": ("tri", CRIMSON, "FLASH"),
    "amaterasu_flame": ("blackflame", BLACKFIRE, "FLAME"),
    "purple_flame": ("shard", VIOLET, "STREAK"),
    "purple_spark": ("star4", VIOLET, "FLASH"),
    "deep_purple_spark": ("diamond", VOID, "FLASH"),
    "red_spark": ("needle", CRIMSON, "STREAK"),
    "plasma_spark": ("star4", PLASMA, "FLASH"),
    "void_wisp": ("shard", VOID, "STREAK"),
    "purple_lightning": ("bolt", LILAC, "BOLT"),
    # ---- Obsidian school signatures
    "fx_spike_explosion": ("shard", OBSID, "STREAK"),
    "fx_storm": ("slash", VIOLET, "FLASH"),
    "fx_tentacles": ("crack", VOID, "DRIFT"),
    "fx_cloak": ("bolt", VIOLET, "BOLT"),
    "fx_shatter": ("diamond", LILAC, "STREAK"),
    "fx_indra": ("chevron", LILAC, "STREAK"),          # Indra's Arrow: the arrow's purple lightning
    "fx_wall": ("hexplate", OBSID, "FLASH"),
    "fx_kirin": ("bolt", LILAC, "BOLT"),
    "fx_lance_rain": ("lance", OBSID, "STREAK"),
    "fx_shard_step": ("needle", VIOLET, "STREAK"),
    "fx_prison": ("glyph", VIOLET, "FLASH"),
    # ---- Hellfire school signatures
    "fx_columns": ("tri", BLOOD, "STREAK"),
    "fx_judgement": ("chevron", CRIMSON, "STREAK"),
    "fx_gyroscope": ("star8", GOLD, "FLASH"),
    "fx_amaterasu": ("blackflame", BLACKFIRE, "FLAME"),
    "fx_nova": ("star4", CRIMSON, "STREAK"),
    "fx_kagutsuchi": ("blade", BLACKFIRE, "STREAK"),
    "fx_brand": ("glyph", CRIMSON, "FLASH"),
    "fx_chains": ("link", BLOOD, "STREAK"),
    # ---- Plasma school signatures
    "fx_beam": ("needle", PLASMA, "STREAK"),
    "fx_black_hole": ("shard", VOID, "STREAK"),
    "fx_barrage": ("chevron", PLASMA, "STREAK"),
    "fx_railgun": ("lance", HOTWHITE, "STREAK"),
    "fx_blink": ("diamond", PLASMA, "FLASH"),
    "fx_ion_storm": ("bolt", HOTWHITE, "BOLT"),
}


def main():
    os.makedirs(T, exist_ok=True)
    os.makedirs(os.path.join(A, "particles"), exist_ok=True)
    for name, (shape, pal, _) in FX.items():
        r = random.Random(sum(map(ord, name)) * 131)
        for i in range(3):
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            SHAPES[shape](ImageDraw.Draw(img), pal, r)
            img.save(os.path.join(T, f"{name}_{i}.png"))
        # drop the old 4th frame of re-drawn shared particles, if any
        old = os.path.join(T, f"{name}_3.png")
        if os.path.exists(old):
            os.remove(old)
        if os.path.exists(os.path.join(T, f"{name}.png")):
            os.remove(os.path.join(T, f"{name}.png"))
        with open(os.path.join(A, "particles", f"{name}.json"), "w") as f:
            json.dump({"textures": [f"{MOD}:{name}_{i}" for i in range(3)]}, f, indent=1)
    rows = ",\n".join(f'        new Entry("{n}", FxStyle.{s})' for n, (_, _, s) in FX.items())
    with open(JAVA, "w") as f:
        f.write(f"""package com.obsidianarsenal.registry;

import java.util.List;

/** Generated by tools/gen_fx.py: every particle type and its style (see client/SharpParticle). */
public final class FxList {{
    public enum FxStyle {{ STREAK, FLASH, BOLT, FLAME, DRIFT }}

    public record Entry(String name, FxStyle style) {{}}

    public static final List<Entry> ALL = List.of(
{rows});

    private FxList() {{}}
}}
""")
    print("v27 sharp particles:", len(FX))


if __name__ == "__main__":
    main()
