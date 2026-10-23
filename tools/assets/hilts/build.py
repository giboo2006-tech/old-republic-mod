#!/usr/bin/env python3
"""Builds the lightsaber hilts' game files from the hilt designs (docs/MILESTONE_7.md 10): every part model, the
designs' own textures, the display parents, the short lit blades the inventory shows, the part items' framed models
and flat icons, and the item definitions of the sabers and of every hilt part. HC owns this file.

  python3 tools/assets/hilts/build.py                    every pass, into the assets folder
  python3 tools/assets/hilts/build.py --only items       one pass: emitters, grips, pommels or items (repeat it, or
                                                         separate passes with commas, for several)
  python3 tools/assets/hilts/build.py --out DIR          a dry run: the same files under DIR, laid out like the
                                                         assets folder (with --prune it only lists what it would delete)
  python3 tools/assets/hilts/build.py --flat-icons       the inventory keeps flat icons: the sabers today's
                                                         (gen_textures_saber.py's), the parts the ones rendered here
  python3 tools/assets/hilts/build.py --prune            also deletes the files of each pass's folders that the layout
                                                         no longer uses (the old guard and staff_guard folders, ...)
  python3 tools/assets/hilts/build.py --strict           fails on a HiltParts id without a design, or on a design's
                                                         problems (lib.Design.problems)

The passes and their files (paths under src/main/resources/assets/oldrepublic/; a slot's files belong to its designer,
tools/ownership_m7.json):

  emitters  models/item/lightsaber/emitter_<id>.json             the emitter on a saber (parent lightsaber/base)
            models/item/lightsaber/staff/emitter_<id>_upper.json, _lower.json   at the staff's two ends (parent
                                                                 lightsaber/staff_base; lib.staff_upper, staff_lower)
            textures/item/hilt/emitter_<id>.png                  the design's own sheet, when it uses one
  grips     models/item/lightsaber/grip_<id>.json, models/item/lightsaber/staff/grip_staff.json (the staff grip),
            textures/item/hilt/grip_<id>.png
  pommels   models/item/lightsaber/pommel_<id>.json, textures/item/hilt/pommel_<id>.png
  items     models/item/lightsaber/base.json, staff_base.json    the display parents: hand, inventory, frame, ground
            models/item/lightsaber/blade/<emitter>.json, blade/staff_<emitter>_upper.json, _lower.json
                                                                 the short lit blade of the inventory icon
            textures/item/lightsaber_blade_stub.png              its texture
            models/item/<part>.json                              a part item's model: its slot model framed for the
                                                                 inventory (with --flat-icons its flat icon)
            textures/item/<part>.png, <part>_detail.png          the flat icon, rendered from the design: the metal
                                                                 (greyscale, tinted by the material) and the rest
            items/lightsaber.json, lightsaber_staff.json, training_saber.json, training_saber_red.json, and
            items/<part>.json for every part of HiltParts

The item definitions select each part's model by the saber's configuration (oldrepublic:hilt_part) and tint every hilt
model's metal faces with the part's material (oldrepublic:hilt_material); each emitter carries its own blade
(oldrepublic:saber_blade with that emitter's mouth, tilt and quillons, 10.4). A HiltParts id with no design yet is left
out of the selects (they fall back to the slot's first design) and its part item keeps the flat model already in the
tree; --strict refuses that. Every JSON file written is checked against the item definition and model formats this
repository uses (check_definition, check_model) before anything is written.

Run the slot passes before the items pass (a plain run does): the items pass frames each part's model and renders its
icon from the designs, and its --prune drops an old display parent (guard_base, staff_guard_base) or texture
(lightsaber_hilt, lightsaber_hilt_legendary) only once no model left in the tree names it, so `--only items --prune` on
its own keeps them while the old guard folders, which the slot passes' --prune deletes, still use them.
"""
import argparse
import importlib
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import lib  # noqa: E402
from lib import NS, STAFF_CENTRE, rot_x, rot_y, rot_z  # noqa: E402

HILT_PARTS_JAVA = os.path.join(lib.ROOT, "src", "main", "java", "com", "oldrepublic", "saber", "HiltParts.java")
SLOTS = ("emitter", "grip", "pommel")
MODULES = {"emitter": "emitters", "grip": "grips", "pommel": "pommels"}
PASSES = ("emitters", "grips", "pommels", "items")
PASS_SLOT = {"emitters": "emitter", "grips": "grip", "pommels": "pommel"}
SABERS = {"lightsaber": "single", "training_saber": "training", "training_saber_red": "training"}

BASE = f"{NS}:item/lightsaber/base"
STAFF_BASE = f"{NS}:item/lightsaber/staff_base"
COMMON = f"{NS}:item/hilt/common"
STUB_TEXTURE = f"{NS}:item/lightsaber_blade_stub"
# Textures of the hilt models before Milestone 7 (gen_textures_saber.py no longer writes them); --prune drops each
# once no model in the tree names it.
OLD_TEXTURES = ("lightsaber_hilt", "lightsaber_hilt_legendary")
# Display parents before Milestone 7: the guard hold, the same as the idle hold since October 2026 (10.1).
OLD_BASES = ("guard_base", "staff_guard_base")

# ------------------------------------------------------------------ display transforms
#
# A display entry turns, scales and moves the model about its centre (8, 8, 8): the game draws a model point p at
# T + R s (p - 8) sixteenths of a block (ItemTransform.apply: translate, Quaternionf.rotationXYZ, scale; then the item
# renderer's -0.5 offset). The GUI shows that space from +z with +x right and +y up, the 16-pixel slot spanning -8 to 8;
# an item frame shows it from -z (vanilla's flat items turn 180 degrees about y for "fixed").

# Third person: the fist holds the grip's centre (y 1.8, this far below the model centre) with the blade tilted up from
# straight forward (the hold tuned in October 2026, kept exactly; gen_textures_saber.py explains the numbers).
GRIP_BELOW_CENTRE = 6.2
THIRD_PERSON_FIST = (0.0, -3.8, 0.9)
THIRD_PERSON_TILT = 22.0
THIRD_PERSON_SCALE = 0.68
# First person: the identity; SaberFirstPerson poses and swings the saber in code.
IDENTITY = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
# The staff lies crosswise in the fist (Z 90), tilted 30 degrees about the fist's forward axis; its grip's centre is the
# model centre. 36 units x 0.62 is about 1.4 blocks, hilt and both blades.
STAFF_THIRD_PERSON_ROTATION = [0, -30, 90]
STAFF_THIRD_PERSON_SCALE = 0.62

