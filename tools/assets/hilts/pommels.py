#!/usr/bin/env python3
"""The pommel designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each pommel id of HiltParts to its
Design. Pommels live in the band y -2.0 to -0.1 (rings and loops to -3.0), body radius up to 1.4, and
meet the grip with a collar of radius 1.05 to 1.15 at y -0.1.

The Weighted pommel is the lead's reference design: read it before writing another. Preview with
  python3 tools/assets/hilts/preview.py part pommel weighted --out <scratch dir>
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import AXIS_X, AXIS_Z, TAN, Design, around, box, lathe, octo, registry, ring, rot_x, side_box, transform, turn  # noqa: E402

DESIGNS, design = registry()


# ------------------------------------------------------------------ helpers

def squared(y0, y1, a, d, paint, corner, *, top=False, bottom=False, name="squared"):
	"""A squared octagonal prism: four broad flat faces at a from the axis (x and z) and four narrow corner
	chamfers at d (a < d < a * sqrt 2) painted corner. Built like lib.octo from four bars that draw only their
	end faces and caps; every cap takes paint's flat colour, because the caps overlap."""
	w = d * math.sqrt(2) - a       # half width of a flat face
	c = math.sqrt(2) * a - d       # half width of a corner chamfer
	cap = paint.solid()
	ends = [d_ for d_, on in (("up", top), ("down", bottom)) if on]
	caps = {"up": cap, "down": cap}
	bars = [
		box(AXIS_X - a, y0, AXIS_Z - w, AXIS_X + a, y1, AXIS_Z + w, paint, faces=["east", "west"] + ends, paints=caps, name=name),
		box(AXIS_X - w, y0, AXIS_Z - a, AXIS_X + w, y1, AXIS_Z + a, paint, faces=["north", "south"] + ends, paints=caps, name=name),
	]
	diag = box(AXIS_X - d, y0, AXIS_Z - c, AXIS_X + d, y1, AXIS_Z + c, corner, faces=["east", "west"] + ends, paints=caps, name=name)
	bars += turn([diag], 45) + turn([diag], -45)
	for b in bars:
		b.meta = {"kind": "octo", "r": max(a, d), "y0": y0, "y1": y1}   # so Design.problems() checks its radius
	return bars


def slot(y0, y1, r, width, depth, face, floor, wall, k=0, light=0, name="slot"):
	"""A vertical slot cut into side k of an octagon of inradius r, for a band drawn with that side left out
	(octo skip=(k,)) between bands with caps: the two strips of the side beside the slot, its floor depth
	units in (glowing at light, for an ember-filled slot) and its two walls. The caps above and below close it."""
	h = r * TAN
	s = width / 2
	x_out, x_in = AXIS_X + r, AXIS_X + r - depth
	els = [
		box(x_out - 0.05, y0, AXIS_Z - h, x_out, y1, AXIS_Z - s, face, faces=["east"], name=name),
		box(x_out - 0.05, y0, AXIS_Z + s, x_out, y1, AXIS_Z + h, face, faces=["east"], seed=3, name=name),
		box(x_in - 0.05, y0, AXIS_Z - s, x_in, y1, AXIS_Z + s, floor, faces=["east"], light=light, name=name),
		box(x_in, y0, AXIS_Z - s - 0.05, x_out, y1, AXIS_Z - s, wall, faces=["south"], name=name),
		box(x_in, y0, AXIS_Z + s, x_out, y1, AXIS_Z + s + 0.05, wall, faces=["north"], name=name),
	]
	return turn(els, 45.0 * k) if k else els


