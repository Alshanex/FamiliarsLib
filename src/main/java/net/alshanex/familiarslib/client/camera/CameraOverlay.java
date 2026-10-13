package net.alshanex.familiarslib.client.camera;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarCameraItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Viewfinder while zoomed (frame corners, focus marks that turn green on a familiar, subject name, paper left),
 * and a white flash after each photo.
 */
public class CameraOverlay implements LayeredDraw.Layer {
    private static final int WHITE = 0xDDFFFFFF;
    private static final int FOCUSED = 0xFF7CFC7C;
    private static final int REC_RED = 0xFFFF3B3B;

    @EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FamiliarsLib.id("familiar_camera"), new CameraOverlay());
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        if (player != null && !mc.options.hideGui && CameraClientEvents.isZoomed(player)) {
            renderViewfinder(graphics, mc, player);
        }
        renderFlash(graphics, partialTick);
    }

    private static void renderViewfinder(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int margin = Math.max(12, Math.min(w, h) / 12);
        int len = margin;

        // Frame corners
        corner(g, margin, margin, 1, 1, len, 2, WHITE);
        corner(g, w - margin, margin, -1, 1, len, 2, WHITE);
        corner(g, margin, h - margin, 1, -1, len, 2, WHITE);
        corner(g, w - margin, h - margin, -1, -1, len, 2, WHITE);

        // Focus marks
        AbstractSpellCastingPet target = CameraClientEvents.target();
        int focus = target != null ? FOCUSED : WHITE;
        int cx = w / 2;
        int cy = h / 2;
        int r = 12;
        corner(g, cx - r, cy - r, 1, 1, 5, 1, focus);
        corner(g, cx + r, cy - r, -1, 1, 5, 1, focus);
        corner(g, cx - r, cy + r, 1, -1, 5, 1, focus);
        corner(g, cx + r, cy + r, -1, -1, 5, 1, focus);
        g.fill(cx, cy, cx + 1, cy + 1, focus);

        // Blinking REC dot + label
        int top = margin + 6;
        if (mc.level != null && (mc.level.getGameTime() / 10) % 2 == 0) {
            g.fill(margin + 6, top, margin + 11, top + 5, REC_RED);
        }
        g.drawString(font, Component.translatable("gui.familiarslib.camera.label"), margin + 15, top - 1, WHITE, true);

        // Paper left
        int paper = FamiliarCameraItem.paperCount(player);
        Component paperText = Component.translatable("gui.familiarslib.camera.paper",
                paper < 0 ? "∞" : String.valueOf(paper));
        g.drawString(font, paperText, w - margin - 6 - font.width(paperText), top - 1,
                paper == 0 ? REC_RED : WHITE, true);

        // Subject: top center, below the label row, so it never overlaps the hotbar or HUD bars (mana, XP...)
        int subjectY = top + 14;
        if (target != null) {
            g.drawCenteredString(font, target.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE), cx, subjectY, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("gui.familiarslib.camera.shoot_hint")
                    .withStyle(ChatFormatting.GRAY), cx, subjectY + 11, 0xFFFFFFFF);
        } else {
            g.drawCenteredString(font, Component.translatable("gui.familiarslib.camera.no_subject")
                    .withStyle(ChatFormatting.GRAY), cx, subjectY, 0xFFFFFFFF);
        }
    }

    private static void renderFlash(GuiGraphics g, float partialTick) {
        int ticks = CameraClientEvents.flashTicks();
        if (ticks <= 0) {
            return;
        }
        float strength = Mth.clamp((ticks - partialTick) / CameraClientEvents.FLASH_TICKS, 0.0F, 1.0F);
        int alpha = (int) (strength * 200.0F);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | 0xFFFFFF);
    }

    /** An L-shaped corner at (x, y), with arms going towards (dx, dy). */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy, int len, int thickness, int color) {
        int armX = x + dx * len;
        int armY = y + dy * len;
        int edgeX = x + dx * thickness;
        int edgeY = y + dy * thickness;
        g.fill(Math.min(x, armX), Math.min(y, edgeY), Math.max(x, armX), Math.max(y, edgeY), color);
        g.fill(Math.min(x, edgeX), Math.min(y, armY), Math.max(x, edgeX), Math.max(y, armY), color);
    }
}