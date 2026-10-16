package com.theman.toolbelt.belt;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.config.ModConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

/**
 * 玩家身上的腰带，存储容量固定为 9 格：
 * <pre>
 *   0 ~ 7 : 工具 / 武器 / 食物槽（实际启用前 {@link #toolSlots()} 个，由服务端配置决定）
 *   8     : 弹药槽
 * </pre>
 * 容量固定是为了让存档格式、网络同步与菜单槽位编号不随配置变化。
 */
public class BeltInventory extends ItemStackHandler {

    public static final int MAX_TOOL_SLOTS = ModConfig.MAX_TOOL_SLOTS;
    public static final int AMMO_SLOT = MAX_TOOL_SLOTS;
    public static final int SIZE = MAX_TOOL_SLOTS + 1;

    /** 弹药槽最大堆叠数量 */
    public static final int AMMO_MAX_STACK_SIZE = 256;

    /** 存档布局版本：1 = 旧版 6 格（弹药在 5），2 = 当前 9 格（弹药在 8） */
    private static final String TAG_LAYOUT = "Layout";
    private static final String TAG_AMMO_COUNT = "AmmoCount";
    private static final int LAYOUT = 2;
    private static final int LEGACY_SIZE = 6;
    private static final int LEGACY_AMMO_SLOT = 5;

    /** 当前启用的工具槽数量（4 ~ 8） */
    public static int toolSlots() {
        return ModConfig.toolSlots();
    }

    /** 是否为当前启用的工具槽编号 */
    public static boolean isToolIndex(int slot) {
        return slot >= 0 && slot < toolSlots();
    }

    /** 记录当前手持第 1 格所投影/激活的腰带槽位索引（-1 表示未激活） */
    public int activeBeltSlot = -1;

    public static final String TAG_ACTIVE_SLOT = "ToolBeltActiveSlot";