def rods(points, t, paint, name="rod"):
	"""A bent wire in the plane x 8 (it faces the thumb side): one square rod of thickness t from each point
	(z, y) to the next, straight or at 45 degrees. A diagonal rod is turned 44.9 degrees about x, so its
	outer face takes the light of the side it leans toward. Use a solid paint: the rods overlap at the
	joints."""
	out = []
	for (z0, y0), (z1, y1) in zip(points, points[1:]):
		dz, dy = z1 - z0, y1 - y0
		if abs(dz) < 1e-9 or abs(dy) < 1e-9:     # straight: a plain box, lengthened over the joints
			out.append(box(AXIS_X - t / 2, min(y0, y1) - t / 2, min(z0, z1) - t / 2, AXIS_X + t / 2, max(y0, y1) + t / 2,
					max(z0, z1) + t / 2, paint, name=name))
			continue
		length = math.hypot(dz, dy)
		cz, cy = (z0 + z1) / 2, (y0 + y1) / 2
		rod = box(AXIS_X - t / 2, cy - length / 2, cz - t / 2, AXIS_X + t / 2, cy + length / 2, cz + t / 2, paint,
				skip=("up", "down"), name=name)
		# the rod starts along +y, and rot_x(a) takes +y to (y cos a, z sin a): a = atan2(dz, dy), folded
		# into (-90, 90] (a rod has no direction), a diagonal eased to 44.9
		angle = math.degrees(math.atan2(dz, dy))
		angle = angle - 180 if angle > 90 else angle + 180 if angle <= -90 else angle
		if abs(abs(angle) - 45) < 1e-6:
			angle = math.copysign(44.9, angle)
		out += transform([rod], rot_x(angle), (AXIS_X, cy, cz))
	return out


# ------------------------------------------------------------------ designs

@design
def weighted():
	"""A heavy counterweight: a wide bevelled cap split by a turned groove and carrying four socket-head
	weight screws, a black band above it, a brass base ring under it and an end plate with a turned boss
	and its hex socket."""
	d = Design("pommel", "weighted", "Weighted")
	sh = d.sheet
	steel, satin, dark = cp("steel"), cp("steel_satin"), cp("steel_dark")

	# the sheet: a socket-head screw (untinted gunmetal: a lit rim, a dark socket) and the black band's
	# two rubber ribs (each a lit top, its face, a dark gap)
	sh.region("screw", 0, 0, 4, 4, mode="fit")
	sh.region("band", 8, 0, 4, 6, mode="fit", flat="band_flat")
	sh.region("band_flat", 12, 0, 2, 2, mode="solid")
	sh.fill("screw", (80, 84, 94))
	sh.rect(0, 0, 3, 0, (132, 138, 150))
	sh.rect(0, 0, 0, 3, (132, 138, 150))
	sh.rect(1, 1, 2, 2, (12, 12, 14))
	for y, v in enumerate((60, 38, 16, 60, 38, 16)):
		sh.rect(8, y, 11, y, (v, v, v + 3))
	sh.fill("band_flat", (30, 30, 33))
	screw, band = sh.paint("screw"), sh.paint("band")
	gun = cp("gunmetal_flat")

	d.add(
		lathe([
			(-1.9, -1.84, 0.92, dark),        # the end plate
			(-1.84, -1.72, 1.08, cp("brass")),  # the brass base ring
			(-1.72, -1.62, 1.24, satin),      # the lower bevel
			(-1.62, -1.28, 1.35, steel),      # the cap, lower band
			(-1.28, -1.2, 1.28, dark),        # a turned groove
			(-1.2, -0.86, 1.35, steel),       # the cap, upper band
			(-0.86, -0.76, 1.25, satin),      # the upper bevel
			(-0.76, -0.3, 1.16, band),        # the black band, two rubber ribs
			(-0.3, -0.1, 1.1, steel),         # the join collar
		]),
		# four socket-head weight screws round the upper band, one on the thumb side
		around(side_box(-1.14, -0.92, 1.35, 0.28, 0.05, gun, paints={"east": screw}, name="screw"), 4),
		# the end boss and its hex socket
		octo(-1.95, -1.9, 0.46, cp("gunmetal"), bottom=True, name="boss"),
		box(7.84, -2.0, 7.84, 8.16, -1.95, 8.16, cp("black_flat"), skip=("up",), name="socket"),
	)
	return d


