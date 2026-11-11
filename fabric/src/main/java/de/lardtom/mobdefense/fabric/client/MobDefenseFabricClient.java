package de.lardtom.mobdefense.fabric.client;

import de.lardtom.mobdefense.client.CadenceSync;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.net.NoticePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MobDefenseFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MobDefenseClient.init();
        ClientTickEvents.END_CLIENT_TICK.register(MobDefenseClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(NoticePayload.TYPE, (payload, context) -> MobDefenseClient.notice(payload));
        CadenceSync.install(payload -> {
            if (!ClientPlayNetworking.canSend(payload.type())) {
                return false;
            }
            ClientPlayNetworking.send(payload);
            return true;
        });
    }
}
