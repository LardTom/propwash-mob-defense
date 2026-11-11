import argparse
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np
from PIL import Image

from mdstyle import parts, paths, sheets, templates, writer

import kit
import layout
import paintable
import render

BG = sheets.BG
CELL = 240
VIEWS = {
    "schräg": (30, 225, 0),
    "vorn": (12, 180, 0),
    "seite": (8, 270, 0),
    "unten": (-35, 200, 0),
}
DRONE_VIEWS = {
    "schräg": (28, 215, 0),
    "seite": (6, 270, 0),
    "vorn": (10, 180, 0),
    "unten": (-30, 205, 0),
}
SCHEMES = [
    ("Standard", None),
    ("rot / grau", {"tpu": "#c0392b", "accessory": "#8d949c"}),
    ("blau / orange", {"tpu": "#2f6fd0", "accessory": "#e08a1e"}),
    ("weiß / schwarz", {"tpu": "#e6e6e6", "accessory": "#1c1d20"}),
]
FRAMES = ("freestyle_5", "race_5", "long_range_7")
MOUNT = {"front": "dispenser_mount", "bottom": "spear_mount", "top": "rocket_booster"}


def extract_propwash(source, target):
    source = Path(source)
    if source.is_dir():
        return source
    target = Path(target)
    stamp = target / ".quelle"
    marker = f"{source} {source.stat().st_mtime_ns}"
    if stamp.exists() and stamp.read_text(encoding="utf-8") == marker:
        return target
    with zipfile.ZipFile(source) as jar:
        for name in jar.namelist():
            if name.startswith("assets/propwash/") and not name.endswith("/"):
                out = target / name[len("assets/"):]
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(jar.read(name))
    stamp.write_text(marker, encoding="utf-8")
    return target


def extract_vanilla(source, target):
    source = Path(source)
    if source.is_dir():
        return source
    target = Path(target)
    with zipfile.ZipFile(source) as jar:
        for sprite in sorted(writer.VANILLA_TEXTURES):
            ns, path = sprite.split(":", 1)
            name = f"assets/{ns}/textures/{path}.png"
            out = target / name[len("assets/"):]
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(jar.read(name))
    return target


def use_assets(roots):
    roots = [Path(r) for r in roots]

    def find(ns, rel):
        for root in roots:
            file = root / ns / rel
            if file.exists():
                return file
        raise FileNotFoundError(f"{ns}:{rel}")

    def texture(sprite_id):
        if isinstance(sprite_id, dict):
            sprite_id = sprite_id["sprite"]
        ns, path = sprite_id.split(":", 1)
        file = find(ns, f"textures/{path}.png")
        if file not in render._texture_cache:
            render._texture_cache[file] = np.asarray(Image.open(file).convert("RGBA"), dtype=np.float64)
        return render._texture_cache[file]

    def model_path(model_id):
        ns, path = model_id.split(":", 1)
        return find(ns, f"models/{path}.json")

    render.texture = texture
    render.model_path = model_path
    return find


def tints_for(groups, scheme):
    out = []
    for channel, base in groups:
        colour = base if scheme is None else scheme.get(channel, base)
        out.append(paintable.tint(colour, channel))
    return out


def cell(quads, label, rotation, size=CELL, translucent=False):
    canvas = render.Canvas(size, size, BG)
    pts = np.concatenate([q[0] for q in quads])
    lo, hi = (pts.min(axis=0) - kit.ANCHOR) * kit.UNIT_MM, (pts.max(axis=0) - kit.ANCHOR) * kit.UNIT_MM
    scale = (size * 0.78) / max(float(np.linalg.norm(hi - lo)), 1e-3)
    tf = render.ViewTransform(rotation, scale, (size / 2, size / 2 + 4), pivot=(lo + hi) / 2)
    canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    sheets.label(img, (6, size - 17), label, 12)
    return img


