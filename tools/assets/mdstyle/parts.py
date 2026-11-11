import json
from pathlib import Path

from . import paths

CATEGORY = "accessory"
HINGE_CLEARANCE_MM = 5.0

DEFAULTS = {
    "dispenser_mount": {"format": 1, "mass_g": 150.0, "slot": "front", "size_mm": [62.0, 58.0, 64.0],
                        "flight_battery_w": 1.2, "frontal_area_m2": 0.0036},
    "spear_mount": {"format": 1, "mass_g": 40.0, "slot": "bottom", "size_mm": [24.0, 18.0, 90.0],
                    "flight_battery_w": 0.0, "frontal_area_m2": 0.0004},
    "rocket_booster": {"format": 1, "mass_g": 28.0, "slot": "top", "size_mm": [30.0, 22.0, 70.0],
                       "flight_battery_w": 0.2, "frontal_area_m2": 0.0005},
}


def size_of(definition):
    size = definition.get("size_mm")
    if isinstance(size, list) and len(size) == 3 and all(isinstance(v, (int, float)) and v > 0 for v in size):
        return float(size[0]), float(size[1]), float(size[2])
    raise ValueError("size_mm fehlt oder ist ungültig")


def slot_of(definition):
    return str(definition.get("slot", "")).lower()


def data_file(key, parts_dir=paths.PARTS):
    return Path(parts_dir) / CATEGORY / f"{key}.json"


def load(parts_dir=paths.PARTS, only=None):
    out = []
    for key, default in DEFAULTS.items():
        if only and key not in only:
            continue
        file = data_file(key, parts_dir)
        if file.exists():
            out.append((CATEGORY, key, json.loads(file.read_text(encoding="utf-8")), "data"))
        else:
            out.append((CATEGORY, key, dict(default), "design"))
    return out


def unknown(parts_dir=paths.PARTS):
    folder = Path(parts_dir) / CATEGORY
    if not folder.is_dir():
        return []
    return sorted(file.stem for file in folder.glob("*.json") if file.stem not in DEFAULTS)


def box_mm(definition):
    w, h, d = size_of(definition)
    slot = slot_of(definition)
    if slot == "top":
        return (-w / 2, HINGE_CLEARANCE_MM, -d / 2), (w / 2, HINGE_CLEARANCE_MM + h, d / 2)
    if slot == "bottom":
        return (-w / 2, -h, -d / 2), (w / 2, 0.0, d / 2)
    if slot == "front":
        return (-w / 2, -h, -d), (w / 2, 0.0, 0.0)
    raise ValueError(f"unbekannte Halterung {slot}")
