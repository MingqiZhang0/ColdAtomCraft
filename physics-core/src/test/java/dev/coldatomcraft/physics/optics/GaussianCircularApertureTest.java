package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaussianCircularApertureTest {
    @Test void centeredOneSpotRadiusUsesClosedFormIntegral() {
        assertEquals(1-Math.exp(-2), GaussianCircularAperture.capturedFraction(0.001,0.001,0),1e-15);
        assertEquals(1-Math.exp(-1), GaussianCircularAperture.capturedFraction(0.001,0.001/Math.sqrt(2),0),1e-15);
    }

    @Test void centeredApertureMatchesExistingGaussianPlaneReference() {
        var plane = new GaussianPlane(0.02,780e-9,new GaussianQ(0.1,0.8));
        double radius=plane.radiusMeters();
        for(double fraction:new double[]{0.1,0.25,0.5,1,2,4}) {
            var reading=GaussianCircularAperture.capture(plane,radius*fraction,0);
            assertEquals(plane.powerWithinRadiusWatts(radius*fraction),reading.capturedPowerWatts(),1e-15);
            assertEquals(plane.powerWatts(),reading.capturedPowerWatts()+reading.uncapturedPowerWatts(),1e-15);
        }
    }

    @Test void displacedApertureHasCorrectNoncentralChiSquareReference() {
        // Independent reference: noncentral chi-square CDF with 2 dof,
        // noncentrality 4(d/w)^2 at threshold 4(a/w)^2.
        assertEquals(0.2671201962031797,GaussianCircularAperture.capturedFraction(1,0.5,0.5),2e-11);
    }

    @Test void finiteOffsetMustReduceFixedAperturePower() {
        double aligned=GaussianCircularAperture.capturedFraction(1,1,0);
        double modest=GaussianCircularAperture.capturedFraction(1,1,0.5);
        double farther=GaussianCircularAperture.capturedFraction(1,1,1.25);
        assertTrue(aligned>modest);
        assertTrue(modest>farther);
        assertTrue(farther>0);
    }

    @Test void largerApertureCannotCollectLessPower() {
        double previous=0;
        for(double a:new double[]{0.05,0.1,0.3,0.8,1,1.5,3,6}) {
            double current=GaussianCircularAperture.capturedFraction(1,a,0.7);
            assertTrue(current>=previous-1e-13);
            previous=current;
        }
    }

    @Test void diskContainingEntireSignificantBeamReturnsOneWithinTailBound() {
        assertEquals(1,GaussianCircularAperture.capturedFraction(1,20,2),0);
        assertEquals(0,GaussianCircularAperture.capturedFraction(1,1,20),0);
    }

    @Test void concentricZeroRadiusHasZeroCapture() {
        assertEquals(0,GaussianCircularAperture.capturedFraction(1,0,0),0);
        assertEquals(0,GaussianCircularAperture.capturedFraction(1,0,1),0);
    }

    @Test void tinyOffsetAndTinyDiskAreNumericallyRepresentable() {
        assertEquals(2e-20,GaussianCircularAperture.capturedFraction(1,1e-10,1e-10),1e-32);
    }

    @Test void nearTangentAperturesRetainNonzeroPowerAndNoOvercollection() {
        for(double d:new double[]{5.999,6,6.001,9.999,10,10.001}) {
            double value=GaussianCircularAperture.capturedFraction(1,10,d);
            assertTrue(value>=0 && value<=1);
        }
    }

    @Test void identicalScaledGeometriesHaveIdenticalFraction() {
        double original=GaussianCircularAperture.capturedFraction(0.002,0.001,0.0012);
        double rescaled=GaussianCircularAperture.capturedFraction(2,1,1.2);
        assertEquals(original,rescaled,1e-14);
    }

    @Test void zeroIncidentPowerStaysZeroWithFiniteAperture() {
        var result=GaussianCircularAperture.capture(0,0.001,0.002,0.0003);
        assertEquals(0,result.capturedPowerWatts());
        assertEquals(0,result.uncapturedPowerWatts());
        assertEquals(0,result.capturedFraction());
    }

    @Test void budgetHasCapturedPlusUncapturedButNeverClaimsHeat() {
        var result=GaussianCircularAperture.capture(1.5,0.001,0.0004,0.0001);
        assertTrue(result.capturedPowerWatts()>0 && result.capturedPowerWatts()<1.5);
        assertEquals(1.5,result.capturedPowerWatts()+result.uncapturedPowerWatts(),1e-14);
    }

    @Test void invalidParametersAreRejectedWithoutClamping() {
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(0,1,0));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(-1,1,0));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(1,-1,0));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(1,1,-1));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(1,1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(Double.NaN,1,0));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capture(-1,1,1,0));
        assertThrows(IllegalArgumentException.class,()->GaussianCircularAperture.capturedFraction(1e-300,1e300,0));
    }
}
