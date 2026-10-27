#!/usr/bin/env python3
"""Validate Blender handoff manifests without Blender, Minecraft or extra packages.

Intentionally checks only the data/interface contract: no visual judgement, no
mesh rigging verification, no guarantee a glTF file is renderer-compatible.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import struct
import sys
from typing import Any

DEVICE_ID = re.compile(r"^[a-z][a-z0-9_]*:[a-z][a-z0-9_/]*$")
ANCHOR_NAME = re.compile(r"^[A-Z][A-Z0-9_]*$")
AXIS = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"}
AXIS_VECTOR = {
    "+X": (1.0, 0.0, 0.0), "-X": (-1.0, 0.0, 0.0),
    "+Y": (0.0, 1.0, 0.0), "-Y": (0.0, -1.0, 0.0),
    "+Z": (0.0, 0.0, 1.0), "-Z": (0.0, 0.0, -1.0),
}
STATUS = {"asset_spec_only_not_a_model", "ready_for_integration"}
INTERCHANGE_AXES = {"up": "+Y", "forward": "+Z"}
ROLES = {
    "optical_output", "optical_input", "interactive_control", "display",
    "power_input", "mounting", "cooling_input", "vacuum_port",
    "mechanical_part", "sensor",
}
# Keep these prefixes synchronized with tools/blender/export_device.py.
PREFIX_ROLE = (
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


class ManifestError(ValueError):
    """A manifest is not safe or does not follow the agreed handoff schema."""


def require(test: bool, message: str) -> None:
    if not test:
        raise ManifestError(message)


def vector3(value: Any, label: str) -> None:
    require(isinstance(value, list) and len(value) == 3, label + " must be a 3-element list")
    require(all(isinstance(x, (int, float)) and not isinstance(x, bool)
                and math.isfinite(x) for x in value), label + " must be finite numbers")


def safe_relative_filename(value: Any, suffix: str, base: Path, must_exist: bool) -> None:
    require(isinstance(value, str) and bool(value.strip()), f"{suffix} file name missing")
    p = Path(value)
    require(not p.is_absolute() and not value.startswith(("/", "\\"))
            and ".." not in p.parts and ":" not in value and "\\" not in value,
            "asset files must use relative paths within the device handoff folder")
    require(p.suffix.lower() == suffix, f"asset file must have {suffix} extension")
    if must_exist:
        root = base.resolve()
        resolved = (root / p).resolve()
        require(resolved.is_relative_to(root) and resolved.is_file(),
                "required asset file is missing or escapes folder: " + value)


def check_asset_header(file: Path) -> None:
    """Structural sanity only; does not parse meshes or prove Blender compatibility."""
    suffix = file.suffix.lower()
    if suffix == ".blend":
        header = file.read_bytes()[:12]
        require(len(header) == 12 and header[:7] == b"BLENDER",
                "source is not a Blender file header")
        require(header[7:8] in {b"-", b"_"} and header[8:9] in {b"v", b"V"},
                "invalid Blender pointer-size/endianness header")
        require(header[9:12].isdigit(), "invalid Blender version header")
    elif suffix == ".glb":
        data = file.read_bytes()
        require(len(data) >= 20, "GLB file too short")
        magic, version, size = struct.unpack_from("<4sII", data)
        require(magic == b"glTF" and version == 2 and size == len(data),
                "invalid GLB 2.0 file header")
        offset = 12
        document = None
        saw_binary = False
        try:
            while offset < len(data):
                require(offset + 8 <= len(data), "truncated GLB chunk header")
                length, chunk_type = struct.unpack_from("<I4s", data, offset)
                offset += 8
                require(length % 4 == 0 and offset + length <= len(data),
                        "invalid GLB chunk length")
                payload = data[offset:offset + length]
                offset += length
                if chunk_type == b"JSON":
                    require(document is None and not saw_binary,
                            "GLB requires one leading JSON chunk")
                    document = json.loads(payload.rstrip(b" \t\r\n\0").decode("utf-8"))
                elif chunk_type == b"BIN\0":
                    require(not saw_binary, "multiple GLB BIN chunks are not supported")
                    saw_binary = True
                else:
                    raise ManifestError("unrecognized GLB chunk type")
            require(offset == len(data), "GLB chunk sizes do not match file size")
        except (ValueError, UnicodeError, struct.error) as exc:
            raise ManifestError("invalid GLB chunk structure") from exc
        require(isinstance(document, dict) and isinstance(document.get("asset"), dict)
                and document["asset"].get("version") == "2.0",
                "GLB JSON must declare glTF asset version 2.0")
    elif suffix == ".gltf":
        try:
            doc = json.loads(file.read_text(encoding="utf-8"))
            require(isinstance(doc, dict) and isinstance(doc.get("asset"), dict)
                    and doc["asset"].get("version") == "2.0", "glTF JSON must declare version 2.0")
        except (ValueError, OSError, AttributeError) as exc:
            raise ManifestError("invalid glTF JSON") from exc


def expected_role(name: str) -> str | None:
    for prefix, role in PREFIX_ROLE:
        if name.startswith(prefix):
            return role
    return None


def validate_manifest(manifest: dict[str, Any], manifest_dir: Path) -> str:
    require(isinstance(manifest, dict), "manifest must contain a JSON object")
    require(type(manifest.get("schemaVersion")) is int and manifest["schemaVersion"] == 1,
            "schemaVersion must be exactly 1")
    require(isinstance(manifest.get("deviceId"), str) and
            DEVICE_ID.fullmatch(manifest["deviceId"]) is not None, "invalid deviceId")
    status = manifest.get("status")
    require(isinstance(status, str) and status in STATUS, "unrecognized asset status")
    ready = status == "ready_for_integration"
    scalar = manifest.get("blenderUnitMeters")
    require(isinstance(scalar, (int, float)) and not isinstance(scalar, bool) and
            math.isfinite(scalar) and scalar > 0, "blenderUnitMeters must be finite and positive")
    axes = manifest.get("axes")
    require(isinstance(axes, dict) and axes.get("up") == "+Z" and axes.get("forward") == "-Y",
            "asset axes must follow the established +Z-up/-Y-forward contract")
    require(manifest.get("pivot") == "base_footprint_center", "unrecognized pivot contract")
    interchange_axes = manifest.get("interchangeAxes")
    if ready:
        require(interchange_axes == INTERCHANGE_AXES,
                "integrated assets require interchangeAxes up=+Y, forward=+Z")
    elif interchange_axes is not None:
        require(interchange_axes == INTERCHANGE_AXES,
                "interchangeAxes must be up=+Y, forward=+Z when declared")
    safe_relative_filename(manifest.get("source"), ".blend", manifest_dir, ready)
    interchange = manifest.get("interchange")
    require(isinstance(interchange, str), "missing interchange asset path")
    suffix = Path(interchange).suffix.lower()
    require(suffix in {".glb", ".gltf"}, "interchange must be .glb or .gltf")
    safe_relative_filename(interchange, suffix, manifest_dir, ready)
    if ready:
        try:
            check_asset_header(manifest_dir / manifest["source"])
            check_asset_header(manifest_dir / interchange)
        except OSError as exc:
            raise ManifestError("cannot read asset file") from exc

    anchors = manifest.get("anchors")
    require(isinstance(anchors, list) and len(anchors) > 0, "at least one anchor required")
    names: set[str] = set()
    output_anchors = 0
    for index, anchor in enumerate(anchors):
        label = f"anchors[{index}]"
        require(isinstance(anchor, dict), label + " must be an object")
        name = anchor.get("name")
        require(isinstance(name, str) and ANCHOR_NAME.fullmatch(name) is not None,
                label + " invalid name")
        require(name not in names, label + " duplicates anchor " + name)
        names.add(name)
        role = anchor.get("role")
        require(isinstance(role, str) and role in ROLES, label + " invalid role")
        expected = expected_role(name)
        require(expected is not None, label + " name has no supported anchor prefix")
        require(expected == role, label + f" prefix requires role {expected}, got {role}")
        if role == "optical_output":
            output_anchors += 1
        if "forward" in anchor:
            require(isinstance(anchor["forward"], str) and anchor["forward"] in AXIS,
                    label + " invalid forward axis")
        if "forwardVectorLocal" in anchor:
            vector3(anchor["forwardVectorLocal"], label + ".forwardVectorLocal")
            norm_sq = sum(x * x for x in anchor["forwardVectorLocal"])
            require(abs(norm_sq - 1.0) < 1e-5, label + " forwardVectorLocal must have unit length")
        if "forward" in anchor and "forwardVectorLocal" in anchor:
            axis_vector = AXIS_VECTOR[anchor["forward"]]
            dot = sum(a * b for a, b in zip(axis_vector, anchor["forwardVectorLocal"]))
            require(dot > 1.0 - 1e-5,
                    label + " forward axis and forwardVectorLocal disagree")
        if "localPositionMeters" in anchor:
            vector3(anchor["localPositionMeters"], label + ".localPositionMeters")
        else:
            require(not ready, label + " requires localPositionMeters for integrated assets")
        if role in {"optical_output", "optical_input"}:
            if ready:
                require("forwardVectorLocal" in anchor,
                        label + " integrated optical port requires forwardVectorLocal")
            else:
                require("forward" in anchor or "forwardVectorLocal" in anchor,
                        label + " optical port requires local forward direction")
    if ready and manifest["deviceId"] == "coldatomcraft:laser_source":
        require(output_anchors > 0, "laser source requires at least one optical output anchor")
        require("PORT_LASER_OUT_0" in names, "laser source requires PORT_LASER_OUT_0")
    return "READY" if ready else "SPEC ONLY (no model files claimed)"


def validate_file(path: Path) -> str:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ManifestError(f"cannot read JSON: {exc}") from exc
    return validate_manifest(value, path.parent)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifests", nargs="+", type=Path)
    args = parser.parse_args(argv)
    errors = 0
    for path in args.manifests:
        try:
            status = validate_file(path)
            print(f"PASS {path}: {status}")
        except ManifestError as exc:
            print(f"FAIL {path}: {exc}", file=sys.stderr)
            errors += 1
    return 0 if errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
