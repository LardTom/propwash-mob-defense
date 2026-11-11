import hashlib
import json
import re
from pathlib import Path

from PIL import Image

from . import display, icon, palette, parts, paths, templates, writer

import kit
import paintable

CONTEXTS = ("gui", "ground", "fixed", "on_shelf", "thirdperson_righthand", "firstperson_righthand", "head")
CATEGORY = "accessory"
BOX_TOLERANCE_MM = 1.5
HINGE_FINGER_MM = -3.0
MIN_DENSITY = 3.0
GENERATOR_FILES = ("booster.py", "common.py", "decals.py", "dispenser.py", "display.py", "icon.py", "palette.py",
                   "parts.py", "spear.py", "templates.py", "writer.py")
READS_IMAGES = re.compile(r"Image\.open|imread|\.read_bytes\(\)|zipfile")


def split(resource_id):
    if ":" not in resource_id:
        return "minecraft", resource_id
    ns, path = resource_id.split(":", 1)
    return ns, path


def load(file):
    return json.loads(Path(file).read_text(encoding="utf-8"))


def model_refs(node, out):
    if isinstance(node, dict):
        if node.get("type", "").endswith("model") and isinstance(node.get("model"), str):
            out.append(node["model"])
        if node.get("type", "").endswith("special") and isinstance(node.get("base"), str):
            out.append(node["base"])
        for value in node.values():
            model_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            model_refs(value, out)


def close(a, b, tol=2e-3):
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(close(a[k], b[k], tol) for k in a)
    if isinstance(a, (list, tuple)) and isinstance(b, (list, tuple)):
        return len(a) == len(b) and all(close(x, y, tol) for x, y in zip(a, b))
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return abs(float(a) - float(b)) <= tol
    return a == b


def vanilla_face(data, face):
    ref = face.get("texture", "")
    sprite = data.get("textures", {}).get(ref[1:]) if ref.startswith("#") else ref
    return sprite in writer.VANILLA_TEXTURES


