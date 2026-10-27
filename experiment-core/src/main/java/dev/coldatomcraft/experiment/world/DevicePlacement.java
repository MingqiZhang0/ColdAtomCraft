package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.physics.Vec3;

/**
 * Geometric placement for Blender assets. By agreement: Blender Z-up / -Y-forward;
 * Minecraft Y-up / south=+Z, east=+X; base origin at footprint center.
 * metersPerBlock is a game-world scale convention, not an atomic length scale.
 *
 * <p>GLB/glTF interchange uses Y-up. Blender's exporter maps a Blender-local
 * vector {@code (x,y,z)} to glTF {@code (x,z,-y)}. A renderer that loads the GLB
 * must use the {@code gltf*ToWorld} methods on GLB coordinates and must not apply
 * the Blender conversion a second time. The manifest keeps anchor coordinates in
 * Blender space; {@link #blenderToGltf(Vec3)} bridges the two conventions.</p>
 */
public record DevicePlacement(Vec3 baseCenterWorldBlocks, CardinalFacing facing, double metersPerBlock) {
    public DevicePlacement {
        if (baseCenterWorldBlocks == null || facing == null ||
                !Double.isFinite(metersPerBlock) || metersPerBlock <= 0
                || !Double.isFinite(1.0 / metersPerBlock)) {
            throw new IllegalArgumentException("valid placement, facing and scale required");
        }
    }

    /** Coordinates of the base-footprint center, NOT the block volume center. */
    public static DevicePlacement fromBlock(int x, int y, int z, CardinalFacing facing) {
        return new DevicePlacement(new Vec3(x + 0.5, y, z + 0.5), facing, 1.0);
    }

    public Vec3 blenderVectorToWorld(Vec3 localMeters) {
        if (localMeters == null) throw new IllegalArgumentException("local vector required");
        // Blender -> south-facing Minecraft: (x,y,z) -> (x,z,-y), det=+1.
        double x = localMeters.x();
        double y = localMeters.z();
        double z = -localMeters.y();
        return switch (facing) {
            case SOUTH -> new Vec3(x, y, z);
            case EAST  -> new Vec3(z, y, -x);
            case NORTH -> new Vec3(-x, y, -z);
            case WEST  -> new Vec3(-z, y, x);
        };
    }

    public Vec3 blenderPointToWorldBlocks(Vec3 localMeters) {
        return baseCenterWorldBlocks.add(blenderVectorToWorld(localMeters).scale(1.0 / metersPerBlock));
    }

    public Vec3 blenderDirectionToWorld(Vec3 localDirection) {
        return blenderVectorToWorld(localDirection).normalized();
    }

    /** Blender-local coordinates to standard glTF Y-up interchange coordinates. */
    public static Vec3 blenderToGltf(Vec3 blenderLocal) {
        if (blenderLocal == null) throw new IllegalArgumentException("local vector required");
        return new Vec3(blenderLocal.x(), blenderLocal.z(), -blenderLocal.y());
    }

    /** Rotation of a GLB/glTF-local vector into Minecraft world axes. */
    public Vec3 gltfVectorToWorld(Vec3 gltfLocal) {
        if (gltfLocal == null) throw new IllegalArgumentException("glTF vector required");
        return switch (facing) {
            case SOUTH -> new Vec3(gltfLocal.x(), gltfLocal.y(), gltfLocal.z());
            case EAST  -> new Vec3(gltfLocal.z(), gltfLocal.y(), -gltfLocal.x());
            case NORTH -> new Vec3(-gltfLocal.x(), gltfLocal.y(), -gltfLocal.z());
            case WEST  -> new Vec3(-gltfLocal.z(), gltfLocal.y(), gltfLocal.x());
        };
    }

    public Vec3 gltfPointToWorldBlocks(Vec3 gltfLocalMeters) {
        return baseCenterWorldBlocks.add(gltfVectorToWorld(gltfLocalMeters).scale(1.0 / metersPerBlock));
    }

    public Vec3 gltfDirectionToWorld(Vec3 gltfLocalDirection) {
        return gltfVectorToWorld(gltfLocalDirection).normalized();
    }
}
