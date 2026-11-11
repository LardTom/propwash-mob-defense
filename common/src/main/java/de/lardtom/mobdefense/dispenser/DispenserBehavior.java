package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.mobdefense.dispenser.core.FireCadence;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.mobdefense.payload.Payloads;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LevelEvent;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class DispenserBehavior implements AccessoryBehavior {
    public static final int FIRE = 1;
    public static final int NEXT_SLOT = 2;
    public static final String FIRE_KEY = "mobdefense.action.dispenser.fire";
    public static final String NEXT_SLOT_KEY = "mobdefense.action.dispenser.next_slot";
    public static final DispenserBehavior INSTANCE = new DispenserBehavior();

    private DispenserBehavior() {
    }

    @Override
    public int slots() {
        return SlotCycle.SLOTS;
    }

    @Override
    public boolean accepts(int slot, ItemStack stack) {
        return slot >= 0 && slot < SlotCycle.SLOTS && !stack.isEmpty() && !PropwashApi.builds().isDrone(stack);
    }

    @Override
    public int maxStackSize(int slot, ItemStack stack) {
        return DispenserAmmo.limit(stack);
    }

    @Override
    public OptionalDouble payloadGrams(List<ItemStack> contents) {
        return OptionalDouble.of(DispenserAmmo.grams(contents));
    }

    @Override
    public Optional<String> actionNameKey(int number) {
        return switch (number) {
            case FIRE -> Optional.of(FIRE_KEY);
            case NEXT_SLOT -> Optional.of(NEXT_SLOT_KEY);
            default -> Optional.empty();
        };
    }

    @Override
    public void onAction(AccessoryAction action) {
        if (action.number() == FIRE) {
            if (action.pressed()) {
                press(action);
            } else {
                DispenserHolds.release(action.drone());
            }
        } else if (action.number() == NEXT_SLOT && action.pressed()) {
            next(action);
        }
    }

    @Override
    public void tick(AccessoryContext context) {
        Entity drone = context.drone();
        if (!context.actionHeld(FIRE)) {
            DispenserHolds.release(drone);
            return;
        }
        long now = context.level().getGameTime();
        FireCadence.Setting setting = DispenserCadences.of(context.pilot());
        FireCadence.Hold hold = DispenserHolds.get(drone).orElseGet(() -> FireCadence.held(now));
        if (FireCadence.due(now, hold) && fire(context, setting)) {
            hold = FireCadence.after(now, hold, setting, DispenserRules.of(context.level()).cooldownTicks());
        }
        DispenserHolds.put(drone, hold);
    }

    private static void press(AccessoryAction action) {
        Entity drone = action.drone();
        long now = action.level().getGameTime();
        FireCadence.Setting setting = DispenserCadences.of(Optional.ofNullable(action.player()).or(action::pilot));
        boolean attempted = fire(action, setting);
        long readyAt = DispenserState.selection(drone).cooldownUntil();
        DispenserHolds.put(drone, FireCadence.press(now, attempted, readyAt, setting, DispenserRules.of(action.level()).cooldownTicks()));
    }

    static AmmoTable.Kind[] kinds(AccessoryInventory inventory) {
        AmmoTable.Kind[] kinds = new AmmoTable.Kind[SlotCycle.SLOTS];
        for (int i = 0; i < Math.min(SlotCycle.SLOTS, inventory.size()); i++) {
            ItemStack stack = inventory.get(i);
            kinds[i] = stack.isEmpty() ? null : DispenserAmmo.kind(stack);
        }
        return kinds;
    }

    static boolean[] filled(AmmoTable.Kind[] kinds) {
        boolean[] filled = new boolean[SlotCycle.SLOTS];
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            filled[i] = kinds[i] != null;
        }
        return filled;
    }

    private static boolean fire(AccessoryContext context, FireCadence.Setting setting) {
        ServerLevel level = context.level();
        Entity drone = context.drone();
        long now = level.getGameTime();
        DispenserState.Selection selection = DispenserState.selection(drone);
        AmmoTable.Kind[] kinds = kinds(context.inventory());
        FireGate.Rules rules = DispenserRules.of(level);
        FireGate.Shot shot = FireGate.decide(now, selection.cooldownUntil(), selection.selected(), kinds, rules);
        if (shot.outcome() == FireGate.Outcome.COOLDOWN) {
            return false;
        }
        String status = "";
        FireGate.Notice notice;
        if (shot.fires()) {
            DispenserRecoil.Before before = DispenserRecoil.before(context);
            DispenseResult result = context.dispense(shot.slot(), DispenseOptions.defaults().consume(shot.consume()).allowFluids(shot.allowFluids())
                    .applyRecoil(false));
            status = result.status().name();
            notice = FireGate.afterDispense(status, kinds[shot.slot()]);
            if (result.success()) {
                DispenserRecoil.apply(context, before, result.recoilNs());
                result.entity().ifPresent(entity -> Payloads.launched(entity, drone));
            }
            DispenserLog.record(drone, shot, result, notice, context.mount(), context.velocity());
        } else {
            notice = shot.notice();
            DispenserLog.record(drone, shot, null, notice, context.mount(), context.velocity());
        }
        DispenserState.cooldownUntil(drone, FireCadence.nextReady(now, setting, rules.cooldownTicks()));
        FireGate.Notice published = notice;
        context.pilot().ifPresent(pilot -> DispenserNotices.publish(pilot, drone, published));
        if (FireGate.failSound(shot.outcome(), status)) {
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, BlockPos.containing(context.mount().front()), 0);
        }
        return true;
    }

    private static void next(AccessoryContext context) {
        Entity drone = context.drone();
        boolean[] filled = filled(kinds(context.inventory()));
        int firing = SlotCycle.firing(DispenserState.selection(drone).selected(), filled);
        if (firing < 0) {
            return;
        }
        DispenserState.select(drone, SlotCycle.next(firing, filled));
    }
}
