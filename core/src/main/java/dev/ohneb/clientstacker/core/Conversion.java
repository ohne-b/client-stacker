package dev.ohneb.clientstacker.core;

import java.util.Objects;

public record Conversion(String compact, String loose, int ratio) {
    public Conversion {
        Objects.requireNonNull(compact);
        Objects.requireNonNull(loose);
        if (compact.equals(loose) || ratio < 2 || ratio > 9) {
            throw new IllegalArgumentException("Invalid conversion");
        }
    }

    public long value(long blocks, long items) {
        if (blocks < 0 || items < 0) throw new IllegalArgumentException("Negative item count");
        return Math.addExact(Math.multiplyExact(blocks, ratio), items);
    }

    public long blocksNeeded(long needed, long looseItems) {
        if (needed < 0 || looseItems < 0) throw new IllegalArgumentException("Negative item count");
        long missing = Math.max(0, needed - looseItems);
        return missing / ratio + (missing % ratio == 0 ? 0 : 1);
    }
}
