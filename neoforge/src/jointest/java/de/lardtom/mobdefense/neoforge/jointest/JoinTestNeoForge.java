package de.lardtom.mobdefense.neoforge.jointest;

import de.lardtom.mobdefense.dev.join.JoinProbe;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "mobdefense_jointest", dist = Dist.CLIENT)
public final class JoinTestNeoForge {
    public JoinTestNeoForge() {
        if (!JoinProbe.requested()) {
            return;
        }
        JoinProbe.install(false);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> JoinProbe.tick(Minecraft.getInstance()));
    }
}
