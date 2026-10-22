"""Builds worker H's Chapter Four hubs (docs/MILESTONE_6.md 5.7), one after the other:

    hub/greenwatch   greenwatch.py   the Greenwatch on Selvaren, template, anchors and site meta
    hub/proving      proving.py      the Proving on Varkhast, template, anchors and site meta (links the Sepulchre)
    hub/sepulchre    sepulchre.py    Zarvos's Sepulchre under the Proving, template and anchors

Run from the repository root: python3 tools/assets/structures/h/build_all.py
Every script is seeded, so two runs write byte-identical files. Previews are written only when the environment
variable H_PREVIEW names a folder outside the repository.
"""
import greenwatch
import proving
import sepulchre


def main():
    for script in (greenwatch, proving, sepulchre):
        script.main()


if __name__ == '__main__':
    main()
