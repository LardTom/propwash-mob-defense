import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np
from PIL import Image, ImageDraw

from mdstyle import itemview as iv
from mdstyle import paths, sheets

CONTEXTS = [
    ("gui", "GUI"),
    ("fp_right", "1. Person rechts"),
    ("fp_left", "1. Person links"),
    ("tp_right", "3. Person rechts"),
    ("ground", "Boden"),
    ("frame", "Item-Rahmen"),
]

PAIRS = [
    ("gripper", "dispenser_mount"),
    ("action_cam_heavy", "dispenser_mount"),
    ("led_strip", "spear_mount"),
    ("gripper", "spear_mount"),
    ("action_cam_light", "rocket_booster"),
    ("action_cam_flashback", "rocket_booster"),
]
SCALE_ROW = [("propwash", "gripper"), ("propwash", "led_strip"), ("propwash", "action_cam_light"),
             ("propwash", "action_cam_heavy"), (paths.NS, "dispenser_mount"), (paths.NS, "spear_mount"),
             (paths.NS, "rocket_booster")]

BG = (30, 32, 36, 255)
CELL_W, CELL_H = 224, 156
LABEL_W = 184
GAP = 6
CATEGORY = "accessory"


def render_cell(store, item_id, context):
    if context == "gui":
        cell = Image.new("RGBA", (CELL_W, CELL_H), (44, 47, 53, 255))
        gui = iv.view_gui(store, item_id, size=CELL_H - 12, gui_scale=8, category=CATEGORY)
        cell.alpha_composite(gui, ((CELL_W - gui.width) // 2, 6))
        return cell
    if context in ("fp_right", "fp_left"):
        return iv.view_first_person(store, item_id, CELL_W, CELL_H, left=context == "fp_left", category=CATEGORY)
    if context == "tp_right":
        return iv.view_third_person(store, item_id, CELL_W, CELL_H, category=CATEGORY)
    if context == "ground":
        return iv.view_ground(store, item_id, CELL_W, CELL_H, category=CATEGORY)
    return iv.view_item_frame(store, item_id, CELL_W, CELL_H, category=CATEGORY)


def scale_strip(store, entries, width, height=200):
    strip = Image.new("RGBA", (width, height), (38, 41, 46, 255))
    draw = ImageDraw.Draw(strip)
    models = []
    for label, item_id in entries:
        layer = iv.render_layer(store, item_id, CATEGORY)
        if layer is None:
            continue
        pts = iv.placed_points(store, layer.model_id, layer.local)
        if pts is None:
            continue
        extent = float(np.max(pts.max(axis=0) - pts.min(axis=0))) * 16.0 * 8.0
        models.append((label, layer, extent))
    if not models:
        sheets.label(strip, (10, 6), "kein Drohnen-Modell", 12, sheets.SUB)
        return strip
    biggest = max(m[2] for m in models)
    px_per_mm = min(3.0, (height - 50) / biggest * 1.15)
    x = 12
    for label, layer, extent in models:
        img = iv.true_scale(store, layer.model_id, px_per_mm, local=layer.local)
        if img.height > height - 30:
            img = img.resize((max(1, int(img.width * (height - 30) / img.height)), height - 30), Image.NEAREST)
        if x + img.width > width - 10:
            break
        strip.alpha_composite(img, (x, height - 22 - img.height))
        sheets.label(strip, (x, height - 19), f"{label} ({extent:.0f} mm)", 11, sheets.SUB)
        x += max(img.width, 168) + 22
    ruler = 50.0 * px_per_mm
    draw.line((width - 20 - ruler, 14, width - 20, 14), fill=sheets.ACCENT, width=2)
    sheets.label(strip, (int(width - 20 - ruler), 18), "50 mm", 11, sheets.ACCENT)
    sheets.label(strip, (10, 6), "gemeinsamer Maßstab (Drohnen-Modelle, echte Größe)", 12, sheets.SUB)
    return strip


def sheet(base, store, contexts):
    width = LABEL_W + len(contexts) * (CELL_W + GAP) + GAP
    height = 50 + len(PAIRS) * (2 * (CELL_H + GAP) + GAP) + 200 + 2 * GAP
    out = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(out)
    sheets.label(out, (GAP, 8), "Propwash gegen Mob Defense – Zubehör (Anzeige-Transformationen aus kit.compute_display "
                                "mit den Werten des Greifers)", 16)
    for i, (_, name) in enumerate(contexts):
        sheets.label(out, (LABEL_W + i * (CELL_W + GAP) + 4, 32), name, 12, sheets.SUB)
    y = 50
    for base_key, md_key in PAIRS:
        rows = [("Propwash", base, f"propwash:{base_key}"), ("Mob Defense", store, f"{paths.NS}:{md_key}")]
        for label, st, item_id in rows:
            sheets.label(out, (GAP, y + 6), label, 13)
            sheets.label(out, (GAP, y + 24), item_id.split(":", 1)[1], 12, sheets.SUB)
            if st.json(item_id, "items") is None:
                sheets.label(out, (LABEL_W, y + 40), "Item fehlt", 12, sheets.ACCENT)
            else:
                for i, (context, _) in enumerate(contexts):
                    out.alpha_composite(render_cell(st, item_id, context), (LABEL_W + i * (CELL_W + GAP), y))
            y += CELL_H + GAP
        draw.line((GAP, y, width - GAP, y), fill=(60, 64, 72, 255), width=1)
        y += GAP
    entries = [(key, f"{ns}:{key}") for ns, key in SCALE_ROW]
    out.alpha_composite(scale_strip(store, entries, width - 2 * GAP), (GAP, y + GAP))
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Vergleichsbild Propwash-Zubehör gegen Mob Defense: GUI, erste Person "
                                                 "(beide Hände), dritte Person, Boden, Item-Rahmen und ein Streifen im "
                                                 "gemeinsamen Maßstab.")
    parser.add_argument("--assets", default=str(paths.ASSETS_ROOT), help="assets-Ordner von Mob Defense")
    parser.add_argument("--propwash-assets", help="Propwash-Assets (Ordner oder Jar), Standard: propwash-common aus mavenLocal")
    parser.add_argument("--out", default=str(paths.SHEETS), help="Zielordner der PNGs (Standard: docs/asset-sheets)")
    parser.add_argument("--contexts", help="Auswahl, z. B. gui,fp_right,ground")
    parser.add_argument("--minecraft-assets", help="Vanilla-Texturen (Ordner oder Client-Jar), Standard: Client-Jar aus dem Gradle-Cache")
    args = parser.parse_args(argv)
    pw = paths.propwash_assets(args.propwash_assets)
    base = iv.AssetStore([pw])
    store = iv.AssetStore([Path(args.assets), pw, paths.minecraft_assets(args.minecraft_assets)])
    contexts = CONTEXTS
    if args.contexts:
        wanted = args.contexts.split(",")
        contexts = [c for c in CONTEXTS if c[0] in wanted]
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    file = out_dir / "style_compare_accessory.png"
    sheet(base, store, contexts).convert("RGB").save(file, optimize=True)
    print(f"{file} ({len(PAIRS)} Paare)")


if __name__ == "__main__":
    main()
