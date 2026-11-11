package de.lardtom.mobdefense.spear;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.core.ContactMemory;
import de.lardtom.mobdefense.spear.core.SpearDamage;
import de.lardtom.mobdefense.spear.core.SpearPose;
import de.lardtom.mobdefense.spear.core.SpearRam;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.EntityHit;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class SpearStrikes {
    public static final double MAX_STEP = 8.0;
    public static final float DEFAULT_MARGIN = 0.125F;
    public static final float KNOCKBACK = 0.4F;
    public static final int MAX_GAP_TICKS = 2;
    public static final Identifier LANCE_FLIGHT = MobDefense.id("lance_flight");

    private static final Map<Entity, Track> TRACKS = new WeakHashMap<>();

    private SpearStrikes() {
    }

    public enum Skip {
        NONE,
        NOT_ALIVE,
        SELF,
        PLAYER_RULE,
        NO_ATTACKER,
        PVP,
        IMMUNE
    }

    public record Hit(LivingEntity target, double attackerSpeed, double relativeSpeed, SpearDamage.Outcome outcome, float dealt, boolean hurt,
                      boolean broke, Skip skip) {
    }

    private static final class Track {
        private @Nullable Vec3 tip;
        private @Nullable ResourceKey<Level> dimension;
        private long tick = Long.MIN_VALUE;
        private final ContactMemory contacts = new ContactMemory();
    }

    public static void tick(AccessoryContext context) {
        Entity drone = context.drone();
        AccessoryInventory inventory = context.inventory();
        ItemStack spear = inventory.size() > Spears.SLOT ? inventory.get(Spears.SLOT) : ItemStack.EMPTY;
        KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
        if (kinetic == null || !Spears.accepts(spear)) {
            forget(drone);
            return;
        }
        ServerLevel level = context.level();
        MountPose mount = context.mount();
        Vec3 nose = nose(drone, mount);
        Vec3 tip = tip(mount);
        Vec3 velocity = context.velocity();
        long now = level.getGameTime();
        Track track = track(drone);
        Vec3 from = track.tip != null && level.dimension().equals(track.dimension) && now - track.tick <= MAX_GAP_TICKS && now > track.tick
                ? track.tip : tip.subtract(velocity.scale(0.05));
        track.tip = tip;
        track.dimension = level.dimension();
        track.tick = now;
        if (from.distanceToSqr(tip) > MAX_STEP * MAX_STEP) {
            return;
        }
        ServerPlayer attacker = context.pilot().orElse(null);
        List<Hit> hits = sweep(level, drone, attacker, spear, kinetic, from, tip, nose, velocity, track.contacts, now);
        if (hits.stream().anyMatch(hit -> hit.outcome().any() && hit.skip() == Skip.NONE)) {
            inventory.set(Spears.SLOT, spear);
        }
    }

    public static Optional<Hit> onHit(EntityHit hit) {
        Entity drone = hit.drone();
        if (!(drone.level() instanceof ServerLevel level)
                || !PropwashApi.accessories().accessoryAt(drone, AccessorySlot.BOTTOM).map(MobDefense.SPEAR_MOUNT::equals).orElse(false)) {
            return Optional.empty();
        }
        AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElse(null);
        ItemStack spear = inventory != null && inventory.size() > Spears.SLOT ? inventory.get(Spears.SLOT) : ItemStack.EMPTY;
        KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (kinetic == null || !Spears.accepts(spear) || view.isEmpty() || view.get().forward().lengthSqr() < 1.0E-12) {
            return Optional.empty();
        }
        LivingEntity target = hit.target();
        Vec3 center = view.get().center();
        Vec3 nose = view.get().forward();
        AABB box = target.getBoundingBox();
        if (!SpearRam.ahead(center.x, center.y, center.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, nose.x, nose.y, nose.z)) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        ContactMemory contacts = track(drone).contacts;
        ServerPlayer attacker = hit.pilot();
        if (contacts.recent(target.getId(), now, kinetic.contactCooldownTicks()) || skip(level, drone, attacker, target) != Skip.NONE) {
            return Optional.empty();
        }
        contacts.remember(target.getId(), now);
        Vec3 velocity = hit.relativeVelocity().add(target.getKnownMovement().scale(20.0));
        Hit result = stab(level, drone, attacker, spear, kinetic, target, nose, velocity);
        if (result.outcome().any()) {
            inventory.set(Spears.SLOT, spear);
        }
        return Optional.of(result);
    }

    public static Vec3 nose(Entity drone, MountPose mount) {
        return PropwashApi.drones().view(drone).map(DroneView::forward).filter(forward -> forward.lengthSqr() > 1.0E-12).orElse(mount.forward());
    }

    public static Vec3 tip(MountPose mount) {
        return mount.at(SpearPose.AXIS_X_MM / 1000.0, SpearPose.tipUpMetres(), SpearPose.tipForwardMetres());
    }

    public static synchronized void forget(Entity drone) {
        TRACKS.remove(drone);
    }

    public static synchronized Optional<Vec3> lastTip(Entity drone) {
        Track track = TRACKS.get(drone);
        return track == null ? Optional.empty() : Optional.ofNullable(track.tip);
    }

    private static synchronized Track track(Entity drone) {
        return TRACKS.computeIfAbsent(drone, key -> new Track());
    }

    public static List<Hit> sweep(ServerLevel level, Entity drone, @Nullable ServerPlayer attacker, ItemStack spear, KineticWeapon kinetic, Vec3 from,
                                  Vec3 to, Vec3 nose, Vec3 velocity, ContactMemory contacts, long now) {
        double margin = margin(spear);
        List<Candidate> candidates = new ArrayList<>();
        for (Entity entity : level.getEntities(drone, new AABB(from, to).inflate(margin), entity -> !entity.isSpectator())) {
            Entity resolved = entity instanceof EnderDragonPart part ? part.parentMob : entity;
            if (!(resolved instanceof LivingEntity living) || resolved == drone || candidates.stream().anyMatch(c -> c.target == living)) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(margin);
            Optional<Vec3> clip = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            clip.ifPresent(point -> candidates.add(new Candidate(living, point.distanceToSqr(from))));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        List<Hit> hits = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (spear.isEmpty()) {
                break;
            }
            LivingEntity target = candidate.target;
            if (contacts.recent(target.getId(), now, kinetic.contactCooldownTicks())) {
                continue;
            }
            Skip skip = skip(level, drone, attacker, target);
            if (skip != Skip.NONE) {
                continue;
            }
            contacts.remember(target.getId(), now);
            hits.add(stab(level, drone, attacker, spear, kinetic, target, nose, velocity));
        }
        return hits;
    }

    public static Skip skip(ServerLevel level, Entity drone, @Nullable ServerPlayer attacker, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            return Skip.NOT_ALIVE;
        }
        if (target == attacker || target.isPassengerOfSameVehicle(drone) || drone.hasPassenger(target)) {
            return Skip.SELF;
        }
        if (target.isInvulnerableToPiercingWeapon() || !target.canBeHitByProjectile()) {
            return Skip.IMMUNE;
        }
        if (target instanceof Player player) {
            if (!MobDefenseRules.flag(level, RuleDefaults.PAYLOADS_HIT_PLAYERS)) {
                return Skip.PLAYER_RULE;
            }
            if (attacker == null) {
                return Skip.NO_ATTACKER;
            }
            if (!attacker.canHarmPlayer(player)) {
                return Skip.PVP;
            }
        }
        return Skip.NONE;
    }

    public static Hit stab(ServerLevel level, Entity drone, @Nullable ServerPlayer attacker, ItemStack spear, LivingEntity target, Vec3 nose,
                           Vec3 velocity) {
        KineticWeapon kinetic = spear.get(DataComponents.KINETIC_WEAPON);
        if (kinetic == null) {
            return new Hit(target, 0.0, 0.0, SpearDamage.Outcome.NONE, 0.0F, false, false, Skip.IMMUNE);
        }
        Skip skip = skip(level, drone, attacker, target);
        if (skip != Skip.NONE) {
            return new Hit(target, 0.0, 0.0, SpearDamage.Outcome.NONE, 0.0F, false, false, skip);
        }
        return stab(level, drone, attacker, spear, kinetic, target, nose, velocity);
    }

    private static Hit stab(ServerLevel level, Entity drone, @Nullable ServerPlayer attacker, ItemStack spear, KineticWeapon kinetic, LivingEntity target,
                            Vec3 nose, Vec3 velocity) {
        Vec3 axis = nose.lengthSqr() > 1.0E-12 ? nose.normalize() : nose;
        double attackerSpeed = axis.dot(velocity);
        double relativeSpeed = Math.max(0.0, attackerSpeed - axis.dot(KineticWeapon.getMotion(target)));
        int percent = MobDefenseRules.number(level, RuleDefaults.SPEAR_DAMAGE_PERCENT);
        SpearDamage.Outcome outcome = SpearDamage.evaluate(Spears.stats(kinetic), attackerSpeed, relativeSpeed, percent);
        if (!outcome.any()) {
            return new Hit(target, attackerSpeed, relativeSpeed, outcome, 0.0F, false, false, Skip.NONE);
        }
        DamageSource source = source(level, spear, drone, attacker);
        float dealt = outcome.damage() && outcome.amount() > 0.0F ? EnchantmentHelper.modifyDamage(level, spear, target, source, outcome.amount()) : 0.0F;
        float healthBefore = target.getHealth();
        boolean hurt = dealt > 0.0F && target.hurtServer(level, source, dealt);
        float healthLost = Math.max(0.0F, healthBefore - target.getHealth());
        if (hurt && attacker != null && target instanceof Mob && target.isDeadOrDying()) {
            award(attacker);
        }
        boolean affected = hurt;
        if (outcome.knockback()) {
            affected = true;
            target.knockback(KNOCKBACK, -axis.x, -axis.z, source, dealt, false);
            float extra = EnchantmentHelper.modifyKnockback(level, spear, target, source, 0.0F);
            if (extra > 0.0F) {
                target.knockback(extra, -axis.x, -axis.z, source, dealt, true);
            }
        }
        if (outcome.dismount() && target.isPassenger() && !target.is(EntityTypeTags.CANNOT_BE_DISMOUNTED_BY_ITEM_USAGE)) {
            affected = true;
            target.stopRiding();
        }
        Weapon weapon = spear.get(DataComponents.WEAPON);
        int wear = weapon == null ? 1 : weapon.itemDamagePerAttack();
        if (wear > 0) {
            spear.hurtAndBreak(wear, level, attacker, broken -> broken(level, drone, broken));
        }
        boolean broke = spear.isEmpty();
        if (hurt) {
            EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(level, target, stabbing(source), spear, broken -> broken(level, drone, broken));
        }
        if (affected) {
            SpearFeedback.hit(level, drone, attacker, target, kinetic, healthLost);
        }
        return new Hit(target, attackerSpeed, relativeSpeed, outcome, hurt ? dealt : 0.0F, hurt, broke, Skip.NONE);
    }

    public static DamageSource source(ServerLevel level, ItemStack spear, Entity drone, @Nullable ServerPlayer attacker) {
        Holder<DamageType> type = spear.get(DataComponents.DAMAGE_TYPE);
        if (type == null) {
            type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SPEAR);
        }
        return new DamageSource(type, drone, attacker);
    }

    private static void award(ServerPlayer player) {
        try {
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(LANCE_FLIGHT);
            if (holder != null) {
                player.getAdvancements().award(holder, "done");
            }
        } catch (RuntimeException e) {
            MobDefense.LOG.debug("{}: Fortschritt {} nicht vergeben", MobDefense.MOD_NAME, LANCE_FLIGHT, e);
        }
    }

    private static DamageSource stabbing(DamageSource source) {
        return new DamageSource(source.typeHolder(), source.getEntity());
    }

    private static double margin(ItemStack spear) {
        AttackRange range = spear.get(DataComponents.ATTACK_RANGE);
        return range == null ? DEFAULT_MARGIN : Math.max(0.0F, range.hitboxMargin());
    }

    private static void broken(ServerLevel level, Entity drone, ItemStack broken) {
        Holder<SoundEvent> sound = broken.get(DataComponents.BREAK_SOUND);
        if (sound != null) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), sound, drone.getSoundSource(), 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        }
        if (!broken.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, broken.getItem()), drone.getX(), drone.getY(), drone.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private record Candidate(LivingEntity target, double distance) {
    }
}
