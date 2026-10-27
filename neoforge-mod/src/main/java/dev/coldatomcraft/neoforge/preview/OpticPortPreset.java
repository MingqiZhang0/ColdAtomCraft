package dev.coldatomcraft.neoforge.preview;

import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Twenty-four explicit source-facing port arrangements; persisted as blockstates. */
public enum OpticPortPreset implements StringRepresentable {
    NORTH_0(PreviewOpticalNetwork3D.Direction3.NORTH, 0),
    NORTH_1(PreviewOpticalNetwork3D.Direction3.NORTH, 1),
    NORTH_2(PreviewOpticalNetwork3D.Direction3.NORTH, 2),
    NORTH_3(PreviewOpticalNetwork3D.Direction3.NORTH, 3),
    EAST_0(PreviewOpticalNetwork3D.Direction3.EAST, 0),
    EAST_1(PreviewOpticalNetwork3D.Direction3.EAST, 1),
    EAST_2(PreviewOpticalNetwork3D.Direction3.EAST, 2),
    EAST_3(PreviewOpticalNetwork3D.Direction3.EAST, 3),
    SOUTH_0(PreviewOpticalNetwork3D.Direction3.SOUTH, 0),
    SOUTH_1(PreviewOpticalNetwork3D.Direction3.SOUTH, 1),
    SOUTH_2(PreviewOpticalNetwork3D.Direction3.SOUTH, 2),
    SOUTH_3(PreviewOpticalNetwork3D.Direction3.SOUTH, 3),
    WEST_0(PreviewOpticalNetwork3D.Direction3.WEST, 0),
    WEST_1(PreviewOpticalNetwork3D.Direction3.WEST, 1),
    WEST_2(PreviewOpticalNetwork3D.Direction3.WEST, 2),
    WEST_3(PreviewOpticalNetwork3D.Direction3.WEST, 3),
    UP_0(PreviewOpticalNetwork3D.Direction3.UP, 0),
    UP_1(PreviewOpticalNetwork3D.Direction3.UP, 1),
    UP_2(PreviewOpticalNetwork3D.Direction3.UP, 2),
    UP_3(PreviewOpticalNetwork3D.Direction3.UP, 3),
    DOWN_0(PreviewOpticalNetwork3D.Direction3.DOWN, 0),
    DOWN_1(PreviewOpticalNetwork3D.Direction3.DOWN, 1),
    DOWN_2(PreviewOpticalNetwork3D.Direction3.DOWN, 2),
    DOWN_3(PreviewOpticalNetwork3D.Direction3.DOWN, 3);
    private final PreviewOpticalNetwork3D.Direction3 incoming;
    private final int selector;

    OpticPortPreset(PreviewOpticalNetwork3D.Direction3 incoming, int selector) {
        this.incoming = incoming;
        this.selector = selector;
    }
    public PreviewOpticalNetwork3D.Direction3 incoming() { return incoming; }
    public PreviewOpticalNetwork3D.Direction3 reflected() { return incoming.perpendicularOutput(selector); }
    public int selector() { return selector; }
    public double focalLengthMeters() {
        return switch (selector) { case 0 -> 0.5; case 1 -> 1.0; case 2 -> 2.0; default -> -1.0; };
    }
    public double waveplateAxisRadians() { return selector * Math.PI / 8.0; }
    public static OpticPortPreset of(PreviewOpticalNetwork3D.Direction3 input, int selector) {
        for (OpticPortPreset value : values()) {
            if (value.incoming == input && value.selector == selector) return value;
        }
        throw new IllegalArgumentException("unknown 3D optical port orientation");
    }
    public OpticPortPreset nextSelector() { return of(incoming, (selector + 1) % 4); }
    public OpticPortPreset nextIncoming() {
        var values = PreviewOpticalNetwork3D.Direction3.values();
        return of(values[(incoming.ordinal() + 1) % values.length], selector);
    }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
