package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.mixin.MobAccessor;
import de.lardtom.mobdefense.mixin.PhantomAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;

import java.util.function.BooleanSupplier;

public final class Access {
    public static final boolean MOB = probe("MobAccessor", () -> MobAccessor.class.isAssignableFrom(Mob.class));
    public static final boolean PHANTOM = probe("PhantomAccessor", () -> PhantomAccessor.class.isAssignableFrom(Phantom.class));

    private Access() {
    }

    private static boolean probe(String name, BooleanSupplier check) {
        try {
            if (check.getAsBoolean()) {
                return true;
            }
            MobDefense.LOG.warn("{}: Accessor {} nicht angewandt, die Reaktionen dafür bleiben aus", MobDefense.MOD_NAME, name);
        } catch (Throwable error) {
            MobDefense.LOG.warn("{}: Accessor {} nicht verfügbar, die Reaktionen dafür bleiben aus", MobDefense.MOD_NAME, name, error);
        }
        return false;
    }
}
