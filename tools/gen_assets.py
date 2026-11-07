"""Generates every model, animation, texture, lang, recipe, tag and data file for Obsidian Arsenal.
Run from anywhere:  python3 tools/gen_assets.py   (needs Pillow + numpy)"""
import json, math, os, random
from PIL import Image, ImageDraw
import numpy as np

MOD = "obsidian_arsenal"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = f"{ROOT}/assets/{MOD}"
D = f"{ROOT}/data/{MOD}"

random.seed(11)
rng = np.random.default_rng(7)

def w(path, data, raw=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(data if raw else json.dumps(data, indent=1, ensure_ascii=False))

# ============================================================================================ textures
def noise(wd, ht, scale=4, octaves=3):
    out = np.zeros((ht, wd)); amp, tot = 1.0, 0.0
    for o in range(octaves):
        s = max(1, scale * (2 ** o))
        small = rng.random((ht // s + 2, wd // s + 2))
        out += np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((wd, ht), Image.BICUBIC)) / 255.0 * amp
        tot += amp; amp *= 0.5
    return out / tot

def obsidian_tex(wd, ht, glow=0.0):
    n = noise(wd, ht, 4)
    vein = np.abs(noise(wd, ht, 6, 2) - 0.5) < 0.035
    img = np.stack([14 + n * 26, 8 + n * 14, 26 + n * 44], -1)
    img[vein] = [140 + 60 * glow, 50, 220]
    img[rng.random((ht, wd)) < 0.012] = [190, 120, 255]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGBA")

def fire_tex(wd, ht, base, alpha=0.82):
    n, n2 = noise(wd, ht, 3), noise(wd, ht, 5, 2)
    r = np.clip(base[0] * (0.6 + 0.6 * n), 0, 255)
    g = np.clip(base[1] * (0.3 + 1.2 * n2), 0, 255)
    b = np.clip(base[2] * (0.5 + 0.8 * n), 0, 255)
    a = np.clip((0.55 + 0.6 * n) * 255 * alpha, 90, 255)
    return Image.fromarray(np.stack([r, g, b, a], -1).astype(np.uint8), "RGBA")

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True); img.save(path)

T = f"{A}/textures"
save(obsidian_tex(64, 64), f"{T}/entity/obsidian_spike.png")
save(obsidian_tex(64, 64, 0.6), f"{T}/entity/obsidian_shard.png")
save(obsidian_tex(64, 64), f"{T}/entity/obsidian_tentacle.png")
save(obsidian_tex(64, 64, 1.0), f"{T}/entity/obsidian_storm_spike.png")
save(obsidian_tex(64, 64, 1.0), f"{T}/entity/obsidian_orb.png")
save(fire_tex(64, 64, (230, 40, 20)), f"{T}/entity/hell_column.png")
save(fire_tex(64, 64, (255, 55, 25), 0.9), f"{T}/entity/hell_gyroscope.png")
save(fire_tex(64, 64, (255, 70, 30), 0.95), f"{T}/entity/indra_arrow.png")
g = obsidian_tex(64, 64, 0.8); d = ImageDraw.Draw(g)
for _ in range(60):
    x, y = random.randint(0, 60), random.randint(0, 60); d.rectangle([x, y, x + 2, y + 1], fill=(200, 60, 255, 255))
save(g, f"{T}/item/obsidian_gauntlet.png")
for layer in (1, 2):
    save(obsidian_tex(64, 32, 0.9), f"{T}/models/armor/obsidian_layer_{layer}.png")

def flame_sprite(core, mid, edge, path):
    img = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 7.5, (y - 9.0) / 8.0
            width = 1.0 - max(0, (9.0 - y)) / 11.0
            t = math.hypot(dx / max(0.25, width), dy * 0.9)
            if t < 1.0:
                col = (np.array(core) * (1 - t) + np.array(mid) * t) if t < 0.55 else \
                      (np.array(mid) * (1 - (t - 0.55) / 0.45) + np.array(edge) * ((t - 0.55) / 0.45))
                img[y, x] = [*col, 255 * (1 - t ** 3)]
    save(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA"), path)
flame_sprite((255, 220, 200), (230, 20, 10), (110, 0, 0), f"{T}/particle/hellfire_flame.png")
flame_sprite((70, 60, 80), (8, 6, 10), (70, 0, 20), f"{T}/particle/amaterasu_flame.png")
flame_sprite((255, 235, 255), (170, 60, 255), (70, 10, 150), f"{T}/particle/purple_flame.png")
for name in ("hellfire_flame", "amaterasu_flame", "purple_flame"):
    w(f"{A}/particles/{name}.json", {"textures": [f"{MOD}:{name}"]})

def effect_icon(path, c1, c2):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 16, 16], fill=c1)
    d.polygon([(10, 1), (5, 10), (9, 10), (7, 17), (13, 7), (9, 7)], fill=c2)
    save(img, path)
effect_icon(f"{T}/mob_effect/purple_lightning.png", (40, 10, 70, 255), (200, 120, 255, 255))
effect_icon(f"{T}/mob_effect/amaterasu.png", (10, 10, 10, 255), (160, 20, 40, 255))

# pixel-art icons (16x16): items, Iron's scroll per school, Iron's spell icons
def pix(rows, pal, path, jitter=6):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows[:16]):
        for x, ch in enumerate(row.ljust(16, ".")[:16]):
            if ch in pal:
                c = pal[ch]; j = random.randint(-jitter, jitter)
                img.putpixel((x, y), (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)), max(0, min(255, c[2] + j)), c[3] if len(c) > 3 else 255))
    save(img, path)

PAL = {"X": (18, 10, 32), "o": (58, 32, 98), "p": (170, 70, 255)}
ICONS = {
 "obsidian_helmet": ["","","....XXXXXXXX....","...XooooooooX...","..XXoXXXXXXoXX..","..XoXX....XXoX..","..XoX......XoX..","..XoX......XoX..","..XXX......XXX.."],
 "obsidian_chestplate": ["","..XXX......XXX..",".XooXX....XXooX.",".XoooXXXXXXoooX.",".XoXXoooooooXXoX","..XXXoXXXXoXXX..","...XXoXppXoXX...","...XXoXppXoXX...","...XXoXXXXoXX...","...XXooooooXX...","...XXXXXXXXXX..."],
 "obsidian_leggings": ["","...XXXXXXXXXX...","...XooooooooX...","...XoXXXXXXoX...","...XoXX..XXoX...","...XoX....XoX...","...XoX....XoX...","...XoX....XoX...","...XoX....XoX...","...XoX....XoX...","...XXX....XXX..."],
 "obsidian_boots": ["","","","","..XXX....XXX....","..XoX....XoX....","..XoX....XoX....","..XoX....XoX....",".XXoXX..XXoXX...",".XooooX.XooooX..",".XXXXXX.XXXXXX.."],
 "obsidian_gauntlet": ["","......XXXX......","....XXooooXX....","...XooppppooX...","..XooXXXXXXooX..","..XoXppppppXoX..","..XoXppXXppXoX..","..XoXppppppXoX..","..XooXXXXXXooX..","...XoooooooooX..","...XXoXXXXoXXX..","....XoX..XoX....","....XXX..XXX...."],
}
for n, rows in ICONS.items(): pix(rows, PAL, f"{T}/item/{n}.png")

SCROLL = ["", "...XXXXXXXXXX...", "..XssssssssssX..", "..XXXXXXXXXXXX..", "...XppppppppX...", "...XpqqqqqqpX...",
          "...XpqppppqpX...", "...XpqpqqpqpX...", "...XpqpqqpqpX...", "...XpqppppqpX...", "...XpqqqqqqpX...",
          "...XppppppppX...", "..XXXXXXXXXXXX..", "..XssssssssssX..", "...XXXXXXXXXX..."]
pix(SCROLL, {"X": (40, 20, 10), "s": (120, 80, 50), "p": (225, 205, 165), "q": (150, 70, 230)}, f"{T}/item/scroll_obsidian.png", 4)
pix(SCROLL, {"X": (40, 20, 10), "s": (120, 80, 50), "p": (225, 205, 165), "q": (220, 25, 15)}, f"{T}/item/scroll_hellfire.png", 4)
for school in ("obsidian", "hellfire"):
    w(f"{A}/models/item/scroll_{school}.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/scroll_{school}"}})

SPELL_ICONS = {
 "obsidian_spike_explosion": (["","","......p.........","......X.....p...","..p..XoX....X...","..X..XoX...XoX..",".XoX.XoX..XooX..",".XoXXooXXXXooX..","XXooooooooooooXX","pppppppppppppppp"], "o"),
 "obsidian_storm": (["","....X.....X.....","...Xo.....oX....","..Xo...pp...oX..","..o...p..p...o..","......p..p......","..o...p..p...o..","..Xo...pp...oX..","...Xo.....oX....","....X.....X....."], "o"),
 "obsidian_tentacles": (["","..X........X....","..oX......Xo....","...oX....Xo.....","...Xo....oX..X..","..Xo......oX.o..","..oX......Xo.oX.","...oX....Xo...o.","...Xo....oX...X.","pppppppppppppppp"], "o"),
 "purple_lightning_cloak": (["","........pp......",".......pp.......","......pp........",".....ppppp......","........pp......",".......pp.......","......pp........",".....pp.........","....p..........."], "p"),
 "obsidian_shatter": (["","","","..............X.","...........X.XoX","........X.XoXXoX",".....X.XoXXoXXoX","..X.XoXXoXXoXXoX",".XoXXoXXoXXoXXoX","pppppppppppppppp"], "o"),
 "columns_of_hell": (["","..r..........r..",".rfr........rfr.",".rfr...rr...rfr.","..fr..rffr..rf..",".rf...rffr...fr.","..fr...ff...rf..",".rfr...rf...rfr.","..rf...fr...fr..","rrrrrrrrrrrrrrrr"], "f"),
 "flaming_judgement": (["","","...........rrr..","r.......rrrfffr.","Xr...rrrfffyyfr.","XXrrrffffyyyyfr.","Xr...rrrfffyyfr.","r.......rrrfffr.","...........rrr..",""], "f"),
 "gyroscope": (["",".....rrrrrr.....","...rr......rr...","..r..rrrrrr..r..",".r..r......r..r.",".r.r...XX...r.r.",".r.r...XX...r.r.",".r..r......r..r.","..r..rrrrrr..r..","...rr......rr..."], "r"),
 "amaterasu": (["","......kk........",".....kkkk.......","....kkrrkk......","...kkrrrrkk.....","...kkrXXrkk.....","...kkrrrrkk.....","....kkrrkk......",".....kkkk.......","......kk........"], "k"),
 "indras_arrow": (["","..............yr","............yyr.","..........yfr...","........yfr.....","......yfr.......","....rfr.........","..rrr...........",".rr.............","r..............."], "f"),
}
SP_PAL = {"X": (18, 10, 32), "o": (80, 40, 140), "p": (190, 90, 255), "r": (200, 20, 10), "f": (255, 90, 20),
          "y": (255, 220, 120), "k": (10, 8, 12)}
for n, (rows, _) in SPELL_ICONS.items():
    rows = [""] * 3 + rows
    pix(rows, SP_PAL, f"{T}/gui/spell_icons/{n}.png", 8)

# ============================================================================================ item models
for n in ("obsidian_helmet", "obsidian_chestplate", "obsidian_leggings", "obsidian_boots"):
    w(f"{A}/models/item/{n}.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/{n}"}})
w(f"{A}/models/item/obsidian_gauntlet.json", {
    "parent": "builtin/entity", "gui_light": "front",
    "display": {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand":  {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [2, 1, -2], "scale": [0.9, 0.9, 0.9]},
        "firstperson_lefthand":  {"rotation": [0, 0, 0], "translation": [-2, 1, -2], "scale": [0.9, 0.9, 0.9]},
        "gui": {"rotation": [25, 135, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}}})

# ============================================================================================ lang
spells = {
 "obsidian_spike_explosion": ("Obsidian Spike Explosion", "Hurl an obsidian orb where you look. On impact, animated obsidian spikes burst from the ground and impale nearby enemies.", "%1$s was impaled by %2$s's obsidian spikes"),
 "obsidian_storm": ("Obsidian Storm", "A ring of swirling obsidian spikes whirls around you for a few seconds, shredding anything it touches.", "%1$s was shredded in %2$s's obsidian storm"),
 "obsidian_tentacles": ("Obsidian Tentacles", "Obsidian tentacles claw out of the ground around you, angled outward, lashing enemies for several seconds.", "%1$s was crushed by %2$s's obsidian tentacles"),
 "purple_lightning_cloak": ("Purple Lightning Cloak", "Wrap yourself in purple lightning: double movement speed and a violet afterimage trail.", "%1$s was struck by %2$s's purple lightning"),
 "obsidian_shatter": ("Obsidian Shatter", "Obsidian shards erupt in a line of about six blocks toward where you are looking.", "%1$s was skewered by %2$s's obsidian shards"),
 "columns_of_hell": ("Columns of Hell", "Four swirling pillars of hellfire orbit you, burning everything they pass.", "%1$s was consumed by %2$s's Columns of Hell"),
 "flaming_judgement": ("Flaming Judgement", "Channel a continuous torrent of red hellfire up to ten blocks ahead.", "%1$s was judged by %2$s's hellfire"),
 "gyroscope": ("Gyroscope", "Rings of hellfire spin around you and follow you, negating all damage and every arrow while they last.", "%1$s burned against %2$s's Gyroscope"),
 "amaterasu": ("Amaterasu", "Ignite the creature you are looking at with black flames that burn until it dies. Fails with no target.", "%1$s was consumed by %2$s's black flames"),
 "indras_arrow": ("Indra's Arrow", "Charge an arrow of hellfire, then loose it. Where it lands, a 25 block radius sphere is annihilated.", "%1$s was erased by %2$s's Indra's Arrow"),
}
lang = {
 f"itemGroup.{MOD}": "Obsidian Arsenal",
 f"item.{MOD}.obsidian_helmet": "Obsidian Helmet", f"item.{MOD}.obsidian_chestplate": "Obsidian Chestplate",
 f"item.{MOD}.obsidian_leggings": "Obsidian Leggings", f"item.{MOD}.obsidian_boots": "Obsidian Boots",
 f"item.{MOD}.obsidian_gauntlet": "Obsidian Gauntlet",
 f"effect.{MOD}.purple_lightning": "Purple Lightning", f"effect.{MOD}.amaterasu": "Amaterasu",
 f"school.{MOD}.obsidian": "Obsidian", f"school.{MOD}.hellfire": "Hellfire",
 f"attribute.{MOD}.obsidian_spell_power": "Obsidian Spell Power", f"attribute.{MOD}.obsidian_magic_resist": "Obsidian Magic Resistance",
 f"attribute.{MOD}.hellfire_spell_power": "Hellfire Spell Power", f"attribute.{MOD}.hellfire_magic_resist": "Hellfire Magic Resistance",
 f"death.attack.{MOD}.obsidian_magic": "%1$s was torn apart by obsidian magic",
 f"death.attack.{MOD}.hellfire_magic": "%1$s was burned away by hellfire",
 "death.attack.obsidian_magic": "%1$s was torn apart by obsidian magic",
 "death.attack.hellfire_magic": "%1$s was burned away by hellfire",
 f"ui.{MOD}.speed_bonus": "+%s Movement Speed", f"ui.{MOD}.immune_all": "Immune to all damage and projectiles",
 f"ui.{MOD}.damage_per_second": "%s Damage per Second", f"ui.{MOD}.until_death": "Burns until the target dies",
 f"message.{MOD}.combat_mode": "Gauntlet: COMBAT mode", f"message.{MOD}.mining_mode": "Gauntlet: MINING mode",
 f"tooltip.{MOD}.gauntlet.mode_combat": "Mode: Combat", f"tooltip.{MOD}.gauntlet.mode_mining": "Mode: Mining",
 f"tooltip.{MOD}.gauntlet.toggle": "Sneak + Right-click: switch mode",
 f"tooltip.{MOD}.gauntlet.combat": "Left-click: 7-hit combo", f"tooltip.{MOD}.gauntlet.mining": "Pickaxe / Axe / Shovel / Hoe, beyond netherite",
}
for k, (n, desc, death) in spells.items():
    lang[f"spell.{MOD}.{k}"] = n
    lang[f"spell.{MOD}.{k}.guide"] = desc
    lang[f"death.attack.{MOD}.{k}"] = death
    lang[f"death.attack.{MOD}.{k}.item"] = death + " using %3$s"
for k in ["obsidian_spike", "obsidian_shard", "obsidian_tentacle", "obsidian_storm_spike", "hell_column", "hell_gyroscope", "obsidian_orb", "indra_arrow"]:
    lang[f"entity.{MOD}.{k}"] = k.replace("_", " ").title()
w(f"{A}/lang/en_us.json", lang)

# ============================================================================================ data
def shaped(name, pattern, key):
    w(f"{D}/recipes/{name}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key, "result": {"item": f"{MOD}:{name}"}})
O = {"item": "minecraft:obsidian"}
shaped("obsidian_helmet", ["OOO", "O O"], {"O": O})
shaped("obsidian_chestplate", ["O O", "OOO", "OOO"], {"O": O})
shaped("obsidian_leggings", ["OOO", "O O", "O O"], {"O": O})
shaped("obsidian_boots", ["O O", "O O"], {"O": O})
shaped("obsidian_gauntlet", ["CNC", "OOO", "ONO"], {"C": {"item": "minecraft:crying_obsidian"}, "N": {"item": "minecraft:netherite_ingot"}, "O": O})

for school in ("obsidian", "hellfire"):
    w(f"{D}/damage_type/{school}_magic.json", {"exhaustion": 0.0, "message_id": f"{MOD}.{school}_magic", "scaling": "when_caused_by_living_non_player"})
w(f"{D}/tags/items/obsidian_focus.json", {"replace": False, "values": ["minecraft:crying_obsidian"]})
w(f"{D}/tags/items/hellfire_focus.json", {"replace": False, "values": ["minecraft:fire_charge"]})
# register our focuses with Iron's Scroll Forge
w(f"{ROOT}/data/irons_spellbooks/tags/items/school_focus.json", {"replace": False, "values": [f"#{MOD}:obsidian_focus", f"#{MOD}:hellfire_focus"]})
# Hellfire counts as fire for vanilla (burning / fire protection interactions)
w(f"{ROOT}/data/minecraft/tags/damage_type/is_fire.json", {"replace": False, "values": [f"{MOD}:hellfire_magic"]})

# ============================================================================================ GeckoLib geo + animations
def cube(origin, size, uv=None, rot=None, pivot=None):
    d = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size],
         "uv": list(uv) if uv else [random.randint(0, 40), random.randint(0, 40)]}
    if rot: d["rotation"] = [round(v, 3) for v in rot]; d["pivot"] = [round(v, 3) for v in pivot]
    return d

def bone(name, pivot=(0, 0, 0), cubes=None, parent=None, rot=None):
    b = {"name": name, "pivot": [round(v, 3) for v in pivot]}
    if parent: b["parent"] = parent
    if rot: b["rotation"] = list(rot)
    if cubes: b["cubes"] = cubes
    return b

def geo(ident, bones, vb=8):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{ident}", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": vb, "visible_bounds_height": vb, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}

def tower(layers):
    cs, y = [], 0
    for wd, h in layers:
        cs.append(cube((-wd / 2, y, -wd / 2), (wd, h, wd))); y += h
    return cs

def shift(cubes, dx=0, dz=0):
    out = []
    for c in cubes:
        c = json.loads(json.dumps(c)); c["origin"][0] += dx; c["origin"][2] += dz; out.append(c)
    return out

G, AN = f"{A}/geo", f"{A}/animations"
def anim_file(model, anims, folder="entity"):
    w(f"{AN}/{folder}/{model}.animation.json", {"format_version": "1.8.0", "animations": {f"animation.{model}.{k}": v for k, v in anims.items()}})

def standard(idle=None, ground=True):
    if ground:
        rise = {"animation_length": 0.4, "bones": {"root": {"position": {"0.0": [0, -56, 0], "0.25": {"vector": [0, 4, 0], "easing": "easeOutQuad"}, "0.4": [0, 0, 0]}}}}
        sink = {"animation_length": 0.5, "bones": {"root": {"position": {"0.0": [0, 0, 0], "0.5": {"vector": [0, -60, 0], "easing": "easeInQuad"}}}}}
    else:
        rise = {"animation_length": 0.35, "bones": {"root": {"scale": {"0.0": [0.05, 0.05, 0.05], "0.35": {"vector": [1, 1, 1], "easing": "easeOutBack"}}}}}
        sink = {"animation_length": 0.5, "bones": {"root": {"scale": {"0.0": [1, 1, 1], "0.5": {"vector": [0.02, 0.02, 0.02], "easing": "easeInQuad"}}}}}
    return {"rise": rise, "idle": idle or {"animation_length": 1.0, "loop": True, "bones": {"root": {"scale": {"0.0": [1, 1, 1], "1.0": [1, 1, 1]}}}}, "sink": sink}

# spike cluster
def lean_spike(name, px, pz, rot, k):
    layers = [(max(2, int(a * k)), max(2, int(b * k))) for a, b in [(8, 6), (7, 6), (6, 6), (5, 6), (3, 6), (2, 4)]]
    return bone(name, (px, 0, pz), shift(tower(layers), px, pz), "root", rot)
w(f"{G}/entity/obsidian_spike.geo.json", geo("obsidian_spike", [bone("root"),
    bone("spike", (0, 0, 0), tower([(14, 8), (12, 8), (10, 8), (8, 8), (6, 8), (4, 8), (2, 6)]), "root"),
    lean_spike("spikeL", -8, 0, (0, 0, 24), 1.0), lean_spike("spikeR", 8, 0, (0, 0, -24), 1.0),
    lean_spike("spikeB", 0, 8, (-22, 0, 0), 0.9), lean_spike("spikeF", 0, -8, (22, 0, 0), 0.8)]))
anim_file("obsidian_spike", standard({"animation_length": 1.0, "loop": True, "bones": {
    "spikeL": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, 2], "1.0": [0, 0, 0]}},
    "spikeR": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, -2], "1.0": [0, 0, 0]}}}}))

# shard crystals
def crystal(px, pz, k):
    return shift([cube((-3 * k, 0, -3 * k), (6 * k, 14 * k, 6 * k)), cube((-2 * k, 14 * k, -2 * k), (4 * k, 10 * k, 4 * k)),
                  cube((-1 * k, 24 * k, -1 * k), (2 * k, 7 * k, 2 * k))], px, pz)
w(f"{G}/entity/obsidian_shard.geo.json", geo("obsidian_shard", [bone("root"),
    bone("c1", (0, 0, 0), crystal(0, 0, 1.3), "root", (0, 0, -10)),
    bone("c2", (6, 0, 3), crystal(6, 3, 1.0), "root", (12, 30, 16)),
    bone("c3", (-6, 0, -3), crystal(-6, -3, 0.9), "root", (-14, 0, 20)),
    bone("c4", (2, 0, -7), crystal(2, -7, 0.7), "root", (-20, -20, 8))]))
anim_file("obsidian_shard", standard({"animation_length": 1.5, "loop": True, "bones": {
    "c1": {"scale": {"0.0": [1, 1, 1], "0.75": [1.03, 1.0, 1.03], "1.5": [1, 1, 1]}}}}))

# tentacle: chained segments leaning toward the entity front (= outward from the caster)
segs, parent = [bone("root")], "root"
for i, wd in enumerate([11, 10, 9, 8, 6, 4]):
    y0 = 10 * i
    segs.append(bone(f"seg{i+1}", (0, y0, 0), [cube((-wd / 2, y0, -wd / 2), (wd, 10, wd))], parent, (8 if i else 14, 0, 0)))
    parent = f"seg{i+1}"
segs.append(bone("tip", (0, 60, 0), [cube((-1, 60, -1), (2, 6, 2))], parent, (12, 0, 0)))
w(f"{G}/entity/obsidian_tentacle.geo.json", geo("obsidian_tentacle", segs))
sway = {}
for i in range(1, 7):
    ph = i * 0.6
    sway[f"seg{i}"] = {"rotation": {f"{t:.1f}": [round(4 * math.sin(ph + t * 3.14), 2), 0, round(6 * math.sin(ph + t * 3.14 + 1.2), 2)] for t in (0.0, 0.5, 1.0, 1.5, 2.0)}}
sway["tip"] = {"rotation": {"0.0": [0, 0, 0], "0.5": [15, 0, 10], "1.0": [0, 0, 0], "1.5": [-10, 0, -10], "2.0": [0, 0, 0]}}
t = standard({"animation_length": 2.0, "loop": True, "bones": sway})
t["rise"]["bones"]["seg2"] = {"rotation": {"0.0": [-40, 0, 0], "0.4": {"vector": [0, 0, 0], "easing": "easeOutBack"}}}
t["rise"]["bones"]["seg4"] = {"rotation": {"0.0": [-40, 0, 0], "0.4": {"vector": [0, 0, 0], "easing": "easeOutBack"}}}
anim_file("obsidian_tentacle", t)

# storm spike (horizontal, pointing along travel)
w(f"{G}/entity/obsidian_storm_spike.geo.json", geo("obsidian_storm_spike", [bone("root", (0, 17, 0)),
    bone("tilt", (0, 17, 0), None, "root", (90, 0, 0)),
    bone("spike", (0, 17, 0), tower([(6, 6), (5, 6), (4, 6), (3.2, 6), (2.4, 6), (1.4, 4)]), "tilt")]))
anim_file("obsidian_storm_spike", standard({"animation_length": 0.6, "loop": True, "bones": {"spike": {"rotation": {"0.0": [0, 0, 0], "0.6": [0, 360, 0]}}}}, ground=False))

# hellfire column (twisting stack)
layers = []
for i, wd in enumerate([12, 11, 10, 9, 8, 8, 9, 10, 11, 10, 8, 5]):
    ox, oz = 3 * math.sin(i * 0.7), 3 * math.cos(i * 0.7)
    layers.append(cube((-wd / 2 + ox, i * 6, -wd / 2 + oz), (wd, 6, wd), None, (0, i * 22, 0), (ox, i * 6 + 3, oz)))
w(f"{G}/entity/hell_column.geo.json", geo("hell_column", [bone("root"), bone("column", (0, 0, 0), layers, "root")]))
anim_file("hell_column", standard({"animation_length": 1.0, "loop": True, "bones": {
    "column": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 360, 0]}, "scale": {"0.0": [1, 1, 1], "0.5": [1.06, 1.03, 1.06], "1.0": [1, 1, 1]}}}}, ground=False))

# gyroscope (three tilted rings)
def ring_cubes(R, y=18, n=16):
    cs = []
    for i in range(n):
        th = 2 * math.pi * i / n
        cx, cz = R * math.cos(th), R * math.sin(th)
        cs.append(cube((cx - 2, y - 2, cz - 3.2), (4, 4, 6.4), None, (0, -math.degrees(th), 0), (cx, y, cz)))
    return cs
rings = [bone("root", (0, 18, 0))]
for i, (R, tl) in enumerate(zip((34, 38, 42), [(0, 0, 0), (65, 0, 35), (-65, 0, -35)]), 1):
    rings.append(bone(f"ring{i}_tilt", (0, 18, 0), None, "root", tl))
    rings.append(bone(f"ring{i}", (0, 18, 0), ring_cubes(R), f"ring{i}_tilt"))
w(f"{G}/entity/hell_gyroscope.geo.json", geo("hell_gyroscope", rings, 10))
anim_file("hell_gyroscope", standard({"animation_length": 5.0, "loop": True, "bones": {
    "ring1": {"rotation": {"0.0": [0, 0, 0], "5.0": [0, 1080, 0]}},
    "ring2": {"rotation": {"0.0": [0, 0, 0], "5.0": [0, -720, 0]}},
    "ring3": {"rotation": {"0.0": [0, 0, 0], "5.0": [0, 1800, 0]}}}}, ground=False))

# obsidian orb
w(f"{G}/entity/obsidian_orb.geo.json", geo("obsidian_orb", [bone("root", (0, 2, 0)), bone("orb", (0, 2, 0), [
    cube((-4, -2, -4), (8, 8, 8), (0, 0)), cube((-4, -2, -4), (8, 8, 8), (0, 0), (45, 45, 0), (0, 2, 0)),
    cube((-4, -2, -4), (8, 8, 8), (0, 0), (0, 45, 45), (0, 2, 0))], "root")], 2))
