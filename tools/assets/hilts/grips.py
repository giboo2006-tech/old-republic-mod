#!/usr/bin/env python3
"""The grip designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each grip id of HiltParts to its Design.
Grips live in the band y -0.1 to 3.7 with a body radius of 0.85 to 1.15 and a collar of radius 1.05 to
1.15 at both ends; the staff grip ("staff", slot staff_grip) spans y 4.6 to 11.4 in the staff model.

The Ridged grip is the lead's reference design: read it before writing another. Preview with
  python3 tools/assets/hilts/preview.py part grip ridged --out <scratch dir>
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import (AXIS_X, AXIS_Z, STAFF_CENTRE, Design, around, box, grey, lathe, octo, registry,  # noqa: E402
		rot_x, rot_z, side_box, transform, turn)

DESIGNS, design = registry()


# ------------------------------------------------------------------ shared helpers

def _point(elements):
	"""The staff's point symmetry (10.3): the elements turned 180 degrees about the z axis through (8, 8, 8).
	transform copies each body's record of its heights (meta, read by Design.problems) unchanged, so the
	turned copies get theirs turned too."""
	out = transform(elements, rot_z(180.0), STAFF_CENTRE)
	for e in out:
		if "y0" in e.meta:
			e.meta["y0"], e.meta["y1"] = 2 * STAFF_CENTRE[1] - e.meta["y1"], 2 * STAFF_CENTRE[1] - e.meta["y0"]
	return out


def _slab(y0, y1, r0, r1, width, thickness, paint, k=0, *, light=0, name="slab"):
	"""A flat slab on octagon side k whose outer face runs from radius r0 at y0 to radius r1 at y1: one side
	of a tapering or flaring body. The slab is tilted about the side's tangent, so it stays a single box."""
	ym, rm = (y0 + y1) / 2, (r0 + r1) / 2
	length = math.hypot(y1 - y0, r1 - r0)
	tilt = math.degrees(math.atan2(r0 - r1, y1 - y0))      # positive leans the top in toward the axis
	el = box(AXIS_X + rm - thickness, ym - length / 2, AXIS_Z - width / 2, AXIS_X + rm, ym + length / 2, AXIS_Z + width / 2,
			paint, skip=("west",), light=light, name=name)
	el = transform([el], rot_z(tilt), (AXIS_X + rm, ym, AXIS_Z))
	return turn(el, 45.0 * k) if k else el


def _rgb(c, n=0.0):
	return tuple(max(0, min(255, int(round(v + n)))) for v in c)


def _knurl(sh, name, peak=222, cut=174, seed=0):
	"""A fine diamond knurl in grey (tinted): at four texels a unit the finest diamond lattice is a one-texel
	checker, its peaks a little lighter on the upper of each pair of rows. Paint it in "scale" mode so no
	texel is squeezed."""
	rng = random.Random(seed)
	for x, y, lx, ly in sh._pixels(name):
		v = (peak + (6 if ly % 2 == 0 else -6)) if (lx + ly) % 2 == 0 else cut
		sh.px(x, y, grey(v + rng.uniform(-2, 2)))


