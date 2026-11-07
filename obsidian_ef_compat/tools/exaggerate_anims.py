"""Makes the gauntlet's Epic Fight animations bigger and livelier.

Reads the original animations (original/assets/obsidian_ef_compat/animmodels/animations/biped/gauntlet/*.json) and
writes exaggerated copies to src/main/resources/assets/obsidian_ef_compat/animmodels/animations/biped/gauntlet/.

Every joint's rotation is measured against a reference pose and that difference is scaled up: for attacks the
reference is the first frame (the guard the move starts from), so wind-ups go further back, strikes further through
and recoveries swing more; for the idle / walk / run loops it is the loop's average pose, so the stance stays the same
and the sway, bounce and arm pump grow. The Root (root motion and facing) and the Tool joints (weapon alignment) are
left alone, so timing, reach and hit direction don't change. A joint never gains more than CAP degrees.

The loops also get motion they never had (lively()): the idle a boxer's bounce (hips dip with the knees), a slow
chest/torso sway, alternating arm pumps in the guard and a head counter-sway; walk and run a chest twist and arm pump
in counter-phase to the legs. Every added wave fits a whole number of cycles into the loop, so it stays seamless.

    python tools/exaggerate_anims.py
"""
import glob
import json
import math
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "original", "assets", "obsidian_ef_compat", "animmodels", "animations", "biped", "gauntlet")
DST = os.path.join(HERE, "..", "src", "main", "resources", "assets", "obsidian_ef_compat", "animmodels", "animations", "biped", "gauntlet")
LOOPS = {"idle", "walk", "run"}
CAP = 60.0
GAIN = {  # attacks, loops
    "Torso": (1.5, 1.7), "Chest": (1.6, 1.8), "Head": (1.2, 1.3),
    "Shoulder_R": (1.6, 1.7), "Shoulder_L": (1.6, 1.7), "Arm_R": (1.6, 1.8), "Arm_L": (1.6, 1.8),
    "Hand_R": (1.5, 1.7), "Hand_L": (1.5, 1.7), "Elbow_R": (1.5, 1.7), "Elbow_L": (1.5, 1.7),
    "Thigh_R": (1.4, 1.4), "Thigh_L": (1.4, 1.4), "Leg_R": (1.4, 1.4), "Leg_L": (1.4, 1.4),
    "Knee_R": (1.4, 1.4), "Knee_L": (1.4, 1.4),
}
# the animation's world axes (as the armature faces): +x across the body, +y forward, +z up
LAT, FWD, UP = np.array([1.0, 0, 0]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0])
_arm = json.load(open(os.path.join(HERE, "biped_armature.json")))["armature"]
PARENT, ORDER = {}, []


def _walk(node, parent):
    PARENT[node["name"]] = parent; ORDER.append(node["name"])
    for c in node.get("children", []): _walk(c, node["name"])


for _n in (_arm["hierarchy"] if isinstance(_arm["hierarchy"], list) else [_arm["hierarchy"]]): _walk(_n, None)


