package dev.ohneb.clientstacker.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ohneb.clientstacker.core.TradeLimits;
import dev.ohneb.clientstacker.core.Conversion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public final class StackerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("client_stacker");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public int formatVersion = 1;
    public boolean emeraldTrading = true;
    public boolean showBalance = true;
    public boolean compactOnTableOpen;
    public int looseReserve = 16;
    public int retainedEmeralds;
    public int spendingCap;
    public int outputTarget;
    public int timeoutSeconds = 10;
    public Set<String> sellingMaterials = new HashSet<>();
    public Set<Integer> protectedSlots = new HashSet<>();
    public Set<String> disabledServers = new HashSet<>();
    public boolean allowNetherite;
    public Map<String, Integer> looseReserves = new HashMap<>();
    public Map<String, Integer> blockReserves = new HashMap<>();
    public Map<String, Integer> retainedValues = new HashMap<>();
    private transient boolean readable = true;
    private transient Path path;

    public static StackerConfig load(Path path) {
        StackerConfig config = new StackerConfig();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, StackerConfig.class);
                if (config == null) throw new IllegalArgumentException("Empty configuration");
                config.validate();
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Cannot read Client Stacker settings; automation is disabled", exception);
                config = new StackerConfig();
                config.enabled = false;
                config.readable = false;
            }
        }
        config.path = path;
        return config;
    }

    public void validate() {
        if (formatVersion != 1 || looseReserve < 0 || retainedEmeralds < 0 || spendingCap < 0 || outputTarget < 0
            || timeoutSeconds < 5 || timeoutSeconds > 15 || sellingMaterials == null
            || protectedSlots == null || disabledServers == null
            || protectedSlots.stream().anyMatch(slot -> slot == null || slot < 0 || slot >= 36)
            || sellingMaterials.stream().anyMatch(id -> !validMaterial(id)) || disabledServers.contains(null)
            || !validReserves(looseReserves) || !validReserves(blockReserves) || !validReserves(retainedValues)) {
            throw new IllegalArgumentException("Invalid Client Stacker settings");
        }
    }

    public boolean save() {
        if (!readable) return false;
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            validate();
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(this) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Cannot save Client Stacker settings", exception);
            return false;
        }
    }

    public boolean readable() { return readable; }

    private static boolean validMaterial(String id) {
        return id != null && Materials.ALL.stream().anyMatch(pair -> pair.loose().equals(id) || pair.compact().equals(id));
    }

    private static boolean validReserves(Map<String, Integer> values) {
        return values != null && values.entrySet().stream().allMatch(entry -> validMaterial(entry.getKey())
            && entry.getValue() != null && entry.getValue() >= 0);
    }

    public int looseReserve(Conversion material) { return looseReserves.getOrDefault(material.loose(), looseReserve); }
    public int blockReserve(Conversion material) { return blockReserves.getOrDefault(material.compact(), 0); }
    public int retainedValue(Conversion material) {
        return retainedValues.getOrDefault(material.loose(), material.equals(Materials.EMERALDS) ? retainedEmeralds : 0);
    }

    public TradeLimits tradeLimits() {
        return new TradeLimits(spendingCap, retainedValue(Materials.EMERALDS), outputTarget);
    }
}
