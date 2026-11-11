package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.core.FireCadence;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CadencePayload(int delayTicks, int intervalTicks) implements CustomPacketPayload {
    public static final Type<CadencePayload> TYPE = new Type<>(MobDefense.id("dispenser_cadence"));
    public static final StreamCodec<ByteBuf, CadencePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CadencePayload::delayTicks,
            ByteBufCodecs.VAR_INT, CadencePayload::intervalTicks,
            CadencePayload::new);

    public CadencePayload {
        delayTicks = FireCadence.clamp(delayTicks);
        intervalTicks = FireCadence.clamp(intervalTicks);
    }

    public static CadencePayload of(FireCadence.Setting setting) {
        return new CadencePayload(setting.delayTicks(), setting.intervalTicks());
    }

    public FireCadence.Setting setting() {
        return new FireCadence.Setting(this.delayTicks, this.intervalTicks);
    }

    @Override
    public Type<CadencePayload> type() {
        return TYPE;
    }
}