# The inventory view of every hilt model: the thumb side (+x) toward the viewer, turned GUI_YAW degrees about the hilt's
# axis (negative shows the -z side too, so the Penitent's bend and the Warden's quillons read), tipped GUI_PITCH degrees
# so the emitter's mouth shows, then laid diagonal by GUI_ROLL (the pommel lower left, the blade upper right).
GUI_YAW = -40.0
GUI_PITCH = 12.0
GUI_ROLL = -45.0
# A saber's hilt in the slot: this many pixels per model unit, the axis point at y SABER_GUI_CENTRE_Y on the slot's
# centre (a little below the middle of a dark hilt, so the lit blade's stub fits in the upper corner).
SABER_GUI_SCALE = 1.45
SABER_GUI_CENTRE_Y = 3.0
STAFF_GUI_SCALE = 1.1
# The lit stub: a saber's is STUB_LENGTH long and a staff's STAFF_STUB_LENGTH, each shortened (to no less than its
# minimum) until it stays within GUI_MARGIN pixels of the slot's centre; the Warden's quillon stubs are
# QUILLON_STUB_LENGTH long and a third as wide.
STUB_LENGTH = 4.2
STUB_MIN_LENGTH = 2.0
STAFF_STUB_LENGTH = 3.6
STAFF_STUB_MIN_LENGTH = 1.4
GUI_MARGIN = 7.5
QUILLON_STUB_LENGTH = 1.5
QUILLON_STUB_SCALE = 1.0 / 3.0
# The stub's cross-section (model units): a darker coloured edge, the blade colour, a white core, each a little nearer
# the viewer than the one around it, so the inventory's view shows all three and an item frame seen at an angle barely
# separates them. (half width, front, back, shorter at the tip by, texture column, tinted)
STUB_LAYERS = (
	(0.78, 0.36, -0.36, 0.0, 8, True),
	(0.50, 0.44, -0.30, 0.18, 0, True),
	(0.22, 0.52, -0.24, 0.42, 0, False),
)
# A loose part in the slot: upright (the staff grip diagonal), PART_GUI_FILL pixels across at most (its flat icon
# PART_ICON_FILL, leaving room for the outline), at most the largest display scale the game reads (4).
PART_GUI_FILL = 13.5
PART_ICON_FILL = 12.5
MAX_DISPLAY_SCALE = 4.0
# Whether a shelf shows the display space from the front (+z, like the GUI) or from behind (like an item frame). Vanilla's
# flat items define no on_shelf turn, so the front is assumed; check it in game (one constant flips it).
SHELF_FROM_FRONT = True
# A loose part held or dropped: its centre in the fist (third person), before the eye (first person; turned so the
# thumb side shows), on the ground (its longest side PART_GROUND["size"] units).
PART_THIRD_PERSON = {"at": (0.0, 3.0, 1.0), "scale": 1.0}
PART_FIRST_PERSON = {"at": (1.13, 3.2, 1.13), "scale": 1.5, "yaw": -30.0}
PART_GROUND = {"at": (0.0, 2.0, 0.0), "size": 6.0}
# The flat icons: 16 pixels, rendered at ICON_SUPERSAMPLE times that and averaged; a pixel shows when at least
# ICON_COVER of it is covered.
ICON_SIZE = 16
ICON_SUPERSAMPLE = 8
ICON_COVER = 0.42
# then sharpened a little (unsharp mask percent) and outlined in the darkest shade of what each outline pixel borders,
# as vanilla's icons are
ICON_SHARPEN = 70
ICON_OUTLINE_SHADE = 0.38


def r(v, places=4):
	v = round(float(v), places)
	return 0.0 if v == 0 else v


def euler_xyz(m):
	"""[x, y, z] degrees with rot_x(x) @ rot_y(y) @ rot_z(z) == m: a display entry's rotation (Quaternionf.rotationXYZ
	turns a vector about z first, then y, then x)."""
	sy = max(-1.0, min(1.0, m[0, 2]))
	y = math.asin(sy)
	if abs(math.cos(y)) > 1e-9:
		x = math.atan2(-m[1, 2], m[2, 2])
		z = math.atan2(-m[0, 1], m[0, 0])
	else:
		x = math.atan2(m[2, 1], m[1, 1])
		z = 0.0
	out = [math.degrees(v) for v in (x, y, z)]
	assert np.allclose(rot_x(out[0]) @ rot_y(out[1]) @ rot_z(out[2]), m, atol=1e-9)
	return out


def matrix_xyz(rotation):
	return rot_x(rotation[0]) @ rot_y(rotation[1]) @ rot_z(rotation[2])


def view(roll=GUI_ROLL):
	"""The inventory view's rotation: the thumb side toward the viewer, turned, tipped, then rolled."""
	return rot_z(roll) @ rot_x(GUI_PITCH) @ rot_y(-90.0 + GUI_YAW)


def placing(m, s, point, at=(0.0, 0.0, 0.0)):
	"""The display entry turning by m and scaling by s that puts model point `point` at `at` (sixteenths of a block),
	solved against the rounded angles the game will read."""
	rotation = [r(v, 3) for v in euler_xyz(m)]
	t = np.array(at, dtype=float) - s * (matrix_xyz(rotation) @ (np.array(point, dtype=float) - 8.0))
	return {"rotation": rotation, "translation": [r(v, 3) for v in t], "scale": [r(s, 4)] * 3}


def in_frame(e):
	"""The same picture in an item frame, which sees the display space from -z: turned 180 degrees about y."""
	flip = rot_y(180.0)
	rotation = [r(v, 3) for v in euler_xyz(flip @ matrix_xyz(e["rotation"]))]
	t = flip @ np.array(e["translation"], dtype=float)
	return {"rotation": rotation, "translation": [r(v, 3) for v in t], "scale": list(e["scale"])}


def on_shelf(e):
	return json.loads(json.dumps(e)) if SHELF_FROM_FRONT else in_frame(e)


def held_third_person(tilt, scale):
	"""Third person: the blade tilted `tilt` degrees up from straight forward, the grip's centre in the fist."""
	t = math.radians(tilt)
	below = GRIP_BELOW_CENTRE * scale
	grip = [0.0, -below * math.cos(t), -below * math.sin(t)]
	translation = [round(THIRD_PERSON_FIST[i] - grip[i], 2) for i in range(3)]
	return {"rotation": [tilt, 0, 0], "translation": translation, "scale": [scale, scale, scale]}


