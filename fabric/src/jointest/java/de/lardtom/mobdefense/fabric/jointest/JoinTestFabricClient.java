package de.lardtom.mobdefense.fabric.jointest;

import de.lardtom.mobdefense.dev.join.JoinProbe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class JoinTestFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!JoinProbe.requested()) {
            return;
        }
        JoinProbe.install(false);
        ClientTickEvents.END_CLIENT_TICK.register(JoinProbe::tick);
    }
}
