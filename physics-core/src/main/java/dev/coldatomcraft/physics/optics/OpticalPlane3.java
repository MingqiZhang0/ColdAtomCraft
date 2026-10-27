package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * A flat, unbounded optical surface with its unit normal pointing from
 * incident medium n1 into transmitted medium n2. Intersection is only valid
 * for front-side incidence. Material identity and block geometry are NOT given.
 */
public record OpticalPlane3(Vec3 pointMeters, Vec3 normal1To2) {
    private static final double MIN_COSINE = 1e-12;
    private static final double NORMAL_DEGENERACY = 1e-12;

    public OpticalPlane3 {
        if (pointMeters == null || normal1To2 == null) {
            throw new IllegalArgumentException("plane point and normal required");
        }
        normal1To2 = normal1To2.normalized();
    }

    public record Hit(double distanceMeters, Vec3 pointMeters, double cosineIncident,
                      Vec3 s, Vec3 pIncident, Vec3 pReflected, Vec3 reflectedDirection) {
        public Hit {
            if (!Double.isFinite(distanceMeters) || distanceMeters < 0
                    || pointMeters == null || s == null || pIncident == null
                    || pReflected == null || reflectedDirection == null
                    || !(cosineIncident > 0)) {
                throw new IllegalArgumentException("valid planar optical hit required");
            }
        }
    }

    /** Pure geometry; no reflectance, Fresnel amplitudes or propagation phase. */
    public Hit intersect(GeometricJonesRay incident) {
        if (incident == null) throw new IllegalArgumentException("incident ray required");
        Vec3 k = incident.frame().direction();
        double cos = k.dot(normal1To2);
        if (cos <= MIN_COSINE) {
            throw new IllegalArgumentException("surface requires non-grazing front-face incidence");
        }
        double t = pointMeters.subtract(incident.referencePointMeters()).dot(normal1To2) / cos;
        if (!Double.isFinite(t) || t < 0) {
            throw new IllegalArgumentException("plane intersection behind ray origin or not finite");
        }
        Vec3 hitPoint = incident.referencePointMeters().add(k.scale(t));
        Vec3 kReflected = k.subtract(normal1To2.scale(2 * cos)).normalized();
        // s is perpendicular to the plane of incidence. At exact normal
        // incidence the incidence plane is undefined: use the input x axis
        // as a declared continuous gauge, never the global world X axis.
        Vec3 sHint = normal1To2.cross(k);
        Vec3 s = sHint.magnitude() <= NORMAL_DEGENERACY
                ? incident.frame().xAxis() : sHint.normalized();
        Vec3 pIncident = k.cross(s).normalized();
        Vec3 pReflected = kReflected.cross(s).normalized();
        return new Hit(t, hitPoint, cos, s, pIncident, pReflected, kReflected);
    }

    static JonesVector rotateIntoSP(GeometricJonesRay incident, Hit hit) {
        Vec3 x = incident.frame().xAxis();
        Vec3 y = incident.frame().yAxis();
        JonesVector a = incident.amplitudeSqrtWatts();
        return new JonesVector(a.x().scale(x.dot(hit.s())).plus(a.y().scale(y.dot(hit.s()))),
                a.x().scale(x.dot(hit.pIncident())).plus(a.y().scale(y.dot(hit.pIncident()))));
    }

    static RayFrame3 frameFor(Vec3 direction, Vec3 s) {
        return new RayFrame3(direction, s, direction.cross(s).normalized());
    }
}