def gui_entries(m, s, point, at=(0.0, 0.0, 0.0)):
	gui = placing(m, s, point, at)
	return {"gui": gui, "fixed": in_frame(gui), "on_shelf": on_shelf(gui)}


def saber_base():
	"""lightsaber/base.json: the display of every single hilt's models (parts, blade stubs, the drawn blade)."""
	third = held_third_person(THIRD_PERSON_TILT, THIRD_PERSON_SCALE)
	display = {
		"thirdperson_righthand": dict(third),
		"thirdperson_lefthand": dict(third),
		"firstperson_righthand": dict(IDENTITY),
		"firstperson_lefthand": dict(IDENTITY),
		"ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
		"head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
	}
	display.update(gui_entries(view(), SABER_GUI_SCALE, (8.0, SABER_GUI_CENTRE_Y, 8.0)))
	return {"gui_light": "front", "textures": {"particle": COMMON}, "display": display}


def staff_base():
	"""lightsaber/staff_base.json: the saber's display with the staff's crosswise hold, ground and inventory view."""
	display = json.loads(json.dumps(saber_base()["display"]))
	s = STAFF_THIRD_PERSON_SCALE
	for hand in ("thirdperson_righthand", "thirdperson_lefthand"):
		display[hand] = {"rotation": list(STAFF_THIRD_PERSON_ROTATION), "translation": list(THIRD_PERSON_FIST), "scale": [s, s, s]}
	display["ground"] = {"rotation": [0, 0, 90], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}
	display.update(gui_entries(view(), STAFF_GUI_SCALE, STAFF_CENTRE))
	return {"gui_light": "front", "textures": {"particle": COMMON}, "display": display}


def part_frame(d, fill=PART_GUI_FILL):
	"""(rotation matrix, scale, model point at the slot's centre) of a loose part in the slot: upright (the staff grip
	diagonal), its projected box centred and at most `fill` pixels across."""
	pts = np.array([c for e in d.elements for c in e.corners()])
	centre = (pts.min(axis=0) + pts.max(axis=0)) / 2
	m = view(GUI_ROLL if d.slot == "staff_grip" else 0.0)
	proj = (m @ (pts - centre).T).T
	s = min(MAX_DISPLAY_SCALE, fill / max(np.ptp(proj[:, 0]), np.ptp(proj[:, 1])))
	mid = np.array(((proj[:, 0].max() + proj[:, 0].min()) / 2, (proj[:, 1].max() + proj[:, 1].min()) / 2, 0.0))
	# the model point that lands on the slot's centre
	return m, s, centre + m.T @ mid


def part_display(d):
	"""A loose part's display: framed in the slot, in a frame and on a shelf; held and dropped by its centre."""
	m, s, point = part_frame(d)
	display = gui_entries(m, s, point)
	pts = np.array([c for e in d.elements for c in e.corners()])
	lo, hi = pts.min(axis=0), pts.max(axis=0)
	centre = (lo + hi) / 2
	ground_scale = min(MAX_DISPLAY_SCALE, PART_GROUND["size"] / float(np.max(hi - lo)))
	display["ground"] = placing(np.eye(3), ground_scale, centre, PART_GROUND["at"])
	held = placing(np.eye(3), PART_THIRD_PERSON["scale"], centre, PART_THIRD_PERSON["at"])
	display["thirdperson_righthand"] = held
	display["thirdperson_lefthand"] = dict(held)
	eye = placing(rot_y(-90.0 + PART_FIRST_PERSON["yaw"]), PART_FIRST_PERSON["scale"], centre, PART_FIRST_PERSON["at"])
	display["firstperson_righthand"] = eye
	display["firstperson_lefthand"] = dict(eye)
	return display


# ------------------------------------------------------------------ the inventory's lit blade stub

def stub_texture():
	"""16x16: white on the left (the core, and under the tint the blade colour), a darker grey on the right (under the
	tint the blade colour's edge)."""
	img = Image.new("RGBA", (16, 16), (255, 255, 255, 255))
	for x in range(8, 16):
		for y in range(16):
			img.putpixel((x, y), (168, 168, 168, 255))
	return img


def viewer():
	"""The inventory viewer's direction in model space (the GUI looks from +z; an item frame sees the same side)."""
	v = view().T @ np.array((0.0, 0.0, 1.0))
	return v / np.linalg.norm(v)


def stub_segment(base, direction, length, scale=1.0):
	"""One stub blade from `base` along `direction`: three boxes (edge, colour, core) turned about the blade so their
	front faces face the inventory's viewer, each nearer the viewer than the one around it."""
	b = np.array(direction, dtype=float)
	b /= np.linalg.norm(b)
	v = viewer()
	n = v - b * (v @ b)
	n /= np.linalg.norm(n)
	R = np.column_stack((n, b, np.cross(n, b)))
	o = np.array(base, dtype=float)
	els = []
	for half, front, back, short, u0, tint in STUB_LAYERS:
		w, f, k = half * scale, front * scale, back * scale
		top = max(length - short * scale, 0.3)
		uv = [u0 + 2, 2, u0 + 6, 6]
		faces = {d: lib.Face("#stub", uv, tint) for d in ("up", "north", "south", "east", "west")}
		els.append(lib.Element(o + np.array((k, -0.2, -w)), o + np.array((f, top, w)), faces, R.copy(), o.copy(),
				shade=False, light=15, name="stub"))
	return els


def _direction(tilt):
	t = math.radians(tilt)
	return np.array((math.sin(t), math.cos(t), 0.0))


def gui_reach(elements, gui):
	"""How far from the slot's centre (pixels, either axis) the elements reach in the inventory view `gui`."""
	pts = np.array([c for e in elements for c in e.corners()]) - 8.0
	q = np.array(gui["translation"], dtype=float) + gui["scale"][0] * (matrix_xyz(gui["rotation"]) @ pts.T).T
	return float(np.abs(q[:, :2]).max())


def fitted(make, length, minimum, gui):
	"""The longest length from `length` down to `minimum` (in tenths) whose stub stays within GUI_MARGIN."""
	while length > minimum and gui_reach(make(length), gui) > GUI_MARGIN:
		length = round(length - 0.1, 4)
	return max(length, minimum)


def quillon_stubs(q, lower=False):
	"""The Warden's quillon stubs (8.1): from (8, y, 8 +- z), pointing +z and -z; on a staff's lower end at height
	16 - y, still pointing +-z (the half turn about the z axis that makes the lower end keeps z)."""
	y = 16.0 - q["y"] if lower else q["y"]
	els = []
	for side in (1.0, -1.0):
		els += stub_segment((8.0, y, 8.0 + side * q["z"]), (0.0, 0.0, side), QUILLON_STUB_LENGTH, QUILLON_STUB_SCALE)
	return els