def part_sheet(root):
    paint = json.loads((Path(root) / paths.NS / "drone_paint.json").read_text(encoding="utf-8"))["models"]
    rows = []
    for key in templates.KEYS:
        model_id = f"{paths.NS}:part/accessory/{key}"
        quads, translucent, _ = render.load_model(model_id)
        cells = [cell(quads, f"{key} {view}", rot, translucent=translucent) for view, rot in VIEWS.items()]
        groups = [(g["channel"], g["base"]) for g in paint[f"accessory/{key}"]]
        for name, scheme in SCHEMES:
            painted, _, _ = render.load_model(model_id + paintable.SUFFIX, tints=tints_for(groups, scheme))
            cells.append(cell(painted, f"Lack: {name}", VIEWS["schräg"]))
        rows.append(sheets.grid(cells, len(VIEWS) + len(SCHEMES)))
    title = ("Mob Defense – Teile aus tools/assets/style_generate.py: vier Ansichten und die Lack-Variante _paint "
             "(Kanäle tpu und accessory wie der Greifer) mit vier Farbschemata")
    return sheets.stack([sheets.heading(rows[0].width, title)] + rows + [icon_row(root, rows[0].width)])


def icon_row(root, width):
    row = Image.new("RGBA", (width, 300), BG)
    file = Path(root) / paths.NS / "icon.png"
    if not file.exists():
        sheets.label(row, (8, 8), "icon.png fehlt", 13, sheets.ACCENT)
        return row
    icon = Image.open(file).convert("RGBA")
    row.alpha_composite(icon, (16, 140))
    row.alpha_composite(icon.resize((icon.width * 2, icon.height * 2), Image.NEAREST), (260, 30))
    sheets.label(row, (16, 30), "Mod-Icon icon.png", 13)
    sheets.label(row, (16, 50), f"{icon.width} × {icon.height}, links 1:1, rechts 2:1", 12, sheets.SUB)
    return row


def placement(offset, tilt=0.0, stretch=1.0):
    rot = kit.element_matrix(tilt, 0.0, 0.0) @ np.diag([stretch, 1.0, 1.0])
    return rot, np.array(offset, dtype=float)


def load(model_id):
    quads, translucent, _ = render.load_model(model_id)
    return quads, translucent


def drone_pieces(frame_key, layout_json, straight, mounts=("front", "bottom", "top")):
    f = layout_json["frames"][frame_key]
    stack_key, motor, prop, video, battery = layout.FRAMES[frame_key]["preset"]
    pieces = [(load(f"propwash:part/frame/{frame_key}"), placement((0, 0, 0)))]
    pieces.append((load(f"propwash:part/stack/{stack_key}"), placement(f["stack"])))
    under = layout.battery_under(frame_key)
    battery_id = f"propwash:part/battery/{battery}" + ("_under" if under else "")
    try:
        pieces.append((load(battery_id), placement(f["battery"])))
    except FileNotFoundError:
        pieces.append((load(f"propwash:part/battery/{battery}"), placement(f["battery"])))
    pieces.append((load(f"propwash:part/video/{video}"), placement(f["video"], f.get("camera_tilt_default", 0.0))))
    for pos, spin in zip(f["motors"], f["motor_spin"]):
        pieces.append((load(f"propwash:part/motor/{motor}"), placement(pos)))
        top = [pos[0], pos[1] + f["prop_height"], pos[2]]
        pieces.append((load(f"propwash:part/prop/{prop}" + ("_ccw" if spin == "ccw" else "")), placement(top)))
    defs = {key: d for _, key, d, _ in parts.load()}
    for mount, anchor in f.get("accessories", {}).items():
        key = MOUNT.get(mount)
        if key is None or mount not in mounts:
            continue
        w = parts.size_of(defs[key])[0]
        tilt = 0.0 if straight else float(anchor.get("tilt", 0.0))
        length = float(anchor.get("length", 0.0))
        stretch = 1.0
        if mount == "bottom" and not straight and length > 0.0:
            stretch = length / w
        if mount == "top":
            pieces.append((load("propwash:part/accessory/mount_top"), placement(anchor["position"])))
        pieces.append((load(f"{paths.NS}:part/accessory/{key}"), placement(anchor["position"], tilt, stretch)))
    return pieces