def quat(m):
    """Rotation matrix -> unit quaternion (w, x, y, z)."""
    t = np.trace(m)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = [0.25 * s, (m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s]
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        q = [(m[2, 1] - m[1, 2]) / s, 0.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s]
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        q = [(m[0, 2] - m[2, 0]) / s, (m[0, 1] + m[1, 0]) / s, 0.25 * s, (m[1, 2] + m[2, 1]) / s]
    else:
        s = math.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        q = [(m[1, 0] - m[0, 1]) / s, (m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, 0.25 * s]
    q = np.array(q); return q / np.linalg.norm(q)


def mat(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def qmul(a, b):
    w1, x1, y1, z1 = a; w2, x2, y2, z2 = b
    return np.array([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2])


def qinv(q): return np.array([q[0], -q[1], -q[2], -q[3]])


def scale_rot(q, k):
    """Scales a relative rotation's angle by k (capped at CAP extra degrees)."""
    if q[0] < 0: q = -q
    ang = 2 * math.acos(min(1.0, q[0]))
    if ang < 1e-6: return q
    axis = q[1:] / math.sin(ang / 2)
    new = min(ang * k, ang + math.radians(CAP))
    return np.array([math.cos(new / 2), *(axis * math.sin(new / 2))])


def axis_rot(axis, deg):
    a = np.array(axis, float); a /= np.linalg.norm(a); h = math.radians(deg) / 2
    return mat(np.array([math.cos(h), *(a * math.sin(h))]))


def lively(name, tracks):
    """Adds looping motion to idle / walk / run: rotations about world axes, applied in each joint's own frame."""
    T = tracks["Root"]["time"][-1]
    n = len(tracks["Root"]["time"])
    wave = lambda i, cycles, phase=0.0: math.sin(2 * math.pi * cycles * tracks["Root"]["time"][i] / T + phase)
    # world frames of every joint per frame, from the (already exaggerated) local transforms
    def worlds(i):
        w = {}
        for j in ORDER:
            if j not in tracks: continue
            m = np.array(tracks[j]["transform"][i]).reshape(4, 4)
            w[j] = m if PARENT[j] not in w else w[PARENT[j]] @ m
        return w
    adds = []   # (frame, joint, world axis, degrees)
    for i in range(n):
        if name == "idle":
            b = 0.5 - 0.5 * math.cos(2 * math.pi * 4 * tracks["Root"]["time"][i] / T)   # 4 bounces, 0..1
            root = np.array(tracks["Root"]["transform"][i]).reshape(4, 4); root[2, 3] -= 0.024 * b
            tracks["Root"]["transform"][i] = list(root.reshape(-1))
            adds += [(i, "Thigh_R", LAT, 8 * b), (i, "Thigh_L", LAT, 8 * b), (i, "Leg_R", LAT, -15 * b), (i, "Leg_L", LAT, -15 * b),
                     (i, "Chest", UP, 7 * wave(i, 1)), (i, "Torso", FWD, 2.5 * wave(i, 1, math.pi / 2)),
                     (i, "Arm_R", LAT, 9 * wave(i, 2)), (i, "Arm_L", LAT, 9 * wave(i, 2, math.pi)),
                     (i, "Head", UP, -3.5 * wave(i, 1))]
        else:
            # leg phase from the right thigh's forward swing
            w = worlds(i); d = w["Leg_R"][:3, 3] - w["Thigh_R"][:3, 3]
            swing = math.atan2(d[1], -d[2]) / math.radians(30)
            twist, pump = (10, 14) if name == "walk" else (8, 10)
            adds += [(i, "Chest", UP, -twist * swing), (i, "Arm_R", LAT, -pump * swing), (i, "Arm_L", LAT, pump * swing),
                     (i, "Head", UP, 0.4 * twist * swing)]
    for i, j, axis, deg in adds:
        w = worlds(i)
        m = np.array(tracks[j]["transform"][i]).reshape(4, 4)
        local_axis = w[j][:3, :3].T @ axis
        m[:3, :3] = m[:3, :3] @ axis_rot(local_axis, deg)
        tracks[j]["transform"][i] = list(m.reshape(-1))


def reference(qs, loop):
    if not loop: return qs[0]
    acc = np.zeros(4)
    for q in qs: acc += q if np.dot(q, qs[0]) >= 0 else -q
    return acc / np.linalg.norm(acc)


def run():
    os.makedirs(DST, exist_ok=True)
    for path in sorted(glob.glob(os.path.join(SRC, "*.json"))):
        name = os.path.splitext(os.path.basename(path))[0]
        data = json.load(open(path))
        loop = name in LOOPS
        for track in data["animation"]:
            gain = GAIN.get(track["name"])
            if not gain: continue
            k = gain[1] if loop else gain[0]
            ms = [np.array(t, float).reshape(4, 4) for t in track["transform"]]
            qs = [quat(m[:3, :3]) for m in ms]
            ref = reference(qs, loop)
            out = []
            for m, q in zip(ms, qs):
                rel = qmul(qinv(ref), q)
                m = m.copy(); m[:3, :3] = mat(qmul(ref, scale_rot(rel, k)))
                out.append([round(float(v), 6) for v in m.reshape(-1)])
            track["transform"] = out
        if loop:
            tracks = {tr["name"]: tr for tr in data["animation"]}
            lively(name, tracks)
            for tr in data["animation"]:
                tr["transform"] = [[round(float(v), 6) for v in t] for t in tr["transform"]]
        with open(os.path.join(DST, name + ".json"), "w") as f:
            json.dump(data, f, separators=(",", ":"))
        print("exaggerated", name, "(loop)" if loop else "")


if __name__ == "__main__":
    run()
