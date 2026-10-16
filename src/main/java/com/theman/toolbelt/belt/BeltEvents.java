package com.theman.toolbelt.belt;

import com.theman.toolbelt.config.ModConfig;
import com.theman.toolbelt.network.SyncBeltPayload;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 腰带生命周期事件：实时同步、手持耐久镜像回写、死亡掉落。
 */
public final class BeltEvents {

    private record BeltSnapshot(ItemStack[] stacks, int activeBeltSlot, int toolSlots) {}
    private static final Map<UUID, BeltSnapshot> SNAPSHOTS = new HashMap<>();

    private BeltEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        BeltInventory belt = ModRegistries.belt(sp);
        int currentToolSlots = BeltInventory.toolSlots();
        var inv = sp.getInventory();

        // 如果手持第 1 格为空，或者手持物品已经被移走（如玩家主动丢出、拖入箱子或调换），自动解除激活绑定
        // 特例：若玩家正打开背包用鼠标光标拎着该激活工具（carried），暂时保留激活状态
        if (BeltInventory.isToolIndex(belt.activeBeltSlot)) {
            ItemStack hand = inv.getItem(BeltSwapLogic.TARGET_SLOT);
            boolean inTargetSlot = !hand.isEmpty() && BeltInventory.getActiveSlot(hand) == belt.activeBeltSlot;
            ItemStack carried = sp.containerMenu != null ? sp.containerMenu.getCarried() : ItemStack.EMPTY;
            boolean onCursor = hand.isEmpty() && !carried.isEmpty()
                    && BeltInventory.getActiveSlot(carried) == belt.activeBeltSlot;
            if (!inTargetSlot && !onCursor) {
                belt.activeBeltSlot = -1;
            }
        }

        // 清理背包其他槽位（以及未激活时的第 1 格/光标）上残留的 ToolBeltActiveSlot 标记，确保食物/工具能正常与原版物品堆叠
        int invSize = inv.getContainerSize();
        for (int i = 0; i < invSize; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (i == BeltSwapLogic.TARGET_SLOT && BeltInventory.isToolIndex(belt.activeBeltSlot)
                    && BeltInventory.getActiveSlot(s) == belt.activeBeltSlot) {
                continue;
            }
            BeltInventory.clearActiveSlot(s);
        }
        if (sp.containerMenu != null) {
            ItemStack carried = sp.containerMenu.getCarried();
            if (!carried.isEmpty() && (!BeltInventory.isToolIndex(belt.activeBeltSlot)
                    || BeltInventory.getActiveSlot(carried) != belt.activeBeltSlot)) {
                BeltInventory.clearActiveSlot(carried);
            }
        }

        BeltSnapshot last = SNAPSHOTS.get(sp.getUUID());
        // 当玩家在游戏内修改了工具槽数量配置时，立即将超出槽位的物品退回背包
        if (last != null && last.toolSlots() != currentToolSlots) {
            returnDisabledSlots(sp);
        }
        if (last != null && unchanged(belt, currentToolSlots, last)) return;

        ItemStack[] snap = new ItemStack[BeltInventory.SIZE];
        List<ItemStack> list = new ArrayList<>(BeltInventory.SIZE);
        for (int i = 0; i < BeltInventory.SIZE; i++) {
            ItemStack copy = belt.getStackInSlot(i).copy();
            snap[i] = copy;
            list.add(copy);
        }
        SNAPSHOTS.put(sp.getUUID(), new BeltSnapshot(snap, belt.activeBeltSlot, currentToolSlots));
        PacketDistributor.sendToPlayer(sp, new SyncBeltPayload(list, belt.activeBeltSlot));
    }

    private static boolean unchanged(BeltInventory belt, int currentToolSlots, BeltSnapshot last) {
        if (belt.activeBeltSlot != last.activeBeltSlot || currentToolSlots != last.toolSlots) return false;
        for (int i = 0; i < BeltInventory.SIZE; i++) {
            if (!ItemStack.matches(belt.getStackInSlot(i), last.stacks[i])) return false;
        }
        return true;
    }

    public static void forceResync(Player player) {
        SNAPSHOTS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            returnDisabledSlots(sp);
        }
        forceResync(event.getEntity());
    }

    /**
     * 工具槽数量调小后，把被停用槽位里的物品退回背包（背包满则掉落在脚下）。
     * 若手上拿着的正是来自被停用槽位的工具，清掉腰带标记，让它作为普通物品留在手上。
     */
    private static void returnDisabledSlots(ServerPlayer sp) {
        BeltInventory belt = ModRegistries.belt(sp);
        int enabled = BeltInventory.toolSlots();

        if (belt.activeBeltSlot >= enabled && belt.activeBeltSlot < BeltInventory.MAX_TOOL_SLOTS) {
            ItemStack hand = sp.getInventory().getItem(BeltSwapLogic.TARGET_SLOT);
            if (BeltInventory.getActiveSlot(hand) == belt.activeBeltSlot) {
                BeltInventory.clearActiveSlot(hand);
            }
            belt.activeBeltSlot = -1;
        }

        for (int i = enabled; i < BeltInventory.MAX_TOOL_SLOTS; i++) {
            ItemStack stack = belt.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            belt.setStackInSlot(i, ItemStack.EMPTY);
            sp.getInventory().placeItemBackInInventory(stack);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forceResync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forceResync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forceResync(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemStack tossed = event.getEntity().getItem();
        if (!tossed.isEmpty()) {
            BeltInventory.clearActiveSlot(tossed);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // 若原版开启了死亡保留 keepInventory，或者配置为保留，则保留不掉落；否则掉落
        boolean keepByRule = sp.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        boolean keepByConfig = ModConfig.KEEP_BELT_ON_DEATH.get();
        if (keepByRule || keepByConfig) return;

        BeltInventory belt = ModRegistries.belt(sp);
        for (int i = 0; i < BeltInventory.SIZE; i++) {
            ItemStack stack = belt.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            // 若当前槽位正装备于快捷栏第 1 格，原版 inventory.dropAll() 已掉落，此处避免重复掉落
            if (i == belt.activeBeltSlot) {
                belt.setStackInSlot(i, ItemStack.EMPTY);
                continue;
            }
            // 超量堆叠（如 256 支箭）按原版单组上限拆分为多个掉落物实体，避免实体 NBT 存盘超出 1~99 报错
            ItemStack remaining = stack.copy();
            int maxPerEntity = Math.max(1, remaining.getMaxStackSize());
            while (!remaining.isEmpty()) {
                ItemStack piece = remaining.split(Math.min(remaining.getCount(), maxPerEntity));
                ItemEntity entity = new ItemEntity(sp.level(), sp.getX(), sp.getEyeY() - 0.3, sp.getZ(), piece);
                entity.setDefaultPickUpDelay();
                event.getDrops().add(entity);
            }
            belt.setStackInSlot(i, ItemStack.EMPTY);
        }
        belt.activeBeltSlot = -1;
    }
}