def single_stub(blade, gui):
	"""The lit stub of a saber with this emitter's blade data: the main blade, tilted like it, and any quillons."""
	direction = _direction(blade.tilt)
	mouth = (blade.mouth[0], blade.mouth[1], 8.0)
	length = fitted(lambda n: stub_segment(mouth, direction, n), STUB_LENGTH, STUB_MIN_LENGTH, gui)
	els = stub_segment(mouth, direction, length)
	if blade.quillons:
		els += quillon_stubs(blade.quillons)
	return els


def staff_stubs(blade, gui):
	"""(upper, lower) lit stubs of a saberstaff: the lower blade leaves from (16 - x, 16 - y) the other way (10.4), its
	quillons at height 16 - y. Each is built where it sits (not turned), so its front faces face the viewer too."""
	up = blade.staff()
	direction = _direction(up.tilt)
	mouth = (up.mouth[0], up.mouth[1], 8.0)
	length = fitted(lambda n: stub_segment(mouth, direction, n), STAFF_STUB_LENGTH, STAFF_STUB_MIN_LENGTH, gui)
	upper = stub_segment(mouth, direction, length)
	lower = stub_segment((16.0 - mouth[0], 16.0 - mouth[1], 8.0), -direction, length)
	if up.quillons:
		upper += quillon_stubs(up.quillons)
		lower += quillon_stubs(up.quillons, lower=True)
	return upper, lower


def stub_model(parent, elements):
	return {"parent": parent, "textures": {"stub": STUB_TEXTURE, "particle": STUB_TEXTURE}, "elements": [e.json() for e in elements]}


# ------------------------------------------------------------------ flat icons

_COMMON = []


def _common_texture():
	if not _COMMON:
		import preview
		_COMMON.append(preview.common_texture())
	return _COMMON[0]


def flat_icon(d):
	"""(metal, detail): the design as a 16-pixel icon in the 3D icon's view, split into its tinted metal (greyscale;
	the item definition tints it with the part's material) and its untinted rest (black, brass, glass, buttons)."""
	import preview
	m, s, point = part_frame(d, PART_ICON_FILL)
	right, up, toward = m.T @ np.array((1.0, 0, 0)), m.T @ np.array((0, 1.0, 0)), m.T @ np.array((0, 0, 1.0))
	textures = {"#common": _common_texture(), "#part": d.sheet.img}
	k = ICON_SUPERSAMPLE * s
	half = ICON_SIZE / 2 / s

	def shot(tint):
		old = preview._camera
		preview._camera = lambda yaw, pitch: (right, up, -toward)
		try:
			img = preview.render([preview.Group(d.elements, textures, tint)], (0, 0), (point, half, half), scale=k, ss=1)
		finally:
			preview._camera = old
		return np.asarray(img, dtype=np.float32)

	white = shot((255, 255, 255))
	black = shot((0, 0, 0))
	n = ICON_SIZE
	f = white.shape[0] // n
	alpha = white[..., 3] / 255.0
	tinted = (np.abs(white[..., :3] - black[..., :3]).max(axis=2) > 8) & (alpha > 0)
	rgb = np.zeros((n, n, 3), dtype=np.float32)
	kind = np.zeros((n, n), dtype=np.int8)          # 0 empty, 1 metal (tinted), 2 detail
	for y in range(n):
		for x in range(n):
			a = alpha[y * f:(y + 1) * f, x * f:(x + 1) * f]
			if a.mean() < ICON_COVER:
				continue
			rgb[y, x] = (white[y * f:(y + 1) * f, x * f:(x + 1) * f, :3] * a[..., None]).sum(axis=(0, 1)) / a.sum()
			metal = tinted[y * f:(y + 1) * f, x * f:(x + 1) * f].sum() >= 0.5 * (a > 0).sum()
			kind[y, x] = 1 if metal else 2
	# sharpen the averaged pixels (the empty ones first take their neighbours' colour, so the edge keeps no dark halo)
	filled = rgb.copy()
	for _ in range(2):
		grown = filled.copy()
		for y, x in zip(*np.nonzero(kind == 0)):
			near = [filled[j, i] for j, i in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
					if 0 <= j < n and 0 <= i < n and filled[j, i].any()]
			if near:
				grown[y, x] = np.mean(near, axis=0)
		filled = grown
	sharp = np.asarray(Image.fromarray(np.clip(filled, 0, 255).astype(np.uint8), "RGB")
			.filter(ImageFilter.UnsharpMask(radius=1.0, percent=ICON_SHARPEN, threshold=0)), dtype=np.float32)
	rgb = np.where(kind[..., None] > 0, sharp, 0.0)
	# the outline: each empty pixel beside the shape takes the darkest shade of its neighbour (metal before detail)
	outline = np.zeros_like(kind)
	for y, x in zip(*np.nonzero(kind == 0)):
		near = [(kind[j, i], rgb[j, i]) for j, i in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
				if 0 <= j < n and 0 <= i < n and kind[j, i] > 0]
		if near:
			near.sort(key=lambda t: t[0])
			outline[y, x] = near[0][0]
			rgb[y, x] = near[0][1] * ICON_OUTLINE_SHADE
	kind = np.where(outline > 0, outline, kind)
	metal = np.zeros((n, n, 4), dtype=np.uint8)
	detail = np.zeros((n, n, 4), dtype=np.uint8)
	grey = np.clip(np.round(rgb @ np.array((0.299, 0.587, 0.114))), 0, 255).astype(np.uint8)
	metal[kind == 1] = np.stack([grey, grey, grey, np.full_like(grey, 255)], axis=-1)[kind == 1]
	detail[kind == 2] = np.concatenate([np.clip(np.round(rgb), 0, 255).astype(np.uint8), np.full((n, n, 1), 255, np.uint8)], axis=-1)[kind == 2]
	return Image.fromarray(metal, "RGBA"), Image.fromarray(detail, "RGBA")


# ------------------------------------------------------------------ item definitions

def model(ref, tints=None):
	out = {"type": "minecraft:model", "model": ref}
	if tints:
		out["tints"] = tints
	return out


def material(part):
	return [{"type": f"{NS}:hilt_material", "part": part}]


BLADE_COLOR = [{"type": f"{NS}:blade_color"}]
EMPTY = {"type": "minecraft:empty"}
GUI_CONTEXTS = ["gui", "fixed", "on_shelf"]
FIRST_PERSON = ["firstperson_righthand", "firstperson_lefthand"]


