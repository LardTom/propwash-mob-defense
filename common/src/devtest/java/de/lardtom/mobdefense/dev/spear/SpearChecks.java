package de.lardtom.mobdefense.dev.spear;

import com.mojang.authlib.GameProfile;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.mobdefense.spear.Spears;
import de.lardtom.mobdefense.spear.core.SpearDamage;
import de.lardtom.mobdefense.spear.core.SpearPose;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

public final class SpearChecks {
    public static final Identifier SPEAR_MOUNT = MobDefense.SPEAR_MOUNT;
    public static final double TEST_SPEED = 15.0;
    public static final float IRON_MULTIPLIER = 0.95F;
    public static final float EXPECTED_IRON_15 = SpearDamage.scaled(TEST_SPEED, IRON_MULTIPLIER, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
    public static final Identifier PAYLOADS_HIT_PLAYERS = MobDefense.id(RuleDefaults.PAYLOADS_HIT_PLAYERS.path());
    private static final String TAG = "[Mob-Defense-Speer]";
    private static boolean forcedByD3;

    private SpearChecks() {
    }

    public static AccessoryBehavior behavior() {
        return PropwashApi.accessories().behavior(SPEAR_MOUNT)
                .orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Verhalten (PropwashApi.accessories)"));
    }

    public static BuildSpec build() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300")).withAccessory(AccessorySlot.BOTTOM, SPEAR_MOUNT);
    }

