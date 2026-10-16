package com.theman.toolbelt.client;

import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.belt.BeltSlot;
import com.theman.toolbelt.belt.BeltSwapLogic;
import com.theman.toolbelt.menu.ToolBeltMenu;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 独立的腰带界面（创造模式等没有抽屉时使用）：
 * 上方一排工具槽（4 ~ 8 个，居中）+ 1 个弹药槽，下方为玩家背包与快捷栏；右上角可返回原版背包。
 */
public class ToolBeltScreen extends AbstractContainerScreen<ToolBeltMenu> {

    public ToolBeltScreen(ToolBeltMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94; // 与原版容器一致
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new IconButton(
                this.leftPos + this.imageWidth - 22, this.topPos + 4, 18,
                new ItemStack(Items.CHEST),
                Component.translatable("gui.tool_wheel.back_to_inventory"),
                btn -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.closeContainer();
                        this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
                    }
                }
        ));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);

        if (this.menu.getCarried().isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            BeltInventory belt = ModRegistries.belt(this.minecraft.player);
            int active = belt.activeBeltSlot;
            ItemStack hand = this.minecraft.player.getInventory().getItem(BeltSwapLogic.TARGET_SLOT);
            if (BeltInventory.isToolIndex(active) && !hand.isEmpty()) {
                int sx = this.leftPos + ToolBeltMenu.toolX(active);
                int sy = this.topPos + ToolBeltMenu.TOOL_ROW_Y;
                if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                    List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, hand));
                    lines.add(Component.translatable("gui.tool_wheel.in_hand").withStyle(ChatFormatting.GREEN));
                    g.renderTooltip(this.font, lines, hand.getTooltipImage(), hand, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (this.minecraft == null || this.minecraft.player == null) return;

        BeltInventory belt = ModRegistries.belt(this.minecraft.player);
        int active = belt.activeBeltSlot;
        ItemStack hand = this.minecraft.player.getInventory().getItem(BeltSwapLogic.TARGET_SLOT);
        if (BeltInventory.isToolIndex(active) && !hand.isEmpty()) {
            int sx = ToolBeltMenu.toolX(active);
            int sy = ToolBeltMenu.TOOL_ROW_Y;
            g.renderItem(hand, sx, sy);
            g.renderItemDecorations(this.font, hand, sx, sy);
            GuiDraw.outline(g, sx - 1, sy - 1, 18, 18, 0xFF55D17A);

            int relX = mouseX - this.leftPos;
            int relY = mouseY - this.topPos;
            if (relX >= sx - 1 && relX < sx + 17 && relY >= sy - 1 && relY < sy + 17) {
                AbstractContainerScreen.renderSlotHighlight(g, sx, sy, 0);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        GuiDraw.panel(g, x, y, this.imageWidth, this.imageHeight);

        BeltInventory belt = this.minecraft != null && this.minecraft.player != null
                ? ModRegistries.belt(this.minecraft.player) : null;

        for (int i = 0; i < BeltInventory.toolSlots(); i++) {
            int sx = x + ToolBeltMenu.toolX(i);
            int sy = y + ToolBeltMenu.TOOL_ROW_Y;
            GuiDraw.slot(g, sx - 1, sy - 1);
            if (belt != null && i == belt.activeBeltSlot && this.minecraft.player != null) {
                ItemStack hand = this.minecraft.player.getInventory().getItem(BeltSwapLogic.TARGET_SLOT);
                if (!hand.isEmpty()) {
                    g.fill(sx, sy, sx + 16, sy + 16, 0x3055D17A);
                }
            } else if (belt != null && belt.getStackInSlot(i).isEmpty()) {
                GuiDraw.ghostItem(g, BeltSlot.ghostItemFor(i), sx, sy);
            }
        }
        int ammoX = x + ToolBeltMenu.AMMO_X;
        int ammoY = y + ToolBeltMenu.AMMO_Y;
        GuiDraw.slot(g, ammoX - 1, ammoY - 1);
        if (belt != null && belt.getStackInSlot(BeltInventory.AMMO_SLOT).isEmpty()) {
            GuiDraw.ghostItem(g, BeltSlot.ghostItemFor(BeltInventory.AMMO_SLOT), ammoX, ammoY);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiDraw.slot(g, x + ToolBeltMenu.INV_X + col * 18 - 1, y + ToolBeltMenu.INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiDraw.slot(g, x + ToolBeltMenu.INV_X + col * 18 - 1, y + ToolBeltMenu.HOTBAR_Y - 1);
        }
    }
}
