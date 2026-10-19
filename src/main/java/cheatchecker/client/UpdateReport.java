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
 *  ▲ LagFinder         0.0.1 ➜ 0.0.2   [↑ Update]
 *  ✘ CraftInspector    not installed   [↓ Install]
 *  ✔ 4  ▲ 1  ✘ 2        [↑ Update all] [↓ Install all]
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * </pre>
 *
 * Every mod name opens its GitHub page, every button runs {@code /cheatchecker}, every piece has a tooltip.
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

        lines.add(summary(upToDate, outdated, missing, unknown, results));
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
        if (status.downloaded) {
            icon = text("⟳", EnumChatFormatting.LIGHT_PURPLE);
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
        if (status.downloaded) {
            return line(
                text(latest.version.toString(), EnumChatFormatting.LIGHT_PURPLE),
                text(" · ", FRAME),
                text(I18n.format("cheatchecker.state.restart"), EnumChatFormatting.LIGHT_PURPLE));
        }
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
                    button(status, "update", EnumChatFormatting.GOLD));
            case MISSING:
                return line(
                    text(I18n.format("cheatchecker.state.missing"), EnumChatFormatting.DARK_GRAY),
                    text("  ", FRAME),
                    button(status, "install", EnumChatFormatting.AQUA));
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

    /** {@code [↑ Update]} / {@code [↓ Install]}: runs the command when the release has a jar, else opens its page. */
    private static IChatComponent button(ModStatus status, String action, EnumChatFormatting color) {
        String arrow = "update".equals(action) ? "↑ " : "↓ ";
        IChatComponent button = line(
            text("[", FRAME),
            bold(arrow + I18n.format("cheatchecker.button." + action), color),
            text("]", FRAME));
        ReleaseInfo latest = status.latest;
        if (latest.hasJar() && Config.allowInGameInstall) {
            runCommand(button, "/" + UpdateCommand.NAME + " " + action + " " + status.mod.modId);
            return hover(
                button,
                I18n.format(
                    "cheatchecker.hover." + action,
                    EnumChatFormatting.WHITE + status.mod.displayName + EnumChatFormatting.GRAY,
                    EnumChatFormatting.GREEN + latest.version.toString() + EnumChatFormatting.GRAY) + "\n"
                    + EnumChatFormatting.DARK_GRAY
                    + latest.assetName
                    + (latest.assetSize > 0 ? " · " + size(latest.assetSize) : "")
                    + "\n\n"
                    + EnumChatFormatting.YELLOW
                    + I18n.format("cheatchecker.hover.restart_needed"));
        }
        openUrl(button, latest.pageUrl);
        return hover(button, I18n.format("cheatchecker.hover.open_release", latest.version));
    }

    private static IChatComponent summary(int upToDate, int outdated, int missing, int unknown,
        List<ModStatus> results) {
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

        boolean canUpdate = false, canInstall = false;
        for (ModStatus status : results) {
            canUpdate |= status.state == ModStatus.State.OUTDATED && status.canDownload();
            canInstall |= status.state == ModStatus.State.MISSING && status.canDownload();
        }
        if (Config.allowInGameInstall && (canUpdate || canInstall)) {
            parts.add(text("    ", FRAME));
            if (canUpdate) {
                parts.add(allButton("update_all", "↑ ", EnumChatFormatting.GOLD));
            }
            if (canUpdate && canInstall) {
                parts.add(text(" ", FRAME));
            }
            if (canInstall) {
                parts.add(allButton("install_all", "↓ ", EnumChatFormatting.AQUA));
            }
        } else if (outdated == 0 && missing == 0 && unknown == 0) {
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

    private static IChatComponent allButton(String action, String arrow, EnumChatFormatting color) {
        IChatComponent button = line(
            text("[", FRAME),
            bold(arrow + I18n.format("cheatchecker.button." + action), color),
            text("]", FRAME));
        runCommand(button, "/" + UpdateCommand.NAME + " " + action.replace("_all", "") + " all");
        return hover(
            button,
            I18n.format("cheatchecker.hover." + action) + "\n\n"
                + EnumChatFormatting.YELLOW
                + I18n.format("cheatchecker.hover.restart_needed"));
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

    static String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return Math.max(1, bytes / 1024) + " KB";
    }
}
