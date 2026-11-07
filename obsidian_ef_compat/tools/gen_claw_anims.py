"""Generates the gauntlet's claw-and-kick combos, its two sprint attacks and its claw-showing idle, solved onto Epic
Fight's biped with ef_moves (Void Powers' Epic Fight generator: its skeleton solver, stances and easing).

v2 (compat 1.2.9): wide, wild clawing. Every rake swings through a big arc round the shoulder (up to ~220 degrees)
with the arm at full reach, the whole trunk whipping into it; combos chain rakes, spins, leaps, X-slashes and kicks
(front snap, roundhouse, axe, knee, spinning hook). While a leg kicks, the claws fling out wide for balance.

Writes biped/gauntlet/auto1..10, dual1..10, dash, dual_dash and idle into src/main/resources, and
src/main/java/.../ClawMoves.java: per attack its hit window, the joints that hit, its stats, the trail of every
strike, the purple slash crescent each strike leaves (GauntletFx.SLASH) and when each claw snaps out.

Run from the project root, after exaggerate_anims.py:  python tools/gen_claw_anims.py
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(os.path.join(HERE, ".."))          # ef_moves reads tools/biped_armature.json from the project root
sys.path.insert(0, HERE)
import ef_moves as em  # noqa: E402

em.ANIM_DIR = os.path.join("src", "main", "resources", "assets", "obsidian_ef_compat", "animmodels", "animations", "biped", "gauntlet")
JAVA = os.path.join("src", "main", "java", "com", "obsidianarsenal", "efcompat", "ClawMoves.java")
FWD, RIGHT, UP = em.FWD, em.RIGHT, em.UP

# ---- the wrist held straight
_solve = em.solve


def _solve_locked(st):
    local = _solve(st)
    for s in ("R", "L"):
        local["Tool_" + s] = em.REST["Tool_" + s].copy()
    return local


em.solve = _solve_locked

SH = {"right": np.array([0.375, 0.0, 1.394]), "left": np.array([-0.375, 0.0, 1.394])}   # shoulders (authoring axes)
REACH, COCK = 0.56, 0.3          # the claw at full reach / cocked back (blocks from the shoulder; the arm is 0.57)
FLOOR = 0.9                      # never swept lower than this (the grip's height)
GUARD = {"right": np.array([0.22, -0.46, 1.24]), "left": np.array([-0.26, -0.44, 1.12])}
RIBS = {"right": np.array([0.24, 0.04, 1.12]), "left": np.array([-0.24, 0.04, 1.12])}    # the counterweight hand
FLUNG = {"right": np.array([0.78, 0.12, 1.3]), "left": np.array([-0.78, 0.1, 1.34])}     # claws thrown wide in a kick
SIDE = {"right": 1, "left": -1}


def hand(p):
    return {"pos": np.array(p, float), "blade": FWD, "edge": UP}


def arc(side, tilt, ang, radius):
    """The claw's grip on an arc round the shoulder: ang 0 is straight ahead, + toward the striking arm's own side (and
    up, for a tilted arc); tilt turns the arc from level (0) to straight up-and-down (90)."""
    s = SIDE[side]
    t, a = math.radians(tilt), math.radians(ang)
    across = math.cos(t) * RIGHT * s + math.sin(t) * UP
    p = SH[side] + (math.cos(a) * FWD + math.sin(a) * across) * radius
    if p[2] < FLOOR:
        p[2] = FLOOR - (FLOOR - p[2]) * 0.3
    return p


def legs(r, l, rk, lk):
    return em.legs(r, l, rk, lk)


def mirror_kick(l):
    """A kick's legs on the other side: the legs swapped, their sideways turns (yaw, roll) flipped."""
    f = lambda a: (a[0], -a[1], -a[2])
    return legs(f(l["left_leg"]), f(l["right_leg"]), l["left_knee"], l["right_knee"])