@design
def balanced():
	"""A slim satin cap with fine turned grooves, a two-step bevelled end and a small brass nub; a flush
	balance screw on the thumb side and another opposite it."""
	d = Design("pommel", "balanced", "Balanced")
	sh = d.sheet
	steel, satin, dark, brass = cp("steel"), cp("steel_satin"), cp("steel_dark"), cp("brass")

	# the sheet: a flush slotted screw head (tinted: a lit rim, a dark slot across it)
	sh.region("screw", 0, 0, 4, 4, tint=True, mode="fit")
	sh.fill("screw", (176, 176, 176))
	sh.rect(0, 0, 3, 0, (226, 226, 226))
	sh.rect(0, 3, 3, 3, (112, 112, 112))
	sh.rect(0, 1, 3, 1, (48, 48, 48))
	screw = sh.paint("screw")

	d.add(
		lathe([
			(-2.0, -1.94, 0.24, cp("brass_hi_flat")),  # the nub's crown
			(-1.94, -1.8, 0.35, brass),        # the brass nub
			(-1.8, -1.68, 0.56, dark),         # the end ring
			(-1.68, -1.56, 0.76, steel),       # the bevel, outer step
			(-1.56, -1.42, 0.92, steel),       # the bevel, inner step
			(-1.42, -0.98, 1.04, satin),       # the body, lower
			(-0.98, -0.92, 0.96, dark),        # a fine turned groove
			(-0.92, -0.34, 1.04, satin),       # the body, upper
			(-0.34, -0.26, 0.98, dark),        # a turned groove under the collar
			(-0.26, -0.1, 1.1, steel),         # the join collar
		]),
		side_box(-1.32, -1.1, 1.04, 0.22, 0.06, cp("steel_lo_flat"), k=0, paints={"east": screw}, name="screw"),
		side_box(-1.32, -1.1, 1.04, 0.22, 0.06, cp("steel_lo_flat"), k=4, paints={"east": screw}, name="screw"),
	)
	return d


@design
def archivist():
	"""A squared archival cap: broad riveted plates between brass corner strips, framed by squared lids
	with brass corners; a small amber glass vial in a dark housing on the thumb side; a bevelled base with
	a brass seal boss."""
	d = Design("pommel", "archivist", "Archivist")
	sh = d.sheet
	steel, satin, dark, brass = cp("steel"), cp("steel_satin"), cp("steel_dark"), cp("brass")

	# the sheet: a riveted plate (tinted: a panel seam inside the edge, a rivet in each corner) and the
	# vial's amber glass (a bright core, a dark edge)
	sh.region("plate", 0, 0, 8, 6, tint=True, mode="fit", flat="plate_flat")
	sh.region("plate_flat", 8, 0, 2, 2, tint=True, mode="solid")
	sh.region("vial", 12, 0, 3, 6, mode="fit")
	sh.brushed("plate", 200, spread=3, vertical=False, seed=4)
	sh.outline("plate", (156, 156, 156))
	for x, y in ((1, 1), (6, 1), (1, 4), (6, 4)):
		sh.px(x, y, (244, 244, 244))
	sh.fill("plate_flat", (200, 200, 200))
	sh.gradient("vial", (255, 206, 112), (170, 96, 22))
	sh.rect(12, 0, 12, 5, (110, 62, 16))
	sh.rect(13, 1, 13, 3, (255, 236, 180))
	plate, vial = sh.paint("plate"), sh.paint("vial")

	d.add(
		octo(-0.26, -0.1, 1.1, steel, top=True, name="collar"),
		squared(-0.46, -0.26, 1.22, 1.38, satin, brass, top=True, bottom=True, name="lid"),
		squared(-1.5, -0.46, 1.12, 1.3, plate, brass, name="body"),
		squared(-1.7, -1.5, 1.22, 1.38, satin, brass, top=True, bottom=True, name="lid"),
		lathe([
			(-1.94, -1.82, 0.78, dark),     # the base
			(-1.82, -1.7, 1.0, steel),      # the bevel
		], top=False),
		octo(-2.0, -1.94, 0.36, brass, bottom=True, name="seal"),
		# the vial housing on the thumb side: a dark plate, the amber glass and its brass stoppers
		side_box(-1.38, -0.58, 1.12, 0.6, 0.07, cp("gunmetal"), name="housing"),
		side_box(-1.2, -0.76, 1.19, 0.3, 0.06, vial, skip=("west", "up", "down"), light=4, name="vial"),   # glows faintly
		side_box(-0.76, -0.66, 1.19, 0.38, 0.12, cp("brass_flat"), name="stopper"),
		side_box(-1.3, -1.2, 1.19, 0.38, 0.12, cp("brass_flat"), name="stopper"),
	)
	return d


