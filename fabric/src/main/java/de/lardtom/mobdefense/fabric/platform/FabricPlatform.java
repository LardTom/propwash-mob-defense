package de.lardtom.mobdefense.fabric.platform;

import de.lardtom.mobdefense.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Optional;

public final class FabricPlatform implements Platform {
    @Override
    public String name() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