    public static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    public static String accepts() {
        AccessoryBehavior behavior = behavior();
        List<String> problems = new ArrayList<>();
        if (behavior.slots() != 1) {
            problems.add(behavior.slots() + " Slots statt 1");
        }
        List<String> spears = new ArrayList<>();
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.SPEARS)) {
            ItemStack stack = new ItemStack(item);
            spears.add(Spears.id(stack));
            if (!behavior.accepts(0, stack)) {
                problems.add(Spears.id(stack) + " abgelehnt");
            }
            if (behavior.maxStackSize(0, stack) != 1) {
                problems.add(Spears.id(stack) + ": Stapel " + behavior.maxStackSize(0, stack));
            }
        }
        if (spears.size() < 7) {
            problems.add("nur " + spears.size() + " Speere im Tag #minecraft:spears");
        }
        if (behavior.accepts(1, new ItemStack(Items.IRON_SPEAR))) {
            problems.add("Slot 1 nimmt einen Speer");
        }
        for (Item item : List.of(Items.TRIDENT, Items.IRON_SWORD, Items.STICK, Items.MACE, Items.ARROW)) {
            ItemStack stack = new ItemStack(item);
            if (behavior.accepts(0, stack)) {
                problems.add(Spears.id(stack) + " angenommen");
            }
        }
        ItemStack bare = new ItemStack(Items.IRON_SPEAR);
        KineticWeapon kinetic = bare.get(DataComponents.KINETIC_WEAPON);
        if (kinetic == null) {
            problems.add("Eisenspeer ohne Komponente minecraft:kinetic_weapon");
        } else {
            bare.remove(DataComponents.KINETIC_WEAPON);
            if (behavior.accepts(0, bare)) {
                problems.add("Eisenspeer ohne Kinetik-Komponente angenommen");
            }
            ItemStack stick = new ItemStack(Items.STICK);
            stick.set(DataComponents.KINETIC_WEAPON, kinetic);
            if (behavior.accepts(0, stick)) {
                problems.add("Stock mit Kinetik-Komponente, aber ohne Tag angenommen");
            }
        }
        OptionalDouble iron = behavior.payloadGrams(List.of(new ItemStack(Items.IRON_SPEAR)));
        OptionalDouble netherite = behavior.payloadGrams(List.of(new ItemStack(Items.NETHERITE_SPEAR)));
        if (iron.isEmpty() || Math.abs(iron.getAsDouble() - 300.0) > 1.0e-9 || netherite.isEmpty() || Math.abs(netherite.getAsDouble() - 360.0) > 1.0e-9) {
            problems.add("Beladung Eisen " + iron + ", Netherit " + netherite + " statt 300 g und 360 g");
        }
        Optional<ItemDisplay> display = behavior.display(0);
        if (display.isEmpty() || Math.abs(display.get().scale() - SpearPose.SCALE) > 1.0e-6) {
            problems.add("Anzeige " + display + " statt Maßstab " + SpearPose.SCALE);
        }
        if (behavior.display(1).isPresent()) {
            problems.add("Anzeige für Slot 1");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return spears.size() + " Speere angenommen, Dreizack, Schwert, Stock, Streitkolben, Pfeil, Speer ohne Kinetik-Komponente und Stock mit "
                + "Komponente abgelehnt, Stapel 1, Beladung Eisen 300 g, Netherit 360 g, Anzeige Maßstab " + SpearPose.SCALE;
    }

    public static Entity placeDrone(ServerLevel level, BlockPos ground, BuildSpec spec) {
        ItemStack item = PropwashApi.builds().createDrone(DroneData.of(spec));
        Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, item, new BlockHitResult(click, Direction.UP, ground, false)) {
        };
        InteractionResult placed = item.getItem().useOn(context);
        if (!placed.consumesAction()) {
            throw new IllegalStateException("Drohne nicht abgestellt: " + placed);
        }
        return PropwashApi.drones().dronesNear(level, click, 3.0, false).stream().findFirst().map(DroneView::entity)
                .orElseThrow(() -> new IllegalStateException("abgestellte Drohne bei " + ground.above().toShortString() + " nicht gefunden"));
    }

    public static Vec3 nose(Entity drone) {
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Drohne"));
        Vec3 flat = new Vec3(view.forward().x, 0.0, view.forward().z);
        if (flat.lengthSqr() < 1.0e-6) {
            throw new IllegalStateException("Nase zeigt senkrecht: " + view.forward());
        }
        return flat.normalize();
    }

    public static ServerPlayer fakePlayer(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("mobdefense-spear-" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        for (int i = 0; i < 100 && !player.connection.hasClientLoaded(); i++) {
            player.connection.tickClientLoadTimeout();
        }
        if (!player.connection.hasClientLoaded()) {
            throw new IllegalStateException(name + " gilt nicht als geladen");
        }
        return player;
    }

    public static <T> void rule(MinecraftServer server, GameRule<T> rule, String value) {
        T parsed = rule.deserialize(value).result().orElseThrow(() -> new IllegalStateException("Wert " + value + " passt nicht zu " + rule.id()));
        server.getGameRules().set(rule, parsed, server);
        String now = server.getGameRules().getAsString(rule);
        if (!value.equals(now)) {
            throw new IllegalStateException("Gamerule " + rule.id() + " ist " + now + " statt " + value);
        }
    }

    public static GameRule<?> payloadsRule() {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(PAYLOADS_HIT_PLAYERS);
        if (rule == null) {
            throw new IllegalStateException("Gamerule " + PAYLOADS_HIT_PLAYERS + " fehlt");
        }
        return rule;
    }

    public static BlockPos d3Column(MinecraftServer server) {
        BlockPos spawn = server.getRespawnData().globalPos().pos();
        return new BlockPos(spawn.getX() + 6, 0, spawn.getZ() + 6);
    }

    public static String d3Area(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos column = d3Column(server);
        int cx = column.getX() >> 4;
        int cz = column.getZ() >> 4;
        forcedByD3 = !level.getForceLoadedChunks().contains(ChunkPos.pack(cx, cz));
        if (forcedByD3 && !level.setChunkForced(cx, cz, true)) {
            throw new IllegalStateException("Chunk " + cx + "," + cz + " lässt sich nicht dauerhaft laden");
        }
        return "Chunk " + cx + "," + cz + " für D3 dauerhaft geladen" + (forcedByD3 ? "" : " (war es schon)");
    }

    public static String d3(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos column = d3Column(server);
        int x = column.getX();
        int z = column.getZ();
        try {
            return d3(server, level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z));
        } finally {
            if (forcedByD3) {
                level.setChunkForced(x >> 4, z >> 4, false);
                forcedByD3 = false;
            }
        }
    }

    private static String d3(MinecraftServer server, ServerLevel level, BlockPos ground) {
        GameRule<?> payloads = payloadsRule();
        String payloadsBefore = server.getGameRules().getAsString(payloads);
        String pvpBefore = server.getGameRules().getAsString(GameRules.PVP);
        Entity drone = placeDrone(level, ground, build());
        Zombie zombie = null;
        try {
            Vec3 nose = nose(drone);
            Vec3 velocity = nose.scale(TEST_SPEED);
            ServerPlayer pilot = fakePlayer(server, level, "SpeerPilot");
            ServerPlayer target = fakePlayer(server, level, "SpeerZiel");
            pilot.snapTo(drone.getX() - nose.x * 2.0, drone.getY(), drone.getZ() - nose.z * 2.0);
            target.snapTo(drone.getX() + nose.x, drone.getY(), drone.getZ() + nose.z);
            List<String> problems = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (boolean rule : new boolean[]{false, true}) {
                for (boolean pvp : new boolean[]{false, true}) {
                    rule(server, payloads, Boolean.toString(rule));
                    rule(server, GameRules.PVP, Boolean.toString(pvp));
                    reset(target);
                    ItemStack spear = new ItemStack(Items.IRON_SPEAR);
                    SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, pilot, spear, target, nose, velocity);
                    float lost = target.getMaxHealth() - target.getHealth();
                    boolean expected = rule && pvp;
                    String label = "payloads_hit_players=" + rule + ", pvp=" + pvp;
                    if (expected) {
                        DamageSource source = target.getLastDamageSource();
                        if (!hit.hurt() || Math.abs(lost - EXPECTED_IRON_15) > 1.0e-3) {
                            problems.add(label + ": " + format(lost) + " Schaden statt " + format(EXPECTED_IRON_15) + " (" + hit + ")");
                        } else if (source == null || !source.is(DamageTypes.SPEAR) || source.getEntity() != pilot || source.getDirectEntity() != drone) {
                            problems.add(label + ": Schadensquelle " + describe(source) + " statt minecraft:spear, Pilot, Drohne");
                        } else if (spear.getDamageValue() != 1) {
                            problems.add(label + ": Haltbarkeit −" + spear.getDamageValue() + " statt −1");
                        } else {
                            notes.add(label + ": " + format(lost) + " Schaden");
                        }
                    } else {
                        SpearStrikes.Skip want = rule ? SpearStrikes.Skip.PVP : SpearStrikes.Skip.PLAYER_RULE;
                        if (hit.skip() != want || lost > 0.0F || spear.getDamageValue() != 0) {
                            problems.add(label + ": " + hit.skip() + " statt " + want + ", " + format(lost) + " Schaden, Haltbarkeit −" + spear.getDamageValue());
                        } else {
                            notes.add(label + ": kein Treffer (" + want + ")");
                        }
                    }
                }
            }
            reset(target);
            SpearStrikes.Hit unpiloted = SpearStrikes.stab(level, drone, null, new ItemStack(Items.IRON_SPEAR), target, nose, velocity);
            if (unpiloted.skip() != SpearStrikes.Skip.NO_ATTACKER || target.getHealth() < target.getMaxHealth()) {
                problems.add("ohne Pilot: " + unpiloted.skip() + ", Gesundheit " + format(target.getHealth()));
            }
            reset(pilot);
            SpearStrikes.Hit self = SpearStrikes.stab(level, drone, pilot, new ItemStack(Items.IRON_SPEAR), pilot, nose, velocity);
            if (self.skip() != SpearStrikes.Skip.SELF || pilot.getHealth() < pilot.getMaxHealth()) {
                problems.add("eigener Pilot: " + self.skip() + ", Gesundheit " + format(pilot.getHealth()));
            }
            rule(server, payloads, "false");
            rule(server, GameRules.PVP, "false");
            zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (zombie == null) {
                throw new IllegalStateException("Zombie nicht erzeugt");
            }
            zombie.setNoAi(true);
            zombie.snapTo(drone.getX() + nose.x, ground.getY() + 1.0, drone.getZ() + nose.z, 0.0F, 0.0F);
            level.addFreshEntity(zombie);
            SpearStrikes.Hit mob = SpearStrikes.stab(level, drone, pilot, new ItemStack(Items.IRON_SPEAR), zombie, nose, velocity);
            float mobLost = zombie.getMaxHealth() - zombie.getHealth();
            if (!mob.hurt() || Math.abs(mobLost - EXPECTED_IRON_15) > 1.0F) {
                problems.add("Zombie mit beiden Regeln aus: " + format(mobLost) + " Schaden statt etwa " + format(EXPECTED_IRON_15) + " (" + mob + ")");
            } else {
                notes.add("Zombie mit beiden Regeln aus: " + format(mobLost) + " Schaden");
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(String.join("; ", problems));
            }
            float formula = SpearDamage.scaled(TEST_SPEED, IRON_MULTIPLIER, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
            MobDefense.LOG.info("{} D3 Speer: {}", TAG, String.join("; ", notes));
            return "D3 Speer mit Eisenspeer bei 15 m/s (Formel " + format(formula) + "): " + String.join("; ", notes)
                    + "; ohne Pilot und gegen den eigenen Piloten kein Treffer";
        } finally {
            set(server, payloads, payloadsBefore);
            set(server, GameRules.PVP, pvpBefore);
            if (zombie != null) {
                zombie.discard();
            }
            drone.discard();
        }
    }

    private static <T> void set(MinecraftServer server, GameRule<T> rule, String value) {
        rule.deserialize(value).result().ifPresent(parsed -> server.getGameRules().set(rule, parsed, server));
    }

    private static void reset(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
        entity.setInvulnerableTime(0);
        entity.hurtTime = 0;
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static String describe(DamageSource source) {
        if (source == null) {
            return "keine";
        }
        return source.typeHolder().getRegisteredName() + ", Verursacher " + (source.getEntity() == null ? "keiner" : source.getEntity().getName().getString())
                + ", direkt " + (source.getDirectEntity() == null ? "keiner" : source.getDirectEntity().getType().toShortString());
    }

    public static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
