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

    /** Plain spaces at least {@code pixels} wide. */
    static String spaces(int pixels) {
        int space = Math.max(font().getCharWidth(' '), 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (pixels + space - 1) / space; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Struck-through spaces: a solid line {@code pixels} wide (no wider). */
    static IChatComponent rule(int pixels, EnumChatFormatting color) {
        int space = Math.max(font().getCharWidth(' '), 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pixels / space; i++) {
            sb.append(' ');
        }
        IChatComponent component = text(sb.toString(), color);
        component.getChatStyle()
            .setStrikethrough(true);
        return component;
    }

    /** A rule over the whole chat width with {@code title} in its middle. */
    static IChatComponent titledRule(IChatComponent title, EnumChatFormatting color) {
        int total = chatWidth() - 6;
        int side = Math.max((total - width(title)) / 2, 8);
        return line(rule(side, color), title, rule(side, color));
    }

    static IChatComponent fullRule(EnumChatFormatting color) {
        return rule(chatWidth() - 6, color);
    }
}
