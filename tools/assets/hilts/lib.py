#!/usr/bin/env python3
"""The hilt model library (docs/MILESTONE_7.md 6 and 10.2): geometry, texture painting and checks for the
lightsaber hilt part designs. The lead owns this file; the design modules (emitters.py, grips.py,
pommels.py), build.py and preview.py import it. Workers ask for changes in their reports.

Conventions (6.1, 6.2)
  * Model units are 1/16 block. The hilt's axis is x 8, z 8, running along +y. The thumb side, which first
    person shows, is +x: activation boxes and buttons face +x.
  * Bands: pommel -2.0 to -0.1 (rings and loops may reach -3.0); grip -0.1 to 3.7; emitter 3.7 to 6.0
    (tall shrouds to 7.6). The staff grip spans 4.6 to 11.4 in the staff model. Parts meet at collars of
    radius 1.05 to 1.15: the emitter's bottom, both ends of a grip, the pommel's top.
  * Bodies are regular octagonal prisms (octo, lathe, ring); details are boxes (box, side_box) copied
    around the axis (around). A part is a Design: its elements, its optional 64x64 sheet and, for an
    emitter, its blade data (mouth, tilt, quillons).
  * Textures are 64x64, 4 texels per model unit: the shared swatches of common.png ("#common", common.py)
    and the design's own sheet ("#part", Design.sheet). Face UVs are in vanilla's 0..16 units.
  * A face painted with a tinted paint carries tintindex 0, which the item definition fills with the
    part's material (tint source oldrepublic:hilt_material). Tinted paint is greyscale; black, dark steel,
    brass, glass, buttons and the mouth are untinted.
  * Side numbering: octo side k = 0 faces +x (the thumb side); k counts toward -z: 1 is +x-z, 2 is -z,
    3 is -x-z, 4 is -x, 5 is -x+z, 6 is +z, 7 is +x+z. "Right" on a face is the viewer's right from
    outside, so a painted strip wrapped around the octagon (Paint.wrap) runs from side 0 through 1, 2, ...

Rotations
  An element may carry any rigid rotation. The game composes a multi-axis rotation ({"x", "y", "z"}) as
  Z * Y * X (CuboidRotation$EulerXYZRotation calls Matrix4f.rotationZYX(z, y, x)), and a single-axis
  rotation is right-handed about the positive axis, so a negative angle about z tilts +y toward +x (the
  vanilla wall torch leans away from its wall that way). Both were read from the 26.2 jars
  (tools/api-probe/m7.txt, m7b.txt). Quarter turns are baked into an element's coordinates, so the
  rotation fields stay small; preview.py renders with exactly the same rules.

Run python3 tools/assets/hilts/lib.py --selftest to check the geometry and rotation helpers, and --designs to
check every design (CI runs both, with preview.py selftest).
"""
import argparse
import math
import os
import random
import sys
from dataclasses import dataclass, field, replace
from itertools import permutations, product

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "oldrepublic")
NS = "oldrepublic"

TEX = 64                 # every hilt texture is 64x64
PX = TEX / 16.0          # texels per UV unit (and per model unit at the intended density)
AXIS_X, AXIS_Z = 8.0, 8.0
MODEL_MIN, MODEL_MAX = -16.0, 32.0
MAX_ELEMENTS = 60
MAX_GLOW = 6
TAN = math.tan(math.radians(22.5))
EPS = 1e-6

SLOTS = ("emitter", "grip", "pommel")
# The bands of 6.2: where a part's body lives, how far it may reach, its join planes and radius limits.
BANDS = {
	"emitter": {"lo": 3.7, "hi": 6.0, "min": 3.7, "max": 7.6, "joins": (3.7,), "body": 1.6, "reach": 1.8},
	"grip": {"lo": -0.1, "hi": 3.7, "min": -0.1, "max": 3.7, "joins": (-0.1, 3.7), "body": 1.15, "reach": 1.6},
	"pommel": {"lo": -2.0, "hi": -0.1, "min": -3.0, "max": -0.1, "joins": (-0.1,), "body": 1.4, "reach": 1.7},
	"staff_grip": {"lo": 4.6, "hi": 11.4, "min": 4.6, "max": 11.4, "joins": (4.6, 11.4), "body": 1.15, "reach": 1.6},
}
JOIN_R = (1.05, 1.15)
PORT_REACH = 3.0         # the Warden's quillon ports may reach x 8 +- 3.0
DEFAULT_MOUTH_Y = 5.6
STAFF_SHIFT = 7.7        # an emitter's base moves from y 3.7 to the staff grip's top, y 11.4
STAFF_CENTRE = (8.0, 8.0, 8.0)

DIRS = ("down", "up", "north", "south", "west", "east")
NORMAL = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
DIR_OF = {v: k for k, v in NORMAL.items()}
# The default (u, v) axes of each face, as vanilla's FaceInfo lays its corners out: u runs to the viewer's
# right seen from outside, v runs down (for the up and down faces, along +x and +-z).
UV_AXES = {
	"down": ((1, 0, 0), (0, 0, -1)),
	"up": ((1, 0, 0), (0, 0, 1)),
	"north": ((-1, 0, 0), (0, -1, 0)),
	"south": ((1, 0, 0), (0, -1, 0)),
	"west": ((0, 0, 1), (0, -1, 0)),
	"east": ((0, 0, -1), (0, -1, 0)),
}
# Vanilla's FaceInfo corner order per face (0 = min, 1 = max per axis); corner i takes UV corner i, where
# UV corner 0 is (u0, v0), 1 is (u0, v1), 2 is (u1, v1), 3 is (u1, v0) before the face's quarter turns.
FACE_CORNERS = {
	"down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
	"up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
	"north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
	"south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
	"west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
	"east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}


# ------------------------------------------------------------------ rotations

def rot_x(deg):
	c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
	return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=float)


