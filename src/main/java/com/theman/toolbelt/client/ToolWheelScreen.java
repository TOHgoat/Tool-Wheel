package com.theman.toolbelt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.theman.toolbelt.belt.BeltInventory;
import com.theman.toolbelt.belt.BeltSwapLogic;
import com.theman.toolbelt.network.SelectToolPayload;
import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 工具轮盘：按住快捷键打开，移动鼠标选择扇区，松开确认。
 * 在任意快捷栏格都可选工具，选中后会自动切到第 1 格。
 * 指针停在中心时松开 = 取消；若正选中第 1 格且拿着腰带工具，则为收回。
 */
public class ToolWheelScreen extends Screen {

    /** 扇区数随服务端配置（4 ~ 8），打开轮盘时确定 */
    private final int sectors = BeltInventory.toolSlots();
    private final double sectorAngle = (2.0 * Math.PI) / sectors;

    private static final float R_CENTER = 24F;        // 中心圆半径
    private static final float R_IN = 32F;            // 扇环内径
    private static final float R_OUT = 78F;           // 扇环外径（整圆统一半径，无参差台阶）
    private static final double DEAD_ZONE = 28.0;     // 指针死区半径
    private static final long OPEN_MS = 200L;         // 展开与旋入动画时长
    private static final float OPEN_SPIN_RAD = (float) Math.toRadians(-22.0); // 开场旋入起始角

    // 配色
    private static final int C_DIM = 0x66000000;
    private static final int C_SECTOR = 0xB4101318;
    private static final int C_SECTOR_HOVER = 0x48FFFFFF;
    private static final int C_EDGE_HOVER = 0xFFFFFFFF;
    private static final int C_EDGE_IDLE = 0x38FFFFFF;
    private static final int C_DIVIDER = 0x22FFFFFF;
    private static final int C_ACTIVE = 0xFF55D17A;
    private static final int C_HOLSTER = 0xFFFFB83D;

    private final long openedAt = Util.getMillis();
    private long lastFrameAt = openedAt;
    private int hoveredSlot = -1;
    private int openTicks = 0;

    /** 每个扇区独立的悬停过渡进度（0.0 ~ 1.0），实现扇区间切换时的丝滑外扩与回缩 */
    private final float[] sectorHover = new float[BeltInventory.MAX_TOOL_SLOTS];
    /** 中心圆的悬停过渡进度（0.0 ~ 1.0） */
    private float centerHover = 1F;
    /** 沿内圈顺滑转动的选中指示弧角度（弧度）与可见度（0.0 ~ 1.0） */
    private double indicatorAngle = 0.0;
    private float indicatorAlpha = 0F;
    private boolean indicatorInitialized = false;

    public ToolWheelScreen() {
        super(Component.translatable("gui.tool_wheel.wheel_title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // 兜底：部分情况下收不到 keyReleased，直接按物理按键状态判断是否已松开
        if (++openTicks > 2 && !isPhysicalWheelKeyDown()) {
            confirmSelection();
        }
    }

    /** 轮盘按键当前是否仍被按住（直接查询 GLFW） */
    public static boolean isPhysicalWheelKeyDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = ModKeys.WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int val = key.getValue();
            return val != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, val);
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Player player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null) return;

        long now = Util.getMillis();
        float dtSec = Math.min(0.1F, Math.max(0.001F, (now - lastFrameAt) / 1000F));
        lastFrameAt = now;

        float elapsed = (now - openedAt) / (float) OPEN_MS;
        float t = Math.min(1F, elapsed);
        float alphaEase = GuiDraw.easeOutCubic(t);
        float scaleEase = GuiDraw.easeOutBack(t);

        g.fill(0, 0, this.width, this.height, GuiDraw.fade(C_DIM, alphaEase));

        int cx = this.width / 2;
        int cy = this.height / 2;
        updateHover(mouseX - cx, mouseY - cy);
        stepAnimations(dtSec);

        BeltInventory belt = ModRegistries.belt(player);
        boolean canHolster = canHolster(player, belt);