anim_file("obsidian_orb", {"idle": {"animation_length": 0.6, "loop": True, "bones": {"orb": {"rotation": {"0.0": [0, 0, 0], "0.6": [360, 360, 0]}}}}})

# Indra's arrow (front = -Z)
w(f"{G}/entity/indra_arrow.geo.json", geo("indra_arrow", [bone("root"), bone("arrow", (0, 0, 0), [
    cube((-1, -1, -16), (2, 2, 30)), cube((-3, -3, -20), (6, 6, 4)), cube((-2, -2, -24), (4, 4, 4)), cube((-1, -1, -28), (2, 2, 4)),
    cube((-4, -.5, 10), (8, 1, 6)), cube((-.5, -4, 10), (1, 8, 6)), cube((-3, -3, -12), (6, 6, 20))], "root")], 4))
anim_file("indra_arrow", {"idle": {"animation_length": 0.4, "loop": True, "bones": {
    "arrow": {"scale": {"0.0": [1, 1, 1], "0.2": [1.35, 1.35, 1.0], "0.4": [1, 1, 1]}, "rotation": {"0.0": [0, 0, 0], "0.4": [0, 0, 180]}}}}})

# Obsidian Gauntlet: fist toward -Z; "arm" scales along Z (stretch) and "fist" travels with the arm's front face
gaunt = [bone("root", (0, 8, 0)),
         bone("arm", (0, 8, 8), [cube((-3.5, 4.5, 0), (7, 7, 8), (0, 0)), cube((-4, 4, 5), (8, 8, 3), (0, 20))], "root"),
         bone("fist", (0, 8, 0), [cube((-4, 4, -8), (8, 8, 8), (0, 28)),
              cube((-3.5, 12, -8.5), (2, 2, 2), (0, 0)), cube((-1, 12, -8.5), (2, 2, 2), (0, 0)), cube((1.5, 12, -8.5), (2, 2, 2), (0, 0)),
              cube((-5.5, 5, -6), (2, 3, 5), (20, 0)), cube((-3.5, 3, -9), (7, 3, 1), (20, 8)), cube((-1.5, 12, -3), (3, 1, 3), (30, 0))], "root"),
         bone("claws", (0, 8, -8), [cube((-3.5, 6, -12), (1, 1, 4), (40, 0)), cube((-1, 6, -12.5), (1, 1, 4.5), (40, 0)),
              cube((1.5, 6, -12), (1, 1, 4), (40, 0))], "fist")]
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", gaunt, 4))

def stretch(s): return round(-8 * (s - 1), 2)
def A_(length, bones, loop=False):
    d = {"animation_length": length, "bones": bones}
    if loop: d["loop"] = True
    return d
claws_out = {"scale": {"0.0": [1, 1, 0.2], "0.1": [1, 1, 1.2], "0.4": [1, 1, 1.2], "0.6": [1, 1, 0.2]}}
anim_file("obsidian_gauntlet", {
 "idle": A_(2.0, {"root": {"position": {"0.0": [0, 0, 0], "1.0": [0, 0.4, 0], "2.0": [0, 0, 0]}}, "claws": {"scale": {"0.0": [1, 1, 0.2]}}}, True),
 "mode_combat": A_(0.5, {"root": {"scale": {"0.0": [1, 1, 1], "0.15": [1.2, 1.2, 1.2], "0.5": [1, 1, 1]}, "rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, 360]}}}),
 "mode_mining": A_(0.4, {"root": {"scale": {"0.0": [1, 1, 1], "0.15": [0.85, 0.85, 0.85], "0.4": [1, 1, 1]}}}),
 # 1 Obsidian Jab (punch)
 "attack_1": A_(0.5, {"root": {"position": {"0.0": [0, 0, 0], "0.1": [0, 0, 3], "0.2": [0, 0, -9], "0.5": [0, 0, 0]}}}),
 # 2 Rending Hook (claw)
 "attack_2": A_(0.5, {"root": {"position": {"0.0": [0, 0, 0], "0.1": [-4, 0, 2], "0.25": [6, 0, -5], "0.5": [0, 0, 0]},
                               "rotation": {"0.0": [0, 0, 0], "0.1": [0, -35, 0], "0.25": [0, 50, 0], "0.5": [0, 0, 0]}}, "claws": claws_out}),
 # 3 Piercing Lance (STRETCH x2.8)
 "attack_3": A_(0.75, {"arm": {"scale": {"0.0": [1, 1, 1], "0.1": [1, 1, 0.9], "0.25": {"vector": [1, 1, 2.8], "easing": "easeOutExpo"}, "0.45": [1, 1, 2.8], "0.75": [1, 1, 1]}},
                       "fist": {"position": {"0.0": [0, 0, 0], "0.1": [0, 0, 1], "0.25": {"vector": [0, 0, stretch(2.8)], "easing": "easeOutExpo"}, "0.45": [0, 0, stretch(2.8)], "0.75": [0, 0, 0]}}}),
 # 4 Hammer Slam (smash)
 "attack_4": A_(0.8, {"root": {"rotation": {"0.0": [0, 0, 0], "0.3": [-70, 0, 0], "0.42": {"vector": [45, 0, 0], "easing": "easeInCubic"}, "0.55": [45, 0, 0], "0.8": [0, 0, 0]},
                               "position": {"0.0": [0, 0, 0], "0.3": [0, 5, 3], "0.42": [0, -4, -6], "0.8": [0, 0, 0]},
                               "scale": {"0.0": [1, 1, 1], "0.42": [1.15, 1.15, 1.15], "0.6": [1, 1, 1]}}}),
 # 5 Claw Rake (claw)
 "attack_5": A_(0.6, {"root": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, 0, -40], "0.3": [0, 0, 55], "0.6": [0, 0, 0]},
                               "position": {"0.0": [0, 0, 0], "0.12": [3, 0, 2], "0.3": [-4, 0, -5], "0.6": [0, 0, 0]}},
                      "fist": {"scale": {"0.0": [1, 1, 1], "0.3": [1.2, 1, 1.2], "0.6": [1, 1, 1]}}, "claws": claws_out}),
 # 6 Seismic Reach (STRETCH x3.4, grab)
 "attack_6": A_(0.9, {"arm": {"scale": {"0.0": [1, 1, 1], "0.2": [1, 1, 0.85], "0.4": {"vector": [1, 1, 3.4], "easing": "easeOutExpo"}, "0.6": [1, 1, 3.4], "0.9": [1, 1, 1]}},
                      "fist": {"position": {"0.0": [0, 0, 0], "0.2": [0, 0, 2], "0.4": {"vector": [0, 0, stretch(3.4)], "easing": "easeOutExpo"}, "0.6": [0, 0, stretch(3.4)], "0.9": [0, 0, 0]}},
                      "root": {"rotation": {"0.0": [0, 0, 0], "0.2": [0, -30, 0], "0.4": [0, 15, 0], "0.9": [0, 0, 0]}},
                      "claws": {"scale": {"0.0": [1, 1, 0.2], "0.4": [1, 1, 1.2], "0.6": [1, 1, 0.4], "0.9": [1, 1, 0.2]}}}),
 # 7 Meteor Smash (smash)
 "attack_7": A_(1.3, {"root": {"position": {"0.0": [0, 0, 0], "0.35": [0, 8, 4], "0.6": [0, 8, 4], "0.72": {"vector": [0, -6, -8], "easing": "easeInQuart"}, "1.3": [0, 0, 0]},
                               "rotation": {"0.0": [0, 0, 0], "0.35": [-90, 0, 0], "0.6": [-90, 0, 0], "0.72": [55, 0, 0], "1.3": [0, 0, 0]},
                               "scale": {"0.0": [1, 1, 1], "0.72": [1.3, 1.3, 1.3], "0.95": [1, 1, 1]}}}),
}, "item")

# ============================================================================================ PlayerAnimator
# GeckoLib/Bedrock format (same as Iron's casting_animations.json). Folder MUST be "player_animation".
# Units: seconds + degrees. Arm X rotation -90 = arm pointing forward, -180 = straight up.
def k(t, v, ease=None):
    d = {"vector": [round(x, 2) for x in v]}
    if ease: d["easing"] = ease
    return f"{t:.4g}", d

def track(*frames):
    return dict(k(*f) for f in frames)

def panim(length, bones, loop=None):
    d = {"animation_length": length, "bones": bones}
    if loop: d["loop"] = loop
    return d

Z = (0, 0, 0)
OC, IC, IO, IQ = "easeOutCubic", "easeInCubic", "easeInOutSine", "easeInQuart"
PA = {
 # 1. Obsidian Jab
 "gauntlet_attack_1": panim(0.55, {
    "right_arm": {"rotation": track((0, Z), (0.1, (25, 20, 10), OC), (0.2, (-90, -3, 0), OC), (0.35, (-85, 0, 0), IO), (0.55, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.1, (-50, 25, -15), OC), (0.4, (-45, 25, -15)), (0.55, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.1, (0, -25, 0), OC), (0.2, (0, 30, 0), OC), (0.55, Z, IO))},
    "right_leg": {"rotation": track((0, Z), (0.2, (-20, 0, 0), OC), (0.55, Z, IO))},
    "left_leg": {"rotation": track((0, Z), (0.2, (20, 0, 0), OC), (0.55, Z, IO))}}),
 # 2. Rending Hook (left claw swipe)
 "gauntlet_attack_2": panim(0.55, {
    "left_arm": {"rotation": track((0, Z), (0.1, (-55, -10, 50), OC), (0.25, (-80, 45, -30), OC), (0.4, (-70, 40, -25)), (0.55, Z, IO))},
    "right_arm": {"rotation": track((0, Z), (0.1, (-75, 15, 15), OC), (0.25, (-60, -25, 10), OC), (0.55, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.1, (0, 35, 0), OC), (0.25, (0, -35, 0), OC), (0.55, Z, IO))},
    "head": {"rotation": track((0, Z), (0.25, (0, -15, 0), OC), (0.55, Z, IO))}}),
 # 3. Piercing Lance (stretch thrust)
 "gauntlet_attack_3": panim(0.8, {
    "right_arm": {"rotation": track((0, Z), (0.15, (40, 20, 10), OC), (0.3, (-90, -2, 0), "easeOutExpo"), (0.55, (-86, 0, 0)), (0.8, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.3, (25, 0, -25), OC), (0.8, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.15, (5, -40, 0), OC), (0.3, (20, 40, 0), OC), (0.55, (17, 35, 0)), (0.8, Z, IO))},
    "right_leg": {"rotation": track((0, Z), (0.15, (30, 0, 0), OC), (0.3, (-35, 0, 0), OC), (0.8, Z, IO))},
    "left_leg": {"rotation": track((0, Z), (0.15, (-30, 0, 0), OC), (0.3, (35, 0, 0), OC), (0.8, Z, IO))},
    "body": {"position": track((0, Z), (0.3, (0, -1.5, -2), OC), (0.8, Z, IO))}}),
 # 4. Hammer Slam (two-handed overhead smash)
 "gauntlet_attack_4": panim(0.95, {
    "right_arm": {"rotation": track((0, Z), (0.2, (-170, 0, 15), OC), (0.4, (-15, 0, 0), IQ), (0.6, (-15, 0, 0)), (0.95, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.2, (-170, 0, -15), OC), (0.4, (-12, 0, 0), IQ), (0.6, (-12, 0, 0)), (0.95, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.2, (-17, 0, 0), OC), (0.4, (37, 0, 0), IQ), (0.6, (32, 0, 0)), (0.95, Z, IO))},
    "head": {"rotation": track((0, Z), (0.4, (17, 0, 0), IQ), (0.95, Z, IO))},
    "right_leg": {"rotation": track((0, Z), (0.4, (-28, 0, 0), IQ), (0.95, Z, IO))},
    "left_leg": {"rotation": track((0, Z), (0.4, (28, 0, 0), IQ), (0.95, Z, IO))},
    "body": {"position": track((0, Z), (0.2, (0, 0.5, 0), OC), (0.4, (0, -3, 0), IQ), (0.65, (0, -2.5, 0)), (0.95, Z, IO))}}),
 # 5. Claw Rake (right then left raking arcs)
 "gauntlet_attack_5": panim(0.7, {
    "right_arm": {"rotation": track((0, Z), (0.1, (-125, 50, 30), OC), (0.25, (-70, -50, -20), OC), (0.7, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.25, (-115, -40, -30), OC), (0.4, (-70, 50, 20), OC), (0.7, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.1, (0, 35, 0), OC), (0.25, (8, -40, 0), OC), (0.4, (8, 35, 0), OC), (0.7, Z, IO))}}),
 # 6. Seismic Reach (stretch haymaker, full body twist)
 "gauntlet_attack_6": panim(0.95, {
    "right_arm": {"rotation": track((0, Z), (0.25, (50, 55, 25), OC), (0.45, (-90, -50, 0), "easeOutExpo"), (0.7, (-86, -45, 0)), (0.95, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.25, (-60, -30, -20), OC), (0.45, (30, 0, -20), OC), (0.95, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.25, (0, -57, 0), OC), (0.45, (12, 60, 0), "easeOutExpo"), (0.7, (9, 55, 0)), (0.95, Z, IO))},
    "head": {"rotation": track((0, Z), (0.25, (0, 30, 0), OC), (0.45, (0, -30, 0), OC), (0.95, Z, IO))},
    "right_leg": {"rotation": track((0, Z), (0.25, (23, 0, 0), OC), (0.45, (-35, 0, 0), OC), (0.95, Z, IO))},
    "left_leg": {"rotation": track((0, Z), (0.25, (-23, 0, 0), OC), (0.45, (35, 0, 0), OC), (0.95, Z, IO))}}),
 # 7. Meteor Smash (leap, hang, crash finisher)
 "gauntlet_attack_7": panim(1.4, {
    "right_arm": {"rotation": track((0, Z), (0.15, (-178, 0, 17), OC), (0.45, (-178, 0, 17)), (0.6, (-12, 0, 5), IQ), (0.85, (-12, 0, 5)), (1.4, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.15, (-178, 0, -17), OC), (0.45, (-178, 0, -17)), (0.6, (-12, 0, -5), IQ), (0.85, (-12, 0, -5)), (1.4, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.15, (-23, 0, 0), OC), (0.45, (-26, 0, 0)), (0.6, (46, 0, 0), IQ), (0.85, (37, 0, 0)), (1.4, Z, IO))},
    "head": {"rotation": track((0, Z), (0.6, (20, 0, 0), IQ), (1.4, Z, IO))},
    "right_leg": {"rotation": track((0, Z), (0.15, (-23, 0, 0), OC), (0.6, (-46, 0, 0), IQ), (1.4, Z, IO))},
    "left_leg": {"rotation": track((0, Z), (0.15, (23, 0, 0), OC), (0.6, (17, 0, 0), IQ), (1.4, Z, IO))},
    "body": {"position": track((0, Z), (0.6, (0, -3.5, 0), IQ), (0.9, (0, -3, 0)), (1.4, Z, IO))}}),
}
CASTS = {
 # Iron's AnimationHolders reference these by name (obsidian_arsenal:<name>)
 "obs_cast_slam": panim(0.8, {
    "right_arm": {"rotation": track((0, Z), (0.2, (-165, 0, 17), OC), (0.4, (-17, 0, 0), IC), (0.8, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.2, (-165, 0, -17), OC), (0.4, (-17, 0, 0), IC), (0.8, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.2, (-14, 0, 0), OC), (0.4, (28, 0, 0), IC), (0.8, Z, IO))}}),
 "obs_cast_thrust": panim(0.9, {
    "right_arm": {"rotation": track((0, Z), (0.15, (23, 17, 11), OC), (0.3, (-90, 0, 0), OC), (0.6, (-86, 0, 0), IO), (0.9, Z, IO))},
    "left_arm": {"rotation": track((0, Z), (0.15, (-34, 23, -17), OC), (0.3, (-69, 17, -11), OC), (0.9, Z, IO))},
    "torso": {"rotation": track((0, Z), (0.15, (0, -23, 0), OC), (0.3, (6, 23, 0), OC), (0.9, Z, IO))}}),
 "obs_cast_channel": panim(1.0, {
    "right_arm": {"rotation": track((0, (-88, -8, 0)), (0.5, (-92, -6, 0), IO), (1.0, (-88, -8, 0), IO))},
    "left_arm": {"rotation": track((0, (-80, 25, 0)), (0.5, (-84, 22, 0), IO), (1.0, (-80, 25, 0), IO))},
    "torso": {"rotation": track((0, (4, 10, 0)), (1.0, (4, 10, 0)))},
    "body": {"position": track((0, (0, 0, 0)), (0.5, (0, 0, 0.3), IO), (1.0, (0, 0, 0), IO))}}, True),
 "obs_cast_bow_charge": panim(2.0, {
    "left_arm": {"rotation": track((0, Z), (0.3, (-90, 10, 0), OC), (2.0, (-90, 10, 0)))},
    "right_arm": {"rotation": track((0, Z), (0.3, (-90, -30, 0), OC), (1.6, (-90, -55, 0), IO), (2.0, (-90, -60, 0)))},
    "torso": {"rotation": track((0, Z), (0.3, (0, 30, 0), OC), (2.0, (0, 35, 0)))},
    "head": {"rotation": track((0, Z), (0.3, (0, -30, 0), OC), (2.0, (0, -35, 0)))},
    "right_leg": {"rotation": track((0, Z), (0.3, (0, 0, 8), OC), (2.0, (0, 0, 8)))},
    "left_leg": {"rotation": track((0, Z), (0.3, (0, 0, -8), OC), (2.0, (0, 0, -8)))}}, "hold_on_last_frame"),
 "obs_cast_bow_release": panim(0.6, {
    "left_arm": {"rotation": track((0, (-90, 10, 0)), (0.1, (-95, 10, 0), OC), (0.6, Z, IO))},
    "right_arm": {"rotation": track((0, (-90, -60, 0)), (0.1, (-80, -90, 20), "easeOutExpo"), (0.6, Z, IO))},
    "torso": {"rotation": track((0, (0, 35, 0)), (0.6, Z, IO))},
    "head": {"rotation": track((0, (0, -35, 0)), (0.6, Z, IO))},
    "right_leg": {"rotation": track((0, (0, 0, 8)), (0.6, Z, IO))},
    "left_leg": {"rotation": track((0, (0, 0, -8)), (0.6, Z, IO))}}),
}
w(f"{A}/player_animation/gauntlet_combo.json", {"format_version": "1.8.0", "animations": PA})
w(f"{A}/player_animation/spell_casts.json", {"format_version": "1.8.0", "animations": CASTS})
print("assets generated")

# =====================================================================================================
# v2 overrides: arm-fitted gauntlet, netherite-style armor, glowing particle sprites, hellfire stream,
# idle variants, purple Indra's Arrow.
# =====================================================================================================
from PIL import ImageFilter
random.seed(99)

def rgba(img_arr):
    return Image.fromarray(np.clip(img_arr, 0, 255).astype(np.uint8), "RGBA")

# ------------------------------------------------------------------------------- glowing particle sprites
def glow_sprite(core, mid, edge, shape, seed, size=16):
    """shape(x, y, rnd) -> 0..1 intensity at normalised coords (-1..1)."""
    r = random.Random(seed)
    img = np.zeros((size, size, 4))
    for y in range(size):
        for x in range(size):
            nx, ny = (x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1
            v = shape(nx, ny, r)
            if v <= 0.02: continue
            if v > 0.66:
                t = (v - 0.66) / 0.34; c = np.array(mid) * (1 - t) + np.array(core) * t
            else:
                t = v / 0.66; c = np.array(edge) * (1 - t) + np.array(mid) * t
            img[y, x] = [*c, min(255, 255 * (v ** 0.7) * 1.15)]
    return rgba(img)

def flame_shape(phase):
    def f(nx, ny, r):
        # tall tongue of fire: wide rounded base (ny = +1), tapering, wobbling tip (ny = -1)
        h = (ny + 1) / 2                                   # 0 at top, 1 at bottom
        wob = 0.22 * math.sin(phase * 1.7 + h * 4.0) * (1 - h)
        width = 0.12 + 0.62 * (h ** 0.65)
        core = 1 - abs(nx - wob) / width
        base = 1 - max(0.0, (h - 0.82) / 0.18) ** 2        # round off the bottom
        lick = 0.25 * math.sin(phase * 3.1 + nx * 5.0) * (1 - h)
        return max(0.0, min(core, base) + lick * 0.3) * (0.55 + 0.6 * h ** 0.5) * 1.25
    return f

def orb_shape(hard):
    def f(nx, ny, r):
        d = math.hypot(nx, ny)
        return max(0.0, 1 - d) ** hard * 1.2
    return f

def bolt_sprite(seed, core=(255, 255, 255), mid=(225, 150, 255), edge=(120, 40, 230)):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    pts = [(r.randint(0, 3), r.randint(0, 15))]
    x = pts[0][0]
    while x < 15:
        x = min(15, x + r.randint(2, 4)); pts.append((x, max(0, min(15, pts[-1][1] + r.randint(-5, 5)))))
    glow = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); dg = ImageDraw.Draw(glow)
    dg.line(pts, fill=(*edge, 170), width=4)
    glow = glow.filter(ImageFilter.GaussianBlur(1.3))
    d.line(pts, fill=(*mid, 255), width=2)
    d.line(pts, fill=(*core, 255), width=1)
    return Image.alpha_composite(glow, img)

P = f"{T}/particle"
FLAMES = {
    "hellfire_flame": ((255, 245, 200), (255, 110, 20), (170, 10, 0)),
    "purple_flame":   ((255, 235, 255), (190, 80, 255), (80, 10, 170)),
    "amaterasu_flame": ((25, 12, 30), (8, 4, 10), (210, 10, 60)),
}
for name, (core, mid, edge) in FLAMES.items():
    for i in range(4):
        save(glow_sprite(core, mid, edge, flame_shape(i * 1.6), i), f"{P}/{name}_{i}.png")
SPARKS = {
    "purple_spark":      ((255, 255, 255), (215, 130, 255), (120, 30, 220), 2.2),
    "deep_purple_spark": ((235, 190, 255), (150, 50, 240), (60, 10, 140), 1.4),
    "red_spark":         ((255, 240, 180), (255, 90, 20), (180, 10, 0), 2.0),
}
for name, (core, mid, edge, hard) in SPARKS.items():
    for i in range(4):
        jitter = 1.0 + 0.12 * math.sin(i * 2.1)
        save(glow_sprite(core, mid, edge, lambda nx, ny, r, h=hard, j=jitter: orb_shape(h)(nx * j, ny / j, r), i), f"{P}/{name}_{i}.png")
for i in range(4):
    save(bolt_sprite(i * 31 + 7), f"{P}/purple_lightning_{i}.png")
for old in ("hellfire_flame", "amaterasu_flame", "purple_flame"):
    p_ = f"{P}/{old}.png"
    if os.path.exists(p_): os.remove(p_)
for name in list(FLAMES) + list(SPARKS) + ["purple_lightning"]:
    w(f"{A}/particles/{name}.json", {"textures": [f"{MOD}:{name}_{i}" for i in range(4)]})

# ------------------------------------------------------------------------------- textures: Indra purple, stream
save(fire_tex(64, 64, (190, 70, 255), 0.95), f"{T}/entity/indra_arrow.png")

def stream_tex():
    n, n2 = noise(64, 64, 3), noise(64, 64, 6, 2)
    yy = np.linspace(0, 1, 64)[:, None]
    r = 200 + 55 * n
    g = 40 + 160 * (n2 ** 1.5) * (1 - yy * 0.4)
    b = 10 + 40 * n2
    a = np.clip(120 + 135 * n * (1 - yy * 0.5), 60, 235)
    return rgba(np.stack([r, g, b, a], -1))
save(stream_tex(), f"{T}/entity/hellfire_stream.png")

# ------------------------------------------------------------------------------- Flaming Judgement cone (front = -Z)
cone = [bone("root"), bone("stream", (0, 0, 0), None, "root")]
SEG = 16
for i in range(SEG):
    z0 = -(i + 1) * 10
    rad = 1.6 + i * 1.25
    cubes = [cube((-rad, -rad, z0), (rad * 2, rad * 2, 10), (0, 0)),
             cube((-rad * 0.55, -rad * 0.55, z0 - 0.5), (rad * 1.1, rad * 1.1, 11), (0, 20), (0, 0, 45), (0, 0, z0 + 5))]
    cone.append(bone(f"seg{i}", (0, 0, z0 + 5), cubes, "stream", (0, 0, i * 23 % 90)))
w(f"{G}/entity/hellfire_stream.geo.json", geo("hellfire_stream", cone, 24))
idle_bones = {}
for i in range(SEG):
    ph = i * 0.07
    idle_bones[f"seg{i}"] = {
        "scale": {"0.0": [1, 1, 1], f"{0.15 + ph % 0.2:.2f}": [1.18, 1.18, 1.0], "0.4": [0.92, 0.92, 1.0], "0.5": [1, 1, 1]},
        "rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, 90 if i % 2 else -90]}}
anim_file("hellfire_stream", {
    "ignite": {"animation_length": 0.2, "bones": {"stream": {"scale": {"0.0": [0.4, 0.4, 0.02], "0.2": {"vector": [1, 1, 1], "easing": "easeOutQuad"}}}}},
    "idle": {"animation_length": 0.5, "loop": True, "bones": idle_bones}})

# ------------------------------------------------------------------------------- idle variants
def sway(amp_x, amp_z, period, phase_mul, tip_amp):
    b = {}
    for i in range(1, 7):
        ph = i * phase_mul
        keys = {}
        for k in range(5):
            t = period * k / 4
            keys[f"{t:.2f}"] = [round(amp_x * math.sin(ph + k * math.pi / 2), 2), 0, round(amp_z * math.sin(ph + k * math.pi / 2 + 1.2), 2)]
        b[f"seg{i}"] = {"rotation": keys}
    b["tip"] = {"rotation": {f"{period * k / 4:.2f}": [round(tip_amp * math.sin(k * math.pi / 2), 2), 0, round(tip_amp * math.cos(k * math.pi / 2), 2)] for k in range(5)}}
    return {"animation_length": period, "loop": True, "bones": b}

t = standard(sway(4, 6, 2.0, 0.6, 12))
t["rise"]["bones"]["seg2"] = {"rotation": {"0.0": [-40, 0, 0], "0.4": {"vector": [0, 0, 0], "easing": "easeOutBack"}}}
t["rise"]["bones"]["seg4"] = {"rotation": {"0.0": [-40, 0, 0], "0.4": {"vector": [0, 0, 0], "easing": "easeOutBack"}}}
t["idle_1"] = sway(9, 4, 1.2, 1.1, 25)                     # whipping
coil = {}
for i in range(1, 7):
    coil[f"seg{i}"] = {"rotation": {"0.0": [6, 0, 0], "0.75": [10, 25 * (1 if i % 2 else -1), 4], "1.5": [6, 0, 0], "2.25": [2, -25 * (1 if i % 2 else -1), -4], "3.0": [6, 0, 0]}}
t["idle_2"] = {"animation_length": 3.0, "loop": True, "bones": coil}  # slow coiling twist
anim_file("obsidian_tentacle", t)

sp = standard({"animation_length": 1.0, "loop": True, "bones": {
    "spikeL": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, 2], "1.0": [0, 0, 0]}},
    "spikeR": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, 0, -2], "1.0": [0, 0, 0]}}}})
sp["idle_1"] = {"animation_length": 1.6, "loop": True, "bones": {
    "spike": {"scale": {"0.0": [1, 1, 1], "0.8": [1.04, 1.06, 1.04], "1.6": [1, 1, 1]}},
    "spikeB": {"rotation": {"0.0": [0, 0, 0], "0.8": [-3, 0, 0], "1.6": [0, 0, 0]}},
    "spikeF": {"rotation": {"0.0": [0, 0, 0], "0.8": [3, 0, 0], "1.6": [0, 0, 0]}}}}
anim_file("obsidian_spike", sp)
sh = standard({"animation_length": 1.5, "loop": True, "bones": {"c1": {"scale": {"0.0": [1, 1, 1], "0.75": [1.03, 1.0, 1.03], "1.5": [1, 1, 1]}}}})
sh["idle_1"] = {"animation_length": 1.2, "loop": True, "bones": {
    "c2": {"rotation": {"0.0": [0, 0, 0], "0.6": [0, 6, 0], "1.2": [0, 0, 0]}},
    "c3": {"rotation": {"0.0": [0, 0, 0], "0.6": [0, -6, 0], "1.2": [0, 0, 0]}}}}
