package de.lardtom.mobdefense.dispenser;

import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.drone.DronePhysics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenserRecoilTest {
    static final double POTION_NS = 9.63;
    static final double POTION_GRAMS = 350.0;

    record Kick(Vec3 impulse, Vec3 point) {
    }

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
    }

    static MountPose frontMount(Vec3 barrel) {
        Vec3 forward = barrel.normalize();
        Vec3 center = new Vec3(0.0, -0.04, -0.06);
        return new MountPose(new Vec3(0.0, -0.04, -0.04), center, center.add(forward.scale(0.032)), forward, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0));
    }

    static AccessoryContext context(MountPose mount, double payloadGrams, List<Kick> kicks) {
        DronePhysics physics = new DronePhysics() {
            @Override
            public boolean impulse(Vec3 impulse, Vec3 framePoint) {
                kicks.add(new Kick(impulse, framePoint));
                return true;
            }

            @Override
            public boolean push(Vec3 force, boolean frameRelative, Vec3 framePoint, double seconds) {
                throw new AssertionError("der Rückstoß ist ein Stoß, keine Kraft über Zeit");
            }
        };
        AccessoryInventory inventory = (AccessoryInventory) Proxy.newProxyInstance(DispenserRecoilTest.class.getClassLoader(),
                new Class<?>[]{AccessoryInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "payloadGrams" -> payloadGrams;
                    case "size" -> 4;
                    default -> throw new AssertionError(method.getName());
                });
        return (AccessoryContext) Proxy.newProxyInstance(DispenserRecoilTest.class.getClassLoader(), new Class<?>[]{AccessoryContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "mount" -> mount;
                    case "physics" -> physics;
                    case "inventory" -> inventory;
                    case "drone" -> null;
                    case "velocity" -> Vec3.ZERO;
                    default -> throw new AssertionError(method.getName());
                });
    }

    static Kick fire(MountPose mount, double payloadGrams, double recoilNs) {
        List<Kick> kicks = new ArrayList<>();
        AccessoryContext context = context(mount, payloadGrams, kicks);
        DispenserRecoil.apply(context, DispenserRecoil.before(context), recoilNs);
        assertTrue(kicks.size() <= 1, "höchstens ein Stoß je Schuss: " + kicks);
        return kicks.isEmpty() ? null : kicks.getFirst();
    }

    @Test
    void everyPotionPushesThroughTheCenterOfMassWithoutALever() {
        MountPose mount = frontMount(new Vec3(0.0, 0.0, -1.0));
        for (int loaded = 4; loaded >= 1; loaded--) {
            Kick kick = fire(mount, loaded * POTION_GRAMS, POTION_NS);
            assertEquals(Vec3.ZERO, kick.point(), loaded + " Tränke geladen: Stoß am Schwerpunkt, kein Hebel, der die 5″ überschlägt");
            assertEquals(0.0, kick.impulse().x, 1.0E-9);
            assertEquals(0.0, kick.impulse().y, 1.0E-9);
            assertEquals(POTION_NS, kick.impulse().z, 1.0E-9, loaded + " Tränke geladen: voller Rückstoß entgegen dem Lauf");
        }
    }

    @Test
    void theImpulseFollowsATiltedBarrel() {
        Vec3 barrel = new Vec3(0.0, Math.sin(0.3), -Math.cos(0.3));
        Kick kick = fire(frontMount(barrel), 2 * POTION_GRAMS, 2.0);
        assertEquals(Vec3.ZERO, kick.point());
        assertEquals(2.0, kick.impulse().length(), 1.0E-9);
        assertEquals(-1.0, kick.impulse().normalize().dot(barrel), 1.0E-9);
    }

    @Test
    void aWindChargePushesThroughTheCenterOfMassToo() {
        MountPose mount = frontMount(new Vec3(0.0, -0.5, -1.0));
        Kick kick = fire(mount, 200.0, 1.8);
        assertEquals(Vec3.ZERO, kick.point(), "Windkugel am Schwerpunkt: die Drohne bleibt fliegbar, kein Hebel an der Mündung (Design §2.1, E27)");
        assertEquals(1.8, kick.impulse().length(), 1.0E-9);
        assertEquals(-1.0, kick.impulse().normalize().dot(mount.forward()), 1.0E-9);
    }

    @Test
    void noImpulseWithoutRecoil() {
        MountPose mount = frontMount(new Vec3(0.0, 0.0, -1.0));
        assertEquals(null, fire(mount, POTION_GRAMS, 0.0));
        assertEquals(null, fire(mount, POTION_GRAMS, -1.0));
        assertEquals(null, fire(mount, POTION_GRAMS, Double.NaN));
        assertEquals(null, fire(mount, POTION_GRAMS, Double.POSITIVE_INFINITY));
    }
}
