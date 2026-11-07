"""Stick-figure comparison of original (grey) vs exaggerated (purple) gauntlet animations, front and side views.

    python tools/preview_anims.py out.png name[,name...] [frames]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ORIG = os.path.join(HERE, "..", "original", "assets", "obsidian_ef_compat", "animmodels", "animations", "biped", "gauntlet")
NEW = os.path.join(HERE, "..", "src", "main", "resources", "assets", "obsidian_ef_compat", "animmodels", "animations", "biped", "gauntlet")
_arm = json.load(open(os.path.join(HERE, "biped_armature.json")))["armature"]
PARENT, ORDER = {}, []


def _walk(node, parent):
    PARENT[node["name"]] = parent; ORDER.append(node["name"])
    for c in node.get("children", []): _walk(c, node["name"])


for _n in (_arm["hierarchy"] if isinstance(_arm["hierarchy"], list) else [_arm["hierarchy"]]): _walk(_n, None)
BONES = [("Root", "Torso"), ("Torso", "Chest"), ("Chest", "Head"), ("Chest", "Arm_R"), ("Arm_R", "Hand_R"), ("Hand_R", "Tool_R"),
         ("Chest", "Arm_L"), ("Arm_L", "Hand_L"), ("Hand_L", "Tool_L"), ("Root", "Thigh_R"), ("Thigh_R", "Leg_R"),
         ("Root", "Thigh_L"), ("Thigh_L", "Leg_L")]


COORD = np.array([[1, 0, 0, 0], [0, 0, -1, 0], [0, 1, 0, 0], [0, 0, 0, 1]], float)


def frame(anim, t):
    tracks = {tr["name"]: tr for tr in anim}
    # an attack's Root sits inside its Coord track (root motion), which turns Blender's axes into Minecraft's
    coord = COORD if "Coord" in tracks else np.eye(4)
    world = {}
    for j in ORDER:
        if j not in tracks: continue
        tr = tracks[j]; times = tr["time"]
        i = min(range(len(times)), key=lambda k: abs(times[k] - t))
        local = np.array(tr["transform"][i]).reshape(4, 4)
        world[j] = coord @ local if PARENT[j] is None or PARENT[j] not in world else world[PARENT[j]] @ local
    return world


def draw(d, world, ox, oy, view, col, s=150):
    def pt(j, extra=None):
        p = world[j][:3, 3] if extra is None else (world[j] @ np.array([*extra, 1]))[:3]
        a, b = (p[0], p[2]) if view == "front" else (p[1], p[2])
        return ox + a * s, oy - b * s
    for a, b in BONES:
        if a in world and b in world: d.line([pt(a), pt(b)], fill=col, width=3)
    for leg in ("Leg_R", "Leg_L"):
        if leg in world: d.line([pt(leg), pt(leg, (0, 0.38, 0))], fill=col, width=3)
    if "Head" in world:
        x, y = pt("Head", (0, 0.12, 0)); d.ellipse([x - 9, y - 9, x + 9, y + 9], outline=col, width=2)


def main():
    out, names = sys.argv[1], sys.argv[2].split(",")
    nf = int(sys.argv[3]) if len(sys.argv) > 3 else 6
    W, H = 150, 260
    img = Image.new("RGB", (W * nf, H * 2 * len(names)), (235, 238, 245)); d = ImageDraw.Draw(img)
    for r, name in enumerate(names):
        orig = os.path.join(ORIG, name + ".json")          # a move with no original is compared with itself
        a0 = json.load(open(orig if os.path.exists(orig) else os.path.join(NEW, name + ".json")))["animation"]
        a1 = json.load(open(os.path.join(NEW, name + ".json")))["animation"]
        end = a0[0]["time"][-1]
        for c in range(nf):
            t = end * c / max(1, nf - 1)
            for v, view in enumerate(("front", "side")):
                oy = H * (2 * r + v) + 200; ox = W * c + W // 2
                draw(d, frame(a0, t), ox, oy, view, (150, 150, 160))
                draw(d, frame(a1, t), ox, oy, view, (130, 50, 200))
            d.text((W * c + 4, H * 2 * r + 4), f"{name} {t:.2f}s", fill=(0, 0, 0))
    img.save(out)


if __name__ == "__main__":
    main()