def composite(*models):
	return {"type": "minecraft:composite", "models": list(models)}


def select(part, designs, make):
	"""An oldrepublic:hilt_part select of one case per design (HiltParts order), falling back to the first design."""
	return {
		"type": "minecraft:select",
		"property": f"{NS}:hilt_part",
		"part": part,
		"cases": [{"when": d.id, "model": make(d)} for d in designs],
		"fallback": make(designs[0]),
	}


def when_lit(m, second=False):
	"""m while the blade is more than half out (with second, the staff's lower blade)."""
	out = {"type": "minecraft:range_dispatch", "property": f"{NS}:blade_extension"}
	if second:
		out["second"] = True
	out["entries"] = [{"threshold": 0.5, "model": m}]
	out["fallback"] = dict(EMPTY)
	return out


def blade(kind, blade_json, base):
	"""The drawn blade (SaberBladeRenderer, oldrepublic:saber_blade) of one emitter, told when it is in the first-person
	hand (its motion trail is then kept relative to the view)."""
	def special(first_person):
		m = {"type": f"{NS}:saber_blade", "kind": kind}
		if first_person:
			m["first_person"] = True
		m.update(blade_json)
		return {"type": "minecraft:special", "base": base, "model": m}
	return {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": FIRST_PERSON, "model": special(True)}],
		"fallback": special(False),
	}


def by_context(gui, hand):
	"""The inventory, frame and shelf model, and in every other place the held model, empty while the saber flies."""
	return {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": list(GUI_CONTEXTS), "model": gui}],
		"fallback": {
			"type": "minecraft:condition",
			"property": "minecraft:has_component",
			"component": f"{NS}:saber_thrown",
			"on_true": dict(EMPTY),
			"on_false": hand,
		},
	}}


def flat_gui(icon):
	"""Today's flat icon: the hilt, and with the blade out the blade layer tinted by the blade colour."""
	return {
		"type": "minecraft:range_dispatch",
		"property": f"{NS}:blade_extension",
		"entries": [{"threshold": 0.5, "model": model(f"{NS}:item/{icon}_lit",
				[{"type": "minecraft:constant", "value": -1}, {"type": f"{NS}:blade_color"}])}],
		"fallback": model(f"{NS}:item/{icon}"),
	}


def saber_definition(kind, ds, flat):
	"""items/lightsaber.json and the training sabers' (kind "single" or "training")."""
	def hilt(slot):
		return select(slot, ds[slot], lambda d: model(f"{NS}:item/lightsaber/{d.item_id}", material(slot)))
	hand = composite(hilt("pommel"), hilt("grip"), select("emitter", ds["emitter"], lambda d: composite(
			model(f"{NS}:item/lightsaber/{d.item_id}", material("emitter")),
			blade(kind, d.blade.json(), BASE))))
	if flat:
		gui = flat_gui("lightsaber_icon" if kind == "single" else "training_saber_icon")
	else:
		gui = composite(hilt("pommel"), hilt("grip"), hilt("emitter"),
				when_lit(select("emitter", ds["emitter"], lambda d: model(f"{NS}:item/lightsaber/blade/{d.id}", BLADE_COLOR))))
	return by_context(gui, hand)


def staff_definition(ds, flat):
	"""items/lightsaber_staff.json: the staff grip and an emitter at each end; no pommel (the config keeps a placeholder
	pommel the staff never shows)."""
	grip = model(f"{NS}:item/lightsaber/staff/grip_staff", material("grip"))

	def ends(d):
		return [model(f"{NS}:item/lightsaber/staff/{d.item_id}_upper", material("emitter")),
				model(f"{NS}:item/lightsaber/staff/{d.item_id}_lower", material("emitter"))]
	hand = composite(grip, select("emitter", ds["emitter"], lambda d: composite(*ends(d), blade("staff", d.blade.staff().json(), STAFF_BASE))))
	if flat:
		gui = flat_gui("lightsaber_staff_icon")
	else:
		gui = composite(grip, select("emitter", ds["emitter"], lambda d: composite(*ends(d))),
				when_lit(select("emitter", ds["emitter"], lambda d: model(f"{NS}:item/lightsaber/blade/staff_{d.id}_upper", BLADE_COLOR))),
				when_lit(select("emitter", ds["emitter"], lambda d: model(f"{NS}:item/lightsaber/blade/staff_{d.id}_lower", BLADE_COLOR)), second=True))
	return by_context(gui, hand)


def part_definition(d, flat):
	part = "grip" if d.slot == "staff_grip" else d.slot
	tints = material(part)
	if flat:
		tints = tints + [{"type": "minecraft:constant", "value": -1}]
	return {"model": model(f"{NS}:item/{d.item_id}", tints)}


# ------------------------------------------------------------------ format checks

ITEM_MODEL_TYPES = {"minecraft:model", "minecraft:select", "minecraft:range_dispatch", "minecraft:condition",
		"minecraft:composite", "minecraft:special", "minecraft:empty"}
DISPLAY_CONTEXTS = {"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand",
		"head", "gui", "ground", "fixed", "on_shelf"}
FACE_DIRS = set(lib.DIRS)


class FormatError(Exception):
	pass


def _need(cond, where, what):
	if not cond:
		raise FormatError(f"{where}: {what}")


def _nums(v, n):
	return isinstance(v, list) and len(v) == n and all(isinstance(x, (int, float)) and not isinstance(x, bool) for x in v)


