"""Reports how far each animation twists the arms about their own bones, compared with the armature at rest
(swing-twist split of each arm joint's local rotation about the bone axis, its local +Y): Epic Fight's own poses keep
it near 0, and the gauntlet claw (held along the forearm) is drawn turned by the same amount.

    python tools/arm_twist.py name [name...]       (names in the gauntlet folder, or paths to other animation files)
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ef_moves as em  # noqa: E402

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "obsidian_ef_compat",
                   "animmodels", "animations", "biped", "gauntlet")


def twist(rot):
    """The twist (degrees) of a rotation about local +Y."""
    w = math.sqrt(max(0.0, 1 + rot[0, 0] + rot[1, 1] + rot[2, 2])) / 2
    y = (rot[0, 2] - rot[2, 0]) / (4 * w) if w > 1e-6 else math.sqrt(max(0.0, (1 - rot[0, 0] + rot[1, 1] - rot[2, 2]) / 4))
    return math.degrees(2 * math.atan2(y, w))


def report(path):
    tracks = {t["name"]: t for t in json.load(open(path))["animation"]}
    out = []
    for j in ("Arm_R", "Hand_R", "Arm_L", "Hand_L"):
        vals = [twist(np.linalg.inv(em.REST[j][:3, :3]) @ np.array(m).reshape(4, 4)[:3, :3]) for m in tracks[j]["transform"]]
        out.append(f"{j} {min(vals):7.1f}..{max(vals):6.1f}")
    return "  ".join(out)


if __name__ == "__main__":
    for n in sys.argv[1:]:
        p = n if os.path.isfile(n) else os.path.join(DIR, n + ".json")
        print(f"{os.path.basename(p):22s} {report(p)}")
