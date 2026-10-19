package cheatchecker.client;

import static cheatchecker.client.ChatLines.bold;
import static cheatchecker.client.ChatLines.line;
import static cheatchecker.client.ChatLines.text;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import cheatchecker.update.ModStatus;
import cheatchecker.update.UpdateChecker;

/**
 * {@code /cheatchecker [check]}: shows the update report again, or asks GitHub again first. Client side only; the
 * report's ↻ runs it.
 */
public final class UpdateCommand extends CommandBase {

    public static final String NAME = "cheatchecker";

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
        return "/" + NAME + " [check]";
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
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "check") : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1 && !"check".equalsIgnoreCase(args[0]))) {
            UpdateNotifier.print(text(getCommandUsage(sender), EnumChatFormatting.RED));
            return;
        }
        List<ModStatus> results = UpdateChecker.getResults();
        if (args.length == 0 && results != null) {
            UpdateNotifier.printReport(results);
            return;
        }
        UpdateNotifier.print(
            line(
                bold("✦ ", EnumChatFormatting.GOLD),
                text("CheatChecker", EnumChatFormatting.YELLOW),
                text(" » ", EnumChatFormatting.DARK_GRAY),
                text(I18n.format("cheatchecker.msg.checking"), EnumChatFormatting.GRAY)));
        UpdateChecker.startAsync(r -> UpdateNotifier.runInWorld(() -> UpdateNotifier.printReport(r)));
    }
}
