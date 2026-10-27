package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Local geometric, monochromatic coherent ray with transverse complex
 * travelling-mode amplitudes in sqrt(W). Not a finite-waist Gaussian solution.
 * Geometry is in laboratory-local metres; its phase reference is a declaration.
 */
public record GeometricJonesRay(Vec3 referencePointMeters,
                                RayFrame3 frame,
                                double vacuumWavelengthMeters,
                                String phaseReferenceKey,
                                JonesVector amplitudeSqrtWatts) {
    public GeometricJonesRay {
        if (referencePointMeters == null || frame == null || amplitudeSqrtWatts == null
                || phaseReferenceKey == null || phaseReferenceKey.isBlank()
                || !Double.isFinite(vacuumWavelengthMeters)
                || vacuumWavelengthMeters <= 0) {
            throw new IllegalArgumentException("valid geometric optical ray required");
        }
        if (!Double.isFinite(amplitudeSqrtWatts.squaredNorm())) {
            throw new IllegalArgumentException("ray power must be finite");
        }
    }

    /**
     * Explicit adapter from D7's reference plane. The geometric model has no
     * q or Gouy-phase law. Do not advance both this ray and the D7 modal field
     * and then sum their phases: that would double-count carrier propagation.
     */
    public static GeometricJonesRay atGaussianReferencePlane(
            ParaxialModeField field, Vec3 location, RayFrame3 frame) {
        if (field == null) throw new IllegalArgumentException("field required");
        return new GeometricJonesRay(location, frame, field.vacuumWavelengthMeters(),
                field.phaseReferenceKey(), field.modalAmplitudeSqrtWatts());
    }

    public double powerWatts() {
        return amplitudeSqrtWatts.squaredNorm();
    }

    /** 3D complex field components in the port's transverse basis, sqrt(W). */
    public LaboratoryJonesAmplitude laboratoryAmplitudeSqrtWatts() {
        var a = amplitudeSqrtWatts.x();
        var b = amplitudeSqrtWatts.y();
        Vec3 x = frame.xAxis();
        Vec3 y = frame.yAxis();
        return new LaboratoryJonesAmplitude(
                a.scale(x.x()).plus(b.scale(y.x())),
                a.scale(x.y()).plus(b.scale(y.y())),
                a.scale(x.z()).plus(b.scale(y.z())));
    }

    /**
     * Homogeneous-medium plane-wave phase transport. Refractive index is real,
     * positive and supplied explicitly. This does NOT propagate Gaussian q.
     * Large coordinate/path doubles may not resolve sub-wavelength phase.
     */
    public GeometricJonesRay advance(double lengthMeters, double refractiveIndex) {
        if (!Double.isFinite(lengthMeters) || lengthMeters < 0
                || !Double.isFinite(refractiveIndex) || refractiveIndex <= 0) {
            throw new IllegalArgumentException("nonnegative distance and real positive index required");
        }
        double mediumWavelength = vacuumWavelengthMeters / refractiveIndex;
        if (!(mediumWavelength > 0) || !Double.isFinite(mediumWavelength)) {
            throw new IllegalArgumentException("medium wavelength not representable");
        }
        double cycles = Math.IEEEremainder(lengthMeters, mediumWavelength) / mediumWavelength;
        var phase = ComplexAmplitude.fromPhaseRadians(2.0 * Math.PI * cycles);
        Vec3 nextPosition = referencePointMeters.add(frame.direction().scale(lengthMeters));
        if (lengthMeters > 0 && nextPosition.equals(referencePointMeters)) {
            throw new IllegalArgumentException("geometric displacement lost to floating point; use local laboratory coordinates");
        }
        return new GeometricJonesRay(nextPosition,
                frame, vacuumWavelengthMeters, phaseReferenceKey,
                amplitudeSqrtWatts.times(phase));
    }

    GeometricJonesRay withSurfaceFrame(Vec3 point, RayFrame3 surfaceFrame,
                                       JonesVector nextAmplitude) {
        return new GeometricJonesRay(point, surfaceFrame, vacuumWavelengthMeters,
                phaseReferenceKey, nextAmplitude);
    }
}
