"""Writes the Obsidian Stretch dodge animation (biped/gauntlet/stretch_dodge.json), 0.45 s, from the gauntlet guard
(the idle's first frame): a quick crouch to load, then a launch pose held while the body is stretched (lean in, arms
swept back, chin up), then a settle back to the guard. No root motion: StretchFx moves the player.

    python tools/gen_stretch_dodge.py
"""
import copy
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from exaggerate_anims import DST, FWD, LAT, ORDER, PARENT, SRC, UP, axis_rot  # noqa: E402

LENGTH, FPS = 0.45, 30


def smooth(a, b, t):
    if t <= a: return 0.0
    if t >= b: return 1.0
    x = (t - a) / (b - a); return x * x * (3 - 2 * x)


def crouch(t): return smooth(0.0, 0.06, t) * (1 - smooth(0.06, 0.16, t))
def launch(t): return smooth(0.04, 0.12, t) * (1 - smooth(0.26, 0.45, t))


def pose(t):
    c, l = crouch(t), launch(t)
    return [("Thigh_R", LAT, 14 * c - 10 * l), ("Thigh_L", LAT, 14 * c - 6 * l),
            ("Leg_R", LAT, -28 * c - 8 * l), ("Leg_L", LAT, -28 * c - 4 * l),
            ("Torso", LAT, -6 * c - 8 * l), ("Chest", LAT, -4 * c - 10 * l),
            ("Arm_R", LAT, 15 * c - 45 * l), ("Arm_L", LAT, 15 * c - 45 * l),
            ("Arm_R", FWD, 18 * l), ("Arm_L", FWD, -18 * l),
            ("Hand_R", LAT, -25 * l), ("Hand_L", LAT, -25 * l),
            ("Head", LAT, 10 * l)], c


def main():
    base = json.load(open(os.path.join(SRC, "idle.json")))["animation"]
    times = [round(i / FPS, 4) for i in range(int(round(LENGTH * FPS)) + 1)]
    tracks = {}
    for tr in base:
        tracks[tr["name"]] = {"name": tr["name"], "time": list(times), "transform": [list(tr["transform"][0]) for _ in times]}
    for i, t in enumerate(times):
        adds, c = pose(t)
        root = np.array(tracks["Root"]["transform"][i]).reshape(4, 4); root[2, 3] -= 0.06 * c
        tracks["Root"]["transform"][i] = list(root.reshape(-1))
        for joint, axis, deg in adds:
            w = {}
            for j in ORDER:
                if j not in tracks: continue
                m = np.array(tracks[j]["transform"][i]).reshape(4, 4)
                w[j] = m if PARENT[j] not in w else w[PARENT[j]] @ m
            m = np.array(tracks[joint]["transform"][i]).reshape(4, 4)
            m[:3, :3] = m[:3, :3] @ axis_rot(w[joint][:3, :3].T @ axis, deg)
            tracks[joint]["transform"][i] = list(m.reshape(-1))
    out = {"animation": [{"name": n, "time": tr["time"], "transform": [[round(float(v), 6) for v in m] for m in tr["transform"]]}
                         for n, tr in ((tr["name"], tracks[tr["name"]]) for tr in base)]}
    os.makedirs(DST, exist_ok=True)
    with open(os.path.join(DST, "stretch_dodge.json"), "w") as f:
        json.dump(out, f, separators=(",", ":"))
    print("wrote stretch_dodge.json,", len(times), "frames")


if __name__ == "__main__":
    main()
