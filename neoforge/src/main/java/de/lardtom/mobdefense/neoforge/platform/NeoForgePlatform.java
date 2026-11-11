package de.lardtom.mobdefense.neoforge.platform;

import de.lardtom.mobdefense.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

public final class NeoForgePlatform implements Platform {
    @Override
    public String name() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
