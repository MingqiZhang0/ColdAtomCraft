package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.RayFrame3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D22D declared lab coordinate and untrusted pose checks. */
class LaboratoryMountFrameTest {
    private static LaboratoryMountFrame.BlockAddress world(long x, long y, long z) {
        return new LaboratoryMountFrame.BlockAddress(x,y,z);
    }

    @Test void distantWorldAnchorPreservesLocalMicrometreDisplacements() {
        var origin=world(29_999_999,80,-29_999_999);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",origin);
        var centre=frame.blockCenter(origin,
                new LaboratoryMountFrame.NanometreOffset(123_456,-90_000,0));
        assertEquals(123_456e-9,centre.x(),1e-15);
        assertEquals(-90_000e-9,centre.y(),1e-15);
        assertEquals(0,centre.z(),0);
    }

    @Test void blockPitchAndLocalOffsetsRemainIndependent() {
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",
                world(2,4,6));
        var centre=frame.blockCenter(world(3,4,6),
                new LaboratoryMountFrame.NanometreOffset(1_000,0,0));
        assertEquals(1.000001,centre.x(),1e-15);
    }

    @Test void rotatedFramesTransformBlockCenters() {
        var frame=new LaboratoryMountFrame("minecraft:overworld",world(0,0,0),
                RayFrame3.fromDirectionAndXAxis(new Vec3(1,0,0),new Vec3(0,1,0)),1);
        var result=frame.blockCenter(world(0,0,1),
                LaboratoryMountFrame.NanometreOffset.zero());
        assertEquals(1,result.x(),1e-14);
        assertEquals(0,result.y(),1e-14);
        assertEquals(0,result.z(),1e-14);
    }

    @Test void extremelyLargeOrOverflowedBlockOffsetsAreRejected() {
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",
                world(0,0,0));
        assertThrows(IllegalArgumentException.class,
                () -> frame.blockCenter(world(Long.MIN_VALUE,0,0),
                        LaboratoryMountFrame.NanometreOffset.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new LaboratoryMountFrame.NanometreOffset(Long.MIN_VALUE,0,0));
        assertThrows(ArithmeticException.class,
                () -> new LaboratoryMountFrame("dim",world(Long.MAX_VALUE,0,0),
                        frame.axes(),1).blockCenter(world(Long.MIN_VALUE,0,0),
                        LaboratoryMountFrame.NanometreOffset.zero()));
    }

    @Test void stageReportIdentityRevisionAndFreshnessAreChecked() {
        var mount=world(0,0,1);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",
                world(0,0,0));
        var report=new LaboratoryMountFrame.ReportedStagePose("minecraft:overworld","stage-001",4,100,
                mount,new LaboratoryMountFrame.NanometreOffset(300_000,0,0),true,true);
        assertEquals(.0003,frame.checkedReportedCenter(report,"stage-001",4,100,mount).x(),
                1e-15);
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(report,"stage-001",5,100,mount));
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(report,"stage-001",4,101,mount));
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(report,"other",4,100,mount));
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(
                        new LaboratoryMountFrame.ReportedStagePose("minecraft:the_nether",
                                "stage-001",4,100,mount,report.reportedPosition(),true,true),
                        "stage-001",4,100,mount));
    }

    @Test void movingOrUnloadedStageIsNotUsableForReference() {
        var mount=world(0,0,1);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",
                world(0,0,0));
        var pose=new LaboratoryMountFrame.NanometreOffset(0,0,0);
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(new LaboratoryMountFrame.ReportedStagePose(
                        "minecraft:overworld","stage",1,2,mount,pose,false,true),"stage",1,2,mount));
        assertThrows(IllegalStateException.class,
                () -> frame.checkedReportedCenter(new LaboratoryMountFrame.ReportedStagePose(
                        "minecraft:overworld","stage",1,2,mount,pose,true,false),"stage",1,2,mount));
    }

    @Test void combiningReportedStageAndOpticsStillProducesReferenceOnly() {
        var block=world(0,0,1);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",
                world(0,0,0));
        var beam=new GaussianBeam(.02,780e-9,.0005,new Vec3(0,0,0),
                new Vec3(0,0,1));
        var predictor=new StageSurfacePrediction(frame,block,"stage",1,beam,
                new Vec3(0,0,-1),new Vec3(1,0,0),.00065);
        var report=new LaboratoryMountFrame.ReportedStagePose("minecraft:overworld","stage",1,300,
                block,new LaboratoryMountFrame.NanometreOffset(300_000,0,0),true,true);
        var point=predictor.predict(report,300);
        assertEquals("REFERENCE_ONLY",point.provenance());
        assertTrue(point.idealPower().capturedWatts()>0);
        assertTrue(point.idealPower().capturedWatts()<.02);
        assertThrows(IllegalStateException.class,
                () -> predictor.predict(report,301));
    }
}
