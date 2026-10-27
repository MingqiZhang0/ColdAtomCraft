"""Cold Atom Craft Blender model handoff helper (requires Blender's bpy).

Usage:
    blender --background laser_source.blend --python tools/blender/export_device.py -- \
       --device-id coldatomcraft:laser_source --out path/to/device_handoff

This script does NOT create the device mesh. Its only job is to export the
owner's Blender work and authoritative anchor transforms as handoff metadata.
Not tested by Blender in the authoring environment; test with your Blender build.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import sys

import bpy
from mathutils import Vector


PREFIX_ROLES = (
    ("PORT_LASER_OUT_", "optical_output"),
    ("PORT_LASER_IN_", "optical_input"),
    ("PORT_POWER_IN", "power_input"),
    ("PORT_VACUUM_", "vacuum_port"),
    ("PORT_COOLING_", "cooling_input"),
    ("CTRL_", "interactive_control"),
    ("SCREEN_", "display"),
    ("SENSOR_", "sensor"),
    ("MOUNT_", "mounting"),
    ("MOVING_", "mechanical_part"),
)


def all_children(root):
    result = []
    for obj in bpy.data.objects:
        parent = obj.parent
        while parent is not None:
            if parent == root:
                result.append(obj)
                break
            parent = parent.parent
    return result


def role_for(name):
    for prefix, role in PREFIX_ROLES:
        if name.startswith(prefix):
            return role
    return None


def main():
    cli = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(cli)

    if not bpy.data.filepath or bpy.data.is_dirty:
        raise RuntimeError("Save the final .blend before exporting.")
    if abs(bpy.context.scene.unit_settings.scale_length - 1.0) > 1e-8:
        raise RuntimeError("For handoff schema v1, set the Blender scene unit scale to 1 meter.")
    root = bpy.data.objects.get("CAC_ROOT")
    if root is None or root.type != "EMPTY":
        raise RuntimeError("Create an Empty named CAC_ROOT as the device root/pivot.")
    if any(abs(x) > 1e-6 for x in root.matrix_world.translation):
        raise RuntimeError("CAC_ROOT must be at the scene origin; apply transforms before export.")
    q = root.matrix_world.to_quaternion()
    if abs(abs(q.w) - 1.0) > 1e-6 or any(abs(x) > 1e-6 for x in (q.x, q.y, q.z)):
        raise RuntimeError("CAC_ROOT must have identity rotation; apply root transforms.")
    if any(abs(s - 1.0) > 1e-6 for s in root.matrix_world.to_scale()):
        raise RuntimeError("CAC_ROOT must have scale (1,1,1); apply root transforms.")
    descendants = all_children(root)
    meshes = [obj for obj in descendants if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError("No device mesh objects parented under CAC_ROOT.")
    anchor_objects = [obj for obj in descendants if obj.type == "EMPTY" and role_for(obj.name)]
    if not anchor_objects:
        raise RuntimeError("No named device anchor Empties under CAC_ROOT.")

    root_inv = root.matrix_world.inverted_safe()
    anchors = []
    for obj in sorted(anchor_objects, key=lambda item: item.name):
        local = root_inv @ obj.matrix_world
        point = local.translation
        forward = (local.to_quaternion() @ Vector((0.0, -1.0, 0.0))).normalized()
        role = role_for(obj.name)
        anchor = {
            "name": obj.name,
            "role": role,
            "localPositionMeters": [float(point.x), float(point.y), float(point.z)],
        }
        if role in {"optical_output", "optical_input"}:
            anchor["forwardVectorLocal"] = [float(forward.x), float(forward.y), float(forward.z)]
        anchors.append(anchor)

    if args.device_id == "coldatomcraft:laser_source" and not any(
            anchor["role"] == "optical_output" for anchor in anchors):
        raise RuntimeError("laser_source requires a PORT_LASER_OUT_* optical output anchor")

    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=True)
    source = Path(bpy.data.filepath).resolve()
    copied = output / source.name
    if copied != source:
        shutil.copy2(source, copied)
    model = output / (source.stem + ".glb")

    previous_selected = list(bpy.context.selected_objects)
    previous_active = bpy.context.view_layer.objects.active
    try:
        bpy.ops.object.select_all(action="DESELECT")
        for obj in [root] + descendants:
            if obj.type in {"MESH", "EMPTY"}:
                obj.select_set(True)
        bpy.context.view_layer.objects.active = root
        bpy.ops.export_scene.gltf(filepath=str(model), export_format="GLB", use_selection=True)
    finally:
        bpy.ops.object.select_all(action="DESELECT")
        for obj in previous_selected:
            if obj.name in bpy.data.objects:
                obj.select_set(True)
        bpy.context.view_layer.objects.active = previous_active

    if not model.is_file() or model.stat().st_size < 20:
        raise RuntimeError("Blender did not produce a nonempty GLB export")

    manifest = {
        "schemaVersion": 1,
        "deviceId": args.device_id,
        "status": "ready_for_integration",
        "source": copied.name,
        "interchange": model.name,
        "blenderUnitMeters": 1.0,
        "axes": {"up": "+Z", "forward": "-Y"},
        "interchangeAxes": {"up": "+Y", "forward": "+Z"},
        "pivot": "base_footprint_center",
        "anchors": anchors,
        "notes": "Blender-local anchor transforms. GLB exporter converts meshes to glTF Y-up; use DevicePlacement.gltf* methods and do not rotate meshes twice.",
    }
    dest = output / "manifest.json"
    dest.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Cold Atom Craft handoff ready:", dest)
    print("Validate: python tools/validate_assets.py", dest)


if __name__ == "__main__":
    main()
