package com.theman.toolbelt.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** 只有物品图标的扁平按钮：平时无底框，悬停时显示内凹格子底框 */
public class IconButton extends Button {

    private final ItemStack icon;

    public IconButton(int x, int y, int size, ItemStack icon, Component tooltip, OnPress onPress) {
        super(x, y, size, size, tooltip, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            GuiDraw.slot(g, x + (width - 18) / 2, y + (height - 18) / 2);
        }
        g.renderFakeItem(icon, x + (width - 16) / 2, y + (height - 16) / 2);
    }
}
