package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class MobDefenseGuide {
    public static final String DISPENSER = MobDefense.MOD_ID + ":dispenser";
    public static final String SPEAR = MobDefense.MOD_ID + ":spear";
    public static final String BOOSTER = MobDefense.MOD_ID + ":booster";
    public static final List<String> SCENES = List.of(DISPENSER, SPEAR, BOOSTER);
    public static final String ACTION_1 = "key.propwash.accessory_action_1";
    public static final String ACTION_2 = "key.propwash.accessory_action_2";
    public static final String ACTION_3 = "key.propwash.accessory_action_3";
    private static final String DRONE = "drone";
    private static final BlockSpec GRASS = BlockSpec.of("minecraft:grass_block");
    private static final BlockSpec TARGET = BlockSpec.of("minecraft:target");
    private static final float SCALE = 2.0F;
    private static final float NOSE_YAW = -90.0F;
    private static final float FORWARD_ITEM_YAW = -45.0F;
    private static final float BACKWARD_ITEM_YAW = 135.0F;
    private static final float FLAT = 90.0F;
    private static final float VIEW = 2.2F;
    private static final float OVERVIEW_ZOOM = 1.3F;
    private static final float FLIGHT_ZOOM = 1.2F;
    private static final ScenePos GROUND = new ScenePos(0.0F, 1.0F, 0.0F);
    private static final ScenePos HOVER = new ScenePos(0.0F, 2.0F, 0.0F);
    private static final ScenePos OVERVIEW = new ScenePos(0.3F, 1.6F, 0.0F);
    private static final ScenePos NOSE = new ScenePos(0.45F, 0.05F, 0.0F);
    private static final ScenePos BELLY = new ScenePos(0.1F, -0.12F, 0.0F);
    private static final ScenePos ROOF = new ScenePos(-0.05F, 0.16F, 0.0F);
    private static final float OVERVIEW_YAW = 25.0F;
    private static final float CLOSE_ZOOM = 2.0F;
    private static final List<String> REGISTERED = new ArrayList<>();
    private static boolean registered;

    private MobDefenseGuide() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        add(DISPENSER, MobDefense.DISPENSER_MOUNT, MobDefenseGuide::dispenser);
        add(SPEAR, MobDefense.SPEAR_MOUNT, MobDefenseGuide::spear);
        add(BOOSTER, MobDefense.ROCKET_BOOSTER, MobDefenseGuide::booster);
    }

    public static synchronized List<String> registered() {
        return List.copyOf(REGISTERED);
    }

    private static void add(String id, Identifier part, Supplier<Scene> scene) {
        try {
            PropwashClientApi.registerFlightSchoolScene(scene);
            PropwashClientApi.linkFlightSchoolItems(id, part.toString());
            REGISTERED.add(id);
        } catch (RuntimeException e) {
            MobDefense.LOG.error("{}: Flugschul-Szene {} nicht angemeldet", MobDefense.MOD_NAME, id, e);
        }
    }

    public static BuildSpec build(AccessorySlot slot, Identifier part) {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300")).withAccessory(slot, part);
    }

    private static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    private static ActorSpec.Drone drone(AccessorySlot slot, Identifier part) {
        return PropwashClientApi.flightSchoolDrone(build(slot, part), true, SCALE);
    }

    private static ActorSpec.Item item(String id, float scale) {
        return new ActorSpec.Item(id, scale, false);
    }

    private static Anchor on(ScenePos offset) {
        return Anchor.on(DRONE, offset.x(), offset.y(), offset.z());
    }

    private static void ground(SceneBuilder.StepBuilder s) {
        s.at(0.0F).floor(-3, -3, 3, 3, 0, GRASS, 1.0F, 10.0F);
    }

    private static void takeOff(SceneBuilder.StepBuilder s, float at) {
        s.at(at).arm(DRONE, true);
        s.at(at).throttle(DRONE, 0.55F, 12.0F);
        s.at(at + 4.0F).move(DRONE, 24.0F, Ease.IN_OUT, HOVER);
    }

    private static void land(SceneBuilder.StepBuilder s, float at, ScenePos from) {
        s.at(at).move(DRONE, 30.0F, Ease.IN_OUT, from.add(0.0F, -0.6F, 0.0F), GROUND);
        s.at(at + 22.0F).throttle(DRONE, 0.08F, 10.0F);
        s.at(at + 34.0F).arm(DRONE, false);
    }

    public static Scene dispenser() {
        String arrow = "minecraft:arrow";
        return SceneBuilder.scene(DISPENSER)
                .icon(MobDefense.DISPENSER_MOUNT.toString())
                .view(VIEW)
                .camera(OVERVIEW, OVERVIEW_YAW, 24.0F, OVERVIEW_ZOOM)
                .step("load", 150.0F, s -> {
                    ground(s);
                    s.at(12.0F).spawn(DRONE, drone(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT), GROUND, NOSE_YAW, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).camera(GROUND.add(0.3F, 0.4F, 0.0F), OVERVIEW_YAW, 14.0F, CLOSE_ZOOM, 22.0F);
                    s.at(24.0F).spawn("arrow", item(arrow, 0.5F), new ScenePos(1.3F, 1.9F, 0.6F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(28.0F).spawn("potion", item("minecraft:splash_potion", 0.5F), new ScenePos(1.5F, 1.7F, 0.1F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(32.0F).spawn("charge", item("minecraft:wind_charge", 0.5F), new ScenePos(1.3F, 1.6F, -0.4F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(40.0F).callout("slots", on(NOSE), Cue.Side.RIGHT, 0.0F);
                    s.at(62.0F).move("arrow", 14.0F, Ease.IN, GROUND.add(NOSE));
                    s.at(68.0F).move("potion", 14.0F, Ease.IN, GROUND.add(NOSE));
                    s.at(74.0F).move("charge", 14.0F, Ease.IN, GROUND.add(NOSE));
                    s.at(76.0F).despawn("arrow", 6.0F);
                    s.at(82.0F).despawn("potion", 6.0F);
                    s.at(88.0F).despawn("charge", 6.0F);
                    s.at(96.0F).callout("service", on(ROOF), Cue.Side.LEFT, 0.0F);
                })
                .step("fire", 180.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.6F, 0.4F, 0.0F), OVERVIEW_YAW, 20.0F, FLIGHT_ZOOM, 20.0F);
                    takeOff(s, 8.0F);
                    s.at(40.0F).keysAt("fire", on(ROOF.add(0.0F, 0.35F, 0.0F)), 60.0F, KeyToken.mapping(ACTION_1));
                    for (int i = 0; i < 3; i++) {
                        String shot = "shot" + i;
                        float at = 52.0F + i * 14.0F;
                        s.at(at).spawn(shot, item(arrow, 0.45F), HOVER.add(NOSE), FORWARD_ITEM_YAW, Cue.Appear.INSTANT, 0.0F);
                        s.at(at).turn(shot, FORWARD_ITEM_YAW, FLAT, 0.0F, 1.0F);
                        s.at(at).move(shot, 16.0F, Ease.LINEAR, HOVER.add(3.2F, -0.1F, 0.0F));
                        s.at(at + 16.0F).despawn(shot, 2.0F);
                    }
                    s.at(112.0F).keysAt("exit", on(ROOF.add(0.0F, 0.35F, 0.0F)), 60.0F, KeyToken.use());
                })
                .step("slots", 170.0F, s -> {
                    s.at(10.0F).keysAt("next", on(ROOF.add(0.0F, 0.35F, 0.0F)), 50.0F, KeyToken.mapping(ACTION_2));
                    s.at(66.0F).callout("display", on(ROOF), Cue.Side.RIGHT, 0.0F);
                    s.at(80.0F).callout("rules", on(BELLY), Cue.Side.LEFT, 0.0F);
                    land(s, 110.0F, HOVER);
                })
                .build();
    }

    public static Scene spear() {
        String spear = "minecraft:iron_spear";
        ScenePos under = BELLY.add(0.1F, 0.02F, 0.0F);
        ScenePos start = new ScenePos(-2.4F, 1.7F, 0.7F);
        ScenePos end = new ScenePos(3.0F, 1.7F, 0.7F);
        return SceneBuilder.scene(SPEAR)
                .icon(MobDefense.SPEAR_MOUNT.toString())
                .view(VIEW)
                .camera(OVERVIEW, OVERVIEW_YAW, 24.0F, OVERVIEW_ZOOM)
                .step("mount", 130.0F, s -> {
                    ground(s);
                    s.at(12.0F).spawn(DRONE, drone(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT), GROUND.add(0.0F, 0.25F, 0.0F), NOSE_YAW,
                            Cue.Appear.POP, 10.0F);
                    s.at(16.0F).camera(GROUND.add(0.2F, 0.4F, 0.0F), OVERVIEW_YAW, 14.0F, CLOSE_ZOOM, 22.0F);
                    s.at(24.0F).spawn("spear", item(spear, 0.6F), new ScenePos(0.2F, 2.1F, 0.9F), FORWARD_ITEM_YAW, Cue.Appear.POP, 8.0F);
                    s.at(24.0F).turn("spear", FORWARD_ITEM_YAW, FLAT, 0.0F, 1.0F);
                    s.at(40.0F).move("spear", 20.0F, Ease.IN_OUT, GROUND.add(0.0F, 0.25F, 0.0F).add(under));
                    s.at(66.0F).callout("only", on(BELLY), Cue.Side.RIGHT, 0.0F);
                })
                .step("joust", 180.0F, s -> {
                    s.at(0.0F).pop(2, 1, 0, TARGET, 8.0F);
                    s.at(0.0F).pop(2, 2, 0, TARGET, 8.0F);
                    s.at(0.0F).camera(new ScenePos(0.4F, 1.8F, 0.3F), OVERVIEW_YAW, 18.0F, FLIGHT_ZOOM, 20.0F);
                    s.at(10.0F).arm(DRONE, true);
                    s.at(10.0F).throttle(DRONE, 0.6F, 12.0F);
                    s.at(14.0F).move(DRONE, 26.0F, Ease.IN_OUT, start);
                    s.at(14.0F).move("spear", 26.0F, Ease.IN_OUT, start.add(under));
                    s.at(44.0F).turn(DRONE, NOSE_YAW, -18.0F, 0.0F, 8.0F);
                    s.at(50.0F).move(DRONE, 26.0F, Ease.IN, end);
                    s.at(50.0F).move("spear", 26.0F, Ease.IN, end.add(under));
                    s.at(60.0F).callout("speed", Anchor.at(2.5F, 2.4F, 0.5F), Cue.Side.ABOVE, 0.0F);
                    s.at(80.0F).turn(DRONE, NOSE_YAW, 0.0F, 0.0F, 12.0F);
                    s.at(96.0F).callout("durability", on(BELLY), Cue.Side.LEFT, 0.0F);
                })
                .build();
    }

    public static Scene booster() {
        String rocket = "minecraft:firework_rocket";
        ScenePos roof = ROOF.add(-0.05F, 0.06F, 0.0F);
        ScenePos far = HOVER.add(3.2F, 0.3F, 0.0F);
        return SceneBuilder.scene(BOOSTER)
                .icon(MobDefense.ROCKET_BOOSTER.toString())
                .view(VIEW)
                .camera(OVERVIEW, OVERVIEW_YAW, 24.0F, OVERVIEW_ZOOM)
                .step("load", 120.0F, s -> {
                    ground(s);
                    s.at(12.0F).spawn(DRONE, drone(AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER), GROUND, NOSE_YAW, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).camera(GROUND.add(0.2F, 0.4F, 0.0F), OVERVIEW_YAW, 14.0F, CLOSE_ZOOM, 22.0F);
                    s.at(24.0F).spawn("rocket", item(rocket, 0.5F), new ScenePos(0.0F, 2.2F, 0.6F), BACKWARD_ITEM_YAW, Cue.Appear.POP, 8.0F);
                    s.at(24.0F).turn("rocket", BACKWARD_ITEM_YAW, FLAT, 0.0F, 1.0F);
                    s.at(40.0F).move("rocket", 18.0F, Ease.IN_OUT, GROUND.add(roof));
                    s.at(62.0F).callout("one", on(ROOF), Cue.Side.RIGHT, 0.0F);
                })
                .step("ignite", 180.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.8F, 0.4F, 0.0F), OVERVIEW_YAW, 20.0F, FLIGHT_ZOOM, 20.0F);
                    takeOff(s, 8.0F);
                    s.at(12.0F).move("rocket", 24.0F, Ease.IN_OUT, HOVER.add(roof));
                    s.at(40.0F).keysAt("ignite", on(ROOF.add(0.0F, 0.35F, 0.0F)), 50.0F, KeyToken.mapping(ACTION_3));
                    s.at(56.0F).turn(DRONE, NOSE_YAW, -20.0F, 0.0F, 6.0F);
                    s.at(60.0F).move(DRONE, 25.0F, Ease.IN, far);
                    s.at(60.0F).move("rocket", 25.0F, Ease.IN, far.add(roof));
                    s.at(86.0F).despawn("rocket", 4.0F);
                    s.at(88.0F).turn(DRONE, NOSE_YAW, 0.0F, 0.0F, 10.0F);
                    s.at(92.0F).callout("boost", on(ROOF), Cue.Side.LEFT, 0.0F);
                    s.at(118.0F).callout("stars", on(BELLY), Cue.Side.RIGHT, 0.0F);
                })
                .build();
    }
}