        float scale = 0.74F + 0.26F * scaleEase;
        float spinAngle = OPEN_SPIN_RAD * (1F - alphaEase);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(scale, scale, 1F);
        pose.mulPose(Axis.ZP.rotation(spinAngle));
        drawShapes(g, belt, canHolster, alphaEase);
        drawSlotContents(g, player, belt, canHolster, alphaEase, -spinAngle);
        pose.popPose();

        int labelY = cy + (int) ((R_OUT + 14) * Math.min(1.03F, scale));
        drawLabel(g, player, belt, canHolster, cx, labelY, alphaEase);
    }

    /** 每帧基于真实时间差 dt 更新各扇区外扩、中心缩放与旋转指示弧的阻尼过渡 */
    private void stepAnimations(float dt) {
        float hoverRate = 1F - (float) Math.exp(-18.0 * dt);
        for (int i = 0; i < sectors; i++) {
            float target = (i == hoveredSlot) ? 1F : 0F;
            sectorHover[i] += (target - sectorHover[i]) * hoverRate;
            if (Math.abs(target - sectorHover[i]) < 0.002F) sectorHover[i] = target;
        }

        float centerTarget = (hoveredSlot == -1) ? 1F : 0F;
        centerHover += (centerTarget - centerHover) * hoverRate;

        if (isToolSlot(hoveredSlot)) {
            double targetAngle = hoveredSlot * sectorAngle;
            if (!indicatorInitialized) {
                indicatorAngle = targetAngle;
                indicatorInitialized = true;
            } else {
                // 沿着最短圆弧方向顺滑旋转到目标扇区
                double diff = Math.atan2(Math.sin(targetAngle - indicatorAngle), Math.cos(targetAngle - indicatorAngle));
                double turnRate = 1.0 - Math.exp(-22.0 * dt);
                indicatorAngle = normalizeAngle(indicatorAngle + diff * turnRate);
            }
            indicatorAlpha += (1F - indicatorAlpha) * hoverRate;
        } else {
            indicatorAlpha += (0F - indicatorAlpha) * hoverRate;
            if (indicatorAlpha < 0.02F) {
                indicatorInitialized = false;
            }
        }
    }

    private static double normalizeAngle(double a) {
        double twoPi = 2.0 * Math.PI;
        a = a % twoPi;
        return a < 0 ? a + twoPi : a;
    }

    /** 整圆环底盘（无缝隙、无参差台阶）、顺滑旋转高亮扇区与中心圆，顶点一次性提交 */
    private void drawShapes(GuiGraphics g, BeltInventory belt, boolean canHolster, float alpha) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();

        // 1. 浑然一体的整圆环底盘 + 内外圈平滑细边框（彻底消除扇区缝隙与参差不齐感）
        GuiDraw.arc(vc, m, 0, 0, R_IN, R_OUT, 0, Math.PI * 2, GuiDraw.fade(C_SECTOR, alpha));
        GuiDraw.arc(vc, m, 0, 0, R_OUT - 1.2F, R_OUT, 0, Math.PI * 2, GuiDraw.fade(C_EDGE_IDLE, alpha));
        GuiDraw.arc(vc, m, 0, 0, R_IN, R_IN + 1.0F, 0, Math.PI * 2, GuiDraw.fade(C_EDGE_IDLE, alpha));

        // 2. 扇区之间的极淡径向分割细线（嵌在圆环内部）
        for (int i = 0; i < sectors; i++) {
            double boundary = i * sectorAngle - sectorAngle / 2.0;
            GuiDraw.radialLine(vc, m, 0, 0, R_IN + 1.0F, R_OUT - 1.2F, boundary, 0.5F,
                    GuiDraw.fade(C_DIVIDER, alpha));
        }

        // 3. 当前手持激活槽位的绿色内沿标记
        if (isToolSlot(belt.activeBeltSlot)) {
            double mid = belt.activeBeltSlot * sectorAngle;
            double a0 = mid - sectorAngle / 2.0;
            double a1 = mid + sectorAngle / 2.0;
            GuiDraw.arc(vc, m, 0, 0, R_IN, R_IN + 2.4F, a0, a1, GuiDraw.fade(C_ACTIVE, alpha));
        }

        // 4. 沿着整圆环顺滑转动的选中高亮扇区（底色高亮 + 外沿亮弧 + 内圈亮弧一体平滑转动）
        if (indicatorAlpha > 0.01F) {
            double halfSpan = sectorAngle / 2.0;
            double ia0 = indicatorAngle - halfSpan;
            double ia1 = indicatorAngle + halfSpan;
            float highlightAlpha = alpha * indicatorAlpha;

            // 选中扇区柔和提亮层（严格贴合在 R_IN ~ R_OUT 之间，不突出外圈）
            GuiDraw.arc(vc, m, 0, 0, R_IN + 1.0F, R_OUT - 1.2F, ia0, ia1,
                    GuiDraw.fade(0x28FFFFFF, highlightAlpha));
            // 外沿高亮弧线
            GuiDraw.arc(vc, m, 0, 0, R_OUT - 2.2F, R_OUT, ia0, ia1,
                    GuiDraw.fade(C_EDGE_HOVER, highlightAlpha));
            // 内沿追随亮弧
            GuiDraw.arc(vc, m, 0, 0, R_IN, R_IN + 2.0F, ia0, ia1,
                    GuiDraw.fade(C_EDGE_HOVER, highlightAlpha));
        }

        // 5. 中心圆与边框
        float cRadius = R_CENTER + centerHover * 1.5F;
        int targetCenterRing = canHolster ? C_HOLSTER : 0x80FFFFFF;
        int ring = GuiDraw.lerpColor(C_EDGE_IDLE, targetCenterRing, centerHover);
        float ringWidth = 1F + centerHover;
        GuiDraw.arc(vc, m, 0, 0, 0F, cRadius, 0, Math.PI * 2, GuiDraw.fade(C_SECTOR, alpha));
        GuiDraw.arc(vc, m, 0, 0, cRadius - ringWidth, cRadius, 0, Math.PI * 2,
                GuiDraw.fade(ring, alpha));

        g.flush();
    }

    /** 扇区内的物品图标（空槽不画任何内容），以及中心符号 */
    private void drawSlotContents(GuiGraphics g, Player player, BeltInventory belt,
                                  boolean canHolster, float alpha, float counterSpin) {
        PoseStack pose = g.pose();
        float rMid = (R_IN + R_OUT) / 2F;

        for (int i = 0; i < sectors; i++) {
            ItemStack stack = stackFor(player, belt, i);
            if (stack.isEmpty()) continue;

            double mid = i * sectorAngle;
            float h = sectorHover[i];
            float x = (float) Math.sin(mid) * rMid;
            float y = (float) -Math.cos(mid) * rMid;

            float s = 1F + 0.25F * h;
            pose.pushPose();
            pose.translate(x, y, 0);
            // 反向抵消轮盘整体的旋入角，让物品图标在旋入过程中始终保持正立
            pose.mulPose(Axis.ZP.rotation(counterSpin));
            pose.scale(s, s, 1F);
            g.renderItem(stack, -8, -8);
            g.renderItemDecorations(this.font, stack, -8, -8);
            pose.popPose();
        }

        // 中心符号：仅在指针位于中心时显示（可收回为 ↓，否则为 ×）
        if (centerHover > 0.05F) {
            String mark = canHolster ? "↓" : "×";
            int color = GuiDraw.fadeText(canHolster ? C_HOLSTER : 0xFF9A9A9A, alpha * centerHover);
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation(counterSpin));
            g.drawString(this.font, mark, -this.font.width(mark) / 2 + 1, -3, color, false);
            pose.popPose();
        }
    }

    /** 轮盘下方的一行说明；没有需要说明的内容时不显示 */
    private void drawLabel(GuiGraphics g, Player player, BeltInventory belt,
                           boolean canHolster, int cx, int y, float alpha) {
        Component text;
        float emphasis = 1F;
        if (isToolSlot(hoveredSlot)) {
            emphasis = 0.5F + 0.5F * sectorHover[hoveredSlot];
            ItemStack stack = stackFor(player, belt, hoveredSlot);
            if (stack.isEmpty()) {
                text = Component.translatable("gui.tool_wheel.empty_slot").withStyle(ChatFormatting.DARK_GRAY);
            } else {
                MutableComponent name = Component.empty().append(itemName(stack));
                if (hoveredSlot == belt.activeBeltSlot) {
                    name.append("  ").append(
                            Component.translatable("gui.tool_wheel.in_hand").withStyle(ChatFormatting.GREEN));
                }
                text = name;
            }
        } else if (canHolster) {
            emphasis = 0.5F + 0.5F * centerHover;
            text = Component.translatable("gui.tool_wheel.holster").withStyle(ChatFormatting.GOLD);
        } else {
            return;
        }

        float finalAlpha = alpha * emphasis;
        int slideY = y + Math.round((1F - emphasis) * 3F);
        int w = this.font.width(text);
        g.fill(cx - w / 2 - 6, slideY - 4, cx + w / 2 + 6, slideY + 12, GuiDraw.fade(0xA0101318, finalAlpha));
        g.drawCenteredString(this.font, text, cx, slideY, GuiDraw.fadeText(0xFFFFFFFF, finalAlpha));
    }

    // ------------------------------------------------------------------ 交互

    private void updateHover(double dx, double dy) {
        int prev = hoveredSlot;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            hoveredSlot = -1;
        } else {
            double angle = Math.atan2(dy, dx) + Math.PI / 2.0; // 正上方为 0，顺时针
            if (angle < 0) angle += 2.0 * Math.PI;
            int slot = (int) (((angle + sectorAngle / 2.0) % (2.0 * Math.PI)) / sectorAngle);
            hoveredSlot = isToolSlot(slot) ? slot : -1;
        }
        // 移到新扇区时给一声很轻的提示音
        if (hoveredSlot != prev && hoveredSlot >= 0 && this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.12F));
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.WHEEL.matches(keyCode, scanCode)) {
            confirmSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            confirmSelection();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ModKeys.WHEEL.matchesMouse(button)) {
            confirmSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 关闭轮盘并把选择发给服务端 */
    private void confirmSelection() {
        this.onClose();
        if (this.minecraft == null || this.minecraft.player == null) return;

        Player player = this.minecraft.player;
        BeltInventory belt = ModRegistries.belt(player);
        boolean onFirstSlot = player.getInventory().selected == BeltSwapLogic.TARGET_SLOT;

        if (isToolSlot(hoveredSlot)) {
            // 选中已在第 1 格的那件：在第 1 格时无事可做；在别的格时由服务端切回第 1 格
            boolean alreadyInHand = hoveredSlot == belt.activeBeltSlot;
            boolean worthSending = alreadyInHand ? !onFirstSlot : !belt.getStackInSlot(hoveredSlot).isEmpty();
            if (worthSending) {
                PacketDistributor.sendToServer(new SelectToolPayload(hoveredSlot));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.25F));
            }
        } else if (canHolster(player, belt)) {
            PacketDistributor.sendToServer(new SelectToolPayload(-1));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F));
        }
    }

    // ------------------------------------------------------------------ 工具方法

    private boolean isToolSlot(int slot) {
        return slot >= 0 && slot < sectors;
    }

    /** 收回只在选中第 1 格且手上是腰带工具时生效（与服务端 tryHolster 一致） */
    private boolean canHolster(Player player, BeltInventory belt) {
        return player.getInventory().selected == BeltSwapLogic.TARGET_SLOT && isToolSlot(belt.activeBeltSlot);
    }

    /** 手持中的槽位在腰带里是空的，改取快捷栏第 1 格的实物 */
    private static ItemStack stackFor(Player player, BeltInventory belt, int slot) {
        return slot == belt.activeBeltSlot
                ? player.getInventory().getItem(BeltSwapLogic.TARGET_SLOT)
                : belt.getStackInSlot(slot);
    }

    /** 与原版物品提示框首行一致的名称（含稀有度颜色、自定义名斜体） */
    private Component itemName(ItemStack stack) {
        if (this.minecraft != null) {
            List<Component> tooltip = getTooltipFromItem(this.minecraft, stack);
            if (!tooltip.isEmpty()) return tooltip.get(0);
        }
        return stack.getHoverName();
    }
}
