package cheatchecker.update;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Old mod jars that could not be deleted right after an update because the game still has them open (Windows locks
 * loaded jars). They are deleted when the game exits, and, failing that, on the next start before mods are loaded.
 * Uses no Minecraft classes: it also runs from the coremod.
 */
public final class PendingDeletions {

    private static final Set<File> SESSION = new LinkedHashSet<>();
    private static boolean hookRegistered;

    private PendingDeletions() {}

    private static File listFile(File gameDir) {
        return new File(gameDir, "config/cheatchecker/pending-deletions.txt");
    }

    /** Deletes the jar now, or queues it. Returns true if it is already gone. */
    public static synchronized boolean deleteOrQueue(File gameDir, File jar) {
        if (jar.delete() || !jar.exists()) {
            return true;
        }
        SESSION.add(jar.getAbsoluteFile());
        save(gameDir);
        if (!hookRegistered) {
            hookRegistered = true;
            Runtime.getRuntime()
                .addShutdownHook(new Thread(PendingDeletions::deleteAfterExit, "CheatChecker old jar cleanup"));
        }
        return false;
    }

    /** Called before mods are discovered: deletes what the last session could not. */
    public static void processOnStartup(File gameDir) {
        File list = listFile(gameDir);
        if (!list.isFile()) {
            return;
        }
        List<String> left = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
                String path = line.trim();
                if (path.isEmpty()) {
                    continue;
                }
                File jar = new File(path);
                if (isJarInModsDir(gameDir, jar) && jar.exists() && !jar.delete()) {
                    left.add(path);
                }
            }
            if (left.isEmpty()) {
                Files.delete(list.toPath());
            } else {
                Files.write(list.toPath(), left, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
    }

    /** Only jars directly inside the mods folder are ever deleted. */
    private static boolean isJarInModsDir(File gameDir, File jar) {
        try {
            File mods = new File(gameDir, "mods").getCanonicalFile();
            File canonical = jar.getCanonicalFile();
            return mods.equals(canonical.getParentFile()) && canonical.getName()
                .toLowerCase(Locale.ROOT)
                .endsWith(".jar");
        } catch (IOException e) {
            return false;
        }
    }

    private static void save(File gameDir) {
        File list = listFile(gameDir);
        list.getParentFile()
            .mkdirs();
        try (PrintWriter out = new PrintWriter(list, "UTF-8")) {
            for (File jar : SESSION) {
                out.println(jar.getPath());
            }
        } catch (IOException ignored) {}
    }

    /**
     * On Windows the jars are only unlocked once this JVM is gone, so a detached script waits a few seconds and
     * deletes them. Elsewhere deleting at once already worked.
     */
    private static void deleteAfterExit() {
        if (!System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win")) {
            return;
        }
        try {
            File script = File.createTempFile("cheatchecker-cleanup", ".bat");
            StringBuilder bat = new StringBuilder();
            bat.append("@echo off\r\nchcp 65001 > nul\r\nping 127.0.0.1 -n 6 > nul\r\n");
            for (File jar : SESSION) {
                bat.append("del /f /q \"")
                    .append(jar.getPath())
                    .append("\"\r\n");
            }
            bat.append("del /f /q \"%~f0\"\r\n");
            Files.write(script.toPath(), bat.toString()
                .getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd", "/c", script.getAbsolutePath()).start();
        } catch (IOException ignored) {}
    }
}
