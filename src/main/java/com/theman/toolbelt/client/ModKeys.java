package com.theman.toolbelt.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 按键（玩家可以在 选项 → 控制 → 按键绑定 → 工具腰带 里改） */
public final class ModKeys {

    public static final String CATEGORY = "key.categories.tool_wheel";

    /** 按住打开轮盘，松开确认。默认 Z */
    public static final KeyMapping WHEEL = new KeyMapping("key.tool_wheel.wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    /** 直接打开腰带界面（创造模式没有背包按钮时用）。默认不绑定 */
    public static final KeyMapping OPEN_BELT = new KeyMapping("key.tool_wheel.open",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);

    private ModKeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(WHEEL);
        event.register(OPEN_BELT);
    }
}
