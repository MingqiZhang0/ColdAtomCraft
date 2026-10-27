package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevicePlacementTest {
    @Test void blenderForwardMapsToEveryCardinalFacing() {
        for (var facing : CardinalFacing.values()) {
            Vec3 result = DevicePlacement.fromBlock(0, 0, 0, facing)
                    .blenderDirectionToWorld(new Vec3(0, -1, 0));
            Vec3 expected = switch (facing) {
                case NORTH -> new Vec3(0, 0, -1);
                case EAST -> new Vec3(1, 0, 0);
                case SOUTH -> new Vec3(0, 0, 1);
                case WEST -> new Vec3(-1, 0, 0);
            };
            assertEquals(expected, result);
        }
    }

    @Test void basePivotIsOnGroundNotInBlockCenter() {
        DevicePlacement pose = DevicePlacement.fromBlock(7, 60, 8, CardinalFacing.SOUTH);
        assertEquals(new Vec3(7.5, 60, 8.5), pose.blenderPointToWorldBlocks(new Vec3(0, 0, 0)));
        assertEquals(new Vec3(7.5, 61, 8.5), pose.blenderPointToWorldBlocks(new Vec3(0, 0, 1)));
    }

    @Test void transformationHasPositiveDeterminantAndPreservesDistances() {
        for (CardinalFacing facing : CardinalFacing.values()) {
            DevicePlacement pose = DevicePlacement.fromBlock(0, 0, 0, facing);
            Vec3 ex = pose.blenderVectorToWorld(new Vec3(1, 0, 0));
            Vec3 ey = pose.blenderVectorToWorld(new Vec3(0, 1, 0));
            Vec3 ez = pose.blenderVectorToWorld(new Vec3(0, 0, 1));
            assertEquals(1.0, ex.cross(ey).dot(ez), 1e-14);
            assertEquals(5.0, pose.blenderVectorToWorld(new Vec3(3, 0, 4)).magnitude(), 1e-14);
        }
    }

    @Test void scaleHasUnitsAndRejectsInvalidValues() {
        DevicePlacement scaled = new DevicePlacement(new Vec3(0, 0, 0), CardinalFacing.SOUTH, 2);
        assertEquals(new Vec3(0, 0.5, 0), scaled.blenderPointToWorldBlocks(new Vec3(0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new DevicePlacement(new Vec3(0, 0, 0), CardinalFacing.SOUTH, 0));
    }

    @Test void opticalAnchorUsesBasePivotAndDeviceScale() {
        DevicePlacement pose = DevicePlacement.fromBlock(4, 72, -6, CardinalFacing.SOUTH);
        Vec3 output = pose.blenderPointToWorldBlocks(new Vec3(0, -0.25, 0.4));
        assertEquals(new Vec3(4.5, 72.4, -5.25), output);
    }

    @Test void gltfConversionMatchesBlenderConversionForAllFacings() {
        Vec3 blenderLocal = new Vec3(0.3, -0.4, 1.2);
        Vec3 gltfLocal = DevicePlacement.blenderToGltf(blenderLocal);
        assertEquals(new Vec3(0.3, 1.2, 0.4), gltfLocal);
        for (CardinalFacing facing : CardinalFacing.values()) {
            DevicePlacement pose = new DevicePlacement(new Vec3(2.5, 64, 3.5), facing, 2.0);
            assertEquals(pose.blenderVectorToWorld(blenderLocal), pose.gltfVectorToWorld(gltfLocal));
            assertEquals(pose.blenderPointToWorldBlocks(blenderLocal), pose.gltfPointToWorldBlocks(gltfLocal));
            assertEquals(pose.blenderDirectionToWorld(new Vec3(0, -1, 0)),
                    pose.gltfDirectionToWorld(new Vec3(0, 0, 1)));
        }
    }

    @Test void gltfYUpForwardIsPositiveZAndIsNotRotatedTwice() {
        DevicePlacement south = DevicePlacement.fromBlock(0, 0, 0, CardinalFacing.SOUTH);
        assertEquals(new Vec3(0, 0, 1), south.gltfDirectionToWorld(new Vec3(0, 0, 1)));
        assertEquals(new Vec3(0, 0, 1), south.gltfDirectionToWorld(DevicePlacement.blenderToGltf(
                new Vec3(0, -1, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> DevicePlacement.blenderToGltf(null));
    }
}
