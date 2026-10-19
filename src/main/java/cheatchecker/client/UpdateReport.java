package cheatchecker.client;

import static cheatchecker.client.ChatLines.bold;
import static cheatchecker.client.ChatLines.gap;
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
 * The update report shown in chat, in aligned columns:
 *
 * <pre>
 * ══════════ ✦ Mods by EvgenWarGold ✦ ══════════
 *  ■ InfoHUD            0.0.24   up to date
 *  ■ LagFinder          0.0.1  ➜ 0.0.2  [↓ Update]
 *  ■ CraftInspector     —        [↓ Install]
 *  ■ 4  ■ 1  ■ 1                              ↻
 * ═══════════════════════════════════════════════
 * </pre>
 *
 * Mod names and buttons open the latest release on GitHub; every piece has a tooltip.
 */
final class UpdateReport {

    private static final EnumChatFormatting FRAME = EnumChatFormatting.DARK_GRAY;
    /** Same glyph for every state (only its colour changes), so the names after it line up. */
    private static final String MARK = "■";
    private static final int COLUMN_GAP = 6;

    private UpdateReport() {}

    static List<IChatComponent> build(List<ModStatus> results) {
        List<IChatComponent> lines = new ArrayList<>();
        if (allFailed(results)) {
            // Offline or rate-limited: one line instead of a full frame of identical errors.
            lines.add(
                line(
                    bold("✦ ", EnumChatFormatting.GOLD),
                    text("CheatChecker", EnumChatFormatting.YELLOW),
                    text(" » ", FRAME),
                    hover(
                        text("⚠ " + I18n.format("cheatchecker.msg.check_failed"), EnumChatFormatting.RED),
                        EnumChatFormatting.RED + String.valueOf(results.get(0).error)),
                    text("  ", FRAME),
                    recheck()));
            return lines;
        }
        lines.add(
            ChatLines.titledRule(
                line(
                    text(" ✦ ", EnumChatFormatting.GOLD),
                    bold(I18n.format("cheatchecker.report.title"), EnumChatFormatting.YELLOW),
                    text(" ✦ ", EnumChatFormatting.GOLD)),
                FRAME));

        List<ModStatus> shown = new ArrayList<>();
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
            if (status.state != ModStatus.State.UP_TO_DATE || Config.showUpToDate) {
                shown.add(status);
            }
        }

        int nameWidth = 0, versionWidth = 0;
        for (ModStatus status : shown) {
            nameWidth = Math.max(nameWidth, ChatLines.width(status.mod.displayName));
            versionWidth = Math.max(versionWidth, ChatLines.width(installedText(status)));
        }
        for (ModStatus status : shown) {
            lines.add(modLine(status, nameWidth, versionWidth));
        }

