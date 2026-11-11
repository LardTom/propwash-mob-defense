package de.lardtom.mobdefense.dispenser;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.phys.Vec3;

public final class DispenserRecoil {
    public record Before(Vec3 forward, Vec3 barrel) {
    }

    private DispenserRecoil() {
    }

    static Before before(AccessoryContext context) {
        MountPose mount = context.mount();
        Vec3 forward = PropwashApi.drones().view(context.drone()).map(DroneView::forward).orElse(mount.forward());
        return new Before(forward, mount.forward());
    }

    static void apply(AccessoryContext context, Before before, double recoilNs) {
        if (!(recoilNs > 0.0) || !Double.isFinite(recoilNs)) {
            return;
        }
        Vec3 barrel = before.barrel().lengthSqr() > 1.0E-12 ? before.barrel().normalize() : before.forward();
        context.physics().impulse(barrel.scale(-recoilNs), Vec3.ZERO);
    }
}
