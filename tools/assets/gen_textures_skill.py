#!/usr/bin/env python3
"""Generates the skill node icons (PLACEHOLDER art, original, deterministic).

One 16x16 GUI sprite per skill node, drawn as holo line art at 4x and downsampled, with a dark
outline so it reads on the node socket, the HUD ability socket and the slot bar alike. Each tree
has its own light: Universal Force in holo blue-white, Lightsaber Combat in pale blade blue and
steel, Jedi Discipline in pale gold, Sith Domination in deep red. The motifs are invented for this
mod (palms, bent air, blades, motes, arcs); none imitates an existing game's ability icons.

GUI sprites (auto-stitched into the vanilla GUI atlas, ids oldrepublic:skill/<node>) under
src/main/resources/assets/oldrepublic/textures/gui/sprites/skill/<node>.png

Run from the repository root: python3 tools/assets/gen_textures_skill.py
"""
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "oldrepublic", "textures", "gui",
                   "sprites", "skill")
S = 4  # supersampling factor
OUTLINE = (10, 13, 17)

# Tree palettes: (main, bright core, dim)
UNIVERSAL = ((143, 216, 255), (226, 246, 255), (62, 126, 166))
SABER = ((170, 214, 255), (240, 250, 255), (104, 114, 126))
JEDI = ((232, 215, 160), (255, 246, 214), (150, 126, 70))
SITH = ((214, 62, 66), (255, 196, 180), (120, 30, 34))
STEEL = (150, 160, 170)
HAND = (200, 222, 236)