# kicks with the right leg: chamber (cocked) and extended legs, trunk yaw and pitch at each, the slash it leaves
KICKS = {
    # snap front kick: knee up, then the foot driven straight out, leaning back over the standing leg
    "front": dict(chamber=legs((-88, 0, 4), (10, 0, -5), 110, 24), extend=legs((-96, 0, 2), (14, 0, -5), 4, 16),
                  yaw=(12, -18), pitch=(-4, -14), slash=(0, 25, -25, 0.95, 1.5)),
    # roundhouse: the knee chambered out to the side, the hips whipping round so the shin sweeps across
    "round": dict(chamber=legs((-50, 0, 62), (8, 0, -6), 118, 26), extend=legs((-58, 0, 86), (10, 0, -8), 6, 18),
                  yaw=(38, -88), pitch=(-6, -18), slash=(12, 80, -95, 1.05, 1.7)),
    # axe kick: the straight leg swung high overhead and chopped down
    "axe": dict(chamber=legs((-138, 0, 12), (12, 0, -5), 6, 22), extend=legs((-24, 0, 6), (18, 0, -6), 12, 32),
                yaw=(10, -6), pitch=(-18, 20), slash=(88, 125, -15, 1.25, 1.6)),
    # rising knee, the trunk curling down into it
    "knee": dict(chamber=legs((-42, 0, 4), (10, 0, -5), 120, 24), extend=legs((-112, 0, 2), (14, 0, -5), 140, 18),
                 yaw=(16, -26), pitch=(4, 16), slash=(70, -40, 50, 1.0, 1.2)),
}


class Strike:
    """One blow. arm: right / left / both. A sweep: tilt and angles from a0 to a1 (see arc). thrust: driven straight
    out instead. lunge: blocks stepped forward; spin: degrees the whole body turns with it; leap: blocks up then down.
    kick: a kick style (KICKS) with the leg given by arm (right / left) instead of a claw."""
    def __init__(self, arm="right", tilt=0.0, a0=80.0, a1=-60.0, thrust=False, lunge=0.0, spin=0.0, leap=0.0, low=False,
                 elbow=False, kick=None, crack=0.0):
        self.arm, self.tilt, self.a0, self.a1 = arm, tilt, a0, a1
        self.thrust, self.lunge, self.spin, self.leap, self.low, self.elbow = thrust, lunge, spin, leap, low, elbow
        self.kick, self.crack = kick, crack

    def arms(self):
        if self.kick:
            return ()
        return ("right", "left") if self.arm == "both" else (self.arm,)

    def cocked(self, side):
        if self.elbow:
            return np.array([0.3, 0.16, 1.18])                    # drawn back by the hip, elbow cocked behind
        if self.thrust:
            return RIBS[side] + np.array([0, 0.02, 0.04])
        return arc(side, self.tilt, self.a0 + (self.a0 - self.a1) * 0.12, COCK + 0.06)

    def at(self, side, u):
        """The striking claw at u (0 cocked .. 1 at the end of the sweep), and on into the follow-through (u > 1)."""
        if self.elbow:
            return self.cocked(side) + (np.array([-0.06, -0.2, 1.33]) - self.cocked(side)) * min(1.0, u)
        if self.thrust:
            tip = SH[side] + FWD * REACH + UP * 0.02 - RIGHT * SIDE[side] * 0.12
            return self.cocked(side) + (tip - self.cocked(side)) * min(1.05, u)
        ang = self.a0 + (self.a1 - self.a0) * u
        reach = COCK + (REACH - COCK) * em.smooth(min(1.0, u * 2.4))     # out to full reach early, held through the arc
        return arc(side, self.tilt, ang, reach)

    def kick_legs(self, phase):
        k = KICKS[self.kick]
        l = k["chamber"] if phase == 0 else k["extend"]
        return mirror_kick(l) if self.arm == "left" else l


