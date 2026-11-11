package de.lardtom.mobdefense.dev.booster.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.BoostState;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.dev.booster.D4Scene;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.join.JoinProbe;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class D4Spectator {
    public static final String RESULT_FILE = "d4-spectator.txt";
    public static final Identifier PROBE = MobDefense.id("d4_probe");
    public static final int CHECK_TICK = 24;
    private static final String TAG = "[Mob-Defense-D4]";
    private static final AtomicInteger FOREIGN_OSD = new AtomicInteger();
    private static final AtomicInteger ANY_OSD = new AtomicInteger();
    private static int inWorld;
    private static boolean done;

    private D4Spectator() {
    }

    public static void init() {
        if (!D4Scene.enabled() || !JoinProbe.requested()) {
            return;
        }
        PropwashClientApi.registerOsdElement(OsdElementSpec.builder(PROBE).nameKey("mobdefense.osd.booster").position(0, 0).enabledByDefault(true)
                .maxChars(2).preview("D4").content(context -> {
                    if (!context.preview()) {
                        ANY_OSD.incrementAndGet();
                        Minecraft mc = Minecraft.getInstance();
                        Optional<Entity> watched = context.drone();
                        if (watched.isPresent() && mc.player != null && !mc.player.getUUID().equals(PropwashApi.drones().view(watched.get())
                                .flatMap(DroneView::pilot).orElse(mc.player.getUUID()))) {
                            FOREIGN_OSD.incrementAndGet();
                        }
                    }
                    return OsdText.of("D4");
                }).build());
        MobDefenseClient.onClientTick(D4Spectator::tick);
        MobDefense.LOG.info("{} Zuschauer-Prüfung aktiv", TAG);
    }

    private static void tick(Minecraft mc) {
        if (done || mc.level == null || mc.player == null || mc.gui.screen() != null) {
            return;
        }
        inWorld++;
        if (inWorld < CHECK_TICK) {
            return;
        }
        done = true;
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        check(mc, problems, notes);
        String result = (problems.isEmpty() ? "PASS D4 Zuschauer: " + String.join(" | ", notes)
                : "FAIL D4 Zuschauer: " + String.join("; ", problems) + (notes.isEmpty() ? "" : " | in Ordnung: " + String.join(" | ", notes)));
        if (problems.isEmpty()) {
            MobDefense.LOG.info("{} {}", TAG, result);
        } else {
            MobDefense.LOG.error("{} {}", TAG, result);
        }
        try {
            Path dir = mc.gameDirectory.toPath().resolve(JoinProbe.FOLDER);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.error("{} {} nicht geschrieben", TAG, RESULT_FILE, e);
        }
    }

    private static void check(Minecraft mc, List<String> problems, List<String> notes) {
        List<Entity> drones = Arena.clientDrones(mc);
        Entity drone = null;
        for (Entity entity : drones) {
            if (drone == null || entity.distanceToSqr(mc.player) < drone.distanceToSqr(mc.player)) {
                drone = entity;
            }
        }
        if (drone == null) {
            problems.add("keine Drohne am Client");
            return;
        }
        notes.add(String.format("Drohne in %.1f m", Math.sqrt(drone.distanceToSqr(mc.player))));
        UUID self = mc.player.getUUID();
        Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        if (pilot.isPresent() && pilot.get().equals(self)) {
            problems.add("Zuschauer fliegt die Drohne selbst");
        } else {
            notes.add("fremde Drohne, Pilot " + pilot.map(UUID::toString).orElse("keiner"));
        }
        if (PropwashClientApi.input().flying() || PropwashClientApi.input().drone().isPresent()) {
            problems.add("Zuschauer fliegt selbst");
        }
        shown(drone, AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER, "Rakete", stack -> stack.is(Items.FIREWORK_ROCKET), problems, notes);
        if (PropwashApi.accessories().behavior(MobDefense.SPEAR_MOUNT).isPresent()) {
            shown(drone, AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT, "Speer", stack -> stack.has(DataComponents.KINETIC_WEAPON), problems, notes);
        } else {
            notes.add("Speer-Halterung ohne Verhalten (md-spear nicht gemergt), Speer nicht geprüft");
        }
        BoostState state = BoostState.of(drone);
        notes.add("Booster-Daten am Client lesbar (ends_at " + state.endsAt() + ")");
        int foreign = FOREIGN_OSD.get();
        int any = ANY_OSD.get();
        if (foreign > 0 || any > 0) {
            problems.add("OSD gezeichnet: " + any + " Mal, davon " + foreign + " Mal für die fremde Drohne");
        } else {
            notes.add("kein OSD gezeichnet (Probe-Element 0 Mal in " + inWorld + " Ticks)");
        }
    }

    private static void shown(Entity drone, AccessorySlot slot, Identifier part, String what, Predicate<ItemStack> expected,
                              List<String> problems, List<String> notes) {
        Optional<Identifier> mounted = PropwashApi.accessories().accessoryAt(drone, slot);
        if (mounted.isEmpty() || !mounted.get().equals(part)) {
            problems.add(what + ": Halterung " + slot + " trägt " + mounted.map(Identifier::toString).orElse("nichts"));
            return;
        }
        List<ItemStack> contents = PropwashApi.accessories().contents(drone, slot);
        ItemStack stack = contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
        if (stack.isEmpty() || !expected.test(stack)) {
            problems.add(what + " fehlt am Client (" + stack + ")");
            return;
        }
        Optional<AccessoryBehavior> behavior = PropwashApi.accessories().behavior(part);
        if (behavior.isEmpty() || behavior.get().display(0).isEmpty()) {
            problems.add(what + ": keine Anzeige über B2");
            return;
        }
        notes.add(what + " " + stack.getItem() + " am Client mit Anzeige " + (part.equals(MobDefense.ROCKET_BOOSTER) ? BoosterBehavior.DISPLAY : behavior.get().display(0).get()));
    }
}
