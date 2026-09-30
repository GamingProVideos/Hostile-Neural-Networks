package dev.shadowsoffire.hostilenetworks.gui;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class HostileTooltips {
    private HostileTooltips() {}

    public static void show(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.setTooltipForNextFrame(font, text, x, y);
    }

    public static void show(GuiGraphicsExtractor graphics, Font font, List<? extends Component> text, int x, int y) {
        graphics.setTooltipForNextFrame(font, text.stream().map(c -> (Component) c).toList(), Optional.empty(), x, y);
    }
}