def move(name, strikes, tempo, dash=False):
    """A whole attack: the wind-up, each strike in turn (a short gap between), a held follow-through and the recovery
    to the guard. Returns its data for ClawMoves."""
    wind, sweep, gap, follow = 0.26 * tempo, 0.17 * tempo, 0.05 * tempo, 0.12 * tempo
    spans, t = [], wind
    for i, s in enumerate(strikes):
        wide = abs(s.a1 - s.a0) / 180.0 if not (s.kick or s.thrust or s.elbow) else 0.8
        d = sweep * (0.7 + 0.45 * wide) * (1 + 1.1 * abs(s.spin) / 360.0) * (1.5 if s.leap else 1.0) * (0.8 if s.elbow else 1.0)
        if s.kick:
            d = sweep * (1.25 if s.kick in ("axe", "round") else 1.0) * (1 + 1.1 * abs(s.spin) / 360.0)
        spans.append((t, t + d))
        nxt = strikes[i + 1] if i + 1 < len(strikes) else None
        t += d + ((gap * (2.2 if (nxt and (nxt.kick or s.kick)) else 1.0)) if nxt else 0)
    end = t
    length = end + follow + 0.36 * tempo

    def kickness(tc):
        """How far the claws are flung wide for balance: 1 while a leg kicks, eased in and out."""
        w = 0.0
        for i, s in enumerate(strikes):
            if s.kick:
                a, b = spans[i]
                pre = wind if i == 0 else 0.12 * tempo
                w = max(w, em.smooth((tc - (a - pre)) / pre) * (1 - em.smooth((tc - b - 0.03 * tempo) / (0.14 * tempo))))
        return w

    def spare(tc, side):
        """A hand not striking right now: at the ribs as a counterweight, flung wide while a leg kicks."""
        w = em.smooth(min(1.0, tc / wind)) * (1 - em.smooth((tc - end - follow) / (0.3 * tempo)))
        base = GUARD[side] + (RIBS[side] - GUARD[side]) * w
        k = kickness(tc)
        return base + (FLUNG[side] - base) * k

    def claw(tc, side):
        mine = [i for i, s in enumerate(strikes) if side in s.arms()]
        if not mine:
            return spare(tc, side)
        first, last = spans[mine[0]], spans[mine[-1]]
        if tc <= first[0]:                             # wind-up: from where the hand is -> cocked
            start = max(0.0, first[0] - wind * 1.1)
            if tc <= start:
                return spare(tc, side)
            k = em.out_quad((tc - start) / (first[0] - start))
            return em.carry(spare(start, side), strikes[mine[0]].cocked(side), k, SIDE[side])
        for j, i in enumerate(mine):
            a, b = spans[i]
            if a <= tc <= b:
                return strikes[i].at(side, em.in_out_cubic((tc - a) / (b - a)))
            if j + 1 < len(mine) and b < tc < spans[mine[j + 1]][0]:
                na = spans[mine[j + 1]][0]
                k = em.smooth((tc - b) / (na - b))
                via = strikes[i].at(side, 1.0)
                if kickness((b + na) / 2) > 0.5:       # a kick in between: the claw thrown wide, then cocked again
                    mid = (b + na) / 2
                    if tc < mid:
                        return em.carry(via, FLUNG[side], em.smooth((tc - b) / (mid - b)), SIDE[side])
                    return em.carry(FLUNG[side], strikes[mine[j + 1]].cocked(side), em.smooth((tc - mid) / (na - mid)), SIDE[side])
                return em.carry(via, strikes[mine[j + 1]].cocked(side), k, SIDE[side])
        s = strikes[mine[-1]]
        if tc <= last[1] + follow:                     # the follow-through, held out past the target
            if kickness(tc) > 0.01:
                return em.carry(s.at(side, 1.0), FLUNG[side], kickness(tc), SIDE[side])
            return s.at(side, 1.0 + 0.1 * em.out_quad((tc - last[1]) / follow))
        if any(st.kick for st in strikes[mine[-1] + 1:]):
            return em.carry(s.at(side, 1.0), spare(tc, side), em.smooth((tc - last[1] - follow) / (0.2 * tempo)), SIDE[side])
        k = em.smooth((tc - last[1] - follow) / (length - last[1] - follow))
        return em.carry(s.at(side, 1.1), GUARD[side], k, SIDE[side])

    def pose(s, phase):                                # phase 0 wound, 1 struck -> (yaw, pitch, legs)
        if s.kick:
            k = KICKS[s.kick]
            sd = SIDE.get(s.arm, 1)
            return k["yaw"][phase] * sd, k["pitch"][phase], s.kick_legs(phase)
        sgn = (1 if s.a0 > s.a1 else -1) * SIDE.get(s.arm, 1)
        if s.arm == "both":
            yaw = 0.0
        elif s.thrust or s.elbow:
            yaw = (30 if phase == 0 else -38) * SIDE[s.arm] * (1.6 if s.elbow else 1.0)
        else:
            wide = min(1.4, abs(s.a1 - s.a0) / 160.0)
            yaw = (58 if phase == 0 else -68) * sgn * wide * (0.35 if s.tilt >= 70 else 1.0)
        down, up = s.tilt >= 30 and s.a0 > s.a1, s.tilt >= 30 and s.a0 < s.a1
        pitch = (-12 if down else 8 if up else -4) if phase == 0 else (18 if down else -10 if up else 10) + (6 if s.thrust or s.elbow else 0)
        if s.arm == "both" and down:
            pitch = -16 if phase == 0 else 24
        legs_ = em.LOAD_LEGS if phase == 0 else em.LOW_LEGS if s.low else em.LUNGE_LEGS if s.thrust or s.lunge > 0.4 or s.elbow else em.STEP_LEGS
        if s.arm == "left":
            legs_ = em.mirror_legs(legs_)
        return yaw, pitch, legs_

    def sink_of(s, k):
        if s.leap:
            return -s.leap * (1 - k * k) + 0.12 * k ** 4
        if s.kick:
            return 0.03 + 0.05 * k
        return 0.04 + (0.09 if s.low else 0.05) * k

    def body(tc):
        """(yaw, pitch, legs, sink, forward, spin) of the trunk at tc: wound up away, driven through into the strike."""
        n = len(strikes)
        fwd = spin = 0.0
        for i, s in enumerate(strikes):
            a, b = spans[i]
            k = 0 if tc <= a else 1 if tc >= b else em.in_out_cubic((tc - a) / (b - a))
            fwd += (s.lunge + (0.1 if s.kick else 0.22)) * k
            spin += s.spin * k
        if dash:
            fwd += 1.4 * em.smooth(min(1.0, tc / end))
        if tc <= spans[0][0]:
            k = em.out_quad(min(1.0, tc / spans[0][0]))
            y, p, l = pose(strikes[0], 0)
            lift = -strikes[0].leap * k * k if strikes[0].leap else 0.04 * k
            return y * k, p * k, em.mix(em.GUARD_LEGS, l, k), lift, fwd, spin
        for i, s in enumerate(strikes):
            a, b = spans[i]
            y0, p0, l0 = pose(s, 0); y1, p1, l1 = pose(s, 1)
            if a <= tc <= b:
                k = em.in_out_cubic((tc - a) / (b - a))
                return y0 + (y1 - y0) * k, p0 + (p1 - p0) * k, em.mix(l0, l1, k), sink_of(s, k), fwd, spin
            if i + 1 < n and b < tc < spans[i + 1][0]:
                k = em.smooth((tc - b) / (spans[i + 1][0] - b))
                ny, np_, nl = pose(strikes[i + 1], 0)
                s0, s1 = sink_of(s, 1.0), (-strikes[i + 1].leap * 0.0 + 0.04)
                return y1 + (ny - y1) * k, p1 + (np_ - p1) * k, em.mix(l1, nl, k), s0 + (s1 - s0) * k, fwd, spin
        y1, p1, l1 = pose(strikes[-1], 1)
        k = em.smooth((tc - end - follow * 0.5) / (length - end - follow * 0.5))
        s_end = sink_of(strikes[-1], 1.0)
        return y1 * (1 - k), p1 * (1 - k), em.mix(l1, em.GUARD_LEGS, k), s_end * (1 - k), fwd, spin

    def state_at(t):
        hips, waist, chest = body(t + 0.03), body(t + 0.012), body(t - 0.008)     # the hips lead, the chest follows
        st = dict(hips[2])
        st["hips"] = (hips[1] * 0.3, hips[0] * 0.45, 0.0)
        st["waist"] = (waist[1] * 0.35, waist[0] * 0.3, 0.0)
        st["chest"] = (chest[1] * 0.35, chest[0] * 0.32, 0.0)
        total_p, total_y = hips[1] * 0.3 + waist[1] * 0.35 + chest[1] * 0.35, hips[0] * 0.45 + waist[0] * 0.3 + chest[0] * 0.32
        st["head"] = (-total_p * 0.7, -total_y * 0.65, 0.0)                         # eyes kept on the target
        st["sink"], st["fwd"], st["spin"] = hips[3], hips[4], hips[5]
        tc = t - 0.02                                                               # the arm arrives last
        st["right_hand"], st["left_hand"] = hand(claw(tc, "right")), hand(claw(tc, "left"))
        return st

    em.write_anim(name, length, state_at)

    # ---- the data the Java side needs
    joints, trails, slashes, claws, cracks = [], [], [], [], []
    for i, s in enumerate(strikes):
        a, b = spans[i]
        if s.kick:
            js = ["Leg_R" if s.arm != "left" else "Leg_L"]
            tilt, a0, a1, h, r = KICKS[s.kick]["slash"]
            slashes.append((a + 0.5 * (b - a), tilt, a0, a1, SIDE.get(s.arm, 1), h, r))
        elif s.elbow:
            js = ["Elbow_R"]
        else:
            js = ["Tool_R" if sd == "right" else "Tool_L" for sd in s.arms()]
            for sd in s.arms():
                claws.append((round(max(0.0, a - 0.08 * tempo), 3), sd == "left"))
                if not s.thrust:
                    slashes.append((a + 0.45 * (b - a), s.tilt, s.a0, s.a1, SIDE[sd], 1.35, 1.75))
                else:
                    slashes.append((a + 0.6 * (b - a), 0.0, 14, -14, SIDE[sd], 1.35, 1.6))
        for j in js:
            if j not in joints:
                joints.append(j)
            if not j.startswith("Elbow"):
                trails.append((j, round(max(0.0, a - 0.04), 3), round(b + 0.03, 3)))
        if s.crack:
            cracks.append((round(b, 3), js[0], s.crack))
    a, b = spans[0]
    return dict(antic=round(max(0.0, a - 0.02), 3), contact=round(spans[-1][1] + 0.02, 3),
                recovery=round(end + follow + 0.4 * (length - end - follow), 3),
                joints=joints, trails=trails, slashes=[tuple(round(x, 3) for x in sl) for sl in slashes], claws=claws, cracks=cracks)


