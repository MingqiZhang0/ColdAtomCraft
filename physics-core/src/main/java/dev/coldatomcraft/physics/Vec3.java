package dev.coldatomcraft.physics;

/**
 * Immutable, finite Cartesian vector. The caller owns the coordinate units;
 * no optical propagation law or material model is assumed here.
 */
public record Vec3(double x, double y, double z) {
    public Vec3 {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        // Canonicalize -0.0 so cardinal transforms and record equality agree.
        if (x == 0) x = 0.0;
        if (y == 0) y = 0.0;
        if (z == 0) z = 0.0;
    }

    public static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double factor) {
        requireFinite(factor, "factor");
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double magnitude() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    /**
     * Scale before taking the norm: direct 1 / magnitude() overflows for finite
     * subnormal vectors, even though a unit vector can be represented exactly.
     */
    public Vec3 normalized() {
        double largest = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (!(largest > 0)) {
            throw new IllegalArgumentException("cannot normalize the zero vector");
        }
        double nx = x / largest;
        double ny = y / largest;
        double nz = z / largest;
        double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new Vec3(nx / norm, ny / norm, nz / norm);
    }
}
