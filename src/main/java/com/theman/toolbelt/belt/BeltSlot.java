package com.theman.toolbelt.belt;

import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * 挂在原版 InventoryMenu 上的腰带槽位。
 * 菜单里始终挂满 8 个工具槽 + 1 个弹药槽（保证客户端 / 服务端槽位编号一致），
 * 超出配置数量的工具槽不启用、不显示。
 * 只在生存背包界面且抽屉展开时显示，创造背包和其他容器中不显示；
 * 每次访问都从玩家身上取最新的腰带数据。
 */
public class BeltSlot extends SlotItemHandler {

    /** 抽屉内槽位相对背包 leftPos / topPos 的坐标 */
    public static final int REL_X = 176 + 5;
    public static final int REL_TOOL_Y = 12;
    private static final int AMMO_GAP = 4;   // 工具槽与弹药槽之间额外留出的分隔空间

    /** 工具槽竖向间距：6 格以内留 2px 缝隙，7 ~ 8 格改为紧贴（18）以减少抽屉超出背包的长度 */
    public static int spacing() {
        return BeltInventory.toolSlots() <= 6 ? 20 : 18;
    }

    public static int toolY(int index) {
        return REL_TOOL_Y + Math.min(index, BeltInventory.toolSlots() - 1) * spacing();
    }

    public static int ammoY() {
        return REL_TOOL_Y + BeltInventory.toolSlots() * spacing() + AMMO_GAP;
    }

    /**
     * 前 n-1 个工具槽的线框虚影贴图：依次为镐 / 斧 / 剑 / 锹 / 锄 / 弓 / 钓竿。
     * 当前启用的最后一格工具槽（toolSlots() - 1）固定为苹果虚影，紧挨下方箭矢弹药槽，风格统一。
     */
    private static final ResourceLocation[] TOOL_GHOSTS = {
            ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe"),
            ResourceLocation.withDefaultNamespace("item/empty_slot_axe"),
            ResourceLocation.withDefaultNamespace("item/empty_slot_sword"),
            ResourceLocation.withDefaultNamespace("item/empty_slot_shovel"),
            ResourceLocation.withDefaultNamespace("item/empty_slot_hoe"),
            com.theman.toolbelt.ToolBeltMod.id("item/empty_slot_bow"),
            com.theman.toolbelt.ToolBeltMod.id("item/empty_slot_fishing_rod"),
    };

    private static final ItemStack GHOST_APPLE = new ItemStack(Items.APPLE);
    private static final ItemStack GHOST_ARROW = new ItemStack(Items.ARROW);

    /** 是否为工具栏最后一格（苹果虚影槽） */
    public static boolean isAppleGhostSlot(int index) {
        return index == BeltInventory.toolSlots() - 1;
    }

    /** 空槽虚影贴图；返回 null 表示该槽位改用 {@link #ghostItemFor} 画原版物品虚影 */
    public static ResourceLocation ghostFor(int index) {
        if (isAppleGhostSlot(index)) return null;
        return index >= 0 && index < TOOL_GHOSTS.length ? TOOL_GHOSTS[index] : null;
    }

    /** 以淡化原版物品作为空槽虚影的槽位：工具栏最后一格画原版苹果，弹药槽画原版箭；其余返回 EMPTY */
    public static ItemStack ghostItemFor(int index) {
        if (isAppleGhostSlot(index)) return GHOST_APPLE;
        if (index == BeltInventory.AMMO_SLOT) return GHOST_ARROW;
        return ItemStack.EMPTY;
    }

    /** 该腰带编号在当前配置下是否启用（弹药槽始终启用） */
    public static boolean isEnabled(int beltIndex) {
        return beltIndex == BeltInventory.AMMO_SLOT || BeltInventory.isToolIndex(beltIndex);
    }

    private final Player player;
    public final int beltIndex;

    public BeltSlot(Player player, int index, int xPosition, int yPosition) {
        super(ModRegistries.belt(player), index, xPosition, yPosition);
        this.player = player;
        this.beltIndex = index;
    }

