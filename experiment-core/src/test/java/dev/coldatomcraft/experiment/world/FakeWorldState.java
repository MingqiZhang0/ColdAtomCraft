package dev.coldatomcraft.experiment.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minecraft-free test double standing in for a server world snapshot.
 * It deliberately models only what the contract consumes: chunk load epochs,
 * content revisions, device revision and target position/revision.
 */
final class FakeWorldState {
    static final String DIMENSION = "minecraft:overworld";
    static final int DEVICE_X = 8;
    static final int DEVICE_Y = 64;
    static final int DEVICE_Z = 8;
    static final int TARGET_CHUNK_X = 1;
    static final int TARGET_CHUNK_Z = 0;

    private final Map<ChunkKey, Chunk> chunks = new LinkedHashMap<>();
    private String deviceId = "coldatomcraft:laser_source";
    private String targetId = "entity:42";
    private TargetKind targetKind = TargetKind.ENTITY;
    private long deviceRevision = 20;
    private long targetRevision = 30;
    private int deviceY = DEVICE_Y;
    private double targetX = 20.5;
    private double targetY = 65;
    private double targetZ = 8.5;
    private boolean deviceLoaded = true;
    private boolean targetLoaded = true;

    FakeWorldState() {
        chunks.put(new ChunkKey(0, 0), new Chunk(1, 10));
        chunks.put(new ChunkKey(TARGET_CHUNK_X, TARGET_CHUNK_Z), new Chunk(1, 11));
        chunks.put(new ChunkKey(2, 2), new Chunk(1, 12));
    }

    WorldReadSet readSet() {
        return new WorldReadSet(chunks.entrySet().stream()
                .map(entry -> new WorldRevision(DIMENSION, entry.getKey().x(), entry.getKey().z(),
                        entry.getValue().loadEpoch, entry.getValue().contentRevision, entry.getValue().loaded))
                .toList());
    }

    /**
     * Current snapshot after unloads contains only chunks that are still loaded.
     * A candidate captured earlier retains its older, complete read set.
     */
    WorldReadSet loadedReadSet() {
        return new WorldReadSet(chunks.entrySet().stream()
                .filter(entry -> entry.getValue().loaded)
                .map(entry -> new WorldRevision(DIMENSION, entry.getKey().x(), entry.getKey().z(),
                        entry.getValue().loadEpoch, entry.getValue().contentRevision, true))
                .toList());
    }

    ServerWorldSnapshot snapshot() {
        return new ServerWorldSnapshot(loadedReadSet(), device(), target());
    }

    DeviceSnapshot device() {
        return new DeviceSnapshot(DIMENSION, DEVICE_X, deviceY, DEVICE_Z,
                deviceId, deviceRevision, deviceLoaded);
    }

    TargetSnapshot target() {
        return new TargetSnapshot(targetId, targetKind, DIMENSION,
                targetX, targetY, targetZ, targetRevision, targetLoaded);
    }

    void changeMirrorContent() {
        chunks.get(new ChunkKey(2, 2)).contentRevision++;
    }

    void changeTargetChunkContent() {
        chunks.get(new ChunkKey(TARGET_CHUNK_X, TARGET_CHUNK_Z)).contentRevision++;
    }

    void changeDeviceState() {
        deviceRevision++;
    }

    void changeDeviceIdentity() {
        deviceId = "coldatomcraft:replacement_laser_source";
    }

    void moveDeviceWithinChunk() {
        deviceY++;
    }

    void moveTarget(double dx) {
        targetX += dx;
        targetRevision++;
    }

    void bumpTargetRevision() {
        targetRevision++;
    }

    void changeTargetIdentity() {
        targetId = "entity:99";
    }

    void changeTargetKind() {
        targetKind = TargetKind.BLOCK;
    }

    void unloadDevice() {
        deviceLoaded = false;
        chunks.get(new ChunkKey(0, 0)).loaded = false;
    }

    void unloadTarget() {
        targetLoaded = false;
        chunks.get(new ChunkKey(TARGET_CHUNK_X, TARGET_CHUNK_Z)).loaded = false;
    }

    void reloadWorld() {
        for (Chunk chunk : chunks.values()) {
            chunk.loadEpoch++;
            chunk.loaded = true;
        }
        deviceLoaded = true;
        targetLoaded = true;
    }

    TargetSnapshot targetAt(double x) {
        return new TargetSnapshot("entity:42", TargetKind.ENTITY, DIMENSION,
                x, targetY, targetZ, targetRevision + 1, true);
    }

    private record ChunkKey(int x, int z) { }

    private static final class Chunk {
        private long loadEpoch;
        private long contentRevision;
        private boolean loaded = true;

        private Chunk(long loadEpoch, long contentRevision) {
            this.loadEpoch = loadEpoch;
            this.contentRevision = contentRevision;
        }
    }
}
