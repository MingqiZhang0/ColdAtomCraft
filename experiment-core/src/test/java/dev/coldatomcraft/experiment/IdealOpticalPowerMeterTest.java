package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdealOpticalPowerMeterTest {
    private static ParaxialModeField input() {
        var plane=new GaussianPlane(0.02,780e-9,new GaussianQ(0,0.8));
        return ParaxialModeField.fromPlane("owner-calibrated-phase-placeholder",
                "port-frame-placeholder",plane,
                new JonesVector(ComplexAmplitude.ONE,ComplexAmplitude.ZERO),0);
    }

    @Test void givesWattsAndExplicitlyIdealObservable() {
        var meter=new IdealOpticalPowerMeter("power-meter-1",0.0004);
        var result=meter.sampleMode(input(),0,123456789);
        assertEquals("power-meter-1",result.observation().deviceId());
        assertEquals("W",result.observation().siUnit());
        assertEquals("ideal_collected_optical_power",result.observation().observable());
        assertEquals(0,result.observation().standardUncertainty(),0);
        assertEquals(123456789,result.observation().simulatedTimeNanos());
        assertTrue(result.observation().value()>0 && result.observation().value()<0.02);
    }

    @Test void detectorSamplingDoesNotModifyOrConsumeTheOpticalMode() {
        var mode=input();
        var result=new IdealOpticalPowerMeter("detector",0.0003).sampleMode(mode,0,0);
        assertEquals(0.02,mode.powerWatts(),1e-15);
        assertEquals(0.02,result.apertureBudget().incidentPowerWatts(),1e-15);
        assertEquals(0.02,result.apertureBudget().capturedPowerWatts()
                +result.apertureBudget().uncapturedPowerWatts(),1e-15);
    }

    @Test void coherentMachZehnderOutputsProduceComplementaryDetectorReadings() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var arms=splitter.split(input());
        var meter0=new IdealOpticalPowerMeter("output0",0.0004);
        var meter1=new IdealOpticalPowerMeter("output1",0.0004);
        double fraction=GaussianCircularAperture.capturedFraction(input().q().radiusMeters(780e-9),0.0004,0);
        for(double delta:new double[]{0,Math.PI/6,Math.PI/2,Math.PI}) {
            var out=splitter.scatter(arms.output0(),arms.output1().withUniformPhaseRadians(delta));
            var read0=meter0.sampleMode(out.output0(),0,0);
            var read1=meter1.sampleMode(out.output1(),0,0);
            assertEquals(0.02*fraction,read0.observation().value()+read1.observation().value(),2e-13);
            assertEquals(0.02*fraction*Math.pow(Math.sin(delta/2),2),read0.observation().value(),2e-13);
            assertEquals(0.02*fraction*Math.pow(Math.cos(delta/2),2),read1.observation().value(),2e-13);
        }
    }

    @Test void centeredDetectorReadsPowerAfterIdealSplitInTwoPorts() {
        var split=new IdealTwoPortBeamSplitter(0.5).split(input());
        var meter=new IdealOpticalPowerMeter("meter",0.001);
        var r0=meter.sampleMode(split.output0(),0,0);
        var r1=meter.sampleMode(split.output1(),0,0);
        assertEquals(r0.observation().value(),r1.observation().value(),1e-15);
        assertEquals(0.02*r0.apertureBudget().capturedFraction(),
                r0.observation().value()+r1.observation().value(),1e-14);
    }

    @Test void movingDetectorSidewaysChangesTheReading() {
        var meter=new IdealOpticalPowerMeter("meter",0.0003);
        var centered=meter.sampleMode(input(),0,0);
        var misaligned=meter.sampleMode(input(),0.0005,0);
        assertTrue(centered.observation().value()>misaligned.observation().value());
    }

    @Test void freePropagationChangesFiniteApertureCollectionNotTotalPower() {
        var mode=input();
        var moved=mode.freeSpace(5);
        var meter=new IdealOpticalPowerMeter("meter",0.0003);
        var a=meter.sampleMode(mode,0,0);
        var b=meter.sampleMode(moved,0,1);
        assertEquals(mode.powerWatts(),moved.powerWatts(),1e-14);
        assertTrue(a.observation().value()>b.observation().value());
    }

    @Test void faceAndModeMeasurementsAgreeAtCorrespondingGaussianPlane() {
        var beam=new GaussianBeam(0.02,780e-9,0.0005,new Vec3(0,0,0),new Vec3(0,0,1));
        double z=0.05;
        var face=new IdealCircularDetectorFace(new Vec3(0.0001,0,z),new Vec3(0,0,-1),0.0003);
        var plane=GaussianPlane.atAxialDistance(beam,z);
        var mode=ParaxialModeField.fromPlane("laser", "port", plane,
                new JonesVector(ComplexAmplitude.ONE,ComplexAmplitude.ZERO),0);
        var meter=new IdealOpticalPowerMeter("meter",0.0003);
        assertEquals(meter.sampleMode(mode,0.0001,0).observation().value(),
                meter.sampleAtFace(beam,face,0).observation().value(),1e-14);
    }

    @Test void mismatchedDetectorFaceRadiusIsRejected() {
        var beam=new GaussianBeam(0.02,780e-9,0.0005,new Vec3(0,0,0),new Vec3(0,0,1));
        var face=new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-1),0.0004);
        var meter=new IdealOpticalPowerMeter("meter",0.0005);
        assertThrows(IllegalArgumentException.class,()->meter.sampleAtFace(beam,face,0));
    }

    @Test void invalidMeterAndTimeAndFieldAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new IdealOpticalPowerMeter("",0.01));
        assertThrows(IllegalArgumentException.class,()->new IdealOpticalPowerMeter("x",0));
        assertThrows(IllegalArgumentException.class,()->new IdealOpticalPowerMeter("x",Double.POSITIVE_INFINITY));
        var meter=new IdealOpticalPowerMeter("meter",0.001);
        assertThrows(IllegalArgumentException.class,()->meter.sampleMode(input(),0,-1));
        assertThrows(IllegalArgumentException.class,()->meter.sampleMode(input(),-1,0));
        assertThrows(IllegalArgumentException.class,()->meter.sampleMode(null,0,0));
        assertThrows(IllegalArgumentException.class,()->meter.sampleAtFace(null,null,0));
    }

    @Test void idealPowerReadingHasNoServerOrModelApprovalAuthority() {
        var reading=new IdealOpticalPowerMeter("fake-detector",0.001).sampleMode(input(),0,0);
        assertEquals("ideal_collected_optical_power",reading.observation().observable());
        assertFalse(reading.observation().observable().equals("OWNER_APPROVED"));
    }
}
