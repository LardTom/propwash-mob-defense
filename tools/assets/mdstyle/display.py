import kit

from . import common

MODEL_MIN = -16.0
MODEL_MAX = 32.0

_reference = {}


def gripper():
    if not _reference:
        common.setup()
        import accessories
        model = accessories.build_gripper()
        _reference.update(gui=tuple(model.gui), gui_fill=float(model.gui_fill), density=float(model.density),
                          tex=int(model.tex), min_density=float(model.min_density))
    return dict(_reference)


def new_model(key, category="accessory", seed=1):
    ref = gripper()
    return kit.Model(category, key, tex=ref["tex"], density=ref["density"], min_density=ref["min_density"],
                     gui=ref["gui"], gui_fill=ref["gui_fill"], seed=seed)


def compute(model):
    return kit.compute_display(model)


def item_definition(model_id, pre):
    return kit.item_definition(model_id, pre)


def fits(model):
    for el in model.elements:
        for value in list(el.frm) + list(el.to):
            if value < MODEL_MIN or value > MODEL_MAX:
                return False
    return True


def rotation_of(element):
    rotation = element.get("rotation")
    if not rotation:
        return None
    origin = [float(v) for v in rotation["origin"]]
    if "axis" in rotation:
        angle = float(rotation.get("angle", 0.0))
        rx, ry, rz = {"x": (angle, 0.0, 0.0), "y": (0.0, angle, 0.0), "z": (0.0, 0.0, angle)}[rotation["axis"]]
    else:
        rx, ry, rz = (float(rotation.get(a, 0.0)) for a in ("x", "y", "z"))
    return origin, rx, ry, rz


def model_from_json(data, category, key):
    ref = gripper()
    model = kit.Model(category, key, gui=ref["gui"], gui_fill=ref["gui_fill"])
    for el in data.get("elements", []):
        model.elements.append(kit.Element(el.get("name", ""), [float(v) for v in el["from"]], [float(v) for v in el["to"]],
                                          {}, rotation_of(el)))
    return model


def recomputed(data, category, key):
    return kit.compute_display(model_from_json(data, category, key))


def extent_mm(data):
    import numpy as np
    model = model_from_json(data, "accessory", "probe")
    pts = np.concatenate([el.corners() for el in model.elements])
    return (pts.min(axis=0) - kit.ANCHOR) * kit.UNIT_MM, (pts.max(axis=0) - kit.ANCHOR) * kit.UNIT_MM
