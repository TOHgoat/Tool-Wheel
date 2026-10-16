package com.theman.toolbelt.registry;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.menu.ToolBeltMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** 所有注册项集中在这里 */
public final class ModRegistries {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ToolBeltMod.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ToolBeltMod.MODID);

    /**
     * 玩家腰带数据：
     * - serializable：自动跟着玩家存档；
     * - copyOnDeath：开了 keepInventory 时重生后保留（没开时我们在死亡掉落里已经清空，复制过去的也是空的）。
     *   从末地回主世界本来就会复制，不需要额外处理。
     */
    public static final Supplier<AttachmentType<BeltInventory>> BELT = ATTACHMENTS.register("belt",
            () -> AttachmentType.serializable(() -> new BeltInventory()).copyOnDeath().build());

    public static final DeferredHolder<MenuType<?>, MenuType<ToolBeltMenu>> TOOL_BELT_MENU = MENUS.register("tool_belt",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new ToolBeltMenu(id, inv)));

    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        MENUS.register(modBus);
    }

    /** 取玩家的腰带（没有就自动创建一个空的） */
    public static BeltInventory belt(Player player) {
        return player.getData(BELT);
    }
}
