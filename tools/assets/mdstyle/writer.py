from pathlib import Path

from . import common, dispenser, display, palette, paths

import addon
import kit
import paint
import paintable

CREDIT = "Propwash: Mob Defense - lardtom - tools/assets/style_generate.py"
CATEGORY = "accessory"
VANILLA_FACES = {"dispenser_mount": dispenser.VANILLA_FACES}
VANILLA_TEXTURES = frozenset(sprite for faces in VANILLA_FACES.values() for sides in faces.values() for _, sprite in sides.values())
VANILLA_UV = [0, 0, 16, 16]
ITEM_ROOT = kit.TEXTURE_ROOT + "/"
BLOCK_ROOT = "block/part/"


def owned_files(root, namespace, keys):
    base = Path(root) / namespace
    out = []
    for key in keys:
        out += sorted((base / "models/part" / CATEGORY).glob(f"{key}*.json")) if (base / "models/part" / CATEGORY).is_dir() else []
        out += sorted((base / "textures/item/part" / CATEGORY).glob(f"{key}*.png")) if (base / "textures/item/part" / CATEGORY).is_dir() else []
        out += sorted((base / "textures/block/part" / CATEGORY).glob(f"{key}*.png")) if (base / "textures/block/part" / CATEGORY).is_dir() else []
        out += sorted((base / "items/drone_render" / CATEGORY).glob(f"{key}*.json")) if (base / "items/drone_render" / CATEGORY).is_dir() else []
        if (base / "items" / f"{key}.json").exists():
            out.append(base / "items" / f"{key}.json")
    return out


def face_materials(model):
    names = set()
    for el in model.elements:
        for fs in el.faces.values():
            if isinstance(fs.material, str):
                names.add(fs.material)
    return names


def palette_problems(model):
    problems = []
    for name in sorted(face_materials(model)):
        if name not in paint.MATERIALS:
            problems.append(f"{model.key}: Material {name} ist nicht angemeldet")
    groups = set()
    for el in model.elements:
        for fs in el.faces.values():
            found = paintable.rule(model, fs)
            if found is not None:
                groups.add(found)
    for group in sorted(groups - palette.PAINT_GROUPS):
        problems.append(f"{model.key}: Lack-Gruppe {group} nicht in der Palette {sorted(palette.PAINT_GROUPS)}")
    for channel in ("tpu", "accessory"):
        count = sum(1 for g in groups if g[0] == channel)
        if count != 1:
            problems.append(f"{model.key}: {count} Lack-Gruppen im Kanal {channel}, erwartet genau eine "
                            f"(lackierbar wie der Greifer: Gehäuse tpu, Zubehör accessory)")
    return problems


def base_key(model_id):
    key = model_id.rsplit("/", 1)[-1]
    return key[:-len(paintable.SUFFIX)] if key.endswith(paintable.SUFFIX) else key


def atlas_sprite(sprite):
    if not isinstance(sprite, str) or ":" not in sprite:
        return sprite
    ns, path = sprite.split(":", 1)
    if ns != kit.NAMESPACE or not path.startswith(ITEM_ROOT):
        return sprite
    if base_key(path) not in VANILLA_FACES:
        return sprite
    return f"{ns}:{BLOCK_ROOT}{path[len(ITEM_ROOT):]}"


def with_vanilla_faces(model_id, data):
    faces = VANILLA_FACES.get(base_key(model_id))
    if not faces:
        return data
    textures = {key: atlas_sprite(sprite) for key, sprite in data["textures"].items()}
    for el in data["elements"]:
        sides = faces.get(el.get("name"))
        if not sides:
            continue
        added = {}
        for side, (variable, sprite) in sides.items():
            textures[variable] = sprite
            added[side] = {"uv": list(VANILLA_UV), "texture": "#" + variable}
        merged = dict(el["faces"])
        merged.update(added)
        el["faces"] = {d: merged[d] for d in kit.DIRS if d in merged}
    data["textures"] = textures
    return data


class Output(addon.Output):
    def texture(self, sprite, img):
        super().texture(atlas_sprite(sprite), img)

    def model(self, model, display_json):
        data = with_vanilla_faces(model.model_id, model.to_json(display_json))
        data["credit"] = CREDIT
        path = self.asset(model.model_id, "models", ".json")
        kit.dump_json(path, data)
        self.written.append(path)


class Writer:
    def __init__(self, assets_root, namespace=paths.NS, icons=True):
        common.setup(namespace)
        self.namespace = namespace
        self.root = Path(assets_root)
        self.out = Output(self.root, namespace)
        self.icons = icons
        self.report = []
        self.problems = []
        self.densities = {}

    def clean(self, keys):
        for file in owned_files(self.root, self.namespace, keys):
            file.unlink()

    def part(self, model):
        self.problems += palette_problems(model)
        model.pack()
        self.densities[model.key] = model.used_density
        if not display.fits(model):
            raise RuntimeError(f"{model.key}: außerhalb der Modellgrenzen {display.MODEL_MIN:g}…{display.MODEL_MAX:g}")
        display_json, pre = display.compute(model)
        self.out.texture(*model.texture_map()["0"])
        self.out.model(model, display_json)
        self.out.render_item(model)
        variant = addon.write_paint(self.out, model, display_json)
        if variant is None:
            self.problems.append(f"{model.key}: keine Lack-Variante")
        if self.icons:
            self.out.icon(model.key, display.item_definition(model.model_id, pre))
        self.report.append((model.category, model.key, f"Dichte {model.used_density:.2f}"))
        return display_json, pre

    def binary(self, path, data):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        self.out.written.append(path)

    def finish(self, merge=False):
        problems = paintable.check_references(self.out.paint_groups)
        if problems:
            raise RuntimeError("Lack-Referenzen zu hell: " + "; ".join(problems))
        base = self.root / self.namespace
        if self.out.paint_groups:
            fresh = paintable.summary_json(self.out.paint_groups)
            file = base / "drone_paint.json"
            if merge and file.exists():
                import json
                data = json.loads(file.read_text(encoding="utf-8"))
                models = dict(data.get("models", {}))
                models.update(fresh["models"])
                fresh["models"] = dict(sorted(models.items()))
            kit.dump_json(file, fresh)
            self.out.written.append(file)
        return self.report