        lines.add(summary(upToDate, outdated, missing, unknown));
        lines.add(ChatLines.fullRule(FRAME));
        return lines;
    }

    private static EnumChatFormatting markColor(ModStatus.State state) {
        switch (state) {
            case UP_TO_DATE:
                return EnumChatFormatting.GREEN;
            case AHEAD:
                return EnumChatFormatting.AQUA;
            case OUTDATED:
                return EnumChatFormatting.GOLD;
            case MISSING:
                return EnumChatFormatting.RED;
            default:
                return EnumChatFormatting.DARK_GRAY;
        }
    }

    private static String installedText(ModStatus status) {
        return status.installed != null ? status.installed.shortText() : "—";
    }

    /** {@code ■ Name   version   rest}, the name and version padded to their column widths. */
    private static IChatComponent modLine(ModStatus status, int nameWidth, int versionWidth) {
        String name = status.mod.displayName;
        boolean installed = status.installed != null;
        String page = status.latest != null ? status.latest.pageUrl : status.mod.repoUrl();
        IChatComponent nameComponent = hover(
            openUrl(text(name, installed ? EnumChatFormatting.WHITE : EnumChatFormatting.GRAY), page),
            tooltip(status) + "\n\n" + EnumChatFormatting.DARK_GRAY + I18n.format("cheatchecker.hover.open_github"));

        String version = installedText(status);
        IChatComponent versionComponent = text(version, versionColor(status.state));
        if (status.installed != null && status.installed.isShortened()) {
            hover(versionComponent, EnumChatFormatting.GRAY + status.installed.toString());
        }

        return line(
            text(" ", FRAME),
            text(MARK, markColor(status.state)),
            text(" ", FRAME),
            nameComponent,
            gap(nameWidth - ChatLines.width(name) + COLUMN_GAP),
            versionComponent,
            gap(versionWidth - ChatLines.width(version) + COLUMN_GAP),
            rest(status));
    }

    private static EnumChatFormatting versionColor(ModStatus.State state) {
        switch (state) {
            case UP_TO_DATE:
                return EnumChatFormatting.GREEN;
            case AHEAD:
                return EnumChatFormatting.AQUA;
            case OUTDATED:
                return EnumChatFormatting.RED;
            default:
                return EnumChatFormatting.DARK_GRAY;
        }
    }

    /** What comes after the installed version. */
    private static IChatComponent rest(ModStatus status) {
        ReleaseInfo latest = status.latest;
        switch (status.state) {
            case UP_TO_DATE:
                return text(I18n.format("cheatchecker.state.up_to_date"), EnumChatFormatting.DARK_GREEN);
            case AHEAD:
                return hover(
                    text(I18n.format("cheatchecker.state.ahead"), EnumChatFormatting.DARK_AQUA),
                    I18n.format("cheatchecker.hover.ahead", latest.version.shortText()));
            case OUTDATED:
                return line(
                    text("➜ ", EnumChatFormatting.GRAY),
                    text(latest.version.shortText(), EnumChatFormatting.GREEN),
                    text("  ", FRAME),
                    button(latest, "update", EnumChatFormatting.GOLD));
            case MISSING:
                return button(latest, "install", EnumChatFormatting.AQUA);
            case NO_RELEASES:
                return text(I18n.format("cheatchecker.state.no_releases"), EnumChatFormatting.DARK_GRAY);
            default:
                return hover(
                    text("⚠ " + I18n.format("cheatchecker.state.error"), EnumChatFormatting.RED),
                    EnumChatFormatting.RED + String.valueOf(status.error));
        }
    }

    /** {@code [↓ Update]} / {@code [↓ Install]}: opens the page of the latest release. */
    private static IChatComponent button(ReleaseInfo latest, String action, EnumChatFormatting color) {
        IChatComponent button = line(
            text("[", FRAME),
            text("↓ " + I18n.format("cheatchecker.button." + action), color),
            text("]", FRAME));
        openUrl(button, latest.pageUrl);
        // Kept short: the button sits at the right edge of the chat, so a wide tooltip runs off the left of the screen.
        return hover(
            button,
            EnumChatFormatting.GREEN + latest.version.shortText()
                + (latest.publishedAt != null ? EnumChatFormatting.DARK_GRAY + " " + latest.publishedAt : "")
                + "\n"
                + EnumChatFormatting.GRAY
                + I18n.format("cheatchecker.hover.open_release"));
    }

    private static IChatComponent summary(int upToDate, int outdated, int missing, int unknown) {
        List<IChatComponent> parts = new ArrayList<>();
        parts.add(text(" ", FRAME));
        parts.add(count(upToDate, EnumChatFormatting.GREEN, "cheatchecker.summary.up_to_date"));
        parts.add(text("   ", FRAME));
        parts.add(count(outdated, EnumChatFormatting.GOLD, "cheatchecker.summary.outdated"));
        parts.add(text("   ", FRAME));
        parts.add(count(missing, EnumChatFormatting.RED, "cheatchecker.summary.missing"));
        if (unknown > 0) {
            parts.add(text("   ", FRAME));
            parts.add(count(unknown, EnumChatFormatting.GRAY, "cheatchecker.summary.unknown"));
        }
        if (outdated == 0 && missing == 0 && unknown == 0) {
            parts.add(text("    ", FRAME));
            parts.add(text(I18n.format("cheatchecker.summary.all_good"), EnumChatFormatting.GREEN));
        }
        parts.add(text("    ", FRAME));
        parts.add(recheck());
        return line(parts.toArray(new IChatComponent[0]));
    }

    /** {@code ■ 3}, grey when zero. */
    private static IChatComponent count(int n, EnumChatFormatting color, String hoverKey) {
        EnumChatFormatting c = n > 0 ? color : EnumChatFormatting.DARK_GRAY;
        return hover(line(text(MARK + " ", c), text(String.valueOf(n), c)), I18n.format(hoverKey, n));
    }

    private static IChatComponent recheck() {
        return hover(
            runCommand(text("↻", EnumChatFormatting.DARK_AQUA), "/" + UpdateCommand.NAME + " check"),
            I18n.format("cheatchecker.hover.recheck"));
    }

    private static boolean allFailed(List<ModStatus> results) {
        for (ModStatus status : results) {
            if (status.state != ModStatus.State.ERROR) {
                return false;
            }
        }
        return !results.isEmpty();
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