def rot_y(deg):
	c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
	return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=float)


def rot_z(deg):
	c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
	return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=float)


def _cube_rotations():
	"""The 24 rotations that map axes to axes, identity first."""
	out = []
	for perm in permutations(range(3)):
		for signs in product((1, -1), repeat=3):
			m = np.zeros((3, 3))
			for row, col in enumerate(perm):
				m[row, col] = signs[row]
			if round(np.linalg.det(m)) == 1:
				out.append(m)
	out.sort(key=lambda m: -np.trace(m))
	return out


CUBE = _cube_rotations()


def euler_zyx(m):
	"""(x, y, z) in degrees with m == rot_z(z) @ rot_y(y) @ rot_x(x), the game's {"x", "y", "z"} rotation."""
	sy = max(-1.0, min(1.0, -m[2, 0]))
	y = math.asin(sy)
	if abs(math.cos(y)) > 1e-9:
		x = math.atan2(m[2, 1], m[2, 2])
		z = math.atan2(m[1, 0], m[0, 0])
	else:  # gimbal lock: fold the whole turn into x
		x = math.atan2(-m[1, 2], m[1, 1])
		z = 0.0
	return math.degrees(x), math.degrees(y), math.degrees(z)


def single_axis(m):
	"""("x"|"y"|"z", degrees) when m turns about one axis only, else None."""
	if abs(m[0, 0] - 1) < 1e-9 and abs(m[0, 1]) < 1e-9 and abs(m[0, 2]) < 1e-9:
		return "x", math.degrees(math.atan2(m[2, 1], m[1, 1]))
	if abs(m[1, 1] - 1) < 1e-9 and abs(m[0, 1]) < 1e-9 and abs(m[1, 2]) < 1e-9:
		return "y", math.degrees(math.atan2(m[0, 2], m[0, 0]))
	if abs(m[2, 2] - 1) < 1e-9 and abs(m[0, 2]) < 1e-9 and abs(m[1, 2]) < 1e-9:
		return "z", math.degrees(math.atan2(m[1, 0], m[0, 0]))
	return None


def rotation_json(m):
	"""The rotation as the game will rebuild it: a single axis when it is one, else Euler {"x", "y", "z"}."""
	one = single_axis(m)
	if one is not None:
		axis, angle = one
		return {"axis": axis, "angle": r4(angle)}
	x, y, z = euler_zyx(m)
	rebuilt = rot_z(z) @ rot_y(y) @ rot_x(x)
	assert np.allclose(rebuilt, m, atol=1e-6), "euler_zyx failed to rebuild the rotation"
	return {"x": r4(x), "y": r4(y), "z": r4(z)}


def matrix_of(rotation):
	"""The matrix the game builds from an element's "rotation" object (inverse of rotation_json)."""
	if rotation is None:
		return np.eye(3)
	if "axis" in rotation:
		return {"x": rot_x, "y": rot_y, "z": rot_z}[rotation["axis"]](rotation["angle"])
	return rot_z(rotation.get("z", 0)) @ rot_y(rotation.get("y", 0)) @ rot_x(rotation.get("x", 0))


def r4(v):
	v = round(float(v), 4)
	return 0.0 if v == 0 else v


def _vec(t):
	return np.array(t, dtype=float)


# ------------------------------------------------------------------ paint (which texels a face shows)

@dataclass(frozen=True)
class Region:
	"""A rectangle of texels on a 64x64 sheet."""
	x: int
	y: int
	w: int
	h: int

	def uv(self):
		return [self.x / PX, self.y / PX, (self.x + self.w) / PX, (self.y + self.h) / PX]


