package com.theman.toolbelt.menu;

import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.belt.BeltSlot;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Optional;

/**
 * 腰带容器。槽位编号：
 * <pre>
 *   0 ~ 7   腰带工具槽（只启用前 n 个，n 由服务端配置决定，其余隐藏）
 *   8       腰带弹药槽
 *   9 ~ 35  玩家背包主区（27 格）
 *   36 ~ 44 玩家快捷栏（9 格）
 * </pre>
 * 服务端和客户端都直接用玩家自己的腰带 Attachment，原版容器同步机制会把格子内容同步过去。
 */
public class ToolBeltMenu extends AbstractContainerMenu {

    public static final int BELT_START = 0;
    public static final int BELT_END = BeltInventory.SIZE;        // 9（不含）
    public static final int INV_START = BELT_END;                  // 9
    public static final int HOTBAR_START = INV_START + 27;         // 36
    public static final int INV_END = HOTBAR_START + 9;            // 45（不含）

    /** 界面布局（Screen 也用这些坐标画格子底框） */
    public static final int IMAGE_WIDTH = 176;
    public static final int TOOL_ROW_Y = 24;
    public static final int AMMO_X = 80;
    public static final int AMMO_Y = 50;
    public static final int INV_X = 8;
    public static final int INV_Y = 84;
    public static final int HOTBAR_Y = 142;

    /** 第 index 个工具槽的 x：按当前槽数整体居中 */
    public static int toolX(int index) {
        int n = BeltInventory.toolSlots();
        return (IMAGE_WIDTH - n * 18) / 2 + 1 + Math.min(index, n - 1) * 18;
    }

    private final Player player;

    public ToolBeltMenu(int containerId, Inventory playerInv) {
        super(ModRegistries.TOOL_BELT_MENU.get(), containerId);
        this.player = playerInv.player;
        BeltInventory belt = ModRegistries.belt(player);

        for (int i = 0; i < BeltInventory.MAX_TOOL_SLOTS; i++) {
            addSlot(new BeltMenuSlot(belt, i, toolX(i), TOOL_ROW_Y));
        }
        addSlot(new BeltMenuSlot(belt, BeltInventory.AMMO_SLOT, AMMO_X, AMMO_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /**
     * Shift 点击：
     * - 腰带里的 → 回背包；
     * - 背包里的箭 → 弹药槽；工具 / 食物 → 工具槽；
     * - 放不进腰带的 → 在背包主区和快捷栏之间互相挪（原版习惯）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < BELT_END) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (BeltInventory.isAmmo(stack)) {
                moved = moveItemStackTo(stack, BeltInventory.AMMO_SLOT, BeltInventory.AMMO_SLOT + 1, false);
            }
            if (!moved && BeltInventory.isTool(stack)) {
                moved = moveItemStackTo(stack, 0, BeltInventory.toolSlots(), false);
            }
            if (!moved) {
                if (index < HOTBAR_START) {
                    if (!moveItemStackTo(stack, HOTBAR_START, INV_END, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (BeltSlot.handleOverstackedSwap(this, slotId, button, clickType, player)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** 腰带格：超出配置数量的工具槽不启用、不显示 */
    private static class BeltMenuSlot extends SlotItemHandler {
        private final int beltIndex;

        BeltMenuSlot(BeltInventory belt, int index, int x, int y) {
            super(belt, index, x, y);
            this.beltIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            BeltInventory belt = (BeltInventory) getItemHandler();
            if (this.beltIndex == belt.activeBeltSlot) return false;
            if (!super.mayPlace(stack)) return false;
            ItemStack existing = getItem();
            if (!existing.isEmpty() && existing.getCount() > existing.getMaxStackSize()
                    && !ItemStack.isSameItemSameComponents(existing, stack)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean mayPickup(Player playerIn) {
            BeltInventory belt = (BeltInventory) getItemHandler();
            if (this.beltIndex == belt.activeBeltSlot) return false;
            return super.mayPickup(playerIn);
        }

        @Override
        public int getMaxStackSize() {
            return getItemHandler().getSlotLimit(this.beltIndex);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return ((BeltInventory) getItemHandler()).getStackLimit(this.beltIndex, stack);
        }

        @Override
        public Optional<ItemStack> tryRemove(int count, int decrement, Player playerIn) {
            ItemStack current = getItem();
            if (!current.isEmpty() && current.getCount() > current.getMaxStackSize() && count < current.getCount()) {
                count = (current.getMaxStackSize() + 1) / 2;
            }
            return super.tryRemove(count, decrement, playerIn);
        }

        @Override
        public void set(ItemStack stack) {
            BeltInventory.clearActiveSlot(stack);
            super.set(stack);
        }

        @Override
        public void initialize(ItemStack stack) {
            BeltInventory.clearActiveSlot(stack);
            super.initialize(stack);
        }

        @Override
        public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
            BeltInventory belt = (BeltInventory) getItemHandler();
            if (this.beltIndex == belt.activeBeltSlot) {
                return null;
            }
            var ghost = BeltSlot.ghostFor(beltIndex);
            return ghost != null ? com.mojang.datafixers.util.Pair.of(InventoryMenu.BLOCK_ATLAS, ghost) : null;
        }

        @Override
        public boolean isActive() {
            if (!BeltSlot.isEnabled(beltIndex)) return false;
            if (beltIndex != BeltInventory.AMMO_SLOT) {
                int targetX = toolX(beltIndex);
                if (this.x != targetX) {
                    ((com.theman.toolbelt.mixin.SlotAccessor) this).toolbelt$setX(targetX);
                }
            }
            return true;
        }
    }
}
