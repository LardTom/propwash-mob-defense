package de.lardtom.mobdefense.platform;

import java.nio.file.Path;
import java.util.Optional;

public interface Platform {
    String name();

    boolean isModLoaded(String modId);

    Optional<String> modVersion(String modId);

    boolean isDevelopment();

    Path gameDir();

    Path configDir();
}
