package com.theman.toolbelt.client;

import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.belt.BeltSlot;
import com.theman.toolbelt.belt.BeltSwapLogic;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/** 原版背包右侧的腰带抽屉：面板、拉手、丝滑推拉动效、手持槽位标记，以及药水效果图标的避让。 */
public final class InventoryButtonHandler {

    public static boolean isDrawerOpen = true;

    /** 抽屉展开/收起动画时长（毫秒） */
    private static final float ANIM_DURATION_MS = 180F;
    /** 当前视觉展开度（0 = 完全收起，1 = 完全展开） */
    private static float drawerVisualProgress = 1F;
    private static long lastFrameMs = -1L;

    private static final int DRAWER_X = 176;                               // 相对 leftPos
    private static final int DRAWER_Y = BeltSlot.REL_TOOL_Y - 5;           // 相对 topPos，四周留 4px
    private static final int DRAWER_W = 26;

    /** 抽屉高度随工具槽数量变化；8 格时会略长于背包（单列布局） */
    private static int drawerH() {
        return BeltSlot.ammoY() + 17 + 5 - DRAWER_Y;
    }

    /** 分隔线位于最后一个工具槽与弹药槽之间的正中 */
    private static int dividerY() {
        int lastBottom = BeltSlot.toolY(BeltInventory.toolSlots() - 1) + 17;
        int ammoTop = BeltSlot.ammoY() - 1;
        return lastBottom + (ammoTop - lastBottom - 2) / 2;
    }

    private static final int TAB_W = 12;      // 含压住面板边框的 3px
    private static final int TAB_H = 18;
    private static final int TAB_OVERLAP = 3;
    private static final int TAB_Y = BeltSlot.REL_TOOL_Y - 1;

    private static final int C_ACTIVE = 0xFF55D17A;
    private static final int C_ACTIVE_BG = 0x3055D17A;

    private InventoryButtonHandler() {}

    /** 抽屉展开动画基本完成时才激活原版槽位拾取/渲染，动画过程中由抽屉自己裁剪绘制滑出效果 */
    public static boolean isDrawerInteractable() {
        return isDrawerOpen && getEasedProgress() >= 0.98F;
    }

    /** 每帧根据真实毫秒差推进抽屉开合进度 */
    private static void tickAnimation() {
        long now = Util.getMillis();
        if (lastFrameMs < 0L) {
            lastFrameMs = now;
            drawerVisualProgress = isDrawerOpen ? 1F : 0F;
            return;
        }
        long dt = Math.min(100L, Math.max(0L, now - lastFrameMs));
        lastFrameMs = now;

        float target = isDrawerOpen ? 1F : 0F;
        if (drawerVisualProgress != target) {
            float step = dt / ANIM_DURATION_MS;
            if (drawerVisualProgress < target) {
                drawerVisualProgress = Math.min(1F, drawerVisualProgress + step);
            } else {
                drawerVisualProgress = Math.max(0F, drawerVisualProgress - step);
            }
        }
    }

    /** 经过缓动曲线处理后的平滑开合进度 (0.0 ~ 1.0) */
    public static float getEasedProgress() {
        float p = drawerVisualProgress;
        return isDrawerOpen
                ? GuiDraw.easeOutCubic(p)
                : 1F - GuiDraw.easeOutCubic(1F - p);
    }

    /** 当前抽屉伸出背包右边缘的像素宽度（0 ~ DRAWER_W） */
    private static int currentExtendedWidth() {
        return Math.round(DRAWER_W * getEasedProgress());
    }

