"""Core of Void Powers Epic Fight addon's tools/gen_ef_animations.py (the skeleton solver, easing, the arc cuts and combo()), copied
here unchanged so the gauntlet's claw attacks are made the same way. Output paths are set by gen_claw_anims.py."""
import math
import json
import os

import numpy as np

MODID = "vpepicfight"
ANIM_DIR = os.path.join("src", "main", "resources", "assets", MODID, "animmodels", "animations", "biped", "combat")
TYPES_DIR = os.path.join("src", "main", "resources", "data", MODID, "capabilities", "weapons", "types")
ITEMS_DIR = os.path.join("src", "main", "resources", "data", "voidpowers", "capabilities", "weapons")
JAVA = os.path.join("src", "main", "java", "com", "vpepicfight", "VPAnimations.java")
FPS = 60   # keys a third of a tick apart: a fast cut turns the arm well over 90 degrees a tick, and a joint turned
#            that far between two keys can be carried the wrong way round (through the body) by the interpolation

# ------------------------------------------------------------------ the armature at rest
_arm = json.load(open(os.path.join("tools", "biped_armature.json")))["armature"]
REST, PARENT, ORDER = {}, {}, []


def _walk(node, parent):
    REST[node["name"]] = np.array(node["transform"], float).reshape(4, 4)
    PARENT[node["name"]] = parent
    ORDER.append(node["name"])
    for c in node.get("children", []):
        _walk(c, node["name"])


for _n in (_arm["hierarchy"] if isinstance(_arm["hierarchy"], list) else [_arm["hierarchy"]]):
    _walk(_n, None)
REST_WORLD = {}
for _j in ORDER:
    REST_WORLD[_j] = REST[_j] if PARENT[_j] is None else REST_WORLD[PARENT[_j]] @ REST[_j]
P0 = {j: REST_WORLD[j][:3, 3].copy() for j in ORDER}
UPPER = float(np.linalg.norm(P0["Hand_R"] - P0["Arm_R"]))      # shoulder to elbow
FORE = float(np.linalg.norm(P0["Tool_R"] - P0["Hand_R"]))       # elbow to grip

# Minecraft's model space in the armature's: +x is the player's left (the armature's -x), +y down (-z), +z back (-y)
V = np.array([[-1, 0, 0], [0, 0, -1], [0, -1, 0]], float).T
# the moves are written with -y forward; the armature faces +y (as Epic Fight's own attacks show: the blade and the root
# motion go to +y), so every hand position and direction is turned round when it's solved
AUTHOR = np.diag([1.0, -1.0, 1.0])
# the Root is stored inside Coord (root motion), which turns Blender's axes into Minecraft's, as Epic Fight's own do
COORD = np.array([[1, 0, 0, 0], [0, 0, -1, 0], [0, 1, 0, 0], [0, 0, 0, 1]], float)
COORD_INV = np.linalg.inv(COORD)

FWD, RIGHT, UP = np.array([0.0, -1, 0]), np.array([1.0, 0, 0]), np.array([0.0, 0, 1])   # (as the moves are written)


def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def mc(deg):
    """A Minecraft model part's rotation (x, y, z degrees, applied Z * Y * X), in the armature's axes:
    pitch + bends forward, yaw + turns toward the player's right."""
    x, y, z = (math.radians(d) for d in deg)
    return V @ (_rz(z) @ _ry(y) @ _rx(x)) @ V.T


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def axis_angle(axis, angle):
    a = unit(np.asarray(axis, float))
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(angle) * K + (1 - math.cos(angle)) * K @ K


def between(a, b):
    """The smallest rotation taking direction a to direction b."""
    a, b = unit(a), unit(b)
    c = float(np.clip(np.dot(a, b), -1, 1))
    axis = np.cross(a, b)
    if np.linalg.norm(axis) < 1e-8:
        if c > 0:
            return np.eye(3)
        axis = np.cross(a, [1, 0, 0]) if abs(a[0]) < 0.9 else np.cross(a, [0, 1, 0])
    return axis_angle(axis, math.acos(c))


def frame(b, e):
    """An orthonormal frame from a direction b and a second direction e (made square to b)."""
    b = unit(b)
    e = unit(e - b * np.dot(e, b))
    return np.column_stack([b, e, np.cross(b, e)])


# A held weapon at rest (the arm hanging): the blade out of the fist forward and a touch down (as Minecraft holds a
# sword), its edge (single-edged blades) facing the ground
BLADE0 = unit(V @ np.array([0.0, 0.17, -0.98]))
EDGE0 = unit(np.array([0.0, 0.0, -1.0]) - BLADE0 * np.dot([0, 0, -1.0], BLADE0))
TOOL0 = frame(BLADE0, EDGE0)
UPPER0 = unit(P0["Hand_R"] - P0["Arm_R"])
FORE0 = unit(P0["Tool_R"] - P0["Hand_R"])
# the elbow's hinge at rest (the arm hanging, the forearm a touch forward): across the body
HINGE0 = unit(np.cross(UPPER0, FORE0))


