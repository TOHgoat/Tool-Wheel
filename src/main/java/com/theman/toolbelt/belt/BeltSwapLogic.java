package com.theman.toolbelt.belt;

import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 轮盘切换逻辑（仅服务端）。
 * <ul>
 *   <li>选择槽位 i：把 i 中的工具移到快捷栏第 1 格并标记来源槽位；若当前选中的不是第 1 格，自动切过去；</li>
 *   <li>之前手持的腰带工具带着最新耐久 / 附魔放回原槽；</li>
 *   <li>第 1 格原本是普通物品时：打开轮盘时所在的格子（第 N 格）为空就挪过去，否则收进背包；
 *       都放不下则取消切换；</li>
 *   <li>收回（tryHolster）仍只在选中第 1 格时生效。</li>
 * </ul>
 */
public final class BeltSwapLogic {

    public static final int TARGET_SLOT = 0; // 快捷栏第 1 格

    private BeltSwapLogic() {}

    public static void trySwap(ServerPlayer player, int beltIndex) {
        // 1. 基础校验
        if (!BeltInventory.isToolIndex(beltIndex)) return;
        if (!player.isAlive() || player.isSpectator()) return;

        Inventory inv = player.getInventory();
        BeltInventory belt = ModRegistries.belt(player);
        int fromSlot = inv.selected; // 打开轮盘时所在的快捷栏格
        ItemStack handItem = inv.getItem(TARGET_SLOT);
        boolean holdingBeltTool = isToolSlot(belt.activeBeltSlot) && !handItem.isEmpty()
                && BeltInventory.getActiveSlot(handItem) == belt.activeBeltSlot;

        // 选的正是第 1 格里那件腰带工具：只需切回第 1 格
        if (holdingBeltTool && beltIndex == belt.activeBeltSlot) {
            selectTargetSlot(player);
            return;
        }

        ItemStack chosen = belt.getStackInSlot(beltIndex);
        if (chosen.isEmpty()) return;

        // 2. 腾出第 1 格
        if (holdingBeltTool) {
            // 放回原槽（保留最新耐久 / 附魔）
            ItemStack toHolster = handItem.copy();
            BeltInventory.clearActiveSlot(toHolster);
            belt.setStackInSlot(belt.activeBeltSlot, toHolster);
        } else if (!handItem.isEmpty()) {
            // 普通物品：挪到第 N 格或收进背包，放不下就取消，物品原地不动
            if (!relocateHandItem(inv, handItem.copy(), fromSlot)) return;
        }

        // 3. 打断右键使用动作（如吃东西、拉弓）
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }

        // 4. 把选中的工具从腰带移到快捷栏第 1 格并标记来源槽位（移动而非复制，避免刷物品）
        ItemStack toolToEquip = chosen.copy();
        belt.setStackInSlot(beltIndex, ItemStack.EMPTY);
        BeltInventory.markActiveSlot(toolToEquip, beltIndex);
        inv.setItem(TARGET_SLOT, toolToEquip);
        belt.activeBeltSlot = beltIndex;
        selectTargetSlot(player);

        // 5. 立即广播同步所有容器，并向客户端推送腰带状态刷新
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        BeltEvents.forceResync(player);

