package de.lardtom.mobdefense.payload;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PayloadAwards {
    public static final String AIR_SUPPORT = "air_support";
    public static final String RECOIL = "recoil";
    public static final String CRITERION = "done";

    private PayloadAwards() {
    }

    public static boolean award(ServerPlayer player, String path) {
        MinecraftServer server = player.level().getServer();
        AdvancementHolder holder = server == null ? null : server.getAdvancements().get(MobDefense.id(path));
        return holder != null && player.getAdvancements().award(holder, CRITERION);
    }
}
