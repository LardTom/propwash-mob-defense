package de.lardtom.mobdefense.dev;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DevResult {
    public static final String RESULT_FILE = "mobdefense-selftest-result.txt";

    private DevResult() {
    }

    public static void write(String result) {
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.error("[Mob-Defense-Selbsttest] Ergebnisdatei nicht geschrieben", e);
        }
    }
}
