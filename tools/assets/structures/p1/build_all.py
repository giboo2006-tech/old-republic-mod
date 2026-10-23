"""Builds worker P1's Selvaren (docs/MILESTONE_6.md 5.5), one script after the other:

    planet/waystone, planet/waystone_corrupted   waystone.py        templates, anchors, site meta; the waystone
                                                                     worldgen pool, structure, set (clear of origin)
    planet/heartstone                            heartstone.py      template, anchors, site meta
    planet/wayfarer_cache                        wayfarer_cache.py  template, anchors, site meta
    selvaren/watch_cloister (bell, fallen)       watch_cloister.py  templates, pool, structure, set, biome tag, loot
    selvaren/tarn_causeway (isle, broken)        tarn_causeway.py   the same
    selvaren/ridge_tower (kept, cold)            ridge_tower.py     the same
    selvaren/hollowhorn_fold (fold, steading)    hollowhorn_fold.py the same
    selvaren/aurelite_dig (adit, face)           aurelite_dig.py    the same

Run from the repository root: python3 tools/assets/structures/p1/build_all.py
Every script is seeded, so two runs write byte-identical files. Previews are rendered only when the environment
variable P1_PREVIEW names a folder outside the repository. Each script checks itself as it goes (chests that open,
the cells a person must reach, the corrupted waystone covering the clean one, the heartstone's wave rings left open,
the cache's platform puzzle, the tarns holding their water) and stops with a message if a check fails.
"""
import aurelite_dig
import heartstone
import hollowhorn_fold
import ridge_tower
import tarn_causeway
import watch_cloister
import wayfarer_cache
import waystone


def main():
    for script in (waystone, heartstone, wayfarer_cache, watch_cloister, tarn_causeway, ridge_tower, hollowhorn_fold,
                   aurelite_dig):
        script.main()


if __name__ == '__main__':
    main()