# ------------------------------------------------------------------ one moment of a move onto the skeleton
LAST_HINGE = {}   # each arm's elbow hinge in the moment solved last (reset for every animation)
LAST_EDGE = {}    # and each hand's weapon edge


def ik(shoulder, target, pole):
    """Two-bone IK: the elbow and the grip for a hand reaching target, the elbow bent toward pole."""
    d = target - shoulder
    dist = float(np.linalg.norm(d))
    dist = min(max(dist, abs(UPPER - FORE) + 1e-3), UPPER + FORE - 1e-3)
    u = unit(d)
    grip = shoulder + u * dist
    cos_a = (UPPER ** 2 + dist ** 2 - FORE ** 2) / (2 * UPPER * dist)
    a = math.acos(max(-1.0, min(1.0, cos_a)))
    v = unit(pole - u * np.dot(pole, u))
    elbow = shoulder + UPPER * (math.cos(a) * u + math.sin(a) * v)
    return elbow, grip


def solve(st):
    """One moment (a dict): body angles, legs and each hand's grip, blade and edge -> every joint's local transform."""
    g = lambda k, d=(0, 0, 0): st.get(k, d)
    # the whole body turned about its middle: a spin (yaw), a flip (pitch); the hands go round with it
    whole = mc((st.get("flip", 0.0), st.get("spin", 0.0), 0))
    pivot = np.array([0.0, 0.0, 0.95])
    hips = whole @ mc(g("hips"))
    absd = {"Root": hips}
    absd["Torso"] = hips @ mc(g("waist"))
    absd["Chest"] = absd["Torso"] @ mc(g("chest"))
    absd["Head"] = absd["Chest"] @ mc(g("head"))
    for s, side in (("R", "right"), ("L", "left")):
        absd["Shoulder_" + s] = absd["Chest"]
        leg = hips @ mc(g(side + "_leg"))
        knee = g(side + "_knee", 0)
        absd["Thigh_" + s] = leg
        absd["Leg_" + s] = leg @ mc((knee, 0, 0))
        absd["Knee_" + s] = leg @ mc((knee * 0.5, 0, 0))
        absd["Arm_" + s] = absd["Hand_" + s] = absd["Elbow_" + s] = absd["Tool_" + s] = absd["Chest"]

    # the trunk first (the hips about the feet, dropped by sink), so the shoulders are where the arms start from
    world = {}

    def place(j):
        par = PARENT[j]
        if par is None:
            pos = pivot + whole @ (mc(g("hips")) @ P0[j] + np.array([0, 0, -st.get("sink", 0.0)]) - pivot)
        else:
            pos = world[par][:3, 3] + absd[par] @ (P0[j] - P0[par])
        w = np.eye(4)
        w[:3, :3] = absd[j] @ REST_WORLD[j][:3, :3]
        w[:3, 3] = pos
        world[j] = w

    for j in ORDER:
        if j.startswith(("Arm_", "Hand_", "Elbow_", "Tool_")):
            continue
        place(j)
    # each arm to its grip
    for s, side in (("R", "right"), ("L", "left")):
        hand = st.get(side + "_hand")
        shoulder = world["Shoulder_" + s][:3, 3] + absd["Chest"] @ (P0["Arm_" + s] - P0["Shoulder_" + s])
        out = RIGHT if s == "R" else -RIGHT
        if hand is None:
            # hanging loose at the side
            target = shoulder + absd["Chest"] @ np.array([0.04 * out[0], 0.06, -0.52])
            blade, edge = absd["Chest"] @ BLADE0, absd["Chest"] @ EDGE0
        else:
            target = pivot + whole @ (AUTHOR @ np.asarray(hand["pos"], float) - pivot)
            blade = unit(whole @ AUTHOR @ np.asarray(hand["blade"], float))
            edge = whole @ AUTHOR @ np.asarray(hand.get("edge", UP), float)
        # which way the elbow points: the reach turned a quarter turn down about the body's side-to-side axis (reaching
        # forward the elbow points down, overhead forward, hanging back, as a real arm's does), with a little "down"
        # for a reach straight out to the side, where that turn vanishes. The two agree for any reach in front of
        # the body, so they never cancel and the elbow never flips sides
        reach0 = unit(target - shoulder)
        sq = lambda d: d - reach0 * np.dot(d, reach0)
        body_right, body_up = whole @ RIGHT, whole @ UP
        pole = unit(np.cross(reach0, body_right) + sq(-body_up) * 0.5 + sq(whole @ out) * 0.15)
        elbow, grip = ik(shoulder, target, pole)
        upper, fore = unit(elbow - shoulder), unit(grip - elbow)
        # the upper arm and forearm share the elbow's hinge, as a real arm does: each is turned so the hinge lines up,
        # so the arm only ever bends at the elbow and never twists against itself (which wrings the skin)
        reach = unit(target - shoulder)
        v = unit(pole - reach * np.dot(pole, reach))
        steady = unit(np.cross(upper, -v))                  # the hinge the elbow's rule gives: always well defined
        bent = np.cross(upper, fore)
        bend_deg = math.degrees(math.asin(min(1.0, float(np.linalg.norm(bent)))))
        bent = unit(bent) if np.linalg.norm(bent) > 1e-6 else steady
        if np.dot(bent, steady) < 0:
            bent = -bent
        # nearly straight, the bend's own hinge is all but undefined and spins from frame to frame: take the steady
        # one there, and the bend's own once the elbow is clearly bent
        w = min(1.0, bend_deg / 30.0)
        hinge = unit(steady * (1 - w) + bent * w)
        # (change from Void Powers' copy) the hinge this rule gives points the opposite way to the armature's own
        # (HINGE0), which turns the whole arm half a turn about itself against Epic Fight's poses: harmless for a blade
        # aimed on its own, but the gauntlet's claw rides the forearm, so it would face backwards
        hinge = -hinge
        LAST_HINGE[s] = hinge
        d_arm = frame(upper, hinge) @ frame(UPPER0, HINGE0).T
        d_hand = frame(fore, hinge) @ frame(FORE0, HINGE0).T
        mid = unit(upper + fore)
        d_elbow = frame(mid, hinge) @ frame(unit(UPPER0 + FORE0), HINGE0).T
        # the weapon's edge kept facing the same way from moment to moment (a flip would spin it in the hand)
        prev_e = LAST_EDGE.get(s)
        square = edge - blade * np.dot(edge, blade)
        if prev_e is not None:
            carried = prev_e - blade * np.dot(prev_e, blade)      # the roll it had, carried along with the blade
            if np.linalg.norm(square) < 0.35:
                square = carried            # the edge given lies almost along the blade: its roll is undefined there
            elif np.dot(carried, square) < 0:
                square = -square            # the same line, turned the way it already faced (never flipped over)
        edge = unit(square)
        LAST_EDGE[s] = edge
        d_tool = frame(blade, edge) @ TOOL0.T
        absd["Arm_" + s], absd["Hand_" + s], absd["Elbow_" + s], absd["Tool_" + s] = d_arm, d_hand, d_elbow, d_tool
        for j in ("Arm_" + s, "Hand_" + s, "Tool_" + s, "Elbow_" + s):
            place(j)
    local = {}
    for j in ORDER:
        par = PARENT[j]
        local[j] = COORD_INV @ world[j] if par is None else np.linalg.inv(world[par]) @ world[j]
    return local


