package de.lardtom.mobdefense.neoforge;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.CadencePayload;
import de.lardtom.mobdefense.dispenser.DispenserCadences;
import de.lardtom.mobdefense.net.NoticePayload;
import de.lardtom.mobdefense.net.Notices;
import de.lardtom.mobdefense.payload.PayloadShield;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(MobDefense.MOD_ID)
public final class MobDefenseNeoForge {
    public MobDefenseNeoForge(IEventBus modBus, ModContainer container) {
        MobDefense.init();
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> MobDefense.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> MobDefense.serverTick(event.getServer()));
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToClient(NoticePayload.TYPE, NoticePayload.CODEC));
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToServer(CadencePayload.TYPE, CadencePayload.CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DispenserCadences.accept(player, payload);
                    }
                }));
        Notices.install((player, payload) -> {
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event -> {
            if (!PayloadShield.allowDamage(event.getEntity(), event.getSource())) {
                event.setCanceled(true);
            }
        });
    }
}
