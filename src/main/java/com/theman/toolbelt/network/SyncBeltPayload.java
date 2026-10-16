package com.theman.toolbelt.network;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S2C：把腰带 6 格内容及当前激活手持槽位同步给客户端。
 */
public record SyncBeltPayload(List<ItemStack> stacks, int activeBeltSlot) implements CustomPacketPayload {

    public static final Type<SyncBeltPayload> TYPE = new Type<>(ToolBeltMod.id("sync_belt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBeltPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, SyncBeltPayload::stacks,
                    ByteBufCodecs.VAR_INT, SyncBeltPayload::activeBeltSlot,
                    SyncBeltPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBeltPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BeltInventory belt = ModRegistries.belt(context.player());
            List<ItemStack> list = payload.stacks();
            for (int i = 0; i < BeltInventory.SIZE && i < list.size(); i++) {
                belt.setStackInSlot(i, list.get(i));
            }
            belt.activeBeltSlot = payload.activeBeltSlot();
        });
    }
}
