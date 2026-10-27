## D10 temporary cuboid models — NOT Blender handoff

D10 has two **development-only** JSON cuboid models, with emitter forward +Z
(south in default blockstate) and detector front +Z. Their visible port/aperture
front face is at local `z=15/16` blocks, i.e. `7/16` metres forward from the
model block centre in the preview one-metre/block convention. Rotations are applied with the vanilla **model-Y** convention:
`south=0`, `west=90`, `north=180`, `east=270` degrees. These are intentionally
NOT the usual right-handed mathematical Y-axis signs; the asset tests check
that the visible +Z port tracks the declared game `facing`.

**These JSON cuboids are disposable.** They do not constitute a `.blend` / GLB
export, do not revise `PORT_LASER_OUT_0` Blender asset coordinates, and should
never be used as the final scientific dimensions. The eventual authored Blender
models may require different output-port anchors. Revalidate alignment and
update the physical geometry adapter when they are delivered.

# Blender asset handoff — M0-R2

The project owner is authoritative for Blender 3D geometry, materials, visual design and moving parts. Engineering provides import, attachment mapping, renderer choice, integration, performance testing and bug fixes.

## Evidence levels (do not conflate)

| Level | Status in M0-R2 | Meaning |
| --- | --- | --- |
| 1. Manifest/schema validation | **PASS (spec only)** | Example manifest and validator tests pass; no model files claimed |
| 2. Actual Blender export | **NOT RUN** | No Blender executable, owner `.blend`, GLB or owner approval in this environment |
| 3. Minecraft rendering/integration acceptance | **NOT RUN** | No renderer, block, model registration or in-game visual acceptance |

The example manifest deliberately has status `asset_spec_only_not_a_model`. Do not present it as an owner asset.

## Export contract v1 (provisional; owner can revise before a final asset)

- Scene unit scale `1.0` (one Blender unit = one meter), Blender axis **+Z up** and device front **-Y**.
- A parent Empty named `CAC_ROOT` at the scene origin `(0,0,0)` with identity rotation and scale `(1,1,1)`.
- All parts and anchor Empties must be descendants of `CAC_ROOT`.
- `CAC_ROOT` is at the **base-footprint center**, not the volumetric center of the block. A Minecraft block at `(x,y,z)` therefore maps to `(x+0.5, y, z+0.5)` in world coordinates.
- Integrated manifests declare `interchangeAxes: { "up": "+Y", "forward": "+Z" }` for the GLB/glTF interchange. This is separate from the Blender-local `+Z/-Y` axes.
- Name the emitter Empty `PORT_LASER_OUT_0`; its local `-Y` axis defines the emission direction, but may be rotated to any angle. Exact anchor position and orientation are stored in the export manifest.
- Other supported prefixes: `PORT_LASER_IN_`, `PORT_POWER_IN`, `PORT_VACUUM_`, `PORT_COOLING_`, `CTRL_`, `SCREEN_`, `SENSOR_`, `MOUNT_`, `MOVING_`.
- Example named parts: `CTRL_POWER`, `CTRL_SHUTTER`, `SCREEN_STATUS`, `MOVING_MIRROR`.
- Laser wave properties remain in the physics model, NOT in the mesh or ray visual.

## Folder delivered for each device

```text
laser_source_handoff/
  laser_source.blend  # editable owner source
  laser_source.glb    # exchange format, NOT native Minecraft block model
  manifest.json       # axes, scale, anchors + positions + unit directions
```

### Export helper (optional)

With your finished, saved `.blend` open or supplied on the command line:

```text
blender --background laser_source.blend --python tools/blender/export_device.py -- --device-id coldatomcraft:laser_source --out laser_source_handoff
```

Then validate locally:

```text
python tools/validate_assets.py laser_source_handoff/manifest.json
```

The exporter selects mesh/Empty objects under `CAC_ROOT`, exports GLB, copies the saved `.blend`, and writes a manifest with `localPositionMeters` and `forwardVectorLocal` for each anchor. **The exporter has been syntax-checked but not run in actual Blender yet.** Inspect the resulting GLB and anchor directions before approving import.

The manifest validator checks schema, naming, unit conventions, numeric validity, path safety and basic Blender/glTF file headers. It **does not** verify visual topology, texture quality, GLB mesh validity, animations or in-game renderer compatibility.

## Coordinate conversion

The pure-Java `DevicePlacement` conversion is:

```text
Blender local (+X,+Y,+Z) -> south-facing Minecraft (+X,+Z,-Y)
Minecraft: +X east, +Y up, +Z south
Minecraft placed block at (x,y,z) has base pivot at (x+0.5,y,z+0.5)
```

All four cardinal directions have executable tests; the transform has determinant +1 and does not mirror meshes. Scale is explicit `metersPerBlock`, and optical anchors use `blenderPointToWorldBlocks`, not the block volume center.

The standard Blender glTF exporter maps Blender-local `(x,y,z)` to glTF `(x,z,-y)`. M0-R2 exposes `DevicePlacement.blenderToGltf`, `gltfPointToWorldBlocks` and `gltfDirectionToWorld`; tests assert that the Blender-space and glTF-space paths agree for all four facings. **A renderer loading the GLB must use the `gltf*` methods and must not apply the Blender conversion twice.**

Manifest anchors remain in Blender-local space (`localPositionMeters`, `forwardVectorLocal`). The manifest records the interchange axes; it does not rotate the GLB again.

A custom renderer may be necessary for complex or moving optics. Vanilla JSON models remain an option for simpler static forms. There is no automatic NeoForge GLB runtime renderer guaranteed by this handoff.

## Handoff quality checks

- Device silhouette/dimensions, moving parts and interactive labels reviewed by owner.
- Emitter ray starts at named port, not arbitrary block center.
- Device rotation updates both rendered mesh and physical output point in lockstep.
- Dedicated server has no dependence on Blender/GLB/client classes.
- Collision/click shapes simplified intentionally, distinct from fine visual mesh.
- Anchor names match their role prefixes; the validator enforces the same prefix table as the exporter.
- Integrated assets carry unit `forwardVectorLocal` for optical ports, not only a coarse axis label.
- The `.blend` and `.glb` are structurally checked; visual topology/materials/animation remain an owner/engineering review, not a manifest claim.
- Installed Minecraft rendering, scale against a block, anchor alignment with the rendered beam, and moving-part animation are **NOT RUN** until a real owner asset exists.
