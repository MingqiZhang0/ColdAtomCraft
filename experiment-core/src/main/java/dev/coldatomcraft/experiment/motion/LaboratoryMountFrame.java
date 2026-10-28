package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.FinitePlaneGaussianFlux;
import dev.coldatomcraft.physics.optics.RayFrame3;

import java.util.Objects;

/**
 * D22D explicit transform between BLOCK ADDRESSES and a small, local SI lab.
 * The chosen metres-per-block factor and orientation are DECLARED modelling
 * parameters, not metrology, encoder calibration or an Owner-approved scale.
 *
 * <p>Integer world coordinates are first SUBTRACTED from the anchor, then
 * converted to metres. Never multiply absolute Minecraft coordinates by a
 * floating scale and subtract them: distant bases can erase micrometre shifts.
 * Even block-relative doubles are rejected beyond a conservative local span.</p>
 */
public record LaboratoryMountFrame(String dimensionId, BlockAddress originBlock,
                                   RayFrame3 axes, double metresPerBlock) {
    public static final long MAX_RELATIVE_BLOCK_SPAN = 4096;
    public static final long MAX_OFFSET_NANOMETRES = 1_000_000_000_000L;

    public record BlockAddress(long x, long y, long z) { }

    /** A REPORTED or DECLARED displacement, never proof of real motion. */
    public record NanometreOffset(long x, long y, long z) {
        public NanometreOffset {
            if (x < -MAX_OFFSET_NANOMETRES || x > MAX_OFFSET_NANOMETRES
                    || y < -MAX_OFFSET_NANOMETRES || y > MAX_OFFSET_NANOMETRES
                    || z < -MAX_OFFSET_NANOMETRES || z > MAX_OFFSET_NANOMETRES) {
                throw new IllegalArgumentException("nanometre displacement outside model range");
            }
        }
        public static NanometreOffset zero() { return new NanometreOffset(0, 0, 0); }
    }

    public LaboratoryMountFrame {
        if (dimensionId == null || dimensionId.isBlank() || dimensionId.length() > 128) {
            throw new IllegalArgumentException("dimension ID required");
        }
        Objects.requireNonNull(originBlock, "origin block");
        Objects.requireNonNull(axes, "lab axes");
        if (!Double.isFinite(metresPerBlock) || metresPerBlock < 1e-3
                || metresPerBlock > 10) {
            throw new IllegalArgumentException("invalid declared metres per block");
        }
    }

    /** Explicit nominal Create/Minecraft-like 1 block = 1 m PREVIEW assumption. */
    public static LaboratoryMountFrame nominalMinecraftMetres(String dimensionId,
                                                               BlockAddress originBlock) {
        RayFrame3 worldBasis = RayFrame3.fromDirectionAndXAxis(
                new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        return new LaboratoryMountFrame(dimensionId, originBlock, worldBasis, 1);
    }

    /** Block CENTRE relative to the centre of the origin block. */
    public Vec3 blockCenter(BlockAddress block, NanometreOffset displacement) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(displacement, "displacement");
        long dx = Math.subtractExact(block.x(), originBlock.x());
        long dy = Math.subtractExact(block.y(), originBlock.y());
        long dz = Math.subtractExact(block.z(), originBlock.z());
        if (dx < -MAX_RELATIVE_BLOCK_SPAN || dx > MAX_RELATIVE_BLOCK_SPAN
                || dy < -MAX_RELATIVE_BLOCK_SPAN || dy > MAX_RELATIVE_BLOCK_SPAN
                || dz < -MAX_RELATIVE_BLOCK_SPAN || dz > MAX_RELATIVE_BLOCK_SPAN) {
            throw new IllegalArgumentException("block too far from local lab anchor");
        }
        // Millimetres and nanometres are converted ONLY after integer block
        // subtraction; translation is applied along the declared local axes.
        double x = dx * metresPerBlock + displacement.x() * 1e-9;
        double y = dy * metresPerBlock + displacement.y() * 1e-9;
        double z = dz * metresPerBlock + displacement.z() * 1e-9;
        // A declared nanometre coordinate must not be swallowed by double ULP.
        // For huge geometry the caller must create a nearer local origin.
        if (Math.max(Math.ulp(x), Math.max(Math.ulp(y), Math.ulp(z))) > 0.5e-9) {
            throw new IllegalArgumentException("lab span too large for nanometre representation");
        }
        return axes.xAxis().scale(x).add(axes.yAxis().scale(y))
                .add(axes.direction().scale(z));
    }

    public FinitePlaneGaussianFlux.Disk detector(BlockAddress block,
                                                 NanometreOffset reportedPosition,
                                                 Vec3 frontNormalLab,
                                                 Vec3 transverseHintLab,
                                                 double apertureRadiusMetres) {
        return FinitePlaneGaussianFlux.Disk.fromNormal(
                blockCenter(block, reportedPosition), frontNormalLab,
                transverseHintLab, apertureRadiusMetres);
    }

    /** The frame never certifies telemetry: caller must bind trusted device state. */
    public record ReportedStagePose(String dimensionId, String deviceId, long revision, long gameTick,
                                    BlockAddress mountedBlock, NanometreOffset reportedPosition,
                                    boolean motorStopped, boolean chunkLoaded) {
        public ReportedStagePose {
            if (dimensionId == null || dimensionId.isBlank() || dimensionId.length() > 128
                    || deviceId == null || deviceId.isBlank() || deviceId.length() > 128
                    || revision < 0 || gameTick < 0) {
                throw new IllegalArgumentException("invalid stage report identity");
            }
            Objects.requireNonNull(mountedBlock, "mounted block");
            Objects.requireNonNull(reportedPosition, "reported position");
        }
    }

    /**
     * Read-only integrity gate for a report provided by a FUTURE trusted server
     * adapter. Passing does not authenticate the report's actual origin and
     * does not prove sufficient settling time. D22A owns settling logic.
     */
    public Vec3 checkedReportedCenter(ReportedStagePose report, String expectedDeviceId,
                                      long expectedRevision, long expectedGameTick,
                                      BlockAddress expectedBlock) {
        Objects.requireNonNull(report, "stage report");
        if (!dimensionId.equals(report.dimensionId())
                || !report.deviceId().equals(expectedDeviceId)
                || report.revision() != expectedRevision
                || report.gameTick() != expectedGameTick
                || !report.mountedBlock().equals(expectedBlock)
                || !report.motorStopped() || !report.chunkLoaded()) {
            throw new IllegalStateException("untrusted or stale stage observation");
        }
        return blockCenter(report.mountedBlock(), report.reportedPosition());
    }
}
