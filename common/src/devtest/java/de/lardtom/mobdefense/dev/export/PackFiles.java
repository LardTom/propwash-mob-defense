package de.lardtom.mobdefense.dev.export;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class PackFiles {
    public static final List<String> ASSET_FOLDERS = List.of("blockstates", "items", "lang", "models", "particles", "textures");
    public static final List<String> ASSET_ROOT_FILES = List.of("icon.png", "sounds.json");
    public static final List<String> DATA_FOLDERS = List.of("advancement", "loot_table", "propwash_parts", "propwash_share", "recipe", "tags");

    private final NavigableMap<String, byte[]> files = new TreeMap<>();
    private final Problems problems;

    private PackFiles(Problems problems) {
        this.problems = problems;
    }

    public static PackFiles read(MinecraftServer server, String namespace, Problems problems) {
        PackFiles out = new PackFiles(problems);
        List<PackResources> packs = server.getPackRepository().openAllSelected();
        try {
            for (PackResources pack : packs) {
                out.collect(pack, PackType.CLIENT_RESOURCES, namespace, ASSET_FOLDERS, ASSET_ROOT_FILES);
                out.collect(pack, PackType.SERVER_DATA, namespace, DATA_FOLDERS, List.of());
            }
        } finally {
            for (PackResources pack : packs) {
                pack.close();
            }
        }
        return out;
    }

    private void collect(PackResources pack, PackType type, String namespace, List<String> folders, List<String> rootFiles) {
        if (!pack.getNamespaces(type).contains(namespace)) {
            return;
        }
        String where = type.getDirectory() + "/" + namespace + "/";
        for (String folder : folders) {
            pack.listResources(type, namespace, folder, (id, supplier) -> this.put(where + id.getPath(), supplier, pack));
        }
        for (String file : rootFiles) {
            IoSupplier<InputStream> supplier = pack.getResource(type, Identifier.fromNamespaceAndPath(namespace, file));
            if (supplier != null) {
                this.put(where + file, supplier, pack);
            }
        }
    }

    private void put(String path, IoSupplier<InputStream> supplier, PackResources pack) {
        if (this.files.containsKey(path)) {
            return;
        }
        try (InputStream in = supplier.get()) {
            this.files.put(path, in.readAllBytes());
        } catch (IOException e) {
            this.problems.error(path, "nicht lesbar aus " + pack.packId() + ": " + e.getMessage());
        }
    }

    public NavigableMap<String, byte[]> files() {
        return this.files;
    }

    public Map<String, byte[]> under(String prefix) {
        return this.files.subMap(prefix, true, prefix + Character.MAX_VALUE, false);
    }

    public boolean has(String path) {
        return this.files.containsKey(path);
    }

    public byte[] get(String path) {
        return this.files.get(path);
    }
}
