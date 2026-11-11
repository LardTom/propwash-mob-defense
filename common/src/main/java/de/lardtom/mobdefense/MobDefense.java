package de.lardtom.mobdefense;

import de.lardtom.mobdefense.dispenser.Dispensers;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public final class MobDefense {
    public static final String MOD_ID = "mobdefense";
    public static final String MOD_NAME = "Propwash: Mob Defense";
    public static final String REQUIRED_API = "1.8";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    public static final Identifier DISPENSER_MOUNT = id("dispenser_mount");
    public static final Identifier SPEAR_MOUNT = id("spear_mount");
    public static final Identifier ROCKET_BOOSTER = id("rocket_booster");
    public static final List<Identifier> PARTS = List.of(DISPENSER_MOUNT, SPEAR_MOUNT, ROCKET_BOOSTER);
    private static final String DEV_COMMON = "de.lardtom.mobdefense.dev.DevBootstrap";

    private static boolean initialized;
    private static int bundledParts = -1;
    private static int items = -1;
    private static volatile Consumer<MinecraftServer> serverStarted = server -> {
    };
    private static volatile Consumer<MinecraftServer> serverTick = server -> {
    };

    private MobDefense() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.requireVersion(MOD_ID, REQUIRED_API);
        bundledParts = PropwashApi.parts().loadBundled(MOD_ID);
        items = PropwashApi.partItems().registerItems(MOD_ID);
        MobDefenseRules.register();
        MobDefenseTab.init();
        de.lardtom.mobdefense.mob.DroneReactions.init();
        de.lardtom.mobdefense.spear.Spears.init();
        Dispensers.init();
        de.lardtom.mobdefense.booster.Boosters.init();
        LOG.info("{} geladen ({}): {} Teile aus JSON, {} Teil-Items, {} Welt-Regeln, Propwash-API {}", MOD_NAME, Services.PLATFORM.name(),
                bundledParts, items, MobDefenseRules.all().size(), PropwashApi.API_VERSION);
        devtest();
    }

    public static boolean initialized() {
        return initialized;
    }

    public static int bundledParts() {
        return bundledParts;
    }

    public static int items() {
        return items;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void onServerStarted(Consumer<MinecraftServer> listener) {
        serverStarted = serverStarted.andThen(listener);
    }

    public static void onServerTick(Consumer<MinecraftServer> listener) {
        serverTick = serverTick.andThen(listener);
    }

    public static void serverStarted(MinecraftServer server) {
        serverStarted.accept(server);
    }

    public static void serverTick(MinecraftServer server) {
        serverTick.accept(server);
    }

    private static void devtest() {
        if (!Boolean.getBoolean("mobdefense.serversmoke") && System.getProperty("mobdefense.webexport") == null
                && System.getProperty("mobdefense.d8") == null) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(DEV_COMMON, true, MobDefense.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.error("{}: Entwicklertest angefordert, aber {} fehlt im Klassenpfad", MOD_NAME, DEV_COMMON);
            return;
        }
        try {
            type.getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(DEV_COMMON + " startet nicht", e);
        }
    }
}
