package dev.ohneb.clientstacker.core;

import java.util.Objects;
import java.util.List;

public record Conversion(String compact, String loose, int ratio) {
    /** Values the catalog's block, ingot, and nugget tiers in whole smallest units. */
    public int unitsPerItem(String item, List<Conversion> catalog) {
        var smaller = catalog.stream().filter(pair -> pair.compact.equals(loose)).findFirst().orElse(null);
        int scale = smaller == null ? 1 : smaller.ratio;
        if (compact.equals(item)) return ratio * scale;
        if (loose.equals(item)) return scale;
        if (smaller != null && smaller.loose.equals(item)) return 1;
        for (var larger : catalog) {
            if (larger.loose.equals(compact) && larger.compact.equals(item)) return larger.ratio * ratio * scale;
        }
        return 0;
    }

    public long itemValue(String item, long count) {
        if (count < 0) throw new IllegalArgumentException("Negative item count");
        if (compact.equals(item)) return Math.multiplyExact(count, ratio);
        return loose.equals(item) ? count : 0;
    }
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
