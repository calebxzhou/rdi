package calebxzhou.rdi.mc.common2.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RMarkdownComponent {
    private static final Set<String> COLORS = colorSet();

    private RMarkdownComponent() {
    }

    public interface ComponentAdapter<T> {
        T empty();

        T literal(String text, TextStyle style);

        T append(T target, T child);
    }

    public record TextStyle(String color, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                            String clickUrl) {

        public static TextStyle plain() {
                return new TextStyle(null, false, false, false, false, null);
            }

            public TextStyle withColor(String color) {
                return new TextStyle(normalizeColor(color), bold, italic, underlined, strikethrough, clickUrl);
            }

            public TextStyle withBold(boolean bold) {
                return new TextStyle(color, bold, italic, underlined, strikethrough, clickUrl);
            }

            public TextStyle withItalic(boolean italic) {
                return new TextStyle(color, bold, italic, underlined, strikethrough, clickUrl);
            }

            public TextStyle withUnderlined(boolean underlined) {
                return new TextStyle(color, bold, italic, underlined, strikethrough, clickUrl);
            }

            public TextStyle withStrikethrough(boolean strikethrough) {
                return new TextStyle(color, bold, italic, underlined, strikethrough, clickUrl);
            }

            public TextStyle withClickUrl(String clickUrl) {
                return new TextStyle(color, bold, italic, underlined, strikethrough, safeUrl(clickUrl));
            }
        }

    public static final class Segment {
        private final String text;
        private final TextStyle style;

        public Segment(String text, TextStyle style) {
            this.text = text;
            this.style = style;
        }

        public String text() {
            return text;
        }

        public TextStyle style() {
            return style;
        }
    }

    public static <T> T parse(String markdown, ComponentAdapter<T> adapter) {
        T root = adapter.empty();
        for (Segment segment : parseSegments(markdown)) {
            adapter.append(root, adapter.literal(segment.text(), segment.style()));
        }
        return root;
    }

    public static <T> T parseLine(String markdown, ComponentAdapter<T> adapter) {
        String line = markdown == null ? "" : markdown.replace("\r", "").replace("\n", " ");
        return parse(line, adapter);
    }

    public static <T> List<T> parseLines(String markdown, ComponentAdapter<T> adapter) {
        String text = markdown == null ? "" : markdown.replace("\r", "");
        String[] lines = text.split("\n", -1);
        var components = new ArrayList<T>(lines.length);
        for (String line : lines) {
            components.add(parseLine(line, adapter));
        }
        return components;
    }

    public static <T> List<T> parseSignLines(String markdown, ComponentAdapter<T> adapter) {
        String text = markdown == null ? "" : markdown.replace("\r", "");
        String[] lines = text.split("\n", -1);
        int count = Math.min(4, Math.max(1, lines.length));
        var components = new ArrayList<T>(count);
        for (int i = 0; i < count; i++) {
            components.add(parseLine(lines[i], adapter));
        }
        return components;
    }

    public static List<Segment> parseSegments(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r", "");
        String[] lines = text.split("\n", -1);
        var segments = new ArrayList<Segment>();
        boolean codeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                codeBlock = !codeBlock;
                continue;
            }
            if (codeBlock) {
                add(segments, line, TextStyle.plain().withColor("gray"));
            } else {
                parseBlockLine(line, segments);
            }
            if (i < lines.length - 1) {
                add(segments, "\n", TextStyle.plain());
            }
        }
        return Collections.unmodifiableList(segments);
    }

    private static void parseBlockLine(String line, List<Segment> segments) {
        int heading = headingLevel(line);
        if (heading > 0) {
            String body = line.substring(heading + 1).trim();
            parseInline(body, TextStyle.plain().withBold(true).withColor(headingColor(heading)), segments);
            return;
        }
        if (line.startsWith("> ")) {
            parseInline(line.substring(2), TextStyle.plain().withItalic(true).withColor("gray"), segments);
            return;
        }
        if (line.startsWith("- ") || line.startsWith("* ")) {
            add(segments, "- ", TextStyle.plain().withColor("gray"));
            parseInline(line.substring(2), TextStyle.plain(), segments);
            return;
        }
        parseInline(line, TextStyle.plain(), segments);
    }

    private static void parseInline(String text, TextStyle base, List<Segment> segments) {
        int i = 0;
        while (i < text.length()) {
            if (starts(text, i, "**")) {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) {
                    parseInline(text.substring(i + 2, end), base.withBold(true), segments);
                    i = end + 2;
                    continue;
                }
            }
            if (starts(text, i, "__")) {
                int end = text.indexOf("__", i + 2);
                if (end > i + 2) {
                    parseInline(text.substring(i + 2, end), base.withBold(true), segments);
                    i = end + 2;
                    continue;
                }
            }
            if (starts(text, i, "~~")) {
                int end = text.indexOf("~~", i + 2);
                if (end > i + 2) {
                    parseInline(text.substring(i + 2, end), base.withStrikethrough(true), segments);
                    i = end + 2;
                    continue;
                }
            }
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    add(segments, text.substring(i + 1, end), base.withColor("aqua"));
                    i = end + 1;
                    continue;
                }
            }
            if (text.charAt(i) == '[') {
                Link link = parseLink(text, i);
                if (link != null) {
                    parseInline(link.label, base.withColor("blue").withUnderlined(true).withClickUrl(link.url), segments);
                    i = link.endIndex;
                    continue;
                }
            }
            if (text.charAt(i) == '{') {
                ColorSpan colorSpan = parseColorSpan(text, i);
                if (colorSpan != null) {
                    parseInline(colorSpan.text, base.withColor(colorSpan.color), segments);
                    i = colorSpan.endIndex;
                    continue;
                }
                ColorSwitch colorSwitch = parseColorSwitch(text, i);
                if (colorSwitch != null) {
                    base = base.withColor(colorSwitch.color);
                    i = colorSwitch.endIndex;
                    continue;
                }
            }
            if (text.charAt(i) == '*') {
                int end = text.indexOf('*', i + 1);
                if (end > i + 1) {
                    parseInline(text.substring(i + 1, end), base.withItalic(true), segments);
                    i = end + 1;
                    continue;
                }
            }
            if (text.charAt(i) == '_') {
                int end = text.indexOf('_', i + 1);
                if (end > i + 1) {
                    parseInline(text.substring(i + 1, end), base.withItalic(true), segments);
                    i = end + 1;
                    continue;
                }
            }

            int next = nextMarker(text, i + 1);
            add(segments, text.substring(i, next), base);
            i = next;
        }
    }

    private static int nextMarker(String text, int from) {
        int next = text.length();
        char[] markers = {'*', '_', '`', '[', '{', '~'};
        for (char marker : markers) {
            int index = text.indexOf(marker, from);
            if (index >= 0 && index < next) {
                next = index;
            }
        }
        return next;
    }

    private static void add(List<Segment> segments, String text, TextStyle style) {
        if (!text.isEmpty()) {
            segments.add(new Segment(text, style));
        }
    }

    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && level < 6 && line.charAt(level) == '#') {
            level++;
        }
        return level > 0 && level < line.length() && line.charAt(level) == ' ' ? level : 0;
    }

    private static String headingColor(int level) {
        return level == 1 ? "gold" : level == 2 ? "yellow" : "aqua";
    }

    private static boolean starts(String text, int index, String marker) {
        return index + marker.length() <= text.length() && text.startsWith(marker, index);
    }

    private static Link parseLink(String text, int start) {
        int labelEnd = text.indexOf("](", start + 1);
        if (labelEnd <= start + 1) {
            return null;
        }
        int urlEnd = text.indexOf(')', labelEnd + 2);
        if (urlEnd <= labelEnd + 2) {
            return null;
        }
        String url = safeUrl(text.substring(labelEnd + 2, urlEnd).trim());
        if (url == null) {
            return null;
        }
        return new Link(text.substring(start + 1, labelEnd), url, urlEnd + 1);
    }

    private static ColorSpan parseColorSpan(String text, int start) {
        int colon = text.indexOf(':', start + 1);
        int end = text.indexOf('}', start + 1);
        if (colon <= start + 1 || end <= colon + 1) {
            return null;
        }
        String color = normalizeColor(text.substring(start + 1, colon));
        if (color == null) {
            return null;
        }
        return new ColorSpan(color, text.substring(colon + 1, end), end + 1);
    }

    private static ColorSwitch parseColorSwitch(String text, int start) {
        int end = text.indexOf('}', start + 1);
        if (end <= start + 1) {
            return null;
        }
        String token = text.substring(start + 1, end).trim();
        if ("reset".equalsIgnoreCase(token)) {
            return new ColorSwitch(null, end + 1);
        }
        String color = normalizeColor(token);
        return color == null ? null : new ColorSwitch(color, end + 1);
    }

    private static String normalizeColor(String color) {
        if (color == null) {
            return null;
        }
        String normalized = color.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return COLORS.contains(normalized) ? normalized : null;
    }

    private static String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? trimmed : null;
    }

    private static Set<String> colorSet() {
        var colors = new HashSet<String>();
        Collections.addAll(
                colors,
                "black",
                "dark_blue",
                "dark_green",
                "dark_aqua",
                "dark_red",
                "dark_purple",
                "gold",
                "gray",
                "dark_gray",
                "blue",
                "green",
                "aqua",
                "red",
                "light_purple",
                "yellow",
                "white"
        );
        return Collections.unmodifiableSet(colors);
    }

    private static final class Link {
        private final String label;
        private final String url;
        private final int endIndex;

        private Link(String label, String url, int endIndex) {
            this.label = label;
            this.url = url;
            this.endIndex = endIndex;
        }
    }

    private static final class ColorSpan {
        private final String color;
        private final String text;
        private final int endIndex;

        private ColorSpan(String color, String text, int endIndex) {
            this.color = color;
            this.text = text;
            this.endIndex = endIndex;
        }
    }

    private static final class ColorSwitch {
        private final String color;
        private final int endIndex;

        private ColorSwitch(String color, int endIndex) {
            this.color = color;
            this.endIndex = endIndex;
        }
    }
}
