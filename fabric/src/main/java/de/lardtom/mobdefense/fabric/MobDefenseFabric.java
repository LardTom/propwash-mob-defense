package de.lardtom.mobdefense.fabric;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.CadencePayload;
import de.lardtom.mobdefense.dispenser.DispenserCadences;
import de.lardtom.mobdefense.net.NoticePayload;
import de.lardtom.mobdefense.net.Notices;
import de.lardtom.mobdefense.payload.PayloadShield;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MobDefenseFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        MobDefense.init();
        ServerLifecycleEvents.SERVER_STARTED.register(MobDefense::serverStarted);
        ServerTickEvents.END_SERVER_TICK.register(MobDefense::serverTick);
        PayloadTypeRegistry.clientboundPlay().register(NoticePayload.TYPE, NoticePayload.CODEC);
        Notices.install((player, payload) -> {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> PayloadShield.allowDamage(entity, source));
        PayloadTypeRegistry.serverboundPlay().register(CadencePayload.TYPE, CadencePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CadencePayload.TYPE, (payload, context) -> DispenserCadences.accept(context.player(), payload));
    }
}
