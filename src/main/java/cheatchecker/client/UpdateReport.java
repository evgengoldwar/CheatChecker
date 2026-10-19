package cheatchecker.client;

import static cheatchecker.client.ChatLines.bold;
import static cheatchecker.client.ChatLines.hover;
import static cheatchecker.client.ChatLines.line;
import static cheatchecker.client.ChatLines.openUrl;
import static cheatchecker.client.ChatLines.runCommand;
import static cheatchecker.client.ChatLines.text;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import cheatchecker.Config;
import cheatchecker.update.ModStatus;
import cheatchecker.update.ReleaseInfo;

/**
 * The update report shown in chat:
 *
 * <pre>
 * ━━━━━━━━━━ ✦ Mods by evgengoldwar ✦ ━━━━━━━━━━
 *  ✔ InfoHUD           0.0.24 · up to date
 *  ▲ LagFinder         0.0.1 ➜ 0.0.2   [↓ Download]
 *  ✘ CraftInspector    not installed   [↓ Download 0.0.3]
 *  ✔ 4  ▲ 1  ✘ 1                                   ↻
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * </pre>
 *
 * Mod names and buttons open the latest release on GitHub; every piece has a tooltip.
 */
final class UpdateReport {

    private static final EnumChatFormatting FRAME = EnumChatFormatting.DARK_GRAY;

    private UpdateReport() {}

    static List<IChatComponent> build(List<ModStatus> results) {
        List<IChatComponent> lines = new ArrayList<>();
        lines.add(
            ChatLines.titledRule(
                line(
                    text(" ✦ ", EnumChatFormatting.GOLD),
                    bold(I18n.format("cheatchecker.report.title"), EnumChatFormatting.YELLOW),
                    text(" ✦ ", EnumChatFormatting.GOLD)),
                FRAME));

        int nameWidth = 0;
        for (ModStatus status : results) {
            nameWidth = Math.max(nameWidth, ChatLines.width(status.mod.displayName));
        }

        int upToDate = 0, outdated = 0, missing = 0, unknown = 0;
        for (ModStatus status : results) {
            switch (status.state) {
                case UP_TO_DATE:
                case AHEAD:
                    upToDate++;
                    break;
                case OUTDATED:
                    outdated++;
                    break;
                case MISSING:
                    missing++;
                    break;
                default:
                    unknown++;
            }
            if (status.state == ModStatus.State.UP_TO_DATE && !Config.showUpToDate) {
                continue;
            }
            lines.add(modLine(status, nameWidth));
        }

        lines.add(summary(upToDate, outdated, missing, unknown));
        lines.add(ChatLines.fullRule(FRAME));
        return lines;
    }

    private static IChatComponent modLine(ModStatus status, int nameWidth) {
        String name = status.mod.displayName;
        IChatComponent icon;
        EnumChatFormatting nameColor;
        switch (status.state) {
            case UP_TO_DATE:
            case AHEAD:
                icon = text("✔", EnumChatFormatting.GREEN);
                nameColor = EnumChatFormatting.WHITE;
                break;
            case OUTDATED:
                icon = text("▲", EnumChatFormatting.GOLD);
                nameColor = EnumChatFormatting.YELLOW;
                break;
            case MISSING:
                icon = text("✘", EnumChatFormatting.RED);
                nameColor = EnumChatFormatting.GRAY;
                break;
            default:
                icon = text("?", EnumChatFormatting.DARK_GRAY);
                nameColor = EnumChatFormatting.GRAY;
        }

        String page = status.latest != null ? status.latest.pageUrl : status.mod.repoUrl();
        IChatComponent nameComponent = hover(
            openUrl(text(name, nameColor), page),
            tooltip(status) + "\n\n" + EnumChatFormatting.DARK_GRAY + I18n.format("cheatchecker.hover.open_github"));
        String pad = ChatLines.spaces(nameWidth - ChatLines.width(name) + 6);

        return line(text(" ", FRAME), icon, text(" ", FRAME), nameComponent, text(pad, FRAME), state(status));
    }

