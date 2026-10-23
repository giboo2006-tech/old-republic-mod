#!/usr/bin/env python3
"""Previews of hilt designs without the game (docs/MILESTONE_7.md 10.2): a small software renderer that
draws any element list the way the game builds it (vanilla's face corners and UV rotations, the element
rotations of lib.py, nearest texel sampling, the material tint on tinted faces), from any angle, with a
blade stub showing where the emitter's blade leaves it. The lead owns this file.

Every command needs --out DIR (a scratch folder; previews never go into the repository):

  preview.py part emitter guardian --out DIR        one design from eight angles, its sheet, its icon sizes
  preview.py hilt guardian ridged weighted --out DIR an assembled hilt (emitter, grip, pommel ids)
  preview.py staff guardian --out DIR               the saberstaff with that emitter at both ends
  preview.py sheet emitters --out DIR               a contact sheet of a slot (emitters, grips, pommels, all)
  preview.py materials guardian ridged weighted --out DIR   one hilt in every material
  preview.py selftest --out DIR                     checks the renderer against lib's baked turns

Options: --material ID (steel, durasteel, starfall, aurelite, duskglass, sunstone, clan_iron, hymnsteel,
white) or --materials E,G,P for a hilt; --scale px per model unit; --blade units (0 hides it);
--colour RRGGBB for the blade; --smooth for true normals (the game lights a quad by its nearest axis
direction, which is the default here too); --game-light for the game's two fixed lights instead of the
studio lights.
"""
import argparse
import importlib
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import lib  # noqa: E402
from lib import DIRS, FACE_CORNERS, NORMAL, Element, Paint, Region, box, flatten, rot_x, rot_z, staff_lower, staff_upper  # noqa: E402

MATERIALS = {
	"steel": "E6E8EC", "durasteel": "B4C0CE", "starfall": "8C8496", "aurelite": "E0C878",
	"duskglass": "7C6C9C", "sunstone": "E09A4C", "clan_iron": "5C6676", "hymnsteel": "CCC2E8", "white": "FFFFFF",
}
MODULES = {"emitter": "emitters", "grip": "grips", "pommel": "pommels"}

# The game's Direction order: calculateFacing keeps the first of equally near directions.
FACING_ORDER = ("down", "up", "north", "south", "west", "east")

# name: (yaw, pitch); yaw 0 looks at the thumb side (+x), positive yaw turns the camera toward -z
VIEWS = {
	"thumb": (0, 0), "three_quarter": (38, 16), "north": (90, 0), "back": (180, 0),
	"south": (270, 0), "back_quarter": (218, 16), "top": (20, 72), "low": (-28, -32),
}
PART_VIEWS = ("thumb", "three_quarter", "north", "back", "south", "back_quarter", "top", "low")

# Frames: the model point at the image centre and the half width and half height shown, in model units
FRAMES = {
	"emitter": ((8.0, 5.65, 8.0), 3.4, 2.4),
	"grip": ((8.0, 1.8, 8.0), 1.9, 2.3),
	"pommel": ((8.0, -1.55, 8.0), 1.9, 1.75),
	"staff_grip": ((8.0, 8.0, 8.0), 1.9, 3.8),
	"hilt": ((8.0, 2.2, 8.0), 3.4, 5.9),
	"staff": ((8.0, 8.0, 8.0), 3.4, 11.4),
}
# pixels per model unit when --scale is not given
SCALES = {"emitter": 32, "grip": 36, "pommel": 40, "staff_grip": 26, "hilt": 24, "staff": 14}
# The first person view: the hilt about 120 pixels tall on an 854 by 480 screen (6.1)
FIRST_PERSON_SCALE = 13


def hex_rgb(h):
	h = h.lstrip("#")
	return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------ scenes

class Group:
	"""Elements sharing textures ("#common", "#part") and one tint for their tinted faces."""

	def __init__(self, elements, textures, tint=(255, 255, 255)):
		self.elements = flatten([elements])
		self.textures = {k: np.asarray(v.convert("RGBA"), dtype=np.float32) if isinstance(v, Image.Image) else v
				for k, v in textures.items()}
		self.tint = np.array(tint, dtype=np.float32) / 255.0


