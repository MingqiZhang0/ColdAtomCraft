package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.FinitePlaneGaussianFlux;
import dev.coldatomcraft.physics.optics.GaussianBeam;

import java.util.Objects;

/**
 * D22D read-only coupling of a REPORTED stage position to local-SI optics.
 * Does not actuate Minecraft blocks, read a power meter, or authenticate an
 * external encoder. Must NOT be converted to PowerTrace.Sample or used as a
 * measurement, an energy allocation, or a motor settling acknowledgement.
 */
public final class StageSurfacePrediction {
    public record ReferenceObservation(long reportedGameTick, String dimensionId, String deviceId,
                                       long deviceRevision, long xNanometres,
                                       long yNanometres, long zNanometres,
                                       FinitePlaneGaussianFlux.Prediction idealPower) {
        public ReferenceObservation {
            if (reportedGameTick < 0 || dimensionId == null || dimensionId.isBlank()
                    || deviceId == null || deviceId.isBlank()
                    || deviceRevision < 0 || idealPower == null) {
                throw new IllegalArgumentException("invalid reference observation");
            }
        }
        public String provenance() { return "REFERENCE_ONLY"; }
    }

    private final LaboratoryMountFrame laboratory;
    private final LaboratoryMountFrame.BlockAddress mountBlock;
    private final String requiredDeviceId;
    private final long requiredRevision;
    private final GaussianBeam beam;
    private final Vec3 frontNormalLab;
    private final Vec3 transverseHintLab;
    private final double apertureRadiusMetres;

    public StageSurfacePrediction(LaboratoryMountFrame laboratory,
                                  LaboratoryMountFrame.BlockAddress mountBlock,
                                  String requiredDeviceId, long requiredRevision,
                                  GaussianBeam beam, Vec3 frontNormalLab,
                                  Vec3 transverseHintLab, double apertureRadiusMetres) {
        this.laboratory = Objects.requireNonNull(laboratory, "laboratory frame");
        this.mountBlock = Objects.requireNonNull(mountBlock, "mount");
        if (requiredDeviceId == null || requiredDeviceId.isBlank()
                || requiredDeviceId.length() > 128 || requiredRevision < 0) {
            throw new IllegalArgumentException("required device identity and revision");
        }
        this.requiredDeviceId = requiredDeviceId;
        this.requiredRevision = requiredRevision;
        this.beam = Objects.requireNonNull(beam, "local-SI beam");
        this.frontNormalLab = Objects.requireNonNull(frontNormalLab, "front normal");
        this.transverseHintLab = Objects.requireNonNull(transverseHintLab, "axis hint");
        if (!(Double.isFinite(apertureRadiusMetres) && apertureRadiusMetres > 0)) {
            throw new IllegalArgumentException("invalid detector aperture");
        }
        this.apertureRadiusMetres = apertureRadiusMetres;
    }

    /**
     * Merely checks consistency of already supplied report fields. A trusted
     * NeoForge adapter must independently establish entity identity, frame,
     * calibration, STOP and continuous settling before invoking this method.
     */
    public ReferenceObservation predict(LaboratoryMountFrame.ReportedStagePose report,
                                        long expectedCurrentServerTick) {
        Objects.requireNonNull(report, "reported stage pose");
        Vec3 centre = laboratory.checkedReportedCenter(report,
                requiredDeviceId, requiredRevision, expectedCurrentServerTick, mountBlock);
        var face = FinitePlaneGaussianFlux.Disk.fromNormal(centre,
                frontNormalLab, transverseHintLab, apertureRadiusMetres);
        var predicted = FinitePlaneGaussianFlux.predict(beam, face);
        var offset = report.reportedPosition();
        return new ReferenceObservation(report.gameTick(), report.dimensionId(), report.deviceId(),
                report.revision(), offset.x(), offset.y(), offset.z(), predicted);
    }
}