anim_file("obsidian_shard", sh)

# ------------------------------------------------------------------------------- GAUNTLET (wrist-centred, +Y up the arm, fist below)
def gauntlet_tex():
    n = noise(64, 64, 3)
    vein = np.abs(noise(64, 64, 5, 2) - 0.5) < 0.04
    base = np.stack([16 + n * 30, 10 + n * 16, 30 + n * 50, np.full((64, 64), 255.0)], -1)
    # plate seams every 8 px + bevel highlights
    for k in range(0, 64, 8):
        base[k, :, :3] *= 0.55; base[:, k, :3] *= 0.6
        if k + 1 < 64: base[k + 1, :, :3] = np.minimum(base[k + 1, :, :3] * 1.5, 255)
    img = base.copy()
    img[vein] = [90, 30, 150, 255]
    glow = np.zeros((64, 64, 4))
    glow[vein] = [200, 90, 255, 255]
    # bright knuckle / claw region (uv rows 40-63, cols 40-63)
    img[40:64, 40:64, :3] = np.clip(img[40:64, 40:64, :3] * 1.2 + [20, 10, 30], 0, 255)
    claw = np.zeros((64, 64), bool); claw[56:64, 56:64] = True
    img[claw] = [170, 80, 255, 255]; glow[claw] = [230, 160, 255, 255]
    return rgba(img), rgba(glow)
gt, gg = gauntlet_tex()
save(gt, f"{T}/item/obsidian_gauntlet.png")
save(gg, f"{T}/item/obsidian_gauntlet_glowmask.png")

UV_PLATE, UV_TRIM, UV_SPIKE, UV_FIST, UV_KNUCK, UV_CLAW = (0, 0), (0, 24), (24, 24), (0, 40), (40, 40), (56, 56)
gl = [bone("root", (0, 0, 0))]
bracer = [
    cube((-2.6, 0.4, -2.6), (5.2, 4.8, 5.2), UV_PLATE),          # forearm shell (arm is 4x4)
    cube((-2.95, 4.6, -2.95), (5.9, 1.1, 5.9), UV_TRIM),          # upper cuff
    cube((-2.95, -0.1, -2.95), (5.9, 1.0, 5.9), UV_TRIM),         # wrist cuff
    cube((-3.6, 1.2, -0.6), (1.0, 3.2, 1.2), UV_SPIKE),           # side fins
    cube((2.6, 1.2, -0.6), (1.0, 3.2, 1.2), UV_SPIKE),
    cube((-0.6, 1.4, -3.4), (1.2, 2.8, 0.8), UV_SPIKE),           # front / back ridges
    cube((-0.6, 1.4, 2.6), (1.2, 2.8, 0.8), UV_SPIKE),
]
gl.append(bone("bracer", (0, 0, 0), bracer, "root"))
gl.append(bone("fin_spikes", (0, 4.4, 0), [
    cube((-4.0, 4.0, -0.4), (1.0, 2.2, 0.8), UV_SPIKE, (0, 0, 25), (-3.5, 4.4, 0)),
    cube((3.0, 4.0, -0.4), (1.0, 2.2, 0.8), UV_SPIKE, (0, 0, -25), (3.5, 4.4, 0))], "bracer"))
gl.append(bone("extender", (0, 0, 0), [cube((-1.6, -1.0, -1.6), (3.2, 1.0, 3.2), UV_TRIM), cube((-2.1, -0.7, -0.5), (4.2, 0.4, 1.0), UV_SPIKE)], "root"))
gl.append(bone("fist", (0, 0, 0), [
    cube((-2.5, -3.7, -2.5), (5.0, 3.7, 5.0), UV_FIST),           # armoured hand (hand is 4x4x3)
    cube((-2.75, -4.5, -2.75), (5.5, 1.0, 5.5), UV_KNUCK),        # knuckle plate (punching end)
    cube((-2.8, -2.6, -1.4), (0.4, 1.8, 2.8), UV_SPIKE),          # thumb guards both sides
    cube((2.4, -2.6, -1.4), (0.4, 1.8, 2.8), UV_SPIKE)], "root"))
knucks = [cube((x - 0.5, -5.6, z - 0.5), (1.0, 1.2, 1.0), UV_CLAW) for x in (-1.3, 1.3) for z in (-1.3, 1.3)]
gl.append(bone("knuckles", (0, -4.5, 0), knucks, "fist"))
claws = [cube((x - 0.3, -9.0, z - 0.3), (0.6, 4.6, 0.6), UV_CLAW) for x, z in ((-1.6, 0), (0, 0), (1.6, 0), (0, -1.6), (0, 1.6))]
gl.append(bone("claws", (0, -4.4, 0), claws, "fist"))
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", gl, 4))

HIDE = {"0.0": [1, 0.02, 1]}
def ga(length, bones, loop=False):
    b = {"claws": {"scale": HIDE}, "extender": {"scale": {"0.0": [1, 0.01, 1]}}}
    b.update(bones)
    d = {"animation_length": length, "bones": b}
    if loop: d["loop"] = True
    return d
CLAW_OUT = lambda t_out, t_hold, t_in, s=1.0: {"scale": {"0.0": [1, 0.02, 1], f"{t_out}": {"vector": [1, s, 1], "easing": "easeOutBack"}, f"{t_hold}": [1, s, 1], f"{t_in}": [1, 0.02, 1]}}
def stretch(d_px, t_out, t_hold, t_end):
    return {
        "fist": {"position": {"0.0": [0, 0, 0], f"{t_out * 0.4:.2f}": [0, 1.5, 0], f"{t_out}": {"vector": [0, -d_px, 0], "easing": "easeOutExpo"}, f"{t_hold}": [0, -d_px, 0], f"{t_end}": {"vector": [0, 0, 0], "easing": "easeInQuad"}}},
        "extender": {"scale": {"0.0": [1, 0.01, 1], f"{t_out * 0.4:.2f}": [1, 0.01, 1], f"{t_out}": {"vector": [1, d_px, 1], "easing": "easeOutExpo"}, f"{t_hold}": [1, d_px, 1], f"{t_end}": {"vector": [1, 0.01, 1], "easing": "easeInQuad"}}}}
KN_POP = lambda t, s: {"scale": {"0.0": [1, 1, 1], f"{t}": [s, s, s], f"{t + 0.2:.2f}": [1, 1, 1]}}
anims = {
    "idle": ga(2.0, {"knuckles": {"scale": {"0.0": [1, 1, 1], "1.0": [1.08, 1.08, 1.08], "2.0": [1, 1, 1]}}}, True),
    "mode_combat": ga(0.6, {"knuckles": KN_POP(0.15, 1.7), "claws": CLAW_OUT(0.12, 0.3, 0.5), "fist": {"scale": {"0.0": [1, 1, 1], "0.15": [1.15, 1.15, 1.15], "0.4": [1, 1, 1]}}}),
    "mode_mining": ga(0.4, {"fist": {"scale": {"0.0": [1, 1, 1], "0.15": [0.88, 0.88, 0.88], "0.4": [1, 1, 1]}}}),
    "attack_1": ga(0.5, {"knuckles": KN_POP(0.2, 1.6), "fist": {"scale": {"0.0": [1, 1, 1], "0.2": [1.1, 1.1, 1.1], "0.45": [1, 1, 1]}}}),
    "attack_2": ga(0.55, {"claws": CLAW_OUT(0.1, 0.35, 0.5)}),
    "attack_3": ga(0.75, stretch(80, 0.25, 0.45, 0.72)),
    "attack_4": ga(0.8, {"fist": {"scale": {"0.0": [1, 1, 1], "0.3": [1.2, 1.2, 1.2], "0.42": {"vector": [1.7, 1.7, 1.7], "easing": "easeInCubic"}, "0.6": [1.6, 1.6, 1.6], "0.8": [1, 1, 1]}}, "knuckles": KN_POP(0.42, 1.5)}),
    "attack_5": ga(0.6, {"claws": CLAW_OUT(0.08, 0.38, 0.58, 1.45)}),
    "attack_6": dict(ga(0.95, stretch(110, 0.4, 0.62, 0.92)), **{}),
    "attack_7": ga(1.3, {"fist": {"scale": {"0.0": [1, 1, 1], "0.35": [1.3, 1.3, 1.3], "0.6": [1.4, 1.4, 1.4], "0.72": {"vector": [2.1, 2.1, 2.1], "easing": "easeInQuart"}, "0.95": [1.8, 1.8, 1.8], "1.3": [1, 1, 1]}}, "knuckles": KN_POP(0.72, 1.8)}),
}
anims["attack_6"]["bones"]["claws"] = CLAW_OUT(0.4, 0.62, 0.85, 1.2)   # grab with claws at full reach
anim_file("obsidian_gauntlet", anims, "item")

