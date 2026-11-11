package de.lardtom.mobdefense.net;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.accessory.DispenseResult;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;

public enum Notice {
    NO_AMMO,
    BUCKET_LOCKED,
    TNT_LOCKED,
    BARREL_BLOCKED,
    NO_PERMISSION,
    THREAT,
    HIT;

    public static final int DEFAULT_TICKS = 40;
    public static final int MAX_TICKS = 200;

    public String path() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public Identifier id() {
        return MobDefense.id("warning/" + this.path());
    }

    public String translationKey() {
        return MobDefense.MOD_ID + ".osd.warning." + this.path();
    }

    public static Notice byIndex(int index) {
        Notice[] all = values();
        return index >= 0 && index < all.length ? all[index] : NO_AMMO;
    }

    public static Optional<Notice> of(DispenseResult.Status status) {
        return switch (status) {
            case EMPTY -> Optional.of(NO_AMMO);
            case BLOCKED -> Optional.of(BARREL_BLOCKED);
            case DENIED -> Optional.of(NO_PERMISSION);
            default -> Optional.empty();
        };
    }
}
