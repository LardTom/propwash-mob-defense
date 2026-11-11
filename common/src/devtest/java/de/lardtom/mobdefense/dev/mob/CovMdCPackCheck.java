package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CovMdCPackCheck {
    public static final String ENV = "MOBDEFENSE_COV_MDC_PACK";
    public static final String RESULT_FILE = "mobdefense-covmdc-pack.txt";
    public static final String STOP_FILE = "jointest.stop";
    public static final int TAG_TICKS = 600;
    public static final int OBSERVE_TICKS = 300;
    private static final String TAG = "[Mob-Defense-cov-md-c]";
    private static CovMdCPackCheck running;

    private enum Phase { TAG_ON, OBSERVE, TAG_OFF, DONE }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final List<Mob> zombies = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private Phase phase = Phase.TAG_ON;
    private int ticks;
    private Mob skeleton;
    private boolean skeletonAssigned;
    private int arrows;

    private CovMdCPackCheck(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
    }

    public static boolean enabled() {
        String value = System.getenv(ENV);
        return value != null && !value.isBlank() && !"0".equals(value.trim());
    }

    public static String start(MinecraftServer server) {
        if (running != null) {
            return "läuft schon";
        }
        if (!server.isDedicatedServer()) {
            throw new IllegalStateException("nur am dedizierten Server");
        }
        CovMdCPackCheck run = new CovMdCPackCheck(server);
        running = run;
        if (!CovMdCPack.skeletonArcher() || CovMdCPack.zombieIgnored()) {
            throw new IllegalStateException("Ausgangslage falsch: Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE) + ", Skelett " + CovMdCPack.kind(EntityTypes.SKELETON));
        }
        Path folder = CovMdCPack.install(server);
        run.command("reload");
        MobDefense.onServerTick(s -> {
            if (running == run) {
                try {
                    run.tick();
                } catch (RuntimeException e) {
                    run.finish("FAIL " + (e.getMessage() == null ? e.toString() : e.getMessage()), e);
                }
            }
        });
        return "Test-Datenpaket nach " + folder.getFileName() + " kopiert, /reload, Prüfung läuft (Ergebnis " + RESULT_FILE + ")";
    }

    private void command(String command) {
        this.server.getCommands().performPrefixedCommand(this.server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private void tick() {
        this.ticks++;
        switch (this.phase) {
            case TAG_ON -> {
                if (CovMdCPack.zombieIgnored() && CovMdCPack.skeletonArcher()) {
                    this.notes.add("nach /reload: Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE) + ", Skelett " + CovMdCPack.kind(EntityTypes.SKELETON) + " ("
                            + this.ticks + " Ticks)");
                    this.scene();
                    this.phase = Phase.OBSERVE;
                    this.ticks = 0;
                } else if (this.ticks > TAG_TICKS) {
                    throw new IllegalStateException("Tag nach " + TAG_TICKS + " Ticks nicht geladen, Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE));
                }
            }
            case OBSERVE -> {
                for (Mob zombie : this.zombies) {
                    if (DroneReactions.assigned(zombie).isPresent() || DroneReactions.goalInstalled(zombie)) {
                        throw new IllegalStateException("Zombie reagiert trotz ignores_drones");
                    }
                }
                this.skeletonAssigned |= DroneReactions.assigned(this.skeleton).isPresent();
                this.arrows = Math.max(this.arrows, this.level.getEntitiesOfClass(AbstractArrow.class, this.skeleton.getBoundingBox().inflate(32.0),
                        arrow -> arrow.getOwner() == this.skeleton).size());
                if (this.ticks >= OBSERVE_TICKS) {
                    if (!this.skeletonAssigned) {
                        throw new IllegalStateException("Skelett reagiert nicht auf die Drohne");
                    }
                    this.notes.add(OBSERVE_TICKS + " Ticks: 2 Zombies ohne Zuordnung und ohne Ziel-Goal, Skelett zugeordnet, " + this.arrows + " Pfeile");
                    DroneReactions.setActiveFilter(null);
                    this.command("datapack disable \"" + CovMdCPack.PACK_ID + "\"");
                    this.phase = Phase.TAG_OFF;
                    this.ticks = 0;
                }
            }
            case TAG_OFF -> {
                if ("chase".equals(CovMdCPack.kind(EntityTypes.ZOMBIE))) {
                    CovMdCPack.remove(this.server);
                    this.notes.add("Paket aus: Zombie wieder chase (" + this.ticks + " Ticks)");
                    this.finish("PASS " + String.join("; ", this.notes), null);
                } else if (this.ticks > TAG_TICKS) {
                    throw new IllegalStateException("Zombie nach dem Abschalten des Pakets " + CovMdCPack.kind(EntityTypes.ZOMBIE));
                }
            }
            case DONE -> {
            }
        }
    }

    private void scene() {
        this.server.getGameRules().set(GameRules.ADVANCE_TIME, false, this.server);
        this.server.getGameRules().set(GameRules.SPAWN_MOBS, false, this.server);
        MobScene.rule(this.server, MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path()), "0");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                this.level.setChunkForced(cx, cz, true);
            }
        }
        int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        Entity drone = MobScene.placeDrone(this.level, new BlockPos(8, y - 1, 8), "cov-md-c Datenpaket");
        DroneReactions.setActiveFilter(view -> view.entity() == drone);
        Vec3 center = drone.position();
        for (int i = 0; i < 2; i++) {
            Mob zombie = MobScene.spawn(this.level, EntityTypes.ZOMBIE, center.add(i == 0 ? 3.0 : -3.0, 0.0, 1.0), 0.0F);
            zombie.setBaby(false);
            this.zombies.add(zombie);
        }
        this.skeleton = MobScene.spawn(this.level, EntityTypes.SKELETON, center.add(0.0, 0.0, -10.0), 0.0F);
    }

    private void finish(String result, RuntimeException error) {
        if (this.phase == Phase.DONE) {
            return;
        }
        this.phase = Phase.DONE;
        running = null;
        DroneReactions.setActiveFilter(null);
        if (error != null) {
            MobDefense.LOG.error("{} {}", TAG, result, error);
            if (CovMdCPack.enabled(this.server)) {
                this.command("datapack disable \"" + CovMdCPack.PACK_ID + "\"");
            }
        } else {
            MobDefense.LOG.info("{} {}", TAG, result);
        }
        Path dir = Services.PLATFORM.gameDir();
        try {
            Files.writeString(dir.resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(STOP_FILE), "cov-md-c fertig" + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.error("{} Ergebnis nicht geschrieben", TAG, e);
        }
    }
}
