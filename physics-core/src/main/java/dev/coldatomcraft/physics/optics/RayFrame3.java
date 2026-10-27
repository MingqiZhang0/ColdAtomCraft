package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Right-handed orthonormal laboratory frame: x cross y = direction.
 * Axes are dimensionless. This frame is a declaration of an optical port,
 * not a calibrated beam alignment measurement.
 */
public record RayFrame3(Vec3 direction, Vec3 xAxis, Vec3 yAxis) {
    private static final double TOLERANCE = 1e-10;

    public RayFrame3 {
        if (direction == null || xAxis == null || yAxis == null) {
            throw new IllegalArgumentException("all ray-frame vectors required");
        }
        if (Math.abs(direction.magnitude() - 1) > TOLERANCE
                || Math.abs(xAxis.magnitude() - 1) > TOLERANCE
                || Math.abs(yAxis.magnitude() - 1) > TOLERANCE
                || Math.abs(direction.dot(xAxis)) > TOLERANCE
                || Math.abs(direction.dot(yAxis)) > TOLERANCE
                || Math.abs(xAxis.dot(yAxis)) > TOLERANCE
                || xAxis.cross(yAxis).dot(direction) < 1 - TOLERANCE) {
            throw new IllegalArgumentException("ray frame must be orthonormal and right handed");
        }
    }

    /** Construct a local frame using a measured/declared transverse-axis hint. */
    public static RayFrame3 fromDirectionAndXAxis(Vec3 direction, Vec3 xAxisHint) {
        if (direction == null || xAxisHint == null) {
            throw new IllegalArgumentException("direction and transverse hint required");
        }
        Vec3 k = direction.normalized();
        // Avoid cancellation by removing the projection from the normalized hint.
        Vec3 hint = xAxisHint.normalized();
        Vec3 transverse = hint.subtract(k.scale(hint.dot(k)));
        if (transverse.magnitude() <= 1e-12) {
            throw new IllegalArgumentException("transverse hint parallel to ray direction");
        }
        Vec3 x = transverse.normalized();
        Vec3 y = k.cross(x).normalized();
        return new RayFrame3(k, x, y);
    }
}
