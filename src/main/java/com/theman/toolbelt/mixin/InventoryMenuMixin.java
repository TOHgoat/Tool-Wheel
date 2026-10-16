package com.theman.toolbelt.mixin;

import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.belt.BeltSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    @Unique
    private int toolbelt$beltSlotStart = -1;

    @Unique
    private final Map<ContainerListener, ContainerListener> toolbelt$wrappedListeners = new IdentityHashMap<>();

    protected InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void toolbelt$addBeltSlots(Inventory playerInventory, boolean active, Player player, CallbackInfo ci) {
        this.toolbelt$beltSlotStart = this.slots.size();
        // 始终挂满 8 个工具槽，未启用的由 BeltSlot.isActive 隐藏，保证两端槽位编号一致
        for (int i = 0; i < BeltInventory.MAX_TOOL_SLOTS; i++) {
            this.addSlot(new BeltSlot(player, i, BeltSlot.REL_X, BeltSlot.toolY(i)));
        }
        this.addSlot(new BeltSlot(player, BeltInventory.AMMO_SLOT, BeltSlot.REL_X, BeltSlot.ammoY()));
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void toolbelt$quickMoveAmmoToBelt(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (this.toolbelt$beltSlotStart < 0) return;
        // 仅当玩家在背包主区或快捷栏（9 ~ 44）Shift 点击弹药时，优先合并进腰带弹药槽（上限 256）
        if (index >= InventoryMenu.INV_SLOT_START && index < InventoryMenu.USE_ROW_SLOT_END) {
            int ammoMenuSlot = this.toolbelt$beltSlotStart + BeltInventory.AMMO_SLOT;
            if (ammoMenuSlot >= this.slots.size()) return;
            if (!BeltSlot.isEnabled(BeltInventory.AMMO_SLOT)) return;

            Slot slot = this.slots.get(index);
            if (!slot.hasItem()) return;
            ItemStack stack = slot.getItem();
            if (!BeltInventory.isAmmo(stack)) return;

            ItemStack original = stack.copy();
            if (this.moveItemStackTo(stack, ammoMenuSlot, ammoMenuSlot + 1, false)) {
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY, original);
                } else {
                    slot.setChanged();
                }
                slot.onTake(player, stack);
                cir.setReturnValue(original);
            }
        }
    }

    @Override
    public void addSlotListener(ContainerListener listener) {
        ContainerListener wrapped = this.toolbelt$wrappedListeners.computeIfAbsent(listener, raw -> new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slotInd, ItemStack stack) {
                // 过滤掉附加在 InventoryMenu 末尾的腰带槽位（>= 46），
                // 防止创造模式 CreativeInventoryListener 对 256 支箭发送 ServerboundSetCreativeModeSlotPacket 导致 Codec 超限掉线
                if (toolbelt$beltSlotStart >= 0 && slotInd >= toolbelt$beltSlotStart) {
                    return;
                }
                raw.slotChanged(menu, slotInd, stack);
            }

            @Override
            public void dataChanged(AbstractContainerMenu menu, int dataIndex, int value) {
                raw.dataChanged(menu, dataIndex, value);
            }
        });
        super.addSlotListener(wrapped);
    }

    @Override
    public void removeSlotListener(ContainerListener listener) {
        ContainerListener wrapped = this.toolbelt$wrappedListeners.remove(listener);
        super.removeSlotListener(wrapped != null ? wrapped : listener);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (BeltSlot.handleOverstackedSwap(this, slotId, button, clickType, player)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
}
