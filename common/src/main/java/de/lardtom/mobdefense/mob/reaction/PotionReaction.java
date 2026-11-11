package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class PotionReaction implements Reaction {
    public static final String KIND = "potion";
    public static final int INTERVAL = 60;
    public static final int FIRST_THROW = 20;
    public static final float SPEED = 0.75F;
    public static final double DRAG = 0.99;
    public static final float SPREAD_FACTOR = 0.5F;
    private static final Map<Mob, Flight> FLIGHTS = new WeakHashMap<>();
    private static long splashes;
    private static long hits;
    private static double lastDistance = Double.NaN;

    private static final class Flight {
        final ThrownSplashPotion potion;
        final Entity drone;
        Vec3 position;

        Flight(ThrownSplashPotion potion, Entity drone) {
            this.potion = potion;
            this.drone = drone;
            this.position = potion.position();
        }
    }

    public record Counts(long splashes, long hits, double lastDistance) {
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.POTION_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.cooldown = FIRST_THROW;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.halt(mob, memory);
        Moves.look(mob, drone);
        ServerLevel level = Support.level(mob);
        if (level == null) {
            return;
        }
        follow(level, mob);
        if (memory.cooldown > 0) {
            memory.cooldown--;
            return;
        }
        if (!Support.sees(mob, drone) || mob instanceof Witch witch && witch.isDrinkingPotion()) {
            memory.cooldown = 5;
            return;
        }
        throwAt(level, mob, drone);
        memory.shots++;
        memory.cooldown = INTERVAL;
    }

    private static void throwAt(ServerLevel level, Mob mob, DroneState drone) {
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
        ThrownSplashPotion potion = new ThrownSplashPotion(level, mob, stack);
        Vec3 direction = Aiming.direction(potion.position(), drone, SPEED * DRAG, potion.getGravity());
        Projectile.spawnProjectileUsingShoot(potion, level, stack, direction.x, direction.y, direction.z, SPEED,
                Aiming.uncertainty(mob) * SPREAD_FACTOR);
        FLIGHTS.put(mob, new Flight(potion, drone.entity()));
        if (!mob.isSilent()) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITCH_THROW, mob.getSoundSource(), 1.0F,
                    0.8F + mob.getRandom().nextFloat() * 0.4F);
        }
    }

    private static void follow(ServerLevel level, Mob mob) {
        Flight flight = FLIGHTS.get(mob);
        if (flight == null) {
            return;
        }
        if (!flight.potion.isRemoved()) {
            flight.position = flight.potion.position();
            return;
        }
        FLIGHTS.remove(mob);
        Entity drone = flight.drone;
        if (drone.isRemoved() || drone.level() != level) {
            return;
        }
        Vec3 from = flight.position;
        Vec3 to = flight.potion.position();
        Vec3 center = drone.getBoundingBox().getCenter();
        double distance = StageTwoWindows.segmentDistance(from.x, from.y, from.z, to.x, to.y, to.z, center.x, center.y, center.z);
        countSplash(distance);
        double damage = StageTwoWindows.potionDamage(distance);
        if (damage > 0.0 && Support.hurt(level, drone, mob.damageSources().indirectMagic(flight.potion, mob), (float) damage)) {
            countHit();
        }
    }

    private static synchronized void countSplash(double distance) {
        splashes++;
        lastDistance = distance;
    }

    private static synchronized void countHit() {
        hits++;
    }

    public static synchronized Counts counts() {
        return new Counts(splashes, hits, lastDistance);
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob.level() instanceof ServerLevel level) {
            follow(level, mob);
        }
        Moves.stop(mob, memory);
    }
}
