package de.lardtom.mobdefense.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.core.FireCadence;
import de.lardtom.mobdefense.platform.Services;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;

public final class ClientSettings {
    public static final String FILE_NAME = "mobdefense-client.json";
    public static final String DELAY = "dispenser_delay_ticks";
    public static final String INTERVAL = "dispenser_interval_ticks";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static @Nullable ClientSettings instance;

    private final Path file;
    private FireCadence.Setting cadence;

    private ClientSettings(Path file, FireCadence.Setting cadence) {
        this.file = file;
        this.cadence = cadence;
    }

    public static synchronized ClientSettings get() {
        if (instance == null) {
            instance = load(Services.PLATFORM.configDir().resolve(FILE_NAME));
        }
        return instance;
    }

    public static ClientSettings load(Path file) {
        Objects.requireNonNull(file, "file");
        FireCadence.Setting cadence = FireCadence.Setting.DEFAULT;
        if (Files.isRegularFile(file)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (parsed instanceof JsonObject values) {
                    cadence = new FireCadence.Setting(ticks(values, DELAY).orElse(FireCadence.DEFAULT_DELAY_TICKS),
                            ticks(values, INTERVAL).orElse(FireCadence.DEFAULT_INTERVAL_TICKS));
                } else {
                    MobDefense.LOG.warn("{}: {} ist kein JSON-Objekt, Standardwerte", MobDefense.MOD_NAME, file.getFileName());
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                MobDefense.LOG.warn("{}: {} nicht lesbar, Standardwerte: {}", MobDefense.MOD_NAME, file.getFileName(), e.toString());
            }
        }
        return new ClientSettings(file, cadence);
    }

    private static OptionalInt ticks(JsonObject values, String key) {
        if (values.get(key) instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (Double.isFinite(value)) {
                return OptionalInt.of((int) Math.round(value));
            }
        }
        return OptionalInt.empty();
    }

    public Path file() {
        return this.file;
    }

    public synchronized FireCadence.Setting cadence() {
        return this.cadence;
    }

    public synchronized boolean cadence(FireCadence.Setting setting) {
        Objects.requireNonNull(setting, "setting");
        boolean changed = !setting.equals(this.cadence) || !Files.isRegularFile(this.file);
        this.cadence = setting;
        return !changed || this.save();
    }

    private boolean save() {
        JsonObject values = new JsonObject();
        values.addProperty(DELAY, this.cadence.delayTicks());
        values.addProperty(INTERVAL, this.cadence.intervalTicks());
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, GSON.toJson(values) + "\n", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            MobDefense.LOG.warn("{}: {} nicht gespeichert: {}", MobDefense.MOD_NAME, this.file.getFileName(), e.toString());
            return false;
        }
    }
}
