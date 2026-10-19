package cheatchecker.client;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

import cheatchecker.CheatChecker;
import cheatchecker.Config;
import cheatchecker.update.ModStatus;
import cheatchecker.update.UpdateChecker;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Shows the update report a moment after the player enters a world, and prints messages queued by other threads on
 * the game thread.
 */
public final class UpdateNotifier {

    /** Ticks after entering a world before the report shows, so it lands below the join messages. */
    private static final int JOIN_DELAY_TICKS = 60;

    /**
     * Chat line ids of the report, one per line: printing a report replaces the previous one instead of stacking a
     * copy under it on every world join.
     */
    private static final int REPORT_LINE_ID = 0x0C4EC400;
    private static final int MAX_REPORT_LINES = 32;

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static int reportLines;

    private int ticksInWorld;
    private boolean shownThisWorld;
    private boolean shownThisSession;

    /** Runs {@code task} on the game thread once a world is open. */
    static void runInWorld(Runnable task) {
        TASKS.add(task);
    }

    static void print(IChatComponent message) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI()
            .printChatMessage(message);
    }

    static void printReport(List<ModStatus> results) {
        GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
        List<IChatComponent> lines = UpdateReport.build(results);
        for (int i = 0; i < reportLines; i++) {
            chat.deleteChatLine(REPORT_LINE_ID + i);
        }
        reportLines = Math.min(lines.size(), MAX_REPORT_LINES);
        for (int i = 0; i < reportLines; i++) {
            chat.printChatMessageWithOptionalDeletion(lines.get(i), REPORT_LINE_ID + i);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            ticksInWorld = 0;
            shownThisWorld = false;
            return;
        }
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                CheatChecker.LOG.error("Could not show the update report", e);
            }
        }
        if (shownThisWorld || ticksInWorld++ < JOIN_DELAY_TICKS) {
            return;
        }
        try {
            showOnJoin();
        } catch (RuntimeException e) {
            shownThisWorld = true;
            CheatChecker.LOG.error("Could not show the update report", e);
        }
    }

    private void showOnJoin() {
        List<ModStatus> results = UpdateChecker.getResults();
        if (results == null) {
            // Still checking (or offline): try again on a later tick, the check may yet finish.
            if (!UpdateChecker.isRunning()) {
                shownThisWorld = true;
            }
            return;
        }
        shownThisWorld = true;
        if (!Config.enableUpdateCheck || (shownThisSession && !Config.showOnEveryWorldJoin)) {
            return;
        }
        if (Config.onlyShowWhenSomethingToDo && !needsAttention(results)) {
            return;
        }
        shownThisSession = true;
        printReport(results);
    }

    private static boolean needsAttention(List<ModStatus> results) {
        for (ModStatus status : results) {
            if (status.state == ModStatus.State.OUTDATED || status.state == ModStatus.State.MISSING) {
                return true;
            }
        }
        return false;
    }
}
