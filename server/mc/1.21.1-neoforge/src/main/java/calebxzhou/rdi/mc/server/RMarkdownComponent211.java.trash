package calebxzhou.rdi.mc.server;

import calebxzhou.rdi.mc.common2.mcp.RMarkdownComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

public final class RMarkdownComponent211 {
    private static final RMarkdownComponent.ComponentAdapter<MutableComponent> ADAPTER =
            new RMarkdownComponent.ComponentAdapter<>() {
                @Override
                public MutableComponent empty() {
                    return Component.empty();
                }

                @Override
                public MutableComponent literal(String text, RMarkdownComponent.TextStyle style) {
                    return Component.literal(text).withStyle(current -> applyStyle(current, style));
                }

                @Override
                public MutableComponent append(MutableComponent target, MutableComponent child) {
                    return target.append(child);
                }
            };

    private RMarkdownComponent211() {
    }

    public static MutableComponent parse(String markdown) {
        return RMarkdownComponent.parse(markdown, ADAPTER);
    }

    public static MutableComponent parseLine(String markdown) {
        return RMarkdownComponent.parseLine(markdown, ADAPTER);
    }

    public static List<MutableComponent> parseLines(String markdown) {
        return RMarkdownComponent.parseLines(markdown, ADAPTER);
    }

    public static List<MutableComponent> parseSignLines(String markdown) {
        return RMarkdownComponent.parseSignLines(markdown, ADAPTER);
    }

    private static Style applyStyle(Style style, RMarkdownComponent.TextStyle markdownStyle) {
        if (markdownStyle.color() != null) {
            ChatFormatting formatting = ChatFormatting.getByName(markdownStyle.color());
            if (formatting != null) {
                style = style.withColor(formatting);
            }
        }
        if (markdownStyle.bold()) {
            style = style.withBold(true);
        }
        if (markdownStyle.italic()) {
            style = style.withItalic(true);
        }
        if (markdownStyle.underlined()) {
            style = style.withUnderlined(true);
        }
        if (markdownStyle.strikethrough()) {
            style = style.withStrikethrough(true);
        }
        if (markdownStyle.clickUrl() != null) {
            style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, markdownStyle.clickUrl()));
        }
        return style;
    }
}