def texel_density(data, texture_px):
    values = []
    for el in data.get("elements", []):
        frm, to = el["from"], el["to"]
        for direction, face in el.get("faces", {}).items():
            if vanilla_face(data, face):
                continue
            w, h = kit.face_size(frm, to, direction)
            uv = face.get("uv")
            if not uv or w < 0.25:
                continue
            px = abs(uv[2] - uv[0]) * texture_px / 16.0
            if face.get("rotation") in (90, 270):
                px = abs(uv[3] - uv[1]) * texture_px / 16.0
            factor = 0.5 if direction == "down" else 1.0
            values.append(px / (w * factor))
    values.sort()
    return values[len(values) // 2] if values else 0.0, values[-1] if values else 0.0


def check_models(assets_root, namespace, used_textures):
    root = Path(assets_root) / namespace
    folder = root / "models"
    problems = []
    ref = display.gripper()
    for file in sorted(folder.rglob("*.json")) if folder.is_dir() else []:
        rel = file.relative_to(folder).with_suffix("").as_posix()
        data = load(file)
        if not rel.startswith(f"part/{CATEGORY}/"):
            problems.append(f"models/{rel}: liegt außerhalb models/part/{CATEGORY}/ (der Generator schreibt nur Teilmodelle)")
        parent = data.get("parent")
        if parent and split(parent)[0] not in ("minecraft", namespace):
            problems.append(f"models/{rel}: fremdes Eltern-Modell {parent}")
        if data.get("credit") != writer.CREDIT:
            problems.append(f"models/{rel}: credit fehlt oder fremd ({data.get('credit')})")
        for el in data.get("elements", []):
            values = el["from"] + el["to"]
            if min(values) < display.MODEL_MIN or max(values) > display.MODEL_MAX:
                problems.append(f"models/{rel}: Element {el.get('name')} außerhalb {display.MODEL_MIN:g}…{display.MODEL_MAX:g}")
        size = data.get("texture_size", [16, 16])
        for key, sprite in data.get("textures", {}).items():
            if isinstance(sprite, dict):
                sprite = sprite["sprite"]
            if sprite.startswith("#"):
                continue
            ns, path = split(sprite)
            if sprite in writer.VANILLA_TEXTURES:
                if (Path(assets_root) / ns).exists():
                    problems.append(f"models/{rel}: {sprite} liegt als Datei in den Ressourcen (nur Verweis, keine Kopie)")
                continue
            if ns != namespace:
                problems.append(f"models/{rel}: fremde Textur {sprite} (nur eigene, erzeugte Texturen und Verweise auf "
                                f"{', '.join(sorted(writer.VANILLA_TEXTURES))})")
                continue
            used_textures.add(path)
            png = Path(assets_root) / ns / "textures" / f"{path}.png"
            if not png.exists():
                problems.append(f"models/{rel}: Textur {sprite} fehlt")
                continue
            with Image.open(png) as img:
                if img.size != (ref["tex"], ref["tex"]):
                    problems.append(f"models/{rel}: Textur {sprite} ist {img.size[0]}×{img.size[1]} "
                                    f"(der Greifer hat {ref['tex']}×{ref['tex']})")
                if list(img.size) != list(size):
                    problems.append(f"models/{rel}: texture_size {size} passt nicht zu {img.size}")
        shown = data.get("display", {})
        for context in CONTEXTS:
            if context not in shown:
                problems.append(f"models/{rel}: display.{context} fehlt")
            elif max(abs(s) for s in shown[context].get("scale", [1, 1, 1])) > 4.0:
                problems.append(f"models/{rel}: display.{context}.scale über 4")
        if data.get("gui_light") != "side":
            problems.append(f"models/{rel}: gui_light ist nicht side")
        key = rel.rsplit("/", 1)[-1]
        expected, _ = display.recomputed(data, CATEGORY, key)
        if not close(shown, expected):
            problems.append(f"models/{rel}: Anzeige-Transformationen weichen von kit.compute_display mit den Werten des "
                            f"Greifers ab (GUI {ref['gui']}, Füllung {ref['gui_fill']})")
        median, top = texel_density(data, size[0])
        if top > ref["density"] * 1.05:
            problems.append(f"models/{rel}: Texeldichte {top:.2f} über der des Greifers ({ref['density']:g})")
        if median < MIN_DENSITY:
            problems.append(f"models/{rel}: Texeldichte {median:.2f} unter {MIN_DENSITY:g} Texel je 8 mm")
    return problems


def check_items(assets_root, namespace):
    root = Path(assets_root) / namespace
    folder = root / "items"
    problems = []
    for file in sorted(folder.rglob("*.json")) if folder.is_dir() else []:
        rel = file.relative_to(folder).with_suffix("").as_posix()
        data = load(file)
        refs = []
        model_refs(data.get("model", {}), refs)
        if not refs:
            problems.append(f"items/{rel}: kein Modell")
        for ref in refs:
            ns, path = split(ref)
            if ns != namespace:
                problems.append(f"items/{rel}: fremdes Modell {ref}")
            elif not (Path(assets_root) / ns / "models" / f"{path}.json").exists():
                problems.append(f"items/{rel}: Modell {ref} fehlt")
    return problems


def check_parts(assets_root, namespace, parts_dir=paths.PARTS):
    root = Path(assets_root) / namespace
    problems = []
    paint_file = root / "drone_paint.json"
    paint_models = load(paint_file).get("models", {}) if paint_file.exists() else {}
    for name in parts.unknown(parts_dir):
        problems.append(f"{CATEGORY}/{name}: Teil-Definition ohne Vorlage in mdstyle.templates")
    for category, key, definition, source in parts.load(parts_dir):
        model_id = f"{namespace}:part/{category}/{key}"
        model_file = root / "models/part" / category / f"{key}.json"
        item_file = root / "items" / f"{key}.json"
        render_file = root / "items/drone_render" / category / f"{key}.json"
        paint_render = root / "items/drone_render" / category / f"{key}_paint.json"
        paint_model = root / "models/part" / category / f"{key}_paint.json"
        for file, label in ((model_file, "Modell"), (item_file, "Item-Modell (Inventar-Symbol)"),
                            (render_file, "Render-Definition für Drohne und Web-Export"),
                            (paint_render, "Render-Definition der Lack-Variante"), (paint_model, "Lack-Modell")):
            if not file.exists():
                problems.append(f"{category}/{key}: {label} {file.relative_to(root).as_posix()} fehlt")
        if not model_file.exists():
            continue
        data = load(model_file)
        _, pre = display.recomputed(data, category, key)
        if item_file.exists() and not close(load(item_file), kit.item_definition(model_id, pre)):
            problems.append(f"{category}/{key}: items/{key}.json weicht von kit.item_definition ab (Vor-Skalierung {pre})")
        if render_file.exists() and load(render_file) != {"model": kit.model_node(model_id)}:
            problems.append(f"{category}/{key}: Render-Definition zeigt nicht auf {model_id}")
        if paint_model.exists() and not close(load(paint_model).get("display"), data.get("display")):
            problems.append(f"{category}/{key}: Lack-Modell hat andere Anzeige-Transformationen")
        groups = paint_models.get(f"{category}/{key}")
        if groups is None:
            problems.append(f"{category}/{key}: kein Eintrag in drone_paint.json (nicht lackierbar)")
        else:
            found = [(g["channel"], g["base"]) for g in groups]
            channels = sorted(g[0] for g in found)
            if channels != ["accessory", "tpu"] or not set(found) <= palette.PAINT_GROUPS:
                problems.append(f"{category}/{key}: Lack-Gruppen {found}, erwartet je eine aus {sorted(palette.PAINT_GROUPS)} "
                                f"in den Kanälen tpu und accessory")
            if paint_render.exists():
                tints = [paintable.tint(g["base"], g["channel"]) for g in groups]
                if load(paint_render) != {"model": kit.model_node(f"{model_id}_paint", tints)}:
                    problems.append(f"{category}/{key}: Render-Definition der Lack-Variante ohne passende Tönung")
        lo, hi = display.extent_mm(data)
        try:
            box_lo, box_hi = parts.box_mm(definition)
        except ValueError as e:
            problems.append(f"{category}/{key}: {e}")
            continue
        floor = list(box_lo)
        if parts.slot_of(definition) == "top":
            floor[1] = HINGE_FINGER_MM
        for axis, name in enumerate("xyz"):
            if lo[axis] < floor[axis] - BOX_TOLERANCE_MM or hi[axis] > box_hi[axis] + BOX_TOLERANCE_MM:
                problems.append(f"{category}/{key}: Modell {name} {lo[axis]:.1f}…{hi[axis]:.1f} mm außerhalb der Maße "
                                f"{floor[axis]:.1f}…{box_hi[axis]:.1f} mm ({source})")
        size = parts.size_of(definition)
        for axis, name in enumerate(("Breite", "Höhe", "Tiefe")):
            span = hi[axis] - lo[axis]
            if axis == 1 and parts.slot_of(definition) == "top":
                span = hi[axis] - max(lo[axis], parts.HINGE_CLEARANCE_MM)
            if span < 0.85 * size[axis]:
                problems.append(f"{category}/{key}: {name} {span:.1f} mm, die Definition sagt {size[axis]:.1f} mm")
    as_tuples = {name: [(g["channel"], g["base"]) for g in entries] for name, entries in paint_models.items()}
    problems += paintable.check_references(as_tuples)
    for name in paint_models:
        if not (root / "items/drone_render" / f"{name}_paint.json").exists():
            problems.append(f"drone_paint.json: {name} ohne Render-Definition {name}_paint")
    return problems


def check_stray_textures(assets_root, namespace, used):
    root = Path(assets_root) / namespace / "textures"
    problems = []
    for png in sorted(root.rglob("*.png")) if root.is_dir() else []:
        rel = png.relative_to(root).with_suffix("").as_posix()
        if rel not in used:
            problems.append(f"textures/{rel}.png: von keinem Modell benutzt (nur erzeugte Texturen)")
    return problems


def check_sounds(assets_root, namespace):
    root = Path(assets_root) / namespace
    problems = []
    if (root / "sounds.json").exists():
        problems.append("sounds.json: Mob Defense nutzt nur Vanilla-Sound-Events (design.md §5), keine eigene Anmeldung")
    folder = root / "sounds"
    if folder.is_dir() and any(folder.rglob("*")):
        problems.append("sounds/: eigene Sound-Dateien gefunden, erlaubt sind nur Vanilla-Sound-Events")
    return problems


def check_icon(file=paths.ICON):
    file = Path(file)
    if not file.exists():
        return [f"{file.name}: fehlt"]
    problems = []
    data = file.read_bytes()
    if hashlib.sha256(data).hexdigest() == icon.PLACEHOLDER_SHA256:
        problems.append(f"{file.name}: ist noch der Platzhalter aus dem Gerüst")
    with Image.open(file) as img:
        if img.size != (icon.SIZE, icon.SIZE):
            problems.append(f"{file.name}: {img.size[0]}×{img.size[1]} statt {icon.SIZE}×{icon.SIZE}")
    return problems


def check_generator_pixels():
    problems = []
    for name in GENERATOR_FILES:
        file = paths.HERE / name
        if file.exists() and READS_IMAGES.search(file.read_text(encoding="utf-8")):
            problems.append(f"mdstyle/{name}: liest Bild- oder Archivdateien (Pixel nur aus den Malern, nichts kopiert)")
    return problems


def check(assets_root, namespace=paths.NS, full=True, icon_file=paths.ICON):
    used = set()
    problems = check_models(assets_root, namespace, used)
    problems += check_items(assets_root, namespace)
    if full:
        problems += check_parts(assets_root, namespace)
        problems += check_stray_textures(assets_root, namespace, used)
        problems += check_sounds(assets_root, namespace)
        if icon_file is not None:
            problems += check_icon(icon_file)
    problems += check_generator_pixels()
    return problems


def read_propwash(source, rel):
    source = Path(source)
    if source.is_dir():
        file = source / "propwash" / rel
        return file.read_text(encoding="utf-8") if file.exists() else None
    import zipfile as archive
    with archive.ZipFile(source) as jar:
        try:
            return jar.read(f"assets/propwash/{rel}").decode("utf-8")
        except KeyError:
            return None


def check_reference(source):
    model = read_propwash(source, "models/part/accessory/gripper.json")
    item = read_propwash(source, "items/gripper.json")
    if model is None or item is None:
        return [f"Greifer in {source} nicht gefunden (Vergleich der Anzeige-Transformationen nicht möglich)"]
    data = json.loads(model)
    expected, pre = display.recomputed(data, CATEGORY, "gripper")
    problems = []
    if not close(data.get("display"), expected):
        problems.append("propwash:gripper: kit.compute_display mit den Greifer-Werten ergibt nicht mehr dessen Anzeige "
                        "(Kopie und Propwash-Stand passen nicht zusammen)")
    if not close(json.loads(item), kit.item_definition("propwash:part/accessory/gripper", pre)):
        problems.append("propwash:gripper: kit.item_definition ergibt nicht mehr dessen Item-Modell")
    return problems


def vendored_intact():
    problems = []
    sums = paths.STYLE / "SHA256SUMS"
    if not sums.exists():
        return ["propwash_style/SHA256SUMS fehlt"]
    listed = set()
    for line in sums.read_text(encoding="utf-8").splitlines():
        digest, name = line.split(None, 1)
        name = name.strip()
        listed.add(name)
        file = paths.STYLE / name
        if not file.exists():
            problems.append(f"propwash_style/{name} fehlt")
        elif hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            problems.append(f"propwash_style/{name} weicht von der Propwash-Quelle ab (Kopie muss unverändert bleiben)")
    for file in sorted(paths.STYLE.glob("*.py")):
        if file.name not in listed:
            problems.append(f"propwash_style/{file.name} steht nicht in SHA256SUMS")
    return problems


def owned_tree(root, namespace, keys):
    base = Path(root) / namespace
    names = set()
    for file in writer.owned_files(root, namespace, keys):
        names.add(file.relative_to(base).as_posix())
    for folder in ("models/part", "textures/item/part", "textures/block/part", "items/drone_render"):
        if (base / folder).is_dir():
            names.update(p.relative_to(base).as_posix() for p in (base / folder).rglob("*") if p.is_file())
    for name in ("drone_paint.json", "icon.png"):
        if (base / name).exists():
            names.add(name)
    return names


def same_tree(generated, committed, namespace=paths.NS, keys=templates.KEYS):
    problems = []
    a_root = Path(generated) / namespace
    b_root = Path(committed) / namespace
    names = owned_tree(generated, namespace, keys) | owned_tree(committed, namespace, keys)
    for name in sorted(names):
        a, b = a_root / name, b_root / name
        if not a.exists():
            problems.append(f"{name}: liegt in den Ressourcen, erzeugt der Generator aber nicht")
        elif not b.exists():
            problems.append(f"{name}: erzeugt, fehlt in den Ressourcen (style_generate.py --write)")
        elif a.read_bytes() != b.read_bytes():
            problems.append(f"{name}: weicht vom Generator ab")
    return problems
