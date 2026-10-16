package com.theman.toolbelt.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** 原版风格的 GUI 绘制工具：面板、格子、分隔线，以及轮盘用的扇环。 */
public final class GuiDraw {

    public static final int BLACK = 0xFF000000;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int BG = 0xFFC6C6C6;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_BG = 0xFF8B8B8B;

    /** 扇环细分步长（弧度），越小越圆滑 */
    private static final double ARC_STEP = Math.PI / 60.0;

    private GuiDraw() {}

    /** 原版容器那种圆角灰色面板 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + 1, BLACK);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, BLACK);
        g.fill(x, y + 1, x + 1, y + h - 1, BLACK);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, BLACK);

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);

        g.fill(x + 1, y + 1, x + w - 3, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 3, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, BG);
    }

    /** 原版 18×18 格子底框。(x, y) 是格子左上角（= 物品坐标 - 1） */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_BG);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    /** 绘制矩形空心线框 */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 虚影物品上盖的半透明灰（与格子底色一致），让原版物品淡化成空槽提示 */
    private static final int GHOST_OVERLAY = 0xB08B8B8B;

    /**
     * 原版配方书同款"虚影物品"：先画真实物品，再用 ghost overlay 盖一层半透明灰。
     * 图标直接取自原版（及材质包）贴图，100% 原汁原味。(x, y) 为物品坐标。
     */
    public static void ghostItem(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderFakeItem(stack, x, y);
        g.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, GHOST_OVERLAY);
    }

    /** 原版风格的内凹水平分隔线（暗线 + 亮线） */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, SHADOW);
        g.fill(x, y + 1, x + w, y + 2, LIGHT);
    }

    /** 按比例缩放颜色的不透明度 */
    public static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0F, Math.min(1F, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** ARGB 颜色线性插值 */
    public static int lerpColor(int c0, int c1, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a = Math.round(a0 + (a1 - a0) * t);
        int r = Math.round(r0 + (r1 - r0) * t);
        int g = Math.round(g0 + (g1 - g0) * t);
        int b = Math.round(b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Ease-Out Cubic 缓动 */
    public static float easeOutCubic(float t) {
        t = Math.max(0F, Math.min(1F, t));
        float inv = 1F - t;
        return 1F - inv * inv * inv;
    }

    /** 带轻微回弹的 Ease-Out Back 缓动 */
    public static float easeOutBack(float t) {
        t = Math.max(0F, Math.min(1F, t));
        float c1 = 1.45F;
        float c3 = c1 + 1F;
        float p = t - 1F;
        return 1F + c3 * p * p * p + c1 * p * p;
    }

    /** 文字用的 fade：原版字体会把 alpha < 4 的颜色当成不透明，这里保底 alpha = 8 */
    public static int fadeText(int argb, float alpha) {
        int c = fade(argb, alpha);
        return (c >>> 24) < 8 ? (0x08 << 24) | (argb & 0x00FFFFFF) : c;
    }

    /**
     * 扇环（圆环的一段）。角度以正上方为 0，顺时针增大，单位弧度。
     * rIn 为 0 时就是扇形；a0 = 0, a1 = 2π 时就是整个圆环。
     * 顶点写入 {@code RenderType.gui()}，调用方画完后需要 {@code g.flush()}。
     */
    public static void arc(VertexConsumer vc, Matrix4f m, float cx, float cy,
                           float rIn, float rOut, double a0, double a1, int color) {
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / ARC_STEP));
        double step = (a1 - a0) / steps;
        for (int i = 0; i < steps; i++) {
            double s = a0 + i * step;
            double e = s + step;
            float sx = (float) Math.sin(s), sy = (float) -Math.cos(s);
            float ex = (float) Math.sin(e), ey = (float) -Math.cos(e);
            // 顺序为 外s → 内s → 内e → 外e，与 GuiGraphics.fill 的绕序一致
            vc.addVertex(m, cx + sx * rOut, cy + sy * rOut, 0F).setColor(color);
            vc.addVertex(m, cx + sx * rIn, cy + sy * rIn, 0F).setColor(color);
            vc.addVertex(m, cx + ex * rIn, cy + ey * rIn, 0F).setColor(color);
            vc.addVertex(m, cx + ex * rOut, cy + ey * rOut, 0F).setColor(color);
        }
    }

    /**
     * 径向分隔细线（沿着角度 angle 从 rIn 到 rOut 画一条宽 thickness 的细线）。
     */
    public static void radialLine(VertexConsumer vc, Matrix4f m, float cx, float cy,
                                  float rIn, float rOut, double angle, float halfThickness, int color) {
        float dx = (float) Math.sin(angle), dy = (float) -Math.cos(angle);
        // 法向量（顺时针旋转 90 度）
        float nx = -dy * halfThickness, ny = dx * halfThickness;

        float xIn = cx + dx * rIn, yIn = cy + dy * rIn;
        float xOut = cx + dx * rOut, yOut = cy + dy * rOut;

        vc.addVertex(m, xOut - nx, yOut - ny, 0F).setColor(color);
        vc.addVertex(m, xIn - nx, yIn - ny, 0F).setColor(color);
        vc.addVertex(m, xIn + nx, yIn + ny, 0F).setColor(color);
        vc.addVertex(m, xOut + nx, yOut + ny, 0F).setColor(color);
    }
}
