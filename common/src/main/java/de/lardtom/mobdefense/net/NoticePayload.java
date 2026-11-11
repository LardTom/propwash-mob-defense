package de.lardtom.mobdefense.net;

import de.lardtom.mobdefense.MobDefense;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NoticePayload(int droneId, Notice notice, int ticks) implements CustomPacketPayload {
    public static final Type<NoticePayload> TYPE = new Type<>(MobDefense.id("notice"));
    private static final StreamCodec<ByteBuf, Notice> NOTICE = ByteBufCodecs.idMapper(Notice::byIndex, Notice::ordinal);
    public static final StreamCodec<ByteBuf, NoticePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NoticePayload::droneId,
            NOTICE, NoticePayload::notice,
            ByteBufCodecs.VAR_INT, NoticePayload::ticks,
            NoticePayload::new);

    public NoticePayload {
        ticks = Math.max(1, Math.min(Notice.MAX_TICKS, ticks));
    }

    @Override
    public Type<NoticePayload> type() {
        return TYPE;
    }
}
