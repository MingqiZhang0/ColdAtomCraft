import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;
import java.util.List;

/** Decimal oracle driver: prints only reference scalar values, no test verdict. */
public final class CoherentReferenceProbe {
    static final double LAMBDA=780e-9, W0=0.0005, POWER=0.02;
    static final Vec3 O=new Vec3(0,0,0), X=new Vec3(1,0,0), Z=new Vec3(0,0,1), NZ=new Vec3(0,0,-1);
    static GaussianBeam scalar(Vec3 direction) { return new GaussianBeam(POWER,LAMBDA,W0,O,direction); }
    static JonesGaussianBeam beam(String id, Vec3 direction, JonesVector jones, double phase) {
        return new JonesGaussianBeam(id,"same-oscillator",scalar(direction),X,jones,phase);
    }
    static void emit(String key, double... values) {
        System.out.print(key);
        for(double v:values)System.out.print(","+Double.toString(v));
        System.out.println();
    }
    public static void main(String[] args) {
        JonesVector lin = JonesVector.linearRadians(Math.PI/9);
        emit("malus", JonesMatrix.idealLinearPolarizer(Math.PI/7).apply(lin).squaredNorm());
        JonesVector q = JonesMatrix.quarterWave(0).apply(JonesVector.linearRadians(Math.PI/4));
        emit("qwp",q.x().real(),q.y().imaginary(),q.squaredNorm());
        JonesVector h = JonesMatrix.halfWave(Math.PI/8).apply(JonesVector.horizontal());
        emit("hwp",h.x().real(),h.y().real(),h.squaredNorm());
        JonesVector calibrated=JonesMatrix.idealRetarder(Math.PI/2,Math.PI,Math.PI)
                .apply(JonesVector.horizontal());
        emit("commonphase",calibrated.x().real(),calibrated.x().imaginary(),calibrated.squaredNorm());
        JonesGaussianBeam a=beam("a",Z,JonesVector.horizontal(),0);
        JonesGaussianBeam b=beam("b",Z,JonesVector.horizontal(),0);
        JonesGaussianBeam p=beam("p",Z,JonesVector.horizontal(),Math.PI/2);
        JonesGaussianBeam v=beam("v",Z,JonesVector.vertical(),0);
        var both=CoherentOptics.coherentAtLabPoint(O,List.of(a,b));
        emit("coprop",both.electricIntensityEquivalentWattsPerSquareMeter(),both.timeAveragePoyntingWattsPerSquareMeter().z());
        var one=CoherentOptics.coherentAtLabPoint(O,List.of(a));
        emit("singleEnergy",one.electricEnergyDensityJoulesPerCubicMeter(),
                one.magneticEnergyDensityJoulesPerCubicMeter(),
                one.totalEnergyDensityJoulesPerCubicMeter());
        emit("quadrature",CoherentOptics.coherentAtLabPoint(O,List.of(a,p)).electricIntensityEquivalentWattsPerSquareMeter());
        emit("orthogonal",CoherentOptics.coherentAtLabPoint(O,List.of(a,v)).electricIntensityEquivalentWattsPerSquareMeter());
        JonesGaussianBeam counter=beam("counter",NZ,JonesVector.horizontal(),0);
        for (int i=0;i<3;i++) {
            double pos=(i==0?0:i==1?LAMBDA/8:LAMBDA/4);
            var result=CoherentOptics.coherentAtLabPoint(new Vec3(0,0,pos),List.of(a,counter));
            emit("standing"+i,result.electricIntensityEquivalentWattsPerSquareMeter(),
                    result.timeAveragePoyntingWattsPerSquareMeter().z());
            emit("standingEnergy"+i,result.electricEnergyDensityJoulesPerCubicMeter(),
                    result.magneticEnergyDensityJoulesPerCubicMeter(),
                    result.totalEnergyDensityJoulesPerCubicMeter());
        }
        var atX = new JonesGaussianBeam("x","same-oscillator",scalar(X),
                new Vec3(0,1,0),JonesVector.horizontal(),0);
        var cross=CoherentOptics.coherentAtLabPoint(O,List.of(beam("y",Z,JonesVector.vertical(),0),atX));
        emit("cross",cross.electricIntensityEquivalentWattsPerSquareMeter(),
                cross.timeAveragePoyntingWattsPerSquareMeter().x(),
                cross.timeAveragePoyntingWattsPerSquareMeter().y(),
                cross.timeAveragePoyntingWattsPerSquareMeter().z());
        var atWaist = beam("off-axis",Z,JonesVector.horizontal(),0.75)
                .sampleAtLabPoint(new Vec3(W0/3,0,0));
        emit("waistPhasor",atWaist.x().real(),atWaist.x().imaginary(),
                atWaist.electricIntensityEquivalentWattsPerSquareMeter());
    }
}
