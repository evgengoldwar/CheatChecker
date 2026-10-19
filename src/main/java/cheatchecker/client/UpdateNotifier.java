package cheatchecker.client;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;

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

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

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

    /** Replaces the earlier message with the same id (progress lines). */
    static void print(IChatComponent message, int id) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI()
            .printChatMessageWithOptionalDeletion(message, id);
    }

    static void printReport(List<ModStatus> results) {
        for (IChatComponent line : UpdateReport.build(results)) {
            print(line);
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
            task.run();
        }
        if (shownThisWorld || ticksInWorld++ < JOIN_DELAY_TICKS) {
            return;
        }
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
