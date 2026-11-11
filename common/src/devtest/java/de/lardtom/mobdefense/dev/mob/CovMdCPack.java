package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class CovMdCPack {
    public static final String NAME = "covmdc_ignores_zombie";
    public static final String PACK_ID = "file/" + NAME;
    public static final String SOURCE = "/cov/cov-md-c/ignores_zombie/";
    public static final List<String> FILES = List.of("pack.mcmeta", "data/mobdefense/tags/entity_type/ignores_drones.json");

    private CovMdCPack() {
    }

    public static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(NAME);
    }

    public static Path install(MinecraftServer server) {
        Path folder = folder(server);
        try {
            for (String file : FILES) {
                Path target = folder.resolve(file);
                Files.createDirectories(target.getParent());
                try (InputStream in = CovMdCPack.class.getResourceAsStream(SOURCE + file)) {
                    if (in == null) {
                        throw new IllegalStateException("Test-Datenpaket fehlt im Klassenpfad: " + SOURCE + file);
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Test-Datenpaket nicht kopiert", e);
        }
        return folder;
    }

    public static boolean installed(MinecraftServer server) {
        return Files.isDirectory(folder(server));
    }

    public static boolean enabled(MinecraftServer server) {
        return server.getPackRepository().getSelectedIds().contains(PACK_ID);
    }

    public static void remove(MinecraftServer server) {
        Path folder = folder(server);
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Test-Datenpaket nicht gelöscht", e);
        }
    }

    public static String kind(EntityType<?> type) {
        Reaction reaction = ReactionTags.reaction(type);
        return reaction == null ? "keine" : reaction.kind();
    }

    public static boolean zombieIgnored() {
        return ReactionTags.reaction(EntityTypes.ZOMBIE) == null;
    }

    public static boolean skeletonArcher() {
        return "archer".equals(kind(EntityTypes.SKELETON));
    }
}
