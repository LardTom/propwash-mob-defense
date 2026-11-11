package de.lardtom.mobdefense.dev.dispenser.client;

import org.jspecify.annotations.Nullable;

public final class CompassProperty {
    public static final String NAME = "propwash.test.compassErrorDeg";

    private final String degrees;
    private boolean active;
    private @Nullable String before;

    public CompassProperty(String degrees) {
        this.degrees = degrees;
    }

    public static @Nullable String current() {
        return System.getProperty(NAME);
    }

    public synchronized void set() {
        if (!this.active) {
            this.before = current();
            this.active = true;
        }
        System.setProperty(NAME, this.degrees);
    }

    public synchronized void restore() {
        if (!this.active) {
            return;
        }
        this.active = false;
        if (this.before == null) {
            System.clearProperty(NAME);
        } else {
            System.setProperty(NAME, this.before);
        }
        this.before = null;
    }
}
