#!/usr/bin/env python3
"""Milestone 7 placeholder art (docs/MILESTONE_7.md 12.2): every new item and block gets an item definition, a model, a
texture and (blocks) a block state and loot table, so no client test meets a missing model before its owner's art lands.

Each placeholder is a copy of the nearest existing mod art: a new hilt part shows the icon of an existing part, a new
weapon or mod renders as the weapon or mod it is closest to, a new ore wears an existing ore's texture. Only files that
do not exist yet are written, so running it again never touches an owner's real art. Owners replace these files with
their own (HC the part items, OR the ores and materials, WM and WB the weapons and mods).

Usage: python3 tools/assets/gen_placeholders_m7.py [--dry-run]
"""
import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/oldrepublic"
DATA = ROOT / "src/main/resources/data/oldrepublic"
NS = "oldrepublic"

# New hilt part item -> the existing part whose flat icon it borrows.
PARTS = {
    "emitter_vanguard": "emitter_guardian", "emitter_warden": "emitter_guardian", "emitter_watchman": "emitter_reliquary",
    "emitter_penitent": "emitter_duelist", "emitter_choir": "emitter_cantor",
    "grip_banded": "grip_ridged", "grip_knurled": "grip_ridged", "grip_fluted": "grip_wrapped", "grip_reliquary": "grip_wrapped",
    "grip_watchman": "grip_wrapped", "grip_penitent": "grip_ridged", "grip_choir": "grip_sunforged",
    "pommel_ringed": "pommel_balanced", "pommel_domed": "pommel_balanced", "pommel_watchman": "pommel_archivist",
    "pommel_penitent": "pommel_weighted", "pommel_choir": "pommel_hask",
}
# New plain item -> the existing item texture it borrows.
MATERIALS = {"raw_starfall": "clan_iron", "starfall_ingot": "durasteel_plate", "hymnsteel_ingot": "hymnstone_shard"}
# New weapon or mod -> the existing item it renders as until its owner's art lands (its item definition, copied).
LOOKALIKES = {
    "vibro_axe": "vibrosword", "force_pike": "electrostaff", "scatter_blaster": "blaster_pistol", "blaster_carbine": "blaster_rifle",
    "mod_starfall_edge": "mod_keen_edge", "mod_clan_iron_edge": "mod_shock_edge", "mod_aurelite_barrel": "mod_focused_barrel",
    "mod_duskglass_cell": "mod_cooling_cell",
}
# New block -> (the existing block texture it borrows, what it drops: "self" or an ore's (silk touch gives the block) item).
BLOCKS = {
    "starfall_ore": ("aurelite_ore", "oldrepublic:raw_starfall"),
    "deepslate_starfall_ore": ("deepslate_aurelite_ore", "oldrepublic:raw_starfall"),
    "starfall_rock": ("ruin_stone", "self"),
    "clan_iron_ore": ("crystal_veined_stone", "oldrepublic:clan_iron"),
}

written = []


def write_json(path, data, dry):
    if path.exists():
        return
    written.append(path)
    if not dry:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + "\n")


def copy(src, dst, dry):
    if dst.exists():
        return
    if not src.exists():
        raise SystemExit(f"placeholder source missing: {src.relative_to(ROOT)}")
    written.append(dst)
    if not dry:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)


def flat_item(item, texture_src, dry):
    copy(ASSETS / f"textures/item/{texture_src}.png", ASSETS / f"textures/item/{item}.png", dry)
    write_json(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}}, dry)
    write_json(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item}"}}, dry)


def ore_loot(block, drop):
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    entry = {"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": f"{NS}:{block}", "conditions": [silk]},
        {"type": "minecraft:item", "name": drop, "functions": [
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]},
    ]}
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}],
            "random_sequence": f"{NS}:blocks/{block}"}


def self_loot(block):
    return {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{block}"}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true", help="list what would be written")
    dry = ap.parse_args().dry_run

    for part, src in PARTS.items():
        flat_item(part, src, dry)
    for item, src in MATERIALS.items():
        flat_item(item, src, dry)
    for item, src in LOOKALIKES.items():
        source = ASSETS / f"items/{src}.json"
        if not source.exists():
            raise SystemExit(f"placeholder source missing: {source.relative_to(ROOT)}")
        write_json(ASSETS / f"items/{item}.json", json.loads(source.read_text()), dry)
    for block, (src, drop) in BLOCKS.items():
        copy(ASSETS / f"textures/block/{src}.png", ASSETS / f"textures/block/{block}.png", dry)
        write_json(ASSETS / f"models/block/{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{block}"}}, dry)
        write_json(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}}, dry)
        write_json(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}}, dry)
        write_json(DATA / f"loot_table/blocks/{block}.json", self_loot(block) if drop == "self" else ore_loot(block, drop), dry)

    for path in written:
        print(("would write " if dry else "wrote ") + str(path.relative_to(ROOT)))
    print(f"{len(written)} placeholder files {'to write' if dry else 'written'}")


if __name__ == "__main__":
    main()
