package cheatchecker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

/** Building blocks for chat messages: styled pieces, pixel padding and full-width rules sized to the chat box. */
final class ChatLines {

    private static final char RULE_CHAR = '\u2550';

    private ChatLines() {}

    static IChatComponent text(String text, EnumChatFormatting color) {
        ChatComponentText component = new ChatComponentText(text);
        component.setChatStyle(new ChatStyle().setColor(color));
        return component;
    }

    static IChatComponent bold(String text, EnumChatFormatting color) {
        IChatComponent component = text(text, color);
        component.getChatStyle()
            .setBold(true);
        return component;
    }

    static IChatComponent hover(IChatComponent component, String tooltip) {
        component.getChatStyle()
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(tooltip)));
        return component;
    }

    static IChatComponent openUrl(IChatComponent component, String url) {
        component.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        return component;
    }

    static IChatComponent runCommand(IChatComponent component, String command) {
        component.getChatStyle()
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        return component;
    }

    static IChatComponent line(IChatComponent... parts) {
        IChatComponent root = new ChatComponentText("");
        for (IChatComponent part : parts) {
            if (part != null) {
                root.appendSibling(part);
            }
        }
        return root;
    }

    private static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    /** Width of the chat box in unscaled pixels: what a line may take before it wraps. */
    static int chatWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        float scale = Math.max(mc.gameSettings.chatScale, 0.1F);
        int width = MathHelper.floor_float(mc.gameSettings.chatWidth * 280.0F + 40.0F);
        return MathHelper.floor_float(width / scale);
    }

    static int width(IChatComponent component) {
        return font().getStringWidth(component.getFormattedText());
    }

    static int width(String text) {
        return font().getStringWidth(text);
    }

    /**
     * Spaces exactly {@code pixels} wide where possible, for aligned columns: a plain space is 4px and a bold one 5px,
     * so up to three bold spaces make up the remainder. Narrower gaps round up to whole plain spaces.
     */
    static IChatComponent gap(int pixels) {
        int plain = Math.max(font().getCharWidth(' '), 1);
        int bold = plain + 1;
        int bolds = 0;
        while (bolds < plain && (pixels - bolds * bold) % plain != 0) {
            bolds++;
        }
        int plains;
        if (pixels - bolds * bold < 0 || (pixels - bolds * bold) % plain != 0) {
            bolds = 0;
            plains = Math.max(0, (pixels + plain - 1) / plain);
        } else {
            plains = (pixels - bolds * bold) / plain;
        }
        IChatComponent root = new ChatComponentText(repeat(' ', plains));
        if (bolds > 0) {
            IChatComponent boldSpaces = new ChatComponentText(repeat(' ', bolds));
            boldSpaces.setChatStyle(new ChatStyle().setBold(true));
            root.appendSibling(boldSpaces);
        }
        return root;
    }

    /**
     * A solid line at most {@code pixels} wide, drawn with box-drawing characters from the default font. (Struck-through
     * spaces are not drawn by every font renderer, so they are not used.)
     */
    static IChatComponent rule(int pixels, EnumChatFormatting color) {
        int ch = Math.max(font().getCharWidth(RULE_CHAR), 1);
        return text(repeat(RULE_CHAR, Math.max(pixels, 0) / ch), color);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(Math.max(n, 0));
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** A rule over the whole chat width with {@code title} in its middle. */
    static IChatComponent titledRule(IChatComponent title, EnumChatFormatting color) {
        int total = chatWidth() - 6;
        int side = Math.max((total - width(title)) / 2, 12);
        return line(rule(side, color), title, rule(side, color));
    }
}
