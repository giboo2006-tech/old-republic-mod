"""structlib: the shared library for the structure templates of the structures overhaul.

Import from a generator script in tools/assets with:

    import sys, os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from structlib import Template, S, shapes, nature, palettes

See docs/STRUCTURES.md for the rules every template follows, and run
``python3 tools/assets/structlib/preview.py <file.nbt> -o out.png`` to look at a result.
"""
from .blocks import S, AIR, CAVE_AIR, parse, rotate, mirror_x, mirror_z, with_props  # noqa: F401
from .template import Template  # noqa: F401
