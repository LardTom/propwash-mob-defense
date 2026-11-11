package de.lardtom.mobdefense.dev.dispenser.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;

import java.util.ArrayList;
import java.util.List;

public final class CovMdASounds implements SoundEventListener {
    public static final String DISPENSE = "minecraft:block.dispenser.dispense";
    public static final String LAUNCH = "minecraft:block.dispenser.launch";
    public static final String FAIL = "minecraft:block.dispenser.fail";
    private static final CovMdASounds INSTANCE = new CovMdASounds();
    private static final List<String> HEARD = new ArrayList<>();
    private static boolean registered;

    private CovMdASounds() {
    }

    public static synchronized void listen() {
        if (registered) {
            return;
        }
        registered = true;
        Minecraft.getInstance().getSoundManager().addListener(INSTANCE);
    }

    public static synchronized void clear() {
        HEARD.clear();
    }

    public static synchronized List<String> heard() {
        return List.copyOf(HEARD);
    }

    public static synchronized int count(String id) {
        int count = 0;
        for (String sound : HEARD) {
            if (sound.equals(id)) {
                count++;
            }
        }
        return count;
    }

    public static synchronized List<String> dispenser() {
        List<String> out = new ArrayList<>();
        for (String sound : HEARD) {
            if (sound.startsWith("minecraft:block.dispenser.")) {
                out.add(sound.substring("minecraft:block.dispenser.".length()));
            }
        }
        return out;
    }

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
        synchronized (CovMdASounds.class) {
            HEARD.add(sound.getIdentifier().toString());
        }
    }
}
