#!/usr/bin/env python3
"""Milestone 7 identifier check (docs/MILESTONE_7.md 12.2). Run on CI.

Every Milestone 7 id must have what the game needs to show it:

  * each new item: its name (item.oldrepublic.<id>), an item definition (items/<id>.json) whose models, their parents
    and their textures all exist, and art of its own (a texture named after the id, or for a hilt part a model under
    models/item/lightsaber/); each hilt part also its tooltip line (item.oldrepublic.<id>.desc);
  * each new block: its name (block.oldrepublic.<id>), a block state whose models and textures exist, its item's
    definition and its loot table (data/oldrepublic/loot_table/blocks/<id>.json);
  * each hilt material: its name (saber.oldrepublic.material.<id>);
  * each sound event of tools/assets/sounds/events_m7.json: its sounds.json entry, its subtitle in the sounds lang part,
    its ModSounds constant and its audio files;
  * each item and block of registry/M7Items is registered there exactly once by its string id.

Phase 0 writes placeholders for all of it (tools/assets/gen_placeholders_m7.py: copies of the nearest existing art), so
the only gap before integration is the "art of its own" line for the weapons and mods, which render as their nearest
existing weapon or mod until WM and WB draw them. CI runs this with --report (it prints every gap and passes) until
integration, then without it (any gap fails).

Only stdlib; run from anywhere: python3 tools/check_m7_ids.py [--report]
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/oldrepublic"
DATA = ROOT / "src/main/resources/data/oldrepublic"
LANG_DIR = ROOT / "src/main/lang-parts"
SOUNDS_JSON = ASSETS / "sounds.json"
SOUNDS_LANG = LANG_DIR / "sounds.json"
EVENTS_M7 = ROOT / "tools/assets/sounds/events_m7.json"
MOD_SOUNDS = ROOT / "src/main/java/com/oldrepublic/registry/ModSounds.java"
M7_ITEMS_JAVA = ROOT / "src/main/java/com/oldrepublic/registry/M7Items.java"
NS = "oldrepublic"

PARTS = ["emitter_vanguard", "emitter_warden", "emitter_watchman", "emitter_penitent", "emitter_choir",
         "grip_banded", "grip_knurled", "grip_fluted", "grip_reliquary", "grip_watchman", "grip_penitent", "grip_choir",
         "pommel_ringed", "pommel_domed", "pommel_watchman", "pommel_penitent", "pommel_choir"]
ITEMS = ["raw_starfall", "starfall_ingot", "hymnsteel_ingot", "vibro_axe", "force_pike", "scatter_blaster", "blaster_carbine",
         "mod_starfall_edge", "mod_clan_iron_edge", "mod_aurelite_barrel", "mod_duskglass_cell"]
BLOCKS = ["starfall_ore", "deepslate_starfall_ore", "starfall_rock", "clan_iron_ore"]
MATERIALS = ["steel", "durasteel", "starfall", "aurelite", "duskglass", "sunstone", "clan_iron", "hymnsteel"]


def load_lang():
    keys = {}
    for f in sorted(LANG_DIR.glob("*.json")):
        for k in json.loads(f.read_text(encoding="utf-8")):
            keys[k] = f.name
    return keys


def split(ref):
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return ns, path


def model_refs(node, out):
    """Every model id an item definition names (plain models, and special models' base)."""
    if isinstance(node, dict):
        kind = node.get("type")
        if kind in ("minecraft:model", "model") and isinstance(node.get("model"), str):
            out.append(node["model"])
        if kind in ("minecraft:special", "special") and isinstance(node.get("base"), str):
            out.append(node["base"])
        for v in node.values():
            model_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            model_refs(v, out)
    return out


def blockstate_refs(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "model" and isinstance(v, str):
                out.append(v)
            else:
                blockstate_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            blockstate_refs(v, out)
    return out


def check_model(ref, gaps, what, seen=None):
    """The model {ref}, its parents and its textures exist (vanilla ones are assumed)."""
    seen = seen if seen is not None else set()
    ns, path = split(ref)
    if ns != NS or ref in seen:
        return
    seen.add(ref)
    f = ASSETS / f"models/{path}.json"
    if not f.exists():
        gaps.append(f"{what}: model {ref} is missing")
        return
    data = json.loads(f.read_text())
    if isinstance(data.get("parent"), str):
        check_model(data["parent"], gaps, what, seen)
    for tex in (data.get("textures") or {}).values():
        if isinstance(tex, str) and not tex.startswith("#"):
            tns, tpath = split(tex)
            if tns == NS and not (ASSETS / f"textures/{tpath}.png").exists():
                gaps.append(f"{what}: texture {tex} of {ref} is missing")


def check_item(item, lang, gaps, part=False, block=False):
    name = f"{'block' if block else 'item'}.{NS}.{item}"
    if name not in lang:
        gaps.append(f"{item}: no name ({name})")
    if part and f"item.{NS}.{item}.desc" not in lang:
        gaps.append(f"{item}: no tooltip line (item.{NS}.{item}.desc)")
    definition = ASSETS / f"items/{item}.json"
    if not definition.exists():
        gaps.append(f"{item}: no item definition (items/{item}.json)")
        return
    refs = model_refs(json.loads(definition.read_text()), [])
    if not refs:
        gaps.append(f"{item}: its item definition names no model")
    for ref in refs:
        check_model(ref, gaps, item)
    if block:
        return
    own = list((ASSETS / "textures/item").glob(f"{item}*.png")) + list((ASSETS / "textures/item").glob(f"*/{item}*.png"))
    if part:
        own += list((ASSETS / "models/item/lightsaber").glob(f"{item}*.json"))
    if not own:
        gaps.append(f"{item}: no art of its own yet (it renders as a placeholder)")


def check_block(block, lang, gaps):
    state = ASSETS / f"blockstates/{block}.json"
    if not state.exists():
        gaps.append(f"{block}: no block state")
    else:
        refs = blockstate_refs(json.loads(state.read_text()), [])
        if not refs:
            gaps.append(f"{block}: its block state names no model")
        for ref in refs:
            check_model(ref, gaps, block)
    check_item(block, lang, gaps, block=True)
    if not (DATA / f"loot_table/blocks/{block}.json").exists():
        gaps.append(f"{block}: no loot table")


def check_sounds(gaps):
    events = json.loads(EVENTS_M7.read_text())["events"]
    sounds = json.loads(SOUNDS_JSON.read_text())
    subtitles = json.loads(SOUNDS_LANG.read_text())
    java = MOD_SOUNDS.read_text()
    for ev in events:
        eid = ev["id"]
        entry = sounds.get(eid)
        if entry is None:
            gaps.append(f"sound {eid}: not in sounds.json (run gen_sounds.py --m7 --java)")
            continue
        if f"subtitles.{NS}.{eid}" not in subtitles:
            gaps.append(f"sound {eid}: no subtitle in lang-parts/sounds.json")
        if f'register("{eid}")' not in java:
            gaps.append(f"sound {eid}: no ModSounds constant")
        for s in entry.get("sounds", []):
            name = s["name"] if isinstance(s, dict) else s
            ns, path = split(name)
            if ns == NS and not (ASSETS / f"sounds/{path}.ogg").exists():
                gaps.append(f"sound {eid}: audio sounds/{path}.ogg is missing")
    return len(events)


def check_registrations(gaps):
    java = M7_ITEMS_JAVA.read_text()
    for item in ITEMS + BLOCKS:
        count = len(re.findall(r'(?:item|block|mod)\("' + re.escape(item) + r'"', java))
        if count != 1:
            gaps.append(f"{item}: registered {count} times in M7Items (expected once)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--report", action="store_true", help="print every gap and pass (until integration)")
    args = ap.parse_args()

    lang = load_lang()
    gaps = []
    for part in PARTS:
        check_item(part, lang, gaps, part=True)
    for item in ITEMS:
        check_item(item, lang, gaps)
    for block in BLOCKS:
        check_block(block, lang, gaps)
    for material in MATERIALS:
        if f"saber.{NS}.material.{material}" not in lang:
            gaps.append(f"material {material}: no name (saber.{NS}.material.{material})")
    sounds = check_sounds(gaps)
    check_registrations(gaps)

    total = len(PARTS) + len(ITEMS) + len(BLOCKS)
    for gap in gaps:
        print(("  gap: " if args.report else "  FAIL: ") + gap)
    print(f"check_m7_ids: {total} items and blocks, {len(MATERIALS)} materials, {sounds} sound events; {len(gaps)} gaps")
    if gaps and not args.report:
        sys.exit(1)


if __name__ == "__main__":
    main()