@design
def hask():
	"""A heavy block of hammered clan iron, cut by four ember-filled slots, with a broad, flat, polished
	striking face; a stout neck and shoulder under the grip."""
	d = Design("pommel", "hask", "Hask")
	sh = d.sheet

	# the sheet: hammered iron (tinted): hammer dents, each lit on its upper left and shadowed on its lower
	# right; the slots' ember (hottest at the foot, where the coals sit)
	sh.region("iron", 0, 0, 16, 16, tint=True, flat="iron_flat")
	sh.region("iron_flat", 16, 0, 2, 2, tint=True, mode="solid")
	sh.region("ember", 20, 0, 4, 8, mode="fit")
	sh.brushed("iron", 146, spread=3, vertical=False, seed=9)
	for fx, fy in ((1, 1), (7, 2), (12, 0), (4, 6), (10, 7), (14, 5), (1, 11), (7, 12), (12, 12), (3, 15), (9, 15)):
		for dx, dy, v in ((0, 0, 26), (1, 0, 18), (0, 1, 18), (1, 1, -8), (2, 1, -24), (1, 2, -24), (2, 2, -30)):
			x, y = fx + dx, fy + dy
			if 0 <= x < 16 and 0 <= y < 16:
				g = sh.get(x, y)[0] + v
				sh.px(x, y, (g, g, g))
	sh.fill("iron_flat", (146, 146, 146))
	for y, c in enumerate(((70, 18, 12), (110, 28, 16), (160, 44, 22), (206, 70, 30), (238, 104, 44), (255, 150, 70),
			(255, 196, 110), (236, 120, 52))):
		sh.rect(20, y, 23, y, c)
	sh.rect(20, 0, 20, 7, (60, 16, 10))
	sh.rect(23, 0, 23, 7, (60, 16, 10))
	iron, ember = sh.paint("iron"), sh.paint("ember")
	face = cp("steel_satin")
	gun = cp("gunmetal")

	d.add(
		lathe([
			(-0.46, -0.34, 1.25, iron),    # the shoulder
			(-0.34, -0.24, 1.0, gun),      # the neck
			(-0.24, -0.1, 1.1, iron),      # the join collar
		], bottom=False),
		octo(-0.7, -0.46, 1.4, iron, top=True, bottom=True, name="block"),
		octo(-1.45, -0.7, 1.4, iron, skip=(0, 2, 4, 6), name="block"),
		around(slot(-1.45, -0.7, 1.4, 0.44, 0.32, iron, ember, gun, light=4, name="slot"), 4),   # the embers glow faintly
		octo(-1.7, -1.45, 1.4, iron, top=True, bottom=True, name="block"),
		lathe([
			(-2.0, -1.84, 1.2, face),      # the striking face
			(-1.84, -1.7, 1.3, face),      # its chamfer
		], top=False),
	)
	return d


@design
def ringed():
	"""A grooved cap with a D-ring hanging below it on two short posts: three turned bands between dark
	grooves, a bevelled end plate, the ring's straight side running through the posts as their pin."""
	d = Design("pommel", "ringed", "Ringed")
	steel, satin, dark = cp("steel"), cp("steel_satin"), cp("steel_dark")
	gun = cp("gunmetal")

	d.add(
		lathe([
			(-1.88, -1.76, 0.9, dark),      # the end plate
			(-1.76, -1.64, 1.06, satin),    # the bevel
			(-1.64, -1.3, 1.18, steel),     # a band
			(-1.3, -1.22, 1.08, dark),      # a groove
			(-1.22, -0.88, 1.18, steel),    # a band
			(-0.88, -0.8, 1.08, dark),      # a groove
			(-0.8, -0.3, 1.18, steel),      # a band
			(-0.3, -0.1, 1.1, steel),       # the join collar
		]),
		# the two posts under the end plate
		box(7.83, -2.34, 7.4, 8.17, -1.88, 7.62, gun, skip=("up",), name="post"),
		box(7.83, -2.34, 8.38, 8.17, -1.88, 8.6, gun, skip=("up",), name="post"),
		# the D-ring in the plane x 8, so it faces the thumb side: its straight pin through the posts, the
		# sides, the turned lower corners and the bottom bar (a solid paint: the rods overlap at the joints)
		rods([(7.66, -2.9), (7.3, -2.54), (7.3, -2.17), (8.7, -2.17), (8.7, -2.54), (8.34, -2.9), (7.66, -2.9)],
				0.18, cp("steel_hi_flat"), name="ring"),
	)
	return d