def check_definition(node, where):
	"""An item definition's model tree, against the forms this repository's item definitions use."""
	_need(isinstance(node, dict), where, "a model must be an object")
	kind = node.get("type")
	_need(kind in ITEM_MODEL_TYPES, where, f"unknown model type {kind}")
	if kind == "minecraft:model":
		_need(isinstance(node.get("model"), str) and ":" in node["model"], where, "minecraft:model needs a model id")
		for t in node.get("tints", []):
			_need(isinstance(t, dict) and isinstance(t.get("type"), str), where, "a tint needs a type")
			if t["type"] == f"{NS}:hilt_material":
				_need(t.get("part") in SLOTS, where, f"hilt_material part {t.get('part')}")
			if t["type"] == "minecraft:constant":
				_need(isinstance(t.get("value"), int), where, "a constant tint needs an int value")
	elif kind == "minecraft:select":
		_need(isinstance(node.get("property"), str), where, "a select needs a property")
		if node["property"] == f"{NS}:hilt_part":
			_need(node.get("part") in SLOTS, where, f"hilt_part part {node.get('part')}")
		if node["property"] == "minecraft:display_context":
			for c in node.get("cases", []):
				for w in (c["when"] if isinstance(c["when"], list) else [c["when"]]):
					_need(w in DISPLAY_CONTEXTS, where, f"unknown display context {w}")
		cases = node.get("cases")
		_need(isinstance(cases, list) and cases, where, "a select needs cases")
		seen = set()
		for i, c in enumerate(cases):
			_need(isinstance(c, dict) and "when" in c and "model" in c, where, f"case {i} needs when and model")
			whens = c["when"] if isinstance(c["when"], list) else [c["when"]]
			_need(all(isinstance(w, str) for w in whens) and whens, where, f"case {i}: when must be strings")
			_need(not (seen & set(whens)), where, f"case {i}: {sorted(seen & set(whens))} listed twice")
			seen |= set(whens)
			check_definition(c["model"], f"{where}/case {i}")
		if "fallback" in node:
			check_definition(node["fallback"], f"{where}/fallback")
	elif kind == "minecraft:range_dispatch":
		_need(isinstance(node.get("property"), str), where, "a range dispatch needs a property")
		entries = node.get("entries")
		_need(isinstance(entries, list) and entries, where, "a range dispatch needs entries")
		for i, e in enumerate(entries):
			_need(isinstance(e.get("threshold"), (int, float)), where, f"entry {i} needs a threshold")
			check_definition(e.get("model"), f"{where}/entry {i}")
		if "fallback" in node:
			check_definition(node["fallback"], f"{where}/fallback")
	elif kind == "minecraft:condition":
		_need(isinstance(node.get("property"), str), where, "a condition needs a property")
		if node["property"] == "minecraft:has_component":
			_need(isinstance(node.get("component"), str), where, "has_component needs a component")
		check_definition(node.get("on_true"), f"{where}/on_true")
		check_definition(node.get("on_false"), f"{where}/on_false")
	elif kind == "minecraft:composite":
		_need(isinstance(node.get("models"), list) and node["models"], where, "a composite needs models")
		for i, m in enumerate(node["models"]):
			check_definition(m, f"{where}/{i}")
	elif kind == "minecraft:special":
		_need(isinstance(node.get("base"), str), where, "a special model needs a base")
		m = node.get("model")
		_need(isinstance(m, dict) and isinstance(m.get("type"), str), where, "a special model needs a model type")
		if m["type"] == f"{NS}:saber_blade":
			_need(m.get("kind") in ("single", "staff", "training"), where, f"saber_blade kind {m.get('kind')}")
			_need(set(m) <= {"type", "kind", "first_person", "mouth", "tilt", "quillons"}, where, f"saber_blade fields {sorted(m)}")
			if "mouth" in m:
				_need(_nums(m["mouth"], 2), where, "mouth must be [x, y]")
			if "quillons" in m:
				q = m["quillons"]
				_need(isinstance(q, dict) and set(q) <= {"y", "z", "length"} and {"y", "z"} <= set(q)
						and all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in q.values()), where, f"quillons {q}")


def check_model(data, where, textures=None):
	"""A block-model JSON (a hilt part, a stub, a display parent, a framed part)."""
	_need(isinstance(data, dict), where, "a model must be an object")
	if "gui_light" in data:
		_need(data["gui_light"] in ("front", "side"), where, f"gui_light {data['gui_light']}")
	if "parent" in data:
		_need(isinstance(data["parent"], str), where, "parent must be an id")
	tex = dict(textures or {})
	tex.update(data.get("textures", {}))
	for k, v in data.get("display", {}).items():
		_need(k in DISPLAY_CONTEXTS, where, f"unknown display context {k}")
		for f in ("rotation", "translation", "scale"):
			if f in v:
				_need(_nums(v[f], 3), where, f"display {k} {f} must be three numbers")
		_need(all(-80 <= t <= 80 for t in v.get("translation", [0])), where, f"display {k} translation beyond 80 (the game clamps it)")
		_need(all(-4 <= s <= 4 for s in v.get("scale", [1])), where, f"display {k} scale beyond 4 (the game clamps it)")
	for i, el in enumerate(data.get("elements", [])):
		at = f"{where} element {i}"
		_need(_nums(el.get("from"), 3) and _nums(el.get("to"), 3), at, "from and to must be three numbers")
		for c in el["from"] + el["to"]:
			_need(lib.MODEL_MIN <= c <= lib.MODEL_MAX, at, f"corner {c} outside {lib.MODEL_MIN}..{lib.MODEL_MAX}")
		rot = el.get("rotation")
		if rot is not None:
			_need(_nums(rot.get("origin"), 3), at, "rotation needs an origin")
			_need(("axis" in rot and "angle" in rot) or ({"x", "y", "z"} & set(rot)), at, "rotation needs axis and angle, or x, y, z")
		if "light_emission" in el:
			_need(isinstance(el["light_emission"], int) and 0 <= el["light_emission"] <= 15, at, "light_emission 0..15")
		_need(el.get("faces"), at, "no faces")
		for d, face in el["faces"].items():
			_need(d in FACE_DIRS, at, f"face {d}")
			_need(_nums(face.get("uv"), 4), at, f"face {d} uv")
			t = face.get("texture", "")
			_need(t.startswith("#") and t[1:] in tex, at, f"face {d} texture {t} is not one of {sorted(tex)}")
			if "tintindex" in face:
				_need(face["tintindex"] == 0, at, "tintindex must be 0 (each model has one tint)")


# ------------------------------------------------------------------ files

def dumps(obj, level=0):
	"""JSON with tabs, short lists of numbers or strings on one line."""
	pad, inner = "\t" * level, "\t" * (level + 1)
	if isinstance(obj, dict):
		if not obj:
			return "{}"
		return "{\n" + ",\n".join(f"{inner}{json.dumps(k)}: {dumps(v, level + 1)}" for k, v in obj.items()) + "\n" + pad + "}"
	if isinstance(obj, list):
		if not obj:
			return "[]"
		if all(isinstance(v, (int, float, str, bool)) for v in obj) and len(obj) <= 8:
			return "[" + ", ".join(json.dumps(v) for v in obj) + "]"
		return "[\n" + ",\n".join(inner + dumps(v, level + 1) for v in obj) + "\n" + pad + "]"
	return json.dumps(obj)


