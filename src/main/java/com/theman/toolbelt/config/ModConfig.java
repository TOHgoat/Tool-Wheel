package com.theman.toolbelt.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 工具腰带配置项。
 * <ul>
 *   <li>{@link #SPEC}（COMMON，config/tool_belt-common.toml）：死亡保留；</li>
 *   <li>{@link #SERVER_SPEC}（SERVER，存档/serverconfig/tool_belt-server.toml）：工具槽数量。
 *       每个存档一份，进服与游戏内修改时自动同步给客户端，保证双方槽位一致。</li>
 * </ul>
 */
public final class ModConfig {

    public static final int MIN_TOOL_SLOTS = 4;
    public static final int MAX_TOOL_SLOTS = 8;
    public static final int DEFAULT_TOOL_SLOTS = 5;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue KEEP_BELT_ON_DEATH;

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue TOOL_SLOTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation("tool_wheel.configuration.gameplay").push("gameplay");
        KEEP_BELT_ON_DEATH = builder
                .comment("Always keep belt items on death, regardless of the 'keepInventory' game rule.",
                        "true：死亡时腰带物品始终保留；false：跟随原版 keepInventory 规则。")
                .translation("tool_wheel.configuration.keepBeltOnDeath")
                .define("keepBeltOnDeath", false);
        builder.pop();
        SPEC = builder.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.translation("tool_wheel.configuration.belt").push("belt");
        TOOL_SLOTS = server
                .comment("Number of tool slots on the belt (the ammo slot is separate and always 1).",
                        "Reducing it moves items in removed slots back to the inventory (dropped if full).",
                        "工具槽数量（不含弹药槽，弹药槽固定 1 个）。",
                        "调小后，被移除槽位里的物品会自动退回背包，背包满则掉落在脚下。")
                .translation("tool_wheel.configuration.toolSlots")
                .defineInRange("toolSlots", DEFAULT_TOOL_SLOTS, MIN_TOOL_SLOTS, MAX_TOOL_SLOTS);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private ModConfig() {}

    /** 当前生效的工具槽数量；配置尚未加载（如主菜单）时返回默认值 */
    public static int toolSlots() {
        int n = SERVER_SPEC.isLoaded() ? TOOL_SLOTS.get() : DEFAULT_TOOL_SLOTS;
        return Math.max(MIN_TOOL_SLOTS, Math.min(MAX_TOOL_SLOTS, n));
    }
}

