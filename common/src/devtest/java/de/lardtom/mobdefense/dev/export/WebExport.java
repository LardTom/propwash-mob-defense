package de.lardtom.mobdefense.dev.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class WebExport {
    public static final int FORMAT = 1;
    public static final String NAMESPACE = MobDefense.MOD_ID;
    public static final List<String> OUTPUTS = List.of("manifest.json", "parts.json", "rules.json", "recipes.json", "share.json", "lang", "assets",
            "problems.json");
    private static final String ASSETS = "assets/" + NAMESPACE + "/";
    private static final String DATA = "data/" + NAMESPACE + "/";

    public record Result(boolean pass, String summary) {
    }

    private final MinecraftServer server;
    private final Path target;
    private final Problems problems = new Problems();
    private final LangTable lang = new LangTable(this.problems);
    private final List<String> written = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Set<String> usedAssets = new TreeSet<>();
    private final Set<String> externalRefs = new TreeSet<>();
    private PackFiles files;

    private WebExport(MinecraftServer server, Path target) {
        this.server = server;
        this.target = target;
    }

    public static Result run(MinecraftServer server, Path target) {
        return new WebExport(server, target).export();
    }

    private Result export() {
        this.clean();
        this.files = PackFiles.read(this.server, NAMESPACE, this.problems);
        Map<String, Map<String, String>> texts = new TreeMap<>();
        for (String language : LangTable.LANGUAGES) {
            String path = ASSETS + "lang/" + language + ".json";
            byte[] data = this.files.get(path);
            if (data == null) {
                this.problems.error(path, "Sprachdatei fehlt");
                continue;
            }
            this.lang.load(path, data);
            texts.put(language, this.flatJson(path, data));
        }
        this.checkLanguages(texts);
        List<PartDefinition> parts = PropwashApi.parts().all().stream().filter(p -> NAMESPACE.equals(p.id().getNamespace()))
                .sorted(Comparator.comparingInt((PartDefinition p) -> p.basics().sortOrder()).thenComparing(p -> p.id().toString())).toList();
        this.write("parts.json", this.partsJson(parts));
        this.write("rules.json", this.rulesJson());
        this.write("recipes.json", this.recipesJson());
        this.write("share.json", this.shareJson());
        for (String language : LangTable.LANGUAGES) {
            JsonObject out = new JsonObject();
            texts.getOrDefault(language, Map.of()).forEach(out::addProperty);
            this.write("lang/" + language + ".json", out);
        }
        int assetFiles = this.copyAssets();
        this.counts.put("asset_files", assetFiles);
        this.write("problems.json", this.problems.toJson());
        this.write("manifest.json", this.manifestJson());
        if (this.problems.errors() > 0) {
            List<String> first = new ArrayList<>();
            for (Problems.Entry entry : this.problems.firstErrors(3)) {
                first.add(entry.where() + ": " + entry.message());
            }
            return new Result(false, "FAIL " + this.problems.errors() + " Fehler im Web-Export: " + String.join(" | ", first));
        }
        return new Result(true, "PASS " + parts.size() + " Teile, " + this.counts.get("rules") + " Welt-Regeln, " + this.counts.get("recipes")
                + " Rezepte, " + assetFiles + " Asset-Dateien, " + this.counts.get("lang_keys") + " Texte, " + this.problems.warnings() + " Warnungen");
    }

    private void clean() {
        for (String name : OUTPUTS) {
            Path path = this.target.resolve(name);
            if (!Files.exists(path)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Web-Export: " + path + " nicht gelöscht", e);
            }
        }
    }

    private void write(String name, JsonElement element) {
        ExportJson.write(this.target.resolve(name), element);
        this.written.add(name);
    }

    private Map<String, String> flatJson(String path, byte[] data) {
        Map<String, String> out = new TreeMap<>();
        try {
            JsonObject object = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    out.put(entry.getKey(), entry.getValue().getAsString());
                } else {
                    this.problems.error(path, "Wert ist kein Text: " + entry.getKey());
                }
            }
        } catch (RuntimeException e) {
            this.problems.error(path, "JSON nicht lesbar: " + e.getMessage());
        }
        return out;
    }

    private void checkLanguages(Map<String, Map<String, String>> texts) {
        Set<String> all = new TreeSet<>();
        texts.values().forEach(map -> all.addAll(map.keySet()));
        for (String language : LangTable.LANGUAGES) {
            Map<String, String> map = texts.getOrDefault(language, Map.of());
            for (String key : all) {
                if (!map.containsKey(key)) {
                    this.problems.warning("lang/" + language + ".json", "Schlüssel fehlt: " + key);
                }
            }
        }
        this.counts.put("lang_keys", all.size());
    }

    private JsonArray partsJson(List<PartDefinition> parts) {
        JsonArray out = new JsonArray();
        Map<String, Integer> kinds = new TreeMap<>();
        for (PartDefinition part : parts) {
            Identifier id = part.id();
            String where = id.toString();
            kinds.merge(part.kind().key(), 1, Integer::sum);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", where);
            entry.addProperty("category", part.kind().key());
            entry.addProperty("builtin", false);
            entry.add("name", this.lang.both("part." + id.getNamespace() + "." + id.getPath(), where, true));
            entry.add("short_name", JsonNull.INSTANCE);
            entry.add("help", this.help(id.getNamespace() + ".help." + id.getPath(), where));
            JsonObject basics = new JsonObject();
            basics.add("mass_g", ExportJson.num(part.basics().massGrams()));
            basics.addProperty("stack_size", part.basics().stackSize());
            basics.addProperty("sort", part.basics().sortOrder());
            basics.addProperty("creative", part.basics().creativeListed());
            basics.add("durability", ExportJson.num(part.basics().durability()));
            entry.add("basics", basics);
            entry.add("data", PropwashApi.parts().toJson(part));
            if (PropwashApi.partItems().item(id).isEmpty()) {
                this.problems.error(where, "Teil ohne Item");
            }
            entry.add("model", this.model(part));
            out.add(entry);
        }
        this.counts.put("parts", parts.size());
        kinds.forEach((kind, count) -> this.counts.put(kind, count));
        return out;
    }

    private JsonObject help(String prefix, String where) {
        JsonObject out = new JsonObject();
        for (String language : LangTable.LANGUAGES) {
            JsonObject help = new JsonObject();
            Optional<String> summary = this.lang.get(language, prefix + ".summary");
            help.add("summary", ExportJson.str(summary));
            if (summary.isEmpty()) {
                this.problems.warning(where, "Hilfe fehlt (" + language + "): " + prefix + ".summary");
            }
            for (String block : List.of("what", "use")) {
                JsonArray lines = new JsonArray();
                for (int i = 1; i <= 8; i++) {
                    this.lang.get(language, prefix + "." + block + "." + i).ifPresent(lines::add);
                }
                help.add(block, lines);
            }
            out.add(language, help);
        }
        return out;
    }

    private JsonObject model(PartDefinition part) {
        Identifier id = part.id();
        String item = ASSETS + "items/" + id.getPath() + ".json";
        String drone = ASSETS + "items/drone_render/" + part.kind().key() + "/" + id.getPath() + ".json";
        List<String> textures = new ArrayList<>();
        JsonObject out = new JsonObject();
        if (this.files.has(item)) {
            this.follow(item, textures, new TreeSet<>(), 0);
            out.addProperty("item", item);
        } else {
            this.problems.warning(id.toString(), "Item-Modell fehlt: " + item);
            out.add("item", JsonNull.INSTANCE);
        }
        if (this.files.has(drone)) {
            this.follow(drone, textures, new TreeSet<>(), 0);
            out.addProperty("kind", "model");
            out.addProperty("drone", drone);
        } else {
            out.addProperty("kind", "procedural");
            out.add("drone", JsonNull.INSTANCE);
        }
        out.add("textures", ExportJson.strings(new TreeSet<>(textures)));
        return out;
    }

    private void follow(String path, List<String> textures, Set<String> visited, int depth) {
        if (depth > 16 || !visited.add(path)) {
            return;
        }
        this.usedAssets.add(path);
        if (path.endsWith(".png")) {
            textures.add(path);
            return;
        }
        byte[] data = this.files.get(path);
        if (data == null) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            this.problems.error(path, "JSON nicht lesbar: " + e.getMessage());
            return;
        }
        this.walk(root, path.contains("/models/"), textures, visited, depth, path);
    }

    private void walk(JsonElement element, boolean model, List<String> textures, Set<String> visited, int depth, String from) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                this.walk(child, model, textures, visited, depth, from);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            String key = entry.getKey();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                if (!model && (key.equals("model") || key.equals("base")) || model && key.equals("parent")) {
                    this.reference(value.getAsString(), "models", ".json", textures, visited, depth, from);
                }
            } else if (model && key.equals("textures") && value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> texture : value.getAsJsonObject().entrySet()) {
                    if (texture.getValue().isJsonPrimitive() && !texture.getValue().getAsString().startsWith("#")) {
                        this.reference(texture.getValue().getAsString(), "textures", ".png", textures, visited, depth, from);
                    }
                }
            } else {
                this.walk(value, model, textures, visited, depth, from);
            }
        }
    }

    private void reference(String text, String folder, String suffix, List<String> textures, Set<String> visited, int depth, String from) {
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            this.problems.error(from, "ungültiger Verweis: " + text);
            return;
        }
        if (!NAMESPACE.equals(id.getNamespace())) {
            this.externalRefs.add(id.getNamespace() + ":" + folder + "/" + id.getPath());
            return;
        }
        String path = ASSETS + folder + "/" + id.getPath() + suffix;
        if (!this.files.has(path)) {
            if (!id.getPath().startsWith("builtin/")) {
                this.problems.error(from, "Verweis ohne Datei: " + path);
            }
            return;
        }
        this.follow(path, textures, visited, depth + 1);
    }

    private JsonObject rulesJson() {
        JsonArray rules = new JsonArray();
        int registered = 0;
        for (RuleSpec spec : RuleDefaults.ALL) {
            Identifier id = MobDefense.id(spec.path());
            String where = "rules.json#" + id;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("type", spec.flag() ? "boolean" : "integer");
            if (spec.flag()) {
                entry.addProperty("default", spec.defaultFlag());
                entry.add("min", JsonNull.INSTANCE);
                entry.add("max", JsonNull.INSTANCE);
            } else {
                entry.addProperty("default", spec.defaultValue());
                entry.addProperty("min", spec.min());
                entry.addProperty("max", spec.max());
            }
            GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
            boolean present = rule != null && BuiltInRegistries.GAME_RULE.containsKey(id);
            entry.addProperty("registered", present);
            if (present) {
                registered++;
                this.compareRule(spec, rule, where);
            }
            String key = "gamerule." + id.getNamespace() + "." + id.getPath();
            entry.add("name", this.lang.both(key, where, present));
            entry.add("description", this.lang.both(key + ".description", where, present));
            rules.add(entry);
        }
        for (Identifier id : BuiltInRegistries.GAME_RULE.keySet()) {
            if (NAMESPACE.equals(id.getNamespace()) && RuleDefaults.ALL.stream().noneMatch(spec -> spec.path().equals(id.getPath()))) {
                this.problems.error("rules.json", "Gamerule " + id + " fehlt in RuleDefaults");
            }
        }
        this.counts.put("rules", RuleDefaults.ALL.size());
        this.counts.put("rules_registered", registered);
        JsonObject out = new JsonObject();
        out.addProperty("section", NAMESPACE);
        out.add("section_name", this.lang.both(NAMESPACE + ".name", "rules.json", true));
        out.add("rules", rules);
        return out;
    }

    private void compareRule(RuleSpec spec, GameRule<?> rule, String where) {
        Object expected = spec.flag() ? (Object) spec.defaultFlag() : (Object) spec.defaultValue();
        if (!expected.equals(rule.defaultValue())) {
            this.problems.error(where, "Standard im Spiel " + rule.defaultValue() + " statt " + expected);
        }
        if (!spec.flag() && rule.argument() instanceof IntegerArgumentType integer
                && (integer.getMinimum() != spec.min() || integer.getMaximum() != spec.max())) {
            this.problems.error(where, "Bereich im Spiel " + integer.getMinimum() + " bis " + integer.getMaximum());
        }
    }

    private JsonArray recipesJson() {
        JsonArray out = new JsonArray();
        String prefix = DATA + "recipe/";
        for (Map.Entry<String, byte[]> file : this.files.under(prefix).entrySet()) {
            if (!file.getKey().endsWith(".json")) {
                continue;
            }
            String path = file.getKey().substring(prefix.length(), file.getKey().length() - 5);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", NAMESPACE + ":" + path);
            try {
                entry.add("recipe", JsonParser.parseString(new String(file.getValue(), StandardCharsets.UTF_8)));
            } catch (RuntimeException e) {
                this.problems.error(file.getKey(), "JSON nicht lesbar: " + e.getMessage());
                continue;
            }
            out.add(entry);
        }
        this.counts.put("recipes", out.size());
        return out;
    }

    private JsonElement shareJson() {
        String path = DATA + "propwash_share/table.json";
        byte[] data = this.files.get(path);
        if (data == null) {
            this.counts.put("share_entries", 0);
            return JsonNull.INSTANCE;
        }
        try {
            JsonObject table = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            int entries = 0;
            if (table.has("parts")) {
                for (Map.Entry<String, JsonElement> kind : table.getAsJsonObject("parts").entrySet()) {
                    entries += kind.getValue().getAsJsonArray().size();
                }
            }
            this.counts.put("share_entries", entries);
            return table;
        } catch (RuntimeException e) {
            this.problems.error(path, "PW3-Tabelle nicht lesbar: " + e.getMessage());
            return JsonNull.INSTANCE;
        }
    }

    private int copyAssets() {
        int copied = 0;
        for (Map.Entry<String, byte[]> file : this.files.under(ASSETS).entrySet()) {
            String path = file.getKey();
            if (path.startsWith(ASSETS + "lang/")) {
                continue;
            }
            if (path.endsWith(".json") && !this.usedAssets.contains(path)) {
                this.follow(path, new ArrayList<>(), new TreeSet<>(), 0);
            }
            ExportJson.write(this.target.resolve(path), file.getValue());
            copied++;
        }
        return copied;
    }

    private JsonObject manifestJson() {
        JsonObject out = new JsonObject();
        out.addProperty("format", FORMAT);
        out.addProperty("generated_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        String version = Services.PLATFORM.modVersion(NAMESPACE).orElse("?");
        out.addProperty("generator", NAMESPACE + " " + version);
        out.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
        JsonObject mods = new JsonObject();
        mods.addProperty(PropwashApi.MOD_ID, Services.PLATFORM.modVersion(PropwashApi.MOD_ID).orElse("?"));
        mods.addProperty(NAMESPACE, version);
        out.add("mods", mods);
        out.addProperty("propwash_api", PropwashApi.API_VERSION.toString());
        out.addProperty("namespace", NAMESPACE);
        out.add("languages", ExportJson.strings(LangTable.LANGUAGES));
        JsonObject counts = new JsonObject();
        this.counts.forEach(counts::addProperty);
        out.add("counts", counts);
        out.addProperty("fingerprint", PropwashApi.parts().fingerprint());
        out.add("external_refs", ExportJson.strings(this.externalRefs));
        List<String> sorted = new ArrayList<>(this.written);
        sorted.add("manifest.json");
        sorted.sort(String::compareTo);
        out.add("files", ExportJson.strings(sorted));
        return out;
    }
}
