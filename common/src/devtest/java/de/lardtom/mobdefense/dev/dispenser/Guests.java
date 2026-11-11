package de.lardtom.mobdefense.dev.dispenser;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Guests {
    private static final List<ServerPlayer> PLACED = new ArrayList<>();

    private Guests() {
    }

    public static ServerPlayer create(MinecraftServer server, String name) {
        UUID id = UUID.nameUUIDFromBytes(("mobdefense-selftest:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    public static ServerPlayer place(MinecraftServer server, String name, ServerLevel level, Vec3 at, float yaw) {
        ServerPlayer player = create(server, name);
        player.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        level.addNewPlayer(player);
        synchronized (PLACED) {
            PLACED.add(player);
        }
        return player;
    }

    public static void ready(ServerPlayer player, float health) {
        player.removeAllEffects();
        player.setHealth(health);
        player.setInvulnerableTime(0);
        player.hurtTime = 0;
        player.setDeltaMovement(Vec3.ZERO);
    }

    public static void removeAll() {
        List<ServerPlayer> players;
        synchronized (PLACED) {
            players = new ArrayList<>(PLACED);
            PLACED.clear();
        }
        for (ServerPlayer player : players) {
            if (!player.isRemoved()) {
                player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
        }
    }

    public static int placed() {
        synchronized (PLACED) {
            return PLACED.size();
        }
    }
}