    public static void markActiveSlot(ItemStack stack, int slot) {
        if (stack.isEmpty()) return;
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_ACTIVE_SLOT, slot));
    }

    public static void clearActiveSlot(ItemStack stack) {
        if (stack.isEmpty()) return;
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        if (data.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        if (!data.contains(TAG_ACTIVE_SLOT)) return;
        CompoundTag tag = data.copyTag();
        tag.remove(TAG_ACTIVE_SLOT);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        }
    }

    public static int getActiveSlot(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_ACTIVE_SLOT)) return -1;
        return data.copyTag().getInt(TAG_ACTIVE_SLOT);
    }

    public static final TagKey<Item> BELT_TOOLS = TagKey.create(Registries.ITEM, ToolBeltMod.id("belt_tools"));
    public static final TagKey<Item> BELT_AMMO = TagKey.create(Registries.ITEM, ToolBeltMod.id("belt_ammo"));

    public BeltInventory() {
        super(SIZE);
    }

    /**
     * 判断物品是否可以放入腰带的工具/武器槽（0 ~ 4 槽位）。
     * 全面支持原版工具、武器，以及各类模组武器（暮色森林全系列弓、虚无世界 AoA3 的各种枪械与弓、主流枪械模组武器等）。
     */
    public static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 1. 食物与饮品类，任何时候允许放入战术快捷腰带
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }

        Item item = stack.getItem();

        // 2. 严格排除：穿戴类护甲、马铠、狼铠、鞘翅、普通建筑方块（防止误放）
        if (item instanceof ArmorItem || item instanceof AnimalArmorItem || item instanceof ElytraItem) {
            return false;
        }
        if (item instanceof BlockItem) {
            return false;
        }

        // 3. 严格排除：纯箭矢类（纯箭矢/弩箭属于第 5 格专用弹药槽）
        if (item instanceof ArrowItem || isAmmo(stack)) {
            return false;
        }

        // 4. 显式白名单标签
        if (stack.is(BELT_TOOLS)) {
            return true;
        }

        // 5. 原版标准工具与武器类（涵盖所有继承 BowItem 的模组弓、弩、发射器、近战武器）
        if (item instanceof ProjectileWeaponItem   // 所有弓 (BowItem)、弩 (CrossbowItem)、发射器
                || item instanceof TieredItem     // 剑、镐、斧、锹、锄
                || item instanceof ShieldItem     // 盾牌
                || item instanceof TridentItem    // 三叉戟
                || item instanceof MaceItem       // 重锤
                || item instanceof FishingRodItem // 钓鱼竿
                || item instanceof ShearsItem     // 剪刀
                || item instanceof FlintAndSteelItem // 打火石
                || item instanceof BrushItem      // 刷子
                || item instanceof SpyglassItem   // 望远镜
        ) {
            return true;
        }

        // 6. DataComponents 工具属性与充能发射属性（1.20.5+ 工具规范）
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.CHARGED_PROJECTILES)) {
            return true;
        }

        // 7. 通用附魔标签匹配（凡能附魔武器、弓、弩、挖掘、耐久等的道具）
        if (stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.is(ItemTags.BOW_ENCHANTABLE)
                || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
                || stack.is(ItemTags.MINING_ENCHANTABLE)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE)
                || stack.is(ItemTags.MACE_ENCHANTABLE)
                || stack.is(ItemTags.FISHING_ENCHANTABLE)) {
            return true;
        }

        // 8. 启发式识别：模组枪械、火炮、法杖、魔杖及各种拓展武器（支持虚无世界 AoA3、CGM、TAC 等）
        var res = BuiltInRegistries.ITEM.getKey(item);
        String path = res.getPath().toLowerCase();
        String className = item.getClass().getName().toLowerCase();

        if (path.contains("gun") || path.contains("blaster") || path.contains("cannon")
                || path.contains("rifle") || path.contains("shotgun") || path.contains("sniper")
                || path.contains("pistol") || path.contains("revolver") || path.contains("firearm")
                || path.contains("musket") || path.contains("carbine") || path.contains("launcher")
                || path.contains("staff") || path.contains("wand") || path.contains("scepter")
                || path.contains("bow") || path.contains("weapon") || path.contains("dagger")
                || path.contains("hammer") || path.contains("wrench") || path.contains("sickle")
                || path.contains("scythe") || path.contains("saw") || path.contains("drill")) {
            return true;
        }

        if (className.contains("gun") || className.contains("blaster") || className.contains("cannon")
                || className.contains("rifle") || className.contains("shotgun") || className.contains("sniper")
                || className.contains("weapon") || className.contains("staff") || className.contains("wand")
                || className.contains("bow")) {
            return true;
        }

        // 9. 耐久度保底：凡是有耐久度且非防具的道具，皆为可用工具/武器
        if (stack.isDamageableItem() || stack.has(DataComponents.MAX_DAMAGE) || stack.getMaxDamage() > 0) {
            return true;
        }

        return false;
    }

    public static boolean isAmmo(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();

        // 1. 护甲、工具、剑、盾牌不能放入弹药槽
        if (item instanceof ArmorItem || item instanceof TieredItem || item instanceof SwordItem || item instanceof ShieldItem) {
            return false;
        }

        // 2. 枪械弹药、子弹、弹夹、能量电池等明确排除（避免玩家误会，枪械需从背包内部检索）
        var res = BuiltInRegistries.ITEM.getKey(item);
        String path = res.getPath().toLowerCase();
        if (path.contains("bullet") || path.contains("shell") || path.contains("slug")
                || path.contains("pellet") || path.contains("magazine") || path.contains("energy_cell")
                || path.contains("cannon") || path.contains("cartridge") || path.contains("grenade")) {
            return false;
        }

        // 3. 显式箭头/箭矢标签（原版 #minecraft:arrows 以及烟花火箭）
        if (stack.is(BELT_AMMO)) {
            return true;
        }

        // 4. 原版及模组继承自 ArrowItem 的各种特殊箭矢
        if (item instanceof net.minecraft.world.item.ArrowItem) {
            return true;
        }

        // 5. 模组专属真实箭矢/弩箭（如 aoa3:haunted_arrow, aoa3:skeletal_arrow 等可被弓弩射击的弹药）
        if (path.endsWith("_arrow") || path.equals("arrow") || path.endsWith("_bolt")) {
            return true;
        }

        return false;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == AMMO_SLOT ? AMMO_MAX_STACK_SIZE : super.getSlotLimit(slot);
    }

    @Override
    public int getStackLimit(int slot, ItemStack stack) {
        if (slot == AMMO_SLOT && stack.getMaxStackSize() > 1) {
            return AMMO_MAX_STACK_SIZE;
        }
        return super.getStackLimit(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == AMMO_SLOT) return isAmmo(stack);
        return isToolIndex(slot) && isTool(stack); // 未启用的工具槽不接收物品
    }

    public ItemStack getAmmo() {
        return getStackInSlot(AMMO_SLOT);
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        // 原版 ItemStack.CODEC 限制 count 在 1 ~ 99 之间，超量弹药先压回单组上限存盘，真实数量单列字段保存
        ItemStack ammo = getStackInSlot(AMMO_SLOT);
        int realAmmoCount = ammo.getCount();
        int clampedCount = Math.min(realAmmoCount, ammo.getMaxStackSize());
        if (!ammo.isEmpty() && realAmmoCount != clampedCount) {
            ammo.setCount(clampedCount);
        }
        CompoundTag tag;
        try {
            tag = super.serializeNBT(provider);
        } finally {
            if (!ammo.isEmpty() && ammo.getCount() != realAmmoCount) {
                ammo.setCount(realAmmoCount);
            }
        }
        if (!ammo.isEmpty()) {
            tag.putInt(TAG_AMMO_COUNT, realAmmoCount);
        }
        tag.putInt("activeBeltSlot", activeBeltSlot);
        tag.putInt(TAG_LAYOUT, LAYOUT);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag nbt) {
        // 父类会按存档里的 Size 重设容量（旧档为 6），读完后再恢复为 9 格
        super.deserializeNBT(provider, nbt);
        boolean legacy = !nbt.contains(TAG_LAYOUT) && stacks.size() == LEGACY_SIZE;
        if (stacks.size() != SIZE) {
            NonNullList<ItemStack> old = stacks;
            stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            if (legacy) {
                // 旧版：0 ~ 4 工具不动，5 号弹药移到新的弹药槽
                for (int i = 0; i < LEGACY_AMMO_SLOT; i++) stacks.set(i, old.get(i));
                stacks.set(AMMO_SLOT, old.get(LEGACY_AMMO_SLOT));
            } else {
                for (int i = 0; i < Math.min(old.size(), SIZE); i++) stacks.set(i, old.get(i));
            }
        }
        if (nbt.contains(TAG_AMMO_COUNT)) {
            ItemStack ammo = stacks.get(AMMO_SLOT);
            if (!ammo.isEmpty()) {
                int count = Math.max(1, Math.min(getStackLimit(AMMO_SLOT, ammo), nbt.getInt(TAG_AMMO_COUNT)));
                ammo.setCount(count);
            }
        }
        activeBeltSlot = nbt.contains("activeBeltSlot") ? nbt.getInt("activeBeltSlot") : -1;
    }
}