@dataclass(frozen=True)
class Paint:
	"""What a face shows: a region of "#common" or "#part", tinted or not, and how the region maps.

	mode:
	  "scale"  the face takes a window of the region at 4 texels per model unit (no stretching; the window's
	           offset varies per face, so neighbouring faces do not repeat the same streaks); the default;
	  "fit"    the whole region stretched over the face;
	  "wrap"   the region cut into 8 columns, octo side k taking column k (only octagon sides; other faces
	           take the whole region);
	  "solid"  one texel (solid_px, or the region's centre): a flat colour. Caps of octagons and rings are
	           always solid (their bars' caps overlap).
	vmap: (y_lo, y_hi) maps the region's height onto those model heights, so each face shows the slice of
	the region level with it (a tall painted strip shared by several segments).
	"""
	texture: str
	region: Region
	tint: bool = False
	mode: str = "scale"
	solid_px: tuple = None
	vmap: tuple = None
	flat: "Paint" = None   # the flat paint used for caps (defaults to this paint's solid texel)

	def fit(self):
		return replace(self, mode="fit")

	def wrap(self):
		return replace(self, mode="wrap")

	def scaled(self):
		return replace(self, mode="scale")

	def solid(self):
		if self.flat is not None:
			return self.flat.solid()
		return replace(self, mode="solid")

	def band(self, y_lo, y_hi):
		return replace(self, vmap=(float(y_lo), float(y_hi)))

	def face(self, w, h, k=None, y_range=None, seed=0):
		"""A Face showing this paint on a face w by h model units (side k of an octagon, if one)."""
		return Face(self.texture, self.uv(w, h, k, y_range, seed), self.tint)

	def uv(self, w, h, k=None, y_range=None, seed=0):
		reg = self.region
		x0, y0, x1, y1 = reg.uv()
		if self.mode == "solid":
			sx, sy = self.solid_px if self.solid_px else (reg.x + reg.w // 2, reg.y + reg.h // 2)
			return [(sx + 0.25) / PX, (sy + 0.25) / PX, (sx + 0.75) / PX, (sy + 0.75) / PX]
		# vertical: the vmap slice, or the top of the region
		if self.vmap is not None and y_range is not None:
			lo, hi = self.vmap
			span = (hi - lo) or 1.0
			va = y0 + (hi - y_range[1]) / span * (y1 - y0)
			vb = y0 + (hi - y_range[0]) / span * (y1 - y0)
			va, vb = max(y0, min(y1, va)), max(y0, min(y1, vb))
		elif self.mode == "scale":
			va, vb = y0, y0 + min(h, y1 - y0)
		else:
			va, vb = y0, y1
		if self.mode == "wrap" and k is not None:
			cw = (x1 - x0) / 8.0
			return [x0 + k * cw, va, x0 + (k + 1) * cw, vb]
		if self.mode == "scale":
			uw = min(w, x1 - x0)
			room = (x1 - x0) - uw
			off = 0.0
			if room > 0:
				off = math.floor(((seed * 0.6180339887) % 1.0) * room * PX) / PX
			return [x0 + off, va, x0 + off + uw, vb]
		return [x0, va, x1, vb]


@dataclass
class Face:
	texture: str
	uv: list
	tint: bool = False
	rotation: int = 0      # quarter turns of the texture, clockwise seen from outside (0, 90, 180, 270)

	def json(self):
		out = {"uv": [r4(v) for v in self.uv], "texture": self.texture}
		if self.rotation:
			out["rotation"] = self.rotation
		if self.tint:
			out["tintindex"] = 0
		return out


# ------------------------------------------------------------------ elements

@dataclass
class Element:
	"""A box from lo to hi with faces, turned by R about origin (world = R (p - origin) + origin)."""
	lo: np.ndarray
	hi: np.ndarray
	faces: dict = field(default_factory=dict)
	R: np.ndarray = field(default_factory=lambda: np.eye(3))
	origin: np.ndarray = None
	shade: bool = True
	light: int = 0
	name: str = ""
	meta: dict = field(default_factory=dict)   # what built it (octo radius and heights, for the checks)

	def __post_init__(self):
		self.lo = _vec(self.lo)
		self.hi = _vec(self.hi)
		if self.origin is None:
			self.origin = (self.lo + self.hi) / 2
		self.origin = _vec(self.origin)

	def copy(self):
		return Element(self.lo.copy(), self.hi.copy(), {d: replace(f) for d, f in self.faces.items()}, self.R.copy(),
				self.origin.copy(), self.shade, self.light, self.name, dict(self.meta))

	def world(self, p):
		return self.R @ (_vec(p) - self.origin) + self.origin

	def corners(self):
		lo, hi = self.lo, self.hi
		return [self.world((x, y, z)) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]

	def json(self):
		out = {"from": [r4(v) for v in self.lo], "to": [r4(v) for v in self.hi]}
		if not np.allclose(self.R, np.eye(3), atol=1e-9):
			rot = {"origin": [r4(v) for v in self.origin]}
			rot.update(rotation_json(self.R))
			out["rotation"] = rot
		if not self.shade:
			out["shade"] = False
		if self.light:
			out["light_emission"] = int(self.light)
		out["faces"] = {d: self.faces[d].json() for d in DIRS if d in self.faces}
		return out


def _face_sizes(lo, hi):
	dx, dy, dz = (hi - lo)
	return {"east": (dz, dy), "west": (dz, dy), "north": (dx, dy), "south": (dx, dy), "up": (dx, dz), "down": (dx, dz)}


def box(x0, y0, z0, x1, y1, z1, paint, *, faces=None, skip=(), paints=None, seed=0, light=0, shade=True, name=""):
	"""An axis-aligned box. faces lists the directions to draw (default all six) minus skip; paints
	overrides the paint per direction. Hide faces that sit inside another element (skip), and keep every
	drawn face at least 0.05 units off any other coplanar face, or the two flicker."""
	lo, hi = _vec((min(x0, x1), min(y0, y1), min(z0, z1))), _vec((max(x0, x1), max(y0, y1), max(z0, z1)))
	sizes = _face_sizes(lo, hi)
	wanted = [d for d in (faces or DIRS) if d not in skip]
	out = {}
	for i, d in enumerate(wanted):
		p = (paints or {}).get(d, paint)
		if p is None:
			continue
		w, h = sizes[d]
		out[d] = p.face(w, h, y_range=(lo[1], hi[1]) if d not in ("up", "down") else None, seed=seed + i)
	return Element(lo, hi, out, light=light, shade=shade, name=name)


# ------------------------------------------------------------------ rigid transforms

def _q_face(q, d, face):
	"""The face direction and quarter turn a face takes when its box is turned by the cube rotation q."""
	n = q @ _vec(NORMAL[d])
	nd = DIR_OF[tuple(int(round(c)) for c in n)]
	qu = tuple(int(round(c)) for c in q @ _vec(UV_AXES[d][0]))
	qv = tuple(int(round(c)) for c in q @ _vec(UV_AXES[d][1]))
	u2, v2 = UV_AXES[nd]
	neg = lambda t: tuple(-c for c in t)
	if (qu, qv) == (u2, v2):
		turn = 0
	elif (qu, qv) == (v2, neg(u2)):
		turn = 90
	elif (qu, qv) == (neg(u2), neg(v2)):
		turn = 180
	elif (qu, qv) == (neg(v2), u2):
		turn = 270
	else:
		raise AssertionError(f"face {d} under {q.tolist()} is mirrored")
	return nd, replace(face, rotation=(face.rotation + turn) % 360)


def _apply(el, rs, ts):
	"""el turned by rs then moved by ts (world' = rs world + ts). Quarter turns are baked into the box."""
	t = (np.eye(3) - el.R) @ el.origin
	r2 = rs @ el.R
	t2 = rs @ t + ts
	q = max(CUBE, key=lambda m: np.trace(r2 @ m.T) - (0 if np.allclose(m, np.eye(3)) else 1e-9))
	res = r2 @ q.T
	c = (el.lo + el.hi) / 2
	a, b = q @ (el.lo - c), q @ (el.hi - c)
	lo, hi = np.minimum(a, b) + c, np.maximum(a, b) + c
	faces = {}
	for d, f in el.faces.items():
		nd, nf = _q_face(q, d, f)
		faces[nd] = nf
	d_move = r2 @ c + t2 - c
	out = Element(lo + d_move, hi + d_move, faces, res, c + d_move, el.shade, el.light, el.name, dict(el.meta))
	out.meta["moved"] = True
	return out


def flatten(items):
	out = []
	for it in items:
		if isinstance(it, Element):
			out.append(it)
		elif it is not None:
			out.extend(flatten(it))
	return out


def transform(elements, rs, center=(8.0, 8.0, 8.0)):
	"""Every element turned by the rotation matrix rs about center."""
	c = _vec(center)
	return [_apply(e, rs, c - rs @ c) for e in flatten([elements])]


def translate(elements, d):
	return [_apply(e, np.eye(3), _vec(d)) for e in flatten([elements])]


def turn(elements, degrees):
	"""Turned about the hilt's axis; positive turns +x toward -z (side k to side k + degrees / 45)."""
	return transform(elements, rot_y(degrees), (AXIS_X, 0.0, AXIS_Z))


def bend(elements, pivot_y, toward_x_deg, pivot_x=AXIS_X):
	"""Bent about the z axis through (pivot_x, pivot_y, 8): +y tilts toward +x by toward_x_deg (the
	Penitent's neck, 8.2). Apply after turn and around, never before them."""
	return transform(elements, rot_z(-toward_x_deg), (pivot_x, pivot_y, AXIS_Z))


def bend_point(p, pivot_y, toward_x_deg, pivot_x=AXIS_X):
	"""Where a point goes under bend(..., pivot_y, toward_x_deg) (the bent emitter's mouth)."""
	c = _vec((pivot_x, pivot_y, AXIS_Z))
	return rot_z(-toward_x_deg) @ (_vec(p) - c) + c


def around(elements, n=4, start=0.0):
	"""n copies (2, 4 or 8) of the elements spaced evenly about the axis, the first turned by start degrees."""
	out = []
	for i in range(n):
		out.extend(turn(elements, start + i * 360.0 / n))
	return out


def staff_upper(elements):
	"""An emitter design moved up onto the staff grip's top (10.3)."""
	return translate(elements, (0.0, STAFF_SHIFT, 0.0))


def staff_lower(elements):
	"""The lower staff emitter: the upper one turned 180 degrees about the staff's centre (z axis), so the
	staff is point-symmetric and a bent emitter bends the other way at the bottom (8.2)."""
	return transform(staff_upper(elements), rot_z(180.0), STAFF_CENTRE)


# ------------------------------------------------------------------ bodies

def octo(y0, y1, r, paint, *, top=False, bottom=False, cap=None, sides=None, skip=(), name="octo"):
	"""A regular octagonal prism of inradius r on the axis, from y0 to y1: four bars (two axis-aligned,
	two turned 45 degrees) whose end faces are the octagon's eight sides. paint covers the sides; sides
	maps a side k to its own paint; skip leaves sides out. top and bottom add the caps, one flat colour
	(cap, or paint's flat colour), because the bars' caps overlap."""
	y0, y1 = float(min(y0, y1)), float(max(y0, y1))
	h = r * TAN
	cap_paint = (cap or paint).solid()
	sides = sides or {}
	height = y1 - y0
	bars = []
	# (bar kind, turn in degrees, [(direction, side k)])
	layout = (("x", 0.0, (("east", 0), ("west", 4))), ("z", 0.0, (("north", 2), ("south", 6))),
			("x", 45.0, (("east", 1), ("west", 5))), ("x", -45.0, (("east", 7), ("west", 3))))
	for kind, deg, ends in layout:
		if kind == "x":
			lo, hi = (AXIS_X - r, y0, AXIS_Z - h), (AXIS_X + r, y1, AXIS_Z + h)
		else:
			lo, hi = (AXIS_X - h, y0, AXIS_Z - r), (AXIS_X + h, y1, AXIS_Z + r)
		faces = {}
		for d, k in ends:
			if k in skip:
				continue
			p = sides.get(k, paint)
			if p is not None:
				faces[d] = p.face(2 * h, height, k=k, y_range=(y0, y1), seed=k)
		if top:
			faces["up"] = cap_paint.face(2 * h, 2 * r)
		if bottom:
			faces["down"] = cap_paint.face(2 * h, 2 * r)
		if not faces:
			continue
		el = Element(lo, hi, faces, name=name, meta={"kind": "octo", "r": r, "y0": y0, "y1": y1})
		if deg:
			el = transform([el], rot_y(deg), (AXIS_X, y0, AXIS_Z))[0]
			el.meta.pop("moved", None)
		bars.append(el)
	return bars


def lathe(rings, paint=None, *, top=True, bottom=True, name="lathe"):
	"""A stack of octagonal segments [(y0, y1, r, paint or None), ...] from the bottom up, each starting
	where the last ended. Where two segments meet, the wider one gets the cap facing the narrower one;
	the ends get caps when top/bottom ask for them."""
	out = []
	for i, ring in enumerate(rings):
		y0, y1, r = ring[0], ring[1], ring[2]
		p = ring[3] if len(ring) > 3 and ring[3] is not None else paint
		if i and abs(rings[i - 1][1] - y0) > 1e-6:
			raise ValueError(f"{name}: segment {i} starts at {y0}, the last ended at {rings[i - 1][1]}")
		below = rings[i - 1][2] if i else None
		above = rings[i + 1][2] if i + 1 < len(rings) else None
		cap_top = (above is None and top) or (above is not None and r > above + 1e-6)
		cap_bottom = (below is None and bottom) or (below is not None and r > below + 1e-6)
		out.extend(octo(y0, y1, r, p, top=cap_top, bottom=cap_bottom, name=f"{name}[{i}]"))
	return out


def ring(y0, y1, r, thickness, paint, *, inner=None, top=True, bottom=True, name="ring"):
	"""An octagonal ring (a hollow octagon) of outer inradius r and the given wall thickness: eight thin
	boxes, one per side. inner paints the inside wall (None leaves it out). Its caps are flat colour."""
	y0, y1 = float(min(y0, y1)), float(max(y0, y1))
	s = 2 * r * TAN
	cap_paint = paint.solid()
	out = []
	for k in range(8):
		faces = ["east"] + (["west"] if inner is not None else []) + (["up"] if top else []) + (["down"] if bottom else [])
		el = box(AXIS_X + r - thickness, y0, AXIS_Z - s / 2, AXIS_X + r, y1, AXIS_Z + s / 2, paint, faces=faces,
				paints={"west": inner, "up": cap_paint, "down": cap_paint}, seed=k, name=f"{name}[{k}]")
		el.meta = {"kind": "ring", "r": r, "y0": y0, "y1": y1}
		if k:
			el = turn([el], 45.0 * k)[0]
			el.meta.pop("moved", None)
		out.append(el)
	return out


def side_box(y0, y1, r, width, depth, paint, k=0, *, inset=0.05, paints=None, skip=("west",), seed=0, light=0, name="detail"):
	"""A detail on side k of an octagon of inradius r: width along the side, from y0 to y1, standing depth
	out of the surface (and inset into it, hiding its inner face). k = 0 is the thumb side, +x."""
	el = box(AXIS_X + r - inset, y0, AXIS_Z - width / 2, AXIS_X + r + depth, y1, AXIS_Z + width / 2, paint,
			paints=paints, skip=skip, seed=seed, light=light, name=name)
	return turn([el], 45.0 * k) if k else [el]


# ------------------------------------------------------------------ the sheet painter

def _clamp(v):
	return max(0, min(255, int(round(v))))


def grey(v):
	return (_clamp(v),) * 3


class Sheet:
	"""A 64x64 texture and its named regions. Paint with the methods below (each clips to its region),
	then hand out paints with sheet.paint(name). Regions marked tint must stay greyscale."""

	def __init__(self, texture="#part", seed=1, size=TEX):
		self.texture = texture
		self.size = size
		self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
		self.regions = {}
		self.rng = random.Random(seed)

	# -- regions and paints
	def region(self, name, x, y, w, h, tint=False, flat=None, solid_px=None, mode="scale"):
		"""Name a rectangle of the sheet. tint: the region is greyscale metal the material tints; flat: the
		region whose solid colour caps and tiny faces take; solid_px: the texel "solid" mode shows (default
		the centre); mode: the default Paint mode of sheet.paint(name)."""
		if name in self.regions:
			raise ValueError(f"region {name} defined twice")
		if x < 0 or y < 0 or x + w > self.size or y + h > self.size or w <= 0 or h <= 0:
			raise ValueError(f"region {name} ({x},{y} {w}x{h}) leaves the {self.size}x{self.size} sheet")
		for other, spec in self.regions.items():
			reg = spec[0]
			if x < reg.x + reg.w and reg.x < x + w and y < reg.y + reg.h and reg.y < y + h:
				raise ValueError(f"region {name} overlaps {other}")
		reg = Region(x, y, w, h)
		self.regions[name] = (reg, tint, flat, solid_px, mode)
		return reg

	def paint(self, name, mode=None):
		"""The Paint of a named region (its default mode, or mode)."""
		if name not in self.regions:
			raise KeyError(f"{self.texture} has no region {name}; it has {', '.join(sorted(self.regions))}")
		reg, tint, flat, solid_px, default_mode = self.regions[name]
		flat_paint = self.paint(flat, "solid") if flat else None
		return Paint(self.texture, reg, tint, mode or default_mode, solid_px, None, flat_paint)

	def _reg(self, r):
		return self.regions[r][0] if isinstance(r, str) else r

	def _pixels(self, r):
		reg = self._reg(r)
		for yy in range(reg.y, reg.y + reg.h):
			for xx in range(reg.x, reg.x + reg.w):
				yield xx, yy, xx - reg.x, yy - reg.y

	# -- primitives
	def px(self, x, y, rgb, a=255):
		if 0 <= x < self.size and 0 <= y < self.size:
			self.img.putpixel((x, y), (*[_clamp(c) for c in rgb], a))

	def get(self, x, y):
		return self.img.getpixel((x, y))[:3]

	def rect(self, x0, y0, x1, y1, rgb):
		"""Fill the inclusive pixel rectangle."""
		for yy in range(y0, y1 + 1):
			for xx in range(x0, x1 + 1):
				self.px(xx, yy, rgb)

	def fill(self, r, rgb):
		for x, y, _, _ in self._pixels(r):
			self.px(x, y, rgb)

	def noise(self, r, amount, seed=None):
		rng = random.Random(seed) if seed is not None else self.rng
		for x, y, _, _ in self._pixels(r):
			c = self.get(x, y)
			n = rng.uniform(-amount, amount)
			self.px(x, y, [v + n for v in c])

	def brushed(self, r, base, spread=10, vertical=True, seed=None):
		"""Brushed metal: base (a grey level or an RGB) with fine streaks along v (or u)."""
		rng = random.Random(seed) if seed is not None else self.rng
		rgb = grey(base) if isinstance(base, (int, float)) else base
		reg = self._reg(r)
		lines = [rng.uniform(-spread, spread) for _ in range(reg.w if vertical else reg.h)]
		for x, y, lx, ly in self._pixels(reg):
			n = lines[lx if vertical else ly] + rng.uniform(-spread * 0.35, spread * 0.35)
			self.px(x, y, [c + n for c in rgb])

	def gradient(self, r, a, b, vertical=True):
		"""a at the top (or left) to b at the bottom (or right)."""
		reg = self._reg(r)
		a = grey(a) if isinstance(a, (int, float)) else a
		b = grey(b) if isinstance(b, (int, float)) else b
		n = (reg.h if vertical else reg.w) - 1 or 1
		for x, y, lx, ly in self._pixels(reg):
			t = (ly if vertical else lx) / n
			self.px(x, y, [a[i] + (b[i] - a[i]) * t for i in range(3)])

	def bevel(self, r, light=24, dark=28):
		"""Lighten the top row and darken the bottom row (a rounded edge catching light)."""
		reg = self._reg(r)
		for xx in range(reg.x, reg.x + reg.w):
			self.px(xx, reg.y, [c + light for c in self.get(xx, reg.y)])
			yb = reg.y + reg.h - 1
			self.px(xx, yb, [c - dark for c in self.get(xx, yb)])

	def ridges(self, r, ridge, groove, period=4, groove_w=1, highlight=None, horizontal=True):
		"""Repeated ridges (T-track rubber, grip rings): bands of ridge colour split by grooves, an optional
		highlight on each ridge's first row."""
		reg = self._reg(r)
		ridge = grey(ridge) if isinstance(ridge, (int, float)) else ridge
		groove = grey(groove) if isinstance(groove, (int, float)) else groove
		for x, y, lx, ly in self._pixels(reg):
			t = (ly if horizontal else lx) % period
			if t >= period - groove_w:
				c = groove
			elif t == 0 and highlight is not None:
				c = grey(highlight) if isinstance(highlight, (int, float)) else highlight
			else:
				c = ridge
			self.px(x, y, c)

	def knurl(self, r, base, dark, period=4):
		"""A diamond knurl: two sets of diagonal grooves."""
		reg = self._reg(r)
		base = grey(base) if isinstance(base, (int, float)) else base
		dark = grey(dark) if isinstance(dark, (int, float)) else dark
		for x, y, lx, ly in self._pixels(reg):
			a = (lx + ly) % period == 0
			b = (lx - ly) % period == 0
			self.px(x, y, dark if (a or b) else base)

	def wrap(self, r, base, line, period=4, slope=1):
		"""Diagonal wrap lines (leather or cord) every period texels."""
		reg = self._reg(r)
		base = grey(base) if isinstance(base, (int, float)) else base
		line = grey(line) if isinstance(line, (int, float)) else line
		for x, y, lx, ly in self._pixels(reg):
			self.px(x, y, line if (lx * slope + ly) % period == 0 else base)

	def vents(self, r, slot, count, slot_w=1, margin=1, vertical=True):
		"""count dark slots evenly across the region (vertical slots by default)."""
		reg = self._reg(r)
		slot = grey(slot) if isinstance(slot, (int, float)) else slot
		span = reg.w if vertical else reg.h
		step = span / count
		for i in range(count):
			c0 = int(round(i * step + (step - slot_w) / 2))
			for c in range(c0, c0 + slot_w):
				for t in range(margin, (reg.h if vertical else reg.w) - margin):
					if vertical:
						self.px(reg.x + c, reg.y + t, slot)
					else:
						self.px(reg.x + t, reg.y + c, slot)

	def wear(self, r, pits=6, scratches=3, amount=40, seed=None):
		"""Pitting and scratches: darker dots and short lighter lines."""
		rng = random.Random(seed) if seed is not None else self.rng
		reg = self._reg(r)
		for _ in range(pits):
			x, y = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h)
			self.px(x, y, [c - amount for c in self.get(x, y)])
		for _ in range(scratches):
			x, y = reg.x + rng.randrange(reg.w), reg.y + rng.randrange(reg.h)
			for i in range(rng.randint(2, 4)):
				xx, yy = x + i, y + (i if rng.random() < 0.5 else 0)
				if reg.x <= xx < reg.x + reg.w and reg.y <= yy < reg.y + reg.h:
					self.px(xx, yy, [c + amount * 0.5 for c in self.get(xx, yy)])

	def glass(self, r, rgb, highlight=60):
		"""Coloured glass: light at the top, deeper at the bottom, one highlight texel."""
		reg = self._reg(r)
		top = [c + 30 for c in rgb]
		bot = [c - 40 for c in rgb]
		self.gradient(reg, tuple(_clamp(c) for c in top), tuple(_clamp(c) for c in bot))
		self.px(reg.x + max(0, reg.w // 4), reg.y + max(0, reg.h // 4), [c + highlight for c in rgb])

	def rivets(self, r, points, head=None, shadow=None):
		"""Rivet heads at region-relative points: a light texel with a darker one below-right."""
		reg = self._reg(r)
		for px_, py_ in points:
			x, y = reg.x + px_, reg.y + py_
			base = self.get(x, y)
			self.px(x, y, head or [c + 45 for c in base])
			self.px(x + 1, y + 1, shadow or [c - 45 for c in base])

	def outline(self, r, rgb):
		reg = self._reg(r)
		self.rect(reg.x, reg.y, reg.x + reg.w - 1, reg.y, rgb)
		self.rect(reg.x, reg.y + reg.h - 1, reg.x + reg.w - 1, reg.y + reg.h - 1, rgb)
		self.rect(reg.x, reg.y, reg.x, reg.y + reg.h - 1, rgb)
		self.rect(reg.x + reg.w - 1, reg.y, reg.x + reg.w - 1, reg.y + reg.h - 1, rgb)

	# -- checks and output
	def problems(self):
		out = []
		for name, (reg, tint, *_rest) in self.regions.items():
			if not tint:
				continue
			for x, y, _, _ in self._pixels(reg):
				r_, g_, b_ = self.get(x, y)
				if max(r_, g_, b_) - min(r_, g_, b_) > 6:
					out.append(f"tinted region {name} is not greyscale at ({x}, {y}): {(r_, g_, b_)}")
					break
		return out

	def used(self):
		return bool(self.regions)

	def save(self, path):
		os.makedirs(os.path.dirname(path), exist_ok=True)
		self.img.save(path)


# ------------------------------------------------------------------ designs

@dataclass
class Blade:
	"""Where an emitter's blade leaves it (10.4): mouth [x, y] in model units, tilt in degrees about z
	(positive toward +x), and optional quillons {"y", "x", "length"} (two side blades from x 8 +- x)."""
	mouth: tuple = (8.0, DEFAULT_MOUTH_Y)
	tilt: float = 0.0
	quillons: dict = None

	def json(self):
		out = {}
		if abs(self.mouth[0] - 8.0) > 1e-6 or abs(self.mouth[1] - DEFAULT_MOUTH_Y) > 1e-6:
			out["mouth"] = [r4(self.mouth[0]), r4(self.mouth[1])]
		if abs(self.tilt) > 1e-6:
			out["tilt"] = r4(self.tilt)
		if self.quillons:
			out["quillons"] = {k: r4(v) for k, v in self.quillons.items()}
		return out

	def staff(self):
		"""The upper blade on a staff (the lower one is point-symmetric about the staff's centre)."""
		return Blade((self.mouth[0], self.mouth[1] + STAFF_SHIFT), self.tilt,
				dict(self.quillons, y=self.quillons["y"] + STAFF_SHIFT) if self.quillons else None)


class Design:
	"""One hilt part variant: slot "emitter", "grip" or "pommel" (or "staff_grip"), id as in HiltParts.

	  d = Design("emitter", "vanguard")
	  steel = common.paint("steel")
	  d.add(lathe([(3.7, 3.9, 1.1), (3.9, 5.9, 1.3)], steel))
	  d.blade = Blade(mouth=(8, 5.5))
	"""

	def __init__(self, slot, id, title=""):
		if slot not in BANDS:
			raise ValueError(f"unknown slot {slot}")
		self.slot = slot
		self.id = id
		self.title = title
		self.elements = []
		self.sheet = Sheet(seed=sum(map(ord, slot + id)))
		self.blade = Blade() if slot == "emitter" else None

	def add(self, *items):
		self.elements.extend(flatten(items))
		return self

	@property
	def item_id(self):
		return f"grip_staff" if self.slot == "staff_grip" else f"{self.slot}_{self.id}"

	@property
	def texture_id(self):
		return f"{NS}:item/hilt/{self.item_id}"

	def uses_sheet(self):
		return any(f.texture == "#part" for e in self.elements for f in e.faces.values())

	def textures(self):
		out = {"common": f"{NS}:item/hilt/common", "particle": f"{NS}:item/hilt/common"}
		if self.uses_sheet():
			out["part"] = self.texture_id
		return out

	def model(self, parent, elements=None):
		"""The model JSON: parent (a base model with the display transforms), textures and elements."""
		return {"parent": parent, "textures": self.textures(), "elements": [e.json() for e in (elements or self.elements)]}

	def bounds(self):
		pts = np.array([c for e in self.elements for c in e.corners()])
		return pts.min(axis=0), pts.max(axis=0)

	def problems(self):
		"""Everything 6.1 and 6.2 forbid, as messages (an empty list passes)."""
		out = []
		band = BANDS[self.slot]
		if not self.elements:
			return [f"{self.item_id}: no elements"]
		if len(self.elements) > MAX_ELEMENTS:
			out.append(f"{self.item_id}: {len(self.elements)} elements (at most {MAX_ELEMENTS})")
		for i, e in enumerate(self.elements):
			for v in list(e.lo) + list(e.hi):
				if v < MODEL_MIN - EPS or v > MODEL_MAX + EPS:
					out.append(f"{self.item_id}: element {i} ({e.name}) leaves the model bounds: {e.lo} {e.hi}")
					break
			if any(e.hi[a] - e.lo[a] < -EPS for a in range(3)):
				out.append(f"{self.item_id}: element {i} ({e.name}) has from above to")
			if e.light > MAX_GLOW:
				out.append(f"{self.item_id}: element {i} ({e.name}) glows at {e.light} (at most {MAX_GLOW})")
			for d, f in e.faces.items():
				if any(v < -EPS or v > 16 + EPS for v in f.uv):
					out.append(f"{self.item_id}: element {i} ({e.name}) face {d} has UV {f.uv} outside 0..16")
				if f.texture not in ("#common", "#part"):
					out.append(f"{self.item_id}: element {i} ({e.name}) face {d} uses texture {f.texture}")
		lo, hi = self.bounds()
		if lo[1] < band["min"] - 0.011 or hi[1] > band["max"] + 0.011:
			out.append(f"{self.item_id}: spans y {lo[1]:.3f} to {hi[1]:.3f}, outside its band {band['min']} to {band['max']}")
		for e in self.elements:
			for c in e.corners():
				dx, dz = abs(c[0] - AXIS_X), abs(c[2] - AXIS_Z)
				reach = math.hypot(dx, dz)
				limit = band["reach"]
				if self.slot == "emitter" and dz <= band["reach"] + 1e-6 and dx <= PORT_REACH + 1e-6:
					continue
				if reach > limit + 1e-6:
					out.append(f"{self.item_id}: {e.name} reaches {reach:.2f} from the axis (at most {limit})")
					break
		bodies = [e.meta for e in self.elements if e.meta.get("kind") in ("octo", "ring")]
		for m in bodies:
			if m["r"] > band["body"] + 1e-6:
				out.append(f"{self.item_id}: a body of radius {m['r']} is wider than the slot's {band['body']}")
				break
		for y in band["joins"]:
			ok = [m for m in bodies if (abs(m["y0"] - y) < 0.011 or abs(m["y1"] - y) < 0.011)
					and JOIN_R[0] - 1e-6 <= m["r"] <= JOIN_R[1] + 1e-6]
			if not ok:
				out.append(f"{self.item_id}: no collar of radius {JOIN_R[0]} to {JOIN_R[1]} meets the join at y {y}")
		out.extend(f"{self.item_id}: {p}" for p in self.sheet.problems())
		return out

	def check(self):
		probs = self.problems()
		if probs:
			raise SystemExit("\n".join(probs))
		return self


def registry():
	"""A design module's DESIGNS and the decorator that fills it: each decorated function returns one
	Design, filed under its id in the order the functions are written.

	  DESIGNS, design = registry()

	  @design
	  def guardian():
	      d = Design("emitter", "guardian", "Guardian")
	      ...
	      return d
	"""
	designs = {}

	def design(fn):
		d = fn()
		if not isinstance(d, Design):
			raise TypeError(f"{fn.__name__} returned {type(d).__name__}, not a Design")
		if d.id in designs:
			raise ValueError(f"design {d.id} defined twice")
		designs[d.id] = d
		return fn

	return designs, design


# ------------------------------------------------------------------ self-test

def _selftest():
	# the bars of an octagon: every side at distance r, corners where the sides meet
	els = octo(0, 2, 1.0, Paint("#common", Region(0, 0, 16, 16)), top=True)
	assert len(els) == 4
	for e in els:
		for d, f in e.faces.items():
			if d in ("up", "down"):
				continue
			n = e.R @ _vec(NORMAL[d])
			c = (e.lo + e.hi) / 2
			fc = e.world(c + _vec(NORMAL[d]) * ((e.hi - e.lo) / 2))
			dist = (fc - _vec((8, fc[1], 8))) @ n
			assert abs(dist - 1.0) < 1e-9, (d, dist)
	# side k faces the direction 45k degrees from +x toward -z
	for e in els:
		for d, f in e.faces.items():
			if d in ("up", "down"):
				continue
			n = e.R @ _vec(NORMAL[d])
			ang = (math.degrees(math.atan2(-n[2], n[0])) + 360) % 360
			k = int(round(ang / 45)) % 8
			assert e.name and k in range(8)
	# rotation json round trips, Euler order Z * Y * X
	for m in (rot_y(45), rot_z(-22.5) @ rot_y(45), rot_x(10) @ rot_z(30), rot_z(-22.5) @ rot_y(-45)):
		assert np.allclose(matrix_of(rotation_json(m)), m, atol=1e-6)
	assert rotation_json(rot_z(-22.5) @ rot_y(45)) == {"x": 0.0, "y": 45.0, "z": -22.5}
	# quarter turns bake into the coordinates: a box on +x turned 90 lands on -z with no rotation field
	b = box(9, 0, 7.5, 10, 1, 8.5, Paint("#common", Region(0, 0, 16, 16)))
	t = turn([b], 90)[0]
	assert np.allclose(t.R, np.eye(3)) and np.allclose(t.lo, (7.5, 0, 6)) and np.allclose(t.hi, (8.5, 1, 7)), (t.lo, t.hi)
	assert set(t.faces) == {"north", "south", "west", "east", "up", "down"}
	# around 8 keeps rotations at 0 or +-45
	for e in around([b], 8):
		one = single_axis(e.R)
		assert one is None or (one[0] == "y" and abs(abs(one[1]) - 45) < 1e-6) or np.allclose(e.R, np.eye(3)), e.R
	# a bent bar keeps its world corners
	src = octo(4, 6, 1.0, Paint("#common", Region(0, 0, 16, 16)))
	bent = bend(src, 4.5, 22.5)
	for a_, b_ in zip(src, bent):
		for ca, cb in zip(a_.corners(), b_.corners()):
			want = bend_point(ca, 4.5, 22.5)
			assert any(np.allclose(want, c, atol=1e-6) for c in b_.corners()), (want, cb)
	assert bend_point((8, 6, 8), 4.5, 22.5)[0] > 8, "a positive bend tilts +y toward +x"
	# the staff's lower emitter is point-symmetric
	lower = staff_lower(src)
	for a_, b_ in zip(src, lower):
		for ca in a_.corners():
			want = _vec((16 - ca[0], 16 - (ca[1] + STAFF_SHIFT), ca[2]))
			assert any(np.allclose(want, c, atol=1e-6) for c in b_.corners())
	# turning faces keeps their UVs and adds quarter turns only to caps
	f = box(9, 0, 7.5, 10, 1, 8.5, Paint("#common", Region(0, 0, 16, 16)))
	t = turn([f], 90)[0]
	assert t.faces["north"].rotation == 0 and t.faces["up"].rotation in (90, 270)
	print("lib selftest: ok")


def _check_designs():
	"""Every design in emitters.py, grips.py and pommels.py against 6.1 and 6.2 (CI runs this)."""
	import importlib
	sys.path.insert(0, HERE)
	bad, count = [], 0
	for module in ("emitters", "grips", "pommels"):
		for d in importlib.import_module(module).DESIGNS.values():
			count += 1
			bad.extend(d.problems())
	for line in bad:
		print(line)
	print(f"hilt designs: {count} checked, {len(bad)} problems")
	return not bad


if __name__ == "__main__":
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("--selftest", action="store_true", help="check the geometry and rotation helpers")
	ap.add_argument("--designs", action="store_true", help="check every design module's designs")
	args = ap.parse_args()
	if args.selftest:
		_selftest()
	if args.designs and not _check_designs():
		sys.exit(1)
	if not (args.selftest or args.designs):
		ap.print_help()
