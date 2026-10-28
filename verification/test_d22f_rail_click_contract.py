"""D22F manual stage/table hit-targeting regression checks.

Static integration checks only: passing does not prove client raycasting or
NeoForge compilation. Run JDK 25 and in-game F3 target checks before merge.
"""
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview"
MODELS = ROOT / "neoforge-mod/src/main/resources/assets/coldatomcraft/models/block"


def source(name):
    return (PREVIEW / name).read_text(encoding="utf-8")


def model_bounds(name):
    model = json.loads((MODELS / name).read_text(encoding="utf-8"))
    items = model["elements"]
    return tuple(min(e["from"][i] for e in items) for i in range(3)) + tuple(
        max(e["to"][i] for e in items) for i in range(3)
    )


def shape_bounds(src, constant):
    match = re.search(
        r"\b" + re.escape(constant) + r"\s*=\s*box\(([^)]+)\)", src
    )
    if not match:
        raise AssertionError(f"shape constant not found: {constant}")
    return tuple(int(part.strip()) for part in match.group(1).split(","))


class D22FRailClickContract(unittest.TestCase):
    def test_shapes_match_visible_model_envelopes(self):
        stage = source("DiagnosticStageBlock.java")
        table = source("DiagnosticOpticalTableBlock.java")
        linear_bounds = model_bounds("diagnostic_linear_stage.json")
        rotary_bounds = model_bounds("diagnostic_rotary_stage.json")
        table_bounds = model_bounds("diagnostic_optical_table.json")
        self.assertEqual(linear_bounds, shape_bounds(stage, "LINEAR_Z_SHAPE"))
        self.assertEqual(rotary_bounds, shape_bounds(stage, "ROTARY_SHAPE"))
        self.assertEqual(table_bounds, shape_bounds(table, "TABLE_SHAPE"))
        # A 90-degree Y rotation interchanges the X and Z envelopes.
        expected = (
            linear_bounds[2], linear_bounds[1], linear_bounds[0],
            linear_bounds[5], linear_bounds[4], linear_bounds[3],
        )
        self.assertEqual(expected, shape_bounds(stage, "LINEAR_X_SHAPE"))
        self.assertIn('state.getValue(FACING).getAxis() == Direction.Axis.Z', stage)
        self.assertIn('protected VoxelShape getShape(', stage)
        self.assertIn('protected VoxelShape getShape(', table)

    def test_table_diagnostic_only_on_explicit_top_sneak_click(self):
        table = source("DiagnosticOpticalTableBlock.java")
        self.assertIn('!player.isShiftKeyDown() || hit.getDirection() != Direction.UP', table)
        before_verbose = table.index('LoadedLabGeometryWorld.captureNominal(')
        self.assertLess(table.index('if (!player.isShiftKeyDown()'), before_verbose)
        self.assertIn('player.sendOverlayMessage(', table)
        self.assertIn('Hit TABLE, not stage', table)
        self.assertIn('Optic directly on table: put a stage', table)
        self.assertIn('server.hasChunkAt(mounted)', table)
        self.assertNotIn('setBlockAndUpdate(', table)
        self.assertNotIn('removeBlock(', table)

    def test_manual_stage_preserves_existing_motion_and_identity_rules(self):
        stage = source("DiagnosticStageBlock.java")
        for required in (
            'useWithoutItem(', 'useItemOn(', 'return interact(state,level,pos,player)',
            'level.setBlockAndUpdate(target,optic)', 'level.removeBlock(from,false)',
            'identity.identityUsable()', 'new device ID',
            'identity.confirmControlledChange(', 'level.hasChunkAt(from)',
            'level.getBlockState(pos.below()).is(PreviewBlocks.OPTICAL_TABLE.get())',
        ):
            self.assertIn(required, stage)


if __name__ == "__main__":
    unittest.main()