def drone_cell(pieces, label, rotation, size=380, focus=None, span=None):
    canvas = render.Canvas(size, size, BG)
    pts = []
    for (quads, _), (rot, offset) in pieces:
        corners = np.concatenate([q[0] for q in quads])
        mm = (corners - kit.ANCHOR) * kit.UNIT_MM
        pts.append(mm @ rot.T + offset)
    pts = np.concatenate(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    pivot = (lo + hi) / 2 if focus is None else np.array(focus, dtype=float)
    reach = float(np.linalg.norm(hi - lo)) if span is None else float(span)
    scale = (size * 0.86) / max(reach, 1e-3)
    for (quads, translucent), place in pieces:
        tf = render.ViewTransform(rotation, scale, (size / 2, size / 2), placement=place, pivot=pivot)
        canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    sheets.label(img, (6, size - 17), label, 12)
    return img


def focus_of(layout_json, frame_key, mount, key):
    anchor = layout_json["frames"][frame_key]["accessories"][mount]["position"]
    lo, hi = parts.box_mm({k: d for _, k, d, _ in parts.load()}[key])
    centre = [(a + b) / 2 for a, b in zip(lo, hi)]
    return [anchor[i] + centre[i] for i in range(3)]


def drone_sheet(find):
    layout_json = json.loads(find("propwash", "drone_layout.json").read_text(encoding="utf-8"))
    cells = []
    for frame_key in FRAMES:
        pieces = drone_pieces(frame_key, layout_json, straight=False)
        for view, rot in DRONE_VIEWS.items():
            cells.append(drone_cell(pieces, f"{frame_key} {view}", rot))
    for mount, key, views in (("bottom", "spear_mount", (("seite", (6, 270, 0)), ("unten", (-40, 215, 0)))),
                              ("top", "rocket_booster", (("seite", (6, 270, 0)), ("schräg", (28, 215, 0))))):
        focus = focus_of(layout_json, "freestyle_5", mount, key)
        for straight in (False, True):
            pieces = drone_pieces("freestyle_5", layout_json, straight=straight, mounts=(mount,))
            note = "gerade" if straight else "wie Propwash heute"
            for view, rot in views:
                cells.append(drone_cell(pieces, f"{key} {view}, {note}", rot, focus=focus, span=160.0))
    title = ("Mob Defense an Propwash-Rahmen: Dispenser vorn, Speer-Halterung unten, Raketen-Booster oben auf dem Sockel "
             "mount_top; Zeilen 1–3 so, wie Propwash heute zeichnet.\nZeilen 4–5: Nahaufnahmen am freestyle_5, links wie "
             "Propwash heute (Add-on-Zubehör geneigt: unten 30° und in x gestreckt, oben 22°), rechts gerade.")
    return sheets.grid(cells, len(DRONE_VIEWS), title)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Vorschaubilder von Mob Defense mit dem Software-Rasterer der Basis: "
                                                 "jedes Teil aus vier Richtungen samt Lack-Variante und an Propwash-Rahmen.")
    parser.add_argument("--assets", default=str(paths.ASSETS_ROOT), help="assets-Ordner (Standard: Mod-Ressourcen)")
    parser.add_argument("--propwash-assets", help="Propwash-Assets (Ordner oder Jar), Standard: propwash-common aus mavenLocal")
    parser.add_argument("--minecraft-assets", help="Vanilla-Texturen (Ordner oder Client-Jar), Standard: Client-Jar aus dem Gradle-Cache")
    parser.add_argument("--out", default=str(paths.SHEETS), help="Zielordner der PNGs (Standard: docs/asset-sheets)")
    args = parser.parse_args(argv)
    pw = extract_propwash(paths.propwash_assets(args.propwash_assets), paths.BUILD / "style-preview" / "propwash-assets")
    mc = extract_vanilla(paths.minecraft_assets(args.minecraft_assets), paths.BUILD / "style-preview" / "minecraft-assets")
    find = use_assets([args.assets, pw, mc])
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for name, img in (("mobdefense_parts.png", part_sheet(args.assets)), ("mobdefense_on_drone.png", drone_sheet(find))):
        img.convert("RGB").save(out / name, optimize=True)
        print(out / name)


if __name__ == "__main__":
    main()