def _strap(sh, name, profile, run=2, seed=0, grain=3.0, vary=6.0):
	"""A strap or cord wound around an octagon, painted on a "wrap" region (an eighth of its width per side,
	so the bands run on from side to side). Each band climbs one texel every run columns; profile lists the
	colours down one band, top first (len(profile) * run must divide the region's width). Each band is a
	little lighter or darker than the next (vary), with a fine grain."""
	reg = sh.regions[name][0]
	rng = random.Random(seed)
	n = len(profile)
	period = n * run
	shades = [rng.uniform(-vary, vary) for _ in range(64)]
	for x, y, lx, ly in sh._pixels(reg):
		t = (lx + run * ly) % period
		band = (lx + run * ly) // period
		sh.px(x, y, _rgb(profile[t // run], shades[band % 64] + rng.uniform(-grain, grain)))


# ------------------------------------------------------------------ the classic

@design
def ridged():
	"""The classic: a turned steel tube with four rubber T-tracks of short ridges over its lower two thirds, a
	clamp band at the top carrying the activation box, a clamp lever hanging behind and a brass clamp screw."""
	d = Design("grip", "ridged", "Ridged")
	sh = d.sheet
	steel, dark = cp("steel"), cp("steel_dark")

	# the sheet: the turned tube (a fine groove near each end), the clamp (a lit lip, a shadowed lip), a
	# T-track ridge (lit top over dark rubber), the near-black rail and the activation box's panel
	sh.region("tube", 0, 0, 16, 10, tint=True, flat="tube_flat")
	sh.region("tube_flat", 16, 0, 2, 2, tint=True, mode="solid")
	sh.region("clamp", 0, 12, 16, 3, tint=True, flat="tube_flat")
	sh.region("ridge", 20, 0, 8, 2, flat="rail", mode="fit")
	sh.region("rail", 20, 4, 8, 8)
	sh.region("panel", 32, 0, 6, 5, mode="fit")
	sh.brushed("tube", 206, spread=3, vertical=False, seed=21)
	for row, v in ((1, 128), (2, 228), (8, 128), (9, 228)):
		sh.rect(0, row, 15, row, grey(v))
	sh.fill("tube_flat", grey(206))
	for row, v in enumerate((238, 214, 156)):
		sh.rect(0, 12 + row, 15, 12 + row, grey(v))
	sh.noise("clamp", 2, seed=22)
	sh.rect(20, 0, 27, 0, (78, 78, 84))
	sh.rect(20, 1, 27, 1, (38, 38, 42))
	sh.fill("rail", (20, 20, 23))
	sh.noise("rail", 2, seed=23)
	sh.fill("panel", (78, 82, 92))
	sh.outline("panel", (40, 42, 48))
	sh.rect(33, 1, 36, 1, (112, 118, 130))
	tube, clamp, ridge, rail = sh.paint("tube"), sh.paint("clamp"), sh.paint("ridge"), sh.paint("rail")

	d.add(
		lathe([
			(-0.1, 0.1, 1.1, steel),       # the join collar
			(0.1, 0.22, 1.03, dark),       # a turned step
			(0.22, 2.62, 0.95, tube),      # the tube
			(2.62, 2.78, 1.03, dark),      # a turned band under the clamp
			(2.78, 3.5, 1.12, clamp),      # the clamp
			(3.5, 3.7, 1.08, steel),       # the join collar
		]),
	)
	# a T-track on each diagonal side: a near-black rail and six short ridges with lit tops
	track = side_box(0.3, 2.46, 0.95, 0.46, 0.08, rail, name="rail")
	for i in range(6):
		y = 0.36 + i * 0.36
		track += side_box(y, y + 0.22, 0.95, 0.62, 0.2, ridge, name="ridge", seed=i)
	d.add(around(track, 4, start=45))
	# the activation box and its stud on the thumb side, the clamp lever behind, the clamp screw on -z
	d.add(
		side_box(2.9, 3.4, 1.12, 0.62, 0.26, sh.paint("panel"), name="box",
				paints={"up": cp("gunmetal_hi_flat"), "down": cp("gunmetal_flat"), "north": cp("gunmetal"), "south": cp("gunmetal")}),
		side_box(3.04, 3.28, 1.38, 0.28, 0.1, cp("button_black"), name="stud"),
		side_box(2.86, 3.44, 1.12, 0.42, 0.08, cp("steel_satin"), k=4, name="lever"),
		side_box(2.28, 3.3, 1.2, 0.2, 0.08, cp("steel_satin"), k=4, name="lever tail"),
		side_box(3.02, 3.28, 1.12, 0.24, 0.1, cp("brass"), k=2, name="screw"),
	)
	return d


# ------------------------------------------------------------------ craftable grips

@design
def wrapped():
	"""A dark brown leather strap wound over the tube between two machined steel collars, bound with cord at
	both ends; a slim slider rail runs down the thumb side from the upper collar."""
	d = Design("grip", "wrapped", "Wrapped")
	sh = d.sheet
	steel = cp("steel")

	# the sheet: the leather wrap (16 rows over the wrap's 2 units; 5 columns per octagon side, so each
	# strap climbs a texel a column and runs on unbroken from side to side: a lit edge, three shades and
	# the dark line where the next turn overlaps it), the two collars with their turned grooves, the
	# binding cord, and the slider rail with its dark slot
	sh.region("wrap", 0, 0, 40, 16, flat="wrap_flat", mode="wrap")
	sh.region("wrap_flat", 40, 0, 2, 2, mode="solid")
	sh.region("low", 0, 18, 16, 2, tint=True, flat="metal_flat")
	sh.region("up", 0, 22, 16, 4, tint=True, flat="metal_flat")
	sh.region("metal_flat", 20, 18, 2, 2, tint=True, mode="solid")
	sh.region("cord", 44, 0, 16, 1)
	sh.region("rail", 44, 4, 3, 8, mode="fit")
	_strap(sh, "wrap", ((124, 87, 57), (98, 66, 41), (82, 55, 34), (68, 44, 27), (28, 18, 11)), run=1, seed=31)
	sh.fill("wrap_flat", (76, 50, 31))
	for row, v in ((18, 222), (19, 132)):
		sh.rect(0, row, 15, row, grey(v))
	for row, v in ((22, 234), (23, 132), (24, 214), (25, 160)):
		sh.rect(0, row, 15, row, grey(v))
	sh.noise("low", 2, seed=32)
	sh.noise("up", 2, seed=33)
	sh.fill("metal_flat", grey(200))
	sh.fill("cord", (40, 30, 23))
	sh.noise("cord", 4, seed=34)
	sh.fill("rail", (80, 84, 94))
	sh.rect(45, 4, 45, 11, (12, 12, 14))
	wrap, low, up, cord = sh.paint("wrap"), sh.paint("low"), sh.paint("up"), sh.paint("cord")

	d.add(
		lathe([
			(-0.1, 0.12, 1.1, steel),      # the join collar
			(0.12, 0.5, 1.07, low),        # the lower collar
			(0.5, 0.58, 1.04, cord),       # the binding
			(0.58, 2.58, 1.02, wrap),      # the leather wrap
			(2.58, 2.66, 1.04, cord),      # the binding
			(2.66, 3.46, 1.1, up),         # the upper collar
			(3.46, 3.7, 1.08, steel),      # the join collar
		]),
		# the slider: a rail plate over the top of the wrap and its knob, low in the slot
		side_box(2.1, 3.38, 1.02, 0.3, 0.13, sh.paint("rail"), inset=0.05, name="rail",
				paints={"up": cp("gunmetal_flat"), "down": cp("gunmetal_flat"), "north": cp("gunmetal"), "south": cp("gunmetal")}),
		side_box(2.32, 2.52, 1.15, 0.2, 0.1, cp("steel_satin"), name="knob"),
	)
	return d


@design
def staff():
	"""The saberstaff's long grip: a black ribbed middle for two hands, dark steel rings, turned steel tubes
	and a clamp band at each end carrying an activation box under its blade. Point-symmetric about the
	staff's centre (8, 8, 8): the upper box faces the thumb side, the lower one the far side."""
	d = Design("staff_grip", "staff", "Staff")
	sh = d.sheet
	dark = cp("steel_dark")

	# the sheet: the ribbed rubber of the middle, a rib, the turned tube and the clamp band (its top lip is
	# the join collar)
	sh.region("ribbed", 0, 0, 16, 11)
	sh.region("rib", 16, 0, 8, 2, flat="rib_flat", mode="fit")
	sh.region("rib_flat", 24, 0, 2, 2, mode="solid")
	sh.region("tube", 0, 12, 16, 3, tint=True, flat="metal_flat")
	sh.region("clamp", 0, 16, 16, 6, tint=True, flat="metal_flat")
	sh.region("metal_flat", 24, 4, 2, 2, tint=True, mode="solid")
	sh.ridges("ribbed", (34, 34, 38), (16, 16, 18), period=4, groove_w=1, highlight=(52, 52, 58))
	sh.noise("ribbed", 2, seed=41)
	sh.rect(16, 0, 23, 0, (82, 82, 88))
	sh.rect(16, 1, 23, 1, (40, 40, 44))
	sh.fill("rib_flat", (60, 60, 66))
	sh.brushed("tube", 204, spread=3, vertical=False, seed=42)
	sh.rect(0, 12, 15, 12, grey(150))
	for row, v in enumerate((236, 214, 210, 128, 206, 156)):
		sh.rect(0, 16 + row, 15, 16 + row, grey(v))
	sh.noise("clamp", 2, seed=43)
	sh.fill("metal_flat", grey(204))
	ribbed, rib, tube, clamp = sh.paint("ribbed"), sh.paint("rib"), sh.paint("tube"), sh.paint("clamp")

	# the upper half, from the ribbed middle up to the emitter; the lower half is the same turned about
	# the staff's centre
	upper = [
		lathe([
			(9.3, 9.52, 1.08, dark),       # a ring closing the ribbed middle
			(9.52, 10.1, 0.95, tube),      # a turned tube
			(10.1, 11.4, 1.1, clamp),      # the clamp band; its top lip is the join collar
		]),
		side_box(10.42, 10.98, 1.1, 0.62, 0.26, cp("gunmetal"), name="box",
				paints={"up": cp("gunmetal_hi_flat"), "down": cp("gunmetal_flat")}),
		side_box(10.56, 10.82, 1.36, 0.28, 0.1, cp("button_black"), name="stud"),
		side_box(10.3, 11.1, 1.1, 0.24, 0.1, cp("brass"), k=4, name="clamp screw"),
	]
	d.add(upper, _point(upper))
	# the ribbed middle: a rubber core and five raised ribs
	d.add(octo(6.7, 9.3, 0.96, ribbed, name="middle"))
	for y in (7.0, 7.5, 8.0, 8.5, 9.0):
		d.add(octo(y - 0.1, y + 0.1, 1.04, rib, top=True, bottom=True, name="rib"))
	return d


@design
def banded():
	"""A ribbed column: six thick machined rings over a black rubber core, a small brass activation stud on
	the thumb side between the top rings."""
	d = Design("grip", "banded", "Banded")
	sh = d.sheet
	steel = cp("steel")

	# the sheet: a machined ring (lit upper edge, shadowed lower edge) and the core's fine-ribbed rubber
	sh.region("ring", 0, 0, 16, 3, tint=True, flat="ring_flat", mode="fit")
	sh.region("ring_flat", 16, 0, 2, 2, tint=True, mode="solid")
	sh.region("rubber", 0, 4, 16, 14)
	for row, v in enumerate((240, 210, 150)):
		sh.rect(0, row, 15, row, grey(v))
	sh.noise("ring", 2, seed=51)
	sh.fill("ring_flat", grey(214))
	sh.ridges("rubber", (32, 32, 35), (20, 20, 22), period=2)
	ring_paint = sh.paint("ring")

	d.add(
		octo(-0.1, 0.12, 1.1, steel, top=True, bottom=True, name="collar"),
		octo(0.12, 3.46, 0.96, sh.paint("rubber"), name="core"),
		octo(3.46, 3.7, 1.08, steel, top=True, bottom=True, name="collar"),
	)
	for i in range(6):
		y = 0.34 + 0.52 * i
		d.add(octo(y, y + 0.3, 1.1, ring_paint, top=True, bottom=True, name="ring"))
	# the activation stud: a dark plate bridging the gap between the top two rings, a brass button on it
	d.add(
		side_box(2.62, 3.04, 1.1, 0.4, 0.06, cp("gunmetal"), name="plate",
				paints={"up": cp("gunmetal_hi_flat"), "down": cp("gunmetal_flat")}),
		side_box(2.73, 2.93, 1.16, 0.22, 0.1, cp("brass"), name="stud",
				paints={"up": cp("brass_hi_flat"), "down": cp("brass_lo_flat")}),
	)
	return d


@design
def knurled():
	"""A slim tube cut with a diamond knurl over its length, parted in three by two turned grooves, between
	thin collars; a flush slide switch sits in a smooth channel on the thumb side."""
	d = Design("grip", "knurled", "Knurled")
	sh = d.sheet
	steel, satin = cp("steel"), cp("steel_satin")

	# the sheet: the knurl (painted at 4 texels a unit, the length of the tube) and the switch channel's
	# plate with its dark slot
	sh.region("knurl", 0, 0, 16, 14, tint=True, flat="knurl_flat")
	sh.region("knurl_flat", 32, 0, 2, 2, tint=True, mode="solid")
	sh.region("plate", 36, 0, 4, 12, tint=True, mode="fit")
	_knurl(sh, "knurl", seed=71)
	sh.fill("knurl_flat", grey(196))
	sh.fill("plate", grey(222))
	sh.rect(37, 1, 38, 10, grey(60))
	sh.rect(36, 0, 39, 0, grey(240))
	sh.rect(36, 11, 39, 11, grey(150))

	knurl, groove = sh.paint("knurl"), cp("black_flat")
	d.add(
		lathe([
			(-0.1, 0.08, 1.06, steel),     # the join collar
			(0.08, 0.16, 1.0, satin),      # a bevel
			(0.16, 1.21, 0.95, knurl),     # the knurled tube, in three lengths
			(1.21, 1.31, 0.88, groove),    # a turned groove
			(1.31, 2.31, 0.95, knurl),
			(2.31, 2.41, 0.88, groove),    # a turned groove (under the switch channel on the thumb side)
			(2.41, 3.46, 0.95, knurl),
			(3.46, 3.54, 1.0, satin),      # a bevel
			(3.54, 3.7, 1.06, steel),      # the join collar
		]),
		# the flush slide switch: a smooth channel plate and its slider
		side_box(1.9, 3.3, 0.95, 0.42, 0.05, sh.paint("plate"), name="channel"),
		side_box(2.7, 2.94, 1.0, 0.3, 0.08, cp("steel_satin"), name="slider",
				paints={"up": cp("steel_hi_flat"), "down": cp("steel_shadow_flat")}),
	)
	return d


@design
def fluted():
	"""Eight deep vertical channels cut into the metal: eight steel ribs over a black core between a turned
	foot and a head band, a brass ring at the top and a small black switch on the thumb rib."""
	d = Design("grip", "fluted", "Fluted")
	steel, dark, satin = cp("steel"), cp("steel_dark"), cp("steel_satin")

	d.add(
		lathe([
			(-0.1, 0.12, 1.1, steel),      # the join collar
			(0.12, 0.36, 1.06, dark),      # the foot the ribs stand on
			(0.36, 3.0, 0.86, cp("black_flat")),   # the channels' floor
			(3.0, 3.16, 1.06, dark),       # the head band
			(3.16, 3.42, 1.12, cp("brass")),       # the brass ring
			(3.42, 3.7, 1.08, steel),      # the join collar
		]),
	)
	# a rib on every side; the channels are the gaps at the corners
	d.add(around(side_box(0.36, 3.0, 0.86, 0.38, 0.2, satin, skip=("west", "up", "down"), name="rib"), 8))
	d.add(side_box(2.36, 2.64, 1.06, 0.26, 0.08, cp("button_black"), name="switch"))
	return d


# ------------------------------------------------------------------ the legendary grip

@design
def sunforged():
	"""A dark fluted core banded with three rings of sunstone (stone that holds the sun, not gold plating),
	a thin sunstone strip inlaid down the thumb side between them."""
	d = Design("grip", "sunforged", "Sunforged")
	sh = d.sheet
	dark = cp("steel_dark")

	# the sheet: the fluted core (two flutes per octagon side), the sunstone (warm, mottled, a hot vein
	# here and there) and the inlaid strip
	sh.region("core", 0, 0, 32, 4, tint=True, flat="core_flat", mode="wrap")
	sh.region("core_flat", 32, 0, 2, 2, tint=True, mode="solid")
	sh.region("sun", 0, 8, 16, 3, flat="sun_flat", mode="fit")
	sh.region("sun_flat", 16, 8, 2, 2, mode="solid")
	sh.region("strip", 20, 8, 2, 8, mode="fit")
	for x, y, lx, ly in sh._pixels("core"):
		sh.px(x, y, grey((132, 96, 40, 88)[lx % 4]))
	sh.fill("core_flat", grey(100))
	rng = random.Random(61)
	for x, y, lx, ly in sh._pixels("sun"):
		c = ((250, 176, 84), (236, 140, 56), (206, 104, 38))[ly]
		sh.px(x, y, _rgb(c, rng.uniform(-12, 12)))
	for vx, vy in ((2, 1), (3, 1), (7, 0), (11, 1), (12, 2), (14, 0)):
		sh.px(vx, 8 + vy, (255, 222, 150))
	sh.fill("sun_flat", (236, 140, 56))
	sh.fill("strip", (240, 150, 60))
	sh.rect(20, 8, 20, 15, (255, 206, 120))
	core, sun = sh.paint("core"), sh.paint("sun")

	d.add(
		octo(-0.1, 0.12, 1.1, dark, top=True, bottom=True, name="collar"),
		octo(0.12, 3.46, 0.98, core, name="core"),
		octo(3.46, 3.7, 1.08, dark, top=True, bottom=True, name="collar"),
	)
	# three sunstone rings, each set between two dark bezels
	for yc in (0.66, 1.74, 2.82):
		stone = octo(yc - 0.18, yc + 0.18, 1.08, sun, name="sunstone")
		stone[0].light = 5                 # the bar carrying the thumb side and the far side glows
		d.add(
			octo(yc - 0.25, yc - 0.18, 1.12, dark, top=True, bottom=True, name="bezel"),
			stone,
			octo(yc + 0.18, yc + 0.25, 1.12, dark, top=True, bottom=True, name="bezel"),
		)
	# the inlaid strip, showing between the rings from the foot of the core to the third ring
	for y0, y1 in ((0.12, 0.41), (0.91, 1.49), (1.99, 2.57)):
		d.add(side_box(y0, y1, 0.98, 0.1, 0.05, sh.paint("strip"), light=5, name="strip"))
	# the activation stud, steel on the dark core above the top ring
	d.add(side_box(3.14, 3.38, 0.98, 0.3, 0.1, cp("button_steel"), name="stud",
			paints={"up": cp("steel_hi_flat"), "down": cp("steel_shadow_flat")}))
	return d


# ------------------------------------------------------------------ the sets' grips

@design
def reliquary():
	"""The Collector's archival tube: three brass bands, a riveted leather panel over the thumb side and a
	blank tag plate riveted on the -z side."""
	d = Design("grip", "reliquary", "Reliquary")
	sh = d.sheet
	steel, satin, brass = cp("steel"), cp("steel_satin"), cp("brass")

	# the sheet: the leather panel (12 rows of a quarter unit over its 3 units, so its rivets sit at the
	# corners of the two windows the bands leave), the blank tag plate with its border and rivets
	sh.region("panel", 0, 0, 8, 12, mode="fit")
	sh.region("tag", 16, 0, 8, 10, tint=True, mode="fit")
	sh.fill("panel", (86, 56, 35))
	sh.noise("panel", 5, seed=81)
	sh.rect(0, 0, 0, 11, (54, 35, 22))
	sh.rect(7, 0, 7, 11, (54, 35, 22))
	for row in (1, 5, 7, 10):
		for col in (1, 5):
			sh.rect(col, row, col + 1, row, (226, 186, 104))
			sh.px(col + 1, row, (150, 112, 52))
	sh.fill("tag", grey(224))
	sh.outline("tag", grey(150))
	for px_, py_ in ((17, 1), (22, 1), (17, 8), (22, 8)):
		sh.px(px_, py_, grey(250))

	d.add(
		lathe([
			(-0.1, 0.1, 1.1, steel),       # the join collar
			(0.1, 0.36, 1.13, brass),      # a brass band
			(0.36, 1.6, 1.0, satin),       # the tube
			(1.6, 1.78, 1.13, brass),      # a brass band
			(1.78, 3.06, 1.0, satin),      # the tube
			(3.06, 3.32, 1.13, brass),     # a brass band
			(3.32, 3.7, 1.08, steel),      # the join collar
		]),
		# the leather panel runs under all three bands, which strap it to the tube
		side_box(0.24, 3.24, 1.0, 0.86, 0.07, sh.paint("panel"), name="panel",
				paints={"up": cp("leather_flat"), "down": cp("leather_flat"), "north": cp("leather_flat"), "south": cp("leather_flat")}),
		side_box(0.62, 1.42, 1.0, 0.56, 0.05, sh.paint("tag"), k=2, name="tag"),
		side_box(2.96, 3.16, 1.08, 0.22, 0.12, cp("button_steel"), name="stud"),
	)
	return d


@design
def watchman():
	"""A Watch knight's grip, carried a long time: a worn grey cord wrap tied off with a leather thong whose
	tail hangs loose, pitted collars and a battered activation box knocked a little askew."""
	d = Design("grip", "watchman", "Watchman")
	sh = d.sheet

	# the sheet: the cord wrap (24 columns, 3 per side, so a cord three rows thick climbs exactly one turn
	# per turn round the grip), the thong's leather, and worn steel (fine streaks, sparse pits and
	# scratches, like the set's emitter)
	sh.region("cord", 0, 0, 24, 16, flat="cord_flat", mode="wrap")
	sh.region("cord_flat", 24, 0, 2, 2, mode="solid")
	sh.region("thong", 28, 0, 4, 4, flat="thong_flat", mode="fit")
	sh.region("thong_flat", 32, 0, 2, 2, mode="solid")
	sh.region("worn", 0, 20, 16, 16, tint=True, flat="worn_flat")
	sh.region("worn_flat", 16, 20, 2, 2, tint=True, mode="solid")
	sh.brushed("worn", 186, spread=4, vertical=False, seed=93)
	sh.wear("worn", pits=16, scratches=4, amount=46, seed=94)
	sh.fill("worn_flat", grey(184))
	worn = sh.paint("worn")
	_strap(sh, "cord", ((170, 166, 152), (128, 124, 112), (64, 61, 54)), run=8, seed=91, vary=9)
	sh.wear("cord", pits=12, scratches=0, amount=34, seed=92)
	sh.fill("cord_flat", (128, 124, 112))
	sh.fill("thong", (64, 41, 27))
	sh.rect(28, 0, 31, 0, (98, 66, 43))
	sh.rect(28, 3, 31, 3, (40, 26, 17))
	sh.fill("thong_flat", (64, 41, 27))
	thong = sh.paint("thong")

	d.add(
		lathe([
			(-0.1, 0.1, 1.1, worn),        # the join collar
			(0.1, 0.38, 1.05, worn),       # the lower collar
			(0.38, 2.62, 1.0, sh.paint("cord")),   # the cord wrap
			(2.62, 2.74, 1.04, worn),      # a lip
			(2.74, 3.44, 1.1, worn),       # the upper collar
			(3.44, 3.7, 1.08, worn),       # the join collar
		]),
		# the thong: a band tied round the foot of the wrap, its knot on the near diagonal
		octo(0.66, 0.8, 1.06, thong, top=True, bottom=True, name="thong"),
		side_box(0.6, 0.86, 1.05, 0.24, 0.14, thong, k=1, name="knot"),
	)
	# the thong's tail hangs from the knot and swings a little aside
	tail = box(AXIS_X + 1.08, 0.12, AXIS_Z - 0.06, AXIS_X + 1.13, 0.74, AXIS_Z + 0.06, thong, name="tail")
	d.add(turn(transform([tail], rot_x(-14.0), (AXIS_X + 1.1, 0.74, AXIS_Z)), 45))
	# the battered activation box, knocked askew, and its button
	bx = side_box(2.84, 3.38, 1.1, 0.62, 0.28, worn, name="box")
	bx += side_box(2.98, 3.22, 1.38, 0.28, 0.09, cp("button_black"), name="button")
	d.add(transform(bx, rot_z(-5.0), (AXIS_X + 1.1, 3.1, AXIS_Z)))
	return d


@design
def penitent():
	"""Darth Ulvaine's grip: smooth dark lacquer flaring toward the pommel, thin ember-red lines inlaid down
	its diagonal sides, dark steel collars and a slim steel switch tab on the thumb side."""
	d = Design("grip", "penitent", "Penitent")
	sh = d.sheet
	dark = cp("steel_dark")

	# the sheet: the lacquer (a soft gloss streak) and the ember inlay
	sh.region("lacquer", 0, 0, 8, 16, flat="lacquer_flat", mode="fit")
	sh.region("lacquer_flat", 8, 0, 2, 2, mode="solid")
	sh.region("inlay", 12, 0, 1, 16, mode="fit")
	for x, y, lx, ly in sh._pixels("lacquer"):
		g = (34, 30, 30, 42, 58, 40, 30, 28)[lx]
		sh.px(x, y, (g, g - 6, g - 3))
	sh.fill("lacquer_flat", (32, 26, 29))
	sh.gradient("inlay", (255, 120, 70), (200, 40, 26))

	# the flare: eight lacquered slabs leaning in from radius 1.12 at the foot to 0.9 at the neck, each as
	# wide as a side at the foot, so they close up all the way
	y0, y1, r0, r1 = 0.12, 3.3, 1.12, 0.9

	def flare(y, out=0.0):
		return r0 + (r1 - r0) * (y - y0) / (y1 - y0) + out

	d.add(
		octo(-0.1, 0.12, 1.12, dark, top=True, bottom=True, name="collar"),
		[_slab(y0, y1, r0, r1, 0.94, 0.14, sh.paint("lacquer"), k) for k in range(8)],
		# the ember lines stand a hair proud of the lacquer, so the two faces never fight
		[_slab(0.2, 3.22, flare(0.2, 0.06), flare(3.22, 0.06), 0.1, 0.09, sh.paint("inlay"), k, light=4, name="inlay")
				for k in (1, 3, 5, 7)],
		lathe([
			(3.24, 3.46, 0.96, dark),      # the neck
			(3.46, 3.7, 1.08, dark),       # the join collar
		]),
		# the switch: a slim steel tab, the one bright touch on the thumb side
		side_box(2.5, 3.14, 0.92, 0.2, 0.1, cp("steel_satin"), name="tab",
				paints={"up": cp("steel_hi_flat"), "down": cp("steel_shadow_flat")}),
	)
	return d


@design
def choir():
	"""The Long Hymn's grip: stacked pale rings like reeds, narrowing to the middle, a column of small violet
	glass beads down the thumb side and another down the far side, like the stops of a pipe; a small steel
	activation stud tops the thumb side's column."""
	d = Design("grip", "choir", "Choir")
	sh = d.sheet
	satin = cp("steel_satin")

	# the sheet: one reed ring (a lit lip on top, a dark joint below)
	sh.region("reed", 0, 0, 16, 4, tint=True, flat="reed_flat", mode="fit")
	sh.region("reed_flat", 16, 0, 2, 2, tint=True, mode="solid")
	for row, v in enumerate((250, 228, 214, 120)):
		sh.rect(0, row, 15, row, grey(v))
	sh.noise("reed", 2, seed=111)
	sh.fill("reed_flat", grey(226))
	reed = sh.paint("reed")

	radii = (1.13, 1.08, 1.03, 0.98, 0.94, 0.94, 0.98, 1.03, 1.08, 1.13)
	segs = [(-0.1, 0.1, 1.1, satin)]
	y = 0.1
	for r in radii:
		segs.append((y, y + 0.335, r, reed))
		y += 0.335
	segs.append((y, 3.7, 1.08, satin))
	d.add(lathe(segs))
	# the beads: three up the thumb side, three up the far side, between them
	for i, k in ((2, 0), (4, 0), (6, 0), (3, 4), (5, 4), (7, 4)):
		yc = 0.1 + 0.335 * i + 0.1675
		d.add(side_box(yc - 0.11, yc + 0.11, radii[i], 0.28, 0.14, cp("glass_violet"), k=k, light=4, name="bead"))
	# the activation stud tops the thumb side's column of beads, on the eighth ring
	d.add(side_box(2.85, 3.05, radii[8], 0.26, 0.1, cp("button_steel"), name="stud",
			paints={"up": cp("steel_hi_flat"), "down": cp("steel_shadow_flat")}))
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		print(f"{d.item_id}: {len(d.elements)} elements" + ("" if not probs else "\n  " + "\n  ".join(probs)))