class Out:
	"""Where files go: the assets folder, or a dry run's folder. Remembers what it wrote."""

	def __init__(self, root, dry):
		self.root = root
		self.dry = dry
		self.written = set()
		self.deleted = []

	def path(self, rel):
		return os.path.join(self.root, rel)

	def json(self, rel, data):
		text = dumps(data) + "\n"
		path = self.path(rel)
		os.makedirs(os.path.dirname(path), exist_ok=True)
		with open(path, "w", encoding="utf-8") as f:
			f.write(text)
		self.written.add(rel)

	def png(self, rel, img):
		path = self.path(rel)
		os.makedirs(os.path.dirname(path), exist_ok=True)
		img.save(path)
		self.written.add(rel)

	def delete(self, rel):
		"""Deletes rel from the assets folder (a dry run only reports it)."""
		self.deleted.append(rel)
		if not self.dry:
			os.remove(os.path.join(lib.ASSETS, rel))


# ------------------------------------------------------------------ designs and HiltParts

def hilt_parts():
	"""{"emitter": [...], "grip": [...], "pommel": [...]}: the variant ids of HiltParts.java, in order."""
	with open(HILT_PARTS_JAVA, encoding="utf-8") as f:
		src = f.read()
	src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
	src = re.sub(r"//[^\n]*", "", src)
	out = {}
	for slot, enum in (("emitter", "Emitter"), ("grip", "Grip"), ("pommel", "Pommel")):
		m = re.search(r"enum\s+" + enum + r"\s+implements\s+Part\s*\{(.*?);", src, flags=re.S)
		if not m:
			raise SystemExit(f"build.py: no enum {enum} in {HILT_PARTS_JAVA}")
		out[slot] = [c.strip().lower() for c in m.group(1).split(",") if c.strip()]
	return out


def load_designs(strict):
	"""Every design of the three modules, checked against HiltParts: {"emitter": [...], "grip": [...] (single grips),
	"pommel": [...], "staff_grip": the staff grip or None}, each list in HiltParts order. Returns (designs, missing)."""
	ids = hilt_parts()
	ds = {"emitter": [], "grip": [], "pommel": [], "staff_grip": None}
	missing = []
	problems = []
	for slot in SLOTS:
		module = importlib.import_module(MODULES[slot])
		found = dict(module.DESIGNS)
		for key, d in found.items():
			if key != d.id:
				raise SystemExit(f"build.py: {MODULES[slot]}.DESIGNS files {d.id} under {key}")
			if d.id not in ids[slot]:
				raise SystemExit(f"build.py: {MODULES[slot]}.py has {d.id}, which HiltParts.{slot.capitalize()} does not")
			want = "staff_grip" if (slot == "grip" and d.id == "staff") else slot
			if d.slot != want:
				raise SystemExit(f"build.py: {MODULES[slot]}.py's {d.id} is a {d.slot} design, not a {want}")
			problems += d.problems()
		for pid in ids[slot]:
			d = found.get(pid)
			if d is None:
				missing.append(f"{slot}_{pid}")
			elif d.slot == "staff_grip":
				ds["staff_grip"] = d
			else:
				ds[slot].append(d)
	for slot in SLOTS:
		if not ds[slot]:
			raise SystemExit(f"build.py: no {slot} design at all")
	if problems:
		print("build.py: design problems (lib.Design.problems):\n  " + "\n  ".join(problems), file=sys.stderr)
	if strict and (missing or problems):
		raise SystemExit(f"build.py --strict: {len(missing)} part(s) without a design ({', '.join(missing) or 'none'}), "
				f"{len(problems)} design problem(s)")
	return ds, missing


# ------------------------------------------------------------------ passes

def slot_files(slot, ds):
	"""{relative path: data} of one slot's pass (JSON as dicts, textures as images)."""
	files = {}
	designs = list(ds[slot])
	if slot == "grip" and ds["staff_grip"] is not None:
		designs.append(ds["staff_grip"])
	for d in designs:
		if d.slot == "staff_grip":
			files[f"models/item/lightsaber/staff/{d.item_id}.json"] = d.model(STAFF_BASE)
		else:
			files[f"models/item/lightsaber/{d.item_id}.json"] = d.model(BASE)
		if d.slot == "emitter":
			files[f"models/item/lightsaber/staff/{d.item_id}_upper.json"] = d.model(STAFF_BASE, lib.staff_upper(d.elements))
			files[f"models/item/lightsaber/staff/{d.item_id}_lower.json"] = d.model(STAFF_BASE, lib.staff_lower(d.elements))
		if d.uses_sheet():
			files[f"textures/item/hilt/{d.item_id}.png"] = d.sheet.img
	return files


def item_files(ds, missing, flat):
	files = {
		"textures/item/lightsaber_blade_stub.png": stub_texture(),
		"models/item/lightsaber/base.json": saber_base(),
		"models/item/lightsaber/staff_base.json": staff_base(),
	}
	saber_gui = files["models/item/lightsaber/base.json"]["display"]["gui"]
	staff_gui = files["models/item/lightsaber/staff_base.json"]["display"]["gui"]
	for d in ds["emitter"]:
		files[f"models/item/lightsaber/blade/{d.id}.json"] = stub_model(BASE, single_stub(d.blade, saber_gui))
		upper, lower = staff_stubs(d.blade, staff_gui)
		files[f"models/item/lightsaber/blade/staff_{d.id}_upper.json"] = stub_model(STAFF_BASE, upper)
		files[f"models/item/lightsaber/blade/staff_{d.id}_lower.json"] = stub_model(STAFF_BASE, lower)
	parts = ds["emitter"] + ds["grip"] + ([ds["staff_grip"]] if ds["staff_grip"] else []) + ds["pommel"]
	for d in parts:
		metal, detail = flat_icon(d)
		files[f"textures/item/{d.item_id}.png"] = metal
		files[f"textures/item/{d.item_id}_detail.png"] = detail
		if flat:
			files[f"models/item/{d.item_id}.json"] = {"parent": "minecraft:item/generated", "textures": {
					"layer0": f"{NS}:item/{d.item_id}", "layer1": f"{NS}:item/{d.item_id}_detail"}}
		else:
			parent = f"{NS}:item/lightsaber/staff/{d.item_id}" if d.slot == "staff_grip" else f"{NS}:item/lightsaber/{d.item_id}"
			files[f"models/item/{d.item_id}.json"] = {"parent": parent, "display": part_display(d)}
		files[f"items/{d.item_id}.json"] = part_definition(d, flat)
	for item_id in missing:
		# no design yet: the flat model already in the tree (Phase 0's placeholder), untinted
		files[f"items/{item_id}.json"] = {"model": model(f"{NS}:item/{item_id}")}
	for item, kind in SABERS.items():
		files[f"items/{item}.json"] = saber_definition(kind, ds, flat)
	files["items/lightsaber_staff.json"] = staff_definition(ds, flat)
	return files


