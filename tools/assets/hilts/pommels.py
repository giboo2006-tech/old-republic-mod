#!/usr/bin/env python3
"""The pommel designs (docs/MILESTONE_7.md 6.3): DESIGNS maps each pommel id of HiltParts to its
Design. Pommels live in the band y -2.0 to -0.1 (rings and loops to -3.0), body radius up to 1.4, and
meet the grip with a collar of radius 1.05 to 1.15 at y -0.1.

The Weighted pommel is the lead's reference design: read it before writing another. Preview with
  python3 tools/assets/hilts/preview.py part pommel weighted --out <scratch dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import paint as cp  # noqa: E402
from lib import Design, around, box, lathe, octo, registry, ring, side_box  # noqa: E402

DESIGNS, design = registry()


@design
def weighted():
	"""A heavy bevelled cap over a black rubber band, a brass base ring under it."""
	d = Design("pommel", "weighted", "Weighted")
	sh = d.sheet
	steel, satin, rubber = cp("steel"), cp("steel_satin"), cp("rubber")

	# the sheet: the cap's turned grooves
	sh.region("cap", 0, 0, 16, 8, tint=True, flat="cap_flat")
	sh.region("cap_flat", 16, 0, 2, 2, tint=True, mode="solid")
	sh.brushed("cap", 204, spread=2, vertical=False, seed=5)
	for y in (2, 5):
		sh.rect(0, y, 15, y, (120, 120, 120))
		sh.rect(0, y + 1, 15, y + 1, (226, 226, 226))
	sh.fill("cap_flat", (204, 204, 204))
	cap = sh.paint("cap")

	d.add(
		lathe([
			(-1.98, -1.86, 0.9, cp("steel_dark")),   # the base plate
			(-1.86, -1.72, 1.06, cp("brass")),       # the brass base ring
			(-1.72, -1.6, 1.24, satin),              # the lower bevel
			(-1.6, -0.9, 1.35, cap),                 # the heavy cap
			(-0.9, -0.8, 1.26, satin),               # the upper bevel
			(-0.8, -0.3, 1.16, rubber),              # the black band
			(-0.3, -0.1, 1.1, steel),                # the join collar
		]),
		# two weight screws in the cap, and the end cap's centre
		side_box(-1.4, -1.1, 1.35, 0.3, 0.08, cp("gunmetal"), k=2, name="screw"),
		side_box(-1.4, -1.1, 1.35, 0.3, 0.08, cp("gunmetal"), k=6, name="screw"),
		box(7.55, -2.0, 7.55, 8.45, -1.98, 8.45, cp("gunmetal"), faces=["down"], name="end"),
	)
	return d


if __name__ == "__main__":
	for id_, d in DESIGNS.items():
		probs = d.problems()
		print(f"{d.item_id}: {len(d.elements)} elements" + ("" if not probs else "\n  " + "\n  ".join(probs)))
