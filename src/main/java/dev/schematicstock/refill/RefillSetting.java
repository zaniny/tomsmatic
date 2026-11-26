package dev.schematicstock.refill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class RefillSetting {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tomsmatic-easy-place.txt");
    private static boolean enabled = load();

    private RefillSetting() {}

    public static boolean enabled() {
        return enabled;
    }

    public static boolean toggle() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Boolean.toString(!enabled) + "\n");
            enabled = !enabled;
            return true;
        } catch (IOException failure) {
            LoggerFactory.getLogger("Tomsmatic").warn("Could not save the Easy Place refill setting", failure);
            return false;
        }
    }

    private static boolean load() {
        try {
            return Files.isRegularFile(FILE) && Boolean.parseBoolean(Files.readString(FILE).strip());
        } catch (IOException failure) {
            LoggerFactory.getLogger("Tomsmatic").warn("Could not read the Easy Place refill setting", failure);
            return false;
        }
    }
}
