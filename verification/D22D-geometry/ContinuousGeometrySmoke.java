package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.motion.LaboratoryMountFrame;
import dev.coldatomcraft.experiment.motion.StageSurfacePrediction;
import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.FinitePlaneGaussianFlux;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.GaussianCircularAperture;

import java.util.Random;

/** D22D independent geometry and flux checks. NOT real game/encoder evidence. */
public final class ContinuousGeometrySmoke {
    private static long assertions;
    private ContinuousGeometrySmoke() { }
    private static void check(boolean predicate, String label) {
        assertions++;
        if (!predicate) throw new AssertionError(label);
    }
    private static void near(double got, double expected, double tolerance, String label) {
        check(Double.isFinite(got) && Math.abs(got - expected) <= tolerance,
                label + " got=" + got + " expected=" + expected);
    }
    private static void rejects(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException
                | ArithmeticException expected) { rejected = true; }
        check(rejected, label);
    }
    private static GaussianBeam beam(double watts) {
        return new GaussianBeam(watts, 780e-9, 500e-6,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
    }
    private static FinitePlaneGaussianFlux.Disk disk(double offsetX, double offsetY,
                                                    double angleRadians, double radius) {
        Vec3 normal = new Vec3(Math.sin(angleRadians), 0, -Math.cos(angleRadians));
        return FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(offsetX,offsetY,1),
                normal, new Vec3(0,1,0), radius);
    }

    /** Independent radial/azimuthal Simpson integration; not equal-area cells. */
    private static double independentSurfaceIntegral(GaussianBeam beam,
                                                     FinitePlaneGaussianFlux.Disk disk) {
        int radial = 320, angular = 256;
        double deltaR = disk.radiusMetres() / radial;
        double deltaPhi = 2 * Math.PI / angular;
        double sum = 0;
        double incomingCosine = -disk.facing().direction().dot(beam.directionLab());
        for (int i = 0; i <= radial; i++) {
            double r = i * deltaR;
            double rw = (i == 0 || i == radial) ? 1 : (i % 2 == 0 ? 2 : 4);
            for (int j = 0; j <= angular; j++) {
                double angle = j * deltaPhi;
                double aw = (j == 0 || j == angular) ? 1 : (j % 2 == 0 ? 2 : 4);
                Vec3 point = disk.center().add(disk.facing().xAxis().scale(r*Math.cos(angle)))
                        .add(disk.facing().yAxis().scale(r*Math.sin(angle)));
                double intensity = beam.intensityAtLabPointWattsPerSquareMeter(point);
                sum += rw * aw * r * intensity;
            }
        }
        return sum * deltaR * deltaPhi / 9 * incomingCosine;
    }

    public static void main(String[] args) {
        var beam = beam(.02);
        var normal = disk(0,0,0,650e-6);
        var c = FinitePlaneGaussianFlux.predict(beam, normal);
        near(c.capturedWatts(), beam.powerWithinRadiusWatts(650e-6,1), 1e-14,
                "coaxial analytic power");
        check(c.method()==FinitePlaneGaussianFlux.Method.NORMAL_ANALYTIC,
                "perpendicular aperture uses existing Gaussian integral");
        check(c.axisIntersectsDisk(), "coaxial optical axis passes disk");
        near(c.capturedWatts()+c.uncapturedWatts(),beam.powerWatts(),1e-14,
                "passive normal power budget");
        check("REFERENCE_ONLY".equals(c.provenance()),"never label prediction as measurement");

        var noAxis=disk(.0015,0,Math.PI/6,650e-6);
        var tails=FinitePlaneGaussianFlux.predict(beam,noAxis);
        check(!tails.axisIntersectsDisk(),"optical axis misses disk");
        check(tails.capturedWatts()>0,"axis miss is NOT zero Gaussian tail flux");
        var crossing=FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,0),
                new Vec3(0,0,1),noAxis);
        check(crossing.status()==FinitePlaneGaussianFlux.CrossingStatus.FORWARD,
                "geometric axis hits front plane");
        near(crossing.diskOffsetMetres(),tails.axisOffsetMetres(),2e-12,
                "independent plane axis geometry agrees with flux centre");
        rejects(() -> FinitePlaneGaussianFlux.predict(beam,
                FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(0,0,1),
                        new Vec3(0,0,1),new Vec3(1,0,0),.00065)),"backside rejected");
        rejects(() -> FinitePlaneGaussianFlux.predict(beam,
                FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(0,0,1),
                        new Vec3(1,0,-.1),new Vec3(0,1,0),.00065)),"grazing rejected");
        check(FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,0),
                new Vec3(1,0,0),normal).status()
                == FinitePlaneGaussianFlux.CrossingStatus.PARALLEL,"parallel ray status");
        check(FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,2),
                new Vec3(0,0,1),normal).status()
                == FinitePlaneGaussianFlux.CrossingStatus.BEHIND_START,
                "behind source crossing does not get turned into a hit");

        // Independent numerical integration on a tilted disk. Quadrature
        // parameterisation and integration algorithm differ from production.
        for (double deg : new double[]{5, 15, 30, 45, 60}) {
            for (double offset : new double[]{0,.0003,.0009,.0015}) {
                var face=disk(offset,.0001,Math.toRadians(deg),.00065);
                var result=FinitePlaneGaussianFlux.predict(beam,face);
                double expected=independentSurfaceIntegral(beam,face);
                near(result.capturedWatts(), expected, 3.5e-7,
                        "independent oblique Simpson Gaussian surface flux");
                check(result.capturedWatts()>=0&&result.capturedWatts()<=beam.powerWatts(),
                        "oblique finite aperture conserves passive source budget");
                near(result.capturedWatts()+result.uncapturedWatts(),beam.powerWatts(),1e-14,
                        "oblique power account exact");
                check(result.method()==FinitePlaneGaussianFlux.Method.OBLIQUE_PARAXIAL_QUADRATURE,
                        "method and approximation visible");
            }
        }
        Random random=new Random(0xD22D2026L);
        double radius=beam.radiusMeters(1.0);
        for (int i=0;i<5_000;i++) {
            double dx=(random.nextDouble()*2-1)*.003;
            double dy=(random.nextDouble()*2-1)*.003;
            var face=disk(dx,dy,0,.00065);
            var result=FinitePlaneGaussianFlux.predict(beam,face);
            double f=GaussianCircularAperture.capturedFraction(radius,.00065,
                    Math.hypot(dx,dy));
            near(result.spatialFraction(),f,5e-12,"D22C Gaussian offset equivalence");
            check(result.capturedWatts()>=0 && result.capturedWatts()<=beam.powerWatts(),
                    "normal random passivity");
            near(result.capturedWatts()+result.uncapturedWatts(),beam.powerWatts(),1e-14,
                    "random normal power conservation");
        }
        var base=new LaboratoryMountFrame.BlockAddress(29_999_999,80,-29_999_999);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",base);
        var same=frame.blockCenter(base,
                new LaboratoryMountFrame.NanometreOffset(123_456,0,-987_654));
        near(same.x(),123_456e-9,1e-16,"large world anchor maintains sub-mm offset");
        near(same.z(),-987_654e-9,1e-16,"large world anchor uses integer subtraction");
        var neighbor=frame.blockCenter(new LaboratoryMountFrame.BlockAddress(
                30_000_000,80,-29_999_999),
                new LaboratoryMountFrame.NanometreOffset(1000,0,0));
        near(neighbor.x(),1.000001,1e-15,"one block nominal pitch + micron");
        rejects(() -> new LaboratoryMountFrame.NanometreOffset(Long.MIN_VALUE,0,0),
                "Long.MIN_VALUE displacement rejected");
        rejects(() -> frame.blockCenter(new LaboratoryMountFrame.BlockAddress(
                -29_999_999,80,-29_999_999),LaboratoryMountFrame.NanometreOffset.zero()),
                "large local span rejected");
        rejects(() -> frame.blockCenter(new LaboratoryMountFrame.BlockAddress(
                Long.MIN_VALUE,80,-29_999_999),LaboratoryMountFrame.NanometreOffset.zero()),
                "block subtraction overflow rejected");

        var localBase=new LaboratoryMountFrame.BlockAddress(0,0,0);
        var localFrame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",localBase);
        var reported=new LaboratoryMountFrame.ReportedStagePose("minecraft:overworld","stage-001",3,1<<20,
                new LaboratoryMountFrame.BlockAddress(0,0,1),
                new LaboratoryMountFrame.NanometreOffset(300_000,0,0),true,true);
        var predictor=new StageSurfacePrediction(localFrame,reported.mountedBlock(),
                "stage-001",3,beam,new Vec3(0,0,-1),new Vec3(1,0,0),.00065);
        var predicted=predictor.predict(reported,1<<20);
        check("REFERENCE_ONLY".equals(predicted.provenance()),
                "reported encoder pose never authenticates power measurements");
        near(predicted.idealPower().capturedWatts(),
                FinitePlaneGaussianFlux.predict(beam,disk(.0003,0,0,.00065)).capturedWatts(),
                1e-14,"reported local displacement changes reference optical capture");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:the_nether","stage-001",3,1<<20,reported.mountedBlock(),
                reported.reportedPosition(),true,true),1<<20),"wrong dimension rejected");
        rejects(() -> predictor.predict(reported,(1<<20)+1),"wrong tick rejected");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:overworld","other-stage",3,1<<20,reported.mountedBlock(),
                reported.reportedPosition(),true,true),1<<20),"identity mismatch rejected");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:overworld","stage-001",4,1<<20,reported.mountedBlock(),
                reported.reportedPosition(),true,true),1<<20),"revision changed rejected");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:overworld","stage-001",3,1<<20,reported.mountedBlock(),
                reported.reportedPosition(),false,true),1<<20),"moving stage rejected");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:overworld","stage-001",3,1<<20,reported.mountedBlock(),
                reported.reportedPosition(),true,false),1<<20),"unloaded chunk rejected");
        rejects(() -> predictor.predict(new LaboratoryMountFrame.ReportedStagePose(
                "minecraft:overworld","stage-001",3,1<<20,localBase,
                reported.reportedPosition(),true,true),1<<20),"mount identity changed rejected");
        System.out.println("D22D ContinuousGeometrySmoke PASS " + assertions + "/" + assertions);
    }
}