def common_texture():
	import common
	return common.sheet().img


def design_group(d, material="steel"):
	return Group(d.elements, {"#common": common_texture(), "#part": d.sheet.img}, hex_rgb(MATERIALS[material]))


def blade_group(d_or_blade, length, colour, staff=False):
	"""A blade stub (and quillons) where the emitter's blade data says the blade leaves."""
	if length <= 0:
		return None
	blade = d_or_blade.blade if hasattr(d_or_blade, "blade") else d_or_blade
	if blade is None:
		return None
	core = Image.new("RGBA", (64, 64), (255, 255, 255, 255))
	white = Paint("#blade", Region(0, 0, 4, 4), mode="solid")
	els = []

	def stick(x, y, ln, tilt_deg, r=0.22):
		el = box(x - r, y, 8 - r, x + r, y + ln, 8 + r, white, light=15, name="blade")
		el.R = rot_z(-tilt_deg)
		el.origin = lib._vec((x, y, 8.0))
		return el

	def side(z, y, ln, sign, r=0.16):
		"""A quillon from (8, y, z) pointing +z (sign 1) or -z (sign -1)."""
		el = box(8 - r, y, z - r, 8 + r, y + ln, z + r, white, light=15, name="quillon")
		el.R = rot_x(90.0 * sign)
		el.origin = lib._vec((8.0, y, z))
		return el

	mx, my = blade.mouth
	els.append(stick(mx, my, length, blade.tilt))
	if blade.quillons:
		q = blade.quillons
		ql = q.get("length", 1.2)
		els.append(side(8 + q["z"], q["y"], ql, 1))
		els.append(side(8 - q["z"], q["y"], ql, -1))
	if staff:
		up = lib.translate(els, (0, lib.STAFF_SHIFT, 0))
		down = lib.transform(up, rot_z(180.0), lib.STAFF_CENTRE)
		els = up + down
	g = Group(els, {"#blade": core})
	g.blade = hex_rgb(colour)
	return g


# ------------------------------------------------------------------ the renderer

def _camera(yaw, pitch):
	cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
	cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
	back = np.array([cp * cy, sp, -cp * sy])        # from the target toward the camera
	fwd = -back
	right = np.cross(fwd, (0.0, 1.0, 0.0))
	right /= np.linalg.norm(right)
	up = np.cross(right, fwd)
	return right, up, fwd


def _facing(n):
	"""The game's calculateFacing: the nearest axis direction, the first in Direction order on a tie."""
	best, best_dot = "up", 0.0
	for d in FACING_ORDER:
		dot = float(np.dot(n, NORMAL[d]))
		if dot > best_dot + 1e-6:
			best, best_dot = d, dot
	return np.array(NORMAL[best], dtype=float)