    @Override
    public IItemHandler getItemHandler() {
        return ModRegistries.belt(this.player);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 如果当前槽位正处于手持激活状态，禁止放入新物品
        BeltInventory belt = (BeltInventory) getItemHandler();
        if (this.beltIndex == belt.activeBeltSlot) {
            return false;
        }
        if (!belt.isItemValid(this.beltIndex, stack)) {
            return false;
        }
        // 当弹药槽内物品数量已超过单组上限（64）时，禁止用不同种类的弹药直接点击交换，防止光标上出现 >64 的堆叠
        ItemStack existing = getItem();
        if (!existing.isEmpty() && existing.getCount() > existing.getMaxStackSize()
                && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem() {
        return getItemHandler().getStackInSlot(this.beltIndex);
    }

    @Override
    public com.mojang.datafixers.util.Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        BeltInventory belt = (BeltInventory) getItemHandler();
        if (this.beltIndex == belt.activeBeltSlot) {
            return null; // 手持中的槽位由抽屉自己画出手上的实物，不显示虚影
        }
        ResourceLocation ghost = ghostFor(this.beltIndex);
        return ghost != null ? com.mojang.datafixers.util.Pair.of(InventoryMenu.BLOCK_ATLAS, ghost) : null;
    }

    @Override
    public void set(ItemStack stack) {
        BeltInventory.clearActiveSlot(stack);
        ((BeltInventory) getItemHandler()).setStackInSlot(this.beltIndex, stack);
    }

    @Override
    public void initialize(ItemStack stack) {
        BeltInventory.clearActiveSlot(stack);
        ((BeltInventory) getItemHandler()).setStackInSlot(this.beltIndex, stack);
    }

    @Override
    public void setChanged() {
        ((BeltInventory) getItemHandler()).setStackInSlot(this.beltIndex, this.getItem());
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
        // 当弹药槽数量超过单组上限（64）且玩家右键取半（count < current.getCount()）时，取出半组（32）
        if (!current.isEmpty() && current.getCount() > current.getMaxStackSize() && count < current.getCount()) {
            count = (current.getMaxStackSize() + 1) / 2;
        }
        return super.tryRemove(count, decrement, playerIn);
    }

    /**
     * 处理对着超量弹药槽按快捷键 1~9 或 F（ClickType.SWAP）时的交换逻辑，
     * 避免把超过 64 的堆叠直接塞进玩家快捷栏或副手。
     *
     * @return true 表示已接管处理，调用方无需再走原版 super.clicked
     */
    public static boolean handleOverstackedSwap(AbstractContainerMenu menu, int slotId, int button, ClickType clickType, Player player) {
        if (clickType != ClickType.SWAP || slotId < 0 || slotId >= menu.slots.size()) return false;
        if ((button < 0 || button >= 9) && button != 40) return false;

        Slot slot = menu.slots.get(slotId);
        if (!(slot instanceof BeltSlot) && !slot.getClass().getSimpleName().equals("BeltMenuSlot")) return false;

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty() || slotStack.getCount() <= slotStack.getMaxStackSize()) {
            return false; // 未超单组上限（<=64）时直接走原版交换
        }

        if (!slot.mayPickup(player)) return true;

        Inventory inv = player.getInventory();
        ItemStack hotbarStack = inv.getItem(button);
        if (hotbarStack.isEmpty()) {
            // 目标快捷栏/副手为空：只取出 1 组（64 个）放过去
            ItemStack extracted = slot.remove(slotStack.getMaxStackSize());
            inv.setItem(button, extracted);
            slot.onTake(player, extracted);
            slot.setChanged();
        } else if (ItemStack.isSameItemSameComponents(slotStack, hotbarStack)) {
            // 目标快捷栏/副手是同种弹药：尽量合并进弹药槽
            int room = slot.getMaxStackSize(hotbarStack) - slotStack.getCount();
            if (room > 0) {
                int move = Math.min(room, hotbarStack.getCount());
                hotbarStack.shrink(move);
                slotStack.grow(move);
                slot.setChanged();
            }
        }
        return true;
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        // 如果当前槽位正处于手持激活状态，禁止抓取（实体道具在快捷栏第 1 格）
        BeltInventory belt = (BeltInventory) getItemHandler();
        if (this.beltIndex == belt.activeBeltSlot) {
            return false;
        }
        return !getItemHandler().extractItem(this.beltIndex, 1, true).isEmpty();
    }

    @Override
    public ItemStack remove(int amount) {
        return getItemHandler().extractItem(this.beltIndex, amount, false);
    }

    private static BooleanSupplier clientDrawerActiveCheck = () -> true;

    public static void setClientDrawerActiveCheck(BooleanSupplier check) {
        clientDrawerActiveCheck = check;
    }

    @Override
    public boolean isActive() {
        if (!isEnabled(this.beltIndex)) return false;
        int targetY = this.beltIndex == BeltInventory.AMMO_SLOT ? ammoY() : toolY(this.beltIndex);
        if (this.y != targetY) {
            ((com.theman.toolbelt.mixin.SlotAccessor) this).toolbelt$setY(targetY);
        }
        if (this.player.level().isClientSide()) {
            return clientDrawerActiveCheck.getAsBoolean();
        }
        return true;
    }
}
