package com.theman.toolbelt.network;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.menu.ToolBeltMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：玩家点了背包里的腰带按钮（或按了快捷键），请求打开腰带界面 */
public record OpenBeltPayload() implements CustomPacketPayload {

    public static final OpenBeltPayload INSTANCE = new OpenBeltPayload();
    public static final Type<OpenBeltPayload> TYPE = new Type<>(ToolBeltMod.id("open_belt"));
    public static final StreamCodec<ByteBuf, OpenBeltPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBeltPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator()) {
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new ToolBeltMenu(id, inv),
                        Component.translatable("container.tool_wheel.belt")));
            }
        });
    }
}