R, L, BOTH = "right", "left", "both"
# (name, strikes, damage, impact, extra targets, stun)
SINGLE = [
    ("auto1", [Strike(R, 35, 125, -95)], 1.0, 1.0, 1, "SHORT"),                                     # huge diagonal rake
    ("auto2", [Strike(R, 25, -100, 120), Strike(R, kick="front")], 1.0, 1.3, 1, "SHORT"),           # rising backhand, snap kick
    ("auto3", [Strike(R, 40, 125, -85), Strike(R, 20, -90, 115), Strike(R, 60, 130, -70)], 0.9, 1.0, 2, "SHORT"),  # three-rake frenzy
    ("auto4", [Strike(R, kick="round", lunge=0.2)], 1.2, 1.6, 2, "LONG"),                           # roundhouse
    ("auto5", [Strike(R, 6, 130, -115, spin=-360, lunge=0.35)], 1.2, 1.4, 3, "SHORT"),              # spinning wide rake
    ("auto6", [Strike(R, kick="knee"), Strike(R, 85, 120, -30)], 1.1, 1.3, 1, "LONG"),              # knee, claw dropped down
    ("auto7", [Strike(R, 8, 115, -105, low=True, lunge=0.15), Strike(R, kick="axe", crack=1.6)], 1.2, 1.5, 2, "LONG"),  # low sweep, axe kick
    ("auto8", [Strike(R, kick="round", spin=-360, lunge=0.25)], 1.4, 1.8, 2, "LONG"),               # spinning hook kick
    ("auto9", [Strike(R, 75, 125, -40, leap=1.0, crack=2.0)], 1.3, 1.5, 2, "LONG"),                 # leaping downward rake
    ("auto10", [Strike(BOTH, 45, 125, -70, lunge=0.3), Strike(BOTH, 88, 115, -30, lunge=0.25, crack=3.0)], 1.6, 1.7, 4, "LONG"),  # X-slash, both claws smashed down
]
DUAL = [
    ("dual1", [Strike(R, 35, 125, -95)], 1.0, 1.0, 1, "SHORT"),
    ("dual2", [Strike(L, 35, 125, -95)], 1.0, 1.0, 1, "SHORT"),
    ("dual3", [Strike(R, 22, -100, 120), Strike(L, 22, -100, 120)], 0.9, 1.0, 1, "SHORT"),          # twin rising rakes
    ("dual4", [Strike(R, kick="front", lunge=0.1)], 1.0, 1.5, 1, "LONG"),                           # snap kick
    ("dual5", [Strike(BOTH, 45, 125, -70, lunge=0.25)], 1.2, 1.2, 2, "SHORT"),                      # X-slash
    ("dual6", [Strike(R, 6, 125, -110, spin=-360), Strike(L, 6, 125, -110)], 1.1, 1.3, 3, "SHORT"),  # spinning rake, then the other claw
    ("dual7", [Strike(L, kick="round"), Strike(R, 30, 120, -95)], 1.1, 1.4, 2, "LONG"),             # left roundhouse, right rake
    ("dual8", [Strike(R, 40, 120, -80), Strike(L, 40, 120, -80), Strike(R, 15, -90, 110), Strike(L, 15, -90, 110)], 0.8, 1.0, 2, "SHORT"),  # four-claw frenzy
    ("dual9", [Strike(R, kick="axe", crack=1.6), Strike(BOTH, 88, 115, -30, crack=2.4)], 1.3, 1.5, 3, "LONG"),  # axe kick, double smash
    ("dual10", [Strike(BOTH, 80, 125, -40, leap=1.0, crack=3.0), Strike(BOTH, 6, 125, -110, spin=-360)], 1.6, 1.8, 4, "LONG"),  # leaping smash, spin
]
DASHES = [
    ("dash", [Strike(R, elbow=True, lunge=0.6)], 0.62),                                         # single: a driving elbow strike
    ("dual_dash", [Strike(R, 35, 115, -80, lunge=0.3), Strike(L, 35, 115, -80, lunge=0.3),
                   Strike(R, 15, 105, -90, lunge=0.3), Strike(L, 15, 105, -90, lunge=0.3)], 0.5),   # dual: a running shred
]


