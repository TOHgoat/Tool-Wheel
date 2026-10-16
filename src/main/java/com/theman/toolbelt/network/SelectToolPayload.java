package com.theman.toolbelt.network;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.belt.BeltSwapLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：轮盘选中了腰带第 beltIndex 格。服务端只信任这个编号，其余全部自己校验 */
public record SelectToolPayload(int beltIndex) implements CustomPacketPayload {

    public static final Type<SelectToolPayload> TYPE = new Type<>(ToolBeltMod.id("select_tool"));
    public static final StreamCodec<ByteBuf, SelectToolPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SelectToolPayload::new, SelectToolPayload::beltIndex);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectToolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                if (payload.beltIndex() == -1) {
                    BeltSwapLogic.tryHolster(sp);
                } else {
                    BeltSwapLogic.trySwap(sp, payload.beltIndex());
                }
            }
        });
    }
}