class Pen:
    """Draws in 16x16 icon coordinates on a 4x canvas."""

    def __init__(self, palette):
        self.img = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.main, self.core, self.dim = palette

    def p(self, pts):
        return [(x * S, y * S) for x, y in pts]

    def line(self, pts, color=None, w=1.0):
        self.d.line(self.p(pts), fill=(color or self.main) + (255,), width=max(1, int(w * S)), joint="curve")

    def arc(self, cx, cy, r, start, end, color=None, w=1.0, ry=None):
        ry = r if ry is None else ry
        self.d.arc([(cx - r) * S, (cy - ry) * S, (cx + r) * S, (cy + ry) * S], start, end, fill=(color or self.main) + (255,), width=max(1, int(w * S)))

    def dot(self, x, y, r, color=None, alpha=255):
        self.d.ellipse([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], fill=(color or self.core) + (alpha,))

    def poly(self, pts, color=None, alpha=255):
        self.d.polygon(self.p(pts), fill=(color or self.main) + (alpha,))

    def rect(self, x0, y0, x1, y1, color=None, alpha=255):
        self.d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], fill=(color or self.main) + (alpha,))

    # ---- shared motifs
    def palm(self, x, y, facing=1):
        """An open palm in profile with fingers toward `facing` (+1 right, -1 left)."""
        self.d.rounded_rectangle(self.box(x, y - 2.5, x + 3.5 * facing, y + 2.5), radius=S, fill=HAND + (235,))
        for i, fy in enumerate((-2.5, -1.2, 0.1, 1.4)):
            x0 = x + 3 * facing
            x1 = x + (6.2 - i * 0.5) * facing
            self.d.rounded_rectangle(self.box(x0, y + fy, x1, y + fy + 1.0), radius=S // 2, fill=HAND + (235,))

    def box(self, x0, y0, x1, y1):
        return [min(x0, x1) * S, min(y0, y1) * S, max(x0, x1) * S, max(y0, y1) * S]

    def blade(self, x0, y0, x1, y1, hilt=True, color=None):
        """A lit blade from (x0, y0) to (x1, y1) with a short steel hilt behind (x0, y0)."""
        color = color or self.main
        self.line([(x0, y0), (x1, y1)], color, 1.6)
        self.line([(x0, y0), (x1, y1)], self.core, 0.6)
        if hilt:
            dx, dy = x1 - x0, y1 - y0
            n = math.hypot(dx, dy) or 1
            hx, hy = x0 - dx / n * 3, y0 - dy / n * 3
            self.line([(hx, hy), (x0, y0)], STEEL, 1.4)

    def bolt(self, pts, w=1.0):
        self.line(pts, self.main, w + 0.6)
        self.line(pts, self.core, w * 0.5)

    def done(self):
        small = self.img.resize((16, 16), Image.LANCZOS)
        px = small.load()
        # Snap soft alpha so the icon stays crisp at 1:1, then add a dark outline.
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[x, y]
                px[x, y] = (r, g, b, 0 if a < 60 else 255 if a > 150 else a)
        out = small.copy()
        opx = out.load()
        for y in range(16):
            for x in range(16):
                if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 150
                                            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    opx[x, y] = OUTLINE + (190,)
        return out


ICONS = {}


def icon(name, palette):
    def wrap(fn):
        ICONS[name] = (palette, fn)
        return fn
    return wrap


# ---------------------------------------------------------------- Universal Force
@icon("force_push", UNIVERSAL)
def force_push(p):
    p.palm(2, 8)
    for i, r in enumerate((3, 5.5, 8)):
        p.arc(7, 8, r, -50, 50, p.main if i < 2 else p.dim, 1.0)


@icon("push_repulse_wave", UNIVERSAL)
def repulse_wave(p):
    p.palm(1.5, 8)
    for i, r in enumerate((3.5, 6.5, 9.5)):
        p.arc(6, 8, r, -70, 70, p.main if i < 2 else p.dim, 1.0, ry=r * 1.1)
    for x in (10, 12.5, 14.5):
        p.dot(x, 14, 0.5, p.dim)


@icon("push_focused_blast", UNIVERSAL)
def focused_blast(p):
    p.palm(1.5, 8)
    for off in (-1.6, 1.6):
        p.line([(7, 8 + off), (12.5, 8)], p.dim, 0.7)
    p.line([(7, 8), (13, 8)], p.core, 1.2)
    p.rect(13.5, 3, 15, 13, STEEL)
    for x, y in ((12, 4.5), (11.5, 11.5)):
        p.rect(x, y, x + 0.9, y + 0.9, STEEL)


@icon("push_deflecting_pulse", UNIVERSAL)
def deflecting_pulse(p):
    p.dot(8, 8, 1.6, HAND)
    for start in (20, 110, 200, 290):
        p.arc(8, 8, 5, start, start + 55, p.main, 1.0)
    p.line([(13, 3), (15, 1)], (255, 120, 90), 0.9)
    p.line([(3, 13), (1, 15)], (255, 120, 90), 0.9)


@icon("force_pull", UNIVERSAL)
def force_pull(p):
    p.palm(14, 8, -1)
    for i, r in enumerate((3, 5.5, 8)):
        p.arc(9 + r, 8, r, 140, 220, p.main if i < 2 else p.dim, 1.0)
    p.line([(1.5, 8), (5, 8)], p.core, 0.8)
    p.poly([(5.8, 8), (4.2, 6.8), (4.2, 9.2)], p.core)


@icon("pull_disarm", UNIVERSAL)
def pull_disarm(p):
    p.palm(14.5, 9, -1)
    # A blaster pistol tumbling toward the hand
    p.poly([(2, 4), (7, 4), (7, 5.5), (4.5, 5.5), (4, 8), (2.6, 8), (3, 5.5), (2, 5.5)], STEEL)
    for x in (8.5, 10):
        p.line([(x, 5), (x + 1, 6)], p.main, 0.7)
    p.arc(5, 10, 3, 200, 330, p.dim, 0.7)


@icon("pull_reel", UNIVERSAL)
def pull_reel(p):
    p.palm(14.5, 8, -1)
    p.arc(7, 8, 5, 100, 340, p.main, 1.0)
    p.poly([(7.3, 2.6), (9.5, 3.2), (8, 4.6)], p.main)
    p.dot(7, 8, 1.2, HAND)


@icon("force_jump", UNIVERSAL)
def force_jump(p):
    for i, y in enumerate((11, 7.5, 4)):
        c = p.main if i < 2 else p.core
        p.line([(4, y + 3), (8, y), (12, y + 3)], c, 1.2)
    p.line([(3, 15), (13, 15)], p.dim, 0.8)


@icon("jump_crushing_descent", UNIVERSAL)
def crushing_descent(p):
    p.line([(8, 1), (8, 9)], p.core, 1.2)
    p.poly([(8, 11), (5.5, 7.5), (10.5, 7.5)], p.main)
    p.line([(1, 13), (15, 13)], STEEL, 1.0)
    for x0, x1, y in ((7, 4, 15), (9, 12, 15), (8, 8, 15.5)):
        p.line([(x0, 13), (x1, y)], p.dim, 0.7)
    p.arc(8, 13, 6, 190, 350, p.main, 0.7, ry=2.5)


@icon("jump_soaring_leap", UNIVERSAL)
def soaring_leap(p):
    p.arc(9, 13, 7.5, 200, 330, p.main, 1.1, ry=9)
    for i, y in enumerate((5, 7, 9)):
        p.line([(11 + i, y), (15, y - 1.2)], p.dim, 0.7)
    p.dot(13.5, 6, 1.0, p.core)


@icon("deep_reserves", UNIVERSAL)
def deep_reserves(p):
    p.d.rounded_rectangle(p.box(4, 2, 12, 15), radius=2 * S, outline=STEEL + (255,), width=S)
    for i, y in enumerate((12.5, 10, 7.5)):
        p.rect(5.2, y, 10.8, y + 1.6, p.main if i < 2 else p.dim)
    p.dot(8, 4.5, 0.7, p.core)


@icon("quickened_flow", UNIVERSAL)
def quickened_flow(p):
    for i, y in enumerate((5, 8.5, 12)):
        pts = [(1.5 + t, y + math.sin(t * 0.9 + i) * 1.1) for t in [k * 0.5 for k in range(0, 22)]]
        p.line(pts, p.main if i != 1 else p.core, 0.9)
    p.poly([(15, 8.5), (12.5, 6.8), (12.5, 10.2)], p.core)


@icon("unbound", UNIVERSAL)
def unbound(p):
    p.d.ellipse(p.box(1.5, 5, 7.5, 11), outline=STEEL + (255,), width=int(1.2 * S))
    p.d.arc(p.box(8.5, 5, 14.5, 11), 60, 300, fill=STEEL + (255,), width=int(1.2 * S))
    for x, y in ((9, 3), (11.5, 2.5), (8.5, 13.5)):
        p.line([(x, y), (x + 0.8, y - 0.8)], p.core, 0.7)
    p.arc(8, 8, 7, 30, 90, p.main, 0.7)


# ---------------------------------------------------------------- Lightsaber Combat
@icon("saber_lunge", SABER)
def saber_lunge(p):
    p.blade(4, 12, 14, 2)
    for i, y in enumerate((8, 10.5, 13)):
        p.line([(0.5, y + 1), (4 - i * 0.6, y - 2)], p.dim, 0.6)


@icon("saber_throw", SABER)
def saber_throw(p):
    p.blade(6, 10, 12, 4)
    p.arc(8, 8, 6.5, 120, 330, p.dim, 0.8)
    p.poly([(2.4, 10.8), (1.4, 7.8), (4, 9)], p.dim)


@icon("throw_ricochet", SABER)
def throw_ricochet(p):
    p.line([(1.5, 13), (5.5, 4), (10, 12), (14.5, 3)], p.main, 0.9)
    for x, y in ((5.5, 4), (10, 12)):
        p.dot(x, y, 1.1, p.core)
    p.blade(12.5, 6, 14.8, 2.2, hilt=False)


@icon("throw_rending_arc", SABER)
def throw_rending_arc(p):
    p.arc(8, 9, 6.5, 190, 350, p.main, 1.6)
    p.arc(8, 9, 6.5, 190, 350, p.core, 0.6)
    for x, y in ((4, 13), (8, 14), (12, 13)):
        p.line([(x, y), (x + 1, y + 1.5)], p.dim, 0.7)


@icon("steady_guard", SABER)
def steady_guard(p):
    p.blade(8, 13, 8, 2)
    p.arc(8, 8, 6.5, 120, 240, STEEL, 1.0)
    p.arc(8, 8, 6.5, -60, 60, STEEL, 1.0)


@icon("riposte", SABER)
def riposte(p):
    p.blade(3, 13, 13, 3)
    p.blade(13, 13, 5, 5, color=p.dim)
    p.arc(11, 11, 3.5, 0, 200, p.core, 0.8)


@icon("deflection_control", SABER)
def deflection_control(p):
    p.blade(3, 14, 7, 5)
    p.line([(15, 9), (7.5, 8)], (255, 120, 90), 1.0)
    p.line([(7.5, 8), (13, 2.5)], (255, 160, 120), 0.8)
    p.arc(13.5, 2.5, 1.8, 0, 360, p.core, 0.5)


@icon("form_soresu", SABER)
def form_soresu(p):
    p.arc(8, 8, 6.8, 0, 360, p.dim, 0.8)
    p.blade(3.5, 9, 12.5, 9, hilt=False)
    p.rect(1.5, 8.4, 3.5, 9.6, STEEL)


@icon("form_ataru", SABER)
def form_ataru(p):
    pts = [(8 + math.cos(t) * (1 + t * 0.9), 8 + math.sin(t) * (1 + t * 0.9)) for t in [k * 0.25 for k in range(0, 27)]]
    p.line(pts, p.dim, 0.8)
    p.blade(9, 9, 14.5, 1.5)


# ---------------------------------------------------------------- Jedi Discipline
@icon("force_heal", JEDI)
def force_heal(p):
    p.palm(2, 10)
    for x, y, r in ((10, 5, 1.4), (13, 8, 1.0), (11.5, 11.5, 0.8), (8, 3, 0.7)):
        p.dot(x, y, r + 0.6, p.main, 120)
        p.dot(x, y, r, p.core)


@icon("heal_shared_light", JEDI)
def heal_shared_light(p):
    pts = ((8, 3), (3, 12), (13, 12))
    for a, b in ((0, 1), (1, 2), (2, 0)):
        p.line([pts[a], pts[b]], p.dim, 0.6)
    for x, y in pts:
        p.dot(x, y, 2.0, p.main, 140)
        p.dot(x, y, 1.2, p.core)


@icon("heal_serene_ward", JEDI)
def heal_serene_ward(p):
    p.poly([(8, 1.5), (14, 4), (13, 10), (8, 14.5), (3, 10), (2, 4)], p.main, 90)
    p.d.line(p.p([(8, 1.5), (14, 4), (13, 10), (8, 14.5), (3, 10), (2, 4), (8, 1.5)]), fill=p.main + (255,), width=S)
    p.dot(8, 8, 1.5, p.core)


@icon("centered_mind", JEDI)
def centered_mind(p):
    for i, r in enumerate((6.5, 4.2, 2)):
        p.arc(8, 8, r, 0, 360, p.main if i else p.dim, 0.8)
    p.dot(8, 8, 0.9, p.core)


@icon("calm_focus", JEDI)
def calm_focus(p):
    p.poly([(8, 2), (10.2, 6.5), (8, 8.5), (5.8, 6.5)], p.core)
    for i, (y, half) in enumerate(((11, 5.5), (13, 3.8), (15, 2))):
        p.line([(8 - half, y), (8 + half, y)], p.main if i == 0 else p.dim, 0.7)


@icon("guardians_resolve", JEDI)
def guardians_resolve(p):
    p.arc(8, 13, 7, 200, 340, p.main, 1.2, ry=9)
    for x in (5, 11):
        p.dot(x, 9, 1.3, HAND)
        p.rect(x - 1.3, 10.8, x + 1.3, 14.5, HAND)


# ---------------------------------------------------------------- Sith Domination
@icon("force_lightning", SITH)
def force_lightning(p):
    p.palm(1.5, 9)
    p.bolt([(7.5, 8), (10, 6.5), (9.5, 9), (12.5, 7), (15, 8)], 0.8)
    p.bolt([(7.5, 10), (10.5, 11.5), (12, 10.5), (14.5, 13)], 0.6)


@icon("lightning_chain", SITH)
def lightning_chain(p):
    pts = ((2.5, 12.5), (8, 4), (13.5, 11))
    p.bolt([pts[0], (4.5, 8), (6, 8.8), pts[1]], 0.6)
    p.bolt([pts[1], (10.5, 7.5), (11, 9.5), pts[2]], 0.6)
    for x, y in pts:
        p.dot(x, y, 1.3, p.dim)


@icon("lightning_consuming", SITH)
def lightning_consuming(p):
    pts = [(8 + math.cos(t) * (7 - t * 0.9), 8 + math.sin(t) * (7 - t * 0.9)) for t in [k * 0.3 for k in range(0, 24)]]
    p.line(pts, p.main, 0.9)
    p.dot(8, 8, 1.4, p.core)


@icon("fury", SITH)
def fury(p):
    p.poly([(8, 1.5), (11, 6), (10, 7), (13, 10), (11.5, 15), (4.5, 15), (3, 10), (6, 7), (5, 6)], p.main)
    p.poly([(8, 6), (10, 10), (9, 14), (7, 14), (6, 10)], p.core)


@icon("hungering_dark", SITH)
def hungering_dark(p):
    p.dot(8, 8, 4, (20, 6, 10), 255)
    p.arc(8, 8, 4.2, 0, 360, p.main, 0.8)
    for ang in range(0, 360, 60):
        a = math.radians(ang)
        p.line([(8 + math.cos(a) * 7.5, 8 + math.sin(a) * 7.5), (8 + math.cos(a) * 5.5, 8 + math.sin(a) * 5.5)], p.core, 0.7)


@icon("dread_presence", SITH)
def dread_presence(p):
    # A hooded silhouette with marks fleeing away from it
    p.poly([(5, 15), (5.5, 7), (8, 2.5), (10.5, 7), (11, 15)], (34, 10, 14))
    p.d.line(p.p([(5, 15), (5.5, 7), (8, 2.5), (10.5, 7), (11, 15)]), fill=p.main + (255,), width=S)
    p.dot(7.2, 7, 0.5, p.core)
    p.dot(8.8, 7, 0.5, p.core)
    for y in (6, 10):
        p.line([(12.5, y), (15, y - 1)], p.dim, 0.6)
        p.line([(3.5, y), (1, y - 1)], p.dim, 0.6)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (palette, fn) in sorted(ICONS.items()):
        pen = Pen(palette)
        fn(pen)
        path = os.path.join(OUT, name + ".png")
        pen.done().save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
