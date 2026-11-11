package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.FlightAssist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobScene {
    public static final Identifier HOVER = MobDefense.id("selftest_hover");
    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Push> PUSHES = new ArrayList<>();
    private static boolean listening;
    private static volatile boolean fireProof;

    public record Hit(Entity drone, String cause, String direct, float original, float amount) {
    }

    public record Push(Entity drone, DroneKnockback.Cause cause, Vec3 delta, double scale) {
    }

    private MobScene() {
    }

    public static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.DRONE_DAMAGED.register(MobScene::damaged);
        PropwashEvents.DRONE_KNOCKBACK.register(MobScene::pushed);
    }

    public static void protectFromFire(boolean value) {
        fireProof = value;
    }

    private static synchronized void damaged(DroneDamage damage) {
        if (fireProof && damage.source().is(DamageTypeTags.IS_FIRE)) {
            damage.cancel();
            return;
        }
        Entity cause = damage.source().getEntity();
        Entity direct = damage.source().getDirectEntity();
        HITS.add(new Hit(damage.drone(), cause == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(cause.getType()).getPath(),
                direct == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath(), damage.originalAmount(), damage.amount()));
    }

    private static synchronized void pushed(DroneKnockback knockback) {
        PUSHES.add(new Push(knockback.drone(), knockback.cause(), knockback.deltaVelocity(), knockback.scale()));
    }

    public static synchronized void clearEvents() {
        HITS.clear();
        PUSHES.clear();
    }

    public static synchronized List<Hit> hits(Entity drone) {
        return HITS.stream().filter(hit -> hit.drone() == drone).toList();
    }

    public static synchronized List<Push> pushes(Entity drone) {
        return PUSHES.stream().filter(push -> push.drone() == drone).toList();
    }

    public static BuildSpec build() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300"));
    }

    public static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    public static Entity placeDrone(ServerLevel level, BlockPos ground, String name) {
        ItemStack item = PropwashApi.builds().createDrone(DroneData.of(build()).withName(name));
        Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, item, new BlockHitResult(click, Direction.UP, ground, false)) {
        };
        InteractionResult placed = item.getItem().useOn(context);
        if (!placed.consumesAction()) {
            throw new IllegalStateException("Drohne nicht abgestellt: " + placed);
        }
        return PropwashApi.drones().dronesNear(level, click, 2.0, false).stream()
                .filter(view -> view.entity().tickCount < 5)
                .min(Comparator.comparingDouble(view -> view.center().distanceToSqr(click)))
                .map(DroneView::entity)
                .orElseThrow(() -> new IllegalStateException("abgestellte Drohne bei " + ground.above().toShortString() + " nicht gefunden"));
    }

    public static FlightAssist hold(double x, double y, double z) {
        return (state, output) -> {
            output.holdPosition(x, z);
            output.holdAltitude(y);
            output.label("MD TEST");
        };
    }

    public static AutonomyResult hover(Entity drone, ServerPlayer owner, double metres) {
        double y = drone.getY() + metres;
        AutonomyOptions options = AutonomyOptions.of(HOVER).label("MD TEST").maxDuration(Duration.ofMinutes(10)).takeoff(Math.min(30.0, metres));
        return PropwashApi.drones().autonomy().start(drone, owner, hold(drone.getX(), y, drone.getZ()), options);
    }

    public static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 at, float yaw) {
        T mob = type.spawn(level, BlockPos.containing(at), EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " nicht gespawnt");
        }
        mob.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.setPersistenceRequired();
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false), null);
        return mob;
    }

    public static int clearProjectiles(ServerLevel level, AABB box) {
        int count = 0;
        for (Entity entity : level.getEntitiesOfClass(Projectile.class, box, entity -> true)) {
            entity.discard();
            count++;
        }
        return count;
    }

    public static void rule(MinecraftServer server, Identifier id, String value) {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
        if (rule == null) {
            throw new IllegalStateException("Gamerule " + id + " fehlt");
        }
        set(server, rule, value);
        String now = server.getGameRules().getAsString(rule);
        if (!value.equals(now)) {
            throw new IllegalStateException("Gamerule " + id + " ist " + now + " statt " + value);
        }
    }

    private static <T> void set(MinecraftServer server, GameRule<T> rule, String value) {
        T parsed = rule.deserialize(value).result().orElseThrow(() -> new IllegalStateException("Wert " + value + " passt nicht zu " + rule.id()));
        server.getGameRules().set(rule, parsed, server);
    }

    public static double horizontal(Entity a, Vec3 b) {
        return Math.hypot(a.getX() - b.x, a.getZ() - b.z);
    }
}
