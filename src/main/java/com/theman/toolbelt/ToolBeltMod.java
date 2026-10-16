package com.theman.toolbelt;

import com.mojang.logging.LogUtils;
import com.theman.toolbelt.belt.AmmoHandler;
import com.theman.toolbelt.belt.BeltEvents;
import com.theman.toolbelt.network.OpenBeltPayload;
import com.theman.toolbelt.network.SelectToolPayload;
import com.theman.toolbelt.network.SyncBeltPayload;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import com.theman.toolbelt.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * 工具腰带模组主类（服务端、客户端都会加载）。
 * 只属于客户端的东西（按键、界面、HUD）全部在 {@link com.theman.toolbelt.client.ToolBeltClient} 里。
 */
@Mod(ToolBeltMod.MODID)
public class ToolBeltMod {

    public static final String MODID = "tool_wheel";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ToolBeltMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ModConfig.SERVER_SPEC);

        ModRegistries.register(modBus);
        modBus.addListener(ToolBeltMod::registerPayloads);

        NeoForge.EVENT_BUS.register(BeltEvents.class);
        NeoForge.EVENT_BUS.register(AmmoHandler.class);

        LOGGER.info("ToolBelt loaded");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * 这个模组改了玩法（弹药槽、容器），客户端和服务端必须都装，所以不加 .optional()。
     */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 2：腰带同步改为固定 9 格（8 工具 + 1 弹药），与旧版不兼容
        PayloadRegistrar registrar = event.registrar(MODID).versioned("2");
        registrar.playToServer(OpenBeltPayload.TYPE, OpenBeltPayload.STREAM_CODEC, OpenBeltPayload::handle);
        registrar.playToServer(SelectToolPayload.TYPE, SelectToolPayload.STREAM_CODEC, SelectToolPayload::handle);
        registrar.playToClient(SyncBeltPayload.TYPE, SyncBeltPayload.STREAM_CODEC, SyncBeltPayload::handle);
    }
}
