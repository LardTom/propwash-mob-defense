package de.lardtom.mobdefense.dev.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class CovMdAScreenText {
    private static @Nullable Field text;
    private static @Nullable Field textX;
    private static @Nullable Field textY;

    public record Text(String text, float x, float y) {
    }

    private CovMdAScreenText() {
    }

    public static List<String> lines(Screen screen, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, state, mouseX, mouseY);
        screen.extractRenderState(graphics, mouseX, mouseY, 0.0F);
        graphics.extractDeferredElements(mouseX, mouseY, 0.0F);
        List<String> out = new ArrayList<>();
        state.forEachText(entry -> out.add(string(entry)));
        return out;
    }

    public static List<Text> texts(Screen screen, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, state, mouseX, mouseY);
        screen.extractRenderState(graphics, mouseX, mouseY, 0.0F);
        graphics.extractDeferredElements(mouseX, mouseY, 0.0F);
        List<Text> out = new ArrayList<>();
        state.forEachText(entry -> {
            try {
                Field fx = textX;
                Field fy = textY;
                if (fx == null || fy == null) {
                    fx = GuiTextRenderState.class.getDeclaredField("x");
                    fy = GuiTextRenderState.class.getDeclaredField("y");
                    fx.setAccessible(true);
                    fy.setAccessible(true);
                    textX = fx;
                    textY = fy;
                }
                Vector2f at = entry.pose.transformPosition(new Vector2f(fx.getInt(entry), fy.getInt(entry)));
                out.add(new Text(string(entry), at.x, at.y));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Lage eines GUI-Texts nicht lesbar: " + e, e);
            }
        });
        return out;
    }

    public static List<String> lines(Screen screen) {
        return lines(screen, -1000, -1000);
    }

    public static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    public static String string(GuiTextRenderState state) {
        try {
            Field field = text;
            if (field == null) {
                field = GuiTextRenderState.class.getDeclaredField("text");
                field.setAccessible(true);
                text = field;
            }
            Object value = field.get(state);
            if (!(value instanceof FormattedCharSequence sequence)) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            sequence.accept((index, style, codepoint) -> {
                out.appendCodePoint(codepoint);
                return true;
            });
            return out.toString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Text der GUI nicht lesbar: " + e, e);
        }
    }

    public static int[] slotCenter(AbstractContainerScreen<?> screen, Slot slot) {
        try {
            Field left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field top = AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            return new int[]{left.getInt(screen) + slot.x + 8, top.getInt(screen) + slot.y + 8};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Lage des Menüs nicht lesbar: " + e, e);
        }
    }
}
