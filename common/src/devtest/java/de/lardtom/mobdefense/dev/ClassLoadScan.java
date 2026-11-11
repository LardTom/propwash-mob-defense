package de.lardtom.mobdefense.dev;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClassLoadScan {
    public static final String PROPERTY = "mobdefense.classlog";
    private static final List<String> CLIENT_PREFIXES = List.of("net.minecraft.client.", "com.mojang.blaze3d.");
    private static final Pattern OWN_CLIENT = Pattern.compile("de\\.lardtom\\.mobdefense\\.(?:[a-z0-9_]+\\.)*client\\..*");
    private static final String WITNESS = "de.lardtom.mobdefense.MobDefense";
    private static final int SHOWN = 5;

    public record Result(boolean ok, String message) {
    }

    private ClassLoadScan() {
    }

    public static Result scan() {
        String file = System.getProperty(PROPERTY);
        if (file == null || file.isBlank()) {
            return new Result(false, "Klassen-Protokoll nicht angefordert (-Xlog:class+load über " + PROPERTY + ")");
        }
        return scan(Path.of(file));
    }

    public static Result scan(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Result(false, "Klassen-Protokoll fehlt: " + file);
        }
        List<String> found = new ArrayList<>();
        int lines = 0;
        boolean witness = false;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = className(line);
                if (name == null) {
                    continue;
                }
                lines++;
                witness |= WITNESS.equals(name);
                if (client(name)) {
                    found.add(name);
                }
            }
        } catch (IOException e) {
            return new Result(false, "Klassen-Protokoll nicht lesbar: " + e);
        }
        if (lines == 0) {
            return new Result(false, "Klassen-Protokoll leer: " + file);
        }
        if (!witness) {
            return new Result(false, "Klassen-Protokoll ohne " + WITNESS + ", also unvollständig: " + file);
        }
        if (!found.isEmpty()) {
            return new Result(false, found.size() + " Client-Klassen auf dem dedizierten Server geladen, zuerst "
                    + String.join(", ", found.subList(0, Math.min(SHOWN, found.size()))));
        }
        return new Result(true, "keine von " + lines + " geladenen Klassen gehört zum Client");
    }

    public static boolean client(String name) {
        for (String prefix : CLIENT_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return OWN_CLIENT.matcher(name).matches();
    }

    static String className(String line) {
        String text = line;
        int bracket = text.lastIndexOf("] ");
        if (bracket >= 0) {
            text = text.substring(bracket + 2);
        }
        int space = text.indexOf(' ');
        String name = space < 0 ? text : text.substring(0, space);
        if (name.isEmpty() || !text.contains(" source: ")) {
            return null;
        }
        return name;
    }
}