@design
def domed():
	"""A dome of stacked turned rings under a flange, narrowing to a brass bezel that holds a small crystal
	window at its apex; the crystal glows faintly."""
	d = Design("pommel", "domed", "Domed")
	sh = d.sheet
	steel, satin = cp("steel"), cp("steel_satin")

	# the sheet: one turned ring of the dome (tinted): a lit upper edge, the face, a dark groove under it
	sh.region("tier", 0, 0, 4, 4, tint=True, mode="fit", flat="tier_flat")
	sh.region("tier_flat", 4, 0, 2, 2, tint=True, mode="solid")
	for y, v in enumerate((236, 212, 198, 112)):
		sh.rect(0, y, 3, y, (v, v, v))
	sh.fill("tier_flat", (204, 204, 204))
	tier = sh.paint("tier")

	dome = lathe([
		(-2.0, -1.78, 0.4, cp("glass_blue")),  # the crystal window
		(-1.78, -1.66, 0.56, cp("brass")),  # its bezel
		(-1.66, -1.5, 0.7, tier),       # the dome's rings, curving in to the apex
		(-1.5, -1.32, 0.86, tier),
		(-1.32, -1.1, 1.0, tier),
		(-1.1, -0.86, 1.13, tier),
		(-0.86, -0.62, 1.23, tier),
		(-0.62, -0.36, 1.29, tier),
		(-0.36, -0.24, 1.34, satin),    # the flange
		(-0.24, -0.1, 1.1, steel),      # the join collar
	], name="dome")
	for e in dome:
		if e.name == "dome[0]":         # the crystal glows faintly
			e.light = 5
	d.add(dome)
	return d


@design
def watchman():
	"""A Watch knight's pommel in worn, pitted metal: a fluted cap under a grooved brass band, a bevelled
	end plate, and below it a lanyard eyelet facing the thumb side."""
	d = Design("pommel", "watchman", "Watchman")
	sh = d.sheet
	worn, dark = cp("steel_worn"), cp("steel_dark")

	# the sheet: a fluted face (tinted worn metal, a dark flute down its middle with a lit lip, a few pits),
	# the grooved brass band, the eyelet tab with its hole
	sh.region("flute", 0, 0, 6, 8, tint=True, mode="fit", flat="flute_flat")
	sh.region("flute_flat", 6, 0, 2, 2, tint=True, mode="solid")
	sh.region("band", 0, 8, 8, 4, mode="fit", flat="band_flat")
	sh.region("band_flat", 8, 8, 2, 2, mode="solid")
	sh.region("eyelet", 12, 0, 4, 4, tint=True, mode="fit")
	sh.brushed("flute", 178, spread=4, seed=21)
	for x, v in ((1, 156), (2, 92), (3, 66), (4, 212)):
		sh.rect(x, 0, x, 7, (v, v, v))
	sh.wear("flute", pits=3, scratches=0, amount=34, seed=22)
	sh.fill("flute_flat", (178, 178, 178))
	sh.brushed("band", (190, 150, 76), spread=6, vertical=False, seed=23)
	sh.rect(0, 9, 7, 9, (110, 80, 34))
	sh.rect(0, 10, 7, 10, (226, 190, 112))
	sh.fill("band_flat", (190, 150, 76))
	sh.fill("eyelet", (172, 172, 172))
	sh.rect(13, 1, 14, 2, (16, 16, 16))
	sh.rect(12, 0, 15, 0, (206, 206, 206))
	flute, band, eyelet = sh.paint("flute"), sh.paint("band"), sh.paint("eyelet")

	d.add(
		lathe([
			(-1.72, -1.58, 0.94, dark),     # the end plate
			(-1.58, -1.44, 1.12, worn),     # the bevel
			(-1.44, -0.44, 1.26, flute),    # the fluted cap
			(-0.44, -0.24, 1.17, band),     # the grooved brass band
			(-0.24, -0.1, 1.1, worn),       # the join collar
		]),
		# the eyelet tab under the end plate, its hole facing the thumb side, its foot rounded off
		box(7.88, -2.24, 7.7, 8.12, -1.72, 8.3, worn, paints={"east": eyelet, "west": eyelet}, skip=("up",), name="eyelet"),
		box(7.88, -2.34, 7.82, 8.12, -2.24, 8.18, worn, skip=("up",), name="eyelet"),
	)
	return d