        // 6. 装备音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.7F, 1.25F);
    }

    /** 把手持中的腰带工具放回原槽并清空快捷栏第 1 格（仅服务端，需选中第 1 格）。 */
    public static void tryHolster(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;

        Inventory inv = player.getInventory();
        if (inv.selected != TARGET_SLOT) {
            return;
        }

        BeltInventory belt = ModRegistries.belt(player);
        if (!BeltInventory.isToolIndex(belt.activeBeltSlot)) {
            return;
        }

        ItemStack handItem = inv.getItem(TARGET_SLOT);
        // 校验手持物是否正是之前拔出的腰带工具
        if (!handItem.isEmpty() && BeltInventory.getActiveSlot(handItem) == belt.activeBeltSlot) {
            ItemStack toHolster = handItem.copy();
            BeltInventory.clearActiveSlot(toHolster);
            belt.setStackInSlot(belt.activeBeltSlot, toHolster);
            inv.setItem(TARGET_SLOT, ItemStack.EMPTY);
        } else {
            belt.activeBeltSlot = -1;
            BeltEvents.forceResync(player);
            return;
        }

        belt.activeBeltSlot = -1;

        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }

        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        BeltEvents.forceResync(player);

        // 收回音效（音调略低于拔出）
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.7F, 0.9F);
    }

    private static boolean isToolSlot(int slot) {
        return BeltInventory.isToolIndex(slot);
    }

    /** 切到快捷栏第 1 格，并通知客户端（与原版 /item 等切换选中格的方式一致） */
    private static void selectTargetSlot(ServerPlayer player) {
        Inventory inv = player.getInventory();
        if (inv.selected == TARGET_SLOT) return;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }
        inv.selected = TARGET_SLOT;
        player.connection.send(new ClientboundSetCarriedItemPacket(TARGET_SLOT));
    }

    /**
     * 给第 1 格原本的普通物品找个去处：
     * 打开轮盘时所在的快捷栏格（第 N 格）为空就放过去；否则收进背包。
     * 返回 false 表示放不下，此时背包不做任何改动。
     */
    private static boolean relocateHandItem(Inventory inv, ItemStack item, int fromSlot) {
        if (fromSlot != TARGET_SLOT && Inventory.isHotbarSlot(fromSlot) && inv.getItem(fromSlot).isEmpty()) {
            inv.setItem(fromSlot, item);
            return true;
        }
        return storeHandItem(inv, item);
    }

    /**
     * 将手上原本的物品收纳进背包，顺序：背包第 3 排 → 第 2 排 → 第 1 排 → 快捷栏第 2 ~ 9 格。
     * 先确认放得下再动手，放不下时返回 false 且不修改背包（避免部分合并后取消导致物品复制）。
     */
    private static boolean storeHandItem(Inventory inv, ItemStack toStore) {
        if (toStore.isEmpty()) return true;

        int[] priorityOrder = getPrioritySlotOrder();
        if (!canStore(inv, toStore, priorityOrder)) return false;

        // 1. 若物品可堆叠，优先尝试合并入已有相同物品槽位
        if (toStore.isStackable()) {
            for (int slot : priorityOrder) {
                ItemStack current = inv.getItem(slot);
                if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, toStore)) {
                    int maxStack = Math.min(current.getMaxStackSize(), inv.getMaxStackSize());
                    int available = maxStack - current.getCount();
                    if (available > 0) {
                        int moveCount = Math.min(available, toStore.getCount());
                        current.grow(moveCount);
                        toStore.shrink(moveCount);
                        if (toStore.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }

        // 2. 将剩余物品存入空槽位（优先第 3 排 -> 第 2 排 -> 第 1 排）
        for (int slot : priorityOrder) {
            ItemStack current = inv.getItem(slot);
            if (current.isEmpty()) {
                inv.setItem(slot, toStore.copy());
                toStore.setCount(0);
                return true;
            }
        }

        return toStore.isEmpty();
    }

    /** 计算可合并空间 + 空格数，判断能否完整放下 */
    private static boolean canStore(Inventory inv, ItemStack toStore, int[] order) {
        int need = toStore.getCount();
        for (int slot : order) {
            ItemStack current = inv.getItem(slot);
            if (current.isEmpty()) return true;
            if (toStore.isStackable() && ItemStack.isSameItemSameComponents(current, toStore)) {
                int max = Math.min(current.getMaxStackSize(), inv.getMaxStackSize());
                need -= Math.max(0, max - current.getCount());
                if (need <= 0) return true;
            }
        }
        return false;
    }

    private static int[] getPrioritySlotOrder() {
        int[] order = new int[35];
        int idx = 0;
        // 第 3 排（最靠近快捷栏的一排，索引 27 ~ 35）
        for (int i = 27; i <= 35; i++) order[idx++] = i;
        // 第 2 排（中间一排，索引 18 ~ 26）
        for (int i = 18; i <= 26; i++) order[idx++] = i;
        // 第 1 排（最顶上一排，索引 9 ~ 17）
        for (int i = 9; i <= 17; i++) order[idx++] = i;
        // 快捷栏其他格（索引 1 ~ 8）
        for (int i = 1; i <= 8; i++) order[idx++] = i;
        return order;
    }
}
