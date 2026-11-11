package de.lardtom.mobdefense.neoforge.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.CadenceSync;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.net.NoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MobDefense.MOD_ID, dist = Dist.CLIENT)
public final class MobDefenseNeoForgeClient {
    public MobDefenseNeoForgeClient(IEventBus modBus, ModContainer container) {
        MobDefenseClient.init();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> MobDefenseClient.tick(Minecraft.getInstance()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(NoticePayload.TYPE,
                (payload, context) -> MobDefenseClient.notice(payload)));
        CadenceSync.install(payload -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null || !connection.hasChannel(payload)) {
                return false;
            }
            ClientPacketDistributor.sendToServer(payload);
            return true;
        });
    }
}