def _face_quads(el):
	lo, hi = el.lo, el.hi
	size = hi - lo
	for d, f in el.faces.items():
		pts = [el.world(lo + np.array(s, dtype=float) * size) for s in FACE_CORNERS[d]]
		u0, v0, u1, v1 = f.uv
		corners = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
		q = (f.rotation // 90) % 4
		uvs = [corners[(i + q) % 4] for i in range(4)]
		yield d, f, pts, uvs, el.R @ np.array(NORMAL[d], dtype=float)


class Canvas:
	def __init__(self, w, h):
		self.w, self.h = w, h
		self.rgb = np.zeros((h, w, 3), dtype=np.float32)
		self.alpha = np.zeros((h, w), dtype=np.float32)
		self.z = np.full((h, w), np.inf, dtype=np.float32)
		self.glow = np.zeros((h, w, 3), dtype=np.float32)

	def tri(self, P, Z, UV, tex, uv_box, mult, glow=None):
		x0 = max(int(math.floor(P[:, 0].min())), 0)
		x1 = min(int(math.ceil(P[:, 0].max())), self.w - 1)
		y0 = max(int(math.floor(P[:, 1].min())), 0)
		y1 = min(int(math.ceil(P[:, 1].max())), self.h - 1)
		if x0 > x1 or y0 > y1:
			return
		(ax, ay), (bx, by), (cx, cy) = P
		area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
		if abs(area) < 1e-9:
			return
		X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
		w0 = ((bx - X) * (cy - Y) - (by - Y) * (cx - X)) / area
		w1 = ((cx - X) * (ay - Y) - (cy - Y) * (ax - X)) / area
		w2 = 1.0 - w0 - w1
		inside = (w0 >= -1e-7) & (w1 >= -1e-7) & (w2 >= -1e-7)
		if not inside.any():
			return
		z = w0 * Z[0] + w1 * Z[1] + w2 * Z[2]
		zs = self.z[y0:y1 + 1, x0:x1 + 1]
		m = inside & (z < zs - 1e-5)
		if not m.any():
			return
		u = w0 * UV[0, 0] + w1 * UV[1, 0] + w2 * UV[2, 0]
		v = w0 * UV[0, 1] + w1 * UV[1, 1] + w2 * UV[2, 1]
		ulo, vlo, uhi, vhi = uv_box
		u = np.clip(u, ulo + 1e-4, uhi - 1e-4)
		v = np.clip(v, vlo + 1e-4, vhi - 1e-4)
		th, tw = tex.shape[:2]
		tx = np.clip((u * tw / 16.0).astype(int), 0, tw - 1)
		ty = np.clip((v * th / 16.0).astype(int), 0, th - 1)
		texel = tex[ty, tx]
		m &= texel[..., 3] >= 128
		if not m.any():
			return
		col = texel[..., :3] * mult
		self.rgb[y0:y1 + 1, x0:x1 + 1][m] = col[m]
		self.alpha[y0:y1 + 1, x0:x1 + 1][m] = 1.0
		zs[m] = z[m]
		g = self.glow[y0:y1 + 1, x0:x1 + 1]
		g[m] = glow if glow is not None else 0.0

	def image(self):
		rgb = np.clip(self.rgb, 0, 255).astype(np.uint8)
		a = (self.alpha * 255).astype(np.uint8)
		return Image.fromarray(np.dstack([rgb, a]), "RGBA")


def _light(n_cam, game_light, n_model):
	if game_light:
		l0 = np.array((0.2, 1.0, -0.7)) / np.linalg.norm((0.2, 1.0, -0.7))
		l1 = np.array((-0.2, 1.0, 0.7)) / np.linalg.norm((-0.2, 1.0, 0.7))
		return min(1.0, (max(0.0, n_model @ l0) + max(0.0, n_model @ l1)) * 0.6 + 0.4)
	key = np.array((-0.45, 0.72, 0.55))
	key /= np.linalg.norm(key)
	fill = np.array((0.75, 0.05, 0.45))
	fill /= np.linalg.norm(fill)
	return min(1.08, 0.36 + 0.62 * max(0.0, n_cam @ key) + 0.24 * max(0.0, n_cam @ fill))


def render(groups, view=(0, 0), frame=None, scale=24, ss=3, smooth=False, game_light=False, roll=0.0):
	"""An RGBA image of the groups seen from view (yaw, pitch), framed by frame (centre, half_w, half_h)."""
	groups = [g for g in groups if g is not None]
	yaw, pitch = VIEWS[view] if isinstance(view, str) else view
	right, up, fwd = _camera(yaw, pitch)
	if roll:
		c, s = math.cos(math.radians(roll)), math.sin(math.radians(roll))
		right, up = c * right + s * up, -s * right + c * up
	centre, half_w, half_h = frame
	centre = np.array(centre, dtype=float)
	k = scale * ss
	W, H = int(round(2 * half_w * k)), int(round(2 * half_h * k))
	cv = Canvas(W, H)
	for g in groups:
		glowing = getattr(g, "blade", None)
		for el in g.elements:
			for d, f, pts, uvs, n in _face_quads(el):
				if n @ fwd >= -1e-9:            # facing away (the game culls item back faces)
					continue
				tex = g.textures.get(f.texture)
				if tex is None:
					raise SystemExit(f"no texture {f.texture} for {el.name}")
				P = np.array([((p - centre) @ right * k + W / 2, -((p - centre) @ up) * k + H / 2) for p in pts])
				Z = np.array([(p - centre) @ fwd for p in pts])
				UV = np.array(uvs, dtype=float)
				nl = n if smooth else _facing(n)
				n_cam = np.array((nl @ right, nl @ up, -(nl @ fwd)))
				shade = 1.0 if glowing else _light(n_cam, game_light, nl)
				if el.light and not glowing:
					shade += (1.0 - min(shade, 1.0)) * el.light / 15.0 * 0.7
				mult = np.full(3, shade, dtype=np.float32)
				if f.tint:
					mult = mult * g.tint
				u_lo, u_hi = sorted((f.uv[0], f.uv[2]))
				v_lo, v_hi = sorted((f.uv[1], f.uv[3]))
				glow = np.array(glowing, dtype=np.float32) if glowing else None
				for a, b, c in ((0, 1, 2), (0, 2, 3)):
					cv.tri(P[[a, b, c]], Z[[a, b, c]], UV[[a, b, c]], tex, (u_lo, v_lo, u_hi, v_hi), mult, glow)
	img = cv.image()
	if cv.glow.any():
		g = Image.fromarray(np.clip(cv.glow, 0, 255).astype(np.uint8), "RGB")
		halo = g.filter(ImageFilter.GaussianBlur(radius=0.35 * k))
		ha = np.asarray(halo, dtype=np.float32)
		glow_a = np.clip(ha.max(axis=2) / 255.0 * 1.6, 0, 1)
		base = np.asarray(img, dtype=np.float32)
		out = base.copy()
		lit = base[..., 3] < 1
		out[..., :3] = np.where(lit[..., None], ha * 1.4, base[..., :3])
		out[..., 3] = np.where(lit, glow_a * 255, base[..., 3])
		# the core takes a little of the blade colour at its edge
		img = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")
	if ss > 1:
		img = img.resize((W // ss, H // ss), Image.Resampling.BOX)
	return img


# ------------------------------------------------------------------ pictures

BG_TOP, BG_BOTTOM = (64, 70, 80), (30, 33, 40)


def backdrop(w, h):
	t = np.linspace(0, 1, h)[:, None, None]
	arr = np.array(BG_TOP, dtype=np.float32) * (1 - t) + np.array(BG_BOTTOM, dtype=np.float32) * t
	arr = np.repeat(arr, w, axis=1)
	return Image.fromarray(arr.astype(np.uint8), "RGB").convert("RGBA")


def _font():
	try:
		return ImageFont.load_default(size=13)
	except TypeError:
		return ImageFont.load_default()


def label(img, text, colour=(220, 224, 232)):
	pad = 18
	out = Image.new("RGBA", (img.width, img.height + pad), (0, 0, 0, 0))
	out.alpha_composite(img, (0, 0))
	d = ImageDraw.Draw(out)
	d.text((4, img.height + 2), text, fill=colour, font=_font())
	return out


def on_backdrop(img):
	bg = backdrop(img.width, img.height)
	bg.alpha_composite(img)
	return bg


def grid(cells, cols, gap=6, bg=(22, 24, 29, 255)):
	"""Cells (RGBA images) in a grid, left to right."""
	if not cells:
		return Image.new("RGBA", (8, 8), bg)
	cw = max(c.width for c in cells)
	ch = max(c.height for c in cells)
	rows = (len(cells) + cols - 1) // cols
	out = Image.new("RGBA", (cols * cw + (cols + 1) * gap, rows * ch + (rows + 1) * gap), bg)
	for i, c in enumerate(cells):
		r, k = divmod(i, cols)
		out.alpha_composite(c, (gap + k * (cw + gap), gap + r * (ch + gap)))
	return out


def sheet_view(d, zoom=8):
	"""The design's own 64x64 sheet enlarged, regions outlined and named (empty sheets say so)."""
	img = d.sheet.img.resize((64 * zoom, 64 * zoom), Image.Resampling.NEAREST)
	bg = Image.new("RGBA", img.size, (255, 0, 255, 255))
	bg.alpha_composite(img)
	dr = ImageDraw.Draw(bg)
	for name, spec in d.sheet.regions.items():
		reg = spec[0]
		dr.rectangle([reg.x * zoom, reg.y * zoom, (reg.x + reg.w) * zoom - 1, (reg.y + reg.h) * zoom - 1], outline=(0, 255, 255, 255))
		dr.text((reg.x * zoom + 2, reg.y * zoom + 1), name, fill=(0, 255, 255, 255), font=_font())
	if not d.sheet.regions:
		dr.text((8, 8), "no sheet: common swatches only", fill=(255, 255, 255, 255), font=_font())
	return bg


def icon_views(groups, frame_key="hilt"):
	"""The hilt as an inventory icon might show it: diagonal, at 32 and 16 pixels (enlarged to read)."""
	centre, hw, hh = FRAMES[frame_key]
	half = max(hw, hh) * 0.82
	img = render(groups, (24, 14), (centre, half, half), scale=24, roll=-45)
	out = []
	for size in (32, 16):
		small = img.resize((size, size), Image.Resampling.BOX)
		out.append(label(on_backdrop(small.resize((128, 128), Image.Resampling.NEAREST)), f"{size} px"))
	return out


# ------------------------------------------------------------------ designs

def designs(module):
	try:
		mod = importlib.import_module(module)
	except ModuleNotFoundError as e:
		if e.name == module:
			return {}
		raise
	return mod.DESIGNS


def find(slot, id):
	ds = designs(MODULES[slot])
	if id not in ds:
		raise SystemExit(f"no {slot} design {id}; {MODULES[slot]}.py has: {', '.join(sorted(ds)) or 'none'}")
	return ds[id]


def hilt_groups(e, g, p, mats, blade_len, colour):
	return [design_group(e, mats[0]), design_group(g, mats[1]), design_group(p, mats[2]), blade_group(e, blade_len, colour)]


def staff_groups(e, g, mats, blade_len, colour):
	up = Group(staff_upper(e.elements), {"#common": common_texture(), "#part": e.sheet.img}, hex_rgb(MATERIALS[mats[0]]))
	down = Group(staff_lower(e.elements), {"#common": common_texture(), "#part": e.sheet.img}, hex_rgb(MATERIALS[mats[0]]))
	return [up, down, design_group(g, mats[1]), blade_group(e, blade_len, colour, staff=True)]


# ------------------------------------------------------------------ commands

def cmd_part(a):
	d = find(a.slot, a.id)
	frame = FRAMES[d.slot]
	scale = a.scale or SCALES[d.slot]
	groups = [design_group(d, a.material)]
	if d.slot == "emitter":
		groups.append(blade_group(d, a.blade if a.blade is not None else 1.6, a.colour))
	cells = [label(on_backdrop(render(groups, v, frame, scale, smooth=a.smooth, game_light=a.game_light)), v) for v in PART_VIEWS]
	views = grid(cells, 4)
	n = len(d.elements)
	glow = sum(1 for e in d.elements if e.light)
	title = f"{d.item_id} ({d.title or d.id}): {n} elements, {glow} glowing, material {a.material}"
	probs = d.problems()
	out = Image.new("RGBA", (views.width, views.height + 22 + 16 * len(probs)), (22, 24, 29, 255))
	out.alpha_composite(views, (0, 22))
	dr = ImageDraw.Draw(out)
	dr.text((8, 4), title, fill=(236, 236, 240), font=_font())
	for i, p in enumerate(probs):
		dr.text((8, views.height + 22 + 16 * i), p, fill=(255, 120, 110), font=_font())
	paths = [save(out, a.out, f"{d.item_id}_{a.material}.png"), save(sheet_view(d), a.out, f"{d.item_id}_sheet.png")]
	return paths


def cmd_hilt(a):
	e, g, p = find("emitter", a.emitter), find("grip", a.grip), find("pommel", a.pommel)
	mats = a.materials.split(",") if a.materials else [a.material] * 3
	groups = hilt_groups(e, g, p, mats, a.blade if a.blade is not None else 2.5, a.colour)
	scale = a.scale or SCALES["hilt"]
	cells = [label(on_backdrop(render(groups, v, FRAMES["hilt"], scale, smooth=a.smooth, game_light=a.game_light)), v)
			for v in ("thumb", "three_quarter", "north", "back", "back_quarter", "low")]
	# first person size, pixel for pixel (no smoothing), as the game will show it
	fp = render(groups, "thumb", FRAMES["hilt"], FIRST_PERSON_SCALE, ss=1, smooth=a.smooth, game_light=a.game_light)
	cells.append(label(on_backdrop(fp), "first person size"))
	cells += icon_views(groups[:3])
	name = f"hilt_{a.emitter}_{a.grip}_{a.pommel}_{'-'.join(mats)}.png"
	return [save(grid(cells, 4), a.out, name)]


def cmd_staff(a):
	e = find("emitter", a.emitter)
	g = find("grip", "staff")
	mats = a.materials.split(",") if a.materials else [a.material] * 2
	groups = staff_groups(e, g, mats, a.blade if a.blade is not None else 2.5, a.colour)
	scale = a.scale or SCALES["staff"]
	cells = [label(on_backdrop(render(groups, v, FRAMES["staff"], scale, smooth=a.smooth, game_light=a.game_light)), v)
			for v in ("thumb", "three_quarter", "north", "back_quarter")]
	return [save(grid(cells, 4), a.out, f"staff_{a.emitter}_{'-'.join(mats)}.png")]


def cmd_sheet(a):
	slots = ("emitter", "grip", "pommel") if a.which == "all" else ({"emitters": "emitter", "grips": "grip", "pommels": "pommel"}[a.which],)
	paths = []
	for slot in slots:
		cells = []
		for id, d in designs(MODULES[slot]).items():
			frame = FRAMES[d.slot]
			scale = a.scale or SCALES[d.slot]
			groups = [design_group(d, a.material)]
			if d.slot == "emitter":
				groups.append(blade_group(d, 1.6, a.colour))
			for v in ("thumb", "three_quarter", "back_quarter"):
				cells.append(label(on_backdrop(render(groups, v, frame, scale, smooth=a.smooth, game_light=a.game_light)),
						f"{id} {v}" + (" !" if d.problems() else "")))
		paths.append(save(grid(cells, 6), a.out, f"contact_{MODULES[slot]}_{a.material}.png"))
	return paths


def cmd_materials(a):
	e, g, p = find("emitter", a.emitter), find("grip", a.grip), find("pommel", a.pommel)
	cells = []
	for m in MATERIALS:
		if m == "white":
			continue
		groups = hilt_groups(e, g, p, [m] * 3, 2.0, a.colour)
		cells.append(label(on_backdrop(render(groups, "three_quarter", FRAMES["hilt"], (a.scale or SCALES["hilt"]) * 0.75, smooth=a.smooth,
				game_light=a.game_light)), m))
	return [save(grid(cells, 8), a.out, f"materials_{a.emitter}_{a.grip}_{a.pommel}.png")]


def cmd_selftest(a):
	"""Turning a design by a quarter turn and turning the camera with it must give the same picture: the
	renderer's UV rules (vanilla's) agree with lib's baked face rotations."""
	import common
	steel = Paint("#common", Region(0, 0, 16, 16), tint=True)
	tex = {"#common": common.sheet().img}
	arrow = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
	dr = ImageDraw.Draw(arrow)
	dr.rectangle([0, 0, 31, 31], fill=(220, 40, 40, 255))
	dr.rectangle([32, 0, 63, 31], fill=(40, 200, 60, 255))
	dr.rectangle([0, 32, 31, 63], fill=(40, 80, 220, 255))
	dr.polygon([(8, 4), (14, 4), (14, 20)], fill=(255, 255, 255, 255))
	tex["#part"] = arrow
	p = Paint("#part", Region(0, 0, 64, 64), mode="fit")
	els = [box(7, 1, 7.4, 9.5, 3.2, 8.6, p, name="probe"), box(6.6, 3.2, 6.6, 9.4, 3.6, 9.4, steel, name="collar")]
	bad = []
	for q in (90, 180, 270):
		for ax, rot in (("y", lib.rot_y), ("x", lib.rot_x), ("z", lib.rot_z)):
			turned = lib.transform(els, rot(q), (8, 2.2, 8))
			assert all(np.allclose(e.R, np.eye(3)) for e in turned), "a quarter turn left a rotation field"
			for view in ((20, 25), (200, -30), (100, 60)):
				right, up, fwd = _camera(*view)
				M = rot(q)
				# the camera turned with the model: render the turned model from the turned camera
				r2, u2, f2 = M @ right, M @ up, M @ fwd
				img_a = _render_basis(els, tex, right, up, fwd)
				img_b = _render_basis(turned, tex, r2, u2, f2)
				diff = np.abs(np.asarray(img_a, dtype=int) - np.asarray(img_b, dtype=int))
				frac = (diff.max(axis=2) > 40).mean()
				if frac > 0.004:
					bad.append(f"turn {ax} {q} view {view}: {frac:.3%} of pixels differ")
					save(grid([img_a, img_b], 2), a.out, f"selftest_{ax}{q}_{view[0]}_{view[1]}.png")
	if bad:
		raise SystemExit("preview selftest failed:\n" + "\n".join(bad))
	print("preview selftest: ok")
	return []


def _render_basis(els, tex, right, up, fwd, k=24.0, size=160):
	"""Render with an explicit camera basis (for the self-test)."""
	g = Group(els, tex)
	centre = np.array((8.0, 2.2, 8.0))
	cv = Canvas(size, size)
	for el in g.elements:
		for d, f, pts, uvs, n in _face_quads(el):
			if n @ fwd >= -1e-9:
				continue
			P = np.array([((p - centre) @ right * k + size / 2, -((p - centre) @ up) * k + size / 2) for p in pts])
			Z = np.array([(p - centre) @ fwd for p in pts])
			n_cam = np.array((n @ right, n @ up, -(n @ fwd)))
			shade = _light(n_cam, False, n)
			u_lo, u_hi = sorted((f.uv[0], f.uv[2]))
			v_lo, v_hi = sorted((f.uv[1], f.uv[3]))
			for a_, b_, c_ in ((0, 1, 2), (0, 2, 3)):
				cv.tri(P[[a_, b_, c_]], Z[[a_, b_, c_]], np.array(uvs)[[a_, b_, c_]], g.textures[f.texture],
						(u_lo, v_lo, u_hi, v_hi), np.full(3, shade, dtype=np.float32))
	return on_backdrop(cv.image())


def save(img, out, name):
	os.makedirs(out, exist_ok=True)
	path = os.path.join(out, name)
	img.save(path)
	return path


def main():
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	sub = ap.add_subparsers(dest="cmd", required=True)

	def common_opts(p):
		p.add_argument("--out", required=True, help="a scratch folder for the pictures")
		p.add_argument("--material", default="steel", choices=sorted(MATERIALS))
		p.add_argument("--scale", type=float, default=None, help="pixels per model unit (default by frame)")
		p.add_argument("--blade", type=float, default=None, help="blade stub length in model units (0 hides it)")
		p.add_argument("--colour", default="5AA8FF", help="blade colour RRGGBB")
		p.add_argument("--smooth", action="store_true", help="light faces by their true normals")
		p.add_argument("--game-light", action="store_true", help="the game's two fixed item lights")

	p = sub.add_parser("part")
	p.add_argument("slot", choices=("emitter", "grip", "pommel"))
	p.add_argument("id")
	common_opts(p)
	p = sub.add_parser("hilt")
	p.add_argument("emitter")
	p.add_argument("grip")
	p.add_argument("pommel")
	p.add_argument("--materials", help="emitter,grip,pommel materials")
	common_opts(p)
	p = sub.add_parser("staff")
	p.add_argument("emitter")
	p.add_argument("--materials", help="emitter,grip materials")
	common_opts(p)
	p = sub.add_parser("sheet")
	p.add_argument("which", choices=("emitters", "grips", "pommels", "all"))
	common_opts(p)
	p = sub.add_parser("materials")
	p.add_argument("emitter")
	p.add_argument("grip")
	p.add_argument("pommel")
	common_opts(p)
	p = sub.add_parser("selftest")
	p.add_argument("--out", required=True)
	a = ap.parse_args()
	paths = globals()[f"cmd_{a.cmd}"](a)
	for path in paths:
		print(f"wrote {path}")


if __name__ == "__main__":
	main()