w(f"{A}/models/item/obsidian_gauntlet.json", {
    "parent": "builtin/entity", "gui_light": "front",
    "display": {
        # third person: places the wrist-centred geo exactly over the forearm through the normal held-item path
        # (vanilla ItemInHandLayer: hand pivot, Rx-90, Ry180, (1,2,-10)px -> this undoes it onto the arm bone).
        # Works with anything that renders held items normally, including Epic Fight's battle mode.
        "thirdperson_righthand": {"rotation": [90, 180, 0], "translation": [0, -2, 3.5], "scale": [1, 1, 1]},
        "thirdperson_lefthand":  {"rotation": [90, 180, 0], "translation": [0, -2, 3.5], "scale": [1, 1, 1]},
        # first person: fist forward, bracer toward the camera, sitting where the arm would be
        "firstperson_righthand": {"rotation": [105, -8, 0], "translation": [1.5, 3.5, -1.0], "scale": [1.15, 1.15, 1.15]},
        "firstperson_lefthand":  {"rotation": [105, 8, 0], "translation": [-1.5, 3.5, -1.0], "scale": [1.15, 1.15, 1.15]},
        "gui":    {"rotation": [25, 40, -20], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed":  {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "head":   {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0, 0, 0]}}})

# ------------------------------------------------------------------------------- ARMOR (vanilla humanoid UV layout)
def face_rects(u, v, w_, h_, d_):
    """Box UV faces for a cube at (u,v) with width w, height h, depth d (vanilla layout)."""
    return {
        "top":    (u + d_, v, w_, d_),
        "bottom": (u + d_ + w_, v, w_, d_),
        "right":  (u, v + d_, d_, h_),
        "front":  (u + d_, v + d_, w_, h_),
        "left":   (u + d_ + w_, v + d_, d_, h_),
        "back":   (u + 2 * d_ + w_, v + d_, w_, h_),
    }

BASE = np.array([34, 26, 44]); HI = np.array([72, 58, 92]); LO = np.array([14, 9, 20]); ACC = np.array([150, 60, 230])
def plate(img, x, y, w_, h_, rows=None, seams=(), accent_rows=(), gem=None, rnd=None):
    """Paint a netherite-style plate (bevelled border, mottled fill, horizontal seams) into a face rect."""
    rnd = rnd or random.Random(x * 131 + y)
    rows = rows if rows is not None else range(h_)
    for j in rows:
        for i in range(w_):
            c = BASE + rnd.randint(-6, 6) + (8 if (i + j) % 3 == 0 else 0)
            if j == rows[0] or i == 0: c = HI + rnd.randint(-5, 5)              # top/left bevel highlight
            if j == rows[-1] or i == w_ - 1: c = LO + rnd.randint(-3, 3)        # bottom/right shadow
            if j in seams: c = LO
            if j - 1 in seams and j != rows[-1]: c = HI - 10
            if j in accent_rows and 0 < i < w_ - 1: c = ACC + rnd.randint(-15, 15)
            img[y + j, x + i] = [*np.clip(c, 0, 255), 255]
    if gem:
        gx, gy = gem
        img[y + gy, x + gx] = [235, 170, 255, 255]; img[y + gy, x + gx + 1] = [190, 90, 255, 255]
        img[y + gy + 1, x + gx] = [160, 60, 240, 255]; img[y + gy + 1, x + gx + 1] = [120, 40, 200, 255]

def armor_layer1():
    img = np.zeros((32, 64, 4))
    # ---- helmet (head 8x8x8 at 0,0) ----
    hf = face_rects(0, 0, 8, 8, 8)
    for f in ("top", "right", "left", "back"):
        x, y, w_, h_ = hf[f]; plate(img, x, y, w_, h_, seams=(4,) if f != "top" else (), accent_rows=(5,) if f in ("right", "left") else ())
    x, y, w_, h_ = hf["front"]
    plate(img, x, y, w_, 3, accent_rows=())                       # brow plate
    for i in range(w_): img[y + 3, x + i] = [10, 6, 14, 255]       # visor slit
    img[y + 3, x + 2] = img[y + 3, x + 5] = [190, 90, 255, 255]    # glowing eye slits
    for j in range(4, 8):                                         # cheek guards, face open in the middle
        for i in (0, 1, 6, 7): img[y + j, x + i] = [*(BASE + (10 if i in (0, 7) else 0)), 255]
    img[y + 7, x + 0] = img[y + 7, x + 7] = [*LO, 255]
    # small crest on the hat layer top
    hx, hy, hw, hh = face_rects(32, 0, 8, 8, 8)["top"]
    for j in range(1, 7): img[hy + j, hx + 3] = img[hy + j, hx + 4] = [*(ACC - 30), 255]
    # ---- chestplate body (8x12x4 at 16,16) ----
    bf = face_rects(16, 16, 8, 12, 4)
    x, y, w_, h_ = bf["front"]; plate(img, x, y, w_, h_, seams=(5, 9), accent_rows=(), gem=(3, 2))
    for j in range(1, 11): img[y + j, x + 3] = np.maximum(img[y + j, x + 3], [*(HI - 5), 255])  # centre ridge
    x, y, w_, h_ = bf["back"]; plate(img, x, y, w_, h_, seams=(5, 9))
    for f in ("right", "left"):
        x, y, w_, h_ = bf[f]; plate(img, x, y, w_, h_, seams=(5, 9))
    x, y, w_, h_ = bf["top"]; plate(img, x, y, w_, h_)
    # ---- chestplate arms (4x12x4 at 40,16): pauldron + vambrace, hands left bare ----
    af = face_rects(40, 16, 4, 12, 4)
    for f in ("right", "front", "left", "back"):
        x, y, w_, h_ = af[f]; plate(img, x, y, w_, h_, rows=list(range(0, 10)), seams=(4,), accent_rows=(2,) if f in ("right", "left") else ())
    x, y, w_, h_ = af["top"]; plate(img, x, y, w_, h_)
    # ---- boots (leg 4x12x4 at 0,16): bottom 5 rows + sole ----
    lf = face_rects(0, 16, 4, 12, 4)
    for f in ("right", "front", "left", "back"):
        x, y, w_, h_ = lf[f]; plate(img, x, y, w_, h_, rows=list(range(7, 12)), seams=(10,), accent_rows=(8,) if f == "front" else ())
    x, y, w_, h_ = lf["bottom"]
    for j in range(h_):
        for i in range(w_): img[y + j, x + i] = [*LO, 255]
    return rgba(img)

def armor_layer2():
    img = np.zeros((32, 64, 4))
    # ---- leggings waist (body 8x12x4 at 16,16): belt on the bottom 4 rows ----
    bf = face_rects(16, 16, 8, 12, 4)
    for f in ("front", "back", "right", "left"):
        x, y, w_, h_ = bf[f]; plate(img, x, y, w_, h_, rows=list(range(8, 12)), accent_rows=(9,) if f == "front" else ())
    x, y, w_, h_ = bf["bottom"]
    for j in range(h_):
        for i in range(w_): img[y + j, x + i] = [*BASE, 255]
    # ---- leggings legs (4x12x4 at 0,16): thighs + knees, boots cover the bottom ----
    lf = face_rects(0, 16, 4, 12, 4)
    for f in ("right", "front", "left", "back"):
        x, y, w_, h_ = lf[f]; plate(img, x, y, w_, h_, rows=list(range(0, 9)), seams=(4,), accent_rows=(6,) if f == "front" else ())
    x, y, w_, h_ = lf["top"]; plate(img, x, y, w_, h_)
    return rgba(img)

save(armor_layer1(), f"{T}/models/armor/obsidian_layer_1.png")
save(armor_layer2(), f"{T}/models/armor/obsidian_layer_2.png")
print("v2 assets generated")

# =====================================================================================================
# v3: solid gyroscope rings (continuous, each tumbling about its own X axis)
# =====================================================================================================
GYRO_RINGS = [  # (radius px, yaw of the ring's spin axis, seconds per turn, direction)
    (34, 0, 1.5, 1),       # periods divide the 6 s loop exactly -> seamless spin
    (39, 60, 2.0, -1),
    (44, -60, 1.2, 1),
]
def solid_ring(R, y, thick=2.6, width=2.6, segs=56):
    """A ring that reads as one continuous band: many short tangent blocks that overlap their neighbours."""
    cs = []
    seg_len = 2 * math.pi * R / segs * 1.35        # 35% overlap -> no visible gaps
    for i in range(segs):
        th = 2 * math.pi * i / segs
        cx, cz = R * math.cos(th), R * math.sin(th)
        # long axis along z, then rotated about Y so it lies tangent to the circle
        cs.append(cube((cx - thick / 2, y - width / 2, cz - seg_len / 2), (thick, width, seg_len), (0, 0),
                       (0, -math.degrees(th), 0), (cx, y, cz)))
    return cs
gy = [bone("root", (0, 18, 0))]
for i, (R, yaw, per, d) in enumerate(GYRO_RINGS, 1):
    gy.append(bone(f"ring{i}_axis", (0, 18, 0), None, "root", (0, yaw, 0)))       # orients this ring's X axis
    gy.append(bone(f"ring{i}", (0, 18, 0), solid_ring(R, 18), f"ring{i}_axis"))   # spins about X
w(f"{G}/entity/hell_gyroscope.geo.json", geo("hell_gyroscope", gy, 10))

def spin_x(period, direction, length=6.0):
    keys, n = {}, int(round(length / period * 4))
    for k in range(n + 1):
        t = k * period / 4
        keys[f"{t:.3f}"] = [direction * 90 * k, 0, 0]
    return {"rotation": keys}
gyro_idle = {f"ring{i}": spin_x(per, d) for i, (R, yaw, per, d) in enumerate(GYRO_RINGS, 1)}
anim_file("hell_gyroscope", {
    "rise": {"animation_length": 0.35, "bones": {"root": {"scale": {"0.0": [0.05, 0.05, 0.05], "0.35": {"vector": [1, 1, 1], "easing": "easeOutBack"}}}}},
    "idle": {"animation_length": 6.0, "loop": True, "bones": gyro_idle},
    "sink": {"animation_length": 0.5, "bones": {"root": {"scale": {"0.0": [1, 1, 1], "0.5": {"vector": [0.02, 0.02, 0.02], "easing": "easeInQuad"}}}}}})
# ring texture: bright molten band with darker edges
rt = np.zeros((64, 64, 4))
n = noise(64, 64, 3)
for yy in range(64):
    edge = abs((yy % 8) - 3.5) / 3.5
    rt[yy, :, 0] = 255
    rt[yy, :, 1] = 60 + 150 * (1 - edge) * n[yy]
    rt[yy, :, 2] = 10 + 60 * (1 - edge) * n[yy]
    rt[yy, :, 3] = 255 - 70 * edge
save(rgba(rt), f"{T}/entity/hell_gyroscope.png")
print("v3 gyroscope generated")

# =====================================================================================================
# v4: scrolls + spell icons drawn in the style of Iron's Spells (original art, not copied)
# =====================================================================================================
from PIL import ImageEnhance

def draw_scroll(pal, seed):
    rnd = random.Random(seed)
    SS = 6                                   # supersampling
    img = np.zeros((16, 16, 4))
    ang = math.radians(-38)
    ca, sa = math.cos(ang), math.sin(ang)
    rune_rows = [(-3.2, rnd.uniform(-3.4, -2.6), rnd.uniform(-0.8, 0.4), rnd.uniform(0.9, 1.6), rnd.uniform(2.6, 3.4)) for _ in range(1)]
    runes = []
    for k, v0 in enumerate((-2.6, 0.0, 2.6)):
        a1 = rnd.uniform(-3.3, -2.5); a2 = rnd.uniform(-0.9, 0.1); b1 = a2 + rnd.uniform(0.8, 1.3); b2 = rnd.uniform(2.4, 3.3)
        runes.append((v0, [(a1, a2), (b1, b2)]))
    for py in range(16):
        for px in range(16):
            acc = np.zeros(4); n = 0
            for sy in range(SS):
                for sx in range(SS):
                    x = px + (sx + 0.5) / SS - 8; y = py + (sy + 0.5) / SS - 8
                    u = x * ca + y * sa; v = -x * sa + y * ca          # u across the sheet, v along it
                    col = None
                    if abs(v) <= 5.0 and abs(u) <= 4.4:                # parchment sheet
                        shade = 1.0 - 0.18 * (u + 4.4) / 8.8 + 0.06 * math.sin(v * 2.1)
                        col = [*(np.array(pal["sheet"]) * shade), 255]
                        if abs(u) > 3.75 or abs(v) > 4.4: col = [*pal["outline"], 255]
                        for v0, segs in runes:
                            if abs(v - v0) < 0.55 and any(a <= u <= b for a, b in segs):
                                col = [*(pal["rune_hi"] if abs(v - v0) < 0.22 else pal["rune"]), 255]
                    if 5.0 < abs(v) <= 6.6 and abs(u) <= 5.0:          # rolled ends
                        t = (abs(v) - 5.0) / 1.6
                        shade = 0.8 + 0.45 * math.sin(t * math.pi)
                        col = [*(np.clip(np.array(pal["roll"]) * shade, 0, 255)), 255]
                        if abs(u) > 4.4 or t > 0.85 or t < 0.08: col = [*pal["outline"], 255]
                    if 5.0 < abs(v) <= 6.6 and 5.0 < abs(u) <= 6.4:    # end caps / knobs
                        col = [*(pal["knob_hi"] if abs(v) < 5.9 else pal["knob"]), 255]
                    if col is not None: acc += col; n += 1
            if n >= SS * SS * 0.45:
                c = acc / n; c[3] = 255; img[py, px] = c
    return rgba(img)

SCROLL_PAL = {
    "obsidian": {"sheet": (58, 40, 82), "roll": (36, 22, 54), "outline": (14, 8, 22), "rune": (170, 70, 250), "rune_hi": (235, 185, 255),
                 "knob": (120, 40, 200), "knob_hi": (200, 120, 255)},
    "hellfire": {"sheet": (112, 40, 32), "roll": (70, 18, 14), "outline": (26, 6, 5), "rune": (255, 120, 20), "rune_hi": (255, 225, 130),
                 "knob": (220, 120, 10), "knob_hi": (255, 215, 60)},
}
for school, pal in SCROLL_PAL.items():
    save(draw_scroll(pal, hash(school) % 1000), f"{T}/item/scroll_{school}.png")

# ---- spell icons: full-bleed painterly 16x16, glowing subject on a dark vignette (drawn at 64px then reduced)
from PIL import ImageDraw as _D
def canvas(bg_in, bg_out):
    im = Image.new("RGBA", (64, 64)); px_ = im.load()
    for y in range(64):
        for x in range(64):
            d = min(1.0, math.hypot(x - 32, y - 32) / 45)
            c = [int(bg_in[i] * (1 - d) + bg_out[i] * d) for i in range(3)]
            px_[x, y] = (*c, 255)
    return im
def glow(base, draw_fn, blur, strength=1.0):
    layer = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); draw_fn(_D.Draw(layer))
    g = layer.filter(ImageFilter.GaussianBlur(blur))
    if strength != 1.0:
        a = np.array(g).astype(float); a[..., 3] = np.clip(a[..., 3] * strength, 0, 255); g = rgba(a)
    base.alpha_composite(g); return base
def solid(base, draw_fn):
    layer = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); draw_fn(_D.Draw(layer)); base.alpha_composite(layer); return base
def finish(im, path):
    small = im.resize((16, 16), Image.LANCZOS)
    small = ImageEnhance.Contrast(small).enhance(1.25); small = ImageEnhance.Color(small).enhance(1.25)
    a = np.array(small); a[..., 3] = 255; save(Image.fromarray(a, "RGBA"), path)

PUR, PUR_HI, OBS, OBS_HI = (175, 70, 255, 255), (240, 200, 255, 255), (78, 50, 118, 255), (205, 150, 255, 255)
RED, ORA, YEL, WHT = (220, 30, 10, 255), (255, 120, 20, 255), (255, 220, 110, 255), (255, 250, 230, 255)
def spike(d, x, base_y, h, w_, fill, edge):
    d.polygon([(x - w_, base_y), (x, base_y - h), (x + w_, base_y)], fill=fill, outline=edge)
    d.line([(x, base_y - h), (x - w_ * 0.3, base_y)], fill=edge, width=1)
SI = f"{T}/gui/spell_icons"

def icon_spike_explosion():
    im = canvas((60, 20, 90), (8, 4, 14))
    glow(im, lambda d: d.ellipse([10, 22, 54, 60], fill=PUR), 9, 1.4)
    solid(im, lambda d: [spike(d, x, 60, h, w_, OBS, OBS_HI) for x, h, w_ in ((32, 46, 9), (17, 30, 7), (47, 34, 7), (8, 18, 5), (57, 20, 5))])
    glow(im, lambda d: [d.line([(32, 16), (32, 56)], fill=PUR_HI, width=2), d.line([(17, 32), (18, 56)], fill=PUR, width=2)], 1.5)
    finish(im, f"{SI}/obsidian_spike_explosion.png")
def icon_storm():
    im = canvas((50, 16, 80), (6, 3, 12))
    glow(im, lambda d: d.arc([8, 8, 56, 56], 0, 300, fill=PUR, width=6), 4, 1.6)
    def spikes(d):
        for k in range(7):
            a = k / 7 * 2 * math.pi; cx, cy = 32 + 22 * math.cos(a), 32 + 22 * math.sin(a)
            tx, ty = -math.sin(a), math.cos(a)
            d.polygon([(cx - tx * 9, cy - ty * 9), (cx + tx * 9 + math.cos(a) * 2, cy + ty * 9 + math.sin(a) * 2), (cx + math.cos(a) * 4, cy + math.sin(a) * 4)], fill=OBS, outline=OBS_HI)
    solid(im, spikes)
    glow(im, lambda d: d.ellipse([26, 26, 38, 38], fill=PUR_HI), 4, 1.5)
    finish(im, f"{SI}/obsidian_storm.png")
def icon_tentacles():
    im = canvas((45, 14, 70), (6, 3, 10))
    glow(im, lambda d: d.rectangle([0, 52, 64, 64], fill=PUR), 6, 1.2)
    def tent(d):
        for bx, curl in ((14, 1), (32, -1), (50, 1)):
            pts = [(bx + curl * 10 * math.sin(t / 10 * 2.2), 62 - t * 4.6) for t in range(11)]
            for i_ in range(len(pts) - 1):
                d.line([pts[i_], pts[i_ + 1]], fill=OBS, width=max(2, 9 - i_))
            d.line(pts, fill=OBS_HI, width=1)
    solid(im, tent)
    finish(im, f"{SI}/obsidian_tentacles.png")
def icon_cloak():
    im = canvas((55, 18, 95), (6, 3, 14))
    bolt = [(36, 2), (24, 26), (34, 28), (20, 62), (44, 22), (33, 20), (42, 2)]
    glow(im, lambda d: d.polygon(bolt, fill=PUR), 7, 1.8)
    solid(im, lambda d: d.polygon(bolt, fill=(200, 120, 255, 255)))
    glow(im, lambda d: d.line([(36, 4), (26, 26), (34, 28), (23, 58)], fill=WHT, width=2), 1)
    glow(im, lambda d: [d.line([(8, 20 + k * 12), (18, 24 + k * 12)], fill=PUR_HI, width=2) for k in range(3)], 1.2)
    finish(im, f"{SI}/purple_lightning_cloak.png")
def icon_shatter():
    im = canvas((50, 18, 85), (7, 3, 12))
    glow(im, lambda d: d.line([(4, 60), (60, 30)], fill=PUR, width=6), 5, 1.4)
    solid(im, lambda d: [spike(d, x, 62 - k * 6, 12 + k * 5, 4 + k, OBS, OBS_HI) for k, x in enumerate((10, 22, 34, 46, 57))])
    finish(im, f"{SI}/obsidian_shatter.png")
def icon_columns():
    im = canvas((70, 12, 6), (10, 2, 2))
    def cols(d, col, w_):
        for k, x in enumerate((11, 26, 40, 54)):
            top = 8 + (k % 2) * 8
            d.polygon([(x - w_, 62), (x - w_ * 0.6, top + 10), (x, top), (x + w_ * 0.6, top + 10), (x + w_, 62)], fill=col)
    glow(im, lambda d: cols(d, RED, 6), 4, 1.3)
    glow(im, lambda d: cols(d, ORA, 3), 2)
    glow(im, lambda d: cols(d, YEL, 1), 1)
    glow(im, lambda d: d.ellipse([4, 50, 60, 64], fill=ORA), 4)
    finish(im, f"{SI}/columns_of_hell.png")
def icon_judgement():
    im = canvas((60, 10, 5), (8, 2, 2))
    cone = [(4, 60), (64, 4), (64, 36)]
    glow(im, lambda d: d.polygon(cone, fill=RED), 5, 1.4)
    glow(im, lambda d: d.polygon([(6, 58), (64, 12), (64, 30)], fill=ORA), 3)
    glow(im, lambda d: d.polygon([(8, 56), (64, 18), (64, 24)], fill=YEL), 2)
    finish(im, f"{SI}/flaming_judgement.png")
def icon_gyroscope():
    im = canvas((60, 12, 6), (8, 2, 2))
    def rings(d, col, w_):
        d.ellipse([6, 22, 58, 42], outline=col, width=w_)
        d.ellipse([22, 6, 42, 58], outline=col, width=w_)
        d.ellipse([10, 10, 54, 54], outline=col, width=w_)
    glow(im, lambda d: rings(d, RED, 6), 3, 1.6)
    glow(im, lambda d: rings(d, ORA, 3), 1.2)
    glow(im, lambda d: rings(d, YEL, 1), 0.6)
    finish(im, f"{SI}/gyroscope.png")
def icon_amaterasu():
    im = canvas((70, 4, 14), (8, 0, 2))
    flame = [(32, 4), (44, 26), (50, 46), (42, 60), (22, 60), (14, 46), (22, 28), (26, 36), (30, 18)]
    glow(im, lambda d: d.polygon(flame, fill=(230, 10, 50, 255)), 5, 1.6)
    solid(im, lambda d: d.polygon(flame, fill=(8, 4, 10, 255)))
    glow(im, lambda d: d.line(flame + [flame[0]], fill=(255, 40, 80, 255), width=1), 0.8)
    finish(im, f"{SI}/amaterasu.png")
def icon_indra():
    im = canvas((50, 14, 90), (6, 3, 14))
    glow(im, lambda d: d.line([(6, 58), (58, 6)], fill=PUR, width=12), 6, 1.5)
    glow(im, lambda d: d.line([(8, 56), (56, 8)], fill=(210, 140, 255, 255), width=4), 2)
    solid(im, lambda d: [d.line([(10, 54), (54, 10)], fill=WHT, width=2), d.polygon([(58, 4), (46, 10), (54, 18)], fill=WHT),
                         d.polygon([(6, 58), (4, 48), (12, 52)], fill=PUR_HI), d.polygon([(6, 58), (16, 60), (12, 52)], fill=PUR_HI)])
    glow(im, lambda d: [d.line([(20, 20), (26, 30), (22, 34), (30, 44)], fill=PUR_HI, width=2), d.line([(40, 50), (46, 42), (42, 38), (50, 30)], fill=PUR_HI, width=2)], 1)
    finish(im, f"{SI}/indras_arrow.png")
for fn in (icon_spike_explosion, icon_storm, icon_tentacles, icon_cloak, icon_shatter, icon_columns, icon_judgement, icon_gyroscope, icon_amaterasu, icon_indra):
    fn()
print("v4 scrolls + icons generated")

# =====================================================================================================
# v5: smooth PlayerAnimator animations (emote format, dense per-tick samples of a smooth Hermite curve)
#   - only parts with keys are enabled, so e.g. legs keep their vanilla walk cycle during punches
#   - each key: (tick, value). Tangents: Catmull-Rom style, flattened at extremes (no unwanted wobble),
#     zero at the first/last key so motion eases in and settles out.
# =====================================================================================================
PA_DIR = f"{A}/player_animation"
for old in ("gauntlet_combo.json", "spell_casts.json"):
    if os.path.exists(f"{PA_DIR}/{old}"): os.remove(f"{PA_DIR}/{old}")

def hermite_sample(keys, tick):
    if tick <= keys[0][0]: return keys[0][1]
    if tick >= keys[-1][0]: return keys[-1][1]
    n = len(keys)
    def tangent(i):
        if i == 0 or i == n - 1: return 0.0
        (t0, v0), (t1, v1), (t2, v2) = keys[i - 1], keys[i], keys[i + 1]
        if (v1 - v0) * (v2 - v1) <= 0: return 0.0           # local extreme: flat, no overshoot past the key
        return (v2 - v0) / (t2 - t0)
    for i in range(n - 1):
        (ta, va), (tb, vb) = keys[i], keys[i + 1]
        if ta <= tick <= tb:
            h = tb - ta; s = (tick - ta) / h
            ma, mb = tangent(i) * h, tangent(i + 1) * h
            h00 = 2 * s**3 - 3 * s**2 + 1; h10 = s**3 - 2 * s**2 + s
            h01 = -2 * s**3 + 3 * s**2; h11 = s**3 - s**2
            return h00 * va + h10 * ma + h01 * vb + h11 * mb
    return keys[-1][1]

def emote(name, end, parts, loop=False, hold=False):
    """parts: {part: {channel: [(tick, deg), ...]}}  ->  emote-format json, one move per tick per part."""
    moves = []
    for tick in range(0, end + 1):
        m = {"tick": tick, "easing": "linear"}
        for part, chans in parts.items():
            m[part] = {ch: round(hermite_sample(keys, tick), 3) for ch, keys in chans.items()}
        moves.append(m)
    em = {"beginTick": 0, "endTick": end, "stopTick": end + 1, "isLoop": bool(loop or hold),
          "returnTick": end if hold else 0, "nsfw": False, "degrees": True, "moves": moves}
    w(f"{PA_DIR}/{name}.json", {"version": 3, "name": name, "author": MOD, "description": "Obsidian Arsenal", "emote": em})

def ch(**kw):  # helper: ch(pitch=[(t,v)...], yaw=..., roll=...)
    return kw

# ---------------------------------------------------------------- gauntlet combo (hit frame = server hitDelay)
# 1. Obsidian Jab (hit @4) - right straight, left guard
emote("gauntlet_attack_1", 15, {
    "rightArm": ch(pitch=[(0, 0), (2, 18), (4, -90), (6, -95), (9, -84), (15, 0)],
                   yaw=[(0, 0), (2, 16), (4, -3), (9, -2), (15, 0)],
                   roll=[(0, 0), (2, 6), (4, 0), (15, 0)]),
    "leftArm": ch(pitch=[(0, 0), (2, -50), (9, -52), (15, 0)], yaw=[(0, 0), (2, 24), (9, 24), (15, 0)], roll=[(0, 0), (2, -12), (15, 0)]),
    "torso": ch(yaw=[(0, 0), (2, -18), (4, 22), (6, 25), (10, 18), (15, 0)]),
    "head": ch(yaw=[(0, 0), (2, 10), (4, -12), (10, -9), (15, 0)])})
# 2. Rending Hook (hit @4) - left hook with claws
emote("gauntlet_attack_2", 15, {
    "leftArm": ch(pitch=[(0, 0), (2, -62), (4, -86), (6, -82), (9, -70), (15, 0)],
                  yaw=[(0, 0), (2, -26), (4, 46), (6, 56), (9, 40), (15, 0)],
                  roll=[(0, 0), (2, 46), (4, -24), (6, -30), (15, 0)]),
    "rightArm": ch(pitch=[(0, 0), (2, -58), (10, -55), (15, 0)], yaw=[(0, 0), (2, -20), (15, 0)], roll=[(0, 0), (2, 12), (15, 0)]),
    "torso": ch(yaw=[(0, 0), (2, 30), (4, -32), (6, -38), (10, -26), (15, 0)]),
    "head": ch(yaw=[(0, 0), (2, -14), (4, 16), (10, 12), (15, 0)])})
# 3. Piercing Lance (hit @6) - long straight thrust, the gauntlet stretches out
emote("gauntlet_attack_3", 19, {
    "rightArm": ch(pitch=[(0, 0), (3, 34), (6, -90), (8, -93), (12, -87), (19, 0)],
                   yaw=[(0, 0), (3, 20), (6, -2), (12, 0), (19, 0)],
                   roll=[(0, 0), (3, 10), (6, 0), (19, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -40), (6, 24), (12, 20), (19, 0)], roll=[(0, 0), (6, -24), (19, 0)]),
    "torso": ch(yaw=[(0, 0), (3, -38), (6, 40), (8, 45), (12, 36), (19, 0)],
                pitch=[(0, 0), (3, 4), (6, 16), (8, 19), (12, 14), (19, 0)]),
    "head": ch(yaw=[(0, 0), (3, 22), (6, -24), (12, -20), (19, 0)])})
# 4. Hammer Slam (hit @8) - both fists overhead, crash down
emote("gauntlet_attack_4", 20, {
    "rightArm": ch(pitch=[(0, 0), (4, -168), (6, -176), (8, -16), (9, -10), (13, -18), (20, 0)], roll=[(0, 0), (4, 16), (8, 4), (20, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -168), (6, -176), (8, -14), (9, -8), (13, -16), (20, 0)], roll=[(0, 0), (4, -16), (8, -4), (20, 0)]),
    "torso": ch(pitch=[(0, 0), (4, -14), (6, -18), (8, 34), (9, 39), (13, 30), (20, 0)]),
    "head": ch(pitch=[(0, 0), (6, -10), (8, 14), (13, 10), (20, 0)])})
# 5. Claw Rake (hit @5) - right rake then left rake
emote("gauntlet_attack_5", 16, {
    "rightArm": ch(pitch=[(0, 0), (2, -122), (5, -72), (7, -66), (16, 0)], yaw=[(0, 0), (2, 50), (5, -50), (7, -56), (16, 0)], roll=[(0, 0), (2, 30), (5, -20), (16, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -60), (5, -114), (8, -72), (10, -66), (16, 0)], yaw=[(0, 0), (5, -40), (8, 50), (10, 54), (16, 0)], roll=[(0, 0), (5, -30), (8, 20), (16, 0)]),
    "torso": ch(yaw=[(0, 0), (2, 34), (5, -36), (8, 34), (10, 30), (16, 0)], pitch=[(0, 0), (5, 8), (8, 8), (16, 0)])})
# 6. Seismic Reach (hit @8) - full-body haymaker, gauntlet stretches and grabs
emote("gauntlet_attack_6", 22, {
    "rightArm": ch(pitch=[(0, 0), (4, 48), (8, -90), (10, -94), (14, -86), (22, 0)],
                   yaw=[(0, 0), (4, 54), (8, -48), (10, -52), (14, -44), (22, 0)],
                   roll=[(0, 0), (4, 24), (8, 0), (22, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -62), (8, 28), (14, 22), (22, 0)], yaw=[(0, 0), (4, -30), (8, 0), (22, 0)], roll=[(0, 0), (8, -22), (22, 0)]),
    "torso": ch(yaw=[(0, 0), (4, -55), (8, 58), (10, 64), (14, 54), (22, 0)], pitch=[(0, 0), (4, -4), (8, 10), (14, 8), (22, 0)]),
    "head": ch(yaw=[(0, 0), (4, 30), (8, -30), (14, -26), (22, 0)])})
# 7. Meteor Smash (hit @12) - leap, hang, crash (legs tuck in the air)
emote("gauntlet_attack_7", 30, {
    "rightArm": ch(pitch=[(0, 0), (3, -170), (9, -180), (12, -12), (13, -6), (18, -14), (30, 0)], roll=[(0, 0), (3, 17), (9, 18), (12, 5), (30, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -170), (9, -180), (12, -12), (13, -6), (18, -14), (30, 0)], roll=[(0, 0), (3, -17), (9, -18), (12, -5), (30, 0)]),
    "torso": ch(pitch=[(0, 0), (3, -20), (9, -26), (12, 44), (13, 50), (18, 38), (30, 0)]),
    "head": ch(pitch=[(0, 0), (9, -12), (12, 18), (18, 14), (30, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (4, -38), (10, -40), (12, -20), (18, -12), (30, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (4, -10), (10, -14), (12, 20), (18, 12), (30, 0)])})

# ---------------------------------------------------------------- spell casts (played through Iron's cast system)
emote("obs_cast_slam", 18, {
    "rightArm": ch(pitch=[(0, 0), (4, -164), (6, -170), (9, -18), (11, -14), (18, 0)], roll=[(0, 0), (4, 16), (9, 2), (18, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -164), (6, -170), (9, -18), (11, -14), (18, 0)], roll=[(0, 0), (4, -16), (9, -2), (18, 0)]),
    "torso": ch(pitch=[(0, 0), (4, -12), (6, -15), (9, 26), (11, 29), (18, 0)])})
emote("obs_cast_thrust", 20, {
    "rightArm": ch(pitch=[(0, 0), (3, 20), (6, -90), (8, -93), (13, -86), (20, 0)], yaw=[(0, 0), (3, 16), (6, 0), (20, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -34), (6, -68), (13, -60), (20, 0)], yaw=[(0, 0), (3, 22), (6, 16), (20, 0)], roll=[(0, 0), (6, -10), (20, 0)]),
    "torso": ch(yaw=[(0, 0), (3, -22), (6, 22), (8, 25), (13, 18), (20, 0)])})
emote("obs_cast_channel", 20, {
    "rightArm": ch(pitch=[(0, -86), (5, -91), (10, -88), (15, -92), (20, -86)], yaw=[(0, -8), (10, -5), (20, -8)]),
    "leftArm": ch(pitch=[(0, -78), (5, -83), (10, -80), (15, -84), (20, -78)], yaw=[(0, 24), (10, 21), (20, 24)]),
    "torso": ch(pitch=[(0, 4), (10, 6), (20, 4)], yaw=[(0, 10), (20, 10)])}, loop=True)
emote("obs_cast_bow_charge", 40, {
    "leftArm": ch(pitch=[(0, 0), (6, -90), (40, -90)], yaw=[(0, 0), (6, 10), (40, 12)]),
    "rightArm": ch(pitch=[(0, 0), (6, -90), (34, -90), (40, -90)], yaw=[(0, 0), (6, -30), (34, -58), (40, -60)]),
    "torso": ch(yaw=[(0, 0), (6, 30), (40, 36)]),
    "head": ch(yaw=[(0, 0), (6, -30), (40, -36)])}, hold=True)
emote("obs_cast_bow_release", 14, {
    "leftArm": ch(pitch=[(0, -90), (2, -96), (14, 0)], yaw=[(0, 12), (14, 0)]),
    "rightArm": ch(pitch=[(0, -90), (2, -80), (14, 0)], yaw=[(0, -60), (2, -92), (14, 0)], roll=[(0, 0), (2, 20), (14, 0)]),
    "torso": ch(yaw=[(0, 36), (14, 0)]),
    "head": ch(yaw=[(0, -36), (14, 0)])})
print("v5 smooth player animations generated")

# =====================================================================================================
# v6: Plasma school - beam + black hole models, lens shader, particles, scroll, icons, data, lang
# =====================================================================================================
# ---- particles
for i in range(4):
    j = 1.0 + 0.12 * math.sin(i * 2.1)
    save(glow_sprite((255, 255, 255), (255, 90, 110), (200, 0, 30), lambda nx, ny, r, j=j: orb_shape(2.2)(nx * j, ny / j, r), i + 40), f"{P}/plasma_spark_{i}.png")
    save(glow_sprite((235, 200, 255), (160, 70, 255), (70, 10, 150), lambda nx, ny, r, k=i: orb_shape(1.1)(nx * (1.3 + 0.2 * k % 2), ny * 0.75, r), i + 50), f"{P}/void_wisp_{i}.png")
for name in ("plasma_spark", "void_wisp"):
    w(f"{A}/particles/{name}.json", {"textures": [f"{MOD}:{name}_{i}" for i in range(4)]})

# ---- plasma beam model: 48 px (3 blocks) along -Z; tapers from the hand to full 8 px (half a block) at 1/3
BEAM_LEN, TAPER, FULL = 48.0, 16.0, 8.0
pb = [bone("root"), bone("beam", (0, 0, 0), None, "root"), bone("core", (0, 0, 0), None, "root")]
outer, inner = [], []
steps = 8
for k in range(steps):                                   # taper: 8 slices across the first third
    z0 = -(k + 1) * TAPER / steps
    wd = 1.0 + (FULL - 1.0) * (k + 1) / steps
    outer.append(cube((-wd / 2, -wd / 2, z0), (wd, wd, TAPER / steps), (0, 0)))
    cw = max(0.4, wd * 0.45)
    inner.append(cube((-cw / 2, -cw / 2, z0 - 0.01), (cw, cw, TAPER / steps + 0.02), (0, 32)))
outer.append(cube((-FULL / 2, -FULL / 2, -BEAM_LEN), (FULL, FULL, BEAM_LEN - TAPER), (0, 0)))   # full-width remainder
inner.append(cube((-FULL * 0.225, -FULL * 0.225, -BEAM_LEN), (FULL * 0.45, FULL * 0.45, BEAM_LEN - TAPER), (0, 32)))
pb[1]["cubes"] = outer
pb[2]["cubes"] = inner
w(f"{G}/entity/plasma_beam.geo.json", geo("plasma_beam", pb, 8))
anim_file("plasma_beam", {
    "ignite": {"animation_length": 0.15, "bones": {"root": {"scale": {"0.0": [0.05, 0.05, 1], "0.15": {"vector": [1, 1, 1], "easing": "easeOutQuad"}}}}},
    "idle": {"animation_length": 0.3, "loop": True, "bones": {
        "beam": {"scale": {"0.0": [1, 1, 1], "0.1": [0.94, 0.94, 1], "0.2": [1.0, 1.0, 1], "0.3": [1, 1, 1]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.15": [1.25, 1.25, 1], "0.3": [1, 1, 1]}}}}})
# animated texture: 4 frames scrolling plasma (outer red rows 0-31, white-hot core rows 32-63)
frames = []
for f_ in range(4):
    n1 = noise(64, 64, 3); n2 = noise(64, 64, 6, 2)
    img = np.zeros((64, 64, 4))
    img[:32, :, 0] = 230 + 25 * n1[:32]; img[:32, :, 1] = 20 + 70 * n2[:32]; img[:32, :, 2] = 30 + 50 * n2[:32]
    img[:32, :, 3] = 150 + 80 * n1[:32]
    img[32:, :, 0] = 255; img[32:, :, 1] = 200 + 55 * n1[32:]; img[32:, :, 2] = 200 + 55 * n2[32:]; img[32:, :, 3] = 255
    frames.append(img)
save(rgba(np.concatenate(frames, 0)), f"{T}/entity/plasma_beam.png")
w(f"{T}/entity/plasma_beam.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})

# ---- black hole model: black core (bevelled sphere) + glowing purple accretion disk + photon ring
def disk(R_in, R_out, y, segs=60, uv=(0, 32)):
    cs, width = [], R_out - R_in
    Rm = (R_in + R_out) / 2
    seg_len = 2 * math.pi * Rm / segs * 1.35
    for i in range(segs):
        th = 2 * math.pi * i / segs
        cx, cz = Rm * math.cos(th), Rm * math.sin(th)
        cs.append(cube((cx - width / 2, y - 0.4, cz - seg_len / 2), (width, 0.8, seg_len), uv, (0, -math.degrees(th), 0), (cx, y, cz)))
    return cs
core = [cube((-7, -7, -7), (14, 14, 14), (0, 0)),
        cube((-7, -7, -7), (14, 14, 14), (0, 0), (45, 0, 0), (0, 0, 0)),
        cube((-7, -7, -7), (14, 14, 14), (0, 0), (0, 45, 0), (0, 0, 0)),
        cube((-7, -7, -7), (14, 14, 14), (0, 0), (0, 0, 45), (0, 0, 0))]
bh = [bone("root", (0, 0, 0)), bone("core", (0, 0, 0), core, "root"),
      bone("disk_tilt", (0, 0, 0), None, "root", (14, 0, 8)),
      bone("disk_inner", (0, 0, 0), disk(17, 26, 0, 56, (0, 32)), "disk_tilt"),
      bone("disk_outer", (0, 0, 0), disk(26, 40, 0, 72, (0, 48)), "disk_tilt"),
      bone("photon_ring", (0, 0, 0), None, "root", (90, 0, 0))]
bh[-1]["cubes"] = disk(14.5, 16.0, 0, 48, (32, 32))
w(f"{G}/entity/black_hole.geo.json", geo("black_hole", bh, 6))
anim_file("black_hole", {"idle": {"animation_length": 4.0, "loop": True, "bones": {
    "disk_inner": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, -360, 0], "2.0": [0, -720, 0], "3.0": [0, -1080, 0], "4.0": [0, -1440, 0]}},
    "disk_outer": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, -360, 0], "4.0": [0, -720, 0]}},
    "photon_ring": {"scale": {"0.0": [1, 1, 1], "1.0": [1.05, 1.05, 1.05], "2.0": [1, 1, 1], "3.0": [1.05, 1.05, 1.05], "4.0": [1, 1, 1]}},
    "core": {"scale": {"0.0": [1, 1, 1], "2.0": [1.03, 1.03, 1.03], "4.0": [1, 1, 1]}}}}})
bt = np.zeros((64, 64, 4))
bt[:32, :, :] = [2, 0, 4, 255]                                     # pitch-black core
n = noise(64, 64, 3)
bt[32:48, :, 0] = 225 + 30 * n[32:48]; bt[32:48, :, 1] = 150 + 90 * n[32:48]; bt[32:48, :, 2] = 255; bt[32:48, :, 3] = 235   # hot inner disk
bt[48:, :, 0] = 120 + 60 * n[48:]; bt[48:, :, 1] = 30 + 40 * n[48:]; bt[48:, :, 2] = 210 + 45 * n[48:]; bt[48:, :, 3] = 150 + 70 * n[48:]  # cooler outer disk
bt[32:48, 32:, :] = [245, 225, 255, 255]                            # white-violet photon ring
save(rgba(bt), f"{T}/entity/black_hole.png")

# ---- gravitational lens core shader
SH = f"{A}/shaders/core"
w(f"{SH}/black_hole_lens.json", {
    "blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
    "vertex": f"{MOD}:black_hole_lens",
    "fragment": f"{MOD}:black_hole_lens",
    "attributes": ["Position", "UV0"],
    "samplers": [{"name": "Sampler0"}],
    "uniforms": [
        {"name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]},
        {"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]},
        {"name": "ScreenSize", "type": "float", "count": 2, "values": [1.0, 1.0]},
        {"name": "GameTime", "type": "float", "count": 1, "values": [0.0]}]})
w(f"{SH}/black_hole_lens.vsh", """#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
}
""", raw=True)
w(f"{SH}/black_hole_lens.fsh", """#version 150

// Gravitational lensing around a black hole.
// Sampler0 = copy of the frame rendered so far; this disc re-samples it with distortion.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;            // -1..1 across the disc
    float r = length(p);
    if (r >= 1.0) discard;

    // how far p moves per screen pixel (works at any distance / FOV / resolution)
    vec2 dpx = vec2(dFdx(p.x), dFdy(p.y));
    if (abs(dpx.x) < 1e-5) dpx.x = 1e-5;
    if (abs(dpx.y) < 1e-5) dpx.y = 1e-5;
    vec2 uv = gl_FragCoord.xy / ScreenSize;

    float secs = GameTime * 1200.0;
    float edge = smoothstep(1.0, 0.35, r);     // distortion fades to zero at the rim -> seamless

    // light bends toward the hole (we look further out than we are) and is dragged around it
    float pull = 0.13 / max(r, 0.08) * edge;
    float swirl = (pow(1.0 - r, 2.0) * 2.6 + secs * 0.35 * pow(1.0 - r, 3.0)) * edge;
    vec2 dir = p / max(r, 1e-4);
    float cs = cos(swirl), sn = sin(swirl);
    vec2 sp = vec2(dir.x * cs - dir.y * sn, dir.x * sn + dir.y * cs) * (r + pull);

    vec2 offsetPx = (sp - p) / dpx;
    vec2 suv = clamp(uv + offsetPx / ScreenSize, vec2(0.001), vec2(0.999));
    vec3 col = texture(Sampler0, suv).rgb;

    // purple glow: tight Einstein ring + soft halo, slowly pulsing
    float pulse = 0.85 + 0.15 * sin(secs * 2.0);
    float ring = exp(-pow((r - 0.255) / 0.028, 2.0));
    float halo = exp(-pow((r - 0.27) / 0.17, 2.0)) * 0.45;
    vec3 purple = vec3(0.66, 0.22, 1.0);
    col = col * (1.0 - 0.3 * halo) + purple * (ring * 1.7 + halo) * pulse;

    // event horizon
    float hole = smoothstep(0.235, 0.205, r);
    col = mix(col, vec3(0.0), hole);

    fragColor = vec4(col, 1.0);
}
""", raw=True)

# ---- scroll + icons
SCROLL_PAL["plasma"] = {"sheet": (205, 196, 200), "roll": (150, 140, 148), "outline": (40, 20, 28), "rune": (255, 40, 60), "rune_hi": (255, 210, 215),
                        "knob": (200, 20, 40), "knob_hi": (255, 120, 130)}
save(draw_scroll(SCROLL_PAL["plasma"], 777), f"{T}/item/scroll_plasma.png")
w(f"{A}/models/item/scroll_plasma.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/scroll_plasma"}})
def icon_plasma_beam():
    im = canvas((70, 8, 14), (8, 1, 3))
    glow(im, lambda d: d.polygon([(4, 60), (64, 8), (64, 22), (10, 64)], fill=(220, 20, 40, 255)), 5, 1.5)
    glow(im, lambda d: d.polygon([(6, 60), (64, 12), (64, 18), (9, 62)], fill=(255, 120, 130, 255)), 2)
    glow(im, lambda d: d.line([(8, 60), (64, 15)], fill=WHT, width=2), 1)
    glow(im, lambda d: d.ellipse([0, 52, 16, 64], fill=(255, 200, 200, 255)), 3, 1.4)
    finish(im, f"{SI}/plasma_beam.png")
def icon_black_hole():
    im = canvas((40, 8, 70), (3, 1, 8))
    glow(im, lambda d: d.ellipse([4, 22, 60, 42], outline=(170, 70, 255, 255), width=5), 3, 1.6)
    glow(im, lambda d: d.ellipse([14, 14, 50, 50], fill=(190, 110, 255, 255)), 5, 1.4)
    solid(im, lambda d: d.ellipse([20, 20, 44, 44], fill=(0, 0, 0, 255)))
    glow(im, lambda d: d.arc([4, 22, 60, 42], 0, 180, fill=(255, 220, 255, 255), width=2), 1)
    finish(im, f"{SI}/black_hole.png")
icon_plasma_beam(); icon_black_hole()

# ---- data
w(f"{D}/damage_type/plasma_magic.json", {"exhaustion": 0.0, "message_id": f"{MOD}.plasma_magic", "scaling": "when_caused_by_living_non_player"})
w(f"{D}/tags/items/plasma_focus.json", {"replace": False, "values": ["minecraft:redstone_block"]})
w(f"{ROOT}/data/irons_spellbooks/tags/items/school_focus.json", {"replace": False, "values": [f"#{MOD}:obsidian_focus", f"#{MOD}:hellfire_focus", f"#{MOD}:plasma_focus"]})

# ---- lang additions
lang_path = f"{A}/lang/en_us.json"
L = json.load(open(lang_path))
L.update({
    f"school.{MOD}.plasma": "Plasma",
    f"attribute.{MOD}.plasma_spell_power": "Plasma Spell Power", f"attribute.{MOD}.plasma_magic_resist": "Plasma Magic Resistance",
    f"death.attack.{MOD}.plasma_magic": "%1$s was vaporised by plasma",
    f"spell.{MOD}.plasma_beam": "Plasma Beam",
    f"spell.{MOD}.plasma_beam.guide": "Channel a searing red plasma beam from your hand to wherever you look. It burns through enemies and melts the block it hits.",
    f"death.attack.{MOD}.plasma_beam": "%1$s was cut in half by %2$s's plasma beam",
    f"death.attack.{MOD}.plasma_beam.item": "%1$s was cut in half by %2$s's plasma beam using %3$s",
    f"spell.{MOD}.black_hole": "Black Hole",
    f"spell.{MOD}.black_hole.guide": "Tear open a glowing purple singularity that bends light around it, drags in everything nearby, crushes what reaches its core and collapses with a shockwave.",
    f"death.attack.{MOD}.black_hole": "%1$s crossed the event horizon of %2$s's black hole",
    f"death.attack.{MOD}.black_hole.item": "%1$s crossed the event horizon of %2$s's black hole using %3$s",
    f"entity.{MOD}.plasma_beam": "Plasma Beam", f"entity.{MOD}.black_hole": "Black Hole", f"entity.{MOD}.hellfire_stream": "Hellfire Stream",
})
w(lang_path, L)
print("v6 plasma school generated")

# =====================================================================================================
# v7: Interstellar-style lens (no spiral) with procedurally lensed accretion disk; Tilt uniform
# =====================================================================================================
w(f"{SH}/black_hole_lens.json", {
    "blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
    "vertex": f"{MOD}:black_hole_lens",
    "fragment": f"{MOD}:black_hole_lens",
    "attributes": ["Position", "UV0"],
    "samplers": [{"name": "Sampler0"}],
    "uniforms": [
        {"name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]},
        {"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]},
        {"name": "ScreenSize", "type": "float", "count": 2, "values": [1.0, 1.0]},
        {"name": "GameTime", "type": "float", "count": 1, "values": [0.0]},
        {"name": "Tilt", "type": "float", "count": 1, "values": [0.1]}]})
w(f"{SH}/black_hole_lens.fsh", """#version 150

// Black hole: gravitational lensing + lensed accretion disk (Interstellar / "Gargantua" look), purple.
//  Sampler0 = copy of the frame so far.  Tilt = sin(camera elevation above the disk plane), signed.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform float Tilt;

in vec2 texCoord0;
out vec4 fragColor;

const float HORIZON = 0.24;      // event horizon radius (disc units)
const float EINSTEIN = 0.40;     // Einstein radius: where the background is stretched into a ring
const vec3 HOT = vec3(1.0, 0.93, 1.0);
const vec3 GLOW = vec3(0.72, 0.30, 1.0);
const vec3 DEEP = vec3(0.35, 0.08, 0.75);

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
// glowing, streaky disk material as a function of radius along the disk and position around it
vec3 diskColor(float rad, float ang, float secs) {
    float t = clamp((rad - 0.30) / 0.62, 0.0, 1.0);                  // 0 inner edge -> 1 outer edge
    float streaks = 0.55 + 0.45 * noise(vec2(rad * 46.0, ang * 3.0 - secs * 1.6));
    streaks *= 0.7 + 0.3 * sin(rad * 120.0 - secs * 3.0);
    vec3 c = mix(HOT, GLOW, smoothstep(0.0, 0.45, t));
    c = mix(c, DEEP, smoothstep(0.45, 1.0, t));
    float bright = (1.6 - 1.3 * t) * streaks;
    return c * bright;
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float r = length(p);
    if (r >= 1.0) discard;

    vec2 dpx = vec2(dFdx(p.x), dFdy(p.y));
    if (abs(dpx.x) < 1e-5) dpx.x = 1e-5;
    if (abs(dpx.y) < 1e-5) dpx.y = 1e-5;
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float secs = GameTime * 1200.0;

    // ---- background lensing: point-mass lens (beta = theta - thetaE^2/theta), faded out at the rim
    float fade = smoothstep(1.0, 0.55, r);
    float beta = r - fade * (EINSTEIN * EINSTEIN) / max(r, 0.05);
    vec2 dir = p / max(r, 1e-4);
    vec2 sp = dir * beta;                                            // beta < 0 -> mirrored image inside the ring
    vec2 suv = clamp(uv + ((sp - p) / dpx) / ScreenSize, vec2(0.001), vec2(0.999));
    vec3 col = texture(Sampler0, suv).rgb;

    // ---- accretion disk (horizontal plane), seen with the camera's elevation
    float tilt = clamp(abs(Tilt), 0.10, 1.0);
    float above = Tilt >= 0.0 ? 1.0 : -1.0;
    float ang = atan(p.y, p.x);
    // direct image of the disk: an ellipse squashed by the viewing angle
    vec2 q = vec2(p.x, p.y / tilt);
    float dr = length(q);
    float diskMask = smoothstep(0.30, 0.34, dr) * smoothstep(0.95, 0.80, dr);
    float frontHalf = (p.y * above < 0.0) ? 1.0 : 0.0;               // near side passes in front of the hole
    vec3 disk = diskColor(dr, atan(q.y, q.x), secs) * diskMask;

    // lensed far side: arches over the top of the horizon (under it when seen from below)
    float arcR = mix(0.27, 0.40, (1.0 - tilt));
    float arcBand = exp(-pow((r - arcR) / (0.065 + 0.075 * (1.0 - tilt)), 2.0));
    float overTop = smoothstep(-0.15, 0.25, p.y * above);
    vec3 arc = diskColor(0.32 + (r - arcR) * 2.0 + 0.1, ang, secs) * arcBand * overTop * (1.45 - 0.55 * tilt);
    // secondary image hugging the bottom of the horizon
    float lowBand = exp(-pow((r - 0.265) / 0.022, 2.0)) * smoothstep(0.1, -0.3, p.y * above);
    vec3 low = HOT * lowBand * 0.9;
    // photon ring
    float photon = exp(-pow((r - 0.252) / 0.012, 2.0));

    vec3 glowHalo = GLOW * exp(-pow((r - 0.3) / 0.22, 2.0)) * 0.35;

    // compose: background -> far-side arc -> horizon -> near side of the disk on top
    col += arc + low + HOT * photon * 1.4 + glowHalo;
    float hole = smoothstep(HORIZON, HORIZON - 0.02, r);
    col = mix(col, vec3(0.0), hole);
    // the disk occludes the horizon on its near side, sits behind it on the far side
    float diskVis = diskMask * (frontHalf + (1.0 - frontHalf) * (1.0 - hole));
    col += disk * diskVis;

    fragColor = vec4(col, 1.0);
}
""", raw=True)
print("v7 lens shader generated")

# =====================================================================================================
# v8: rounded plasma beam + fast red/white streak texture; whole-body gauntlet animations; push cast
# =====================================================================================================
def round_slice(z0, length, wd, uv, pivot_z):
    """Three boxes rotated 0/30/60 deg around the beam axis -> 12-sided, visually round cross-section."""
    return [cube((-wd / 2, -wd / 2, z0), (wd, wd, length), uv, (0, 0, a), (0, 0, pivot_z)) for a in (0, 30, 60)]
outer, inner = [], []
steps = 8
for k in range(steps):
    z0 = -(k + 1) * TAPER / steps
    wd = 1.0 + (FULL - 1.0) * (k + 1) / steps
    outer += round_slice(z0, TAPER / steps, wd * 0.94, (0, 0), z0 + TAPER / steps / 2)
    inner += round_slice(z0 - 0.01, TAPER / steps + 0.02, max(0.4, wd * 0.42), (0, 32), z0 + TAPER / steps / 2)
outer += round_slice(-BEAM_LEN, BEAM_LEN - TAPER, FULL * 0.94, (0, 0), -(BEAM_LEN + TAPER) / 2)
inner += round_slice(-BEAM_LEN, BEAM_LEN - TAPER, FULL * 0.42, (0, 32), -(BEAM_LEN + TAPER) / 2)
# hemispherical-ish cap at the far end
outer += round_slice(-BEAM_LEN - 1.2, 1.2, FULL * 0.7, (0, 0), -BEAM_LEN - 0.6)
pb = [bone("root"), bone("beam", (0, 0, 0), outer, "root"), bone("core", (0, 0, 0), inner, "root")]
w(f"{G}/entity/plasma_beam.geo.json", geo("plasma_beam", pb, 8))
anim_file("plasma_beam", {
    "ignite": {"animation_length": 0.15, "bones": {"root": {"scale": {"0.0": [0.05, 0.05, 1], "0.15": {"vector": [1, 1, 1], "easing": "easeOutQuad"}}}}},
    "idle": {"animation_length": 0.2, "loop": True, "bones": {
        "beam": {"scale": {"0.0": [1, 1, 1], "0.05": [0.95, 0.95, 1], "0.1": [1.02, 1.02, 1], "0.15": [0.97, 0.97, 1], "0.2": [1, 1, 1]},
                 "rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, 30]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.1": [1.3, 1.3, 1], "0.2": [1, 1, 1]},
                 "rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, -30]}}}}})
# fast streaks: each frame shifts the streak field 9 px along U (U runs along the beam on the side faces)
srng = np.random.default_rng(5)
base = np.zeros((64, 64, 4))
base[..., 0] = 225; base[..., 1] = 18; base[..., 2] = 32; base[..., 3] = 190
streak = np.zeros((64, 64))
for _ in range(70):
    yy = srng.integers(0, 64); xx = srng.integers(0, 64); ln = srng.integers(6, 26); b = srng.uniform(0.5, 1.0)
    for k in range(ln):
        streak[yy, (xx + k) % 64] = max(streak[yy, (xx + k) % 64], b * (1 - k / ln) ** 0.5)
streak_core = np.zeros((64, 64))
for _ in range(90):
    yy = srng.integers(32, 64); xx = srng.integers(0, 64); ln = srng.integers(8, 30)
    for k in range(ln): streak_core[yy, (xx + k) % 64] = 1.0
frames = []
for f_ in range(8):
    s_ = np.roll(streak, f_ * 9, axis=1); sc = np.roll(streak_core, f_ * 13, axis=1)
    img = base.copy()
    img[..., 0] = np.clip(225 + 30 * s_, 0, 255)
    img[..., 1] = np.clip(18 + 230 * s_ ** 1.5, 0, 255)
    img[..., 2] = np.clip(32 + 220 * s_ ** 1.5, 0, 255)
    img[..., 3] = np.clip(170 + 85 * s_, 0, 255)
    core = img[32:].copy()
    core[..., 0] = 255; core[..., 1] = np.clip(175 + 80 * sc[32:], 0, 255); core[..., 2] = np.clip(185 + 70 * sc[32:], 0, 255); core[..., 3] = 255
    img[32:] = core
    frames.append(img)
save(rgba(np.concatenate(frames, 0)), f"{T}/entity/plasma_beam.png")
w(f"{T}/entity/plasma_beam.png.mcmeta", {"animation": {"frametime": 1, "interpolate": False}})

# ---------------------------------------------------------------- whole-body gauntlet combo
# "body" = whole player (hips): its yaw/pitch use the opposite sign convention to torso in PlayerAnimator's
# emote format, so a twist that matches torso yaw +X is body yaw -X.
emote("gauntlet_attack_1", 15, {   # Obsidian Jab: step in, hips drive the straight right
    "body": ch(yaw=[(0, 0), (2, 10), (4, -16), (6, -18), (10, -10), (15, 0)], pitch=[(0, 0), (4, -6), (8, -4), (15, 0)]),
    "torso": ch(yaw=[(0, 0), (2, -14), (4, 14), (6, 16), (10, 10), (15, 0)]),
    "rightArm": ch(pitch=[(0, 0), (2, 22), (4, -92), (6, -96), (9, -84), (15, 0)], yaw=[(0, 0), (2, 16), (4, -4), (9, -2), (15, 0)], roll=[(0, 0), (2, 6), (4, 0), (15, 0)]),
    "leftArm": ch(pitch=[(0, 0), (2, -52), (9, -50), (15, 0)], yaw=[(0, 0), (2, 26), (9, 24), (15, 0)], roll=[(0, 0), (2, -14), (15, 0)]),
    "head": ch(yaw=[(0, 0), (2, 6), (4, -2), (15, 0)], pitch=[(0, 0), (4, 6), (15, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (2, -8), (4, -28), (6, -30), (10, -22), (15, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (2, 6), (4, 22), (6, 24), (10, 16), (15, 0)])})
emote("gauntlet_attack_2", 15, {   # Rending Hook: pivot on the front foot, whole body corkscrews
    "body": ch(yaw=[(0, 0), (2, -22), (4, 26), (6, 30), (10, 18), (15, 0)], roll=[(0, 0), (2, 4), (4, -5), (15, 0)]),
    "torso": ch(yaw=[(0, 0), (2, 16), (4, -18), (6, -22), (10, -14), (15, 0)], pitch=[(0, 0), (4, 6), (15, 0)]),
    "leftArm": ch(pitch=[(0, 0), (2, -64), (4, -88), (6, -84), (9, -70), (15, 0)], yaw=[(0, 0), (2, -28), (4, 46), (6, 56), (9, 40), (15, 0)], roll=[(0, 0), (2, 46), (4, -24), (6, -30), (15, 0)]),
    "rightArm": ch(pitch=[(0, 0), (2, -60), (10, -55), (15, 0)], yaw=[(0, 0), (2, -20), (15, 0)], roll=[(0, 0), (2, 12), (15, 0)]),
    "head": ch(yaw=[(0, 0), (2, -6), (4, 6), (15, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (2, -18), (6, -20), (15, 0)], roll=[(0, 0), (4, 6), (15, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (2, 14), (6, 18), (15, 0)], yaw=[(0, 0), (4, -12), (15, 0)])})
emote("gauntlet_attack_3", 19, {   # Piercing Lance: deep fencing lunge behind the stretched gauntlet
    "body": ch(yaw=[(0, 0), (3, 24), (6, -26), (8, -30), (12, -24), (19, 0)], pitch=[(0, 0), (3, 4), (6, -16), (8, -19), (12, -14), (19, 0)]),
    "torso": ch(yaw=[(0, 0), (3, -18), (6, 16), (8, 18), (12, 14), (19, 0)], pitch=[(0, 0), (6, 8), (12, 6), (19, 0)]),
    "rightArm": ch(pitch=[(0, 0), (3, 36), (6, -92), (8, -96), (12, -88), (19, 0)], yaw=[(0, 0), (3, 22), (6, -2), (19, 0)], roll=[(0, 0), (3, 10), (6, 0), (19, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -40), (6, 30), (12, 24), (19, 0)], roll=[(0, 0), (6, -30), (19, 0)]),
    "head": ch(yaw=[(0, 0), (3, 10), (6, -8), (19, 0)], pitch=[(0, 0), (6, 10), (19, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (3, 10), (6, -42), (8, -46), (12, -38), (19, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (3, -8), (6, 34), (8, 38), (12, 30), (19, 0)])})
emote("gauntlet_attack_4", 20, {   # Hammer Slam: rise on the toes, then the whole body folds into the strike
    "body": ch(pitch=[(0, 0), (4, 10), (6, 13), (8, -28), (9, -32), (13, -24), (20, 0)]),
    "torso": ch(pitch=[(0, 0), (4, -10), (6, -12), (8, 14), (9, 16), (13, 12), (20, 0)]),
    "rightArm": ch(pitch=[(0, 0), (4, -168), (6, -176), (8, -16), (9, -10), (13, -18), (20, 0)], roll=[(0, 0), (4, 16), (8, 4), (20, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -168), (6, -176), (8, -14), (9, -8), (13, -16), (20, 0)], roll=[(0, 0), (4, -16), (8, -4), (20, 0)]),
    "head": ch(pitch=[(0, 0), (6, -12), (8, 12), (13, 8), (20, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (4, -6), (8, 30), (9, 34), (13, 26), (20, 0)], roll=[(0, 0), (8, 8), (20, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (4, -6), (8, 30), (9, 34), (13, 26), (20, 0)], roll=[(0, 0), (8, -8), (20, 0)])})
emote("gauntlet_attack_5", 16, {   # Claw Rake: hips whip one way, then back the other
    "body": ch(yaw=[(0, 0), (2, -22), (5, 24), (8, -22), (10, -18), (16, 0)], pitch=[(0, 0), (5, -6), (8, -6), (16, 0)]),
    "torso": ch(yaw=[(0, 0), (2, 14), (5, -16), (8, 16), (10, 12), (16, 0)]),
    "rightArm": ch(pitch=[(0, 0), (2, -122), (5, -72), (7, -66), (16, 0)], yaw=[(0, 0), (2, 50), (5, -50), (7, -56), (16, 0)], roll=[(0, 0), (2, 30), (5, -20), (16, 0)]),
    "leftArm": ch(pitch=[(0, 0), (3, -60), (5, -114), (8, -72), (10, -66), (16, 0)], yaw=[(0, 0), (5, -40), (8, 50), (10, 54), (16, 0)], roll=[(0, 0), (5, -30), (8, 20), (16, 0)]),
    "head": ch(yaw=[(0, 0), (2, 8), (5, -8), (8, 8), (16, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (2, 10), (5, -20), (8, 14), (16, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (2, -10), (5, 18), (8, -16), (16, 0)])})
emote("gauntlet_attack_6", 22, {   # Seismic Reach: huge coil, then everything unwinds behind the throw
    "body": ch(yaw=[(0, 0), (4, 34), (8, -40), (10, -44), (14, -36), (22, 0)], pitch=[(0, 0), (4, 4), (8, -10), (14, -8), (22, 0)], roll=[(0, 0), (4, -5), (8, 6), (22, 0)]),
    "torso": ch(yaw=[(0, 0), (4, -24), (8, 22), (10, 24), (14, 20), (22, 0)], pitch=[(0, 0), (8, 6), (22, 0)]),
    "rightArm": ch(pitch=[(0, 0), (4, 50), (8, -90), (10, -94), (14, -86), (22, 0)], yaw=[(0, 0), (4, 56), (8, -48), (10, -52), (14, -44), (22, 0)], roll=[(0, 0), (4, 24), (8, 0), (22, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -64), (8, 30), (14, 24), (22, 0)], yaw=[(0, 0), (4, -30), (8, 0), (22, 0)], roll=[(0, 0), (8, -24), (22, 0)]),
    "head": ch(yaw=[(0, 0), (4, 14), (8, -14), (14, -10), (22, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (4, 22), (8, -26), (10, -30), (14, -24), (22, 0)], yaw=[(0, 0), (8, 10), (22, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (4, -24), (8, 30), (10, 34), (14, 28), (22, 0)])})
emote("gauntlet_attack_7", 30, {   # Meteor Smash: crouch-spring, arch in the air, crash and absorb
    "body": ch(pitch=[(0, 0), (2, -10), (5, 14), (9, 18), (12, -34), (13, -38), (18, -28), (30, 0)]),
    "torso": ch(pitch=[(0, 0), (2, 8), (5, -16), (9, -20), (12, 18), (13, 20), (18, 14), (30, 0)]),
    "rightArm": ch(pitch=[(0, 0), (2, -40), (5, -172), (9, -180), (12, -12), (13, -6), (18, -14), (30, 0)], roll=[(0, 0), (5, 17), (9, 18), (12, 5), (30, 0)]),
    "leftArm": ch(pitch=[(0, 0), (2, -40), (5, -172), (9, -180), (12, -12), (13, -6), (18, -14), (30, 0)], roll=[(0, 0), (5, -17), (9, -18), (12, -5), (30, 0)]),
    "head": ch(pitch=[(0, 0), (5, -14), (9, -16), (12, 16), (18, 12), (30, 0)]),
    "rightLeg": ch(pitch=[(0, 0), (2, -22), (5, -46), (9, -50), (12, 26), (13, 30), (18, 22), (30, 0)], roll=[(0, 0), (12, 8), (30, 0)]),
    "leftLeg": ch(pitch=[(0, 0), (2, -22), (5, 18), (9, 14), (12, 26), (13, 30), (18, 22), (30, 0)], roll=[(0, 0), (12, -8), (30, 0)])})
# Black Hole: cup both hands at the chest while it charges (12 ticks), then shove it forward
emote("obs_cast_push", 24, {
    "rightArm": ch(pitch=[(0, 0), (4, -70), (11, -74), (13, -96), (16, -92), (24, 0)], yaw=[(0, 0), (4, 28), (11, 30), (13, 6), (24, 0)]),
    "leftArm": ch(pitch=[(0, 0), (4, -70), (11, -74), (13, -96), (16, -92), (24, 0)], yaw=[(0, 0), (4, -28), (11, -30), (13, -6), (24, 0)]),
    "torso": ch(pitch=[(0, 0), (4, -6), (11, -8), (13, 10), (16, 8), (24, 0)]),
    "body": ch(pitch=[(0, 0), (11, 4), (13, -8), (16, -6), (24, 0)]),
    "head": ch(pitch=[(0, 0), (11, 4), (13, -2), (24, 0)])})
print("v8 beam + whole-body animations generated")

# =====================================================================================================
# v9: GeckoLib armor - obsidian spines only (no plating). JSON/Blockbench space: +Y up, FRONT = -Z,
#     player's RIGHT = -X (same conventions as GeckoLib's own example armor).
# =====================================================================================================
SPINE_UV = [(0, 0), (16, 0), (32, 0), (48, 0)]            # base -> tip tiers (tip tier glows)
AXES = {"+x": (1, 0, 0), "-x": (-1, 0, 0), "+y": (0, 1, 0), "+z": (0, 0, 1), "-z": (0, 0, -1)}
def spine(name, parent, base, axis, length, width, sweep=0.0):
    """Tapered 4-tier spine growing straight out of the body along `axis`, with a small sweep (tilt) applied."""
    ax = AXES[axis]
    cubes, t = [], 0.0
    tiers = [(1.0, 0.34), (0.72, 0.28), (0.48, 0.22), (0.24, 0.16)]
    for k, (wf, lf) in enumerate(tiers):
        L = length * lf; wd = max(0.35, width * wf)
        # cube centred on the axis, starting t along it
        o = [base[i] - wd / 2 if ax[i] == 0 else (base[i] + t if ax[i] > 0 else base[i] - t - L) for i in range(3)]
        s = [wd if ax[i] == 0 else L for i in range(3)]
        cubes.append(cube(o, s, SPINE_UV[k]))
        t += L * 0.92
    rot = (0, 0, 0)
    if sweep:
        if axis in ("+z", "-z"): rot = (sweep * (1 if axis == "+z" else -1), 0, 0)
        elif axis in ("+x", "-x"): rot = (0, 0, sweep * (1 if axis == "-x" else -1))
        else: rot = (sweep, 0, 0)
    return bone(name, base, cubes, parent, rot if sweep else None)

ar = [bone("bipedHead", (0, 24, 0)), bone("armorHead", (0, 24, 0), None, "bipedHead"),
      bone("bipedBody", (0, 24, 0)), bone("armorBody", (0, 24, 0), None, "bipedBody"),
      bone("bipedRightArm", (-5, 22, 0)), bone("armorRightArm", (-5, 22, 0), None, "bipedRightArm"),
      bone("bipedLeftArm", (5, 22, 0)), bone("armorLeftArm", (5, 22, 0), None, "bipedLeftArm"),
      bone("bipedRightLeg", (-1.9, 12, 0)), bone("armorRightLeg", (-1.9, 12, 0), None, "bipedRightLeg"),
      bone("armorRightBoot", (-1.9, 12, 0), None, "bipedRightLeg"),
      bone("bipedLeftLeg", (1.9, 12, 0)), bone("armorLeftLeg", (1.9, 12, 0), None, "bipedLeftLeg"),
      bone("armorLeftBoot", (1.9, 12, 0), None, "bipedLeftLeg")]
S = []
# ---- helmet: swept-back crest + skull spines + temple horns
for i, (z, L) in enumerate(((-2.6, 2.2), (-0.9, 3.2), (0.8, 3.8), (2.5, 3.2))):
    S.append(spine(f"crest_{i}", "armorHead", (0, 32, z), "+y", L, 1.5, sweep=-14 - 6 * i))
for sx in (-1, 1):
    S.append(spine(f"skull_{'r' if sx < 0 else 'l'}", "armorHead", (2.2 * sx, 29.5, 4), "+z", 2.6, 1.3, sweep=12))
    S.append(spine(f"temple_{'r' if sx < 0 else 'l'}", "armorHead", (4 * sx, 30.0, -1.5), "+x" if sx > 0 else "-x", 2.4, 1.2, sweep=24))
# ---- chestplate: dorsal ridge down the spine, shoulder-blade spines, collarbone studs, rib spurs
for i, (y, L) in enumerate(((22.5, 3.6), (20.0, 4.4), (17.5, 4.0), (15.0, 3.0), (13.0, 2.0))):
    S.append(spine(f"dorsal_{i}", "armorBody", (0, y, 2), "+z", L, 1.6, sweep=22))
for sx in (-1, 1):
    side = 'r' if sx < 0 else 'l'
    S.append(spine(f"blade_{side}", "armorBody", (2.6 * sx, 21.0, 2), "+z", 2.8, 1.2, sweep=30))
    S.append(spine(f"collar_{side}", "armorBody", (2.2 * sx, 22.2, -2), "-z", 1.5, 1.0, sweep=20))
    S.append(spine(f"rib_{side}", "armorBody", (4 * sx, 17.0, 0.5), "+x" if sx > 0 else "-x", 1.6, 1.0, sweep=15))
# ---- chestplate arms: pauldron spines, elbow spur, forearm fin
for arm, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
    side = 'r' if sx < 0 else 'l'
    out = "+x" if sx > 0 else "-x"
    S.append(spine(f"pauldron_{side}", arm, (8 * sx, 22.6, 0), out, 3.2, 1.5, sweep=32))
    S.append(spine(f"shoulder_{side}", arm, (6 * sx, 24, 0.5), "+y", 2.6, 1.3, sweep=-10))
    S.append(spine(f"elbow_{side}", arm, (6 * sx, 17.0, 2), "+z", 2.6, 1.2, sweep=-25))
    S.append(spine(f"forearm_{side}", arm, (8 * sx, 14.5, 0), out, 1.6, 0.9, sweep=-15))
# ---- leggings: hip spurs, knee spike, hamstring spine
for leg, sx in (("armorRightLeg", -1), ("armorLeftLeg", 1)):
    side = 'r' if sx < 0 else 'l'
    S.append(spine(f"hip_{side}", leg, (4 * sx, 10.5, 0), "+x" if sx > 0 else "-x", 2.2, 1.2, sweep=28))
    S.append(spine(f"knee_{side}", leg, (2 * sx, 6.5, -2), "-z", 2.0, 1.2, sweep=18))
    S.append(spine(f"thigh_{side}", leg, (2 * sx, 9.0, 2), "+z", 1.6, 0.9, sweep=10))
# ---- boots: heel spur, ankle spikes
for boot, sx in (("armorRightBoot", -1), ("armorLeftBoot", 1)):
    side = 'r' if sx < 0 else 'l'
    S.append(spine(f"heel_{side}", boot, (2 * sx, 1.5, 2), "+z", 2.6, 1.2, sweep=-8))
    S.append(spine(f"ankle_{side}", boot, (4 * sx, 2.6, 0), "+x" if sx > 0 else "-x", 1.5, 0.9, sweep=20))
ar += S
w(f"{G}/item/armor/obsidian_armor.geo.json", geo("obsidian_armor", ar, 4))
# subtle "breathing" of the spines so they feel alive
breathe = {}
for b in S:
    breathe[b["name"]] = {"scale": {"0.0": [1, 1, 1], "1.5": [1.06, 1.06, 1.06], "3.0": [1, 1, 1]}}
w(f"{AN}/item/armor/obsidian_armor.animation.json", {"format_version": "1.8.0", "animations": {
    "animation.obsidian_armor.idle": {"animation_length": 3.0, "loop": True, "bones": breathe}}})
# texture: glossy obsidian tiers, purple-glowing tips
at = np.zeros((64, 64, 4)); ag = np.zeros((64, 64, 4))
n = noise(64, 64, 2)
for k in range(4):
    x0 = k * 16
    shade = 1.0 + k * 0.12
    at[:, x0:x0 + 16, 0] = (16 + 34 * n[:, x0:x0 + 16]) * shade
    at[:, x0:x0 + 16, 1] = (10 + 18 * n[:, x0:x0 + 16]) * shade
    at[:, x0:x0 + 16, 2] = (30 + 60 * n[:, x0:x0 + 16]) * shade
    at[:, x0:x0 + 16, 3] = 255
vein = np.abs(noise(64, 64, 4, 2) - 0.5) < 0.05
at[vein] = [110, 40, 180, 255]
at[:, 48:64, :3] = [170, 80, 255]; ag[:, 48:64] = [215, 140, 255, 255]          # glowing tips
at[:, 32:48][vein[:, 32:48]] = [190, 100, 255, 255]; ag[:, 32:48][vein[:, 32:48]] = [190, 100, 255, 255]
save(rgba(at), f"{T}/item/armor/obsidian_armor.png")
save(rgba(ag), f"{T}/item/armor/obsidian_armor_glowmask.png")
print("v9 spine armor generated:", len(S), "spines")

# =====================================================================================================
# v10: gauntlet remodel - layered lames on the bracer, crystal ridge shards, articulated fingers,
#      wrapped thumb and per-finger knuckle spikes. Same bone names/pivots so all animations still apply.
# =====================================================================================================
gl = [bone("root", (0, 0, 0), None, None, (0, 90, 0))]   # quarter-turn about the arm: knuckle row runs vertically
bracer = [
    cube((-2.6, -0.2, -2.6), (5.2, 1.0, 5.2), UV_TRIM),            # wrist band
    cube((-2.55, 0.7, -2.55), (5.1, 1.5, 5.1), UV_PLATE),          # overlapping lames, widening toward the elbow
    cube((-2.72, 2.0, -2.72), (5.44, 1.5, 5.44), UV_PLATE),
    cube((-2.9, 3.3, -2.9), (5.8, 1.5, 5.8), UV_PLATE),
    cube((-3.1, 4.6, -3.1), (6.2, 1.2, 6.2), UV_TRIM),             # elbow cuff
    cube((-0.6, 2.3, -3.05), (1.2, 1.0, 0.4), UV_CLAW),            # glowing core gems front / back
    cube((-0.6, 2.3, 2.65), (1.2, 1.0, 0.4), UV_CLAW),
    cube((2.65, 0.9, -0.55), (0.7, 3.6, 1.1), UV_SPIKE),           # side fins along the lames
    cube((-3.35, 0.9, -0.55), (0.7, 3.6, 1.1), UV_SPIKE),
]
for zs in (-1, 1):   # crystal ridge: three shards climbing toward the elbow on each face
    for i, (y, h) in enumerate(((1.0, 1.4), (2.3, 1.9), (3.6, 2.4))):
        z0 = 2.7 if zs > 0 else -3.5
        bracer.append(cube((-0.4, y, z0), (0.8, h, 0.8), UV_SPIKE, (-22 * zs, 0, 0), (0, y, 3.1 * zs)))
gl.append(bone("bracer", (0, 0, 0), bracer, "root"))
gl.append(bone("fin_spikes", (0, 4.4, 0), [
    cube((3.0, 4.2, -0.45), (1.0, 2.8, 0.9), UV_SPIKE, (0, 0, -28), (3.4, 4.4, 0)),
    cube((3.2, 6.6, -0.3), (0.6, 1.4, 0.6), UV_CLAW, (0, 0, -28), (3.4, 4.4, 0)),
    cube((-4.0, 4.2, -0.45), (1.0, 2.8, 0.9), UV_SPIKE, (0, 0, 28), (-3.4, 4.4, 0)),
    cube((-3.8, 6.6, -0.3), (0.6, 1.4, 0.6), UV_CLAW, (0, 0, 28), (-3.4, 4.4, 0))], "bracer"))
gl.append(bone("extender", (0, 0, 0), [cube((-1.6, -1.0, -1.6), (3.2, 1.0, 3.2), UV_TRIM), cube((-2.1, -0.7, -0.5), (4.2, 0.4, 1.0), UV_SPIKE)], "root"))
FX = (-1.75, -0.58, 0.58, 1.75)
fist = [
    cube((-2.3, -3.3, -2.2), (4.6, 3.3, 4.4), UV_FIST),            # hand core
    cube((-2.5, -2.9, -2.65), (5.0, 2.4, 0.6), UV_PLATE),          # back-of-hand plate
    cube((-2.5, -2.9, 2.05), (5.0, 2.4, 0.6), UV_PLATE),           # palm guard
    cube((-3.05, -3.0, -1.2), (0.8, 2.4, 2.4), UV_PLATE),          # thumb, wrapped across the fingers
    cube((-2.95, -4.1, -1.0), (1.0, 1.2, 2.0), UV_KNUCK),
]
for x in FX:   # curled fingers: proximal + middle segments, small gaps between them
    fist.append(cube((x - 0.52, -4.2, -2.1), (1.04, 1.0, 4.2), UV_KNUCK))
    fist.append(cube((x - 0.48, -4.75, -1.6), (0.96, 0.6, 3.2), UV_KNUCK))
gl.append(bone("fist", (0, 0, 0), fist, "root"))
knucks = []
for x in FX:
    knucks.append(cube((x - 0.36, -5.5, -0.36), (0.72, 0.8, 0.72), UV_SPIKE))
    knucks.append(cube((x - 0.2, -6.1, -0.2), (0.4, 0.65, 0.4), UV_CLAW))
gl.append(bone("knuckles", (0, -4.5, 0), knucks, "fist"))
claws = []
for x in FX:
    claws.append(cube((x - 0.3, -7.6, -0.3), (0.6, 3.2, 0.6), UV_CLAW))
    claws.append(cube((x - 0.18, -9.5, -0.18), (0.36, 1.95, 0.36), UV_CLAW))
claws.append(cube((-2.75, -6.8, -0.25), (0.5, 2.4, 0.5), UV_CLAW))   # thumb talon
gl.append(bone("claws", (0, -4.4, 0), claws, "fist"))
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", gl, 4))
print("v10 gauntlet remodel:", sum(len(b.get("cubes", [])) for b in gl), "cubes")

# =====================================================================================================
# v11: recipe-unlock advancements so the gauntlet / armor recipes show up in the vanilla recipe book
#      as soon as the player picks up obsidian (crafting itself never needed this, the book does).
# =====================================================================================================
for name in ("obsidian_gauntlet", "obsidian_helmet", "obsidian_chestplate", "obsidian_leggings", "obsidian_boots"):
    rid = f"{MOD}:{name}"
    w(f"{D}/advancements/recipes/equipment/{name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_obsidian": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ["minecraft:obsidian", "minecraft:crying_obsidian"]}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": rid}}},
        "requirements": [["has_obsidian", "has_the_recipe"]],
        "rewards": {"recipes": [rid]}})
print("v11 recipe advancements")

# v12: lang for the gauntlet grappling hook
_l = json.load(open(f"{A}/lang/en_us.json"))
_l[f"tooltip.{MOD}.gauntlet.grapple"] = "Right-click: grappling hook (80 blocks), right-click again to let go"
_l[f"entity.{MOD}.grapple_hook"] = "Grappling Hook"
w(f"{A}/lang/en_us.json", _l)

# =====================================================================================================
# v13: grappling hook 3D model - obsidian claw (rendered with the vanilla obsidian texture, 16x16 box UV).
#      Tip at the origin pointing -Z (the direction of travel); shaft + eyelet run back along +Z to the chain.
# =====================================================================================================
hk = [bone("root", (0, 0, 0))]
hook_cubes = [
    cube((-0.75, -0.75, -1.4), (1.5, 1.5, 2.2), (0, 0), (0, 0, 45), (0, 0, 0)),     # diamond spear point
    cube((-1.3, -1.3, 0.6), (2.6, 2.6, 2.4), (0, 0)),                                 # head
    cube((-0.75, -0.75, 3.0), (1.5, 1.5, 4.6), (0, 0)),                               # shaft
    cube((-1.1, -1.1, 7.6), (2.2, 2.2, 0.8), (0, 0)),                                 # eyelet the chain hooks into
]
hk.append(bone("hook", (0, 0, 0), hook_cubes, "root"))
# four barbed prongs flaring backwards off the head (file-space rotations: +x prong y+, +y prong x+)
prongs = [
    cube((1.0, -0.5, 1.2), (1.0, 1.0, 4.2), (0, 0), (0, 32, 0), (1.5, 0, 1.2)),
    cube((-2.0, -0.5, 1.2), (1.0, 1.0, 4.2), (0, 0), (0, -32, 0), (-1.5, 0, 1.2)),
    cube((-0.5, 1.0, 1.2), (1.0, 1.0, 4.2), (0, 0), (32, 0, 0), (0, 1.5, 1.2)),
    cube((-0.5, -2.0, 1.2), (1.0, 1.0, 4.2), (0, 0), (-32, 0, 0), (0, -1.5, 1.2)),
]
hk.append(bone("prongs", (0, 0, 1.2), prongs, "hook"))
_g = geo("grapple_hook", hk, 2)
_g["minecraft:geometry"][0]["description"]["texture_width"] = 16
_g["minecraft:geometry"][0]["description"]["texture_height"] = 16
w(f"{G}/entity/grapple_hook.geo.json", _g)
w(f"{AN}/entity/grapple_hook.animation.json", {"format_version": "1.8.0", "animations": {
    "animation.grapple_hook.idle": {"animation_length": 1.0, "loop": True, "bones": {}}}})
print("v13 grapple hook model")

# =====================================================================================================
# v14: smooth meshes. Armor spines and the whole gauntlet are drawn as smooth procedural meshes (curved needle
#      thorns, rounded bands, capsules, soft plates) by client/mesh/SmoothPartsLayer, attached to the GeckoLib bones.
#      The geo files keep only the (cube-less) bones so animations and armor slot visibility still drive them.
# =====================================================================================================
def _rot(r):
    # Blockbench rotation (deg) -> effective file-space rotation matrix (same convention GeckoLib bakes)
    a, b, c = np.radians([-r[0], r[1], -r[2]])
    Rx = np.array([[1, 0, 0], [0, np.cos(a), -np.sin(a)], [0, np.sin(a), np.cos(a)]])
    Ry = np.array([[np.cos(b), 0, np.sin(b)], [0, 1, 0], [-np.sin(b), 0, np.cos(b)]])
    Rz = np.array([[np.cos(c), -np.sin(c), 0], [np.sin(c), np.cos(c), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def thorn(base, axis, length, width, sweep=0.0, glow=0.3, curl=0.55):
    ax = np.array(AXES[axis], float)
    rot = (0, 0, 0)
    if sweep:
        if axis in ("+z", "-z"): rot = (sweep * (1 if axis == "+z" else -1), 0, 0)
        elif axis in ("+x", "-x"): rot = (0, 0, sweep * (1 if axis == "-x" else -1))
        else: rot = (sweep, 0, 0)
    R = _rot(rot)
    d = R @ ax
    d2 = R @ R @ ax                                   # continuing the sweep gives the thorn its curve
    bend = (d2 - d) * length * curl
    return {"type": "thorn", "a": [round(v, 3) for v in base], "b": [round(v, 4) for v in d], "c": [round(v, 3) for v in bend],
            "len": round(length * 1.25, 3), "r": round(width * 0.62, 3), "glow": glow}

A9 = {k: [] for k in ("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot")}
for i, (z, L) in enumerate(((-2.6, 2.2), (-0.9, 3.2), (0.8, 3.8), (2.5, 3.2))):
    A9["armorHead"].append(thorn((0, 32, z), "+y", L, 1.5, -14 - 6 * i))
for sx in (-1, 1):
    A9["armorHead"].append(thorn((2.2 * sx, 29.5, 4), "+z", 2.6, 1.3, 12))
    A9["armorHead"].append(thorn((4 * sx, 30.0, -1.5), "+x" if sx > 0 else "-x", 2.4, 1.2, 24))
for i, (y, L) in enumerate(((22.5, 3.6), (20.0, 4.4), (17.5, 4.0), (15.0, 3.0), (13.0, 2.0))):
    A9["armorBody"].append(thorn((0, y, 2), "+z", L, 1.6, 22))
for sx in (-1, 1):
    A9["armorBody"].append(thorn((2.6 * sx, 21.0, 2), "+z", 2.8, 1.2, 30))
    A9["armorBody"].append(thorn((2.2 * sx, 22.2, -2), "-z", 1.5, 1.0, 20))
    A9["armorBody"].append(thorn((4 * sx, 17.0, 0.5), "+x" if sx > 0 else "-x", 1.6, 1.0, 15))
for arm, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
    out = "+x" if sx > 0 else "-x"
    A9[arm] += [thorn((8 * sx, 22.6, 0), out, 3.2, 1.5, 32), thorn((6 * sx, 24, 0.5), "+y", 2.6, 1.3, -10),
                thorn((6 * sx, 17.0, 2), "+z", 2.6, 1.2, -25), thorn((8 * sx, 14.5, 0), out, 1.6, 0.9, -15)]
for leg, sx in (("armorRightLeg", -1), ("armorLeftLeg", 1)):
    A9[leg] += [thorn((4 * sx, 10.5, 0), "+x" if sx > 0 else "-x", 2.2, 1.2, 28), thorn((2 * sx, 6.5, -2), "-z", 2.0, 1.2, 18),
                thorn((2 * sx, 9.0, 2), "+z", 1.6, 0.9, 10)]
for boot, sx in (("armorRightBoot", -1), ("armorLeftBoot", 1)):
    A9[boot] += [thorn((2 * sx, 1.5, 2), "+z", 2.6, 1.2, -8), thorn((4 * sx, 2.6, 0), "+x" if sx > 0 else "-x", 1.5, 0.9, 20)]
os.makedirs(f"{A}/smooth", exist_ok=True)
w(f"{A}/smooth/obsidian_armor.json", A9)
w(f"{G}/item/armor/obsidian_armor.geo.json", geo("obsidian_armor", [dict((k, v) for k, v in b.items() if k != "cubes") for b in ar[:14]], 4))
w(f"{AN}/item/armor/obsidian_armor.animation.json", {"format_version": "1.8.0", "animations": {
    "animation.obsidian_armor.idle": {"animation_length": 3.0, "loop": True, "bones": {}}}})

# ---- gauntlet: same bones as v10 (all animations keep working), smooth parts instead of cubes
def tube(bottom, h, r, flare=1.0, r2=None):
    return {"type": "tube", "a": list(bottom), "b": [0, 1, 0], "len": h, "r": r, "r2": r2 or r, "p": flare}
def caps(a, b, r): return {"type": "capsule", "a": list(a), "b": list(b), "r": r}
def blob(c, radii, p=3.0, mat="obsidian"): return {"type": "blob", "a": list(c), "b": list(radii), "p": p, "mat": mat}
def needle(base, d, L, r, bend=(0, 0, 0), glow=0.35):
    d = np.array(d, float); d /= np.linalg.norm(d)
    return {"type": "thorn", "a": list(base), "b": [round(v, 4) for v in d], "c": list(bend), "len": L, "r": r, "glow": glow}
GS = {"bracer": [], "fin_spikes": [], "extender": [], "fist": [], "knuckles": [], "claws": []}
GS["bracer"] += [tube((0, -0.2, 0), 1.0, 2.62), tube((0, 0.7, 0), 1.5, 2.55, 1.05), tube((0, 2.0, 0), 1.5, 2.72, 1.05),
                 tube((0, 3.3, 0), 1.5, 2.9, 1.05), tube((0, 4.6, 0), 1.2, 3.1, 1.03),
                 blob((0, 2.8, -2.86), (0.65, 0.5, 0.28), 2.2, "glow"), blob((0, 2.8, 2.86), (0.65, 0.5, 0.28), 2.2, "glow"),
                 needle((2.85, 0.8, 0), (0.22, 1, 0), 4.2, 0.5, (0.5, 0, 0), 0.25), needle((-2.85, 0.8, 0), (-0.22, 1, 0), 4.2, 0.5, (-0.5, 0, 0), 0.25)]
for zs in (-1, 1):
    for y, h in ((1.0, 1.8), (2.3, 2.4), (3.6, 3.0)):
        GS["bracer"].append(needle((0, y, 2.85 * zs), (0, 0.93, 0.37 * zs), h, 0.45, (0, 0.2, 0.35 * zs)))
GS["fin_spikes"] += [needle((3.1, 4.5, 0), (0.47, 0.88, 0), 3.8, 0.55, (0.6, 0.2, 0)), needle((-3.1, 4.5, 0), (-0.47, 0.88, 0), 3.8, 0.55, (-0.6, 0.2, 0))]
GS["extender"] += [tube((0, -1.0, 0), 1.0, 1.6)]
GS["fist"] += [blob((0, -1.65, 0), (2.3, 1.7, 2.2), 3.2), blob((0, -1.75, -2.35), (2.5, 1.25, 0.4), 3.0),
               blob((0, -1.75, 2.35), (2.5, 1.25, 0.4), 3.0), caps((-2.65, -0.9, -0.2), (-2.55, -3.5, 0.3), 0.8)]
for x in FX:
    GS["fist"] += [caps((x, -3.75, -1.7), (x, -3.75, 1.7), 0.56), caps((x, -4.45, -1.25), (x, -4.45, 1.25), 0.46)]
    GS["knuckles"].append(needle((x, -4.7, 0), (0, -1, 0), 2.1, 0.42, (0, 0, 0), 0.5))
    GS["claws"].append(needle((x, -4.4, 0), (0, -1, 0), 5.4, 0.36, (0, 0, -0.9), 0.55))
GS["claws"].append(needle((-2.6, -4.0, 0.2), (-0.15, -1, 0), 2.8, 0.32, (0, 0, -0.5), 0.55))
w(f"{A}/smooth/obsidian_gauntlet.json", GS)
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", [dict((k, v) for k, v in b.items() if k != "cubes") for b in gl], 4))

# ---- smooth-mesh materials: glossy obsidian (dark violet-black, conchoidal sheen) and the glowing tip gradient
so = np.zeros((32, 32, 4)); nz = noise(32, 32, 3)
yy, xx = np.mgrid[0:32, 0:32] / 31.0
sheen = np.clip(1 - np.abs(np.sin((xx * 2.0 + yy * 0.6) * np.pi)) * 1.2, 0, 1) ** 3
so[..., 0] = 14 + 22 * nz + 70 * sheen; so[..., 1] = 9 + 12 * nz + 45 * sheen; so[..., 2] = 26 + 40 * nz + 110 * sheen; so[..., 3] = 255
vein2 = np.abs(noise(32, 32, 4, 2) - 0.5) < 0.035
so[vein2] = [120, 50, 200, 255]
save(rgba(so), f"{T}/entity/smooth_obsidian.png")
sg = np.zeros((16, 16, 4)); t = np.mgrid[0:16, 0:16][0] / 15.0
sg[..., 0] = 150 + 105 * t; sg[..., 1] = 60 + 170 * t ** 2; sg[..., 2] = 255; sg[..., 3] = 230
save(rgba(sg), f"{T}/entity/smooth_glow.png")
print("v14 smooth meshes:", sum(len(v) for v in A9.values()), "armor thorns,", sum(len(v) for v in GS.values()), "gauntlet parts")

# =====================================================================================================
# v15: plasma beam textures for the smooth capsule renderer (tile in both directions; streaks run along V)
# =====================================================================================================
rs = np.random.RandomState(7)
st = np.zeros((64, 32, 4)); st[..., 0] = 235; st[..., 1] = 25; st[..., 2] = 35; st[..., 3] = 235
for _ in range(26):
    u = rs.randint(0, 32); v0 = rs.randint(0, 64); L = rs.randint(10, 40); wd = rs.choice([1, 1, 2])
    br = rs.uniform(0.55, 1.0)
    for k in range(L):
        v = (v0 + k) % 64
        fall = 1 - abs(k - L / 2) / (L / 2)
        for du in range(wd):
            c = st[v, (u + du) % 32]
            mix = br * fall
            c[:3] = c[:3] * (1 - mix) + np.array([255, 245, 240]) * mix
            c[3] = max(c[3], 235 + 20 * mix)
# hot core band in the middle of each u-period so the beam reads brighter along its axis
for u in range(32):
    f = 0.25 * (1 + np.cos((u / 32.0) * 4 * np.pi))
    st[:, u, :3] = np.clip(st[:, u, :3] + np.array([20, 70, 60]) * f, 0, 255)
save(rgba(st), f"{T}/entity/plasma_streak.png")
ha = np.zeros((32, 32, 4)); hn = noise(32, 32, 2)
ha[..., 0] = 255; ha[..., 1] = 40 + 50 * hn; ha[..., 2] = 50; ha[..., 3] = 70 + 90 * hn
save(rgba(ha), f"{T}/entity/plasma_halo.png")
print("v15 plasma beam textures")

# =====================================================================================================
# v16: plated obsidian armor + open-claw gauntlet, recoloured black / grey / purple.
#      Armor: layered GeckoLib plate cubes, every face painted into its own rect of a 128x128 atlas (per-face UV),
#      plus a few glowing purple gems drawn by SmoothPartsLayer. Gauntlet: the hand is an open claw (splayed,
#      segmented fingers with hooked talons); the extending claw blades now pivot at the fingertips.
# =====================================================================================================
BLK_LO, BLK_HI = np.array([10, 9, 13]), np.array([42, 39, 50])
GRY_HI, GRY, GRY_LO = np.array([132, 129, 142]), np.array([80, 77, 88]), np.array([34, 32, 40])
PUR, PUR_HI = np.array([118, 50, 186]), np.array([200, 135, 255])
AW = 128
atlas = np.zeros((AW, AW, 4)); aglow = np.zeros((AW, AW, 4))
_shelf = {"x": 0, "y": 0, "h": 0}
def alloc(w_, h_):
    if _shelf["x"] + w_ > AW: _shelf.update(x=0, y=_shelf["y"] + _shelf["h"], h=0)
    x, y = _shelf["x"], _shelf["y"]
    assert y + h_ <= AW, "armor atlas full"
    _shelf["x"] += w_; _shelf["h"] = max(_shelf["h"], h_)
    return x, y

def paint_face(w_, h_, kind, deco):
    """Paints one cube face (w x h texels) and returns its atlas position. kind: plate | trim | inlay."""
    x0, y0 = alloc(w_, h_)
    n, n2 = noise(w_, h_, 2), noise(w_, h_, 3, 2)
    px = np.zeros((h_, w_, 3)); gl_ = np.zeros((h_, w_, 4))
    for j in range(h_):
        for i in range(w_):
            if kind == "plate":
                c = BLK_LO + (BLK_HI - BLK_LO) * n[j, i] ** 1.6
                d = (i + 0.6 * j) / max(w_ + 0.6 * h_, 1)
                c = c + 34 * max(0.0, 1 - abs(d - 0.3) * 7) ** 2          # glassy sheen streak
                if abs(n2[j, i] - 0.5) < 0.018 and 0 < i < w_ - 1 and 0 < j < h_ - 1: c = PUR * 0.75
                if w_ == 1 or h_ == 1: c = GRY * 0.9                      # thin plate edges read as grey rims
                elif j == 0: c = GRY
                elif i == 0: c = GRY * 0.8
                elif j == h_ - 1 or i == w_ - 1: c = GRY_LO
            else:
                c = GRY_LO + (GRY - GRY_LO) * (0.6 + 0.4 * n[j, i])
                if h_ > 1 and j == 0: c = GRY_HI
                elif h_ > 1 and j == h_ - 1: c = GRY_LO * 0.8
                if kind == "trim" and h_ >= 3 and w_ >= 5 and j == h_ // 2 and i % 4 == 2: c = np.array([22, 20, 26])
                if kind == "inlay" and h_ >= 3 and j == h_ // 2 and 0 < i < w_ - 1:
                    c = PUR; gl_[j, i] = [*PUR_HI, 255]
            px[j, i] = c
    cx = w_ // 2
    if deco == "visor":
        r = round(h_ * 0.55)
        for j in range(1, r): px[j, cx] = GRY * 0.95                   # nose ridge
        for i in range(1, w_ - 1): px[r, i] = [6, 5, 8]                # eye slit
        px[r, cx] = GRY
        for i in (cx - 3, cx - 2, cx + 2, cx + 3):
            if 0 < i < w_ - 1: px[r, i] = PUR_HI; gl_[r, i] = [*PUR_HI, 255]
        for i in (2, 3, w_ - 3, w_ - 4):
            if r + 2 < h_ - 1: px[r + 2, i] = [8, 7, 10]               # breathing holes
    elif deco == "ridge":
        for j in range(1, h_ - 1): px[j, cx] = GRY * 0.9
    elif deco == "gem":
        px[h_ // 2, cx] = PUR_HI; gl_[h_ // 2, cx] = [*PUR_HI, 255]
    elif deco == "vents":
        for i in (2, 4, w_ - 3, w_ - 5):
            if 0 < i < w_ - 1: px[h_ // 2, i] = [8, 7, 10]
    atlas[y0:y0 + h_, x0:x0 + w_, :3] = np.clip(px, 0, 255); atlas[y0:y0 + h_, x0:x0 + w_, 3] = 255
    aglow[y0:y0 + h_, x0:x0 + w_] = gl_
    return x0, y0

def pcube(lo, hi, kind="plate", deco=None):
    """Cube between two corners. deco decorates the north (front) face: visor | ridge | gem | vents;
    ridge also goes on the south face so back plates match."""
    size = [hi[i] - lo[i] for i in range(3)]
    dims = {"north": (size[0], size[1]), "south": (size[0], size[1]), "east": (size[2], size[1]),
            "west": (size[2], size[1]), "up": (size[0], size[2]), "down": (size[0], size[2])}
    uv = {}
    for f, (fw, fh) in dims.items():
        tw, th = max(1, round(fw)), max(1, round(fh))
        fd = deco if f == "north" or (f == "south" and deco == "ridge") else None
        uv[f] = {"uv": list(paint_face(tw, th, kind, fd)), "uv_size": [tw, th]}
    return {"origin": [round(v, 3) for v in lo], "size": [round(v, 3) for v in size], "uv": uv}

def box(sx, t, y, z, kind="plate", deco=None):
    """Mirrored box: t = (inner, outer) distance along the side's outward X (sx = -1 right, +1 left)."""
    a, b = sx * t[0], sx * t[1]
    return pcube((min(a, b), y[0], z[0]), (max(a, b), y[1], z[1]), kind, deco)

AC = {k: [] for k in ("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot")}
# ---- helmet: closed great-helm, brow band, low crest ridge, jaw guard, cheek plates, neck guard
AC["armorHead"] += [
    pcube((-4.6, 24.3, -4.6), (4.6, 33.0, 4.6), "plate", "visor"),
    pcube((-4.85, 29.2, -4.85), (4.85, 30.3, 4.85), "inlay"),
    pcube((-0.55, 32.9, -4.4), (0.55, 33.8, 4.9), "trim"),
    pcube((-0.55, 27.6, 4.5), (0.55, 33.8, 5.0), "trim"),
    pcube((-4.85, 24.0, -4.95), (4.85, 26.4, -4.5), "plate", "vents"),
    pcube((-4.95, 24.0, -4.5), (-4.5, 28.0, 1.2)), pcube((4.5, 24.0, -4.5), (4.95, 28.0, 1.2)),
    pcube((-4.8, 23.6, 3.6), (4.8, 25.4, 5.1)),
]
# ---- chestplate: cuirass, raised breastplate, two abdominal lames, gorget, back plate, fauld band
AC["armorBody"] += [
    pcube((-4.7, 13.0, -2.7), (4.7, 24.4, 2.7)),
    pcube((-4.35, 17.6, -3.3), (4.35, 24.1, -2.7), "plate", "ridge"),
    pcube((-4.15, 15.8, -3.15), (4.15, 17.6, -2.7)),
    pcube((-4.0, 14.0, -3.0), (4.0, 15.8, -2.7)),
    pcube((-3.4, 23.6, -3.2), (3.4, 24.9, 3.2), "inlay"),
    pcube((-4.35, 15.0, 2.7), (4.35, 24.1, 3.25), "plate", "ridge"),
    pcube((-4.85, 12.6, -2.95), (4.85, 13.8, 2.95), "trim"),
]
# ---- arms: sleeve, three-lame pauldron, elbow cop, vambrace + cuff
for arm, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
    AC[arm] += [
        box(sx, (3.35, 8.65), (12.4, 24.5), (-2.65, 2.65)),
        box(sx, (3.4, 9.4), (21.6, 25.3), (-3.35, 3.35)),
        box(sx, (3.9, 9.15), (19.9, 21.7), (-3.1, 3.1)),
        box(sx, (4.4, 8.95), (18.6, 20.0), (-2.9, 2.9), "trim"),
        box(sx, (4.8, 7.2), (15.6, 17.6), (2.6, 3.25)),
        box(sx, (3.25, 8.75), (13.4, 16.0), (-2.75, 2.75)),
        box(sx, (3.15, 8.85), (12.2, 13.4), (-2.85, 2.85), "inlay"),
    ]
# ---- legs: cuisse, two-lame tasset, knee cop (the left leg sits a hair further out so the inner faces never z-fight)
for leg, sx in (("armorRightLeg", -1), ("armorLeftLeg", 1)):
    zo = 0.0 if sx < 0 else 0.04
    AC[leg] += [
        box(sx, (-0.2, 4.45), (5.0, 12.3), (-2.55 - zo, 2.55 + zo)),
        box(sx, (-0.25, 4.7), (11.0, 12.7), (-2.95 - zo, 2.95 + zo), "inlay"),
        box(sx, (-0.2, 4.6), (9.4, 11.0), (-2.8 - zo, 2.8 + zo)),
        box(sx, (0.7, 3.1), (4.3, 6.9), (-3.15 - zo, -2.4), "plate", "gem"),
    ]
# ---- boots: greave, toe cap, cuff
for boot, sx in (("armorRightBoot", -1), ("armorLeftBoot", 1)):
    zo = 0.0 if sx < 0 else 0.04
    AC[boot] += [
        box(sx, (-0.2, 4.85), (-0.3, 5.6), (-2.85 - zo, 2.85 + zo), "plate", "ridge"),
        box(sx, (0.0, 4.6), (-0.3, 2.0), (-3.7 - zo, -2.85)),
        box(sx, (-0.3, 5.05), (4.6, 5.9), (-3.05 - zo, 3.05 + zo), "inlay"),
    ]
_ar = []
for b in ar[:14]:
    b = dict((k, v) for k, v in b.items() if k != "cubes")
    if AC.get(b["name"]): b["cubes"] = AC[b["name"]]
    _ar.append(b)
_g = geo("obsidian_armor", _ar, 4)
_g["minecraft:geometry"][0]["description"]["texture_width"] = AW
_g["minecraft:geometry"][0]["description"]["texture_height"] = AW
w(f"{G}/item/armor/obsidian_armor.geo.json", _g)
save(rgba(atlas), f"{T}/item/armor/obsidian_armor.png")
save(rgba(aglow), f"{T}/item/armor/obsidian_armor_glowmask.png")
# glowing gems / eye slits (emissive, drawn by SmoothPartsLayer on the same bones)
A16 = {k: [] for k in AC}
A16["armorHead"] += [blob((sx * 1.9, 28.25, -4.63), (1.05, 0.2, 0.1), 2.2, "glow") for sx in (-1, 1)]
A16["armorBody"] += [blob((0, 22.4, -3.36), (0.7, 0.9, 0.16), 2.0, "glow"), blob((0, 22.4, 3.31), (0.55, 0.7, 0.12), 2.0, "glow")]
for arm, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
    A16[arm].append(blob((sx * 9.45, 23.45, 0), (0.1, 0.65, 0.65), 2.0, "glow"))
for leg, sx in (("armorRightLeg", -1), ("armorLeftLeg", 1)):
    A16[leg].append(blob((sx * 1.9, 5.6, -3.2 - (0.0 if sx < 0 else 0.04)), (0.38, 0.45, 0.1), 2.0, "glow"))
w(f"{A}/smooth/obsidian_armor.json", A16)
PAL16 = {"X": (12, 11, 15), "o": (58, 55, 68), "p": (165, 85, 240)}
for n in ("obsidian_helmet", "obsidian_chestplate", "obsidian_leggings", "obsidian_boots"):
    pix(ICONS[n], PAL16, f"{T}/item/{n}.png")
print("v16 plated armor:", sum(len(v) for v in AC.values()), "plates, atlas rows used:", _shelf["y"] + _shelf["h"])

# ---- gauntlet: open claw. Same bones (all animations still apply); thumb on -X, palm toward +Z, fingers toward -Y.
def _mid(a, b, off=(0, 0, 0)): return [round((a[i] + b[i]) / 2 + off[i], 3) for i in range(3)]
GS["fist"] = [blob((0, -1.6, 0.1), (2.35, 1.65, 1.7), 3.0),                       # palm
              blob((0, -1.7, -1.8), (2.5, 1.4, 0.38), 3.0),                       # back-of-hand plate
              caps((-2.0, -3.0, -0.9), (2.0, -3.0, -0.9), 0.55)]                  # knuckle guard
GS["knuckles"], GS["claws"] = [], []
for x0, L in zip(FX, (0.95, 1.0, 0.96, 0.82)):
    s = x0 * 0.32
    k0 = (x0, -3.0, -0.2)
    k1 = (round(x0 + s * 0.6, 3), round(-3.0 - 1.9 * L, 3), 0.25)
    k2 = (round(x0 + s, 3), round(-3.0 - 3.5 * L, 3), 1.05)
    GS["fist"] += [caps(k0, k1, 0.56), caps(k1, k2, 0.48),
                   blob(_mid(k0, k1, (0, 0, -0.45)), (0.5, 0.9 * L, 0.28), 2.5), blob(_mid(k1, k2, (0, 0, -0.38)), (0.44, 0.75 * L, 0.25), 2.5),
                   needle(k2, (s * 0.25, -0.75, 0.75), 2.6 * L, 0.45, (0, 0, 0.5), 0.45)]          # hooked talon
    GS["knuckles"].append(needle((x0, -3.0, -1.3), (0, -0.35, -1), 1.3, 0.32, (0, -0.3, 0), 0.5))
    GS["claws"].append(needle(k2, (s * 0.2, -1, 0.25), 4.6 * L, 0.38, (0, 0, 0.8), 0.6))          # extending blade
t0, t1, t2 = (-2.3, -1.0, 0.4), (-3.1, -2.6, 1.2), (-3.0, -3.9, 2.0)
GS["fist"] += [caps(t0, t1, 0.62), caps(t1, t2, 0.52), blob(_mid(t0, t1, (-0.4, 0, 0)), (0.35, 0.8, 0.5), 2.5),
               needle(t2, (0.15, -0.6, 0.8), 2.2, 0.4, (0.3, 0, 0.4), 0.45)]
w(f"{A}/smooth/obsidian_gauntlet.json", GS)
for b in gl:
    if b["name"] == "claws": b["pivot"] = [0, -6.3, 0]   # blades fold away at the fingertips, not inside the palm
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", [dict((k, v) for k, v in b.items() if k != "cubes") for b in gl], 4))

# ---- smooth-mesh materials: near-black obsidian with a neutral grey sheen + purple veins; deep purple glow
so = np.zeros((32, 32, 4)); nz = noise(32, 32, 3)
so[..., 0] = 11 + 18 * nz + 62 * sheen; so[..., 1] = 10 + 16 * nz + 60 * sheen; so[..., 2] = 15 + 24 * nz + 74 * sheen; so[..., 3] = 255
so[vein2] = [*PUR, 255]
save(rgba(so), f"{T}/entity/smooth_obsidian.png")
sg = np.zeros((16, 16, 4)); t = np.mgrid[0:16, 0:16][0] / 15.0
sg[..., 0] = 105 + 95 * t; sg[..., 1] = 28 + 135 * t ** 2; sg[..., 2] = 195 + 60 * t; sg[..., 3] = 230
save(rgba(sg), f"{T}/entity/smooth_glow.png")
print("v16 open-claw gauntlet:", sum(len(v) for v in GS.values()), "parts")

# =====================================================================================================
# v17: slanted, sharp plated skin-armor + angular cube claw gauntlet (both GeckoLib cubes, per-face painted atlases,
#      purple seams/edges glow through AutoGlowingGeoLayer). Rotated cubes use _rot (verified against GeckoLib's
#      baking: file-space R = Rz(-rz) Ry(ry) Rx(-rx), about the file-space pivot).
#
#      Gauntlet frame (no root rotation, checked against ItemInHandLayer + the third-person display transform):
#      +Y up the forearm, wrist at y=0 (= 6.3 px down the arm bone), the arm's hand end is at y=-3.66 and its 4x4
#      cross-section is x,z in [-2,2]. +X = outer side of the arm, -X = toward the body, +Z = front, -Z = back.
#      The hand is modelled as a RIGHT hand with the palm toward -Z (palm down when the arm is raised, thumb on
#      the inner side); the left-hand display transforms mirror X so the off-hand is a true left hand.
# =====================================================================================================
def new_atlas(size, dens):
    """Fresh atlas; dens = texels per model pixel (GeckoLib divides UVs by texture_width, so any density works)."""
    global AW, atlas, aglow, DENS
    AW, DENS = size, dens; atlas = np.zeros((size, size, 4)); aglow = np.zeros((size, size, 4)); _shelf.update(x=0, y=0, h=0)

SEAM = [*PUR_HI, 255]
PAINTED = {}   # (atlas, w, h, kind, deco) -> where that face was painted: mirrored plates share one texture


def paint2(w_, h_, kind, deco):
    """kinds: skin (dark living obsidian, glowing veins) | plate (glossy black, grey rim, glowing bottom seam)
    | edge (black, glowing purple outline) | blade (black to grey) | bladetip (blade with a glowing tip).
    Each face shape is painted once per atlas and reused, so the left and right copies of a plate look identical."""
    key = (id(atlas), w_, h_, kind, deco)
    if key in PAINTED:
        return PAINTED[key]
    x0, y0 = alloc(w_, h_)
    PAINTED[key] = (x0, y0)
    n, n2 = noise(w_, h_, 2 * DENS), noise(w_, h_, 3 * DENS, 2)
    n, n2 = (n + n[:, ::-1]) / 2, (n2 + n2[:, ::-1]) / 2          # left-right symmetric, so mirrored plates match
    px = np.zeros((h_, w_, 3)); g = np.zeros((h_, w_, 4))
    thin = w_ == 1 or h_ == 1
    for j in range(h_):
        for i in range(w_):
            edge = i == 0 or j == 0 or i == w_ - 1 or j == h_ - 1
            if kind == "skin":
                c = np.array([13, 11, 17]) + np.array([16, 13, 24]) * n[j, i] + np.array([10, 2, 20]) * n2[j, i] ** 3
            elif kind == "plate":
                c = BLK_LO + (BLK_HI - BLK_LO) * n[j, i] ** 1.6
                d = abs(i - (w_ - 1) / 2) / max(w_, 1) + 0.25 * j / max(h_, 1)
                c = c + 34 * max(0.0, 1 - abs(d - 0.28) * 7) ** 2
                if thin: c = GRY * 0.85
                elif j == 0: c = GRY
                elif i == 0 or i == w_ - 1: c = GRY * 0.62
                elif j == h_ - 1: c = PUR; g[j, i] = SEAM
            elif kind == "edge":
                c = BLK_LO + (BLK_HI - BLK_LO) * n[j, i] ** 1.6
                c = c + 26 * max(0.0, 1 - abs((i + 0.6 * j) / max(w_ + 0.6 * h_, 1) - 0.3) * 7) ** 2
                if thin or edge: c = PUR; g[j, i] = SEAM
            else:  # blade / bladetip
                f = j / max(h_ - 1, 1)
                c = BLK_LO + (GRY - BLK_LO) * (0.1 + 0.45 * f) * (0.7 + 0.3 * n[j, i])
                if i == 0 or i == w_ - 1: c = c * 0.6
                if kind == "bladetip" and f > 0.55: c = PUR_HI; g[j, i] = SEAM
            px[j, i] = c
    if deco == "eyes":
        r = round(h_ * 0.5); cx = w_ // 2
        for i in range(1, w_ - 1):
            if abs(i - cx + 0.5) > 0.8: px[r, i] = PUR_HI; g[r, i] = SEAM
    atlas[y0:y0 + h_, x0:x0 + w_, :3] = np.clip(px, 0, 255); atlas[y0:y0 + h_, x0:x0 + w_, 3] = 255
    aglow[y0:y0 + h_, x0:x0 + w_] = g
    return x0, y0

def save_atlas(geo_json, tex_path):
    """Crops the atlas to the rows in use (next power of two) and records the size in the geo file."""
    h_ = 1 << max(4, (_shelf["y"] + _shelf["h"] - 1).bit_length())
    geo_json["minecraft:geometry"][0]["description"].update(texture_width=AW, texture_height=h_)
    save(rgba(atlas[:h_]), tex_path); save(rgba(aglow[:h_]), tex_path.replace(".png", "_glowmask.png"))
    return h_

def tcube(lo, hi, kind="plate", deco=None, rot=None, pivot=None):
    size = [hi[i] - lo[i] for i in range(3)]
    dims = {"north": (size[0], size[1]), "south": (size[0], size[1]), "east": (size[2], size[1]),
            "west": (size[2], size[1]), "up": (size[0], size[2]), "down": (size[0], size[2])}
    uv = {}
    for f, (fw, fh) in dims.items():
        tw, th = max(1, round(fw * DENS)), max(1, round(fh * DENS))
        k = kind if not (kind.startswith("blade") and f in ("up", "down")) else "edge"
        uv[f] = {"uv": list(paint2(tw, th, k, deco if f == "north" else None)), "uv_size": [tw, th]}
    c = {"origin": [round(v, 3) for v in lo], "size": [round(v, 3) for v in size], "uv": uv}
    if rot and any(rot):
        c["rotation"] = [round(v, 3) for v in rot]; c["pivot"] = [round(v, 3) for v in pivot]
    return c

def aim(d):
    """Cube rotation (rx, 0, rz) that turns the cube's -Y axis onto direction d (file space)."""
    d = np.array(d, float); d /= np.linalg.norm(d)
    a = math.asin(max(-1.0, min(1.0, -d[2]))); c = math.atan2(d[0], -d[1])
    r = (-math.degrees(a), 0.0, -math.degrees(c))
    assert np.allclose(_rot(r) @ np.array([0, -1, 0]), d, atol=1e-6)
    return r

def seg(j, d, L, w_, t_, kind="plate"):
    """Box hanging from joint j along direction d: length L, width w_ (local x) and thickness t_ (local z)."""
    d = np.array(d, float); d /= np.linalg.norm(d)
    lo = (j[0] - w_ / 2, j[1] - L, j[2] - t_ / 2); hi = (j[0] + w_ / 2, j[1], j[2] + t_ / 2)
    return tcube(lo, hi, kind, None, aim(d), j), tuple(np.array(j) + d * L)

SPIKE_PARTS = None   # while a list: blade() adds a jagged OBJ spike to it (via a marker) instead of making cubes
SPIKE_N = [0]


def spike_part(j, d, L, r):
    """A jagged obsidian spike (models/obj/spike_<n>.obj, drawn by SmoothPartsLayer) from j along d."""
    d = np.array(d, float); d /= np.linalg.norm(d)
    SPIKE_N[0] += 1
    return {"type": "obj", "mat": f"spike_{SPIKE_N[0] % 6}", "a": [round(float(v), 3) for v in j], "b": [round(float(v), 4) for v in d],
            "len": round(float(L), 3), "r": round(float(r), 3), "p": float((SPIKE_N[0] * 67) % 360)}


def blade(j, d, L, w_, t_, tiers=3, kind="blade"):
    """Sharp tapered blade: `tiers` shrinking boxes along d (the last ones glow at the tip)."""
    if SPIKE_PARTS is not None:
        return [{"__spike__": spike_part(j, d, L * 1.05, max(w_, t_) * 0.55)}]
    out, p = [], tuple(j)
    fr = [(1.0, 0.42), (0.66, 0.34), (0.36, 0.3)][:tiers]
    for k, (wf, lf) in enumerate(fr):
        c, e = seg(p, d, L * lf, max(0.15, w_ * wf), max(0.15, t_ * wf), "bladetip" if k == len(fr) - 1 else "blade")
        out.append(c); p = tuple(np.array(p) + (np.array(e) - np.array(p)) * 0.92)
    return out

def xf(p, rot, pivot):
    """Where point p of a cube rotated by rot about pivot ends up (file space)."""
    return tuple(_rot(rot) @ (np.array(p, float) - np.array(pivot)) + np.array(pivot))

def slab(lo, hi, rot, pivot, kind="plate"):
    return tcube(lo, hi, kind, None, rot, pivot)

# ------------------------------------------------------------------------------------------- ARMOR
new_atlas(512, 2)
SPIKE_PARTS = []
AK = {k: [] for k in ("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot")}
H = AK["armorHead"]
H.append(tcube((-4.4, 24.1, -4.4), (4.4, 32.4, 4.4), "skin", "eyes"))                    # skin-tight skull
for sx in (-1, 1):
    # V visor: brow plates rising outward over the glowing eye slit
    H.append(slab((min(0, sx * 4.7), 28.9, -4.95), (max(0, sx * 4.7), 30.0, -4.4), (0, 0, -16 * sx), (0, 28.9, -4.7)))
    # angular jaw: lower V meeting in a chin point
    H.append(slab((min(0, sx * 4.6), 24.0, -4.95), (max(0, sx * 4.6), 25.9, -4.4), (0, 0, -13 * sx), (0, 24.0, -4.7)))
    # swept-back side blades along the temples, ending in points behind the head
    c, e = seg((sx * 4.7, 29.0, -3.4), (0, 0.32, 1), 8.2, 0.6, 2.2); H.append(c)
    H += blade(tuple(np.array(e) - [0, 0, 0.3]), (0, 0.4, 1), 4.0, 0.55, 2.1)
    c, e = seg((sx * 4.64, 26.9, -1.0), (0, 0.18, 1), 5.6, 0.55, 1.6); H.append(c)          # second, lower blade
    H += blade(tuple(np.array(e) - [0, 0, 0.3]), (0, 0.24, 1), 2.8, 0.5, 1.5)
c, e = seg((0, 32.3, -3.2), (0, 0.42, 1), 6.8, 0.85, 2.0); H.append(c)                   # crest fin
H += blade(tuple(np.array(e) - [0, 0, 0.3]), (0, 0.5, 1), 4.2, 0.8, 1.9)
H.append(slab((-4.2, 23.7, 4.25), (4.2, 26.6, 4.85), (20, 0, 0), (0, 26.6, 4.55)))        # flared neck guard

B = AK["armorBody"]
B.append(tcube((-4.45, 12.3, -2.45), (4.45, 24.3, 2.45), "skin"))
for sx in (-1, 1):
    lo_x, hi_x = (min(0.1 * sx, sx * 4.6), max(0.1 * sx, sx * 4.6))
    B.append(slab((lo_x, 19.4, -3.0), (hi_x, 23.9, -2.45), (-8, 0, -12 * sx), (0.1 * sx, 23.9, -2.7)))   # pectorals
    for k, (y0, y1) in enumerate(((17.2, 19.3), (15.1, 17.2), (13.0, 15.1))):                          # chevron ab plates
        wx = 4.3 - 0.25 * k
        # a hair apart at the middle and each row a little further out than the one above, so no two plates' faces
        # share a depth (they fought over the pixels, and the glowing seams lost on one side)
        zf = -2.95 - 0.07 * k
        B.append(slab((min(sx * 0.06, sx * wx), y0, zf), (max(sx * 0.06, sx * wx), y1, -2.45), (-8, 0, -10 * sx), (sx * 0.06, y1, -2.7)))
    B.append(slab((min(sx * 0.9, sx * 4.4), 18.4, 2.45), (max(sx * 0.9, sx * 4.4), 23.8, 2.95), (10, 0, 8 * sx), (sx * 0.9, 23.8, 2.7)))  # shoulder blades
for k, y in enumerate((23.6, 21.4, 19.2, 17.0, 14.8)):                                    # vertebra plates down the back
    hw = 1.5 - 0.12 * k
    B.append(slab((-hw, y - 2.4, 2.45), (hw, y, 3.0), (24, 0, 0), (0, y, 2.7)))
B.append(tcube((-3.6, 23.9, -2.8), (3.6, 24.6, 2.8)))                                     # collar
B.append(tcube((-4.65, 12.1, -2.65), (4.65, 13.1, 2.65)))                                 # waist band

for arm, sx in (("armorRightArm", -1), ("armorLeftArm", 1)):
    A_ = AK[arm]
    A_.append(tcube((min(sx * 3.55, sx * 8.45), 12.25, -2.45), (max(sx * 3.55, sx * 8.45), 24.45, 2.45), "skin"))
    # three overlapping pauldron plates angled down and out, each ending in a sharp point
    for k, (y0, y1, x0, x1, ang, zw) in enumerate(((23.6, 24.9, 3.6, 9.0, 22, 3.0), (22.2, 23.5, 4.2, 9.3, 28, 2.85), (20.8, 22.1, 4.8, 9.4, 34, 2.7))):
        piv = (sx * (x0 + 0.4), y1 - 0.3, 0); rot = (0, 0, ang * sx)
        A_.append(slab((min(sx * x0, sx * x1), y0, -zw), (max(sx * x0, sx * x1), y1, zw), rot, piv))
        tip = xf((sx * (x1 - 0.2), (y0 + y1) / 2, 0), rot, piv)
        A_ += blade(tip, _rot(rot) @ np.array([sx, -0.25, 0.0]), 2.4 - 0.3 * k, 0.55, 2.0 * zw / 3.0)
    # forearm: slanted outer vane, back elbow spur, wrist cuff
    A_.append(slab((min(sx * 8.4, sx * 8.95), 13.2, -2.1), (max(sx * 8.4, sx * 8.95), 18.2, 2.1), (0, 0, -8 * sx), (sx * 8.6, 13.2, 0)))
    c, e = seg((sx * 6.0, 17.2, 2.4), (0, 0.4, 1), 0.6, 1.4, 1.0); A_.append(c)
    A_ += blade(e, (0, 0.45, 1), 2.4, 1.2, 0.8)
    A_.append(tcube((min(sx * 3.4, sx * 8.6), 12.1, -2.65), (max(sx * 3.4, sx * 8.6), 13.1, 2.65)))

for leg, sx in (("armorRightLeg", -1), ("armorLeftLeg", 1)):
    zo = 0.0 if sx < 0 else 0.04; L_ = AK[leg]
    X = lambda a, b: (min(sx * a, sx * b), max(sx * a, sx * b))
    xa, xb = X(-0.15, 4.35); L_.append(tcube((xa, 4.8, -2.45 - zo), (xb, 12.35, 2.45 + zo), "skin"))
    for (y0, y1) in ((10.0, 12.6), (7.8, 10.3)):                                          # outer tassets flaring out
        xa, xb = X(1.0, 4.75); piv = (sx * 4.5, y1, 0)
        L_.append(slab((xa, y0, -2.7 - zo), (xb, y1, 2.7 + zo), (0, 0, -12 * sx), piv))
    xa, xb = X(0.1, 3.8); L_.append(slab((xa, 7.5, -2.95 - zo), (xb, 11.8, -2.45), (-10, 0, 0), (sx * 1.9, 11.8, -2.7)))  # front thigh
    xa, xb = X(0.5, 3.3); L_.append(slab((xa, 4.6, -3.1 - zo), (xb, 7.4, -2.5), (-14, 0, 0), (sx * 1.9, 7.4, -2.8)))      # knee cop
    L_ += blade((sx * 1.9, 7.1, -3.0 - zo), (0, 0.6, -1), 2.0, 1.1, 0.5)                                                   # knee point

for boot, sx in (("armorRightBoot", -1), ("armorLeftBoot", 1)):
    zo = 0.0 if sx < 0 else 0.04; F = AK[boot]
    X = lambda a, b: (min(sx * a, sx * b), max(sx * a, sx * b))
    xa, xb = X(-0.15, 4.4); F.append(tcube((xa, -0.2, -2.55 - zo), (xb, 5.2, 2.55 + zo), "skin"))
    xa, xb = X(0.2, 3.6); F.append(slab((xa, 1.8, -3.1 - zo), (xb, 6.0, -2.55), (-6, 0, 0), (sx * 1.9, 6.0, -2.8)))  # shin plate
    F += blade((sx * 1.9, 5.8, -3.0 - zo), (0, 1, -0.35), 1.8, 1.2, 0.45)
    xa, xb = X(0.0, 3.8); F.append(tcube((xa, -0.2, -3.2 - zo), (xb, 1.6, -2.55)))                                 # sabaton
    F += blade((sx * 1.9, 0.6, -3.15 - zo), (0, -0.05, -1), 1.8, 1.4, 0.9)                                         # pointed toe
    F += blade((sx * 1.9, 1.2, 2.5 + zo), (0, -0.2, 1), 1.3, 0.8, 0.6)                                             # heel spur
    xa, xb = X(-0.25, 4.55); F.append(tcube((xa, 4.6, -2.75 - zo), (xb, 5.6, 2.75 + zo)))                          # ankle cuff
SPIKE_PARTS = None
ASP = {k: [c["__spike__"] for c in v if "__spike__" in c] for k, v in AK.items()}   # the spikes, as OBJ parts per bone
AK = {k: [c for c in v if "__spike__" not in c] for k, v in AK.items()}
_ar = []
for b in ar[:14]:
    b = dict((k, v) for k, v in b.items() if k != "cubes")
    if AK.get(b["name"]): b["cubes"] = AK[b["name"]]
    _ar.append(b)
_g = geo("obsidian_armor", _ar, 4)
save_atlas(_g, f"{T}/item/armor/obsidian_armor.png")
w(f"{G}/item/armor/obsidian_armor.geo.json", _g)
w(f"{A}/smooth/obsidian_armor.json", ASP)          # jagged OBJ spikes (glow comes from the glowmask)
print("v17 skin-armor:", sum(len(v) for v in AK.values()), "cubes, atlas rows used:", _shelf["y"] + _shelf["h"], "/", AW)

# ------------------------------------------------------------------------------------------- GAUNTLET
new_atlas(512, 4)
GK = {k: [] for k in ("bracer", "fin_spikes", "extender", "fist", "knuckles", "claws")}
GK["bracer"] += [tcube((-2.55, 0.1, -2.55), (2.55, 6.2, 2.55), "edge")]                    # forearm box around the 4x4 arm
GK["extender"].append(tcube((-1.6, -1.0, -1.6), (1.6, 0.0, 1.6), "edge"))
# hand: box that fully encloses the arm's hand end (to y=-3.66), knuckle plate on the back, fingers out of the end
GK["fist"] += [tcube((-2.3, -4.0, -2.3), (2.3, 0.15, 2.3), "edge"),
               slab((-2.2, -3.9, 2.3), (2.2, -1.0, 2.75), (-8, 0, 0), (0, -1.0, 2.5))]
FXS = (-1.62, -0.54, 0.54, 1.62)
for x0, L in zip(FXS, (0.95, 1.0, 0.96, 0.84)):
    s = x0 * 0.12
    j = (x0, -3.85, 0.35)
    c1, j1 = seg(j, (s, -1, 0.18), 2.3 * L, 0.88, 0.95, "edge")                             # reaching, slightly extended
    c2, j2 = seg(tuple(np.array(j1) + [0, 0.15, 0]), (s, -0.8, -0.6), 1.9 * L, 0.78, 0.85, "edge")   # bends toward the palm
    GK["fist"] += [c1, c2] + blade(tuple(np.array(j2) + [0, 0.1, 0.05]), (s * 0.5, -0.45, -0.9), 3.6 * L, 0.7, 0.76)  # hooked talon
t0 = (-2.25, -1.6, -1.3)                                                                   # thumb, inner side, opposing the fingers
ct1, tj1 = seg(t0, (-0.55, -0.7, -0.45), 1.8, 0.95, 0.95, "edge")
ct2, tj2 = seg(tj1, (-0.1, -0.6, -0.8), 1.4, 0.82, 0.82, "edge")
GK["fist"] += [ct1, ct2] + blade(tj2, (0.25, -0.35, -0.9), 2.8, 0.66, 0.66)
for x0 in FXS:                                                                             # knuckle spikes (pop on punches)
    GK["knuckles"] += blade((x0, -3.6, 2.6), (0, -0.45, 1), 1.4, 0.55, 0.5)
for x0 in (-1.08, 0.0, 1.08):                                                              # extending blades between the fingers
    GK["claws"] += blade((x0, -4.0, 0.9), (0, -1, -0.12), 6.5, 0.32, 0.95)
gk = [bone("root", (0, 0, 0)), bone("bracer", (0, 0, 0), GK["bracer"], "root"),
      bone("fin_spikes", (0, 4.4, 0), GK["fin_spikes"], "bracer"), bone("extender", (0, 0, 0), GK["extender"], "root"),
      bone("fist", (0, 0, 0), GK["fist"], "root"), bone("knuckles", (0, -3.6, 2.6), GK["knuckles"], "fist"),
      bone("claws", (0, -4.0, 0), GK["claws"], "fist")]
_g = geo("obsidian_gauntlet", gk, 4)
save_atlas(_g, f"{T}/item/obsidian_gauntlet.png")
w(f"{G}/item/obsidian_gauntlet.geo.json", _g)
w(f"{A}/smooth/obsidian_gauntlet.json", {k: [] for k in GK})
_gm = json.load(open(f"{A}/models/item/obsidian_gauntlet.json"))
_gm["display"].update({
    # third person: same wrist placement as before. Left-hand entries repeat the right-hand values (vanilla mirrors
    # translation x / rotation y,z itself) and add scale x = -1, so the off-hand is an exact mirror: a real left hand.
    "thirdperson_righthand": {"rotation": [90, 180, 0], "translation": [0, -2, 3.5], "scale": [1, 1, 1]},
    "thirdperson_lefthand":  {"rotation": [90, 180, 0], "translation": [0, -2, 3.5], "scale": [-1, 1, 1]},
    # first person: claw reaching forward, palm down, thumb toward the screen centre
    "firstperson_righthand": {"rotation": [105, 172, 0], "translation": [1.5, 3.5, -1.0], "scale": [1.15, 1.15, 1.15]},
    "firstperson_lefthand":  {"rotation": [105, 172, 0], "translation": [1.5, 3.5, -1.0], "scale": [-1.15, 1.15, 1.15]},
    "gui":    {"rotation": [20, 125, -30], "translation": [0, 0.5, 0], "scale": [0.85, 0.85, 0.85]}})
w(f"{A}/models/item/obsidian_gauntlet.json", _gm)
print("v17 cube claw gauntlet:", sum(len(v) for v in GK.values()), "cubes, atlas rows used:", _shelf["y"] + _shelf["h"], "/", AW)

# =====================================================================================================
# v18: organic obsidian claw growing out of the skin. Same frame as v17 (+X outer side, -X toward the body, +Z front,
#      wrist at y=0, hand end of the arm at y=-3.66). The hand now hangs naturally: palm toward the body, finger row
#      running front-to-back along the player's side, thumb at the front. Smooth meshes (SmoothPartsLayer) for the
#      hand, knuckles and the crust that thins out up the forearm (bare skin shows between patches, glowing cracks
#      run into it); faceted cube shards break out of the crust and the back of the hand.
#      The inner side must stay within x >= -2 (the torso is right there), so the fingers curl only a little inward.
# =====================================================================================================
new_atlas(512, 4)
GC = {k: [] for k in ("bracer", "fin_spikes", "extender", "fist", "knuckles", "claws")}       # cube shards
GM = {k: [] for k in GC}                                                                       # smooth parts
def crack(*pts):
    """Thin glowing vein along the skin through the given points."""
    return [dict(caps(pts[i], pts[i + 1], 0.07), mat="glow") for i in range(len(pts) - 1)]
# ---- forearm: full crust at the wrist, patches thinning toward the elbow, shards erupting, cracks into the skin
GM["bracer"] += [blob((0.1, 0.6, 0), (2.35, 1.0, 2.4), 6.0),
                 blob((1.75, 2.7, 0.2), (0.55, 2.0, 1.7), 3.0),                                 # outer side
                 blob((0.3, 2.1, -1.8), (1.6, 1.5, 0.5), 3.0),                                  # back
                 blob((-0.2, 1.7, 1.8), (1.3, 1.1, 0.45), 3.0),                                 # front
                 blob((-1.8, 1.3, 0), (0.42, 0.8, 1.3), 3.0)]                                   # inner (flat, stays off the torso)
GM["bracer"] += crack((2.04, 4.5, 0.4), (2.04, 5.3, -0.2), (2.04, 6.0, 0.35), (2.04, 6.6, -0.1))
GM["bracer"] += crack((2.04, 4.1, 1.1), (2.04, 4.8, 1.6))
GM["bracer"] += crack((0.6, 3.5, -2.04), (-0.2, 4.4, -2.04), (0.3, 5.2, -2.04))
GM["bracer"] += crack((-0.4, 2.7, 2.04), (0.3, 3.5, 2.04), (-0.1, 4.2, 2.04))
GC["bracer"] += blade((2.2, 2.0, -0.4), (0.75, 0.65, -0.1), 1.6, 0.55, 0.7)
GC["bracer"] += blade((-0.5, 2.3, 2.05), (0.05, 0.7, 0.7), 1.3, 0.5, 0.5)
GC["fin_spikes"] += blade((2.1, 3.4, 0.6), (0.55, 0.85, 0.12), 2.6, 0.75, 0.8)
GC["fin_spikes"] += blade((2.1, 4.3, -0.5), (0.5, 0.85, -0.2), 1.8, 0.55, 0.6)
GC["fin_spikes"] += blade((0.5, 3.4, -2.1), (0.15, 0.75, -0.65), 1.9, 0.6, 0.6)
GM["extender"].append(tube((0, -1.0, 0), 1.0, 1.5))
# ---- hand: soft rounded mass enclosing the arm's hand end (x up to -2.25 only), knuckle bumps on the back edge
GM["fist"] += [blob((0.15, -1.6, 0), (2.4, 2.4, 2.5), 8.0),
               blob((-1.6, -1.9, 0.2), (0.75, 1.5, 1.9), 3.0),                                  # palm pad
               blob((-1.0, -1.4, 1.95), (1.0, 1.1, 0.7), 3.0)]                                  # thumb mound
for z in (1.56, 0.52, -0.52, -1.56):
    GM["fist"].append(blob((1.75, -3.6, z * 0.95), (0.7, 0.55, 0.5), 2.6))
GC["fist"] += blade((2.45, -1.2, 0.8), (0.7, 0.7, 0.1), 1.6, 0.55, 0.6)                          # shards on the back of the hand
GC["fist"] += blade((2.45, -2.0, -0.9), (0.75, 0.6, -0.2), 1.3, 0.5, 0.5)
# fingers: front (index, next to the thumb) to back (little finger); straight down, curling gently toward the body
for z0, L in zip((1.56, 0.52, -0.52, -1.56), (0.97, 1.0, 0.95, 0.8)):
    s = z0 * 0.08
    p, r = np.array((0.7, -3.55, z0 * 0.95)), 0.6
    for d, ln, rr in (((-0.05, -1, s), 1.9, 0.6), ((-0.3, -0.95, s * 0.6), 1.5, 0.5), ((-0.55, -0.83, s * 0.3), 1.0, 0.43)):
        d = np.array(d) / np.linalg.norm(d); q = p + d * ln * L
        GM["fist"] += [caps(tuple(np.round(p, 3)), tuple(np.round(q, 3)), rr),
                       blob(tuple(np.round(p + [0.25, 0, 0], 3)), (rr * 0.95, rr * 0.9, rr * 0.9), 2.4)]   # knuckle joint
        p = q
    GM["fist"].append(needle(tuple(np.round(p, 3)), (-0.3, -0.95, 0), 2.6 * L, 0.42, (-0.4, 0, 0), 0.45))  # talon
# thumb: from the front-inner side, down and back under the index finger
p = np.array((-1.0, -1.6, 2.2))
for d, ln, rr in (((-0.25, -0.8, 0.55), 1.4, 0.62), ((-0.15, -0.9, -0.2), 1.1, 0.52)):
    d = np.array(d) / np.linalg.norm(d); q = p + d * ln
    GM["fist"] += [caps(tuple(np.round(p, 3)), tuple(np.round(q, 3)), rr), blob(tuple(np.round(p, 3)), (rr, rr, rr), 2.4)]
    p = q
GM["fist"].append(needle(tuple(np.round(p, 3)), (0, -0.8, -0.6), 2.0, 0.42, (0, 0, -0.3), 0.45))
# knuckle crystals (pop on punches) and the extending claw blades between the fingers
for z in (1.56, 0.52, -0.52, -1.56):
    GC["knuckles"] += blade((2.2, -3.4, z * 0.95), (1, -0.45, 0), 1.3, 0.5, 0.5)
for z in (1.04, 0.0, -1.04):
    GM["claws"].append(needle((0.7, -3.9, z), (-0.08, -1, 0), 6.0, 0.34, (-0.3, 0, 0), 0.5))
gk = [bone("root", (0, 0, 0)), bone("bracer", (0, 0, 0), GC["bracer"], "root"),
      bone("fin_spikes", (0, 4.4, 0), GC["fin_spikes"], "bracer"), bone("extender", (0, 0, 0), None, "root"),
      bone("fist", (0, 0, 0), GC["fist"], "root"), bone("knuckles", (2.0, -3.4, 0), GC["knuckles"], "fist"),
      bone("claws", (0, -3.9, 0), None, "fist")]
_g = geo("obsidian_gauntlet", gk, 4)
save_atlas(_g, f"{T}/item/obsidian_gauntlet.png")
w(f"{G}/item/obsidian_gauntlet.geo.json", _g)
w(f"{A}/smooth/obsidian_gauntlet.json", GM)
_gm = json.load(open(f"{A}/models/item/obsidian_gauntlet.json"))
_gm["display"].update({
    # first person: palm down, claws reaching forward, thumb toward the screen centre
    "firstperson_righthand": {"rotation": [105, -90, 0], "translation": [1.5, 3.5, -1.0], "scale": [1.15, 1.15, 1.15]},
    "firstperson_lefthand":  {"rotation": [105, -90, 0], "translation": [1.5, 3.5, -1.0], "scale": [-1.15, 1.15, 1.15]},
    "gui":    {"rotation": [15, 35, -25], "translation": [0, 0.5, 0], "scale": [0.85, 0.85, 0.85]}})
w(f"{A}/models/item/obsidian_gauntlet.json", _gm)
print("v18 organic claw:", sum(len(v) for v in GC.values()), "shard cubes,", sum(len(v) for v in GM.values()), "smooth parts")

# =====================================================================================================
# v19: simpler, more natural claw. Smooth parts only, drawn with the vanilla obsidian block texture (no shards,
#      cracks, knuckle bumps or glow). Fingers flex more at each joint like a relaxed hand: the index finger is the
#      straightest, the little finger curls the most, and they fan slightly front-to-back. Every fingertip stays at
#      x >= -2 so nothing reaches into the torso or leg while the arm hangs.
# =====================================================================================================
GM = {k: [] for k in ("bracer", "fin_spikes", "extender", "fist", "knuckles", "claws")}
GM["bracer"] += [blob((0.1, 0.6, 0), (2.35, 1.0, 2.4), 6.0),                                    # crust around the wrist
                 blob((1.2, 2.2, 0.1), (1.1, 1.9, 2.0), 3.0)]                                   # thinning up the outer forearm
GM["extender"].append(tube((0, -1.0, 0), 1.0, 1.5))
GM["fist"].append(blob((0.15, -1.6, 0), (2.4, 2.4, 2.5), 8.0))                                 # hand, encloses the arm's end
def chain(p, segs, out):
    """Capsule chain from p; segs = (angle from straight down toward the body (deg), front/back lean, length, radius)."""
    p = np.array(p, float)
    for ang, lean, ln, rr in segs:
        a = math.radians(ang)
        d = np.array([-math.sin(a), -math.cos(a), lean]); d /= np.linalg.norm(d)
        q = p + d * ln
        out.append(caps(tuple(np.round(p, 3)), tuple(np.round(q, 3)), rr))
        p = q
    return p, d
#              z     length  curl  fan
FINGERS = ((1.56, 0.95, 0.8, 0.10), (0.52, 1.0, 0.95, 0.03), (-0.52, 0.96, 1.05, -0.03), (-1.56, 0.8, 1.2, -0.10))
for z0, L, k, fan in FINGERS:
    tip, d = chain((1.3, -3.6, z0 * 0.95), ((10 * k, fan, 1.7 * L, 0.58), (30 * k, fan * 0.6, 1.3 * L, 0.5), (45 * k, fan * 0.3, 0.9 * L, 0.43)), GM["fist"])
    GM["fist"].append(needle(tuple(np.round(tip, 3)), tuple(np.round(d, 4)), 2.0 * L, 0.42, (-0.25 * k, 0.1, 0), 0.0))   # talon
# thumb: rests forward and down beside the index finger; thick and set well out on the front of the hand so it shows
p = np.array((-0.7, -1.3, 2.45))
GM["fist"].append(blob(tuple(p), (0.75, 0.9, 0.55), 2.6))                                       # its base pad
for d, ln, rr in (((-0.2, -0.7, 0.68), 1.6, 0.8), ((-0.25, -0.95, 0.2), 1.3, 0.68)):
    d = np.array(d) / np.linalg.norm(d); q = p + d * ln
    GM["fist"].append(caps(tuple(np.round(p, 3)), tuple(np.round(q, 3)), rr)); p = q
GM["fist"].append(needle(tuple(np.round(p, 3)), (-0.2, -0.95, -0.2), 1.9, 0.52, (-0.1, 0, -0.2), 0.0))
for z in (1.04, 0.0, -1.04):                                                                   # extending claw blades
    GM["claws"].append(needle((0.9, -3.9, z), (-0.08, -1, 0), 6.0, 0.34, (-0.3, 0, 0), 0.0))
_tips = [c for c in GM["fist"] if c["type"] == "thorn"]
assert all(c["a"][0] + c["b"][0] * c["len"] + c["c"][0] >= -2.05 for c in _tips), "a fingertip reaches into the body"
gk = [bone("root", (0, 0, 0)), bone("bracer", (0, 0, 0), None, "root"), bone("fin_spikes", (0, 4.4, 0), None, "bracer"),
      bone("extender", (0, 0, 0), None, "root"), bone("fist", (0, 0, 0), None, "root"),
      bone("knuckles", (2.0, -3.4, 0), None, "fist"), bone("claws", (0, -3.9, 0), None, "fist")]
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", gk, 4))
w(f"{A}/smooth/obsidian_gauntlet.json", GM)
print("v19 simple claw:", sum(len(v) for v in GM.values()), "smooth parts (vanilla obsidian texture)")

# =====================================================================================================
# v20: Epic Fight battle mode. Epic Fight draws a held item at its Tool_R / Tool_L joint times a correction
#      (default translate(0,0,-0.13) rotateX(-90)) and then the normal third-person display transform, skipping
#      vanilla's own hand offset. The correction below is the exact vanilla equivalent on the forearm:
#      inverse(Tool rest, from the biped armature) * forearm-to-vanilla-arm frame * vanilla ItemInHandLayer offset,
#      so the claw lines up with the arm exactly as it does without Epic Fight. "alwaysInHand" like Epic Fight's glove.
#      Epic Fight reads assets/<mod>/item_skins/<item>.json itself; without Epic Fight the file is simply unused.
#      (Right with Epic Fight's own poses; the add-on's claw animations once twisted the arm half a turn about itself,
#      which made the claw face backwards there - fixed in the add-on's tools/ef_moves.py, not here.)
# =====================================================================================================
_hand = {"translation": [0.0, -0.0579, -0.1601], "rotation": [-84.73, 0.0, 0.0], "scale": [1.0, 1.0, 1.0]}
w(f"{A}/item_skins/obsidian_gauntlet.json", {"renderer": "minecraft:base", "alwaysInHand": True,
                                            "transforms": {"mainhand": {"Tool_R": _hand}, "offhand": {"Tool_L": _hand}}})
print("v20 Epic Fight item skin for the gauntlet")

# =====================================================================================================
# v21: Epic Fight claw moveset hooks (triggered by the obsidian_ef_compat add-on). claws_out: the extending claw
#      blades held out (the add-on re-triggers it through its idle / walk / run loops, so they stay out); claw_strike:
#      the blades already out, snapped longer for a rake and held out after it.
# =====================================================================================================
_ga = json.load(open(f"{AN}/item/obsidian_gauntlet.animation.json"))
_ga["animations"]["animation.obsidian_gauntlet.claws_out"] = {"animation_length": 2.6, "bones": {
    "claws": {"scale": {"0.0": [1, 1, 1], "1.3": [1, 1.08, 1], "2.6": [1, 1, 1]}},
    "knuckles": {"scale": {"0.0": [1, 1, 1], "1.3": [1.06, 1.06, 1.06], "2.6": [1, 1, 1]}},
    "extender": {"scale": {"0.0": [1, 0.01, 1]}}}}
_ga["animations"]["animation.obsidian_gauntlet.claw_strike"] = {"animation_length": 1.6, "bones": {
    "claws": {"scale": {"0.0": [1, 1, 1], "0.1": {"vector": [1, 1.4, 1], "easing": "easeOutBack"}, "0.32": [1, 1.4, 1],
                        "0.6": [1, 1, 1], "1.6": [1, 1, 1]}},
    "knuckles": {"scale": {"0.0": [1, 1, 1], "0.1": [1.35, 1.35, 1.35], "0.3": [1, 1, 1]}},
    "extender": {"scale": {"0.0": [1, 0.01, 1]}}}}
w(f"{AN}/item/obsidian_gauntlet.animation.json", _ga)
print("v21 gauntlet claws_out / claw_strike")


# =====================================================================================================
# v22: the whole claw 1.2x larger, scaled from the wrist (it still encloses the arm), then set 0.35 px outward so its
#      inner side stays clear of the torso as before. The extender keeps its 1 px length (the stretch attacks scale
#      it by exactly the distance the hand moves), only its girth grows.
# =====================================================================================================
CLAW_SCALE, CLAW_OUT = 1.2, 0.35
def _grow(v, point=True):
    v = [x * CLAW_SCALE for x in v]
    if point: v[0] += CLAW_OUT
    return [round(x, 3) for x in v]
def _grow_part(p, bone):
    p = dict(p)
    if bone == "extender":
        p["r"] = round(p["r"] * CLAW_SCALE, 3); p["r2"] = round(p.get("r2", p["r"]) * CLAW_SCALE, 3)
        p["a"] = [CLAW_OUT] + list(p["a"][1:])
        return p
    p["a"] = _grow(p["a"])
    t = p["type"]
    if t == "blob": p["b"] = _grow(p["b"], False)
    elif t == "capsule": p["b"] = _grow(p["b"]); p["r"] = round(p["r"] * CLAW_SCALE, 3)
    elif t == "thorn":
        p["c"] = _grow(p["c"], False); p["len"] = round(p["len"] * CLAW_SCALE, 3); p["r"] = round(p["r"] * CLAW_SCALE, 3)
    elif t == "tube": p["len"] = round(p["len"] * CLAW_SCALE, 3); p["r"] = round(p["r"] * CLAW_SCALE, 3)
    return p
GM = {k: [_grow_part(p, k) for p in v] for k, v in GM.items()}
_tips = [c for c in GM["fist"] if c["type"] == "thorn"] + [c for c in GM["fist"] if c["type"] == "capsule"]
assert min(min(c["a"][0], (c["b"][0] if c["type"] == "capsule" else c["a"][0] + c["b"][0] * c["len"] + c["c"][0])) for c in _tips) >= -2.6, \
    "the scaled claw reaches into the body"
w(f"{A}/smooth/obsidian_gauntlet.json", GM)
gk = [dict(b, pivot=_grow(b["pivot"]) if b["name"] not in ("root", "extender", "fist") else b["pivot"]) for b in gk]
w(f"{G}/item/obsidian_gauntlet.geo.json", geo("obsidian_gauntlet", gk, 4))
print("v22 claw scaled", CLAW_SCALE, "with a bigger thumb")

# =====================================================================================================
# v23: jagged obsidian spikes. Six OBJ models (models/obj/spike_0..5.obj): a unit spike along +Y from its base
#      (y=0, radius 1) to a sharp tip (y=1), an irregular faceted cone with knocked-off ridges and a crooked point,
#      flat faces, UVs tiling the vanilla obsidian texture. SmoothPartsLayer draws them ("obj" parts: base a, direction
#      b, length len, radius r, roll p) with the same vanilla obsidian texture as the gauntlet. Used by the armor's
#      spikes (v17 blade()), the Spike Explosion's ground spikes and the Obsidian Storm's spikes (cubes replaced).
# =====================================================================================================
def jagged_obj(seed):
    rs = np.random.RandomState(1000 + seed)
    sides = int(rs.choice([5, 6, 7]))
    rings = [(0.0, 1.0), (0.28, 0.78), (0.55, 0.5), (0.8, 0.24)]   # (height, radius)
    pts = []
    for k, (h, r) in enumerate(rings):
        ring = []
        for i in range(sides):
            a = 2 * math.pi * (i + rs.uniform(-0.22, 0.22)) / sides
            rr = r * rs.uniform(0.72, 1.12) * (1.0 if k == 0 else rs.uniform(0.85, 1.1))
            hh = h + (rs.uniform(-0.05, 0.05) if k else 0.0)
            ring.append((rr * math.cos(a), hh, rr * math.sin(a), i / sides, h))
        pts.append(ring)
    tip = (rs.uniform(-0.08, 0.08), 1.0, rs.uniform(-0.08, 0.08))
    lines = [f"# jagged obsidian spike {seed}, generated by tools/gen_assets.py (v23)", "o spike"]
    vs, vts, faces = [], [], []
    def v(p, uv):
        vs.append(p); vts.append(uv); return len(vs)
    for k in range(len(rings) - 1):
        a, b = pts[k], pts[k + 1]
        for i in range(sides):
            j = (i + 1) % sides
            u0, u1 = i / sides * 2, (i + 1) / sides * 2
            q = [v(a[i][:3], (u0, a[i][4] * 2)), v(a[j][:3], (u1, a[j][4] * 2)), v(b[j][:3], (u1, b[j][4] * 2)), v(b[i][:3], (u0, b[i][4] * 2))]
            faces += [(q[0], q[1], q[2]), (q[0], q[2], q[3])]
    top = pts[-1]
    for i in range(sides):
        j = (i + 1) % sides
        u0, u1 = i / sides * 2, (i + 1) / sides * 2
        faces.append((v(top[i][:3], (u0, 1.6)), v(top[j][:3], (u1, 1.6)), v(tip, ((u0 + u1) / 2, 2.0))))
    base = pts[0]
    c = v((0.0, 0.0, 0.0), (0.5, 0.5))
    for i in range(sides):
        j = (i + 1) % sides
        faces.append((c, v(base[j][:3], (0.5 + 0.5 * base[j][0], 0.5 + 0.5 * base[j][2])), v(base[i][:3], (0.5 + 0.5 * base[i][0], 0.5 + 0.5 * base[i][2]))))
    lines += [f"v {p[0]:.4f} {p[1]:.4f} {p[2]:.4f}" for p in vs]
    lines += [f"vt {t[0]:.4f} {t[1]:.4f}" for t in vts]
    lines += [f"f {a}/{a} {b}/{b} {c_}/{c_}" for a, b, c_ in faces]
    return "\n".join(lines) + "\n"
for _k in range(6):
    w(f"{A}/models/obj/spike_{_k}.obj", jagged_obj(_k), raw=True)

# ---- the Spike Explosion's ground spikes and the Obsidian Storm's spikes: the cube towers become jagged spikes
SPIKE_N[0] = 0
def _spike_bones(model, parts):
    g = json.load(open(f"{G}/entity/{model}.geo.json"))
    for b in g["minecraft:geometry"][0]["bones"]:
        b.pop("cubes", None)
    w(f"{G}/entity/{model}.geo.json", g)
    w(f"{A}/smooth/{model}.json", parts)
_spike_bones("obsidian_spike", {
    "spike": [spike_part((0, -2, 0), (0, 1, 0), 56, 7.5)],
    "spikeL": [spike_part((-8, -2, 0), (0, 1, 0), 36, 4.4)], "spikeR": [spike_part((8, -2, 0), (0, 1, 0), 36, 4.4)],
    "spikeB": [spike_part((0, -2, 8), (0, 1, 0), 32, 4.0)], "spikeF": [spike_part((0, -2, -8), (0, 1, 0), 29, 3.6)]})
_spike_bones("obsidian_storm_spike", {"spike": [spike_part((0, 0, 0), (0, 1, 0), 34, 3.3)]})
print("v23 jagged OBJ spikes: armor", sum(len(v) for v in ASP.values()), "+ ground and storm spikes")

# =====================================================================================================
# v24: a grasping claw. Every finger hangs at its own angle (the index nearly straight and fanned forward, each
#      finger behind it curling further and fanning further back, a little more splayed apart), and the thumb is
#      a true opposing digit: it leaves the palm on the inner side and curls down and out toward the fingertips, so
#      the claw closes like a hand gripping. Built at v19 size, then 1.2x from the wrist like v22.
# =====================================================================================================
GM = {k: [] for k in ("bracer", "fin_spikes", "extender", "fist", "knuckles", "claws")}
GM["bracer"] += [blob((0.1, 0.6, 0), (2.35, 1.0, 2.4), 6.0),                                    # crust around the wrist
                 blob((1.2, 2.2, 0.1), (1.1, 1.9, 2.0), 3.0)]                                   # thinning up the outer forearm
GM["extender"].append(tube((0, -1.0, 0), 1.0, 1.5))
GM["fist"].append(blob((0.15, -1.6, 0), (2.4, 2.4, 2.5), 8.0))                                 # hand, encloses the arm's end
#                z     length curl  fan    first-joint angle (deg toward the body), talon bend
FINGERS24 = ((1.66, 1.0, 0.45, 0.26, -6.0, 0.08), (0.56, 1.1, 0.7, 0.07, -1.0, 0.16),
             (-0.52, 1.0, 0.88, -0.12, 2.0, 0.22), (-1.56, 0.8, 1.02, -0.3, 5.0, 0.26))
for z0, L, k, fan, a0, bend in FINGERS24:
    tip, d = chain((1.3, -3.6, z0 * 0.95),
                   ((a0 + 10 * k, fan, 1.7 * L, 0.58), (a0 + 30 * k, fan * 0.7, 1.3 * L, 0.5), (a0 + 44 * k, fan * 0.45, 0.9 * L, 0.43)),
                   GM["fist"])
    GM["fist"].append(needle(tuple(np.round(tip, 3)), tuple(np.round(d, 4)), 2.0 * L, 0.42, (-bend, 0.05, fan * 0.3), 0.0))   # talon
# opposing thumb: a thick pad on the inner front of the palm, then down and outward toward the index fingertip
p = np.array((-1.4, -1.6, 2.05))                                                               # (shifted toward the body, user request)
GM["fist"].append(blob(tuple(p), (0.8, 1.0, 0.7), 2.6))                                         # thenar pad
for dv, ln, rr in (((-0.05, -0.75, 0.42), 1.6, 0.8), ((0.3, -0.85, -0.1), 1.35, 0.68)):
    dv = np.array(dv) / np.linalg.norm(dv); q = p + dv * ln
    GM["fist"].append(caps(tuple(np.round(p, 3)), tuple(np.round(q, 3)), rr)); p = q
GM["fist"].append(needle(tuple(np.round(p, 3)), (0.25, -0.9, -0.25), 1.9, 0.52, (0.2, 0, -0.1), 0.0))    # thumb talon, curling at the fingers
for z in (1.04, 0.0, -1.04):                                                                   # extending claw blades
    GM["claws"].append(needle((0.9, -3.9, z), (-0.08, -1, 0), 6.0, 0.34, (-0.3, 0, 0), 0.0))
_tips = [c for c in GM["fist"] if c["type"] == "thorn"]
_reach = [round(c["a"][0] + c["b"][0] * c["len"] + c["c"][0], 2) for c in _tips]
print("v24 talon tips x:", _reach)
assert min(_reach) >= -2.05, "a fingertip reaches into the body"
GM = {k: [_grow_part(p, k) for p in v] for k, v in GM.items()}
w(f"{A}/smooth/obsidian_gauntlet.json", GM)
print("v24 grasping claw with an opposing thumb:", sum(len(v) for v in GM.values()), "smooth parts")

# v24: the grappling hook is on its own key now (right-click guards in Epic Fight battle mode)
_l = json.load(open(f"{A}/lang/en_us.json"))
_l[f"tooltip.{MOD}.gauntlet.grapple"] = "Grapple key (default H): grappling hook (80 blocks), press again to let go"
_l[f"key.{MOD}.grapple"] = "Gauntlet Grappling Hook"
_l[f"key.categories.{MOD}"] = "Obsidian Arsenal"
w(f"{A}/lang/en_us.json", _l)
print("v24 lang for the grapple key")