def idle():
    """A predator's guard: the right claw held out in front, flexing; slow breathing, a slight sway, knees soft."""
    length = 2.4

    def state_at(t):
        w = 2 * math.pi * t / length
        breath, flex = math.sin(w), math.sin(2 * w)
        st = dict(em.GUARD_LEGS)
        st["hips"] = (0.0, 8.0 + 2.0 * breath, 0.0)
        st["waist"] = (4.0, 4.0, 0.0)
        st["chest"] = (7.0 + 2.5 * breath, 6.0 + 3.0 * flex, 0.0)
        st["head"] = (-8.0 - 2.0 * breath, -10.0, 0.0)
        st["sink"] = 0.05 + 0.015 * (0.5 - 0.5 * math.cos(2 * w))
        st["right_hand"] = hand(GUARD["right"] + np.array([0.02 * flex, -0.03 * breath, 0.025 * flex]))
        st["left_hand"] = hand(GUARD["left"] + np.array([0.0, 0.0, 0.015 * breath]))
        return st

    em.write_anim("idle", length, state_at, root_motion=False)


def guard():
    """Right-click guard (Epic Fight's guard skill): both claws crossed in an X before the face and chest, elbows
    in, the trunk hunched forward behind them, knees bent; a slow breath so it reads alive."""
    length = 2.0

    def state_at(t):
        w = 2 * math.pi * t / length
        breath = math.sin(w)
        st = dict(em.LOAD_LEGS)
        st["hips"] = (6.0, 10.0, 0.0)
        st["waist"] = (6.0, 4.0, 0.0)
        st["chest"] = (9.0 + 1.5 * breath, 4.0, 0.0)
        st["head"] = (-14.0 - 1.0 * breath, -12.0, 0.0)
        st["sink"] = 0.1 + 0.01 * breath
        st["right_hand"] = hand(np.array([-0.1, -0.44, 1.5]) + np.array([0.0, 0.0, 0.01 * breath]))
        st["left_hand"] = hand(np.array([0.12, -0.4, 1.36]) + np.array([0.0, 0.0, 0.01 * breath]))
        return st

    em.write_anim("guard", length, state_at, root_motion=False)


