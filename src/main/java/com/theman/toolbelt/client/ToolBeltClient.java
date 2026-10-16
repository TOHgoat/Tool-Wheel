package com.theman.toolbelt.client;

import com.theman.toolbelt.ToolBeltMod;
import com.theman.toolbelt.belt.BeltSlot;
import com.theman.toolbelt.network.OpenBeltPayload;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端入口：只在实体客户端（Physical Client）上加载，绝不触碰服务端。
 */
@Mod(value = ToolBeltMod.MODID, dist = Dist.CLIENT)
public class ToolBeltClient {

    public ToolBeltClient(IEventBus modBus, ModContainer container) {
        // 注入客户端抽屉槽位可用性判断（与 BeltSlot 解耦，避免独立服务端加载客户端类崩溃）
        BeltSlot.setClientDrawerActiveCheck(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen instanceof InventoryScreen && InventoryButtonHandler.isDrawerInteractable();
        });

        // 注册模组菜单中的“配置 (Config)”按钮界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册按键
        modBus.addListener(ModKeys::register);
        // 注册容器对应的界面
        modBus.addListener(ToolBeltClient::registerScreens);

        // 注册原版背包界面添加按钮的处理器
        NeoForge.EVENT_BUS.register(InventoryButtonHandler.class);
        // 注册客户端每 tick 按键检测
        NeoForge.EVENT_BUS.register(ToolBeltClient.class);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.TOOL_BELT_MENU.get(), ToolBeltScreen::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 没有打开任何 GUI 时，响应按键
        if (mc.screen == null) {
            // 长按 Z 打开战术轮盘
            if (ModKeys.WHEEL.isDown()) {
                mc.setScreen(new ToolWheelScreen());
            }
            // 按下备用快捷键打开腰带界面
            else if (ModKeys.OPEN_BELT.consumeClick()) {
                PacketDistributor.sendToServer(OpenBeltPayload.INSTANCE);
            }
        }
    }

    /** 打开战术轮盘期间隐藏屏幕中央的原版十字准星，避免透过轮盘中心露出 */
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
                && Minecraft.getInstance().screen instanceof ToolWheelScreen) {
            event.setCanceled(true);
        }
    }
}
