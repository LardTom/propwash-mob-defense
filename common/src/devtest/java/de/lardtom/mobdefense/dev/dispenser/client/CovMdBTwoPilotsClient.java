package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdReader;
import de.lardtom.mobdefense.dev.dispenser.CovMdBTwoPilotsScene;
import de.lardtom.mobdefense.dev.join.JoinProbe;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class CovMdBTwoPilotsClient {
    public static final int CHECK_TICKS = 10;
    public static final int ARM_RETRY_TICKS = 40;
    private static final String TAG = "[Mob-Defense-Zwei-Piloten]";
    private static int flying;
    private static int armWait;
    private static int armedAt = -1;
    private static boolean own;
    private static @Nullable UUID pilot;
    private static String firstOsd = "-";
    private static String lastOsd = "-";
    private static volatile boolean finished;

    private CovMdBTwoPilotsClient() {
    }

    public static void init() {
        if (!CovMdBTwoPilotsScene.enabled() || !JoinProbe.requested()) {
            return;
        }
        MobDefenseClient.onClientTick(CovMdBTwoPilotsClient::tick);
        JoinProbe.stayUntil("Zwei Piloten", CovMdBTwoPilotsClient::finished);
        MobDefense.LOG.info("{} Client-Prüfung aktiv, der Client bleibt bis zum Ende des Flugs in der Welt", TAG);
    }

    public static boolean finished() {
        return finished;
    }

    private static void arm() {
        if (Pilot.armed()) {
            if (armedAt < 0) {
                armedAt = flying;
            }
            return;
        }
        if (armWait > 0) {
            armWait--;
            return;
        }
        armWait = ARM_RETRY_TICKS;
        Pilot.click(Pilot.ARM);
    }

    private static void tick(Minecraft mc) {
        if (finished || mc.level == null || mc.player == null) {
            return;
        }
        Optional<Entity> drone = PropwashClientApi.input().drone();
        if (!PropwashClientApi.input().flying() || drone.isEmpty()) {
            if (flying > 0) {
                finished = true;
                report(mc);
            }
            return;
        }
        flying++;
        arm();
        OsdText text = MobDefenseOsd.dispenser(OsdReader.read(drone.get(), mc.level.getGameTime()));
        lastOsd = text == null ? "-" : text.text();
        if (flying == CHECK_TICKS) {
            pilot = PropwashApi.drones().view(drone.get()).flatMap(DroneView::pilot).orElse(null);
            own = mc.player.getUUID().equals(pilot);
            firstOsd = lastOsd;
        }
    }

    private static void report(Minecraft mc) {
        boolean checked = flying >= CHECK_TICKS;
        boolean pass = checked && own && !firstOsd.equals("-") && armedAt >= 0;
        String result = (pass ? "PASS" : "FAIL") + " Zwei Piloten am Client " + mc.player.getGameProfile().name() + ": eigene Drohne "
                + (!checked ? "nicht geprüft" : own ? "ja" : "nein (Pilot " + pilot + ")") + ", OSD „" + firstOsd + "“, zuletzt „" + lastOsd + "“, "
                + (armedAt < 0 ? "nie scharf" : "scharf nach " + armedAt + " Ticks") + ", Flug nach " + flying + " Ticks vom Server beendet";
        MobDefense.LOG.info("{} {}", TAG, result);
        try {
            Path dir = mc.gameDirectory.toPath().resolve(JoinProbe.FOLDER);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("cov-two-pilots-" + JoinProbe.label() + ".txt"), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.error("{} Ergebnis nicht geschrieben", TAG, e);
        }
    }
}