    /** The part of a mod line after its name. */
    private static IChatComponent state(ModStatus status) {
        ReleaseInfo latest = status.latest;
        switch (status.state) {
            case UP_TO_DATE:
                return line(
                    text(status.installed.toString(), EnumChatFormatting.GREEN),
                    text(" · ", FRAME),
                    text(I18n.format("cheatchecker.state.up_to_date"), EnumChatFormatting.DARK_GREEN));
            case AHEAD:
                return line(
                    text(status.installed.toString(), EnumChatFormatting.AQUA),
                    text(" · ", FRAME),
                    hover(
                        text(I18n.format("cheatchecker.state.ahead"), EnumChatFormatting.DARK_AQUA),
                        I18n.format("cheatchecker.hover.ahead", latest.version)));
            case OUTDATED:
                return line(
                    text(status.installed.toString(), EnumChatFormatting.RED),
                    text(" ➜ ", EnumChatFormatting.GRAY),
                    text(latest.version.toString(), EnumChatFormatting.GREEN),
                    text("  ", FRAME),
                    button(latest, false, EnumChatFormatting.GOLD));
            case MISSING:
                return line(
                    text(I18n.format("cheatchecker.state.missing"), EnumChatFormatting.DARK_GRAY),
                    text("  ", FRAME),
                    button(latest, true, EnumChatFormatting.AQUA));
            case NO_RELEASES:
                return line(
                    text(status.installed != null ? status.installed.toString() : "—", EnumChatFormatting.GRAY),
                    text(" · ", FRAME),
                    text(I18n.format("cheatchecker.state.no_releases"), EnumChatFormatting.DARK_GRAY));
            default:
                return hover(
                    text("⚠ " + I18n.format("cheatchecker.state.error"), EnumChatFormatting.RED),
                    EnumChatFormatting.RED + String.valueOf(status.error));
        }
    }

    /** {@code [↓ Download]}: opens the page of the latest release. */
    private static IChatComponent button(ReleaseInfo latest, boolean withVersion, EnumChatFormatting color) {
        String label = I18n.format("cheatchecker.button.download") + (withVersion ? " " + latest.version : "");
        IChatComponent button = line(text("[", FRAME), bold("↓ " + label, color), text("]", FRAME));
        openUrl(button, latest.pageUrl);
        // Kept short: the button sits at the right edge of the chat, so a wide tooltip runs off the left of the screen.
        return hover(
            button,
            EnumChatFormatting.GREEN + latest.version.toString()
                + (latest.publishedAt != null ? EnumChatFormatting.DARK_GRAY + " " + latest.publishedAt : "")
                + "\n"
                + EnumChatFormatting.GRAY
                + I18n.format("cheatchecker.hover.open_release"));
    }

    private static IChatComponent summary(int upToDate, int outdated, int missing, int unknown) {
        List<IChatComponent> parts = new ArrayList<>();
        parts.add(text(" ", FRAME));
        parts.add(
            hover(
                text("✔ " + upToDate, EnumChatFormatting.GREEN),
                I18n.format("cheatchecker.summary.up_to_date", upToDate)));
        parts.add(text("  ", FRAME));
        parts.add(
            hover(
                text("▲ " + outdated, outdated > 0 ? EnumChatFormatting.GOLD : EnumChatFormatting.DARK_GRAY),
                I18n.format("cheatchecker.summary.outdated", outdated)));
        parts.add(text("  ", FRAME));
        parts.add(
            hover(
                text("✘ " + missing, missing > 0 ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY),
                I18n.format("cheatchecker.summary.missing", missing)));
        if (unknown > 0) {
            parts.add(text("  ", FRAME));
            parts.add(
                hover(
                    text("? " + unknown, EnumChatFormatting.GRAY),
                    I18n.format("cheatchecker.summary.unknown", unknown)));
        }
        if (outdated == 0 && missing == 0 && unknown == 0) {
            parts.add(text("    ", FRAME));
            parts.add(text(I18n.format("cheatchecker.summary.all_good"), EnumChatFormatting.GREEN));
        }
        parts.add(text("  ", FRAME));
        parts.add(
            hover(
                runCommand(text("↻", EnumChatFormatting.DARK_AQUA), "/" + UpdateCommand.NAME + " check"),
                I18n.format("cheatchecker.hover.recheck")));
        return line(parts.toArray(new IChatComponent[0]));
    }

    private static String tooltip(ModStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.GOLD)
            .append(EnumChatFormatting.BOLD)
            .append(status.mod.displayName)
            .append(EnumChatFormatting.RESET)
            .append('\n')
            .append(EnumChatFormatting.GRAY)
            .append(I18n.format("cheatchecker.hover.installed"))
            .append(' ')
            .append(
                status.installed != null ? EnumChatFormatting.WHITE + status.installed.toString()
                    : EnumChatFormatting.RED + I18n.format("cheatchecker.state.missing"));
        if (status.latest != null) {
            sb.append('\n')
                .append(EnumChatFormatting.GRAY)
                .append(I18n.format("cheatchecker.hover.latest"))
                .append(' ')
                .append(EnumChatFormatting.GREEN)
                .append(status.latest.version);
            if (status.latest.publishedAt != null) {
                sb.append(EnumChatFormatting.DARK_GRAY)
                    .append(" (")
                    .append(status.latest.publishedAt)
                    .append(')');
            }
        }
        sb.append('\n')
            .append(EnumChatFormatting.DARK_GRAY)
            .append(status.mod.repoUrl()
                .replace("https://", ""));
        return sb.toString();
    }
}