    /** 当前抽屉左上角相对于 leftPos 的 X 坐标（收起时缩进背包后面） */
    private static int currentDrawerRelX() {
        return DRAWER_X - DRAWER_W + currentExtendedWidth();
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) {
            lastFrameMs = Util.getMillis();
            drawerVisualProgress = isDrawerOpen ? 1F : 0F;
            event.addListener(new DrawerTab(inv));
        }
    }

    /** 药水效果图标默认贴着背包右侧，向右平滑让出抽屉的宽度；放不下时挪到左侧或隐藏 */
    @SubscribeEvent
    public static void onRenderMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;
        tickAnimation();

        int used = currentExtendedWidth() + (TAB_W - TAB_OVERLAP) + 2;
        int remaining = event.getAvailableSpace() - used;
        if (remaining >= 32) {
            event.addHorizontalOffset(used);
            if (remaining < 120) event.setCompact(true);
            return;
        }

        int bookWidth = inv.getRecipeBookComponent().isVisible() ? 147 : 0;
        int leftX = inv.getGuiLeft() - bookWidth - 34;
        if (leftX >= 0) {
            event.setHorizontalOffset(leftX);
            event.setCompact(true);
        } else {
            event.setCanceled(true);
        }
    }

    /** 面板、格子底框、分隔线，以及滑动动画期间的槽位内容预览 */
    @SubscribeEvent
    public static void onRenderBackground(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen inv)) return;
        tickAnimation();

        int extW = currentExtendedWidth();
        if (extW <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        int left = inv.getGuiLeft();
        int top = inv.getGuiTop();
        int drawerRelX = currentDrawerRelX();
        int slideOffset = drawerRelX - DRAWER_X; // 负值表示向左缩进背包内部
        int x = left + BeltSlot.REL_X + slideOffset;
        int h = drawerH();

        // 裁剪在背包右边缘之外，让抽屉像从背包背后平滑推拉出来一样
        g.enableScissor(left + DRAWER_X, top + DRAWER_Y, left + DRAWER_X + DRAWER_W + 2, top + DRAWER_Y + h);

        GuiDraw.panel(g, left + drawerRelX, top + DRAWER_Y, DRAWER_W, h);

        int active = activeSlot(inv);
        Player player = inv.getMinecraft().player;
        BeltInventory belt = player != null ? ModRegistries.belt(player) : null;

        for (int i = 0; i < BeltInventory.toolSlots(); i++) {
            int y = top + BeltSlot.toolY(i);
            GuiDraw.slot(g, x - 1, y - 1);
            if (i == active) {
                g.fill(x, y, x + 16, y + 16, C_ACTIVE_BG);
            } else if (belt != null && belt.getStackInSlot(i).isEmpty()) {
                GuiDraw.ghostItem(g, BeltSlot.ghostItemFor(i), x, y);
            }
        }

        GuiDraw.divider(g, x, top + dividerY(), 16);
        int ammoY = top + BeltSlot.ammoY();
        GuiDraw.slot(g, x - 1, ammoY - 1);
        if (belt != null && belt.getStackInSlot(BeltInventory.AMMO_SLOT).isEmpty()) {
            GuiDraw.ghostItem(g, BeltSlot.ghostItemFor(BeltInventory.AMMO_SLOT), x, ammoY);
        }

        // 当抽屉正在滑动途中（BeltSlot.isActive() 为 false）时，由我们跟着抽屉位移把槽位里的物品/虚影画出来
        if (!isDrawerInteractable()) {
            renderSlidingSlotContents(g, inv, x, top, active);
        }

        g.disableScissor();
    }

    /** 滑动过渡期间绘制腰带内的物品与空槽虚影，保证推拉过程里图标也一起平滑移动 */
    private static void renderSlidingSlotContents(GuiGraphics g, InventoryScreen inv, int slotScreenX, int top, int active) {
        Player player = inv.getMinecraft().player;
        if (player == null) return;
        BeltInventory belt = ModRegistries.belt(player);
        var font = Minecraft.getInstance().font;

        for (Slot s : inv.getMenu().slots) {
            if (!(s instanceof BeltSlot bs) || !BeltSlot.isEnabled(bs.beltIndex)) continue;
            int sy = top + (bs.beltIndex == BeltInventory.AMMO_SLOT ? BeltSlot.ammoY() : BeltSlot.toolY(bs.beltIndex));
            if (bs.beltIndex == active) {
                ItemStack hand = handStack();
                if (!hand.isEmpty()) {
                    g.renderItem(hand, slotScreenX, sy);
                    g.renderItemDecorations(font, hand, slotScreenX, sy);
                    GuiDraw.outline(g, slotScreenX - 1, sy - 1, 18, 18, C_ACTIVE);
                }
            } else {
                ItemStack stack = belt.getStackInSlot(bs.beltIndex);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, slotScreenX, sy);
                    g.renderItemDecorations(font, stack, slotScreenX, sy);
                } else {
                    var icon = bs.getNoItemIcon();
                    if (icon != null) {
                        var sprite = Minecraft.getInstance().getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                        g.blit(slotScreenX, sy, 0, 16, 16, sprite);
                    }
                }
            }
        }
    }

    /** 手持中的槽位在腰带里是空的：完全展开时在槽内画出手上的实物并加绿框（此时坐标系已平移到 leftPos/topPos） */
    @SubscribeEvent
    public static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!isDrawerInteractable() || !(event.getContainerScreen() instanceof InventoryScreen inv)) return;

        int active = activeSlot(inv);
        ItemStack hand = handStack();
        if (active < 0 || hand.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int x = BeltSlot.REL_X;
        int y = BeltSlot.toolY(active);
        g.renderItem(hand, x, y);
        g.renderItemDecorations(Minecraft.getInstance().font, hand, x, y);
        GuiDraw.outline(g, x - 1, y - 1, 18, 18, C_ACTIVE);

        // 物品是在原版悬停高亮之后画的，补一层高亮，保持与其他格子一致
        int mx = event.getMouseX() - inv.getGuiLeft();
        int my = event.getMouseY() - inv.getGuiTop();
        if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) {
            AbstractContainerScreen.renderSlotHighlight(g, x, y, 0);
        }
    }

    /** 手持槽位的提示框：原版物品提示 + 一行“手持中” */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!isDrawerInteractable() || !(event.getScreen() instanceof InventoryScreen inv)) return;
        if (!inv.getMenu().getCarried().isEmpty()) return;

        int active = activeSlot(inv);
        ItemStack hand = handStack();
        if (active < 0 || hand.isEmpty()) return;

        int x = inv.getGuiLeft() + BeltSlot.REL_X;
        int y = inv.getGuiTop() + BeltSlot.toolY(active);
        int mx = event.getMouseX();
        int my = event.getMouseY();
        if (mx < x - 1 || mx >= x + 17 || my < y - 1 || my >= y + 17) return;

        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, hand));
        lines.add(Component.translatable("gui.tool_wheel.in_hand").withStyle(ChatFormatting.GREEN));
        event.getGuiGraphics().renderTooltip(mc.font, lines, hand.getTooltipImage(), hand, mx, my);
    }

    private static int activeSlot(InventoryScreen inv) {
        Player player = inv.getMinecraft().player;
        if (player == null) return -1;
        int slot = ModRegistries.belt(player).activeBeltSlot;
        return BeltInventory.isToolIndex(slot) ? slot : -1;
    }

    private static ItemStack handStack() {
        Player player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getInventory().getItem(BeltSwapLogic.TARGET_SLOT);
    }

    /** 贴在背包 / 抽屉右缘的小拉手，跟随抽屉推拉丝滑移动 */
    private static class DrawerTab extends Button {
        private final InventoryScreen parent;

        DrawerTab(InventoryScreen parent) {
            super(tabX(parent), parent.getGuiTop() + TAB_Y, TAB_W, TAB_H, Component.empty(), b -> {}, DEFAULT_NARRATION);
            this.parent = parent;
            refresh();
        }

        private static int tabX(InventoryScreen parent) {
            return parent.getGuiLeft() + DRAWER_X + currentExtendedWidth() - TAB_OVERLAP;
        }

        @Override
        public void onPress() {
            isDrawerOpen = !isDrawerOpen;
            lastFrameMs = Util.getMillis();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), isDrawerOpen ? 1.2F : 0.95F, 0.5F));
            refresh();
        }

        private void refresh() {
            Component text = Component.translatable(isDrawerOpen ? "gui.tool_wheel.collapse" : "gui.tool_wheel.expand");
            setMessage(text);
            setTooltip(Tooltip.create(text));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // 配方书开关或抽屉滑动都会改变 X，每帧实时跟随
            setX(tabX(parent));
            setY(parent.getGuiTop() + TAB_Y);

            int x = getX(), y = getY(), w = width, h = height;
            int bg = isHoveredOrFocused() ? 0xFFDADADA : GuiDraw.BG;

            // 左侧 3px 盖住面板右边框，让拉手看起来是从面板里伸出来的
            int bx = x + TAB_OVERLAP - 1; // 与面板黑边对齐的列
            g.fill(x, y + 1, x + w - 1, y + h - 1, bg);
            g.fill(bx, y, x + w - 2, y + 1, GuiDraw.BLACK);
            g.fill(bx, y + h - 1, x + w - 2, y + h, GuiDraw.BLACK);
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, GuiDraw.BLACK);
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, GuiDraw.BLACK);
            g.fill(x + w - 1, y + 2, x + w, y + h - 2, GuiDraw.BLACK);
            g.fill(bx, y + 1, x + w - 2, y + 2, GuiDraw.LIGHT);
            g.fill(bx, y + h - 2, x + w - 2, y + h - 1, GuiDraw.SHADOW);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, GuiDraw.SHADOW);

            // 箭头：展开时指向背包（收起），收起时指向外侧（展开）
            int color = isHoveredOrFocused() ? 0xFF202020 : 0xFF404040;
            int ax = x + TAB_OVERLAP + (w - TAB_OVERLAP - 1) / 2 - 1;
            int ay = y + h / 2 - 3;
            boolean showCollapseArrow = getEasedProgress() >= 0.5F;
            for (int i = 0; i < 3; i++) {
                int dx = showCollapseArrow ? 2 - i : i;
                g.fill(ax + dx, ay + i, ax + dx + 1, ay + i + 1, color);
                g.fill(ax + dx, ay + 4 - i, ax + dx + 1, ay + 5 - i, color);
            }
        }
    }
}
