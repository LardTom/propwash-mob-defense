package de.lardtom.mobdefense.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

final class DataFiles {
    static final String NAMESPACE = "mobdefense";

    private DataFiles() {
    }

    static Path resources() {
        return Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
    }

    static Path data() {
        return resources().resolve("data").resolve(NAMESPACE);
    }

    static Path assets() {
        return resources().resolve("assets").resolve(NAMESPACE);
    }

    static Path root() {
        return resources().getParent().getParent().getParent().getParent();
    }

    static Path lang() {
        return root().resolve("common/src/lang");
    }

    static String version() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version");
    }

    static JsonObject object(Path file) {
        return element(file).getAsJsonObject();
    }

    static JsonElement element(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Path> json(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return new ArrayList<>(files.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String stem(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}
