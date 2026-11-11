package de.lardtom.mobdefense.dispenser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenserImportTest {
    static final List<String> FORBIDDEN_MODULES = List.of("spear", "booster", "mob", "sculk");
    static final Pattern REFERENCE = Pattern.compile("de\\.lardtom\\.(mobdefense|propwash)\\.([a-z0-9_]+)");
    static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);

    static Path sources() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().resolve("java").resolve("de/lardtom/mobdefense");
    }

    static List<Path> backportFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String dir : List.of("dispenser", "payload")) {
            try (Stream<Path> files = Files.walk(sources().resolve(dir))) {
                files.filter(file -> file.toString().endsWith(".java")).sorted().forEach(out::add);
            }
        }
        out.add(sources().resolve("mixin/AbstractThrownPotionMixin.java"));
        out.add(sources().resolve("mixin/PrimedTntMixin.java"));
        return out;
    }

    @Test
    void backportFilesImportNothingFromSpearBoosterMobOrSculk() throws IOException {
        List<Path> files = backportFiles();
        assertTrue(files.size() >= 10, "zu wenige Dateien: " + files);
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            assertTrue(Files.isRegularFile(file), file + " fehlt");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = REFERENCE.matcher(text);
            while (matcher.find()) {
                String root = matcher.group(1);
                String module = matcher.group(2);
                if ("mobdefense".equals(root) && FORBIDDEN_MODULES.contains(module)) {
                    problems.add(sources().relativize(file) + ": " + matcher.group());
                }
                if ("propwash".equals(root) && !"api".equals(module)) {
                    problems.add(sources().relativize(file) + ": Propwash außerhalb der API: " + matcher.group());
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void portableCoreImportsOnlyJava() throws IOException {
        try (Stream<Path> files = Files.walk(sources().resolve("dispenser/core"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher matcher = IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String imported = matcher.group(2);
                    assertTrue(imported.startsWith("java.") || imported.startsWith("de.lardtom.mobdefense.dispenser.core."),
                            file.getFileName() + " importiert " + imported);
                }
            }
        }
    }

    @Test
    void checkSeesForbiddenImports() {
        Matcher matcher = REFERENCE.matcher("import de.lardtom.mobdefense.spear.SpearBehavior;");
        assertTrue(matcher.find());
        assertTrue(FORBIDDEN_MODULES.contains(matcher.group(2)));
        Matcher allowed = REFERENCE.matcher("import de.lardtom.mobdefense.rules.MobDefenseRules;");
        assertTrue(allowed.find());
        assertFalse(FORBIDDEN_MODULES.contains(allowed.group(2)));
    }
}
