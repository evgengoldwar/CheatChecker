package cheatchecker.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import net.minecraft.launchwrapper.Launch;

import cheatchecker.CheatChecker;

/**
 * Downloads the newest jar of a mod into the mods folder and removes the jar it replaces. The new version is loaded on
 * the next start of the game.
 */
public final class ModInstaller {

    private static final Pattern SAFE_JAR_NAME = Pattern.compile("[A-Za-z0-9._+\\-\\[\\]() ]+\\.jar");

    private ModInstaller() {}

    public static File gameDir() {
        return Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
    }

    public static File modsDir() {
        return new File(gameDir(), "mods");
    }

    /** Outcome of {@link #install}. */
    public static final class Result {

        public final File jar;
        public final long bytes;
        /** False when the old jar is still locked and will be deleted when the game exits. */
        public final boolean oldJarRemoved;

        Result(File jar, long bytes, boolean oldJarRemoved) {
            this.jar = jar;
            this.bytes = bytes;
            this.oldJarRemoved = oldJarRemoved;
        }
    }

    /** Blocking: call off the game thread. */
    public static Result install(ModStatus status) throws IOException {
        ReleaseInfo release = status.latest;
        if (release == null || !release.hasJar()) {
            throw new IOException("the release has no jar");
        }
        if (!SAFE_JAR_NAME.matcher(release.assetName)
            .matches()) {
            throw new IOException("unexpected file name " + release.assetName);
        }
        File mods = modsDir();
        if (!mods.isDirectory() && !mods.mkdirs()) {
            throw new IOException("no mods folder at " + mods);
        }
        File target = new File(mods, release.assetName);
        // Not .jar, so a game started meanwhile does not try to load a half-written file.
        File part = new File(mods, release.assetName + ".part");
        long bytes = download(release.assetUrl, part);
        if (release.assetSize > 0 && bytes != release.assetSize) {
            part.delete();
            throw new IOException("download incomplete: " + bytes + " of " + release.assetSize + " bytes");
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        boolean removed = true;
        File old = status.source;
        if (old != null && old.isFile()
            && !old.getCanonicalFile()
                .equals(target.getCanonicalFile())) {
            removed = PendingDeletions.deleteOrQueue(gameDir(), old);
        }
        status.downloaded = true;
        CheatChecker.LOG.info(
            "Downloaded {} {} to {}{}",
            status.mod.displayName,
            release.version,
            target,
            removed ? "" : " (old jar is deleted when the game exits)");
        return new Result(target, bytes, removed);
    }

    private static long download(String url, File to) throws IOException {
        HttpURLConnection connection = UpdateChecker.open(url);
        connection.setRequestProperty("Accept", "application/octet-stream");
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        long total = 0;
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(to.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                total += n;
            }
        } catch (IOException e) {
            to.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
        return total;
    }
}