def jf(x):
    return f"{x}F"


def java_move(name, d, dmg, imp, extra, stun):
    trails = ", ".join(f'new Trail("{j}", {jf(a)}, {jf(b)})' for j, a, b in d["trails"])
    slashes = ", ".join("new float[]{" + ", ".join(jf(x) for x in sl) + "}" for sl in d["slashes"])
    claws = ", ".join(f"new Claw({jf(t)}, {str(left).lower()})" for t, left in d["claws"])
    cracks = ", ".join(f'new Crack({jf(t)}, "{j}", {r})' for t, j, r in d["cracks"])
    joints = ", ".join(f'"{j}"' for j in d["joints"])
    return (f'        new Move("{name}", {jf(d["antic"])}, {jf(d["contact"])}, {jf(d["recovery"])}, List.of({joints}), '
            f'{jf(dmg)}, {jf(imp)}, {extra}, StunType.{stun},\n'
            f'            List.of({trails}),\n            List.of({slashes}),\n            List.of({claws}),\n            List.of({cracks}))')


def main():
    rows = {"SINGLE": [], "DUAL": [], "DASHES": []}
    for key, moves, tempo in (("SINGLE", SINGLE, 0.62), ("DUAL", DUAL, 0.55)):
        for name, strikes, dmg, imp, extra, stun in moves:
            d = move(name, strikes, tempo)
            print(f"{name}: antic {d['antic']:.3f} contact {d['contact']:.3f} recovery {d['recovery']:.3f} joints {d['joints']}")
            rows[key].append(java_move(name, d, dmg, imp, extra, stun))
    for name, strikes, tempo in DASHES:
        d = move(name, strikes, tempo, dash=True)
        print(f"{name}: antic {d['antic']:.3f} contact {d['contact']:.3f} recovery {d['recovery']:.3f}")
        rows["DASHES"].append(java_move(name, d, 1.0, 1.0, 0, "SHORT"))
    idle()
    guard()
    with open(JAVA, "w") as f:
        f.write(f"""package com.obsidianarsenal.efcompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Generated by tools/gen_claw_anims.py - do not edit. Every claw attack: its hit window (antic, contact, recovery),
 * the joints that hit (Tool_R/L claws, Leg_R/L kicks, Elbow_R), damage / impact multipliers, extra targets and stun,
 * the trail of each strike, its purple slash crescents ({{time, tilt, from, to, side, height, radius}}, see
 * GauntletFx.SLASH), when each claw snaps out, and any ground cracks.
 */
public final class ClawMoves {{
    public record Trail(String joint, float start, float end) {{}}
    public record Claw(float time, boolean left) {{}}
    public record Crack(float time, String joint, double radius) {{}}
    public record Move(String id, float antic, float contact, float recovery, List<String> joints, float damage, float impact,
                       int extraTargets, StunType stun, List<Trail> trails, List<float[]> slashes, List<Claw> claws, List<Crack> cracks) {{}}

    public static final List<Move> SINGLE = List.of(
{",\n".join(rows["SINGLE"])});

    public static final List<Move> DUAL = List.of(
{",\n".join(rows["DUAL"])});

    public static final List<Move> DASHES = List.of(
{",\n".join(rows["DASHES"])});

    public static final Map<String, Move> OF = new HashMap<>();

    static {{
        for (List<Move> l : List.of(SINGLE, DUAL, DASHES)) for (Move m : l) OF.put(m.id(), m);
    }}

    private ClawMoves() {{}}
}}
""")
    print("wrote", sum(len(v) for v in rows.values()), "claw attacks, idle and ClawMoves.java")


if __name__ == "__main__":
    main()