# ------------------------------------------------------------------ easing
def smooth(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def in_out_cubic(t):
    t = min(1.0, max(0.0, t))
    return 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def out_quad(t):
    t = min(1.0, max(0.0, t))
    return 1 - (1 - t) ** 2


def slerp(a, b, k):
    a, b = unit(a), unit(b)
    c = float(np.clip(np.dot(a, b), -1, 1))
    w = math.acos(c)
    if w < 1e-4:
        return unit(a + (b - a) * k)
    if w > math.pi - 1e-3:   # opposite: go round through forward
        mid = unit(FWD - a * np.dot(FWD, a))
        return slerp(a, mid, k * 2) if k < 0.5 else slerp(mid, b, k * 2 - 1)
    return (math.sin((1 - k) * w) * a + math.sin(k * w) * b) / math.sin(w)


def carry(a, b, k, side=1):
    """The hand carried from a to b round the shoulder (its direction swinging, its distance blending) rather than
    straight across: a straight line from overhead down to the guard passes through the shoulder itself, folding
    the arm flat and flipping the elbow."""
    pivot = np.array([0.3 * side, 0.0, 1.38])
    da, db = a - pivot, b - pivot
    ra, rb = float(np.linalg.norm(da)), float(np.linalg.norm(db))
    return pivot + slerp(da, db, k) * (ra + (rb - ra) * k)


def mix(a, b, k):
    if isinstance(a, dict):
        return {key: mix(a.get(key, 0 if not isinstance(b.get(key), tuple) else (0, 0, 0)), b.get(key, 0), k) for key in b}
    if isinstance(a, tuple):
        return tuple(x + (y - x) * k for x, y in zip(a, b))
    return a + (b - a) * k


def write_anim(name, length, state_at, root_motion=True):
    """state_at(t) -> a moment (see solve). Sampled every tick, plus the last instant. root_motion=False leaves out
    the Coord track: Epic Fight takes any animation with one for an action (one that carries the body) and fails on
    any other kind (a guard), so only the attacks have it."""
    times = [round(i / FPS, 4) for i in range(int(length * FPS) + 1)]
    if times[-1] < length - 1e-4:
        times.append(round(length, 4))
    tracks = {j: [] for j in ORDER}
    coord = []
    LAST_HINGE.clear()
    LAST_EDGE.clear()
    for t in times:
        st = state_at(t)
        local = solve(st)
        # root motion: how far the move carries the player forward (blocks)
        c = [1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, round(st.get("fwd", 0.0), 4), 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0]
        coord.append(c)
        if not root_motion:
            # no Coord to hold it: the Root carries Coord's turn (and the shove back) itself
            local = dict(local)
            local["Root"] = np.array(c, float).reshape(4, 4) @ local["Root"]
        for j in ORDER:
            tracks[j].append([round(float(x), 5) for x in local[j].flatten()])
    data = {"animation": ([{"name": "Coord", "time": times, "transform": coord}] if root_motion else []) +
                         [{"name": j, "time": times, "transform": tracks[j]} for j in ORDER]}
    os.makedirs(ANIM_DIR, exist_ok=True)
    json.dump(data, open(os.path.join(ANIM_DIR, name + ".json"), "w"))


# ------------------------------------------------------------------ stances (thigh angles as Minecraft's, knee bend)
def legs(r, l, rk, lk):
    return {"right_leg": r, "left_leg": l, "right_knee": rk, "left_knee": lk}


GUARD_LEGS = legs((-18, 0, 7), (16, 0, -7), 28, 18)       # a fighting stance, right foot leading, knees soft
LOAD_LEGS = legs((-4, 0, 9), (26, 0, -9), 18, 42)         # the weight rocked well back onto the rear leg
STEP_LEGS = legs((-46, 0, 8), (32, 0, -8), 54, 14)        # stepped in, the front knee taking the weight
LUNGE_LEGS = legs((-64, 0, 9), (40, 0, -9), 76, 10)       # a deep lunge
LOW_LEGS = legs((-34, 0, 14), (28, 0, -14), 66, 48)       # sunk low for a sweep
AIR_LEGS = legs((-50, 0, 6), (-18, 0, -6), 80, 60)        # tucked in the air


def mirror_legs(l):
    return legs(l["left_leg"], l["right_leg"], l["left_knee"], l["right_knee"])


# ------------------------------------------------------------------ the cuts: arcs round the body
CHEST_H, WAIST_H, SHOULDER_H, HEAD_H = 1.32, 1.08, 1.42, 1.58
# nothing is swung below this (the grip's height, blocks): a follow-through that would carry lower is held up near it
GRIP_FLOOR = 0.98


def arc_point(cut, angle, x0=0.0, radius=0.62):
    """Where the grip is at angle (degrees) along a cut, and which way the blade points (out along the arc).
    Sideways cuts circle a vertical axis (angle from straight ahead, + toward the right); up-and-down cuts circle
    the axis through the shoulders (angle up from straight ahead)."""
    a = math.radians(angle)
    if cut.startswith("D"):
        # a diagonal: the sideways circle tipped about the forward line, its right end raised (+) or lowered (-)
        t = math.radians(float(cut[1:]))
        side = math.cos(t) * RIGHT + math.sin(t) * UP
        radial = math.cos(a) * FWD + math.sin(a) * side
        pos = np.array([x0, -0.2, CHEST_H + 0.05]) + radial * radius
        return pos, radial, unit(-math.sin(a) * FWD + math.cos(a) * side)
    if cut in ("STAB", "STABH"):
        # a thrust: angle runs 0 (drawn back by the hip, or the shoulder) to 100 (driven out to full reach)
        k = angle / 100.0
        high = cut == "STABH"
        pos = np.array([x0 * (1 - 0.6 * k), -0.08 - 0.62 * k, (1.34 + 0.18 * k) if high else (1.22 + 0.1 * k)])
        return pos, unit(FWD + UP * (0.18 if high else 0.05)), UP
    if cut in ("H", "LOW", "HH"):
        h = WAIST_H if cut == "LOW" else HEAD_H if cut == "HH" else CHEST_H
        radial = math.cos(a) * FWD + math.sin(a) * RIGHT
        pos = np.array([x0, -0.2, h]) + radial * radius     # out in front, clear of the chest
        blade = unit(radial + (UP * -0.35 if cut == "LOW" else UP * 0.05))
        tangent = unit(-math.sin(a) * FWD + math.cos(a) * RIGHT)
    else:
        radial = math.cos(a) * FWD + math.sin(a) * UP
        pos = np.array([x0, -0.1, SHOULDER_H - 0.08]) + radial * radius
        blade = radial
        tangent = unit(-math.sin(a) * FWD + math.cos(a) * UP)
    return pos, blade, tangent


# each cut: (plane, from, to) in degrees; the follow-through carries on a little past "to"
CUTS = {
    "R2L": ("H", 85, -70), "L2R": ("H", -70, 85),               # level, across the chest
    "HR2L": ("HH", 80, -65), "HL2R": ("HH", -65, 80),           # level, at head height
    "DOWN": ("V", 82, -30), "UP": ("V", -35, 88),               # straight down from overhead, straight up
    "LOWR2L": ("LOW", 80, -65), "LOWL2R": ("LOW", -65, 80),     # level, low across the waist
    "XDL": ("D+40", 85, -60),   # diagonal down, high right to lower left
    "XDR": ("D-40", -60, 85),   # diagonal down, high left to lower right
    "XUL": ("D-40", 85, -60),   # rising, low right to high left
    "XUR": ("D+40", -60, 85),   # rising, low left to high right
    "SDL": ("D+65", 85, -50),   # steep, almost overhead, down to the left
    "SDR": ("D-65", -50, 85),   # steep, down to the right
    "SUL": ("D-65", 85, -50),   # steep rising, up to the left
    "SUR": ("D+65", -50, 85),   # steep rising, up to the right
    "FDL": ("D+20", 85, -70),   # shallow, nearly level, falling to the left
    "FDR": ("D-20", -70, 85),   # shallow, falling to the right
    "STAB": ("STAB", 0, 100),   # a thrust at the chest
    "STABH": ("STABH", 0, 100), # a thrust at the face
}


def mirror_hand(h):
    """The mirror image of a hand (across the body's middle), for the other hand's cut."""
    m = np.array([-1.0, 1, 1])
    return {"pos": h["pos"] * m, "blade": h["blade"] * m, "edge": h["edge"] * m}


def guard_hand(kind, side="right"):
    """Where a weapon is held between blows."""
    s = 1 if side == "right" else -1
    if kind == "two":
        h = {"pos": np.array([0.1, -0.38, 1.02]), "blade": unit(np.array([-0.15, -0.55, 0.82])), "edge": FWD}
    elif kind == "pole":
        h = {"pos": np.array([0.34, -0.3, 1.04]), "blade": unit(np.array([0.12, -0.8, 0.58])), "edge": FWD}
    elif kind == "fist":
        h = {"pos": np.array([0.16, -0.24, 1.42]), "blade": unit(np.array([0.0, -1.0, 0.2])), "edge": UP}
    elif kind == "gun":
        h = {"pos": np.array([0.2, -0.56, 1.34]), "blade": FWD, "edge": UP}
    else:
        h = {"pos": np.array([0.26, -0.32, 1.06]), "blade": unit(np.array([0.15, -0.6, 0.78])), "edge": FWD}
    return h if s > 0 else mirror_hand(h)


def other_hand(hand, length=0.24):
    """The left hand on the same handle, a hand's width toward the pommel."""
    return {"pos": hand["pos"] - hand["blade"] * length, "blade": hand["blade"], "edge": hand["edge"]}


# ------------------------------------------------------------------ registry output
TRAIL_DIR = os.path.join(ANIM_DIR, "data")


def write_trails(name, sweeps):
    """The weapon's own trail (its Epic Fight item skin: assets/voidpowers/item_skins) lit through every sweep, on the
    hand that swings it: [(start, end, hands)]."""
    effects = []
    for start, end, hands in sweeps:
        for h in hands:
            effects.append({"start_time": round(max(0.0, start - 0.02), 3), "end_time": round(end + 0.02, 3),
                            "joint": "Tool_L" if h == "left" else "Tool_R", "item_skin_hand": "off_hand" if h == "left" else "main_hand"})
    os.makedirs(TRAIL_DIR, exist_ok=True)
    json.dump({"trail_effects": effects}, open(os.path.join(TRAIL_DIR, name + ".json"), "w"), indent="	")
REGISTRY = []   # (name, kind, [(start, antic, contact, recovery, end, hand)])


# ------------------------------------------------------------------ the attacks: strings of beats
# An attack is one or more beats, each a cut (or a thrust, or a punch), thrown one into the next. A beat can carry the
# whole body round with it (spin, degrees: + to the right), take it up into the air and slam it down (leap, blocks),
# turn it over in a flip (flip, degrees), or carry it forward (lunge, blocks). Every beat lands its own blow.
PUNCHES = ("JAB", "CROSS", "HOOK", "UPPER", "HAMMER")


def beat_ease(b):
    """How a beat's sweep (and the body's turn with it) speeds up and slows: a plain cut snaps through (cubic); a spin
    or a flip turns more evenly, so it's never too fast between keys."""
    return smooth if abs(b.spin) >= 300 or b.flip else in_out_cubic


class Beat:
    def __init__(self, cut, arm="right", spin=0.0, leap=0.0, flip=0.0, lunge=0.0, slow=1.0):
        self.cut, self.arm, self.spin, self.leap, self.flip, self.lunge, self.slow = cut, arm, spin, leap, flip, lunge, slow


def fist_path(cut, k, x0):
    """A punch's fist at k (0 chambered .. 1 landed), as the right hand's (cuts' conventions)."""
    if cut in ("JAB", "CROSS"):
        p = mix(np.array([0.16, -0.16, 1.36]), np.array([0.05, -0.68, 1.4]), k)
        return {"pos": p, "blade": FWD, "edge": UP}, (-FWD)
    if cut == "HOOK":
        a = math.radians(70 - 100 * k)
        p = np.array([0, -0.12, 1.42]) + 0.46 * (math.cos(a) * FWD + math.sin(a) * RIGHT)
        return {"pos": p, "blade": unit(-math.sin(a) * FWD + math.cos(a) * RIGHT) * -1, "edge": UP}, (-RIGHT)
    if cut == "UPPER":
        p = mix(np.array([0.14, -0.3, 1.0]), np.array([0.06, -0.42, 1.64]), k)
        return {"pos": p, "blade": unit(np.array([0, -0.4, 1.0])), "edge": FWD}, UP
    # HAMMER: a fist brought down from overhead
    a = math.radians(95 - 135 * k)
    p = np.array([0.12, -0.12, 1.3]) + 0.5 * (math.cos(a) * FWD + math.sin(a) * UP)
    return {"pos": p, "blade": unit(math.cos(a) * FWD + math.sin(a) * UP), "edge": FWD}, -UP


# How far before its start a cut is wound up, and how far past its end it follows through (fractions of its arc), and
# how high the wind-up draws the weapon (blocks): measured off the attacks of Epic Fight and its addons, which draw
# the weapon well back and up, cut through a wide arc in a fraction of the move, and carry on low past the target
WIND_BACK, FOLLOW, WIND_LIFT = 0.3, 0.2, 0.22
# How far each kind of weapon steps the body in with every blow (blocks)
STEP = {"one": 0.5, "dual": 0.55, "two": 0.75, "pole": 0.7, "fist": 0.45}


def combo(name, beats, tempo, kind="one", reach=1.0, dyn=1.0, move="basic"):
    """One attack (see above). tempo: the length of a single-blow attack (seconds); kind: one (one-handed), two
    (two-handed sword), pole (two-handed, a long shaft), dual (one in each hand), fist. Shaped as Epic Fight's own:
    a quarter of it drawing the weapon back and up, a fifth the cut itself (snapping through from the wind-up to well
    past the target), and the rest held in the follow-through before settling back."""
    n = len(beats)
    wind, cut_t, gap, recover = 0.25 * tempo, 0.2 * tempo, 0.06 * tempo, 0.55 * tempo
    # the timeline: the wind-up, each beat (a spin or a leap takes longer), the gaps between, the recovery
    spans, t = [], wind
    for i, b in enumerate(beats):
        # a spin takes about two fifths of a second a turn (so the body never turns more than about 35 degrees between
        # two keys: further than that and the interpolation can carry a joint the wrong way round)
        turns = abs(b.spin) / 360.0
        d = cut_t * b.slow * (1 + 1.2 * turns + (0.6 if turns > 1 else 0)) * (1.5 if b.leap else 1.0) * (1.6 if b.flip else 1.0)
        spans.append((t, t + d))
        t += d + (gap if i < n - 1 else 0)
    length = t + recover
    gkind = {"two": "two", "pole": "pole", "fist": "fist"}.get(kind, "one")
    radius = 0.74 * reach

    def geometry(b):
        """The beat's start and end (grip, blade, edge) as the right hand's, and its sweep's sign."""
        if b.cut in PUNCHES:
            s0, _ = fist_path(b.cut, 0, 0)
            s1, sweep = fist_path(b.cut, 1, 0)
            return s0, s1, 1, (lambda k: fist_path(b.cut, k, 0)[0])
        plane, a0, a1 = CUTS[b.cut]
        stab = plane.startswith("STAB")
        # the whole sweep, from the wind-up (drawn back past the cut's start) to the follow-through (carried past its end)
        back = 0.0 if stab else WIND_BACK
        on = 0.0 if stab else FOLLOW * dyn
        w0, w1 = a0 - (a1 - a0) * back, a1 + (a1 - a0) * on
        x0 = (0.32 if kind == "pole" else 0.05 if kind == "two" else 0.12) + (0.13 if plane == "V" and kind not in ("two", "pole") else 0.0)
        sgn = 1 if a0 > a1 else -1
        lift = 0.0 if stab or b.cut in ("UP", "XUL", "XUR", "LOWR2L", "LOWL2R") else WIND_LIFT

        def at(k):
            p, bl, tn = arc_point(plane, w0 + (w1 - w0) * k, x0, radius)
            # wound up, the weapon is drawn high; the lift is gone by the time the blade reaches the cut
            p = p + UP * lift * max(0.0, 1 - k / (back / (1 + back + on) + 0.12)) if lift else p
            if p[2] < GRIP_FLOOR:
                p = np.array([p[0], p[1], GRIP_FLOOR - (GRIP_FLOOR - p[2]) * 0.3])
            return {"pos": p, "blade": bl, "edge": tn * -sgn}
        return at(0), at(1), sgn, at

    geo = [geometry(b) for b in beats]
    guard = guard_hand(gkind, "right")

    def side_hand(h, arm):
        return mirror_hand(h) if arm == "left" else h

    def hand_at(tc, which):
        """The weapon hand (which: right/left) at tc: the guard, a beat's sweep, or carried between them."""
        def active(b):
            return b.arm in (which, "both") or kind in ("two", "pole") and which == "right"
        g = guard if which == "right" else guard_hand(gkind, "left")
        # this hand's own beats, in order; between them it is carried from one to the next
        mine = [(i, b) for i, b in enumerate(beats) if active(b)]
        if not mine:
            return g
        points = []          # (time, hand) waypoints with the sweeps between them
        for i, b in mine:
            s0, s1, _, at = geo[i]
            conv = (lambda h, _b=b: side_hand(h, "left") if which == "left" else h)
            points.append((spans[i], conv, at, beat_ease(b)))
        # before the first sweep: from the guard to its start
        (f0, f1), conv, at, _ = points[0]
        if tc <= f0:
            k = out_quad(min(1.0, tc / (0.8 * f0))) if f0 > 0 else 1
            st = conv(at(0))
            return {"pos": carry(g["pos"], st["pos"], k, 1 if which == "right" else -1), "blade": slerp(g["blade"], st["blade"], k),
                    "edge": unit(mix(g["edge"], st["edge"], k))}
        for j, ((s, e), conv, at, ease) in enumerate(points):
            if s <= tc <= e:
                return conv(at(ease((tc - s) / (e - s))))
            nxt = points[j + 1] if j + 1 < len(points) else None
            if nxt and e < tc < nxt[0][0]:
                k = smooth((tc - e) / (nxt[0][0] - e))
                a, b2 = conv(at(1)), nxt[1](nxt[2](0))
                return {"pos": carry(a["pos"], b2["pos"], k, 1 if which == "right" else -1), "blade": slerp(a["blade"], b2["blade"], k),
                        "edge": unit(mix(a["edge"], b2["edge"], k))}
        (s, e), conv, at, _ = points[-1]
        k = smooth(((tc - e) / (length - e) - 0.35) / 0.65)
        a = conv(at(1))
        return {"pos": carry(a["pos"], g["pos"], k, 1 if which == "right" else -1), "blade": slerp(a["blade"], g["blade"], k),
                "edge": unit(mix(a["edge"], g["edge"], k))}

    def beat_body(i):
        """A beat's trunk: (twist at its start, twist at its end, pitch start, pitch end, legs start, legs end)."""
        b = beats[i]
        sgn = geo[i][2]
        left = b.arm == "left"
        plane = "P" if b.cut in PUNCHES else CUTS[b.cut][0]
        sideways = plane in ("H", "HH", "LOW", "P") or plane.startswith("D") and abs(float(plane[1:])) < 55
        tw = (58 if plane == "P" else 74) * dyn * (sgn if sideways else 0.35 * sgn)
        if plane.startswith("STAB"):
            tw = 42 * dyn
        if left:
            tw = -tw
        down = b.cut in ("DOWN", "SDL", "SDR", "HAMMER")
        up = b.cut in ("UP", "XUL", "XUR", "SUL", "SUR", "UPPER", "STABH")
        p0 = -18 * dyn if down else 11 * dyn if up else -6
        p1 = 26 * dyn if down else -15 * dyn if up else 15 * dyn if plane.startswith("STAB") else 11 * dyn
        l0 = LOW_LEGS if plane == "LOW" else LOAD_LEGS
        l1 = LUNGE_LEGS if b.lunge or plane.startswith("STAB") else LOW_LEGS if plane == "LOW" else STEP_LEGS
        if left:
            l0, l1 = mirror_legs(l0), mirror_legs(l1)
        if b.leap or move == "air":
            l0 = AIR_LEGS
        if move == "air":
            l1 = AIR_LEGS
        return tw, -tw * 1.1, p0, p1, l0, l1

    bodies = [beat_body(i) for i in range(n)]
    step_base = STEP.get(kind, 0.35) * dyn ** 0.5 if move != "air" else 0.2

    def body_at(tc):
        """(twist, pitch, sink, legs, forward, spin, flip) of the trunk at tc."""
        spin = flip = fwd = 0.0
        for i, b in enumerate(beats):
            s, e = spans[i]
            k = 0 if tc <= s else 1 if tc >= e else beat_ease(b)((tc - s) / (e - s))
            spin += b.spin * k
            flip += b.flip * k
            fwd += (b.lunge + step_base + (1.8 if move == "dash" and i == 0 else 0)) * k
        sink_hit = 0.1 + 0.05 * dyn
        if tc <= spans[0][0]:
            k = out_quad(min(1.0, tc / (0.8 * spans[0][0])))
            tw0, _, p0, _, l0, _ = bodies[0]
            lift = -beats[0].leap * out_quad(k) if beats[0].leap else 0.0
            return tw0 * k, p0 * k, lift or sink_hit * 0.4 * k, mix(GUARD_LEGS, l0, k), fwd, spin, flip
        for i, b in enumerate(beats):
            s, e = spans[i]
            tw0, tw1, p0, p1, l0, l1 = bodies[i]
            if s <= tc <= e:
                k = in_out_cubic((tc - s) / (e - s))
                if b.leap:
                    # up at the start, slammed down onto the target at the end
                    sink = -b.leap * (1 - in_out_cubic((tc - s) / (e - s)) ** 2) + (sink_hit * 1.6) * k ** 4
                else:
                    sink = sink_hit * 0.4 + (sink_hit - sink_hit * 0.4) * k
                return tw0 + (tw1 - tw0) * k, p0 + (p1 - p0) * k, sink, mix(l0, l1, k), fwd, spin, flip
            if i + 1 < n and e < tc < spans[i + 1][0]:
                k = smooth((tc - e) / (spans[i + 1][0] - e))
                ntw0, _, np0, _, nl0, _ = bodies[i + 1]
                lift = -beats[i + 1].leap * out_quad(k) if beats[i + 1].leap else sink_hit * (1 - 0.6 * k)
                return tw1 + (ntw0 - tw1) * k, p1 + (np0 - p1) * k, lift, mix(l1, nl0, k), fwd, spin, flip
        e = spans[-1][1]
        k = smooth(((tc - e) / (length - e) - 0.35) / 0.65)
        _, tw1, _, p1, _, l1 = bodies[-1]
        over = math.sin(math.pi * k) * 0.18
        return tw1 * (1 - k - over), p1 * (1 - k - over), sink_hit * (1 - k), mix(l1, GUARD_LEGS, k), fwd, spin, flip

    def state_at(t):
        # the kinetic chain: the hips lead, the chest follows, the arm arrives last
        hips, waist, chest = body_at(t + 0.04), body_at(t + 0.015), body_at(t - 0.01)
        st = dict(hips[3])
        st["hips"] = (hips[1] * 0.32, hips[0] * 0.5, -hips[0] * 0.06)
        st["waist"] = (waist[1] * 0.38, (waist[0] - hips[0]) * 0.55 + waist[0] * 0.22, waist[0] * 0.09)
        st["chest"] = (chest[1] * 0.45, (chest[0] - waist[0]) * 0.65 + chest[0] * 0.28, chest[0] * 0.12)
        st["head"] = (-chest[1] * 0.4, -chest[0] * 0.55, -chest[0] * 0.08)
        st["sink"], st["fwd"], st["spin"], st["flip"] = hips[2], hips[4], hips[5], hips[6]
        tc = t - 0.03
        if kind in ("two", "pole"):
            r_ = hand_at(tc, "right")
            st["right_hand"] = r_
            st["left_hand"] = other_hand(r_, 0.24 if kind == "two" else 0.34)
        elif kind in ("dual", "fist"):
            st["right_hand"] = hand_at(tc, "right")
            st["left_hand"] = hand_at(tc, "left")
        else:
            st["right_hand"] = hand_at(tc, "right")
            k = math.sin(math.pi * min(1, max(0, t / length)))
            sg = geo[0][2]
            st["left_hand"] = {"pos": np.array([-0.3 - 0.14 * k, -0.34 + 0.36 * k * sg, 0.98 + 0.22 * k]),
                               "blade": unit(np.array([0, -1.0, -0.3])), "edge": UP}
        return st

    write_anim(name, length, state_at)
    write_trails(name, [(spans[i][0], spans[i][1], ("left",) if b.arm == "left" else ("right", "left") if b.arm == "both" else ("right",))
                        for i, b in enumerate(beats)])
    # each beat lands its own blow: hit boxes on from a fifth of the way into its sweep to its end
    # (a beat struck with both blades is one phase with both hands' hit boxes)
    phases = []
    for i, b in enumerate(beats):
        s, e = spans[i]
        start = 0.0 if i == 0 else phases[-1][4]
        end = Float_MAX if i == n - 1 else round((e + spans[i + 1][0]) / 2, 3)
        rec = e + 0.45 * (length - e) if i == n - 1 else end
        hands = ("left",) if b.arm == "left" else ("right", "left") if b.arm == "both" else ("right",)
        phases.append((round(start, 3), round(s + 0.2 * (e - s), 3), round(e, 3), round(rec, 3), end, hands))
    REGISTRY.append((name, move, phases))
    return spans, length


Float_MAX = "MAX"

