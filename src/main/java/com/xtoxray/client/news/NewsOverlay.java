package com.xtoxray.client.news;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class NewsOverlay {
    private static final int MARGIN = 10;
    private static final int BOTTOM_OFFSET = 26;
    private static final int IMAGE_W = 220;

    private static int lastX;
    private static int lastY;
    private static int lastW;
    private static int lastH;
    private static boolean lastVisible;

    private NewsOverlay() {
    }

    public static void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        lastVisible = false;
        NewsManager.Layout layout = NewsManager.layoutFor(screen.width, screen.height);
        NewsManager.Frame frame = NewsManager.frame();
        if (layout == null || layout.compact() || frame == null || frame.current() == null) {
            return;
        }
        Identifier image;
        try {
            image = Identifier.parse(frame.current().getImage());
        } catch (Exception e) {
            return;
        }
        float aspect = NewsImage.aspect(frame.current().getImage());
        int w = IMAGE_W;
        int h = Math.max(40, Math.round(w / aspect));
        int x = screen.width - w - MARGIN;
        int y = screen.height - h - BOTTOM_OFFSET;

        if (frame.fade() < 0.0f) {
            graphics.blit(image, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            float t = frame.fade();
            if (t < 0.5f) {
                blitEntry(graphics, frame.oldEntry(), x, y, w, h);
                shade(graphics, x, y, w, h, t * 2.0f);
            } else {
                graphics.blit(image, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
                shade(graphics, x, y, w, h, (1.0f - t) * 2.0f);
            }
        }

        graphics.outline(x - 2, y - 2, w + 4, h + 4, 0xFF000000);
        graphics.outline(x - 1, y - 1, w + 2, h + 2, 0xFF000000);

        int by1 = y + h;
        int by0 = by1 - 3;
        graphics.fill(x, by0, x + w, by1, 0xAA000000);
        int fillW = (int) (w * Math.min(1.0f, Math.max(0.0f, frame.progress())));
        if (fillW > 0) {
            graphics.fill(x, by0 + 1, x + fillW, by1 - 1, 0xFFFFFFFF);
        }

        lastX = x - 2;
        lastY = y - 2;
        lastW = w + 4;
        lastH = h + 4;
        lastVisible = true;
    }

    private static void blitEntry(GuiGraphicsExtractor graphics, NewsEntry entry, int x, int y, int w, int h) {
        if (entry == null || !entry.hasImage()) {
            return;
        }
        try {
            Identifier id = Identifier.parse(entry.getImage());
            graphics.blit(id, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
        } catch (Exception e) {
        }
    }

    private static void shade(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float strength) {
        int alpha = (int) (Math.min(1.0f, Math.max(0.0f, strength)) * 255.0f);
        if (alpha > 0) {
            graphics.fill(x, y, x + w, y + h, (alpha << 24));
        }
    }
    public static String debugState() {
        return "visible=" + lastVisible + " rect=[" + lastX + "," + lastY + " " + lastW + "x" + lastH + "]";
    }

    public static String clickUrl(double mouseX, double mouseY, int button) {
        if ((button != 0 && button != 1) || !lastVisible) {
            return null;
        }
        if (mouseX < lastX || mouseX >= lastX + lastW || mouseY < lastY || mouseY >= lastY + lastH) {
            return null;
        }
        NewsEntry entry = NewsManager.getCurrentEntry();
        return entry != null && entry.hasUrl() ? entry.getUrl() : null;
    }
}