@design
def penitent():
	"""Darth Ulvaine's pommel: a dark lacquered teardrop tapering to a blunt metal tip (no spike), under a
	dark steel shoulder with an ember-red ring set in below it."""
	d = Design("pommel", "penitent", "Penitent")
	sh = d.sheet
	dark, satin = cp("steel_dark"), cp("steel_satin")

	# the sheet: dark lacquer (paint, untinted, like the set's grip): a soft gloss streak down each side
	sh.region("lacquer", 0, 0, 8, 16, flat="lacquer_flat", mode="fit")
	sh.region("lacquer_flat", 8, 0, 2, 2, mode="solid")
	for x, y, lx, ly in sh._pixels("lacquer"):
		g = (34, 30, 30, 44, 60, 42, 31, 28)[lx]
		sh.px(x, y, (g, g - 6, g - 3))
	sh.fill("lacquer_flat", (32, 26, 29))
	lacquer = sh.paint("lacquer")

	body = lathe([
			(-2.0, -1.9, 0.22, satin),      # the blunt tip
			(-1.9, -1.78, 0.36, dark),      # its collar
			(-1.78, -1.62, 0.52, lacquer),  # the teardrop, tapering downward
			(-1.62, -1.44, 0.68, lacquer),
			(-1.44, -1.24, 0.84, lacquer),
			(-1.24, -1.02, 0.98, lacquer),
			(-1.02, -0.78, 1.1, lacquer),
			(-0.78, -0.52, 1.18, lacquer),  # its widest swell
			(-0.52, -0.44, 1.1, cp("ember")),  # the ember ring
			(-0.44, -0.24, 1.16, dark),     # the shoulder
			(-0.24, -0.1, 1.1, dark),       # the join collar
		])
	for e in body:
		if e.name == "lathe[8]":        # the ember ring glows faintly, like the emitter's and the grip's
			e.light = 4
	d.add(body)
	return d


@design
def choir():
	"""The Long Hymn choir's pommel: a small bell of three stacked rings widening downward to a flared lip,
	a pale violet inlay under the first ring and a violet glass clapper in its dark mouth."""
	d = Design("pommel", "choir", "Choir")
	sh = d.sheet
	satin, dark = cp("steel_satin"), cp("steel_dark")

	# the sheet: one ring of the bell (tinted: a lit lip on top, a dark joint below), the pale violet inlay
	sh.region("ring", 0, 0, 16, 4, tint=True, mode="fit", flat="ring_flat")
	sh.region("ring_flat", 16, 0, 2, 2, tint=True, mode="solid")
	sh.region("inlay", 20, 0, 2, 2, mode="solid")
	sh.brushed("ring", 214, spread=2, vertical=False, seed=16)
	sh.rect(0, 0, 15, 0, (244, 244, 244))
	sh.rect(0, 3, 15, 3, (140, 140, 140))
	sh.fill("ring_flat", (214, 214, 214))
	sh.fill("inlay", (184, 150, 250))
	rings, inlay = sh.paint("ring"), sh.paint("inlay")

	d.add(
		lathe([
			(-1.8, -1.44, 1.3, rings),      # the third ring, the bell's sound bow
			(-1.44, -1.36, 1.16, dark),     # a groove
			(-1.36, -1.02, 1.2, rings),     # the second ring
			(-1.02, -0.88, 1.06, inlay),    # the violet inlay
			(-0.88, -0.52, 1.12, rings),    # the first ring, the bell's shoulder
			(-0.52, -0.24, 0.98, dark),     # the bell's crown
			(-0.24, -0.1, 1.1, satin),      # the join collar
		], bottom=False, name="bell"),
		# the flared lip: a hollow ring, dark inside, round the mouth; the mouth's dark roof and the clapper
		ring(-2.0, -1.8, 1.4, 0.18, satin, inner=cp("steel_deep_flat"), name="lip"),
		octo(-1.82, -1.8, 1.24, cp("mouth_flat"), skip=range(8), bottom=True, name="mouth"),
		octo(-1.96, -1.82, 0.3, cp("glass_violet"), bottom=True, name="clapper"),
	)
	for e in d.elements:
		if e.name == "bell[3]":         # the violet inlay glows faintly, like the emitter's inlay and the grip's beads
			e.light = 4
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		glow = sum(1 for e in d.elements if e.light)
		print(f"{d.item_id}: {len(d.elements)} elements, {glow} glowing" + ("" if not probs else "\n  " + "\n  ".join(probs)))
