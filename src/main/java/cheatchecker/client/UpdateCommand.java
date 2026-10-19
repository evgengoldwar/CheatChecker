package cheatchecker.client;

import static cheatchecker.client.ChatLines.bold;
import static cheatchecker.client.ChatLines.hover;
import static cheatchecker.client.ChatLines.line;
import static cheatchecker.client.ChatLines.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import cheatchecker.CheatChecker;
import cheatchecker.Config;
import cheatchecker.update.ModInstaller;
import cheatchecker.update.ModStatus;
import cheatchecker.update.TrackedMod;
import cheatchecker.update.UpdateChecker;

/**
 * {@code /cheatchecker [check | update <mod|all> | install <mod|all>]}: shows the update report, re-checks GitHub, or
 * downloads new jars into the mods folder. Client side only; the report's buttons run it.
 */
public final class UpdateCommand extends CommandBase {

    public static final String NAME = "cheatchecker";

    /** Chat line id of the download progress line, so each download replaces the previous one. */
    private static final int PROGRESS_LINE = 0x0C4EC4;

    private static volatile boolean downloading;

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("modupdates");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + NAME + " [check | update <mod|all> | install <mod|all>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "check", "update", "install");
        }
        if (args.length == 2 && ("update".equals(args[0]) || "install".equals(args[0]))) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            for (TrackedMod mod : TrackedMod.values()) {
                ids.add(mod.modId);
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase();
        switch (action) {
            case "":
                List<ModStatus> results = UpdateChecker.getResults();
                if (results == null) {
                    recheck();
                } else {
                    UpdateNotifier.printReport(results);
                }
                return;
            case "check":
                recheck();
                return;
            case "update":
            case "install":
                if (args.length == 2) {
                    download(action, args[1]);
                    return;
                }
                break;
            default:
        }
        UpdateNotifier.print(text(getCommandUsage(sender), EnumChatFormatting.RED));
    }

    private static void recheck() {
        boolean started = UpdateChecker.startAsync(r -> UpdateNotifier.runInWorld(() -> UpdateNotifier.printReport(r)));
        if (started) {
            UpdateNotifier.print(prefix(text(I18n.format("cheatchecker.msg.checking"), EnumChatFormatting.GRAY)));
        }
    }

    private static void download(String action, String target) {
        if (!Config.allowInGameInstall) {
            UpdateNotifier.print(prefix(text(I18n.format("cheatchecker.msg.disabled"), EnumChatFormatting.RED)));
            return;
        }
        List<ModStatus> results = UpdateChecker.getResults();
        if (results == null) {
            UpdateNotifier.print(prefix(text(I18n.format("cheatchecker.msg.not_checked"), EnumChatFormatting.RED)));
            return;
        }
        ModStatus.State wanted = "update".equals(action) ? ModStatus.State.OUTDATED : ModStatus.State.MISSING;
        final List<ModStatus> queue = new ArrayList<>();
        if ("all".equalsIgnoreCase(target)) {
            for (ModStatus status : results) {
                if (status.state == wanted && status.canDownload()) {
                    queue.add(status);
                }
            }
        } else {
            TrackedMod mod = TrackedMod.byModId(target);
            ModStatus status = mod == null ? null : UpdateChecker.find(mod);
            if (status == null) {
                UpdateNotifier
                    .print(prefix(text(I18n.format("cheatchecker.msg.unknown_mod", target), EnumChatFormatting.RED)));
                return;
            }
            if (status.downloaded) {
                UpdateNotifier.print(
                    prefix(
                        text(
                            I18n.format("cheatchecker.msg.already", status.mod.displayName),
                            EnumChatFormatting.LIGHT_PURPLE)));
                return;
            }
            if (!status.canDownload()) {
                UpdateNotifier.print(
                    prefix(
                        text(
                            I18n.format("cheatchecker.msg.nothing", status.mod.displayName),
                            EnumChatFormatting.GRAY)));
                return;
            }
            queue.add(status);
        }
        if (queue.isEmpty()) {
            UpdateNotifier.print(prefix(text(I18n.format("cheatchecker.msg.nothing_all"), EnumChatFormatting.GRAY)));
            return;
        }
        if (downloading) {
            UpdateNotifier.print(prefix(text(I18n.format("cheatchecker.msg.busy"), EnumChatFormatting.GRAY)));
            return;
        }
        downloading = true;
        Thread thread = new Thread(() -> {
            int done = 0;
            try {
                for (ModStatus status : queue) {
                    if (installOne(status)) {
                        done++;
                    }
                }
            } finally {
                downloading = false;
            }
            final int count = done;
            if (count > 0) {
                UpdateNotifier.runInWorld(() -> UpdateNotifier.print(restartLine(count)));
            }
        }, "CheatChecker download");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs on the download thread. */
    private static boolean installOne(final ModStatus status) {
        final String name = status.mod.displayName;
        final String version = status.latest.version.toString();
        UpdateNotifier.runInWorld(
            () -> UpdateNotifier.print(
                prefix(
                    text("↓ ", EnumChatFormatting.AQUA),
                    text(I18n.format("cheatchecker.msg.downloading", name, version), EnumChatFormatting.GRAY)),
                PROGRESS_LINE));
        try {
            final ModInstaller.Result result = ModInstaller.install(status);
            UpdateNotifier.runInWorld(() -> {
                IChatComponent done = prefix(
                    text("✔ ", EnumChatFormatting.GREEN),
                    bold(name, EnumChatFormatting.WHITE),
                    text(" " + version, EnumChatFormatting.GREEN),
                    text(" · ", EnumChatFormatting.DARK_GRAY),
                    hover(
                        text(UpdateReport.size(result.bytes), EnumChatFormatting.GRAY),
                        EnumChatFormatting.GRAY + result.jar.getAbsolutePath()));
                if (!result.oldJarRemoved) {
                    done.appendSibling(text(" · ", EnumChatFormatting.DARK_GRAY));
                    done.appendSibling(
                        hover(
                            text(I18n.format("cheatchecker.msg.old_on_exit"), EnumChatFormatting.YELLOW),
                            I18n.format("cheatchecker.hover.old_on_exit")));
                }
                UpdateNotifier.print(done, PROGRESS_LINE);
            });
            return true;
        } catch (Exception e) {
            CheatChecker.LOG.error("Could not download {}", name, e);
            final String error = String.valueOf(e.getMessage());
            UpdateNotifier.runInWorld(
                () -> UpdateNotifier.print(
                    prefix(
                        text("✘ ", EnumChatFormatting.RED),
                        hover(
                            text(I18n.format("cheatchecker.msg.failed", name), EnumChatFormatting.RED),
                            EnumChatFormatting.RED + error)),
                    PROGRESS_LINE));
            return false;
        }
    }

    private static IChatComponent restartLine(int count) {
        IChatComponent folder = text(I18n.format("cheatchecker.msg.open_mods"), EnumChatFormatting.AQUA);
        folder.getChatStyle()
            .setUnderlined(true)
            .setChatClickEvent(
                new ClickEvent(
                    ClickEvent.Action.OPEN_FILE,
                    ModInstaller.modsDir()
                        .getAbsolutePath()));
        return prefix(
            bold("⟳ ", EnumChatFormatting.LIGHT_PURPLE),
            text(I18n.format("cheatchecker.msg.restart", count), EnumChatFormatting.LIGHT_PURPLE),
            text("  ", EnumChatFormatting.DARK_GRAY),
            folder);
    }

    /** {@code CheatChecker »} before a message. */
    private static IChatComponent prefix(IChatComponent... parts) {
        IChatComponent line = line(
            bold("✦ ", EnumChatFormatting.GOLD),
            text("CheatChecker", EnumChatFormatting.YELLOW),
            text(" » ", EnumChatFormatting.DARK_GRAY));
        for (IChatComponent part : parts) {
            line.appendSibling(part);
        }
        return line;
    }
}