def check_files(files):
	"""Checks every JSON of a pass before anything is written; models see their parents' textures."""
	for rel, data in files.items():
		if not rel.endswith(".json"):
			continue
		if rel.startswith("items/"):
			_need(set(data) <= {"model", "hand_animation_on_swap", "oversized_in_gui"}, rel, f"fields {sorted(data)}")
			check_definition(data["model"], rel)
		else:
			check_model(data, rel, {"particle": COMMON} if data.get("parent", "").startswith(f"{NS}:item/lightsaber/") else None)


def stale_slot_files(slot, written):
	"""A slot's files the pass did not write: models under models/item/lightsaber/ (the guard and staff_guard folders)
	and textures under textures/item/hilt/."""
	out = []
	prefix = slot + "_"
	for folder, ext in (("models/item/lightsaber", ".json"), ("textures/item/hilt", ".png")):
		top = os.path.join(lib.ASSETS, folder)
		for dirpath, _, names in os.walk(top):
			for name in names:
				if name.startswith(prefix) and name.endswith(ext):
					rel = os.path.relpath(os.path.join(dirpath, name), lib.ASSETS).replace(os.sep, "/")
					if rel not in written:
						out.append(rel)
	return sorted(out)


def model_refs(out):
	"""What each model JSON names in the tree as this run leaves it (a file the run wrote as written, a dry run's copy
	included; a file it deleted left out): {path: (texture ids, parent id or None)}."""
	rels = set()
	for dirpath, _, names in os.walk(os.path.join(lib.ASSETS, "models")):
		for name in names:
			if name.endswith(".json"):
				rels.add(os.path.relpath(os.path.join(dirpath, name), lib.ASSETS).replace(os.sep, "/"))
	rels |= {rel for rel in out.written if rel.startswith("models/") and rel.endswith(".json")}
	refs = {}
	for rel in sorted(rels - set(out.deleted)):
		path = out.path(rel) if rel in out.written else os.path.join(lib.ASSETS, rel)
		with open(path, encoding="utf-8") as f:
			try:
				data = json.load(f)
			except ValueError:
				continue
		textures = {v for v in (data.get("textures") or {}).values() if isinstance(v, str)}
		parent = data.get("parent")
		refs[rel] = (textures, parent if isinstance(parent, str) else None)
	return refs


def stale_item_files(out):
	"""The items pass's files the layout no longer uses: blade stubs and detail textures the run did not write, then the
	display parents and textures from before Milestone 7 once no model left in the tree names them (so the old guard
	folders keep their parent until the slot passes' --prune deletes them)."""
	written = out.written
	stale = []
	stubs = os.path.join(lib.ASSETS, "models/item/lightsaber/blade")
	if os.path.isdir(stubs):
		for name in sorted(os.listdir(stubs)):
			rel = f"models/item/lightsaber/blade/{name}"
			if rel not in written:
				stale.append(rel)
	for name in sorted(os.listdir(os.path.join(lib.ASSETS, "textures/item"))):
		if name.endswith("_detail.png") and name.split("_")[0] in SLOTS:
			rel = f"textures/item/{name}"
			if rel not in written:
				stale.append(rel)
	refs = model_refs(out)

	def named(ref, kind):
		return any(ref in (tex if kind == "texture" else {parent}) for rel, (tex, parent) in refs.items() if rel not in stale)

	for base in OLD_BASES:
		rel = f"models/item/lightsaber/{base}.json"
		if os.path.exists(os.path.join(lib.ASSETS, rel)) and rel not in written and not named(f"{NS}:item/lightsaber/{base}", "parent"):
			stale.append(rel)
	for name in OLD_TEXTURES:
		rel = f"textures/item/{name}.png"
		if os.path.exists(os.path.join(lib.ASSETS, rel)) and not named(f"{NS}:item/{name}", "texture"):
			stale.append(rel)
	return stale


def remove_empty_dirs(dry):
	for folder in ("models/item/lightsaber/guard", "models/item/lightsaber/staff_guard"):
		path = os.path.join(lib.ASSETS, folder)
		if os.path.isdir(path) and not os.listdir(path):
			print(f"{'would remove' if dry else 'removed'} empty folder {folder}")
			if not dry:
				os.rmdir(path)


def write(out, files):
	for rel, data in files.items():
		if isinstance(data, Image.Image):
			out.png(rel, data)
		else:
			out.json(rel, data)


def main(argv=None):
	ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("--only", action="append", help="emitters, grips, pommels or items (comma-separated or repeated)")
	ap.add_argument("--out", help="write under this folder instead of the assets folder (a dry run)")
	ap.add_argument("--flat-icons", action="store_true", help="keep flat inventory icons for the sabers and the parts")
	ap.add_argument("--prune", action="store_true", help="delete what the new layout no longer uses (a dry run lists it)")
	ap.add_argument("--strict", action="store_true", help="fail on a part without a design or a design's problems")
	a = ap.parse_args(argv)
	passes = []
	for item in a.only or list(PASSES):
		for p in item.split(","):
			if p not in PASSES:
				ap.error(f"unknown pass {p} (one of {', '.join(PASSES)})")
			if p not in passes:
				passes.append(p)
	passes.sort(key=PASSES.index)
	dry = a.out is not None
	out = Out(os.path.abspath(a.out) if dry else lib.ASSETS, dry)
	ds, missing = load_designs(a.strict)
	planned = {}
	for p in passes:
		files = slot_files(PASS_SLOT[p], ds) if p in PASS_SLOT else item_files(ds, missing, a.flat_icons)
		check_files(files)
		planned[p] = files
	for p in passes:
		write(out, planned[p])
		print(f"{p}: wrote {len(planned[p])} files")
	if a.prune:
		for p in passes:
			stale = stale_slot_files(PASS_SLOT[p], out.written) if p in PASS_SLOT else stale_item_files(out)
			for rel in stale:
				print(f"{'would delete' if dry else 'deleted'} {rel}")
				out.delete(rel)
		remove_empty_dirs(dry)
	if missing:
		print(f"build.py: no design yet for {', '.join(missing)}: left out of the selects; their items keep their flat models")
	print(f"build.py: {len(out.written)} files written under {os.path.relpath(out.root, lib.ROOT) if not dry else out.root}"
			+ (f", {len(out.deleted)} {'to delete' if dry else 'deleted'}" if a.prune else ""))
	return 0


if __name__ == "__main__":
	sys.exit(main())
