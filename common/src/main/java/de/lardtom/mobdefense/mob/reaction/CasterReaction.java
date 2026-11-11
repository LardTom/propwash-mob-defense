package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class CasterReaction implements Reaction {
    public static final String KIND = "caster";
    public static final int FANGS_CAST = 40;
    public static final int FANGS_INTERVAL = 100;
    public static final int SUMMON_CAST = 100;
    public static final int SUMMON_INTERVAL = 340;
    public static final int FANGS_IN_LINE = 16;
    public static final double FANGS_SPACING = 1.25;
    public static final double CLOSE = 3.0;
    public static final int BITE_DELAY = 8;
    public static final double BITE_HALF_WIDTH = 0.45;
    public static final float FANGS_DAMAGE = 6.0F;
    public static final double VEX_COUNT_RANGE = 16.0;
    public static final int VEXES = 3;
    private static final int IDLE = 0;
    private static final int CAST_FANGS = 1;
    private static final int CAST_SUMMON = 2;
    private static final Map<Mob, List<Bite>> BITES = new WeakHashMap<>();

    private record Bite(EvokerFangs fangs, long at) {
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.CASTER_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.step = IDLE;
        memory.cooldown = 20;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.halt(mob, memory);
        Moves.look(mob, drone);
        ServerLevel level = Support.level(mob);
        if (level == null) {
            return;
        }
        bite(level, mob, drone, memory);
        boolean sees = Support.sees(mob, drone);
        switch (memory.step) {
            case CAST_FANGS, CAST_SUMMON -> {
                Vec3 at = mob.getEyePosition();
                level.sendParticles(memory.step == CAST_FANGS ? ParticleTypes.WITCH : ParticleTypes.ENCHANT, at.x, at.y + 0.3, at.z, 2, 0.4, 0.2, 0.4, 0.0);
                if (--memory.ticks > 0) {
                    return;
                }
                if (memory.step == CAST_FANGS) {
                    fangs(level, mob, drone);
                    memory.cooldown = FANGS_INTERVAL - FANGS_CAST;
                } else {
                    summon(level, mob);
                    memory.cooldown = SUMMON_INTERVAL - SUMMON_CAST;
                }
                memory.shots++;
                mob.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
                memory.step = IDLE;
            }
            default -> {
                if (memory.cooldown > 0) {
                    memory.cooldown--;
                    return;
                }
                if (!sees) {
                    memory.cooldown = 5;
                } else if (StageTwoWindows.fangs(drone.height())) {
                    memory.step = CAST_FANGS;
                    memory.ticks = FANGS_CAST;
                    mob.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.0F);
                } else if (mob.getRandom().nextInt(8) + 1 > vexes(level, mob)) {
                    memory.step = CAST_SUMMON;
                    memory.ticks = SUMMON_CAST;
                    mob.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.0F);
                } else {
                    memory.cooldown = 20;
                }
            }
        }
    }

    private static void bite(ServerLevel level, Mob mob, DroneState drone, ReactionMemory memory) {
        List<Bite> bites = BITES.get(mob);
        if (bites == null || bites.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean hit = false;
        Iterator<Bite> pending = bites.iterator();
        while (pending.hasNext()) {
            Bite bite = pending.next();
            if (bite.at() > now) {
                continue;
            }
            pending.remove();
            if (!hit && bitten(bite.fangs(), drone)) {
                hit = Support.hurt(level, drone.entity(), mob.damageSources().indirectMagic(bite.fangs(), mob), FANGS_DAMAGE);
            }
        }
        if (hit) {
            memory.special++;
            bites.clear();
        }
    }

    private static boolean bitten(EvokerFangs fangs, DroneState drone) {
        AABB volume = new AABB(fangs.getX() - BITE_HALF_WIDTH, fangs.getY(), fangs.getZ() - BITE_HALF_WIDTH, fangs.getX() + BITE_HALF_WIDTH,
                fangs.getY() + StageTwoWindows.CASTER_FANGS_HEIGHT, fangs.getZ() + BITE_HALF_WIDTH);
        return volume.intersects(drone.box());
    }

    private static void fangs(ServerLevel level, Mob mob, DroneState drone) {
        Vec3 target = drone.ground();
        double minY = Math.min(target.y, mob.getY());
        double maxY = Math.max(target.y, mob.getY()) + 1.0;
        float angle = (float) Mth.atan2(target.z - mob.getZ(), target.x - mob.getX());
        List<Bite> bites = BITES.computeIfAbsent(mob, key -> new ArrayList<>());
        bites.clear();
        long now = level.getGameTime();
        if (Support.flat(mob, target) < CLOSE) {
            for (int i = 0; i < 5; i++) {
                float a = angle + i * (float) Math.PI * 0.4F;
                place(level, mob, mob.getX() + Mth.cos(a) * 1.5, mob.getZ() + Mth.sin(a) * 1.5, minY, maxY, a, 0, now, bites);
            }
            for (int i = 0; i < 8; i++) {
                float a = angle + i * (float) Math.PI * 2.0F / 8.0F + (float) (Math.PI * 2.0 / 5.0);
                place(level, mob, mob.getX() + Mth.cos(a) * 2.5, mob.getZ() + Mth.sin(a) * 2.5, minY, maxY, a, 3, now, bites);
            }
            return;
        }
        for (int i = 0; i < FANGS_IN_LINE; i++) {
            double reach = FANGS_SPACING * (i + 1);
            place(level, mob, mob.getX() + Mth.cos(angle) * reach, mob.getZ() + Mth.sin(angle) * reach, minY, maxY, angle, i, now, bites);
        }
    }

    private static void place(ServerLevel level, Mob mob, double x, double z, double minY, double maxY, float angle, int delay, long now, List<Bite> bites) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double top = 0.0;
        boolean found = false;
        do {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        top = shape.max(Direction.Axis.Y);
                    }
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);
        if (!found) {
            return;
        }
        EvokerFangs fangs = new EvokerFangs(level, x, pos.getY() + top, z, angle, delay, mob);
        if (level.addFreshEntity(fangs)) {
            level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, pos.getY() + top, z), GameEvent.Context.of(mob));
            bites.add(new Bite(fangs, now + delay + BITE_DELAY));
        }
    }

    private static int vexes(ServerLevel level, Mob mob) {
        return level.getEntitiesOfClass(Vex.class, mob.getBoundingBox().inflate(VEX_COUNT_RANGE), Vex::isAlive).size();
    }

    private static void summon(ServerLevel level, Mob mob) {
        PlayerTeam team = mob.getTeam();
        for (int i = 0; i < VEXES; i++) {
            BlockPos pos = mob.blockPosition().offset(-2 + mob.getRandom().nextInt(5), 1, -2 + mob.getRandom().nextInt(5));
            Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (vex == null) {
                continue;
            }
            vex.snapTo(pos, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            vex.setOwner(mob);
            vex.setBoundOrigin(pos);
            vex.setLimitedLife(20 * (30 + mob.getRandom().nextInt(90)));
            if (team != null) {
                level.getScoreboard().addPlayerToTeam(vex.getScoreboardName(), team);
            }
            level.addFreshEntityWithPassengers(vex);
            level.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(mob));
        }
    }

    public static int pendingBites(Mob mob) {
        List<Bite> bites = BITES.get(mob);
        return bites == null ? 0 : bites.size();
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        BITES.remove(mob);
        memory.step = IDLE;
        Moves.stop(mob, memory);
    }
}
